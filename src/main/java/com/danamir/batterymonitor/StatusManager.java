package com.danamir.batterymonitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class StatusManager {
    private static final int MAX_STATUS_ENTRIES = 10000;
    private static StatusManager instance;
    private final Context context;
    private List<StatusData> statusList;

    private StatusManager(Context context) {
        this.context = context.getApplicationContext();
        statusList = loadData();
    }

    public static synchronized StatusManager getInstance(Context context) {
        if (instance == null) {
            instance = new StatusManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Add a status period with start and end timestamps
     */
    public synchronized void addStatus(String statusName, long startTimestamp, long endTimestamp) {
        statusList.add(new StatusData(statusName, startTimestamp, endTimestamp));

        // Keep only recent data
        if (statusList.size() > MAX_STATUS_ENTRIES) {
            statusList = statusList.subList(statusList.size() - MAX_STATUS_ENTRIES, statusList.size());
        }

        saveData();
    }

    /**
     * Add a one-off event (start = end timestamp)
     */
    public synchronized void addEvent(String statusName, long timestamp) {
        addStatus(statusName, timestamp, timestamp);
    }

    /**
     * Start an ongoing status (end = 0)
     */
    public synchronized void startStatus(String statusName, long startTimestamp) {
        addStatus(statusName, startTimestamp, 0);
    }

    /**
     * End the most recent ongoing status with the given name
     */
    public synchronized void endStatus(String statusName, long endTimestamp) {
        // Find the most recent ongoing status with this name
        for (int i = statusList.size() - 1; i >= 0; i--) {
            StatusData status = statusList.get(i);
            if (status.getStatusName().equals(statusName) && status.isOngoing()) {
                // Replace with ended status
                statusList.set(i, new StatusData(statusName, status.getStartTimestamp(), endTimestamp));
                saveData();
                return;
            }
        }
    }

    /**
     * Get all status data within the specified time range
     */
    public synchronized List<StatusData> getStatusData(int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<StatusData> filteredData = new ArrayList<>();

        for (StatusData status : statusList) {
            // Include if start or end is within range, or if it spans the range
            if (status.getStartTimestamp() >= cutoffTime ||
                (status.getEndTimestamp() >= cutoffTime && status.getEndTimestamp() > 0) ||
                (status.isOngoing() && status.getStartTimestamp() < cutoffTime)) {
                filteredData.add(status);
            }
        }

        return filteredData;
    }

    /**
     * Get all status data for specific status names within the specified time range
     */
    public synchronized List<StatusData> getStatusData(List<String> statusNames, int hours) {
        List<StatusData> allData = getStatusData(hours);
        List<StatusData> filteredData = new ArrayList<>();

        for (StatusData status : allData) {
            if (statusNames.contains(status.getStatusName())) {
                filteredData.add(status);
            }
        }

        return filteredData;
    }

    /**
     * Get all status data for a specific status name within the specified time range
     */
    public synchronized List<StatusData> getStatusData(String statusName, int hours) {
        return getStatusData(List.of(statusName), hours);
    }

    private List<StatusData> loadData() {
        List<StatusData> data = new ArrayList<>();

        try {
            Cursor cursor = context.getContentResolver().query(
                StatusProvider.CONTENT_URI,
                null, null, null, null
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int dataIndex = cursor.getColumnIndex("data");
                    if (dataIndex != -1) {
                        String jsonString = cursor.getString(dataIndex);
                        JSONArray jsonArray = new JSONArray(jsonString);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject obj = jsonArray.getJSONObject(i);
                            data.add(new StatusData(
                                obj.getString("name"),
                                obj.getLong("start"),
                                obj.getLong("end")
                            ));
                        }
                    }
                }
                cursor.close();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return data;
    }

    private void saveData() {
        JSONArray jsonArray = new JSONArray();

        try {
            for (StatusData status : statusList) {
                JSONObject obj = new JSONObject();
                obj.put("name", status.getStatusName());
                obj.put("start", status.getStartTimestamp());
                obj.put("end", status.getEndTimestamp());
                jsonArray.put(obj);
            }

            String jsonString = jsonArray.toString();

            // Use ContentProvider to save data
            ContentValues values = new ContentValues();
            values.put("data", jsonString);
            context.getContentResolver().insert(StatusProvider.CONTENT_URI, values);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public synchronized void clearOldData(int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<StatusData> filteredData = new ArrayList<>();

        for (StatusData status : statusList) {
            // Keep if it starts after cutoff, or if ongoing
            if (status.getStartTimestamp() >= cutoffTime || status.isOngoing()) {
                filteredData.add(status);
            }
        }

        statusList = filteredData;
        saveData();
    }
}
