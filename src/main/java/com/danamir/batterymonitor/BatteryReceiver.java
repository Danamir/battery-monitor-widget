package com.danamir.batterymonitor;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;

public class BatteryReceiver extends BroadcastReceiver {

    /**
     * Query and update current battery status to ensure fresh data.
     * @param context The application context
     * @return int array with [batteryPct, isCharging ? 1 : 0] or null if failed
     */
    private int[] updateCurrentBatteryStatus(Context context) {
        android.content.IntentFilter batteryFilter = new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = context.registerReceiver(null, batteryFilter);

        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL;

            if (level >= 0 && scale > 0) {
                int batteryPct = (int) ((level / (float) scale) * 100);

                // Update battery data with current status
                BatteryDataManager dataManager = BatteryDataManager.getInstance(context);
                dataManager.addDataPoint(batteryPct, isCharging);

                return new int[]{batteryPct, isCharging ? 1 : 0};
            }
        }
        return null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
            // Configuration changed (e.g., orientation change) - update widgets
            BatteryWidgetProvider.updateAllWidgets(context);
            return;
        }

        if (Intent.ACTION_USER_PRESENT.equals(action)) {
            // User unlocked device - start user_present status
            StatusManager statusManager = StatusManager.getInstance(context);
            statusManager.startStatus("user_present", System.currentTimeMillis());

            // Log unlock event
            EventLogManager eventLogManager = EventLogManager.getInstance(context);
            eventLogManager.logEvent("Device unlocked");

            // Query current battery status to ensure fresh data - use unified source
            int[] batteryData = updateCurrentBatteryStatus(context);

            BatteryWidgetProvider.updateAllWidgets(context);

            // Update notification with the same battery data used for widgets
            if (batteryData != null) {
                Intent serviceIntent = new Intent(context, BatteryMonitorService.class);
                serviceIntent.putExtra("battery_level", batteryData[0]);
                serviceIntent.putExtra("charging", batteryData[1] == 1);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return;
        }

        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            // Screen turned off - end user_present status
            StatusManager statusManager = StatusManager.getInstance(context);
            statusManager.endStatus("user_present", System.currentTimeMillis());
            BatteryWidgetProvider.updateAllWidgets(context);
            return;
        }

        if (Intent.ACTION_SCREEN_ON.equals(action)) {
            // Screen turned on - query current battery status for fresh data
            updateCurrentBatteryStatus(context);

            // Update widgets to show latest data
            BatteryWidgetProvider.updateAllWidgets(context);
            return;
        }

        if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL;

            if (level >= 0 && scale > 0) {
                int batteryPct = (int) ((level / (float) scale) * 100);

                // Store the battery data
                BatteryDataManager dataManager = BatteryDataManager.getInstance(context);
                dataManager.addDataPoint(batteryPct, isCharging);

                // Update all widgets
                BatteryWidgetProvider.updateAllWidgets(context);

                // Start service if widgets are active (only on first battery change)
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, BatteryWidgetProvider.class)
                );

                if (appWidgetIds != null && appWidgetIds.length > 0) {
                    Intent serviceIntent = new Intent(context, BatteryMonitorService.class);
                    serviceIntent.putExtra("battery_level", batteryPct);
                    serviceIntent.putExtra("charging", isCharging);
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent);
                        } else {
                            context.startService(serviceIntent);
                        }
                    } catch (Exception e) {
                        // Service start failed, will rely on periodic widget updates
                        e.printStackTrace();
                    }
                }
            }
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Check if widgets are active before starting service
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, BatteryWidgetProvider.class)
            );

            if (appWidgetIds != null && appWidgetIds.length > 0) {
                Intent serviceIntent = new Intent(context, BatteryMonitorService.class);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
