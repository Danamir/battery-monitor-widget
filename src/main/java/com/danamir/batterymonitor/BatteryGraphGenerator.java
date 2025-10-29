package com.danamir.batterymonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Picture;

import androidx.core.content.ContextCompat;

import java.util.List;

public class BatteryGraphGenerator {

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
        // Get padding and background color from preferences
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        int paddingHorizontalDp = Integer.parseInt(prefs.getString("horizontal_padding", "20"));
        int paddingVerticalDp = Integer.parseInt(prefs.getString("vertical_padding", "10"));
        int backgroundColor = prefs.getInt("main_color", 0x80000000); // Default: 50% transparent black

        // Convert dp to pixels for padding and text sizing
        float density = context.getResources().getDisplayMetrics().density;
        int paddingHorizontal = (int) (paddingHorizontalDp * density);
        int paddingVertical = (int) (paddingVerticalDp * density);
        float labelTextSize = 12 * density;
        float percentTextSize = 16 * density;

        // Initialize paints
        Paint backgroundPaint = new Paint();
        backgroundPaint.setColor(backgroundColor);
		backgroundPaint.setStyle(Paint.Style.FILL);

        Paint gridPaint = new Paint();
        gridPaint.setColor(ContextCompat.getColor(context, R.color.battery_graph_grid));
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(context, R.color.battery_graph_text));
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
        for (int i = 0; i <= 4; i++) {
            float y = paddingVertical + (height - 2 * paddingVertical) * i / 4f;
            canvas.drawLine(paddingHorizontal, y, width - paddingHorizontal, y, gridPaint);

            // Draw percentage labels (right-aligned)
            String label = String.valueOf(100 - i * 25);
            float labelWidth = textPaint.measureText(label);
            canvas.drawText(label, paddingHorizontal - labelWidth - 5, y + 5, textPaint);
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
            float textWidth = textPaint.measureText(levelText);
            canvas.drawText(levelText, width - paddingHorizontal - textWidth, paddingVertical + 40, textPaint);
        }
    }
}
