package com.danamir.batterymonitor;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.widget.RemoteViews;
import androidx.preference.PreferenceManager;

public class BatteryWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        // Called when widget is resized
        updateAppWidget(context, appWidgetManager, appWidgetId);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        
        // Initialize with current battery level
        android.content.IntentFilter intentFilter = new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryIntent = context.registerReceiver(null, intentFilter);
        
        if (batteryIntent != null) {
            int level = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            int status = batteryIntent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            
            boolean isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == android.os.BatteryManager.BATTERY_STATUS_FULL;
            
            if (level >= 0 && scale > 0) {
                int batteryPct = (int) ((level / (float) scale) * 100);
                BatteryDataManager dataManager = BatteryDataManager.getInstance(context);
                dataManager.addDataPoint(batteryPct, isCharging);
            }
        }
        
        // Service will be started by BatteryReceiver on first battery change
        // No need to start service here to avoid background service restriction
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        // Stop the service when no widgets are active
        Intent serviceIntent = new Intent(context, BatteryMonitorService.class);
        context.stopService(serviceIntent);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.battery_widget);

        // Get display hours from preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        int displayHours = Integer.parseInt(prefs.getString("display_length_hours", "48"));

        // Get battery data
        BatteryDataManager dataManager = BatteryDataManager.getInstance(context);

        // Get actual widget size
        android.os.Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int widgetWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250);
        int widgetHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 40);

        // Convert dp to pixels for rendering
        float density = context.getResources().getDisplayMetrics().density;
        int width = (int) (widgetWidthDp * density);
        int height = (int) (widgetHeightDp * density);

        // Ensure minimum size
        if (width < 100) width = (int) (250 * density);
        if (height < 100) height = (int) (40 * density);

        // Generate as Picture for resolution-independent rendering
        Picture picture = BatteryGraphGenerator.generateGraphAsPicture(
            context,
            dataManager.getDataPoints(displayHours),
            displayHours,
            width,
            height
        );

        // Convert Picture to Bitmap for RemoteViews
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        picture.draw(canvas);

        // Set the bitmap to the ImageView
        views.setImageViewBitmap(R.id.battery_graph, bitmap);

        // Set up click intent to open settings
        Intent intent = new Intent(context, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.battery_graph, pendingIntent);

        // Force cache invalidation by setting a unique content description
        views.setContentDescription(R.id.battery_graph, "Updated: " + System.currentTimeMillis());

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
            new android.content.ComponentName(context, BatteryWidgetProvider.class)
        );

        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}
