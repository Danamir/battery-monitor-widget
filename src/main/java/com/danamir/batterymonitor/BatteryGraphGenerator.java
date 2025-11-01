package com.danamir.batterymonitor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class BatteryGraphGenerator {

    /**
     * Calculate a contrasting text color based on the background color.
     * Uses relative luminance to determine if background is light or dark,
     * then returns a lighter or darker variation of the background color.
     */
    private static int getContrastingTextColor(int backgroundColor) {
        // Extract RGB components
        int red = Color.red(backgroundColor);
        int green = Color.green(backgroundColor);
        int blue = Color.blue(backgroundColor);

        // Calculate relative luminance (perceived brightness)
        // Using sRGB luminance formula: Y = 0.2126*R + 0.7152*G + 0.0722*B
        double luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255.0;

        // If background is dark (luminance < 0.5), make text lighter
        // If background is light (luminance >= 0.5), make text darker
        if (luminance < 0.5) {
            // Dark background - make lighter version
            int newRed = Math.min(255, red + (int)((255 - red) * 0.6));
            int newGreen = Math.min(255, green + (int)((255 - green) * 0.6));
            int newBlue = Math.min(255, blue + (int)((255 - blue) * 0.6));
            return Color.rgb(newRed, newGreen, newBlue);
        } else {
            // Light background - make darker version
            int newRed = (int)(red * 0.3);
            int newGreen = (int)(green * 0.3);
            int newBlue = (int)(blue * 0.3);
            return Color.rgb(newRed, newGreen, newBlue);
        }
    }

    /**
     * Linearly blend between two colors based on a ratio.
     * @param color1 The first color
     * @param color2 The second color
     * @param ratio The blend ratio (0.0 = color1, 1.0 = color2)
     * @return The blended color
     */
    private static int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1.0f - ratio;

        int r = (int) (Color.red(color1) * inverseRatio + Color.red(color2) * ratio);
        int g = (int) (Color.green(color1) * inverseRatio + Color.green(color2) * ratio);
        int b = (int) (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio);
        int a = (int) (Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio);

        return Color.argb(a, r, g, b);
    }

    /**
     * Parse time string in HH:mm format to minutes since midnight.
     * @param timeStr Time string (e.g., "20:00" or "08:00")
     * @return Minutes since midnight, or -1 if invalid
     */
    private static int parseTimeToMinutes(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 2) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60 + minutes;
            }
        } catch (Exception e) {
            // Invalid format
        }
        return -1;
    }

    /**
     * Check if a given timestamp is during night time based on preferences.
     * @param timestamp The timestamp to check
     * @param nightStartMinutes Night start time in minutes since midnight
     * @param nightEndMinutes Night end time in minutes since midnight
     * @return true if timestamp is during night time
     */
    private static boolean isNightTime(long timestamp, int nightStartMinutes, int nightEndMinutes) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        int currentMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE);

        // Handle cases where night spans midnight (e.g., 20:00 to 08:00)
        if (nightStartMinutes > nightEndMinutes) {
            return currentMinutes >= nightStartMinutes || currentMinutes < nightEndMinutes;
        } else {
            return currentMinutes >= nightStartMinutes && currentMinutes < nightEndMinutes;
        }
    }

    /**
     * Get the appropriate line color for a given battery level with blending.
     * @param level The battery level (0-100)
     * @param isCharging Whether the battery is charging
     * @param normalColor The normal line color
     * @param lowColor The low battery color
     * @param criticalColor The critical battery color
     * @param chargingColor The charging color
     * @param lowLevel The low battery threshold
     * @param criticalLevel The critical battery threshold
     * @param blendValue The blend range
     * @return The color to use for this battery level
     */
    private static int getBlendedLineColor(int level, boolean isCharging,
                                          int normalColor, int lowColor, int criticalColor, int chargingColor,
                                          int lowLevel, int criticalLevel, int blendValue) {
        if (isCharging) {
            return chargingColor;
        }

        // Pure critical color below critical level
        if (level <= criticalLevel) {
            return criticalColor;
        }

        // Blending between critical and low (above critical level)
        if (level < criticalLevel + blendValue && level <= lowLevel) {
            // Calculate the blend range, limited by the delta between critical and low levels
            int criticalBlendRange = Math.min(blendValue, lowLevel - criticalLevel);

            if (level <= criticalLevel + criticalBlendRange) {
                // Within blend range - blend between critical and low colors
                float ratio = (float)(level - criticalLevel) / criticalBlendRange;
                return blendColors(criticalColor, lowColor, ratio);
            } else {
                // Above blend range - pure low color
                return lowColor;
            }
        }

        // Pure low color at or below low level (and outside critical blend range)
        if (level <= lowLevel) {
            return lowColor;
        }

        // Blending between low and normal (above low level)
        if (level < lowLevel + blendValue) {
            float ratio = (float)(level - lowLevel) / blendValue;
            return blendColors(lowColor, normalColor, ratio);
        }

        // Normal level
        return normalColor;
    }

    public static Picture generateGraphAsPicture(Context context, List<BatteryData> dataPoints, int displayHours, int width, int height) {
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording(width, height);

        drawGraph(context, canvas, dataPoints, null, displayHours, width, height);

        picture.endRecording();
        return picture;
    }

    public static Picture generateGraphAsPicture(Context context, List<BatteryData> dataPoints, List<StatusData> statusData, int displayHours, int width, int height) {
        Picture picture = new Picture();
        Canvas canvas = picture.beginRecording(width, height);

        drawGraph(context, canvas, dataPoints, statusData, displayHours, width, height);

        picture.endRecording();
        return picture;
    }

    private static void drawGraph(Context context, Canvas canvas, List<BatteryData> dataPoints, List<StatusData> statusData, int displayHours, int width, int height) {
        // Get padding and colors from preferences
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        int paddingHorizontalDp = prefs.getInt("horizontal_padding", 40);
        int paddingVerticalDp = prefs.getInt("vertical_padding", 40);
        int backgroundColor = prefs.getInt("main_color", 0x80000000); // Default: 50% transparent black

        // Convert dp to pixels for padding and text sizing
        float density = context.getResources().getDisplayMetrics().density;
        int paddingHorizontal = (int) (paddingHorizontalDp * density);
        int paddingVertical = (int) (paddingVerticalDp * density);
        float labelTextSize = 12 * density;
        float percentTextSize = 16 * density;

        // Get custom colors or use auto-calculated ones
        int textColor;
        int gridColor;

        if (prefs.contains("text_color")) {
            textColor = prefs.getInt("text_color", getContrastingTextColor(backgroundColor));
        } else {
            textColor = getContrastingTextColor(backgroundColor);
        }

        if (prefs.contains("grid_color")) {
            gridColor = prefs.getInt("grid_color", textColor);
        } else {
            gridColor = textColor;
        }

        // Initialize paints
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setStyle(Paint.Style.FILL);

        Paint gridPaint = new Paint();
        gridPaint.setColor(gridColor);
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(labelTextSize);
        textPaint.setAntiAlias(true);

        Paint linePaint = new Paint();
        linePaint.setColor(prefs.getInt("graph_line_color", 0xFF4CAF50)); // Green
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        // Get night time settings
        String nightStartStr = prefs.getString("night_start", "20:00");
        String nightEndStr = prefs.getString("night_end", "08:00");
        int nightStartMinutes = parseTimeToMinutes(nightStartStr);
        int nightEndMinutes = parseTimeToMinutes(nightEndStr);
        if (nightStartMinutes == -1) nightStartMinutes = 20 * 60; // Default 20:00
        if (nightEndMinutes == -1) nightEndMinutes = 8 * 60; // Default 08:00

        int dayFillColor = prefs.getInt("graph_fill_color", 0x33ADD8E6); // 20% transparent light blue
        int nightFillColor = prefs.getInt("graph_night_fill_color", 0x33000080); // 20% transparent dark blue

        Paint fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);

        Paint chargingPaint = new Paint();
        chargingPaint.setColor(prefs.getInt("charging_line_color", 0xFFADD8E6)); // Light blue
        chargingPaint.setStrokeWidth(2f * density);
        chargingPaint.setStyle(Paint.Style.STROKE);
        chargingPaint.setAntiAlias(true);

        Paint batteryLowPaint = new Paint();
        batteryLowPaint.setColor(prefs.getInt("battery_low_color", 0xFFFFFF3A)); // Yellow
        batteryLowPaint.setStrokeWidth(2f * density);
        batteryLowPaint.setStyle(Paint.Style.STROKE);
        batteryLowPaint.setAntiAlias(true);

        Paint batteryCriticalPaint = new Paint();
        batteryCriticalPaint.setColor(prefs.getInt("battery_critical_color", 0xFFFF3C1C)); // Red
        batteryCriticalPaint.setStrokeWidth(2f * density);
        batteryCriticalPaint.setStyle(Paint.Style.STROKE);
        batteryCriticalPaint.setAntiAlias(true);

        // Get battery level thresholds and blend value
        int batteryLowLevel = prefs.getInt("battery_low_level", 30);
        int batteryCriticalLevel = prefs.getInt("battery_critical_level", 15);
        int blendValue = prefs.getInt("battery_blend_value", 10);

        // Get base colors for blending
        int normalColor = prefs.getInt("graph_line_color", 0xFF4CAF50);
        int lowColor = prefs.getInt("battery_low_color", 0xFFFFFF3A);
        int criticalColor = prefs.getInt("battery_critical_color", 0xFFFF3C1C);
        int chargingColor = prefs.getInt("charging_line_color", 0xFFADD8E6);

        // Draw background
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        if (dataPoints == null || dataPoints.isEmpty()) {
            canvas.drawText("No battery data available", width / 2f - 150, height / 2f, textPaint);
            return;
        }

        // Draw grid - horizontal lines for battery percentages
        boolean showYAxisLabels = prefs.getBoolean("show_y_axis_labels", false);
        
        for (int i = 0; i <= 4; i++) {
            float y = paddingVertical + (height - 2 * paddingVertical) * i / 4f;
            canvas.drawLine(paddingHorizontal, y, width - paddingHorizontal, y, gridPaint);

            // Draw percentage labels (right-aligned) if enabled
            if (showYAxisLabels) {
                String label = String.valueOf(100 - i * 25);
                float labelWidth = textPaint.measureText(label);
                canvas.drawText(label, paddingHorizontal + labelWidth + 5, y + 5, textPaint);
            }
        }

        // Draw grid vertical lines for time intervals
        int intervals = Math.min(displayHours / 6, 8);
        for (int i = 0; i <= intervals; i++) {
            float x = paddingHorizontal + (width - 2 * paddingHorizontal) * i / (float) intervals;
            canvas.drawLine(x, paddingVertical, x, height - paddingVertical, gridPaint);
        }

        // Draw battery level graph
        if (dataPoints.size() >= 2) {
            long now = System.currentTimeMillis();
            long timeRange = displayHours * 60 * 60 * 1000L;
            long startTime = now - timeRange;

            // Build fill paths with day/night detection
            Path currentPath = null;
            Boolean isCurrentNight = null;
            List<Path> fillPaths = new ArrayList<>();
            List<Boolean> pathIsNight = new ArrayList<>();

            for (int i = 0; i < dataPoints.size(); i++) {
                BatteryData data = dataPoints.get(i);

                if (data.getTimestamp() < startTime) continue;

                float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (data.getTimestamp() - startTime) / (float) timeRange;
                float y = paddingVertical + (height - 2 * paddingVertical) * (100 - data.getLevel()) / 100f;

                boolean isNight = isNightTime(data.getTimestamp(), nightStartMinutes, nightEndMinutes);

                // Check if we need to start a new path (day/night transition or first point)
                if (isCurrentNight == null || isCurrentNight != isNight) {
                    // Close previous path if exists
                    if (currentPath != null) {
                        currentPath.lineTo(x, y);
                        currentPath.lineTo(x, height - paddingVertical);
                        currentPath.close();
                    }

                    // Start new path
                    currentPath = new Path();
                    currentPath.moveTo(x, height - paddingVertical);
                    currentPath.lineTo(x, y);
                    isCurrentNight = isNight;
                    fillPaths.add(currentPath);
                    pathIsNight.add(isNight);
                } else {
                    currentPath.lineTo(x, y);
                }
            }

            // Close the last path
            if (currentPath != null) {
                BatteryData lastData = dataPoints.get(dataPoints.size() - 1);
                float lastX = paddingHorizontal + (width - 2 * paddingHorizontal) * (lastData.getTimestamp() - startTime) / (float) timeRange;
                currentPath.lineTo(lastX, height - paddingVertical);
                currentPath.close();
            }

            // Draw fill paths with appropriate colors
            for (int i = 0; i < fillPaths.size(); i++) {
                fillPaint.setColor(pathIsNight.get(i) ? nightFillColor : dayFillColor);
                canvas.drawPath(fillPaths.get(i), fillPaint);
            }

            // Create a paint for drawing line segments with blended colors
            Paint segmentPaint = new Paint();
            segmentPaint.setStrokeWidth(2f * density);
            segmentPaint.setStyle(Paint.Style.STROKE);
            segmentPaint.setAntiAlias(true);

            // Draw line segments with blended colors
            Float prevX = null;
            Float prevY = null;

            for (int i = 0; i < dataPoints.size(); i++) {
                BatteryData data = dataPoints.get(i);

                if (data.getTimestamp() < startTime) continue;

                float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (data.getTimestamp() - startTime) / (float) timeRange;
                float y = paddingVertical + (height - 2 * paddingVertical) * (100 - data.getLevel()) / 100f;

                // Draw line segment from previous point to current point with blended color
                if (prevX != null && prevY != null) {
                    // Use the color for the current battery level
                    int blendedColor = getBlendedLineColor(
                        data.getLevel(),
                        data.isCharging(),
                        normalColor,
                        lowColor,
                        criticalColor,
                        chargingColor,
                        batteryLowLevel,
                        batteryCriticalLevel,
                        blendValue
                    );
                    segmentPaint.setColor(blendedColor);
                    canvas.drawLine(prevX, prevY, x, y, segmentPaint);
                }

                prevX = x;
                prevY = y;
            }
        }

        // Draw user_present status bar at the bottom
        if (statusData != null && !statusData.isEmpty()) {
            long now = System.currentTimeMillis();
            long timeRange = displayHours * 60 * 60 * 1000L;
            long startTime = now - timeRange;

            Paint userPresentPaint = new Paint();
            userPresentPaint.setColor(prefs.getInt("user_present_color", 0xFFFFEB3B)); // Yellow
            userPresentPaint.setStyle(Paint.Style.FILL);
            userPresentPaint.setAntiAlias(true);

            // Height of the status bar
            float barHeight = 8 * density;
            float barBottom = height - paddingVertical;
            float barTop = barBottom - barHeight;

            for (StatusData status : statusData) {
                if (!status.getStatusName().equals("user_present")) {
                    continue;
                }

                long statusStart = Math.max(status.getStartTimestamp(), startTime);
                long statusEnd = status.getEndTimestamp();

                // Handle ongoing status
                if (statusEnd == 0 || statusEnd > now) {
                    statusEnd = now;
                }

                // Skip if status is entirely outside the time range
                if (statusStart > now || statusEnd < startTime) {
                    continue;
                }

                // Calculate x positions
                float x1 = paddingHorizontal + (width - 2 * paddingHorizontal) * (statusStart - startTime) / (float) timeRange;
                float x2 = paddingHorizontal + (width - 2 * paddingHorizontal) * (statusEnd - startTime) / (float) timeRange;

                // Draw the filled bar
                canvas.drawRect(x1, barTop, x2, barBottom, userPresentPaint);
            }
        }

        // Draw current battery level
        if (!dataPoints.isEmpty()) {
            BatteryData currentData = dataPoints.get(dataPoints.size() - 1);
            String levelText = currentData.getLevel() + "%";
            if (currentData.isCharging()) {
                levelText += " ⚡";
            }

            textPaint.setTextSize(percentTextSize);
            canvas.drawText(levelText, paddingHorizontal + percentTextSize + (showYAxisLabels ? labelTextSize * 3 : 0), height - paddingVertical - percentTextSize * 0.5f, textPaint);
        }
    }
}
