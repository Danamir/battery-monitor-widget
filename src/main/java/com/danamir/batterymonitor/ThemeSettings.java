package com.danamir.batterymonitor;

import android.os.Bundle;
import android.util.Log;
import androidx.preference.PreferenceFragmentCompat;

public class ThemeSettings extends PreferenceFragmentCompat {
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private ColorSettingsManager colorSettingsManager;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        try {
            setPreferencesFromResource(R.xml.theme_colors_preferences, rootKey);
        } catch (ClassCastException e) {
            Log.e("ThemeSettings", "Could not load preferences", e);
        }

        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());

        // Initialize color settings manager
        colorSettingsManager = new ColorSettingsManager(getContext());

        // Migrate old preferences
        colorSettingsManager.migrateColorPreferences();

        // Register a global preference change listener for immediate updates
        preferenceChangeListener = (sharedPreferences, key) -> {
            BatteryWidgetProvider.updateAllWidgets(getContext());
            if ("main_color".equals(key) || "text_color".equals(key) || "grid_color".equals(key) ||
                "graph_line_color".equals(key) || "graph_fill_color".equals(key) ||
                "graph_night_fill_color".equals(key) || "night_start".equals(key) || "night_end".equals(key) ||
                "charging_line_color".equals(key) || "battery_low_color".equals(key) ||
                "battery_critical_color".equals(key) || "user_present_color".equals(key) ||
                "battery_low_level".equals(key) || "battery_critical_level".equals(key) ||
                "battery_blend_value".equals(key)) {
                updateColorPreview();
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        androidx.preference.Preference backgroundColorPref = findPreference("main_color");
        if (backgroundColorPref != null) {
            updateColorPreferenceSummary(backgroundColorPref, "main_color");
            backgroundColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("main_color", "Background Color");
                return true;
            });
        }

        androidx.preference.Preference textColorPref = findPreference("text_color");
        if (textColorPref != null) {
            updateColorPreferenceSummary(textColorPref, "text_color");
            textColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("text_color", "Text Color");
                return true;
            });
        }

        androidx.preference.Preference gridColorPref = findPreference("grid_color");
        if (gridColorPref != null) {
            updateColorPreferenceSummary(gridColorPref, "grid_color");
            gridColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("grid_color", "Grid Color");
                return true;
            });
        }

        androidx.preference.Preference graphLineColorPref = findPreference("graph_line_color");
        if (graphLineColorPref != null) {
            updateColorPreferenceSummary(graphLineColorPref, "graph_line_color");
            graphLineColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("graph_line_color", "Graph Line Color");
                return true;
            });
        }

        androidx.preference.Preference graphFillColorPref = findPreference("graph_fill_color");
        if (graphFillColorPref != null) {
            updateColorPreferenceSummary(graphFillColorPref, "graph_fill_color");
            graphFillColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("graph_fill_color", "Graph Fill Color");
                return true;
            });
        }

        androidx.preference.Preference graphNightFillColorPref = findPreference("graph_night_fill_color");
        if (graphNightFillColorPref != null) {
            updateColorPreferenceSummary(graphNightFillColorPref, "graph_night_fill_color");
            graphNightFillColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("graph_night_fill_color", "Night Fill Color");
                return true;
            });
        }

        androidx.preference.Preference chargingLineColorPref = findPreference("charging_line_color");
        if (chargingLineColorPref != null) {
            updateColorPreferenceSummary(chargingLineColorPref, "charging_line_color");
            chargingLineColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("charging_line_color", "Charging Line Color");
                return true;
            });
        }

        androidx.preference.Preference userPresentColorPref = findPreference("user_present_color");
        if (userPresentColorPref != null) {
            updateColorPreferenceSummary(userPresentColorPref, "user_present_color");
            userPresentColorPref.setOnPreferenceClickListener(preference -> {
                showColorPickerDialog("user_present_color", "User Present Color");
                return true;
            });
        }
    }

    private void updateColorPreferenceSummary(androidx.preference.Preference colorPref, String key) {
        // Summary is now displayed from XML, no need to set it dynamically
    }

    private void updateColorPreview() {
        // Update all color preferences
        androidx.preference.Preference mainColorPref = findPreference("main_color");
        androidx.preference.Preference textColorPref = findPreference("text_color");
        androidx.preference.Preference gridColorPref = findPreference("grid_color");
        androidx.preference.Preference graphLineColorPref = findPreference("graph_line_color");
        androidx.preference.Preference graphFillColorPref = findPreference("graph_fill_color");
        androidx.preference.Preference graphNightFillColorPref = findPreference("graph_night_fill_color");
        androidx.preference.Preference chargingLineColorPref = findPreference("charging_line_color");
        androidx.preference.Preference batteryLowColorPref = findPreference("battery_low_color");
        androidx.preference.Preference batteryCriticalColorPref = findPreference("battery_critical_color");
        androidx.preference.Preference userPresentColorPref = findPreference("user_present_color");

        if (mainColorPref != null) {
            getPreferenceScreen().removePreference(mainColorPref);
            getPreferenceScreen().addPreference(mainColorPref);
            updateColorPreferenceSummary(mainColorPref, "main_color");
        }
        if (textColorPref != null) {
            getPreferenceScreen().removePreference(textColorPref);
            getPreferenceScreen().addPreference(textColorPref);
            updateColorPreferenceSummary(textColorPref, "text_color");
        }
        if (gridColorPref != null) {
            getPreferenceScreen().removePreference(gridColorPref);
            getPreferenceScreen().addPreference(gridColorPref);
            updateColorPreferenceSummary(gridColorPref, "grid_color");
        }
        if (graphLineColorPref != null) {
            getPreferenceScreen().removePreference(graphLineColorPref);
            getPreferenceScreen().addPreference(graphLineColorPref);
            updateColorPreferenceSummary(graphLineColorPref, "graph_line_color");
        }
        if (graphFillColorPref != null) {
            getPreferenceScreen().removePreference(graphFillColorPref);
            getPreferenceScreen().addPreference(graphFillColorPref);
            updateColorPreferenceSummary(graphFillColorPref, "graph_fill_color");
        }
        if (graphNightFillColorPref != null) {
            getPreferenceScreen().removePreference(graphNightFillColorPref);
            getPreferenceScreen().addPreference(graphNightFillColorPref);
            updateColorPreferenceSummary(graphNightFillColorPref, "graph_night_fill_color");
        }
        if (chargingLineColorPref != null) {
            getPreferenceScreen().removePreference(chargingLineColorPref);
            getPreferenceScreen().addPreference(chargingLineColorPref);
            updateColorPreferenceSummary(chargingLineColorPref, "charging_line_color");
        }
        if (batteryLowColorPref != null) {
            getPreferenceScreen().removePreference(batteryLowColorPref);
            getPreferenceScreen().addPreference(batteryLowColorPref);
            updateColorPreferenceSummary(batteryLowColorPref, "battery_low_color");
        }
        if (batteryCriticalColorPref != null) {
            getPreferenceScreen().removePreference(batteryCriticalColorPref);
            getPreferenceScreen().addPreference(batteryCriticalColorPref);
            updateColorPreferenceSummary(batteryCriticalColorPref, "battery_critical_color");
        }
        if (userPresentColorPref != null) {
            getPreferenceScreen().removePreference(userPresentColorPref);
            getPreferenceScreen().addPreference(userPresentColorPref);
            updateColorPreferenceSummary(userPresentColorPref, "user_present_color");
        }
    }

    private void showColorPickerDialog(String colorKey, String title) {
        colorSettingsManager.showColorPickerDialog(colorKey, title);
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
