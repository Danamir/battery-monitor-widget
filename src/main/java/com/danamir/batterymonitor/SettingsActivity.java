package com.danamir.batterymonitor;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import java.util.List;

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
        private android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            // Register a global preference change listener for immediate updates
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            preferenceChangeListener = (sharedPreferences, key) -> {
				List<String> ignoredKeys = List.of("ignored_pref_key_example");
				if (ignoredKeys.contains(key)) {
					return;
				}
				BatteryWidgetProvider.updateAllWidgets(getContext());
            };
            prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

            // Update widgets when preferences change
            androidx.preference.EditTextPreference displayLengthPref =
                findPreference("display_length_hours");
            androidx.preference.EditTextPreference horizontalPaddingPref =
                findPreference("horizontal_padding");
            androidx.preference.EditTextPreference verticalPaddingPref =
                findPreference("vertical_padding");
            androidx.preference.SeekBarPreference backgroundAlphaPref =
                findPreference("background_alpha");

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

            if (horizontalPaddingPref != null) {
                horizontalPaddingPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int padding = Integer.parseInt((String) newValue);
                        if (padding < 0 || padding > 200) {
                            return false;
                        }
                        BatteryWidgetProvider.updateAllWidgets(getContext());
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (verticalPaddingPref != null) {
                verticalPaddingPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int padding = Integer.parseInt((String) newValue);
                        if (padding < 0 || padding > 200) {
                            return false;
                        }
                        BatteryWidgetProvider.updateAllWidgets(getContext());
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (backgroundAlphaPref != null) {
                backgroundAlphaPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int alpha = (int)newValue;
                        if (alpha < 0 || alpha > 100) {
                            return false;
                        }
						SeekBarPreference seekBarPreference = (SeekBarPreference) preference;
						int increment = seekBarPreference.getSeekBarIncrement();
						float floatValue = (int) newValue;
						int rounded = Math.round(floatValue / increment);
						int finalValue = rounded * increment;

						if (finalValue != floatValue) {
							seekBarPreference.setValue(finalValue);
							return false;
						}
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            // Unregister listener to prevent memory leaks
            if (preferenceChangeListener != null) {
                android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
            }
        }
    }
}
