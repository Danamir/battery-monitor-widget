package com.danamir.batterymonitor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;
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
			try {
				setPreferencesFromResource(R.xml.preferences, rootKey);
			} catch (ClassCastException e) {
				Log.e("SettingsFragment", "Could not load preferences", e);
			}

            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());

            // Migrate old preferences
            android.content.SharedPreferences.Editor editor = prefs.edit();
            boolean needsMigration = false;

            // Migrate color preferences
            if (!prefs.contains("main_color")) {
                if (prefs.contains("background_color")) {
                    int color = prefs.getInt("background_color", 0x80000000);
                    editor.putInt("main_color", color).remove("background_color");
                    needsMigration = true;
                } else if (prefs.contains("background_alpha")) {
                    int alpha = Math.round(prefs.getInt("background_alpha", 100) * 255f / 100f);
                    int color = Color.argb(alpha, 0, 0, 0);
                    editor.putInt("main_color", color).remove("background_alpha");
                    needsMigration = true;
                }
            }

            if (needsMigration) {
                editor.apply();
            }

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
            };
            prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

            // Setup preference listeners
            androidx.preference.EditTextPreference displayLengthPref =
                findPreference("display_length_hours");
			androidx.preference.SeekBarPreference horizontalPaddingPref =
       			findPreference("horizontal_padding");
            androidx.preference.SeekBarPreference verticalPaddingPref =
       			findPreference("vertical_padding");
            androidx.preference.Preference backgroundColorPref =
                findPreference("main_color");

            if (displayLengthPref != null) {
                displayLengthPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
                        int hours = Integer.parseInt((String) newValue);
                        if (hours < 1 || hours > 168) {
                            // Limit to 1-168 hours (1 week)
                            return false;
                        }

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

            if (verticalPaddingPref != null) {
				verticalPaddingPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    try {
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
        }

        private void updateColorPreferenceSummary(androidx.preference.Preference colorPref, String key) {
            if (colorPref != null) {
                android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                int color = prefs.getInt(key, 0x80000000);
                int alpha = Color.alpha(color);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                colorPref.setSummary(String.format("ARGB: %d, %d, %d, %d", alpha, red, green, blue));
            }
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

        private Drawable createCheckerPattern() {
            int tileSize = 20; // Size of each checker square in pixels
            int patternSize = tileSize * 2;
            Bitmap bitmap = Bitmap.createBitmap(patternSize, patternSize, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint lightPaint = new Paint();
            lightPaint.setColor(0xFFCCCCCC); // Light gray
            lightPaint.setStyle(Paint.Style.FILL);

            Paint darkPaint = new Paint();
            darkPaint.setColor(0xFF999999); // Dark gray
            darkPaint.setStyle(Paint.Style.FILL);

            // Draw checker pattern
            canvas.drawRect(0, 0, tileSize, tileSize, lightPaint);
            canvas.drawRect(tileSize, 0, patternSize, tileSize, darkPaint);
            canvas.drawRect(0, tileSize, tileSize, patternSize, darkPaint);
            canvas.drawRect(tileSize, tileSize, patternSize, patternSize, lightPaint);

            BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
            drawable.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
            return drawable;
        }

        private void showColorPickerDialog(String colorKey, String title) {
            android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            int currentColor = prefs.getInt(colorKey, 0x80000000);

            View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_color_picker, null);
            View checkerBackground = dialogView.findViewById(R.id.checker_background);
            TextView colorPreview = dialogView.findViewById(R.id.color_preview);
            SeekBar alphaSeekBar = dialogView.findViewById(R.id.alpha_seekbar);
            SeekBar redSeekBar = dialogView.findViewById(R.id.red_seekbar);
            SeekBar greenSeekBar = dialogView.findViewById(R.id.green_seekbar);
            SeekBar blueSeekBar = dialogView.findViewById(R.id.blue_seekbar);
            TextView alphaValue = dialogView.findViewById(R.id.alpha_value);
            TextView redValue = dialogView.findViewById(R.id.red_value);
            TextView greenValue = dialogView.findViewById(R.id.green_value);
            TextView blueValue = dialogView.findViewById(R.id.blue_value);

            // Set checker pattern background
            checkerBackground.setBackground(createCheckerPattern());

            // Set initial values
            alphaSeekBar.setProgress(Color.alpha(currentColor));
            redSeekBar.setProgress(Color.red(currentColor));
            greenSeekBar.setProgress(Color.green(currentColor));
            blueSeekBar.setProgress(Color.blue(currentColor));
            alphaValue.setText(String.valueOf(Color.alpha(currentColor)));
            redValue.setText(String.valueOf(Color.red(currentColor)));
            greenValue.setText(String.valueOf(Color.green(currentColor)));
            blueValue.setText(String.valueOf(Color.blue(currentColor)));
            colorPreview.setBackgroundColor(currentColor);

            // Update preview on seekbar change
            SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    // Update value displays
                    alphaValue.setText(String.valueOf(alphaSeekBar.getProgress()));
                    redValue.setText(String.valueOf(redSeekBar.getProgress()));
                    greenValue.setText(String.valueOf(greenSeekBar.getProgress()));
                    blueValue.setText(String.valueOf(blueSeekBar.getProgress()));

                    // Update color preview
                    int color = Color.argb(
                        alphaSeekBar.getProgress(),
                        redSeekBar.getProgress(),
                        greenSeekBar.getProgress(),
                        blueSeekBar.getProgress()
                    );
                    colorPreview.setBackgroundColor(color);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            };

            alphaSeekBar.setOnSeekBarChangeListener(listener);
            redSeekBar.setOnSeekBarChangeListener(listener);
            greenSeekBar.setOnSeekBarChangeListener(listener);
            blueSeekBar.setOnSeekBarChangeListener(listener);

            new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    int color = Color.argb(
                        alphaSeekBar.getProgress(),
                        redSeekBar.getProgress(),
                        greenSeekBar.getProgress(),
                        blueSeekBar.getProgress()
                    );
                    prefs.edit().putInt(colorKey, color).apply();
                })
                .setNegativeButton("Cancel", null)
                .show();
        }

        private void showEventLogDialog() {
            BatteryDataManager dataManager = BatteryDataManager.getInstance(getContext());
            List<String> eventLog = dataManager.getEventLog();

            // Create scrollable text view
            android.widget.TextView textView = new android.widget.TextView(getContext());
            textView.setPadding(40, 40, 40, 40);
            textView.setTextIsSelectable(true);
            textView.setTextSize(12);

            if (eventLog.isEmpty()) {
                textView.setText(R.string.event_log_empty);
            } else {
                // Display in reverse order (newest first)
                StringBuilder logText = new StringBuilder();
                for (int i = eventLog.size() - 1; i >= 0; i--) {
                    logText.append(eventLog.get(i));
                    if (i > 0) {
                        logText.append("\n");
                    }
                }
                textView.setText(logText.toString());
            }

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
