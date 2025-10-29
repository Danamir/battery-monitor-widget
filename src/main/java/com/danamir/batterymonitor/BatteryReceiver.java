package com.danamir.batterymonitor;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;

public class BatteryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

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
                    serviceIntent.putExtra("is_charging", isCharging);
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
