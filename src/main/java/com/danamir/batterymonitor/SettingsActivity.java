package com.danamir.batterymonitor;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AlertDialog;
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

        // Start battery monitor service to display notification
        Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
        startService(serviceIntent);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
        private ColorSettingsManager colorSettingsManager;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            try {
                setPreferencesFromResource(R.xml.preferences, rootKey);
            } catch (ClassCastException e) {
                Log.e("SettingsFragment", "Could not load preferences", e);
            }

            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());

            // Initialize color settings manager
            colorSettingsManager = new ColorSettingsManager(getContext());

            // Migrate old preferences
            colorSettingsManager.migrateColorPreferences();

            // Register a global preference change listener for immediate updates
            preferenceChangeListener = (sharedPreferences, key) -> {
                List<String> ignoredKeys = List.of("ignored_pref_key_example");
                if (ignoredKeys.contains(key)) {
                    return;
                }
                BatteryWidgetProvider.updateAllWidgets(getContext());
                if ("main_color".equals(key) || "text_color".equals(key) || "grid_color".equals(key)) {
                    updateColorPreview();
                }
                // Update notification for relevant settings
                if ("low_target_percent".equals(key) || "high_target_percent".equals(key) || "display_length_hours".equals(key)
                    || "usage_calculation_time".equals(key) || "rounded_time_estimates".equals(key)) {
                    Intent serviceIntent = new Intent(getContext(), BatteryMonitorService.class);
                    getContext().startService(serviceIntent);
                }
            };
            prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

            // Setup preference listeners
            androidx.preference.EditTextPreference displayLengthPref =
                    findPreference("display_length_hours");
            androidx.preference.SeekBarPreference horizontalPaddingPref =
                    findPreference("horizontal_padding");
            androidx.preference.SeekBarPreference verticalPaddingPref =
                    findPreference("vertical_padding");
            androidx.preference.SeekBarPreference subdivisionPref =
                    findPreference("gridVerticalIntervalSubdivisionPref");
            androidx.preference.Preference backgroundColorPref =
                    findPreference("main_color");
            androidx.preference.SeekBarPreference usageCalculationTimePref =
                    findPreference("usage_calculation_time");

            if (displayLengthPref != null) {
                displayLengthPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int hours = Integer.parseInt((String) newValue);
                        if (hours < 1 || hours > 168) {
                            // Limit to 1-168 hours (1 week)
                            return false;
                        }

                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (horizontalPaddingPref != null) {
                horizontalPaddingPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        SeekBarPreference seekBarPreference = (SeekBarPreference) preference;
                        int increment = seekBarPreference.getSeekBarIncrement();
                        if (increment > 1) {
                            float floatValue = (int) newValue;
                            int rounded = Math.round(floatValue / increment);
                            int finalValue = rounded * increment;

                            if (finalValue != floatValue) {
                                seekBarPreference.setValue(finalValue);
                                return false;
                            }
                        }
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (verticalPaddingPref != null) {
                verticalPaddingPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        SeekBarPreference seekBarPreference = (SeekBarPreference) preference;
                        int increment = seekBarPreference.getSeekBarIncrement();
                        if(increment > 1) {
                            float floatValue = (int) newValue;
                            int rounded = Math.round(floatValue / increment);
                            int finalValue = rounded * increment;

                            if (finalValue != floatValue) {
                                seekBarPreference.setValue(finalValue);
                                return false;

                            }
                        }
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
            }

            if (subdivisionPref != null) {
                // Ensure current value is at least 1
                if (subdivisionPref.getValue() < 1) {
                    subdivisionPref.setValue(1);
                }

                // Enforce minimum value of 1
                subdivisionPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = (int) newValue;
                    if (value < 1) {
                        return false; // Reject values less than 1
                    }
                    return true;
                });
            }

            if (usageCalculationTimePref != null) {
                // Ensure current value is at least 1
                if (usageCalculationTimePref.getValue() < 1) {
                    usageCalculationTimePref.setValue(1);
                }

                // Enforce minimum value of 1
                usageCalculationTimePref.setOnPreferenceChangeListener((preference, newValue) -> {
                    int value = (int) newValue;
                    if (value < 1) {
                        return false; // Reject values less than 1
                    }
                    return true;
                });
            }

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

            androidx.preference.Preference eventLogPref = findPreference("view_event_log");
            if (eventLogPref != null) {
                eventLogPref.setOnPreferenceClickListener(preference -> {
                    showEventLogDialog();
                    return true;
                });
            }

            androidx.preference.Preference resetStatsPref = findPreference("reset_statistics");
            if (resetStatsPref != null) {
                resetStatsPref.setOnPreferenceClickListener(preference -> {
                    showResetStatisticsDialog();
                    return true;
                });
            }

            androidx.preference.Preference clearPrefsPref = findPreference("clear_preferences");
            if (clearPrefsPref != null) {
                clearPrefsPref.setOnPreferenceClickListener(preference -> {
                    showClearPreferencesDialog();
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
        }

        private void showColorPickerDialog(String colorKey, String title) {
            colorSettingsManager.showColorPickerDialog(colorKey, title);
        }

        private void showEventLogDialog() {
            BatteryDataManager dataManager = BatteryDataManager.getInstance(getContext());
            List<String> eventLog = dataManager.getEventLog();

            // Create scrollable text view
            android.widget.TextView textView = new android.widget.TextView(getContext());
            textView.setPadding(40, 40, 40, 40);
            textView.setTextIsSelectable(true);
            textView.setTextSize(12);

            StringBuilder logText = new StringBuilder();

            // Add all-time statistics at the top
            double meanChargeRate = DataProvider.getMeanChargeRate(getContext());
            double meanDischargeRate = DataProvider.getMeanDischargeRate(getContext());
            long totalChargeTime = DataProvider.getTotalChargeTime(getContext());
            long totalDischargeTime = DataProvider.getTotalDischargeTime(getContext());

            logText.append("=== All-Time Statistics ===\n");

            if (totalChargeTime > 0) {
                logText.append(String.format("Mean Charge Rate: %.2f %%/hour\n", meanChargeRate));
                logText.append(String.format("Total Charge Time: %s\n", BatteryUtils.formatDuration(totalChargeTime)));
            } else {
                logText.append("Mean Charge Rate: No data\n");
                logText.append("Total Charge Time: No data\n");
            }

            if (totalDischargeTime > 0) {
                logText.append(String.format("Mean Discharge Rate: %.2f %%/hour\n", meanDischargeRate));
                logText.append(String.format("Total Discharge Time: %s\n", BatteryUtils.formatDuration(totalDischargeTime)));
            } else {
                logText.append("Mean Discharge Rate: No data\n");
                logText.append("Total Discharge Time: No data\n");
            }

            logText.append("\n=== Event Log ===\n");

            if (eventLog.isEmpty()) {
                logText.append(getString(R.string.event_log_empty));
            } else {
                // Display in reverse order (newest first)
                for (int i = eventLog.size() - 1; i >= 0; i--) {
                    logText.append(eventLog.get(i));
                    if (i > 0) {
                        logText.append("\n");
                    }
                }
            }

            textView.setText(logText.toString());

            // Wrap in ScrollView
            android.widget.ScrollView scrollView = new android.widget.ScrollView(getContext());
            scrollView.addView(textView);

            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.event_log_dialog_title)
                    .setView(scrollView)
                    .setPositiveButton("OK", null)
                    .setNeutralButton(R.string.event_log_clear, (dialog, which) -> {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Clear Event Log")
                                .setMessage("Are you sure you want to clear the event log?")
                                .setPositiveButton("Clear", (d, w) -> {
                                    dataManager.clearEventLog();
                                    android.widget.Toast.makeText(getContext(), "Event log cleared", android.widget.Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    })
                    .show();
        }

        private void showResetStatisticsDialog() {
            new AlertDialog.Builder(getContext())
                    .setTitle("Reset All-Time Statistics")
                    .setMessage("This will reset all-time charge/discharge rates and times to zero.\n\nAre you sure?")
                    .setPositiveButton("Reset", (dialog, which) -> {
                        DataProvider.resetStats(getContext());
                        android.widget.Toast.makeText(getContext(), "Statistics reset", android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }

        private void showClearPreferencesDialog() {
            new AlertDialog.Builder(getContext())
                    .setTitle("Clear App Preferences")
                    .setMessage("This will reset all settings to their default values. Battery data will be preserved.\n\nAre you sure?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                        android.content.SharedPreferences.Editor editor = prefs.edit();
                        editor.clear();
                        editor.apply();

                        // Refresh the preference screen
                        getPreferenceScreen().removeAll();
                        setPreferencesFromResource(R.xml.preferences, null);

                        android.widget.Toast.makeText(getContext(), "Preferences cleared", android.widget.Toast.LENGTH_SHORT).show();
                        BatteryWidgetProvider.updateAllWidgets(getContext());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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
}
