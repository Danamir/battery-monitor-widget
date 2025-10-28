package com.danamir.batterymonitor;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        }

        // Set up toolbar
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Update widgets when preferences change
            androidx.preference.EditTextPreference displayLengthPref =
                findPreference("display_length_hours");

            if (displayLengthPref != null) {
                displayLengthPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int hours = Integer.parseInt((String) newValue);
                        if (hours < 1 || hours > 168) {
                            // Limit to 1-168 hours (1 week)
                            return false;
                        }

                        // Update all widgets with new setting
                        BatteryWidgetProvider.updateAllWidgets(getContext());

                        // Clean up old data beyond the new display length
                        BatteryDataManager dataManager = BatteryDataManager.getInstance(getContext());
                        dataManager.clearOldData(hours);

                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }
        }
    }
}
