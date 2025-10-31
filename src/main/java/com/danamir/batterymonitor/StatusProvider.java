package com.danamir.batterymonitor;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.preference.PreferenceManager;

public class StatusProvider extends ContentProvider {
    public static final String AUTHORITY = "com.danamir.batterymonitor.status";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/status");

    private static final String COLUMN_DATA = "data";
    private static final String PREF_STATUS_DATA = "status_data";

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
        String jsonData = prefs.getString(PREF_STATUS_DATA, "[]");

        MatrixCursor cursor = new MatrixCursor(new String[]{COLUMN_DATA});
        cursor.addRow(new Object[]{jsonData});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.item/vnd." + AUTHORITY + ".status";
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
            prefs.edit().putString(PREF_STATUS_DATA, jsonData).commit();

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
        prefs.edit().putString(PREF_STATUS_DATA, "[]").commit();

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
}
