package com.danamir.batterymonitor;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BatteryDataManager {
    private static final String PREF_BATTERY_DATA = "battery_data";
    private static final int MAX_DATA_POINTS = 10000;
    private static BatteryDataManager instance;
    private final SharedPreferences prefs;
    private final Context context;
    private List<BatteryData> dataPoints;

    private BatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        dataPoints = loadData();
    }

    public static synchronized BatteryDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new BatteryDataManager(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized void addDataPoint(int level, boolean isCharging) {
        long timestamp = System.currentTimeMillis();
        boolean shouldAddPoint = true;
        EventLogManager eventLogManager = EventLogManager.getInstance(context);

        // Check if we should add this point (allow if 1 minute has passed OR if data changed)
        if (!dataPoints.isEmpty()) {
            BatteryData lastPoint = dataPoints.get(dataPoints.size() - 1);
            long timeSinceLastPoint = timestamp - lastPoint.getTimestamp();
            boolean dataChanged = lastPoint.getLevel() != level || lastPoint.isCharging() != isCharging;

            // Skip only if less than 1 minute has passed AND data hasn't changed
            if (timeSinceLastPoint < 60000 && !dataChanged) {
                shouldAddPoint = false;
            }

            // Log battery level changes
            if (lastPoint.getLevel() != level) {
                eventLogManager.logEvent("Battery level changed: " + lastPoint.getLevel() + "% → " + level + "%");
            }

            // Log charging status changes
            if (lastPoint.isCharging() != isCharging) {
                eventLogManager.logEvent("Battery status: " + (isCharging ? "Charging" : "Discharging"));
            }

            // Log 1 minute auto update
            if (timeSinceLastPoint > 60000 && !dataChanged) {
                eventLogManager.logEvent("Battery level unchanged: " + lastPoint.getLevel() + "%");
            }
        } else {
            // First data point
            eventLogManager.logEvent("Battery level: " + level + "% (" + (isCharging ? "Charging" : "Discharging") + ")");
        }

        if (shouldAddPoint) {
            dataPoints.add(new BatteryData(timestamp, level, isCharging));

            // Keep only recent data
            if (dataPoints.size() > MAX_DATA_POINTS) {
                dataPoints = dataPoints.subList(dataPoints.size() - MAX_DATA_POINTS, dataPoints.size());
            }

            saveData();
        }
    }

    public synchronized List<BatteryData> getDataPoints(int hours) {
        return getDataPoints(hours, false);
    }

    public synchronized List<BatteryData> getDataPoints(int hours, boolean getPreviousPoint) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<BatteryData> filteredData = new ArrayList<>();

        BatteryData lastPoint = null;
        BatteryData previousPoint = null; // The last point before cutoffTime
        boolean addedPreviousPoint = false;

        for (BatteryData data : dataPoints) {
            if (data.getTimestamp() < cutoffTime) {
                // Track the last point before cutoffTime
                previousPoint = data;
                continue;
            }

            // Add the previous point once when we find the first point in range
            if (getPreviousPoint && previousPoint != null && filteredData.isEmpty()) {
                filteredData.add(previousPoint);
                lastPoint = previousPoint;
                previousPoint = null; // Only add it once
                addedPreviousPoint = true;
            }

            // Don't apply duplicate filtering to the previous point we just added
            if (lastPoint != null && lastPoint.getLevel() == data.getLevel()
                    && lastPoint.isCharging() == data.isCharging() && !filteredData.isEmpty()
                    && !addedPreviousPoint) {
                filteredData.remove(filteredData.size() - 1);
            }

            addedPreviousPoint = false;

            filteredData.add(data);
            lastPoint = data;
        }

        return filteredData;
    }

    private List<BatteryData> loadData() {
        // Use ContentProvider to load data
        List<BatteryData> data = new ArrayList<>();

        try {
            Cursor cursor = context.getContentResolver().query(
                DataProvider.CONTENT_URI,
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
                            data.add(new BatteryData(
                                obj.getLong("timestamp"),
                                obj.getInt("level"),
                                obj.getBoolean("charging")
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
            for (BatteryData data : dataPoints) {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", data.getTimestamp());
                obj.put("level", data.getLevel());
                obj.put("charging", data.isCharging());
                jsonArray.put(obj);
            }

            String jsonString = jsonArray.toString();

            // Use ContentProvider to save data
            ContentValues values = new ContentValues();
            values.put("data", jsonString);
            context.getContentResolver().insert(DataProvider.CONTENT_URI, values);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public synchronized void clearOldData(int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<BatteryData> filteredData = new ArrayList<>();

        for (BatteryData data : dataPoints) {
            if (data.getTimestamp() >= cutoffTime) {
                filteredData.add(data);
            }
        }

        dataPoints = filteredData;
        saveData();
    }

    public synchronized List<String> getEventLog() {
        return EventLogManager.getInstance(context).getEventLog();
    }

    public synchronized void clearEventLog() {
        EventLogManager.getInstance(context).clearEventLog();
    }
}
