package com.danamir.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PreciseBatteryCalculator {
    
    private static final int MIN_SAMPLES = 5;
    private static final int MAX_SAMPLES = 50;
    private static final int CALIBRATION_MIN_PERCENT = 15;
    private static final int CALIBRATION_MAX_PERCENT = 95;
    private static final double MAX_DEVIATION_PERCENT = 2.0;
    private static final long SAMPLE_RETENTION_MS = 7L * 24 * 60 * 60 * 1000; // 7 days
    private static final long MIN_SAMPLE_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    // SharedPreferences keys
    private static final String PREF_CAPACITY_SAMPLES = "capacity_samples";
    private static final String PREF_SMOOTHED_CAPACITY = "smoothed_capacity";
    private static final String PREF_LAST_SYSTEM_PERCENT = "last_system_percent";

    private List<CapacitySample> samples = new ArrayList<>();
    private double smoothedCapacity = -1;
    private int lastSystemPercent = -1;
    private boolean dataLoaded = false;

    static class CapacitySample {
        int systemPercent;
        double chargeMah;
        long timestamp;
        
        CapacitySample(int systemPercent, double chargeMah, long timestamp) {
            this.systemPercent = systemPercent;
            this.chargeMah = chargeMah;
            this.timestamp = timestamp;
        }
    }

    /**
     * Returns a calibrated battery percentage with decimal precision.
     * Falls back to system percentage if calibration is not yet reliable.
     */
    public double getCalibratedBatteryPercentage(Context context) {
        // Load persistent data on first use
        if (!dataLoaded) {
            loadData(context);
            dataLoaded = true;
        }

        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return 0.0;

        // Get raw data - use correct property types
        int systemPercent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        long chargeCounterMicro = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);

        // Validate raw data
        if (systemPercent <= 0 || chargeCounterMicro <= 0) {
            return systemPercent > 0 ? (double) systemPercent : 0.0;
        }

        // Convert µAh to mAh (handle negative values from some devices)
        double currentMah = Math.abs(chargeCounterMicro) / 1000.0;

        // Store sample for historical analysis
        storeSample(context, systemPercent, currentMah);

        // Update capacity estimate using both smoothing and median
        updateCapacityEstimate(context, systemPercent, currentMah);

        // Calculate precise percentage
        if (smoothedCapacity > 0) {
            double precisePercent = (currentMah / smoothedCapacity) * 100.0;

            // Sanity check: ensure result is reasonable
            if (precisePercent >= 0 && precisePercent <= 100
                    && Math.abs(precisePercent - systemPercent) <= MAX_DEVIATION_PERCENT) {
                return precisePercent;
            }
        }

        // Fallback to system percentage
        return (double) systemPercent;
    }

    /**
     * Stores a sample for historical capacity estimation.
     * Only stores samples in reliable percentage ranges and respects minimum time interval.
     */
    private void storeSample(Context context, int systemPercent, double chargeMah) {
        if (systemPercent >= CALIBRATION_MIN_PERCENT
            && systemPercent <= CALIBRATION_MAX_PERCENT
            && chargeMah > 0) {

            long currentTime = System.currentTimeMillis();

            // Check if enough time has passed since the last sample
            if (!samples.isEmpty()) {
                long lastSampleTime = samples.get(samples.size() - 1).timestamp;
                if (currentTime - lastSampleTime < MIN_SAMPLE_INTERVAL_MS) {
                    return; // Skip this sample, too soon
                }
            }

            samples.add(new CapacitySample(systemPercent, chargeMah, currentTime));

            // Clean up old samples
            long cutoffTime = currentTime - SAMPLE_RETENTION_MS;
            samples.removeIf(s -> s.timestamp < cutoffTime);

            // Limit total samples
            while (samples.size() > MAX_SAMPLES) {
                samples.remove(0);
            }

            // Save samples to persistent storage
            saveSamples(context);
        }
    }

    /**
     * Updates capacity estimate using weighted smoothing and median filtering.
     * Combines real-time calibration with historical data robustness.
     */
    private void updateCapacityEstimate(Context context, int systemPercent, double currentMah) {
        boolean changed = false;

        // Real-time calibration (from SmartBattery approach)
        if (systemPercent != lastSystemPercent
            && systemPercent >= CALIBRATION_MIN_PERCENT
            && systemPercent <= CALIBRATION_MAX_PERCENT) {

            double impliedCapacity = currentMah / (systemPercent / 100.0);

            if (smoothedCapacity <= 0) {
                smoothedCapacity = impliedCapacity;
            } else {
                // Weighted average: 75% old, 25% new
                smoothedCapacity = (smoothedCapacity * 0.75) + (impliedCapacity * 0.25);
            }

            lastSystemPercent = systemPercent;
            changed = true;
        }

        // Historical validation
        if (samples.size() >= MIN_SAMPLES) {
            double medianCapacity = calculateMedianCapacity();

            if (medianCapacity > 0) {
                // If we have no smoothed capacity yet, use median
                if (smoothedCapacity <= 0) {
                    smoothedCapacity = medianCapacity;
                    changed = true;
                } else {
                    // Validate smoothed capacity against median (prevent drift)
                    // If they differ by more than 10%, blend them
                    double deviation = Math.abs(smoothedCapacity - medianCapacity) / medianCapacity;
                    if (deviation > 0.10) {
                        smoothedCapacity = (smoothedCapacity * 0.7) + (medianCapacity * 0.3);
                        changed = true;
                    }
                }
            }
        }

        // Save if capacity or percent changed
        if (changed) {
            saveCapacityData(context);
        }
    }

    /**
     * Calculates median battery capacity from historical samples.
     * Median is more robust to outliers than mean.
     */
    private double calculateMedianCapacity() {
        if (samples.size() < MIN_SAMPLES) {
            return 0;
        }

        List<Double> capacities = new ArrayList<>();
        for (CapacitySample sample : samples) {
            double capacity = (sample.chargeMah * 100.0) / sample.systemPercent;
            capacities.add(capacity);
        }

        Collections.sort(capacities);
        int size = capacities.size();
        
        // Return true median (average of middle two for even-sized lists)
        if (size % 2 == 0) {
            return (capacities.get(size / 2 - 1) + capacities.get(size / 2)) / 2.0;
        } else {
            return capacities.get(size / 2);
        }
    }

    /**
     * Returns the current estimated battery capacity in mAh (for debugging/display).
     */
    public double getEstimatedCapacityMah() {
        return smoothedCapacity;
    }

    /**
     * Returns the number of calibration samples collected.
     */
    public int getSampleCount() {
        return samples.size();
    }

    /**
     * Loads persistent data from SharedPreferences.
     */
    private void loadData(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Load samples
        String samplesJson = prefs.getString(PREF_CAPACITY_SAMPLES, "[]");
        try {
            JSONArray jsonArray = new JSONArray(samplesJson);
            samples.clear();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                samples.add(new CapacitySample(
                    obj.getInt("percent"),
                    obj.getDouble("mah"),
                    obj.getLong("timestamp")
                ));
            }
        } catch (JSONException e) {
            // If parsing fails, start with empty samples
            samples.clear();
        }

        // Load smoothed capacity
        smoothedCapacity = Double.longBitsToDouble(
            prefs.getLong(PREF_SMOOTHED_CAPACITY, Double.doubleToRawLongBits(-1.0))
        );

        // Load last system percent
        lastSystemPercent = prefs.getInt(PREF_LAST_SYSTEM_PERCENT, -1);
    }

    /**
     * Saves samples to SharedPreferences.
     */
    private void saveSamples(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        try {
            JSONArray jsonArray = new JSONArray();
            for (CapacitySample sample : samples) {
                JSONObject obj = new JSONObject();
                obj.put("percent", sample.systemPercent);
                obj.put("mah", sample.chargeMah);
                obj.put("timestamp", sample.timestamp);
                jsonArray.put(obj);
            }

            prefs.edit()
                .putString(PREF_CAPACITY_SAMPLES, jsonArray.toString())
                .apply();
        } catch (JSONException e) {
            // Silently fail if we can't save
        }
    }

    /**
     * Saves capacity data (smoothedCapacity and lastSystemPercent) to SharedPreferences.
     */
    private void saveCapacityData(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        prefs.edit()
            .putLong(PREF_SMOOTHED_CAPACITY, Double.doubleToRawLongBits(smoothedCapacity))
            .putInt(PREF_LAST_SYSTEM_PERCENT, lastSystemPercent)
            .apply();
    }
}
