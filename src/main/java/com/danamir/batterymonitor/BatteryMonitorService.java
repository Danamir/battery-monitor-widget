package com.danamir.batterymonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class BatteryMonitorService extends Service {
    private BatteryReceiver batteryReceiver;
    private BatteryReceiver screenReceiver;
    private Handler handler;
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "battery_monitor_channel";
    private int currentBatteryLevel = -1;
    private boolean isCharging = false;

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

        // Register screen receiver for USER_PRESENT and SCREEN_OFF (must be registered dynamically)
        screenReceiver = new BatteryReceiver();
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_USER_PRESENT);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, screenFilter);

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

        String contentTitle;
        String contentText = "Battery Monitor";

        if (currentBatteryLevel >= 0) {
            Double usageRateValue = calculateBatteryUsageRateValue();
            String usageRate = usageRateValue != null ? String.format("%.1f%%/h", usageRateValue) : null;
            contentTitle = "Battery " + currentBatteryLevel + "%" +
                          (usageRate != null ? " • " + usageRate : "") + (isCharging ? " (Charging)" : "");

            // Calculate time to 20% if discharging and rate is available
            if (!isCharging && usageRateValue != null && usageRateValue > 0 && currentBatteryLevel > 20) {
                double hoursTo20 = (currentBatteryLevel - 20) / usageRateValue;
                contentText = formatTimeEstimate(hoursTo20);
                contentText += " • Battery Monitor";
            }
        } else {
            contentTitle = "Battery Monitor";
        }

        return builder
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_battery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private String formatTimeEstimate(double hours) {
        if (hours < 0) {
            return "Battery Monitor";
        }

        int totalMinutes = (int) Math.round(hours * 60);
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;

        if (h > 0) {
            return String.format("~%dh%02dm to 20%%", h, m);
        } else {
            return String.format("~%dm to 20%%", m);
        }
    }

    private Double calculateBatteryUsageRateValue() {
        BatteryDataManager dataManager = BatteryDataManager.getInstance(this);
        java.util.List<BatteryData> dataPoints = dataManager.getDataPoints(24);

        if (dataPoints.size() < 2) {
            return null;
        }

        // Find the most recent continuous discharge period
        BatteryData endPoint = null;
        BatteryData startPoint = null;

        for (int i = dataPoints.size() - 1; i >= 0; i--) {
            BatteryData point = dataPoints.get(i);

            if (endPoint == null && !point.isCharging()) {
                endPoint = point;
            } else if (endPoint != null && !point.isCharging()) {
                startPoint = point;
            } else if (endPoint != null && point.isCharging()) {
                break;
            }
        }

        if (startPoint == null || endPoint == null) {
            return null;
        }

        long timeDiffMs = endPoint.getTimestamp() - startPoint.getTimestamp();
        int levelDiff = startPoint.getLevel() - endPoint.getLevel();

        // Need at least 10 minutes of data for reasonable calculation
        if (timeDiffMs < 600000 || levelDiff <= 0) {
            return null;
        }

        // Calculate rate per hour
        double hours = timeDiffMs / 3600000.0;
        double ratePerHour = levelDiff / hours;

        return ratePerHour;
    }

    public void updateNotification(int batteryLevel, boolean charging) {
        this.currentBatteryLevel = batteryLevel;
        this.isCharging = charging;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (batteryReceiver != null) {
            unregisterReceiver(batteryReceiver);
        }

        if (screenReceiver != null) {
            unregisterReceiver(screenReceiver);
        }

        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }
}
