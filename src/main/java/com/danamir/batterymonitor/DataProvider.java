package com.danamir.batterymonitor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.preference.PreferenceManager;

public class DataProvider extends ContentProvider {
    public static final String AUTHORITY = "com.danamir.batterymonitor.data";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/battery");
    public static final Uri PRECISE_CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/precise_battery");

    private static final String COLUMN_DATA = "data";
    private static final String PREF_BATTERY_DATA = "battery_data";
    private static final String PREF_PRECISE_BATTERY_DATA = "precise_battery_data";

    // URI matcher codes
    private static final int BATTERY_DATA = 1;
    private static final int PRECISE_BATTERY_DATA = 2;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, "battery", BATTERY_DATA);
        uriMatcher.addURI(AUTHORITY, "precise_battery", PRECISE_BATTERY_DATA);
    }

    // All-time statistics keys
    private static final String PREF_TOTAL_CHARGE_TIME = "total_charge_time";
    private static final String PREF_TOTAL_DISCHARGE_TIME = "total_discharge_time";
    private static final String PREF_MEAN_CHARGE_RATE = "mean_charge_rate";
    private static final String PREF_MEAN_DISCHARGE_RATE = "mean_discharge_rate";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Context context = getContext();
        if (context == null) {
            return null;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String jsonData;

        switch (uriMatcher.match(uri)) {
            case PRECISE_BATTERY_DATA:
                jsonData = prefs.getString(PREF_PRECISE_BATTERY_DATA, "[]");
                break;
            case BATTERY_DATA:
            default:
                jsonData = prefs.getString(PREF_BATTERY_DATA, "[]");
                break;
        }

        MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_DATA});
        cursor.addRow(new Object[]{jsonData});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        switch (uriMatcher.match(uri)) {
            case PRECISE_BATTERY_DATA:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".precise_battery";
            case BATTERY_DATA:
            default:
                return "vnd.android.cursor.item/vnd." + AUTHORITY + ".battery";
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Context context = getContext();
        if (context == null || values == null) {
            return null;
        }

        String jsonData = values.getAsString(COLUMN_DATA);
        if (jsonData != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String prefKey;

            switch (uriMatcher.match(uri)) {
                case PRECISE_BATTERY_DATA:
                    prefKey = PREF_PRECISE_BATTERY_DATA;
                    break;
                case BATTERY_DATA:
                default:
                    prefKey = PREF_BATTERY_DATA;
                    break;
            }

            prefs.edit().putString(prefKey, jsonData).commit();

            // Notify observers that data changed
            context.getContentResolver().notifyChange(uri, null);
        }
        return uri;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Context context = getContext();
        if (context == null) {
            return 0;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String prefKey;

        switch (uriMatcher.match(uri)) {
            case PRECISE_BATTERY_DATA:
                prefKey = PREF_PRECISE_BATTERY_DATA;
                break;
            case BATTERY_DATA:
            default:
                prefKey = PREF_BATTERY_DATA;
                break;
        }

        prefs.edit().putString(prefKey, "[]").commit();

        // Notify observers that data changed
        context.getContentResolver().notifyChange(uri, null);
        return 1;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // Use insert for updates
        insert(uri, values);
        return 1;
    }

    // Methods to manage all-time statistics

    /**
     * Updates all-time mean charge rate and total charge time.
     * Calculates weighted mean: (oldMean * oldTime + newRate * newTime) / (oldTime + newTime)
     *
     * @param newRate The new charge rate in %/hour
     * @param newTime The time duration for this rate in milliseconds
     */
    public static void updateChargeStats(Context context, double newRate, long newTime) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        long totalTime = prefs.getLong(PREF_TOTAL_CHARGE_TIME, 0);
        double meanRate = Double.longBitsToDouble(prefs.getLong(PREF_MEAN_CHARGE_RATE, 0));

        // Calculate new weighted mean
        double newMean;
        if (totalTime == 0) {
            newMean = newRate;
        } else {
            newMean = (meanRate * totalTime + newRate * newTime) / (totalTime + newTime);
        }

        totalTime += newTime;

        // Store updated values
        prefs.edit()
            .putLong(PREF_TOTAL_CHARGE_TIME, totalTime)
            .putLong(PREF_MEAN_CHARGE_RATE, Double.doubleToRawLongBits(newMean))
            .apply();
    }

    /**
     * Removes a previous charge rate contribution from the statistics.
     * Used when correcting the mean after replacing a data point.
     *
     * @param oldRate The old charge rate to remove in %/hour
     * @param oldTime The time duration to remove in milliseconds
     */
    public static void removeChargeStats(Context context, double oldRate, long oldTime) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        long totalTime = prefs.getLong(PREF_TOTAL_CHARGE_TIME, 0);
        double meanRate = Double.longBitsToDouble(prefs.getLong(PREF_MEAN_CHARGE_RATE, 0));

        // Calculate new weighted mean by removing the old contribution
        if (totalTime > oldTime) {
            double newMean = (meanRate * totalTime - oldRate * oldTime) / (totalTime - oldTime);
            totalTime -= oldTime;

            // Store updated values
            prefs.edit()
                .putLong(PREF_TOTAL_CHARGE_TIME, totalTime)
                .putLong(PREF_MEAN_CHARGE_RATE, Double.doubleToRawLongBits(newMean))
                .apply();
        } else {
            // If we're removing all the time, reset to zero
            prefs.edit()
                .putLong(PREF_TOTAL_CHARGE_TIME, 0)
                .putLong(PREF_MEAN_CHARGE_RATE, 0)
                .apply();
        }
    }

    /**
     * Updates all-time mean discharge rate and total discharge time.
     * Calculates weighted mean: (oldMean * oldTime + newRate * newTime) / (oldTime + newTime)
     *
     * @param newRate The new discharge rate in %/hour (positive value)
     * @param newTime The time duration for this rate in milliseconds
     */
    public static void updateDischargeStats(Context context, double newRate, long newTime) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        long totalTime = prefs.getLong(PREF_TOTAL_DISCHARGE_TIME, 0);
        double meanRate = Double.longBitsToDouble(prefs.getLong(PREF_MEAN_DISCHARGE_RATE, 0));

        // Calculate new weighted mean
        double newMean;
        if (totalTime == 0) {
            newMean = newRate;
        } else {
            newMean = (meanRate * totalTime + newRate * newTime) / (totalTime + newTime);
        }

        totalTime += newTime;

        // Store updated values
        prefs.edit()
            .putLong(PREF_TOTAL_DISCHARGE_TIME, totalTime)
            .putLong(PREF_MEAN_DISCHARGE_RATE, Double.doubleToRawLongBits(newMean))
            .apply();
    }

    /**
     * Removes a previous discharge rate contribution from the statistics.
     * Used when correcting the mean after replacing a data point.
     *
     * @param oldRate The old discharge rate to remove in %/hour (positive value)
     * @param oldTime The time duration to remove in milliseconds
     */
    public static void removeDischargeStats(Context context, double oldRate, long oldTime) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        long totalTime = prefs.getLong(PREF_TOTAL_DISCHARGE_TIME, 0);
        double meanRate = Double.longBitsToDouble(prefs.getLong(PREF_MEAN_DISCHARGE_RATE, 0));

        // Calculate new weighted mean by removing the old contribution
        if (totalTime > oldTime) {
            double newMean = (meanRate * totalTime - oldRate * oldTime) / (totalTime - oldTime);
            totalTime -= oldTime;

            // Store updated values
            prefs.edit()
                .putLong(PREF_TOTAL_DISCHARGE_TIME, totalTime)
                .putLong(PREF_MEAN_DISCHARGE_RATE, Double.doubleToRawLongBits(newMean))
                .apply();
        } else {
            // If we're removing all the time, reset to zero
            prefs.edit()
                .putLong(PREF_TOTAL_DISCHARGE_TIME, 0)
                .putLong(PREF_MEAN_DISCHARGE_RATE, 0)
                .apply();
        }
    }

    /**
     * Gets the all-time mean charge rate
     * @return Mean charge rate in %/hour
     */
    public static double getMeanChargeRate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Double.longBitsToDouble(prefs.getLong(PREF_MEAN_CHARGE_RATE, 0));
    }

    /**
     * Gets the all-time mean discharge rate
     * @return Mean discharge rate in %/hour (positive value)
     */
    public static double getMeanDischargeRate(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return Double.longBitsToDouble(prefs.getLong(PREF_MEAN_DISCHARGE_RATE, 0));
    }

    /**
     * Gets the total charge time
     * @return Total charge time in milliseconds
     */
    public static long getTotalChargeTime(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(PREF_TOTAL_CHARGE_TIME, 0);
    }

    /**
     * Gets the total discharge time
     * @return Total discharge time in milliseconds
     */
    public static long getTotalDischargeTime(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(PREF_TOTAL_DISCHARGE_TIME, 0);
    }

    /**
     * Resets all statistics to zero
     */
    public static void resetStats(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit()
            .putLong(PREF_TOTAL_CHARGE_TIME, 0)
            .putLong(PREF_TOTAL_DISCHARGE_TIME, 0)
            .putLong(PREF_MEAN_CHARGE_RATE, 0)
            .putLong(PREF_MEAN_DISCHARGE_RATE, 0)
            .apply();
    }
}
