package com.danamir.batterymonitor;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;

import androidx.core.content.ContextCompat;

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

        drawGraph(context, canvas, dataPoints, displayHours, width, height);

        picture.endRecording();
        return picture;
    }

    public static Bitmap generateGraphAsBitmap(Context context, List<BatteryData> dataPoints, int displayHours, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        drawGraph(context, canvas, dataPoints, displayHours, width, height);

        return bitmap;
    }

    private static void drawGraph(Context context, Canvas canvas, List<BatteryData> dataPoints, int displayHours, int width, int height) {
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
        linePaint.setColor(ContextCompat.getColor(context, R.color.battery_graph_line));
        linePaint.setStrokeWidth(3f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        Paint fillPaint = new Paint();
        fillPaint.setColor(ContextCompat.getColor(context, R.color.battery_graph_fill));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAlpha(100);
        fillPaint.setAntiAlias(true);

        Paint chargingPaint = new Paint();
        chargingPaint.setColor(0xFFFFC107); // Amber color
        chargingPaint.setStrokeWidth(2f * density);
        chargingPaint.setStyle(Paint.Style.STROKE);
        chargingPaint.setAntiAlias(true);

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

            Path linePath = new Path();
            Path fillPath = new Path();
            boolean pathStarted = false;

            for (int i = 0; i < dataPoints.size(); i++) {
                BatteryData data = dataPoints.get(i);

                if (data.getTimestamp() < startTime) continue;

                float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (data.getTimestamp() - startTime) / (float) timeRange;
                float y = paddingVertical + (height - 2 * paddingVertical) * (100 - data.getLevel()) / 100f;

                if (!pathStarted) {
                    linePath.moveTo(x, y);
                    fillPath.moveTo(x, height - paddingVertical);
                    fillPath.lineTo(x, y);
                    pathStarted = true;
                } else {
                    linePath.lineTo(x, y);
                    fillPath.lineTo(x, y);
                }

                // Draw charging indicator
                if (data.isCharging()) {
                    canvas.drawCircle(x, y, 4, chargingPaint);
                }
            }

            // Close fill path
            BatteryData lastData = dataPoints.get(dataPoints.size() - 1);
            float lastX = paddingHorizontal + (width - 2 * paddingHorizontal) * (lastData.getTimestamp() - startTime) / (float) timeRange;
            fillPath.lineTo(lastX, height - paddingVertical);
            fillPath.close();

            // Draw fill first, then line
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);
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
