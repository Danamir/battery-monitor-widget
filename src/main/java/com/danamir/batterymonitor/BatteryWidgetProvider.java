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

    // Click action constants
    private static final String ACTION_WIDGET_CLICK = "com.danamir.batterymonitor.WIDGET_CLICK";
    private static final String EXTRA_CLICK_ZONE = "click_zone";

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
        updateAppWidget(context, appWidgetManager, appWidgetId);
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
        boolean zoomedDisplay = prefs.getBoolean("zoomed_display", false);
        int displayHours = Integer.parseInt(prefs.getString("display_length_hours", "48"));

        // Apply zoom multiplier if zoomed display is enabled
        if (zoomedDisplay) {
            int zoomMult = prefs.getInt("display_zoom_mult", 10);
            displayHours = displayHours / zoomMult;
            if (displayHours < 1) displayHours = 1; // Ensure at least 1 hour
        }

        // Get battery data
        BatteryDataManager dataManager = BatteryDataManager.getInstance(context);

        // Get status data
        StatusManager statusManager = StatusManager.getInstance(context);
        java.util.List<StatusData> statusData = statusManager.getStatusData("user_present", displayHours);

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
            dataManager.getDataPoints(displayHours, true),
            statusData,
            displayHours,
            width,
            height
        );

        // Convert Picture to Bitmap for RemoteViews
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        picture.draw(canvas);

        // Force cache invalidation
        long timestamp = System.currentTimeMillis();
        views.setContentDescription(R.id.battery_graph, "Widget:" + appWidgetId + "@" + timestamp);

        // Set the bitmap to the ImageView
        views.setImageViewBitmap(R.id.battery_graph, bitmap);

        // Set up click intents for each zone
        setupClickZone(context, views, R.id.click_zone_top_left, "top_left");
        setupClickZone(context, views, R.id.click_zone_top, "top");
        setupClickZone(context, views, R.id.click_zone_top_right, "top_right");
        setupClickZone(context, views, R.id.click_zone_left, "left");
        setupClickZone(context, views, R.id.click_zone_center, "center");
        setupClickZone(context, views, R.id.click_zone_right, "right");
        setupClickZone(context, views, R.id.click_zone_bottom_left, "bottom_left");
        setupClickZone(context, views, R.id.click_zone_bottom, "bottom");
        setupClickZone(context, views, R.id.click_zone_bottom_right, "bottom_right");

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void setupClickZone(Context context, RemoteViews views, int viewId, String zoneName) {
        Intent intent = new Intent(context, BatteryWidgetProvider.class);
        intent.setAction(ACTION_WIDGET_CLICK);
        intent.putExtra(EXTRA_CLICK_ZONE, zoneName);

        // Use unique request code for each zone to ensure different PendingIntents
        int requestCode = zoneName.hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        views.setOnClickPendingIntent(viewId, pendingIntent);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_WIDGET_CLICK.equals(intent.getAction())) {
            String clickZone = intent.getStringExtra(EXTRA_CLICK_ZONE);
            handleWidgetClick(context, clickZone);
        }
    }

    private String getDefaultActionForZone(String zoneName) {
        switch (zoneName) {
            case "left":
            case "bottom_left":
                return "switch_estimation_source";
            case "bottom":
                return "toggle_date_estimation";
            case "right":
            case "bottom_right":
                return "toggle_zoom";
            default:
                return "open_preferences";
        }
    }

    private void handleWidgetClick(Context context, String clickZone) {
        // Log the click event
        EventLogManager eventLogManager = EventLogManager.getInstance(context);
        eventLogManager.logEvent("Widget clicked: " + clickZone);

        // Get the action for this zone
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String zoneKey = "zone_action_" + clickZone;
        String action = prefs.getString(zoneKey, getDefaultActionForZone(clickZone));

        // Execute action based on zone setting
        boolean needsUpdate = false;
        switch (action) {
            case "switch_estimation_source":
                // Toggle use_long_term preference
                boolean useLongTerm = prefs.getBoolean("use_long_term", false);
                prefs.edit().putBoolean("use_long_term", !useLongTerm).apply();
                needsUpdate = true;
                break;

            case "toggle_date_estimation":
                // Toggle show_time_estimation preference
                boolean showTimeEstimation = prefs.getBoolean("show_time_estimation", false);
                prefs.edit().putBoolean("show_time_estimation", !showTimeEstimation).apply();
                needsUpdate = true;
                break;

            case "toggle_zoom":
                // Toggle zoomed_display preference
                boolean zoomedDisplay = prefs.getBoolean("zoomed_display", false);
                prefs.edit().putBoolean("zoomed_display", !zoomedDisplay).apply();
                needsUpdate = true;
                break;

            case "open_preferences":
            default:
                // Open settings activity
                Intent settingsIntent = new Intent(context, SettingsActivity.class);
                settingsIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                settingsIntent.putExtra(EXTRA_CLICK_ZONE, clickZone);
                context.startActivity(settingsIntent);
                break;
        }

        // Update all widgets if needed
        if (needsUpdate) {
            updateAllWidgets(context);
        }
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
