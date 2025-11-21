package com.danamir.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/**
 * Custom SeekBar preference that allows direct numeric input by clicking on the value display
 */
public class CustomSeekBarPreference extends Preference {

    private int mDefaultValue = 0;
    private int mCurrentValue = 0;
    private int mMinValue = 0;
    private int mMaxValue = 100;
    private int mSeekBarIncrement = 1;

    public CustomSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public CustomSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public CustomSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CustomSeekBarPreference(Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutResource(R.layout.preference_float_seekbar);

        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomSeekBarPreference);
            try {
                // Read default value - handle both integer and string formats
                if (a.hasValue(R.styleable.CustomSeekBarPreference_android_defaultValue)) {
                    mDefaultValue = a.getInt(R.styleable.CustomSeekBarPreference_android_defaultValue, 0);
                }
                mMinValue = a.getInt(R.styleable.CustomSeekBarPreference_android_min, 0);
                mMaxValue = a.getInt(R.styleable.CustomSeekBarPreference_android_max, 100);
                mSeekBarIncrement = a.getInt(R.styleable.CustomSeekBarPreference_seekBarIncrement, 1);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        // Use the default value passed by the framework if available
        if (defaultValue != null) {
            if (defaultValue instanceof Integer) {
                mDefaultValue = (Integer) defaultValue;
            } else if (defaultValue instanceof String) {
                try {
                    mDefaultValue = Integer.parseInt((String) defaultValue);
                } catch (NumberFormatException e) {
                    // Keep the default from init()
                }
            }
        }

        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            if (!prefs.contains(getKey())) {
                // Initialize value if not set
                prefs.edit().putInt(getKey(), mDefaultValue).apply();
                mCurrentValue = mDefaultValue;
            } else {
                // Try to get as int first (new format)
                try {
                    mCurrentValue = prefs.getInt(getKey(), mDefaultValue);
                } catch (ClassCastException e) {
                    // Old format (string), migrate to int
                    try {
                        String stringValue = prefs.getString(getKey(), String.valueOf(mDefaultValue));
                        mCurrentValue = Integer.parseInt(stringValue);
                        // Migrate to int format
                        prefs.edit().putInt(getKey(), mCurrentValue).apply();
                    } catch (NumberFormatException ex) {
                        mCurrentValue = mDefaultValue;
                        prefs.edit().putInt(getKey(), mDefaultValue).apply();
                    }
                }
            }
        }
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        SeekBar seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        TextView seekBarValue = (TextView) holder.findViewById(R.id.seekbar_value);

        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            mCurrentValue = prefs.getInt(getKey(), mDefaultValue);
        }

        // Setup SeekBar
        if (seekBar != null && seekBarValue != null) {
            int steps = mMaxValue - mMinValue;
            if (mSeekBarIncrement > 1) {
                steps = steps / mSeekBarIncrement;
            }
            
            seekBar.setMax(steps);
            int currentProgress = (mCurrentValue - mMinValue);
            if (mSeekBarIncrement > 1) {
                currentProgress = currentProgress / mSeekBarIncrement;
            }
            seekBar.setProgress(currentProgress);
            seekBarValue.setText(String.valueOf(mCurrentValue));

            // Add click listener to value TextView for direct numeric input
            seekBarValue.setOnClickListener(v -> showNumericInputDialog(seekBar, seekBarValue));

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        int value = mMinValue + (progress * mSeekBarIncrement);
                        seekBarValue.setText(String.valueOf(value));
                        mCurrentValue = value;

                        SharedPreferences prefs = getSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putInt(getKey(), value).apply();
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

    /**
     * Shows a dialog allowing direct numeric input of the value
     */
    private void showNumericInputDialog(SeekBar seekBar, TextView seekBarValue) {
        EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.valueOf(mCurrentValue));
        input.setSelectAllOnFocus(true);

        new AlertDialog.Builder(getContext())
                .setTitle(getTitle())
                .setMessage(String.format("Enter value (%d - %d):", mMinValue, mMaxValue))
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    try {
                        int value = Integer.parseInt(input.getText().toString());
                        
                        // Clamp to min/max
                        value = Math.max(mMinValue, Math.min(mMaxValue, value));
                        
                        // Apply seekbar increment if set
                        if (mSeekBarIncrement > 1) {
                            int rounded = Math.round((float) value / mSeekBarIncrement);
                            value = rounded * mSeekBarIncrement;
                        }
                        
                        // Update UI
                        mCurrentValue = value;
                        seekBarValue.setText(String.valueOf(value));
                        
                        // Update SeekBar position
                        int progress = (value - mMinValue);
                        if (mSeekBarIncrement > 1) {
                            progress = progress / mSeekBarIncrement;
                        }
                        seekBar.setProgress(progress);
                        
                        // Save to preferences
                        SharedPreferences prefs = getSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putInt(getKey(), value).apply();
                        }
                        
                        // Notify widget to update
                        BatteryWidgetProvider.updateAllWidgets(getContext());
                    } catch (NumberFormatException e) {
                        // Invalid input, ignore
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Gets the current value
     */
    public int getValue() {
        return mCurrentValue;
    }

    /**
     * Sets the current value
     */
    public void setValue(int value) {
        mCurrentValue = value;
        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            prefs.edit().putInt(getKey(), value).apply();
        }
        notifyChanged();
    }

    /**
     * Gets the seekbar increment value
     */
    public int getSeekBarIncrement() {
        return mSeekBarIncrement;
    }
}
