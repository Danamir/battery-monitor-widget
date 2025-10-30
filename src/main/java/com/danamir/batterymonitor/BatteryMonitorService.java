package com.danamir.batterymonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class BatteryMonitorService extends Service {
    private BatteryReceiver batteryReceiver;
    private Handler handler;
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "battery_monitor_channel";
    private int currentBatteryLevel = -1;
    private boolean isCharging = false;

    private ServiceConnection guardConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Connection established
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // BatteryMonitorGuardService was killed, restart it
            restartGuardService();

            // Rebind to continue monitoring
            bindToGuardService();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        // Create notification channel first (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, createNotification());

        // Register battery receiver
        batteryReceiver = new BatteryReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);

        // Set up periodic updates (fallback to ensure updates even if battery doesn't change)
        handler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                // Request battery status update
                Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                if (batteryIntent != null) {
                    batteryReceiver.onReceive(BatteryMonitorService.this, batteryIntent);

                    // Explicitly update widgets to ensure they stay synchronized
                    BatteryWidgetProvider.updateAllWidgets(BatteryMonitorService.this);
                }

                // Schedule next update
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        };
        handler.post(updateRunnable);

        // Start and bind to BatteryMonitorGuardService for mutual protection
        startGuardService();
        bindToGuardService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Update battery level from intent if available
        if (intent != null && intent.hasExtra("battery_level")) {
            int batteryLevel = intent.getIntExtra("battery_level", -1);
            boolean charging = intent.getBooleanExtra("is_charging", false);
            updateNotification(batteryLevel, charging);
        }

        // Start as foreground service on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        return START_STICKY; // Ensure service restarts if killed
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Battery Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Monitors battery status for widget");
            channel.setShowBadge(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        String contentText;
        if (currentBatteryLevel >= 0) {
            contentText = currentBatteryLevel + "%" + (isCharging ? " (Charging)" : "");
        } else {
            contentText = "Monitoring battery for widget";
        }

        return builder
            .setContentTitle("Battery Monitor")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    public void updateNotification(int batteryLevel, boolean charging) {
        this.currentBatteryLevel = batteryLevel;
        this.isCharging = charging;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    private void startGuardService() {
        Intent intent = new Intent(this, BatteryMonitorGuardService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void bindToGuardService() {
        Intent intent = new Intent(this, BatteryMonitorGuardService.class);
        bindService(intent, guardConnection, Context.BIND_IMPORTANT);
    }

    private void restartGuardService() {
        Intent intent = new Intent(this, BatteryMonitorGuardService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }

        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }

        try {
            unbindService(guardConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new BatteryMonitorBinder();
    }

    public class BatteryMonitorBinder extends android.os.Binder {
        BatteryMonitorService getService() {
            return BatteryMonitorService.this;
        }
    }
}
