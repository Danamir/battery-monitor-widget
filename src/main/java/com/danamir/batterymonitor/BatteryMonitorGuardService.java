package com.danamir.batterymonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

public class BatteryMonitorGuardService extends Service {
    private static final int NOTIFICATION_ID = 2;
    private static final String CHANNEL_ID = "guard_service_channel";

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Connection established
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // BatteryMonitorService was killed, restart it
            restartBatteryMonitorService();
            
            // Rebind to continue monitoring
            bindToBatteryMonitorService();
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

        bindToBatteryMonitorService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start as foreground service on Android O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification());
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Guard Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Protects battery monitoring service");
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

        return builder
            .setContentTitle("Battery Monitor Guard")
            .setContentText("Protecting battery monitor service")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void bindToBatteryMonitorService() {
        Intent intent = new Intent(this, BatteryMonitorService.class);
        bindService(intent, serviceConnection, Context.BIND_IMPORTANT);
    }

    private void restartBatteryMonitorService() {
        Intent intent = new Intent(this, BatteryMonitorService.class);
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
        try {
            unbindService(serviceConnection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new GuardBinder();
    }

    public class GuardBinder extends android.os.Binder {
        BatteryMonitorGuardService getService() {
            return BatteryMonitorGuardService.this;
        }
    }
}
