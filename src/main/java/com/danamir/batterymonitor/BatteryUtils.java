package com.danamir.batterymonitor;

import java.util.List;

public class BatteryUtils {

    /**
     * Calculate the battery discharge rate in percent per hour.
     * Analyzes the most recent continuous discharge period.
     *
     * @param dataPoints List of battery data points
     * @return Battery usage rate in %/h, or null if insufficient data
     */
    public static Double calculateBatteryUsageRateValue(List<BatteryData> dataPoints) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return null;
        }

        // Find the most recent continuous discharge period
        BatteryData endPoint = null;
        BatteryData startPoint = null;

        for (int i = dataPoints.size() - 1; i >= 0; i--) {
            BatteryData point = dataPoints.get(i);

            if (endPoint == null && !point.isCharging()) {
                endPoint = point;
            } else if (endPoint != null && !point.isCharging()) {
                startPoint = point;
            } else if (endPoint != null && point.isCharging()) {
                break;
            }
        }

        if (startPoint == null || endPoint == null) {
            return null;
        }

        long timeDiffMs = endPoint.getTimestamp() - startPoint.getTimestamp();
        int levelDiff = startPoint.getLevel() - endPoint.getLevel();

        // Need at least 10 minutes of data for reasonable calculation
        if (timeDiffMs < 600000 || levelDiff <= 0) {
            return null;
        }

        // Calculate rate per hour
        double hours = timeDiffMs / 3600000.0;
        double ratePerHour = levelDiff / hours;

        return ratePerHour;
    }

    /**
     * Format time estimate to a human-readable string.
     *
     * @param hours Time in hours
     * @return Formatted string like "~2h 15m to 20%" or "~45m to 20%"
     */
    public static String formatTimeEstimate(double hours) {
        if (hours < 0) {
            return "";
        }

        int totalMinutes = (int) Math.round(hours * 60);
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;

        if (h > 0) {
            return String.format("~%dh%02dm to 20%%", h, m);
        } else {
            return String.format("~%dm to 20%%", m);
        }
    }
}
