package com.danamir.batterymonitor;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class BatteryUtils {
    public static final String TEXT_SEPARATOR = "  •  ";
    public static final String TEXT_SEPARATOR_ALT = "  —  ";
    // public static final String TEXT_SEPARATOR = "  |  ";

    /**
     * Calculate the battery discharge rate in percent per hour.
     * Analyzes the most recent continuous discharge period.
     *
     * @param dataPoints List of battery data points
     * @param minDuration Minimum duration to do the calculation
     * @param maxDuration Maximum duration to do the calculation (optional)
     * @return Battery usage rate in %/h, or null if insufficient data
     */
    public static Double calculateBatteryUsageRateValue(List<BatteryData> dataPoints, int minDuration, Integer maxDuration) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return null;
        }

        // Check if the last data point is charging
        BatteryData lastPoint = dataPoints.get(dataPoints.size() - 1);
        boolean isChargingPeriod = lastPoint.isCharging();
        int differentPeriodsAllowed = 1;
        if (maxDuration != null) {
            // Give more leeway to find a concurrent dataPoints period if maxDuration is defined
            differentPeriodsAllowed = 10;
        }

        // Find the most recent continuous discharge or charge period
        BatteryData endPoint = null;
        BatteryData startPoint = null;

        for (int i = dataPoints.size() - 1; i >= 0; i--) {
            BatteryData point = dataPoints.get(i);

            if (endPoint == null && point.isCharging() == isChargingPeriod) {
                endPoint = point;
            } else if (endPoint != null && point.isCharging() == isChargingPeriod) {
                startPoint = point;
                // Check if we've reached maxDuration
                long currentTimeDiff = endPoint.getTimestamp() - startPoint.getTimestamp();
                if (maxDuration != null && currentTimeDiff >= maxDuration * 60 * 1000) {
                    break;
                }
            } else if (endPoint != null && point.isCharging() != isChargingPeriod) {
                differentPeriodsAllowed--;
                if (differentPeriodsAllowed < 0) {
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

        // put debug here
        boolean debug = false;
        if (debug) {
            android.util.Log.d("BatteryUtils", "=== Battery Calculation Debug ===");
            android.util.Log.d("BatteryUtils", "isChargingPeriod: " + isChargingPeriod);
            android.util.Log.d("BatteryUtils", "startPoint.timestamp: " + new java.util.Date(startPoint.getTimestamp()));
            android.util.Log.d("BatteryUtils", "startPoint.level: " + startPoint.getLevel() + "%");
            android.util.Log.d("BatteryUtils", "startPoint.isCharging: " + startPoint.isCharging());
            android.util.Log.d("BatteryUtils", "endPoint.timestamp: " + new java.util.Date(endPoint.getTimestamp()));
            android.util.Log.d("BatteryUtils", "endPoint.level: " + endPoint.getLevel() + "%");
            android.util.Log.d("BatteryUtils", "endPoint.isCharging: " + endPoint.isCharging());
            android.util.Log.d("BatteryUtils", "timeDiffMs: " + (timeDiffMs / 60000.0) + " minutes, " + (timeDiffMs / 3600000.0) + " hours");
            android.util.Log.d("BatteryUtils", "levelDiff: " + levelDiff + "%");
            android.util.Log.d("BatteryUtils", "minDuration: " + minDuration + " minutes");
            android.util.Log.d("BatteryUtils", "maxDuration: " + maxDuration + " minutes");
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
     * Calculate the battery discharge rate in percent per hour since the maximum charge level.
     * This method analyzes data points from the nearest charge >= target charge, or from the max charge level found.
     * If a limited charging period (< target charge) is present, the gained charge is subtracted from the calculation.
     *
     * @param dataPoints List of battery data points
     * @param minDuration Minimum duration to do the calculation (in minutes)
     * @param targetCharge Target charge percentage to look for (e.g., 80%)
     * @return Battery usage rate in %/h, or null if insufficient data
     */
    public static Double calculateBatteryUsageRateValueSinceMax(List<BatteryData> dataPoints, int minDuration, int targetCharge) {
        if (dataPoints == null || dataPoints.size() < 2) {
            return null;
        }

        BatteryData lastPoint = dataPoints.get(dataPoints.size() - 1);
        
        // If currently charging, return null
        if (lastPoint.isCharging()) {
            return null;
        }

        BatteryData maxChargePoint = null;
        int maxChargeLevel = 0;
        BatteryData targetChargePoint = null;
        
        // Find the nearest charge >= targetCharge, or the max charge level
        for (int i = dataPoints.size() - 1; i >= 0; i--) {
            BatteryData point = dataPoints.get(i);
            
            // Track the maximum charge level found
            if (point.getLevel() > maxChargeLevel) {
                maxChargeLevel = point.getLevel();
                maxChargePoint = point;
            }
            
            // Look for a charging period that reached or exceeded target charge
            if (point.isCharging() && point.getLevel() >= targetCharge) {
                targetChargePoint = point;
                break;
            }
        }
        
        // Use targetChargePoint if found, otherwise use maxChargePoint
        BatteryData startPoint = (targetChargePoint != null) ? targetChargePoint : maxChargePoint;
        
        if (startPoint == null) {
            return null;
        }
        
        // Calculate base time and level differences
        long timeDiffMs = lastPoint.getTimestamp() - startPoint.getTimestamp();
        int levelDiff = startPoint.getLevel() - lastPoint.getLevel();
        
        // Check minimum duration requirement
        if (timeDiffMs < minDuration * 60 * 1000) {
            return null;
        }
        
        // Find and subtract any charging periods between startPoint and lastPoint
        int chargeGained = 0;
        long chargeTimeDuration = 0;
        
        for (int i = 0; i < dataPoints.size() - 1; i++) {
            BatteryData current = dataPoints.get(i);
            BatteryData next = dataPoints.get(i + 1);
            
            // Only consider data points between startPoint and lastPoint
            if (current.getTimestamp() < startPoint.getTimestamp()) {
                continue;
            }
            if (current.getTimestamp() > lastPoint.getTimestamp()) {
                break;
            }
            
            // If this is a charging period (and it didn't reach target charge)
            if (current.isCharging() && next.getLevel() < targetCharge) {
                int levelIncrease = next.getLevel() - current.getLevel();
                if (levelIncrease > 0) {
                    chargeGained += levelIncrease;
                    chargeTimeDuration += (next.getTimestamp() - current.getTimestamp());
                }
            }
        }
        
        // Adjust the level difference by subtracting charge gained during limited charging periods
        int adjustedLevelDiff = levelDiff + chargeGained;
        long adjustedTimeDiffMs = timeDiffMs - chargeTimeDuration;
        
        // Debug logging
        boolean debug = false;
        if (debug) {
            android.util.Log.d("BatteryUtils", "=== Battery Usage Since Max Debug ===");
            android.util.Log.d("BatteryUtils", "startPoint: " + new java.util.Date(startPoint.getTimestamp()) + " @ " + startPoint.getLevel() + "%");
            android.util.Log.d("BatteryUtils", "lastPoint: " + new java.util.Date(lastPoint.getTimestamp()) + " @ " + lastPoint.getLevel() + "%");
            android.util.Log.d("BatteryUtils", "targetCharge: " + targetCharge + "%");
            android.util.Log.d("BatteryUtils", "maxChargeLevel: " + maxChargeLevel + "%");
            android.util.Log.d("BatteryUtils", "usedTargetChargePoint: " + (targetChargePoint != null));
            android.util.Log.d("BatteryUtils", "timeDiffMs: " + (timeDiffMs / 60000.0) + " minutes");
            android.util.Log.d("BatteryUtils", "levelDiff: " + levelDiff + "%");
            android.util.Log.d("BatteryUtils", "chargeGained: " + chargeGained + "%");
            android.util.Log.d("BatteryUtils", "chargeTimeDuration: " + (chargeTimeDuration / 60000.0) + " minutes");
            android.util.Log.d("BatteryUtils", "adjustedLevelDiff: " + adjustedLevelDiff + "%");
            android.util.Log.d("BatteryUtils", "adjustedTimeDiffMs: " + (adjustedTimeDiffMs / 60000.0) + " minutes");
        }
        
        // Ensure we have valid data after adjustments
        if (adjustedTimeDiffMs < minDuration * 60 * 1000 || adjustedLevelDiff <= 0) {
            return null;
        }
        
        // Calculate rate per hour
        double hours = adjustedTimeDiffMs / 3600000.0;
        double ratePerHour = adjustedLevelDiff / hours;
        
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
            targetPercentStr = String.format(Locale.getDefault(), " to %d%%", targetPercent);
        } else if (targetPercent != null && targetPercent == 100) {
            targetPercentStr = " to Full";
        } else if (targetPercent != null && targetPercent == 0) {
            targetPercentStr = " to Empty";
        }

        if (h >= 24) {
            int d = h / 24;
            h = h % 24;
            if(h == 0) {
                return String.format(Locale.getDefault(), "%dd%s", d, targetPercentStr);
            }
            return String.format(Locale.getDefault(), "%dd %dh%s", d, h, targetPercentStr);
        } else if (h > 0) {
            return String.format(Locale.getDefault(), "%dh%02dm%s", h, m, targetPercentStr);
        } else {
            return String.format(Locale.getDefault(), "%dm%s", m, targetPercentStr);
        }
    }

    /**
     * Format duration end time to a human-readable string showing time of day.
     *
     * @param hours Time duration in hours from now
     * @return Formatted string like "14:30" if today, or "Mon. @ 14:30" if another day
     */
    public static String formatDurationEndTime(double hours) {
        Calendar now = Calendar.getInstance();
        Calendar endTime = Calendar.getInstance();
        endTime.add(Calendar.MILLISECOND, (int) Math.round(hours * 3600000));

        SimpleDateFormat timeFormat = new SimpleDateFormat("H:mm", Locale.getDefault());

        // Check if the end time falls on the same day
        boolean isSameDay = now.get(Calendar.YEAR) == endTime.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == endTime.get(Calendar.DAY_OF_YEAR);

        if (isSameDay) {
            return timeFormat.format(endTime.getTime());
        } else {
            SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
            return dayFormat.format(endTime.getTime()) + " @ " + timeFormat.format(endTime.getTime());
        }
    }

    /**
     * Calculate battery usage values for display in notification or graph.
     * This method fetches up-to-date data and settings to ensure consistency.
     *
     * @param context The application context
     * @return Map with keys: "usage_rate", "charging", "hours_to", "time_to"
     */
    public static Map<String, String> calculateValues(Context context) {
        return calculateValues(context, false);
    }

    /**
     * Calculate battery usage values for display in notification or graph.
     * This method fetches up-to-date data and settings to ensure consistency.
     *
     * @param context         The application context
     * @param includeSinceMax Whether to include usage rate calculations since max charge level
     * @return Map with keys: "usage_rate", "charging", "hours_to", "time_to"
     */
    public static Map<String, String> calculateValues(Context context, boolean includeSinceMax) {
        Map<String, String> values = new HashMap<>();

        // Get preferences
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context);
        int lowTargetPercent = prefs.getInt("low_target_percent", 20);
        int highTargetPercent = prefs.getInt("high_target_percent", 80);
        int displayLengthHours = Integer.parseInt(prefs.getString("display_length_hours", "48"));
        int maxDuration = prefs.getInt("usage_calculation_time", 10);
        int minDuration = Math.min(maxDuration, 10);
        values.put("calculation_duration", maxDuration+"m");

        // Get up-to-date data points
        BatteryDataManager dataManager = BatteryDataManager.getInstance(context);
        List<BatteryData> dataPoints = dataManager.getDataPoints(displayLengthHours);

        if (dataPoints == null || dataPoints.isEmpty()) {
            // No data available
            values.put("usage_rate", "");
            values.put("charging", "false");
            values.put("current_percent", "-%");
            values.put("current_level", "");
            values.put("hours_to", "");
            values.put("time_to", "");

            values.put("usage_rate_since_max", "");
            values.put("hours_to_since_max", "");
            values.put("time_to_since_max", "");
            return values;
        }

        // Get the last data point for current status
        BatteryData lastPoint = dataPoints.get(dataPoints.size() - 1);
        int currentBatteryLevel = lastPoint.getLevel();
        boolean isCharging = lastPoint.isCharging();

        values.put("current_level", String.valueOf(currentBatteryLevel));
        values.put("current_percent", currentBatteryLevel + "%");
        values.put("charging", String.valueOf(isCharging));

        // Calculate target percent
        int targetPercent = getTargetPercent(lowTargetPercent, highTargetPercent, currentBatteryLevel, isCharging);

        // Calculate usage rate
        Double usageRateValue = calculateBatteryUsageRateValue(dataPoints, minDuration, maxDuration);
        if (usageRateValue != null) {
            values.put("usage_rate", String.format(Locale.getDefault(), "%.1f", usageRateValue));

            // Calculate time estimates
            double hoursToLevel = Math.abs(currentBatteryLevel - targetPercent) / usageRateValue;
            String hoursTo = formatTimeEstimate(hoursToLevel, targetPercent);
            String timeTo = formatDurationEndTime(hoursToLevel);

            values.put("hours_to", hoursTo);
            values.put("time_to", timeTo);
        } else {
            values.put("usage_rate", "");
            values.put("hours_to", "");
            values.put("time_to", "");
        }

        if (includeSinceMax) {
            // Calculate usage rate since max
            Double usageRateValueSinceMax = calculateBatteryUsageRateValueSinceMax(dataPoints, minDuration, highTargetPercent);
            if (usageRateValueSinceMax != null) {
                values.put("usage_rate_since_max", String.format(Locale.getDefault(), "%.1f", usageRateValueSinceMax));

                // Calculate time estimates
                double hoursToLevel = Math.abs(currentBatteryLevel - targetPercent) / usageRateValueSinceMax;
                String hoursTo = formatTimeEstimate(hoursToLevel, targetPercent);
                String timeTo = formatDurationEndTime(hoursToLevel);

                values.put("hours_to_since_max", hoursTo);
                values.put("time_to_since_max", timeTo);
            } else {
                values.put("usage_rate_since_max", "");
                values.put("hours_to_since_max", "");
                values.put("time_to_since_max", "");
            }
        }

        return values;
    }
}
