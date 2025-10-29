package com.danamir.batterymonitor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
            setPreferencesFromResource(R.xml.preferences, rootKey);

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
				if ("main_color".equals(key)) {
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
                updateColorPreferenceSummary();
                backgroundColorPref.setOnPreferenceClickListener(preference -> {
                    showColorPickerDialog();
                    return true;
                });
            }
        }

        private void updateColorPreferenceSummary() {
            androidx.preference.Preference colorPref = findPreference("main_color");
            if (colorPref != null) {
                android.content.SharedPreferences prefs =
                    androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
                int color = prefs.getInt("main_color", 0x80000000);
                int alpha = Color.alpha(color);
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                colorPref.setSummary(String.format("R: %d, G: %d, B: %d, Alpha: %d", red, green, blue, alpha));
            }
        }

        private void updateColorPreview() {
            androidx.preference.Preference colorPref = findPreference("main_color");
            if (colorPref != null) {
                // Force preference to recreate its view
                getPreferenceScreen().removePreference(colorPref);
                getPreferenceScreen().addPreference(colorPref);
                updateColorPreferenceSummary();
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

        private void showColorPickerDialog() {
            android.content.SharedPreferences prefs =
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(getContext());
            int currentColor = prefs.getInt("main_color", 0x80000000);

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
                .setTitle("Select Background Color")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    int color = Color.argb(
                        alphaSeekBar.getProgress(),
                        redSeekBar.getProgress(),
                        greenSeekBar.getProgress(),
                        blueSeekBar.getProgress()
                    );
                    prefs.edit().putInt("main_color", color).apply();
                    updateColorPreferenceSummary();
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
