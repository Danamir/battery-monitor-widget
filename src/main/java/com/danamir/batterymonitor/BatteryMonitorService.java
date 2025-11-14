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
    private BatteryReceiver configReceiver;
    private Handler handler;
    private Runnable updateRunnable;
    private static final long UPDATE_INTERVAL = 60000; // 1 minute
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "battery_monitor_channel";

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

        // Register configuration change receiver (must be registered dynamically)
        configReceiver = new BatteryReceiver();
        IntentFilter configFilter = new IntentFilter();
        configFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(configReceiver, configFilter);

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
            boolean charging = intent.getBooleanExtra("charging", false);
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

        // Get target percentages
        // android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);

        String contentTitle;
        String contentText = "";

        java.util.Map<String, String> values = BatteryUtils.calculateValues(this, true);
        if(!values.get("current_level").isEmpty()) {

            String usageRate = values.get("usage_rate");
            String hoursTo = values.get("hours_to");
            String timeTo = values.get("time_to");
            String currentPercent = values.get("current_percent");
            String calculationDuration = values.get("calculation_duration");
            boolean isCharging = "true".equals(values.get("charging"));

            contentTitle = "Battery " + currentPercent + (isCharging ? " ⚡" : "")
                        + (!usageRate.isEmpty() ? BatteryUtils.TEXT_SEPARATOR + usageRate + "%/h" : "");

            // Add time estimate if available
            if (!usageRate.isEmpty() && !hoursTo.isEmpty()) {
                String timeEstimate = hoursTo + " (" + timeTo + ")";
                contentText = timeEstimate + " Last " + calculationDuration;
            }

            // Long-term estimation (max charge or all-time stats)
            String usageRateLongTerm = values.get("usage_rate_long_term");
            String hoursToLongTerm = values.get("hours_to_long_term");
            String timeToLongTerm = values.get("time_to_long_term");
            if (!usageRateLongTerm.isEmpty()) {
                contentTitle += BatteryUtils.TEXT_SEPARATOR_ALT + usageRateLongTerm + "%/h";

                String timeEstimate = hoursToLongTerm + " (" + timeToLongTerm + ")";
                if (!contentText.isEmpty()) {
                    contentText += "\n";
                }
                
                // Determine label: always "All-time" when charging, otherwise use preference
                String label;
                if (isCharging) {
                    label = "All-time";
                } else {
                    android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
                    String estimationSource = prefs.getString("estimation_source", "max_charge");
                    label = "all_time_stats".equals(estimationSource) ? "All-time" : "Since max";
                }
                
                contentText += timeEstimate + " " + label;
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

    public void updateNotification(int batteryLevel, boolean charging) {
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

        if (configReceiver != null) {
            unregisterReceiver(configReceiver);
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
