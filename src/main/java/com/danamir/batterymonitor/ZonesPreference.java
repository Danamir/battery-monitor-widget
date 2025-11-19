package com.danamir.batterymonitor;

import android.os.Bundle;
import android.util.Log;
import androidx.preference.PreferenceFragmentCompat;

public class ZonesPreference extends PreferenceFragmentCompat {
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            setPreferencesFromResource(R.xml.zones_preferences, rootKey);
        } catch (ClassCastException e) {
            Log.e("ZonesPreference", "Could not load preferences", e);
        }

        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());

        // Register a global preference change listener for immediate updates
        preferenceChangeListener = (sharedPreferences, key) -> {
            BatteryWidgetProvider.updateAllWidgets(getContext());
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister listener to prevent memory leaks
        if (preferenceChangeListener != null) {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
    }
}
