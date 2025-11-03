package com.danamir.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class BatteryThresholdPreference extends Preference {

    private int mDefaultColorValue = 0x80000000;
    private int mDefaultThresholdValue = 50;
    private String mColorKey;
    private String mThresholdKey;
    private int mCurrentThreshold;
    private int mCurrentColor;
    private ColorSettingsManager colorSettingsManager;

    public BatteryThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public BatteryThresholdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public BatteryThresholdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public BatteryThresholdPreference(Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutResource(R.layout.preference_battery_threshold);
        colorSettingsManager = new ColorSettingsManager(context);

        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BatteryThresholdPreference);
            try {
                mColorKey = a.getString(R.styleable.BatteryThresholdPreference_colorKey);
                mThresholdKey = a.getString(R.styleable.BatteryThresholdPreference_thresholdKey);
                mDefaultColorValue = a.getInteger(R.styleable.BatteryThresholdPreference_defaultColor, 0x80000000);
                mDefaultThresholdValue = a.getInteger(R.styleable.BatteryThresholdPreference_defaultThreshold, 50);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            // Initialize color if not set
            if (!prefs.contains(mColorKey)) {
                prefs.edit().putInt(mColorKey, mDefaultColorValue).apply();
            }
            // Initialize threshold if not set
            if (!prefs.contains(mThresholdKey)) {
                prefs.edit().putInt(mThresholdKey, mDefaultThresholdValue).apply();
            }

            mCurrentColor = prefs.getInt(mColorKey, mDefaultColorValue);
            mCurrentThreshold = prefs.getInt(mThresholdKey, mDefaultThresholdValue);
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View colorSwatchContainer = holder.findViewById(R.id.color_swatch_container);
        View colorCheckerBg = holder.findViewById(R.id.color_checker_bg);
        View colorPreviewIcon = holder.findViewById(R.id.color_preview_icon);
        SeekBar seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        TextView seekBarValue = (TextView) holder.findViewById(R.id.seekbar_value);

        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            mCurrentColor = prefs.getInt(mColorKey, mDefaultColorValue);
            mCurrentThreshold = prefs.getInt(mThresholdKey, mDefaultThresholdValue);
        }

        // Setup color swatch
        if (colorCheckerBg != null && colorPreviewIcon != null) {
            colorCheckerBg.setBackground(createCheckerPatternSmall());
            colorPreviewIcon.setBackgroundColor(mCurrentColor);
        }

        // Setup color swatch click listener
        if (colorSwatchContainer != null) {
            colorSwatchContainer.setOnClickListener(v -> {
                String title = getTitle() != null ? getTitle().toString() : "Color";
                colorSettingsManager.showColorPickerDialog(mColorKey, title, color -> {
                    // Update the color preview when color is changed
                    mCurrentColor = color;
                    if (colorPreviewIcon != null) {
                        colorPreviewIcon.setBackgroundColor(color);
                    }
                    // Notify widget to update
                    BatteryWidgetProvider.updateAllWidgets(getContext());
                }, mCurrentColor);
            });
        }

        // Setup SeekBar
        if (seekBar != null && seekBarValue != null) {
            seekBar.setMax(100);
            seekBar.setProgress(mCurrentThreshold);
            seekBarValue.setText(String.valueOf(mCurrentThreshold));

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        seekBarValue.setText(String.valueOf(progress));
                        mCurrentThreshold = progress;

                        SharedPreferences prefs = getSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putInt(mThresholdKey, progress).apply();
                        }

                        // Notify widget to update
                        BatteryWidgetProvider.updateAllWidgets(getContext());
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }
    }

    private Drawable createCheckerPatternSmall() {
        int tileSize = 10;
        int patternSize = tileSize * 2;
        Bitmap bitmap = Bitmap.createBitmap(patternSize, patternSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint lightPaint = new Paint();
        lightPaint.setColor(0xFFCCCCCC);
        lightPaint.setStyle(Paint.Style.FILL);

        Paint darkPaint = new Paint();
        darkPaint.setColor(0xFF999999);
        darkPaint.setStyle(Paint.Style.FILL);

        canvas.drawRect(0, 0, tileSize, tileSize, lightPaint);
        canvas.drawRect(tileSize, 0, patternSize, tileSize, darkPaint);
        canvas.drawRect(0, tileSize, tileSize, patternSize, darkPaint);
        canvas.drawRect(tileSize, tileSize, patternSize, patternSize, lightPaint);

        BitmapDrawable drawable = new BitmapDrawable(getContext().getResources(), bitmap);
        drawable.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        return drawable;
    }
}
