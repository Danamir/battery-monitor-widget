package com.danamir.batterymonitor;

public class StatusData {
    private final String statusName;
    private final long startTimestamp;
    private final long endTimestamp; // Same as startTimestamp for one-off events, 0 for ongoing

    public StatusData(String statusName, long startTimestamp, long endTimestamp) {
        this.statusName = statusName;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
    }

    public String getStatusName() {
        return statusName;
    }

    public long getStartTimestamp() {
        return startTimestamp;
    }

    public long getEndTimestamp() {
        return endTimestamp;
    }

    public boolean isOngoing() {
        return endTimestamp == 0;
    }

    public boolean isOneOffEvent() {
        return endTimestamp > 0 && startTimestamp == endTimestamp;
    }
}
