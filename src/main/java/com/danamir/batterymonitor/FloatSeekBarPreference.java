package com.danamir.batterymonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class FloatSeekBarPreference extends Preference {

    private float mDefaultValue = 2.0f;
    private float mCurrentValue;
    private float mMinValue = 0.0f;
    private float mMaxValue = 10.0f;
    private float mStepSize = 0.1f;
    private boolean mUseLogScale = false;

    public FloatSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    public FloatSeekBarPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public FloatSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public FloatSeekBarPreference(Context context) {
        super(context);
        init(context, null);
    }

    private void init(Context context, AttributeSet attrs) {
        setLayoutResource(R.layout.preference_float_seekbar);

        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FloatSeekBarPreference);
            try {
                mDefaultValue = a.getFloat(R.styleable.FloatSeekBarPreference_defaultFloatValue, 2.0f);
                mMinValue = a.getFloat(R.styleable.FloatSeekBarPreference_minFloatValue, 0.0f);
                mMaxValue = a.getFloat(R.styleable.FloatSeekBarPreference_maxFloatValue, 10.0f);
                mStepSize = a.getFloat(R.styleable.FloatSeekBarPreference_floatStepSize, 0.1f);
                mUseLogScale = a.getBoolean(R.styleable.FloatSeekBarPreference_useLogScale, false);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            // Initialize value if not set
            if (!prefs.contains(getKey())) {
                prefs.edit().putFloat(getKey(), mDefaultValue).apply();
            }
            mCurrentValue = prefs.getFloat(getKey(), mDefaultValue);
        }
    }

    /**
     * Converts a linear progress value to a logarithmic value
     * @param progress Linear progress from 0 to max
     * @param maxProgress Maximum progress value
     * @return Logarithmic value between mMinValue and mMaxValue
     */
    private float progressToLogValue(int progress, int maxProgress) {
        if (maxProgress == 0) return mMinValue;
        float ratio = (float) progress / maxProgress;
        float logMin = (float) Math.log(mMinValue + 1);
        float logMax = (float) Math.log(mMaxValue + 1);
        float logValue = logMin + ratio * (logMax - logMin);
        return (float) Math.exp(logValue) - 1;
    }

    /**
     * Converts a logarithmic value to a linear progress value
     * @param value Logarithmic value between mMinValue and mMaxValue
     * @param maxProgress Maximum progress value
     * @return Linear progress from 0 to max
     */
    private int logValueToProgress(float value, int maxProgress) {
        float logMin = (float) Math.log(mMinValue + 1);
        float logMax = (float) Math.log(mMaxValue + 1);
        float logValue = (float) Math.log(value + 1);
        float ratio = (logValue - logMin) / (logMax - logMin);
        return Math.round(ratio * maxProgress);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        SeekBar seekBar = (SeekBar) holder.findViewById(R.id.seekbar);
        TextView seekBarValue = (TextView) holder.findViewById(R.id.seekbar_value);

        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            mCurrentValue = prefs.getFloat(getKey(), mDefaultValue);
        }

        // Setup SeekBar
        if (seekBar != null && seekBarValue != null) {
            int steps;
            int currentProgress;

            if (mUseLogScale) {
                // For log scale, use a fixed number of steps for smooth progression
                steps = 1000;
                seekBar.setMax(steps);
                currentProgress = logValueToProgress(mCurrentValue, steps);
            } else {
                // Convert float range to integer range for SeekBar (linear)
                steps = (int) ((mMaxValue - mMinValue) / mStepSize);
                seekBar.setMax(steps);
                currentProgress = (int) ((mCurrentValue - mMinValue) / mStepSize);
            }

            seekBar.setProgress(currentProgress);
            seekBarValue.setText(String.format("%.1f", mCurrentValue));

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        float value;
                        if (mUseLogScale) {
                            value = progressToLogValue(progress, seekBar.getMax());
                            // Round to step size
                            value = Math.round(value / mStepSize) * mStepSize;
                            // Clamp to min/max
                            value = Math.max(mMinValue, Math.min(mMaxValue, value));
                        } else {
                            value = mMinValue + (progress * mStepSize);
                        }

                        seekBarValue.setText(String.format("%.1f", value));
                        mCurrentValue = value;

                        SharedPreferences prefs = getSharedPreferences();
                        if (prefs != null) {
                            prefs.edit().putFloat(getKey(), value).apply();
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
}
