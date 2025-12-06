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

public class PreciseBatteryDataManager {
    private static final String PREF_PRECISE_BATTERY_DATA = "precise_battery_data";
    private static final int MAX_DATA_POINTS = 10000;
    private static PreciseBatteryDataManager instance;
    private final SharedPreferences prefs;
    private final Context context;
    private List<PreciseBatteryData> dataPoints;

    private PreciseBatteryDataManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        dataPoints = loadData();
    }

    public static synchronized PreciseBatteryDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new PreciseBatteryDataManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Add a precise battery data point. Always adds a new entry without deduplication.
     */
    public synchronized void addDataPoint(float preciseLevel, boolean isCharging) {
        long timestamp = System.currentTimeMillis();

        dataPoints.add(new PreciseBatteryData(timestamp, preciseLevel, isCharging));

        // Keep only recent data
        if (dataPoints.size() > MAX_DATA_POINTS) {
            dataPoints = dataPoints.subList(dataPoints.size() - MAX_DATA_POINTS, dataPoints.size());
        }

        saveData();
    }

    public synchronized List<PreciseBatteryData> getDataPoints(int hours) {
        return getDataPoints(hours, false);
    }

    public synchronized List<PreciseBatteryData> getDataPoints(int hours, boolean getPreviousPoint) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<PreciseBatteryData> filteredData = new ArrayList<>();

        PreciseBatteryData previousPoint = null;

        for (PreciseBatteryData data : dataPoints) {
            if (data.getTimestamp() < cutoffTime) {
                previousPoint = data;
                continue;
            }

            // Add the previous point once when we find the first point in range
            if (getPreviousPoint && previousPoint != null && filteredData.isEmpty()) {
                filteredData.add(previousPoint);
                previousPoint = null;
            }

            filteredData.add(data);
        }

        return filteredData;
    }

    /**
     * Get the timestamp of the oldest data point.
     * @return Timestamp in milliseconds, or -1 if no data
     */
    public synchronized long getOldestTimestamp() {
        if (dataPoints.isEmpty()) {
            return -1;
        }
        return dataPoints.get(0).getTimestamp();
    }

    private List<PreciseBatteryData> loadData() {
        List<PreciseBatteryData> data = new ArrayList<>();

        try {
            Cursor cursor = context.getContentResolver().query(
                DataProvider.CONTENT_URI,
                null, null, null, null
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int dataIndex = cursor.getColumnIndex(PREF_PRECISE_BATTERY_DATA);
                    if (dataIndex != -1) {
                        String jsonString = cursor.getString(dataIndex);
                        if (jsonString != null && !jsonString.isEmpty()) {
                            JSONArray jsonArray = new JSONArray(jsonString);
                            for (int i = 0; i < jsonArray.length(); i++) {
                                JSONObject obj = jsonArray.getJSONObject(i);
                                data.add(new PreciseBatteryData(
                                    obj.getLong("timestamp"),
                                    (float) obj.getDouble("preciseLevel"),
                                    obj.getBoolean("charging")
                                ));
                            }
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
            for (PreciseBatteryData data : dataPoints) {
                JSONObject obj = new JSONObject();
                obj.put("timestamp", data.getTimestamp());
                obj.put("preciseLevel", data.getPreciseLevel());
                obj.put("charging", data.isCharging());
                jsonArray.put(obj);
            }

            String jsonString = jsonArray.toString();

            // Use ContentProvider to save data
            ContentValues values = new ContentValues();
            values.put(PREF_PRECISE_BATTERY_DATA, jsonString);
            context.getContentResolver().insert(DataProvider.CONTENT_URI, values);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public synchronized void clearOldData(int hours) {
        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        List<PreciseBatteryData> filteredData = new ArrayList<>();

        for (PreciseBatteryData data : dataPoints) {
            if (data.getTimestamp() >= cutoffTime) {
                filteredData.add(data);
            }
        }

        dataPoints = filteredData;
        saveData();
    }

    /**
     * Get hybrid data points: uses precise data where available, fills gaps with integer data.
     * @param context Application context
     * @param hours Number of hours to retrieve
     * @param getPreviousPoint Whether to include a point before the time range for interpolation
     * @return List of hybrid data points (precise when available and enabled, integer otherwise)
     */
    public static List<HybridBatteryData> getHybridDataPoints(Context context, int hours, boolean getPreviousPoint) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean usePrecise = prefs.getBoolean("use_precise_battery", false);

        List<HybridBatteryData> result = new ArrayList<>();

        if (!usePrecise) {
            // Use integer data only
            BatteryDataManager intManager = BatteryDataManager.getInstance(context);
            List<BatteryData> intData = intManager.getDataPoints(hours, getPreviousPoint);

            for (BatteryData data : intData) {
                result.add(new HybridBatteryData(
                    data.getTimestamp(),
                    data.getLevel(),
                    (float) data.getLevel(),
                    data.isCharging(),
                    false // not precise
                ));
            }
            return result;
        }

        // Get precise and integer data
        PreciseBatteryDataManager preciseManager = PreciseBatteryDataManager.getInstance(context);
        BatteryDataManager intManager = BatteryDataManager.getInstance(context);

        List<PreciseBatteryData> preciseData = preciseManager.getDataPoints(hours, getPreviousPoint);

        long cutoffTime = System.currentTimeMillis() - (hours * 60 * 60 * 1000L);
        long oldestPreciseTime = preciseManager.getOldestTimestamp();

        // If precise data doesn't cover the full range, get integer data for the gap
        if (preciseData.isEmpty() || oldestPreciseTime > cutoffTime) {
            // Calculate how many hours of integer data we need
            int intHoursNeeded = hours;
            if (oldestPreciseTime > 0) {
                long gapMillis = oldestPreciseTime - cutoffTime;
                intHoursNeeded = (int) Math.ceil(gapMillis / (60.0 * 60 * 1000));
            }

            // Get integer data to fill the gap
            List<BatteryData> intData = intManager.getDataPoints(hours, getPreviousPoint);

            for (BatteryData data : intData) {
                // Only add integer data that's before the oldest precise data
                if (oldestPreciseTime < 0 || data.getTimestamp() < oldestPreciseTime) {
                    result.add(new HybridBatteryData(
                        data.getTimestamp(),
                        data.getLevel(),
                        (float) data.getLevel(),
                        data.isCharging(),
                        false // not precise
                    ));
                }
            }
        }

        // Add precise data
        for (PreciseBatteryData data : preciseData) {
            result.add(new HybridBatteryData(
                data.getTimestamp(),
                (int) Math.round(data.getPreciseLevel()),
                data.getPreciseLevel(),
                data.isCharging(),
                true // is precise
            ));
        }

        // Sort by timestamp (should already be sorted, but ensure it)
        result.sort((a, b) -> Long.compare(a.getTimestamp(), b.getTimestamp()));

        return result;
    }
}
