package com.danamir.batterymonitor;

/**
 * Unified battery data container that supports both integer and floating-point battery levels.
 *
 * This class bridges integer-based battery reporting (standard Android API) with
 * floating-point precision data (from precise battery calculations). The {@code batteryLevel}
 * field stores the actual level value (as a float), while {@code standardLevel} maintains the
 * integer representation from the standard Android battery API.
 *
 * The {@code isPrecise} flag indicates the source and accuracy of the data:
 * <ul>
 *   <li>When {@code true}: batteryLevel comes from precise calculations with sub-percent accuracy</li>
 *   <li>When {@code false}: batteryLevel represents standard integer battery data (converted to float),
 *       which is identical to standardLevel</li>
 * </ul>
 */
public class HybridBatteryData {
    private final long timestamp;
    private final int standardLevel;
    private final float batteryLevel;
    private final boolean isCharging;
    private final boolean isPrecise;

    public HybridBatteryData(long timestamp, int standardLevel, float batteryLevel, boolean isCharging, boolean isPrecise) {
        this.timestamp = timestamp;
        this.standardLevel = standardLevel;
        this.batteryLevel = batteryLevel;
        this.isCharging = isCharging;
        this.isPrecise = isPrecise;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getStandardLevel() {
        return standardLevel;
    }

    public float getBatteryLevel() {
        return batteryLevel;
    }

    public boolean isCharging() {
        return isCharging;
    }

    public boolean isPrecise() {
        return isPrecise;
    }
}
