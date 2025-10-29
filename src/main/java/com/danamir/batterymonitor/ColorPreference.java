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
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

public class ColorPreference extends Preference {

    public ColorPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ColorPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ColorPreference(Context context) {
        super(context);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        View colorCheckerBg = holder.findViewById(R.id.color_checker_bg);
        View colorPreviewIcon = holder.findViewById(R.id.color_preview_icon);

        if (colorCheckerBg != null && colorPreviewIcon != null) {
            SharedPreferences prefs = getSharedPreferences();
            int color = prefs.getInt(getKey(), 0x80000000);

            // Set checker pattern background
            colorCheckerBg.setBackground(createCheckerPatternSmall());
            // Set color preview on top
            colorPreviewIcon.setBackgroundColor(color);
        }
    }

    private Drawable createCheckerPatternSmall() {
        int tileSize = 10; // Smaller tiles for the icon
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
