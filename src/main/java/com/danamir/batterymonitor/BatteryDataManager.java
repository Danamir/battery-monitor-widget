package com.danamir.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BatteryDataManager {
    private static final String PREF_BATTERY_DATA = "battery_data";
    private static final String PREF_BATTERY_LOG = "battery_event_log";
    private static final int MAX_DATA_POINTS = 10000;
    private static final int MAX_LOG_ENTRIES = 1000;
    private static BatteryDataManager instance;
    private final SharedPreferences prefs;
    private List<BatteryData> dataPoints;
    private List<String> eventLog;

    private BatteryDataManager(Context context) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        dataPoints = loadData();
        eventLog = loadEventLog();
    }

    public static synchronized BatteryDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new BatteryDataManager(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized void addDataPoint(int level, boolean isCharging) {
        long timestamp = System.currentTimeMillis();

        // Check if we should add this point (avoid duplicates within 10 seconds)
        if (!dataPoints.isEmpty()) {
            BatteryData lastPoint = dataPoints.get(dataPoints.size() - 1);
            if (timestamp - lastPoint.getTimestamp() < 10000 &&
                lastPoint.getLevel() == level &&
                lastPoint.isCharging() == isCharging) {
                return; // Skip duplicate data
            }

            // Log battery level changes
            if (lastPoint.getLevel() != level) {
                logEvent("Battery level changed: " + lastPoint.getLevel() + "% → " + level + "%");
            }

            // Log charging status changes
            if (lastPoint.isCharging() != isCharging) {
                logEvent("Battery status: " + (isCharging ? "Charging" : "Discharging"));
            }
        } else {
            // First data point
            logEvent("Battery level: " + level + "% (" + (isCharging ? "Charging" : "Discharging") + ")");
        }

        dataPoints.add(new BatteryData(timestamp, level, isCharging));

        // Keep only recent data
        if (dataPoints.size() > MAX_DATA_POINTS) {
            dataPoints = dataPoints.subList(dataPoints.size() - MAX_DATA_POINTS, dataPoints.size());
        }

        saveData();
    }

    public synchronized List<BatteryData> getDataPoints(int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<BatteryData> filteredData = new ArrayList<>();

        for (BatteryData data : dataPoints) {
            if (data.getTimestamp() >= cutoffTime) {
                filteredData.add(data);
            }
        }

        return filteredData;
    }

    private List<BatteryData> loadData() {
        List<BatteryData> data = new ArrayList<>();
        String jsonString = prefs.getString(PREF_BATTERY_DATA, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                data.add(new BatteryData(
                    obj.getLong("timestamp"),
                    obj.getInt("level"),
                    obj.getBoolean("charging")
                ));
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

            prefs.edit().putString(PREF_BATTERY_DATA, jsonArray.toString()).apply();
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

    private synchronized void logEvent(String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - " + message;

        eventLog.add(logEntry);

        // Keep only recent log entries
        if (eventLog.size() > MAX_LOG_ENTRIES) {
            eventLog = eventLog.subList(eventLog.size() - MAX_LOG_ENTRIES, eventLog.size());
        }

        saveEventLog();
    }

    public synchronized List<String> getEventLog() {
        return new ArrayList<>(eventLog);
    }

    public synchronized void clearEventLog() {
        eventLog.clear();
        saveEventLog();
    }

    private List<String> loadEventLog() {
        List<String> log = new ArrayList<>();
        String jsonString = prefs.getString(PREF_BATTERY_LOG, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonString);
            for (int i = 0; i < jsonArray.length(); i++) {
                log.add(jsonArray.getString(i));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return log;
    }

    private void saveEventLog() {
        JSONArray jsonArray = new JSONArray();

        for (String entry : eventLog) {
            jsonArray.put(entry);
        }

        prefs.edit().putString(PREF_BATTERY_LOG, jsonArray.toString()).apply();
    }
}
