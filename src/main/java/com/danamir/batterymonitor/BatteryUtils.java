package com.danamir.batterymonitor;

import java.util.List;

public class BatteryUtils {

    /**
     * Calculate the battery discharge rate in percent per hour.
     * Analyzes the most recent continuous discharge period.
     *
     * @param dataPoints List of battery data points
     * @param minDuration Minimum duration to do the calculation
     * @return Battery usage rate in %/h, or null if insufficient data
     */
    public static Double calculateBatteryUsageRateValue(List<BatteryData> dataPoints, int minDuration) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return null;
        }

        // Check if the last data point is charging
        BatteryData lastPoint = dataPoints.get(dataPoints.size() - 1);
        boolean isChargingPeriod = lastPoint.isCharging();
        int differentPeriodAllowed = 10;

        // Find the most recent continuous discharge or charge period
        BatteryData endPoint = null;
        BatteryData startPoint = null;

        for (int i = dataPoints.size() - 1; i >= 0; i--) {
            BatteryData point = dataPoints.get(i);

            if (endPoint == null && point.isCharging() == isChargingPeriod) {
                endPoint = point;
            } else if (endPoint != null && point.isCharging() == isChargingPeriod) {
                startPoint = point;
            } else if (endPoint != null && point.isCharging() != isChargingPeriod) {
                differentPeriodAllowed--;
                if (differentPeriodAllowed < 0) {
                    break;
                }
            }
        }

        if (startPoint == null || endPoint == null) {
            return null;
        }

        long timeDiffMs = endPoint.getTimestamp() - startPoint.getTimestamp();
        int levelDiff;

        if (isChargingPeriod) {
            // For charging, level increases over time
            levelDiff = endPoint.getLevel() - startPoint.getLevel();
        } else {
            // For discharging, level decreases over time
            levelDiff = startPoint.getLevel() - endPoint.getLevel();
        }

        // Need at least that many minutes of data for reasonable calculation
        if (timeDiffMs < minDuration * 60 * 1000 || levelDiff <= 0) {
            return null;
        }

        // Calculate rate per hour
        double hours = timeDiffMs / 3600000.0;
        double ratePerHour = levelDiff / hours;

        return ratePerHour;
    }

    /**
     * Calculate target battery percentage based on current level and charging status.
     *
     * @param lowTargetPercent  Low target percentage (typically 20%)
     * @param highTargetPercent High target percentage (typically 80%)
     * @param currentLevel      Current battery level
     * @param isCharging        Whether the battery is currently charging
     * @return Target percentage to reach
     */
    public static int getTargetPercent(int lowTargetPercent, int highTargetPercent, int currentLevel, boolean isCharging) {
        if (isCharging) {
            if (currentLevel > highTargetPercent) {
                return 100;
            } else {
                return highTargetPercent;
            }
        } else if (currentLevel < lowTargetPercent) {
            return 0;
        }
        return lowTargetPercent;
    }

    /**
     * Format time estimate to a human-readable string.
     *
     * @param hours Time in hours
     * @param targetPercent Target battery percentage to display
     * @return Formatted string like "~2h15m to 20%" or "~45m to Empty"
     */
    public static String formatTimeEstimate(double hours, Integer targetPercent) {
        if (hours < 0) {
            return "";
        }

        int totalMinutes = (int) Math.round(hours * 60);
        int h = totalMinutes / 60;
        int m = totalMinutes % 60;

        String targetPercentStr = "";
        if (targetPercent != null && targetPercent > 0 && targetPercent < 100) {
            targetPercentStr = String.format(" to %d%%", targetPercent);
        } else if (targetPercent != null && targetPercent == 100) {
            targetPercentStr = " to Full";
        } else if (targetPercent != null && targetPercent == 0) {
            targetPercentStr = " to Empty";
        }

        if (h >= 24) {
            int d = h / 24;
            h = h % 24;
            if(h == 0) {
                return String.format("~%dd%s", d, targetPercentStr);
            }
            return String.format("~%dd %dh%s", d, h, targetPercentStr);
        } else if (h > 0) {
            return String.format("~%dh%02dm%s", h, m, targetPercentStr);
        } else {
            return String.format("~%dm%s", m, targetPercentStr);
        }
    }
}
