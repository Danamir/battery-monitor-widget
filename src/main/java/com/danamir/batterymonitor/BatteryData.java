package com.danamir.batterymonitor;

public class BatteryData {
    private final long timestamp;
    private final int level;
    private final boolean isCharging;

    public BatteryData(long timestamp, int level, boolean isCharging) {
        this.timestamp = timestamp;
        this.level = level;
        this.isCharging = isCharging;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getLevel() {
        return level;
    }

    public boolean isCharging() {
        return isCharging;
    }
}
