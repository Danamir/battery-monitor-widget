package com.danamir.batterymonitor;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import androidx.preference.PreferenceFragmentCompat;

public class ZonesPreference extends PreferenceFragmentCompat {
    private static final String[] ZONE_KEYS = {
        "zone_action_top_left",
        "zone_action_top",
        "zone_action_top_right",
        "zone_action_left",
        "zone_action_center",
        "zone_action_right",
        "zone_action_bottom_left",
        "zone_action_bottom",
        "zone_action_bottom_right"
    };
    private static final String CENTER_ZONE_KEY = "zone_action_center";
    private static final String OPEN_PREFERENCES_VALUE = "open_preferences";

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
            // Check if a zone preference was changed
            if (isZoneKey(key)) {
                ensureAtLeastOneOpenPreferences(sharedPreferences);
            }
            BatteryWidgetProvider.updateAllWidgets(getContext());
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    private boolean isZoneKey(String key) {
        if (key == null) return false;
        for (String zoneKey : ZONE_KEYS) {
            if (zoneKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private void ensureAtLeastOneOpenPreferences(SharedPreferences prefs) {
        // Check if any zone has "open_preferences"
        boolean hasOpenPreferences = false;
        for (String zoneKey : ZONE_KEYS) {
            String value = prefs.getString(zoneKey, OPEN_PREFERENCES_VALUE);
            if (OPEN_PREFERENCES_VALUE.equals(value)) {
                hasOpenPreferences = true;
                break;
            }
        }

        // If no zone has "open_preferences", set center to it
        if (!hasOpenPreferences) {
            prefs.edit().putString(CENTER_ZONE_KEY, OPEN_PREFERENCES_VALUE).apply();

            // Show message to user
            android.widget.Toast.makeText(
                getContext(),
                "At least one zone must open preferences. Center zone reset.",
                android.widget.Toast.LENGTH_LONG
            ).show();

            // Refresh the preference display
            androidx.preference.ListPreference centerPref = findPreference(CENTER_ZONE_KEY);
            if (centerPref != null) {
                centerPref.setValue(OPEN_PREFERENCES_VALUE);
            }
        }
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
