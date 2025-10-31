package com.danamir.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

/**
 * Manages color settings for the battery monitor widget.
 * Handles color migration, picker dialogs, and color preference updates.
 */
public class ColorSettingsManager {
    private final Context context;
    private final SharedPreferences prefs;

    public ColorSettingsManager(Context context) {
        this.context = context;
        this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Migrates old color preferences to new format if needed.
     */
    public void migrateColorPreferences() {
        SharedPreferences.Editor editor = prefs.edit();
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
    }

    /**
     * Gets the color value for a given preference key.
     *
     * @param key The preference key
     * @param defaultColor The default color if not found
     * @return The color value
     */
    public int getColor(String key, int defaultColor) {
        return prefs.getInt(key, defaultColor);
    }

    /**
     * Sets the color value for a given preference key.
     *
     * @param key The preference key
     * @param color The color value to set
     */
    public void setColor(String key, int color) {
        prefs.edit().putInt(key, color).apply();
    }

    /**
     * Creates a checker pattern drawable for transparency preview.
     *
     * @return Drawable with checker pattern
     */
    public Drawable createCheckerPattern() {
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

        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        return drawable;
    }

    /**
     * Formats a color value as an ARGB summary string.
     *
     * @param color The color value
     * @return Formatted ARGB string (e.g., "ARGB: 128, 255, 0, 0")
     */
    public String formatColorSummary(int color) {
        int alpha = Color.alpha(color);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return String.format("ARGB: %d, %d, %d, %d", alpha, red, green, blue);
    }

    /**
     * Shows a color picker dialog for the specified color preference.
     *
     * @param colorKey The preference key for the color
     * @param title The dialog title
     */
    public void showColorPickerDialog(String colorKey, String title) {
        int currentColor = prefs.getInt(colorKey, 0x80000000);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null);
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
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };

        alphaSeekBar.setOnSeekBarChangeListener(listener);
        redSeekBar.setOnSeekBarChangeListener(listener);
        greenSeekBar.setOnSeekBarChangeListener(listener);
        blueSeekBar.setOnSeekBarChangeListener(listener);

        new AlertDialog.Builder(context)
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
}
