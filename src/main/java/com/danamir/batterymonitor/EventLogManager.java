package com.danamir.batterymonitor;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventLogManager {
    private static final int MAX_LOG_ENTRIES = 40;
    private static EventLogManager instance;
    private final Context context;
    private List<String> eventLog;

    private EventLogManager(Context context) {
        this.context = context.getApplicationContext();
        eventLog = loadEventLog();
    }

    public static synchronized EventLogManager getInstance(Context context) {
        if (instance == null) {
            instance = new EventLogManager(context.getApplicationContext());
        }
        return instance;
    }

    public synchronized void logEvent(String message) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String timestamp = dateFormat.format(new Date());
        String logEntry = timestamp + " - " + message;
        Log.i("BatteryMonitorService", logEntry);

        // Check if the last log entry has the same message (ignoring timestamp and counter)
        if (!eventLog.isEmpty()) {
            String lastEntry = eventLog.get(eventLog.size() - 1);

            // Extract the message part from the last entry (after " - " and before any " (+")
            int lastDashIndex = lastEntry.indexOf(" - ");
            if (lastDashIndex != -1) {
                String lastMessage = lastEntry.substring(lastDashIndex + 3);

                // Remove counter if present
                int counterIndex = lastMessage.lastIndexOf(" (+");
                if (counterIndex != -1) {
                    lastMessage = lastMessage.substring(0, counterIndex);
                }

                // If messages match, update the last entry with new timestamp and increment counter
                if (lastMessage.equals(message)) {
                    // Extract existing counter
                    int existingCounter = 1;
                    String lastEntryOriginal = eventLog.get(eventLog.size() - 1);
                    int existingCounterIndex = lastEntryOriginal.lastIndexOf(" (+");
                    if (existingCounterIndex != -1) {
                        int closingParen = lastEntryOriginal.indexOf(")", existingCounterIndex);
                        if (closingParen != -1) {
                            String counterStr = lastEntryOriginal.substring(existingCounterIndex + 3, closingParen);
                            try {
                                existingCounter = Integer.parseInt(counterStr);
                            } catch (NumberFormatException e) {
                                existingCounter = 1;
                            }
                        }
                    }

                    // Update the last entry with new timestamp and incremented counter
                    existingCounter++;
                    logEntry = timestamp + " - " + message + " (+" + existingCounter + ")";
                    eventLog.set(eventLog.size() - 1, logEntry);
                    saveEventLog();
                    return;
                }
            }
        }

        // Add new log entry
        eventLog.add(logEntry);

        // Keep only recent log entries
        if (eventLog.size() > MAX_LOG_ENTRIES) {
            eventLog = eventLog.subList(eventLog.size() - MAX_LOG_ENTRIES, eventLog.size());
        }

        saveEventLog();
    }

    public synchronized List<String> getEventLog() {
        // Reload using ContentProvider to get latest data
        eventLog = loadEventLog();
        return new ArrayList<>(eventLog);
    }

    public synchronized void clearEventLog() {
        eventLog.clear();
        saveEventLog();
    }

    private List<String> loadEventLog() {
        // Use ContentProvider to load data
        List<String> log = new ArrayList<>();

        try {
            Cursor cursor = context.getContentResolver().query(
                EventLogProvider.CONTENT_URI,
                null, null, null, null
            );

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int dataIndex = cursor.getColumnIndex("data");
                    if (dataIndex != -1) {
                        String jsonString = cursor.getString(dataIndex);
                        JSONArray jsonArray = new JSONArray(jsonString);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            log.add(jsonArray.getString(i));
                        }
                    }
                }
                cursor.close();
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

        String jsonString = jsonArray.toString();

        // Use ContentProvider to save data
        ContentValues values = new ContentValues();
        values.put("data", jsonString);
        context.getContentResolver().insert(EventLogProvider.CONTENT_URI, values);
    }
}
