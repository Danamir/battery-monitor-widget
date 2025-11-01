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

        Paint fillPaint = new Paint();
        fillPaint.setColor(prefs.getInt("graph_fill_color", 0x33ADD8E6)); // 20% transparent light blue
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

        // Get battery level thresholds
        int batteryLowLevel = prefs.getInt("battery_low_level", 30);
        int batteryCriticalLevel = prefs.getInt("battery_critical_level", 15);

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
                canvas.drawText(label, paddingHorizontal - labelWidth - 5, y + 5, textPaint);
            }
        }

        // Draw grid - vertical lines for time intervals
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

            List<Path> linePaths = new ArrayList<>();
            List<Integer> lineStates = new ArrayList<>(); // 0=normal, 1=charging, 2=low, 3=critical
            Path fillPath = new Path();

            Path currentLinePath = null;
            int currentState = -1;
            boolean pathStarted = false;

            for (int i = 0; i < dataPoints.size(); i++) {
                BatteryData data = dataPoints.get(i);

                if (data.getTimestamp() < startTime) continue;

                float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (data.getTimestamp() - startTime) / (float) timeRange;
                float y = paddingVertical + (height - 2 * paddingVertical) * (100 - data.getLevel()) / 100f;

                // Determine battery state: charging, critical, low, or normal
                int batteryState;
                if (data.isCharging()) {
                    batteryState = 1; // Charging
                } else if (data.getLevel() < batteryCriticalLevel) {
                    batteryState = 3; // Critical
                } else if (data.getLevel() < batteryLowLevel) {
                    batteryState = 2; // Low
                } else {
                    batteryState = 0; // Normal
                }

                // Detect state change or path start
                if (!pathStarted || batteryState != currentState) {
                    // End previous path if exists
                    if (pathStarted) {
                        // Add the current point to the previous path for continuity
                        currentLinePath.lineTo(x, y);
                    }

                    // Start new line path
                    currentLinePath = new Path();
                    currentLinePath.moveTo(x, y);
                    currentState = batteryState;

                    linePaths.add(currentLinePath);
                    lineStates.add(currentState);

                    // Add to fill path
                    if (!pathStarted) {
                        fillPath.moveTo(x, height - paddingVertical);
                        fillPath.lineTo(x, y);
                    } else {
                        fillPath.lineTo(x, y);
                    }

                    pathStarted = true;
                } else {
                    currentLinePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }
            }

            // Close the fill path
            if (pathStarted) {
                BatteryData lastData = dataPoints.get(dataPoints.size() - 1);
                float lastX = paddingHorizontal + (width - 2 * paddingHorizontal) * (lastData.getTimestamp() - startTime) / (float) timeRange;
                fillPath.lineTo(lastX, height - paddingVertical);
                fillPath.close();
            }

            // Draw fill path first
            canvas.drawPath(fillPath, fillPaint);

            // Draw line paths with appropriate color based on battery state
            for (int i = 0; i < linePaths.size(); i++) {
                Paint paint;
                int state = lineStates.get(i);
                switch (state) {
                    case 1: // Charging
                        paint = chargingPaint;
                        break;
                    case 2: // Low battery
                        paint = batteryLowPaint;
                        break;
                    case 3: // Critical battery
                        paint = batteryCriticalPaint;
                        break;
                    default: // Normal
                        paint = linePaint;
                        break;
                }
                canvas.drawPath(linePaths.get(i), paint);
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
            canvas.drawText(levelText, paddingHorizontal + percentTextSize, height - paddingVertical - percentTextSize * 0.5f, textPaint);
        }
    }
}
