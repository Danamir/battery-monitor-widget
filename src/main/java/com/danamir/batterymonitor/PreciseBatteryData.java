package com.danamir.batterymonitor;

public class PreciseBatteryData {
    private final long timestamp;
    private final float preciseLevel;
    private final boolean isCharging;

    public PreciseBatteryData(long timestamp, float preciseLevel, boolean isCharging) {
        this.timestamp = timestamp;
        this.preciseLevel = preciseLevel;
        this.isCharging = isCharging;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getPreciseLevel() {
        return preciseLevel;
    }

    public boolean isCharging() {
        return isCharging;
    }
}
