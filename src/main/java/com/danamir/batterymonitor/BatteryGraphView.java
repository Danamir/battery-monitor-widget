package com.danamir.batterymonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BatteryGraphView extends View {
    private Paint linePaint;
    private Paint fillPaint;
    private Paint gridPaint;
    private Paint textPaint;
    private Paint chargingPaint;
    private List<BatteryData> dataPoints;
    private int displayHours = 48;

    public BatteryGraphView(Context context) {
        super(context);
        init();
    }

    public BatteryGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        linePaint = new Paint();
        linePaint.setColor(ContextCompat.getColor(getContext(), R.color.battery_graph_line));
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(ContextCompat.getColor(getContext(), R.color.battery_graph_fill));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAlpha(100);
        fillPaint.setAntiAlias(true);

        gridPaint = new Paint();
        gridPaint.setColor(ContextCompat.getColor(getContext(), R.color.battery_graph_grid));
        gridPaint.setStrokeWidth(1f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAntiAlias(true);

        textPaint = new Paint();
        textPaint.setColor(ContextCompat.getColor(getContext(), R.color.battery_graph_text));
        textPaint.setTextSize(24f);
        textPaint.setAntiAlias(true);

        chargingPaint = new Paint();
        chargingPaint.setColor(0xFFFFC107); // Amber color for charging
        chargingPaint.setStrokeWidth(2f);
        chargingPaint.setStyle(Paint.Style.STROKE);
        chargingPaint.setAntiAlias(true);
    }

    public void setDataPoints(List<BatteryData> dataPoints, int displayHours) {
        this.dataPoints = dataPoints;
        this.displayHours = displayHours;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints == null || dataPoints.isEmpty()) {
            drawEmptyState(canvas);
            return;
        }

        int width = getWidth();
        int height = getHeight();
        int padding = 40;

        // Draw grid
        drawGrid(canvas, width, height, padding);

        // Draw battery level graph
        drawBatteryGraph(canvas, width, height, padding);

        // Draw current battery level
        drawCurrentLevel(canvas, width, padding);
    }

    private void drawEmptyState(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawText("No battery data available", width / 2f - 150, height / 2f, textPaint);
    }

    private void drawGrid(Canvas canvas, int width, int height, int padding) {
        // Horizontal lines for battery percentages
        for (int i = 0; i <= 4; i++) {
            float y = padding + (height - 2 * padding) * i / 4f;
            canvas.drawLine(padding, y, width - padding, y, gridPaint);

            // Draw percentage labels
            String label = String.valueOf(100 - i * 25);
            canvas.drawText(label, 5, y + 5, textPaint);
        }

        // Vertical lines for time intervals
        int intervals = Math.min(displayHours / 6, 8);
        for (int i = 0; i <= intervals; i++) {
            float x = padding + (width - 2 * padding) * i / (float) intervals;
            canvas.drawLine(x, padding, x, height - padding, gridPaint);
        }
    }

    private void drawBatteryGraph(Canvas canvas, int width, int height, int padding) {
        if (dataPoints.size() < 2) return;

        long now = System.currentTimeMillis();
        long timeRange = displayHours * 60 * 60 * 1000L;
        long startTime = now - timeRange;

        Path linePath = new Path();
        Path fillPath = new Path();
        boolean pathStarted = false;

        for (int i = 0; i < dataPoints.size(); i++) {
            BatteryData data = dataPoints.get(i);

            if (data.getTimestamp() < startTime) continue;

            float x = padding + (width - 2 * padding) * (data.getTimestamp() - startTime) / (float) timeRange;
            float y = padding + (height - 2 * padding) * (100 - data.getLevel()) / 100f;

            if (!pathStarted) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, height - padding);
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
        float lastX = padding + (width - 2 * padding) * (lastData.getTimestamp() - startTime) / (float) timeRange;
        fillPath.lineTo(lastX, height - padding);
        fillPath.close();

        // Draw fill first, then line
        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);
    }

    private void drawCurrentLevel(Canvas canvas, int width, int padding) {
        if (dataPoints.isEmpty()) return;

        BatteryData currentData = dataPoints.get(dataPoints.size() - 1);
        String levelText = currentData.getLevel() + "%";
        if (currentData.isCharging()) {
            levelText += " (Charging)";
        }

        textPaint.setTextSize(32f);
        canvas.drawText(levelText, width - padding - 150, padding - 10, textPaint);
        textPaint.setTextSize(24f);
    }
}
