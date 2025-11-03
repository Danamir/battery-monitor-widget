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
     * Parse human-readable time interval to milliseconds.
     * Supports formats like: "10 seconds", "10s", "20m", "30mn", "60 minutes", "1h", "6 hours"
     * @param intervalStr Time interval string
     * @return Milliseconds, or -1 if invalid
     */
    private static long parseIntervalToMillis(String intervalStr) {
        if (intervalStr == null || intervalStr.trim().isEmpty()) {
            return -1;
        }

        String str = intervalStr.trim().toLowerCase();

        try {
            // Extract number and unit
            String numberPart = "";
            String unitPart = "";

            for (int i = 0; i < str.length(); i++) {
                char c = str.charAt(i);
                if (Character.isDigit(c) || c == '.') {
                    numberPart += c;
                } else if (Character.isLetter(c)) {
                    unitPart = str.substring(i).trim();
                    break;
                } else if (c == ' ' && !numberPart.isEmpty()) {
                    unitPart = str.substring(i).trim();
                    break;
                }
            }

            if (numberPart.isEmpty()) {
                return -1;
            }

            double value = Double.parseDouble(numberPart);

            // Parse unit
            long multiplier;
            if (unitPart.isEmpty() || unitPart.equals("h") || unitPart.equals("hrs") ||
                unitPart.equals("hour") || unitPart.equals("hours")) {
                multiplier = 60 * 60 * 1000L; // hours
            } else if (unitPart.equals("m") || unitPart.equals("mn") || unitPart.equals("min") ||
                       unitPart.equals("minute") || unitPart.equals("minutes")) {
                multiplier = 60 * 1000L; // minutes
            } else if (unitPart.equals("s") || unitPart.equals("sec") || unitPart.equals("second") ||
                       unitPart.equals("seconds")) {
                multiplier = 1000L; // seconds
            } else {
                return -1; // Unknown unit
            }

            return (long) (value * multiplier);
        } catch (Exception e) {
            return -1;
        }
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
        int paddingHorizontalDp = prefs.getInt("horizontal_padding", 0);
        int paddingVerticalDp = prefs.getInt("vertical_padding", 0);
        int backgroundColor = prefs.getInt("main_color", 0x1A000000); // Default: transparent black

        // Convert dp to pixels for padding and text sizing
        float density = context.getResources().getDisplayMetrics().density;
        int paddingHorizontal = (int) (paddingHorizontalDp * density);
        int paddingVertical = (int) (paddingVerticalDp * density);
        float labelTextSize = 12 * density;
        float percentTextSize = 16 * density;

        // Get custom colors or use auto-calculated ones
        int textColor = prefs.getInt("text_color", 0xFFB4B4B4);
        int gridColor = prefs.getInt("grid_color", 0x33CCCCCC);

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
        String nightStartStr = prefs.getString("night_start", "00:00");
        String nightEndStr = prefs.getString("night_end", "06:00");
        int nightStartMinutes = parseTimeToMinutes(nightStartStr);
        int nightEndMinutes = parseTimeToMinutes(nightEndStr);
        if (nightStartMinutes == -1) nightStartMinutes = 0 * 60; // Default 00:00
        if (nightEndMinutes == -1) nightEndMinutes = 6 * 60; // Default 06:00

        int dayFillColor = prefs.getInt("graph_fill_color", 0x66000000); // transparent black
        int nightFillColor = prefs.getInt("graph_night_fill_color", 0x1AB6B6FF); // 20% transparent dark blue

        Paint fillPaint = new Paint();
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);

        Paint chargingPaint = new Paint();
        chargingPaint.setColor(prefs.getInt("charging_line_color", 0xFF09A6D9)); // Light blue
        chargingPaint.setStrokeWidth(2f * density);
        chargingPaint.setStyle(Paint.Style.STROKE);
        chargingPaint.setAntiAlias(true);

        Paint batteryLowPaint = new Paint();
        batteryLowPaint.setColor(prefs.getInt("battery_low_color", 0xFFFFFF23)); // Yellow
        batteryLowPaint.setStrokeWidth(2f * density);
        batteryLowPaint.setStyle(Paint.Style.STROKE);
        batteryLowPaint.setAntiAlias(true);

        Paint batteryCriticalPaint = new Paint();
        batteryCriticalPaint.setColor(prefs.getInt("battery_critical_color", 0xFFFF3B1B)); // Red
        batteryCriticalPaint.setStrokeWidth(2f * density);
        batteryCriticalPaint.setStyle(Paint.Style.STROKE);
        batteryCriticalPaint.setAntiAlias(true);

        // Get battery level thresholds and blend value
        int batteryLowLevel = prefs.getInt("battery_low_level", 30);
        int batteryCriticalLevel = prefs.getInt("battery_critical_level", 15);
        int blendValue = prefs.getInt("battery_blend_value", 10);

        // Get base colors for blending
        int normalColor = prefs.getInt("graph_line_color", 0xFF4CAF50);
        int lowColor = prefs.getInt("battery_low_color", 0xFFFFFF23);
        int criticalColor = prefs.getInt("battery_critical_color", 0xFFFF3B1B);
        int chargingColor = prefs.getInt("charging_line_color", 0xFF09A6D9);

        // Draw background with night time sections
        long now = System.currentTimeMillis();
        long timeRange = displayHours * 60 * 60 * 1000L;
        long startTime = now - timeRange;

        // Draw day background first
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        // Draw night time background sections aligned to absolute time
        Paint nightBackgroundPaint = new Paint();
        nightBackgroundPaint.setColor(nightFillColor);
        nightBackgroundPaint.setStyle(Paint.Style.FILL);

        if (nightStartMinutes == nightEndMinutes) {
            // Equal start and end times: alternating 24-hour periods starting at the defined time
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(startTime);
            cal.set(java.util.Calendar.HOUR_OF_DAY, nightStartMinutes / 60);
            cal.set(java.util.Calendar.MINUTE, nightStartMinutes % 60);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);

            // Go back to find the first period boundary before startTime
            while (cal.getTimeInMillis() > startTime) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
            }

            // Determine if we should start with night (odd periods) or day (even periods)
            int periodIndex = 0;
            while (cal.getTimeInMillis() <= now) {
                long periodStart = cal.getTimeInMillis();
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
                long periodEnd = cal.getTimeInMillis();

                // Draw night background for odd periods (1st, 3rd, 5th, etc.)
                if (periodIndex % 2 == 1) {
                    long visibleStart = Math.max(periodStart, startTime);
                    long visibleEnd = Math.min(periodEnd, now);

                    if (visibleStart < visibleEnd) {
                        float x1 = paddingHorizontal + (width - 2 * paddingHorizontal) * (visibleStart - startTime) / (float) timeRange;
                        float x2 = paddingHorizontal + (width - 2 * paddingHorizontal) * (visibleEnd - startTime) / (float) timeRange;
                        canvas.drawRect(x1, 0, x2, height, nightBackgroundPaint);
                    }
                }

                periodIndex++;
            }
        } else {
            // Normal mode: night period between start and end times
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(startTime);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);

            // Go back one day to catch any night period that might overlap
            cal.add(java.util.Calendar.DAY_OF_MONTH, -1);

            while (cal.getTimeInMillis() <= now) {
                // Calculate night start and end for this day
                java.util.Calendar nightStart = (java.util.Calendar) cal.clone();
                nightStart.set(java.util.Calendar.HOUR_OF_DAY, nightStartMinutes / 60);
                nightStart.set(java.util.Calendar.MINUTE, nightStartMinutes % 60);

                java.util.Calendar nightEnd = (java.util.Calendar) cal.clone();
                if (nightStartMinutes > nightEndMinutes) {
                    // Night spans midnight - end is next day
                    nightEnd.add(java.util.Calendar.DAY_OF_MONTH, 1);
                }
                nightEnd.set(java.util.Calendar.HOUR_OF_DAY, nightEndMinutes / 60);
                nightEnd.set(java.util.Calendar.MINUTE, nightEndMinutes % 60);

                long nightStartTime = nightStart.getTimeInMillis();
                long nightEndTime = nightEnd.getTimeInMillis();

                // Clip to visible time range
                long visibleNightStart = Math.max(nightStartTime, startTime);
                long visibleNightEnd = Math.min(nightEndTime, now);

                // Draw night section if it's visible
                if (visibleNightStart < visibleNightEnd) {
                    float x1 = paddingHorizontal + (width - 2 * paddingHorizontal) * (visibleNightStart - startTime) / (float) timeRange;
                    float x2 = paddingHorizontal + (width - 2 * paddingHorizontal) * (visibleNightEnd - startTime) / (float) timeRange;
                    canvas.drawRect(x1, 0, x2, height, nightBackgroundPaint);
                }

                // Move to next day
                cal.add(java.util.Calendar.DAY_OF_MONTH, 1);
            }
        }

        if (dataPoints == null || dataPoints.isEmpty()) {
            canvas.drawText("No battery data available", width / 2f - 150, height / 2f, textPaint);
            return;
        }

        // Draw grid - horizontal lines for battery percentages
        boolean showYAxisLabels = prefs.getBoolean("show_y_axis_labels", false);
        int horizontalInterval = prefs.getInt("gridHorizontalIntervalPref", 25);

        // Ensure valid interval (avoid division by zero)
        if (horizontalInterval <= 0) {
            horizontalInterval = 25;
        }

        // Draw horizontal grid lines at specified percentage intervals
        for (int percentage = 0; percentage <= 100; percentage += horizontalInterval) {
            float y = paddingVertical + (height - 2 * paddingVertical) * (100 - percentage) / 100f;
            canvas.drawLine(paddingHorizontal, y, width - paddingHorizontal, y, gridPaint);

            // Draw percentage labels (right-aligned) if enabled
            if (showYAxisLabels) {
                String label = String.valueOf(percentage);
                float labelWidth = textPaint.measureText(label);
                canvas.drawText(label, paddingHorizontal + labelWidth + 5, y + 5, textPaint);
            }
        }

        // Draw grid vertical lines for time intervals
        boolean staticGrid = prefs.getBoolean("staticGridPref", true);

        // Parse grid interval from human-readable format
        String intervalStr = prefs.getString("gridVerticalIntervalPref", "12 hours");
        long intervalMillis = parseIntervalToMillis(intervalStr);
        if (intervalMillis <= 0) {
            intervalMillis = 6 * 60 * 60 * 1000L; // Default to 6 hours
        }

        // Get subdivision count
        int intervalSubdivisionCount = Math.max(1, prefs.getInt("gridVerticalIntervalSubdivisionPref", 2));

        // Create paint for subdivision grid lines (thinner and dashed)
        Paint subGridPaint = null;
        if (intervalSubdivisionCount > 1) {
            subGridPaint = new Paint();
            subGridPaint.setColor(gridColor);
            subGridPaint.setStrokeWidth(0.8f * density);
            subGridPaint.setStyle(Paint.Style.STROKE);
            subGridPaint.setAntiAlias(true);
            subGridPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{3 * density, 3 * density}, 0));
        }

        now = System.currentTimeMillis();
        timeRange = displayHours * 60 * 60 * 1000L;
        startTime = now - timeRange;

        if (staticGrid) {
            // Static grid: evenly spaced lines
            int intervals = Math.max(1, (int) (timeRange / intervalMillis));
            intervals = Math.min(intervals, 100); // Cap at 100 intervals
            for (int i = 0; i <= intervals; i++) {
                float x = paddingHorizontal + (width - 2 * paddingHorizontal) * i / (float) intervals;
                canvas.drawLine(x, paddingVertical, x, height - paddingVertical, gridPaint);

                // Draw interval subdivision lines
                if (subGridPaint != null && i < intervals) {
                    for (int j = 1; j < intervalSubdivisionCount; j++) {
                        float subX = paddingHorizontal + (width - 2 * paddingHorizontal) * (i + j / (float)intervalSubdivisionCount) / (float) intervals;
                        canvas.drawLine(subX, paddingVertical, subX, height - paddingVertical, subGridPaint);
                    }
                }
            }
        } else {
            // Time-aligned grid: lines at regular time marks
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.setTimeInMillis(startTime);

            // Convert interval to hours for alignment (if >= 1 hour)
            if (intervalMillis >= 60 * 60 * 1000L) {
                int intervalHours = (int) (intervalMillis / (60 * 60 * 1000L));

                // Round down to the previous interval mark
                int currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY);
                int alignedHour = (currentHour / intervalHours) * intervalHours;
                cal.set(java.util.Calendar.HOUR_OF_DAY, alignedHour);
                cal.set(java.util.Calendar.MINUTE, 0);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);

                // Draw grid lines at specified intervals
                while (cal.getTimeInMillis() <= now) {
                    long gridTime = cal.getTimeInMillis();
                    if (gridTime >= startTime) {
                        float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (gridTime - startTime) / (float) timeRange;
                        canvas.drawLine(x, paddingVertical, x, height - paddingVertical, gridPaint);

                        // Draw interval subdivision lines
                        if (subGridPaint != null) {
                            long subIntervalMillis = intervalMillis / intervalSubdivisionCount;
                            for (int j = 1; j < intervalSubdivisionCount; j++) {
                                long subGridTime = gridTime + j * subIntervalMillis;
                                if (subGridTime >= startTime && subGridTime <= now) {
                                    float subX = paddingHorizontal + (width - 2 * paddingHorizontal) * (subGridTime - startTime) / (float) timeRange;
                                    canvas.drawLine(subX, paddingVertical, subX, height - paddingVertical, subGridPaint);
                                }
                            }
                        }
                    }
                    cal.add(java.util.Calendar.HOUR_OF_DAY, intervalHours);
                }
            } else {
                // For intervals < 1 hour, use millisecond-based alignment
                // Round down to the previous interval mark
                long alignedTime = (startTime / intervalMillis) * intervalMillis;

                // Draw grid lines at specified intervals
                for (long gridTime = alignedTime; gridTime <= now; gridTime += intervalMillis) {
                    if (gridTime >= startTime) {
                        float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (gridTime - startTime) / (float) timeRange;
                        canvas.drawLine(x, paddingVertical, x, height - paddingVertical, gridPaint);

                        // Draw interval subdivision lines
                        if (subGridPaint != null) {
                            long subIntervalMillis = intervalMillis / intervalSubdivisionCount;
                            for (int j = 1; j < intervalSubdivisionCount; j++) {
                                long subGridTime = gridTime + j * subIntervalMillis;
                                if (subGridTime >= startTime && subGridTime <= now) {
                                    float subX = paddingHorizontal + (width - 2 * paddingHorizontal) * (subGridTime - startTime) / (float) timeRange;
                                    canvas.drawLine(subX, paddingVertical, subX, height - paddingVertical, subGridPaint);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Draw battery level graph
        if (dataPoints.size() >= 2) {
            now = System.currentTimeMillis();
            timeRange = displayHours * 60 * 60 * 1000L;
            startTime = now - timeRange;

            // Build fill path (single color)
            Path fillPath = new Path();
            boolean firstPoint = true;

            for (int i = 0; i < dataPoints.size(); i++) {
                BatteryData data = dataPoints.get(i);

                if (data.getTimestamp() < startTime) continue;

                float x = paddingHorizontal + (width - 2 * paddingHorizontal) * (data.getTimestamp() - startTime) / (float) timeRange;
                float y = paddingVertical + (height - 2 * paddingVertical) * (100 - data.getLevel()) / 100f;

                if (firstPoint) {
                    fillPath.moveTo(x, height - paddingVertical);
                    fillPath.lineTo(x, y);
                    firstPoint = false;
                } else {
                    fillPath.lineTo(x, y);
                }
            }

            // Close the path
            if (!firstPoint) {
                BatteryData lastData = dataPoints.get(dataPoints.size() - 1);
                float lastX = paddingHorizontal + (width - 2 * paddingHorizontal) * (lastData.getTimestamp() - startTime) / (float) timeRange;
                fillPath.lineTo(lastX, height - paddingVertical);
                fillPath.close();
            }

            // Draw fill with day color only
            fillPaint.setColor(dayFillColor);
            canvas.drawPath(fillPath, fillPaint);

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
            now = System.currentTimeMillis();
            timeRange = displayHours * 60 * 60 * 1000L;
            startTime = now - timeRange;

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
