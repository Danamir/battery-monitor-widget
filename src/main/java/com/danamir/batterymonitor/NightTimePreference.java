package com.danamir.batterymonitor;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class NightTimePreference extends Preference {

    private String nightStartTime = "20:00";
    private String nightEndTime = "08:00";

    public NightTimePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setLayoutResource(R.layout.preference_night_time);
    }

    public NightTimePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayoutResource(R.layout.preference_night_time);
    }

    public NightTimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_night_time);
    }

    public NightTimePreference(Context context) {
        super(context);
        setLayoutResource(R.layout.preference_night_time);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        SharedPreferences prefs = getSharedPreferences();
        if (prefs != null) {
            nightStartTime = prefs.getString("night_start", "20:00");
            nightEndTime = prefs.getString("night_end", "08:00");
        }

        TextView nightStartValue = (TextView) holder.findViewById(R.id.night_start_value);
        TextView nightEndValue = (TextView) holder.findViewById(R.id.night_end_value);
        View nightStartContainer = holder.findViewById(R.id.night_start_container);
        View nightEndContainer = holder.findViewById(R.id.night_end_container);

        if (nightStartValue != null) {
            nightStartValue.setText(nightStartTime);
        }

        if (nightEndValue != null) {
            nightEndValue.setText(nightEndTime);
        }

        if (nightStartContainer != null) {
            nightStartContainer.setOnClickListener(v -> showTimePickerDialog("night_start", "Night Start Time", nightStartTime, nightStartValue));
        }

        if (nightEndContainer != null) {
            nightEndContainer.setOnClickListener(v -> showTimePickerDialog("night_end", "Night End Time", nightEndTime, nightEndValue));
        }
    }

    private void showTimePickerDialog(String key, String title, String currentTime, TextView valueView) {
        // Parse current time
        int hour = 20;
        int minute = 0;
        try {
            String[] parts = currentTime.split(":");
            if (parts.length == 2) {
                hour = Integer.parseInt(parts[0]);
                minute = Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            // Use default values
        }

        TimePickerDialog timePickerDialog = new TimePickerDialog(
            getContext(),
            (view, selectedHour, selectedMinute) -> {
                // Format time as HH:mm
                String formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute);

                // Save to preferences
                SharedPreferences prefs = getSharedPreferences();
                if (prefs != null) {
                    prefs.edit().putString(key, formattedTime).apply();
                }

                // Update the displayed value
                if (valueView != null) {
                    valueView.setText(formattedTime);
                }

                // Update stored value
                if (key.equals("night_start")) {
                    nightStartTime = formattedTime;
                } else {
                    nightEndTime = formattedTime;
                }

                // Notify change
                notifyChanged();

                // Update widget
                BatteryWidgetProvider.updateAllWidgets(getContext());
            },
            hour,
            minute,
            true  // 24-hour format
        );

        timePickerDialog.setTitle(title);
        timePickerDialog.show();
    }
}
