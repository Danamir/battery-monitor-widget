package com.danamir.batterymonitor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

/**
 * Color picker view with HSL+Alpha color space selection.
 * Provides:
 * - 2:1 rectangular zone for saturation/lightness selection
 * - Hue slider with rainbow gradient
 * - Alpha slider with checker background
 * - Text inputs for R, G, B, Alpha, and Hex values
 */
public class ColorPickerView extends LinearLayout {
    private static final int SL_PICKER_HEIGHT = 300;
    private static final int SLIDER_HEIGHT = 60;
    private static final int CHECKER_SIZE = 20;

    // Color components
    private float hue = 0f;        // 0-360
    private float saturation = 1f; // 0-1
    private float lightness = 0.5f; // 0-1
    private int alpha = 255;       // 0-255

    // Views
    private SaturationLightnessView slView;
    private HueSliderView hueSlider;
    private AlphaSliderView alphaSlider;
    private EditText redEdit, greenEdit, blueEdit, alphaEdit, hexEdit;

    // Listener
    private OnColorChangedListener listener;
    private boolean isUpdating = false;

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    public ColorPickerView(Context context) {
        super(context);
        init();
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setPadding(24, 24, 24, 24);

        // Zone 1: Saturation/Lightness picker (2:1 ratio)
        slView = new SaturationLightnessView(getContext());
        slView.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                SL_PICKER_HEIGHT
        ));
        addView(slView);

        // Add spacing
        addSpacing(16);

        // Zone 2: Hue slider
        hueSlider = new HueSliderView(getContext());
        hueSlider.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                SLIDER_HEIGHT
        ));
        addView(hueSlider);

        // Add spacing
        addSpacing(16);

        // Zone 3: Alpha slider
        alphaSlider = new AlphaSliderView(getContext());
        alphaSlider.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                SLIDER_HEIGHT
        ));
        addView(alphaSlider);

        // Add spacing
        addSpacing(16);

        // Zone 4: Text inputs
        addTextInputs();
    }

    private void addSpacing(int dp) {
        View space = new View(getContext());
        space.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                (int) (dp * getResources().getDisplayMetrics().density)
        ));
        addView(space);
    }

    private void addTextInputs() {
        Context ctx = getContext();

        // Create horizontal layout for inputs
        LinearLayout horizontalLayout = new LinearLayout(ctx);
        horizontalLayout.setOrientation(HORIZONTAL);
        horizontalLayout.setLayoutParams(new LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
        ));

        // R input
        LinearLayout redContainer = createColorInputWithLabel(ctx, "Red");
        redEdit = (EditText) redContainer.getChildAt(1);
        horizontalLayout.addView(redContainer, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        // G input
        LinearLayout greenContainer = createColorInputWithLabel(ctx, "Green");
        greenEdit = (EditText) greenContainer.getChildAt(1);
        horizontalLayout.addView(greenContainer, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        // B input
        LinearLayout blueContainer = createColorInputWithLabel(ctx, "Blue");
        blueEdit = (EditText) blueContainer.getChildAt(1);
        horizontalLayout.addView(blueContainer, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        // Alpha input (0-100)
        LinearLayout alphaContainer = createColorInputWithLabel(ctx, "Alpha%");
        alphaEdit = (EditText) alphaContainer.getChildAt(1);
        horizontalLayout.addView(alphaContainer, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));

        // Hex input (twice as large)
        LinearLayout hexContainer = createHexInputWithLabel(ctx);
        hexEdit = (EditText) hexContainer.getChildAt(1);
        horizontalLayout.addView(hexContainer, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 2));

        addView(horizontalLayout);
    }

    private LinearLayout createColorInputWithLabel(Context ctx, String label) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(VERTICAL);
        container.setPadding(4, 0, 4, 0);

        android.widget.TextView textView = new android.widget.TextView(ctx);
        textView.setText(label);
        textView.setTextSize(12);
        textView.setGravity(android.view.Gravity.CENTER);
        container.addView(textView);

        EditText edit = new EditText(ctx);
        edit.setTextSize(14);
        edit.setSingleLine();
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edit.setGravity(android.view.Gravity.CENTER);

        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                try {
                    int r = Integer.parseInt(redEdit.getText().toString());
                    int g = Integer.parseInt(greenEdit.getText().toString());
                    int b = Integer.parseInt(blueEdit.getText().toString());
                    int a = Integer.parseInt(alphaEdit.getText().toString());

                    if (r >= 0 && r <= 255 && g >= 0 && g <= 255 &&
                        b >= 0 && b <= 255 && a >= 0 && a <= 100) {
                        setColorFromRGB(Color.argb((int)(a * 2.55f), r, g, b));
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });

        container.addView(edit);
        return container;
    }

    private LinearLayout createHexInputWithLabel(Context ctx) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(VERTICAL);
        container.setPadding(4, 0, 4, 0);

        android.widget.TextView textView = new android.widget.TextView(ctx);
        textView.setText("Hex");
        textView.setTextSize(12);
        textView.setGravity(android.view.Gravity.CENTER);
        container.addView(textView);

        EditText edit = new EditText(ctx);
        edit.setTextSize(14);
        edit.setSingleLine();
        edit.setGravity(android.view.Gravity.CENTER);
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isUpdating) return;
                try {
                    String hex = s.toString().trim();
                    if (hex.startsWith("#")) {
                        hex = hex.substring(1);
                    }
                    if (hex.length() == 8 || hex.length() == 6) {
                        int color = (int) Long.parseLong(hex, 16);
                        if (hex.length() == 6) {
                            color |= 0xFF000000; // Add full alpha if not specified
                        }
                        setColorFromRGB(color);
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid input
                }
            }
        });

        container.addView(edit);
        return container;
    }


    public void setColor(int color) {
        alpha = Color.alpha(color);

        // Convert RGB to HSL
        float[] hsl = new float[3];
        rgbToHsl(Color.red(color), Color.green(color), Color.blue(color), hsl);
        hue = hsl[0];
        saturation = hsl[1];
        lightness = hsl[2];

        updateAllViews();
    }

    private void setColorFromRGB(int color) {
        alpha = Color.alpha(color);

        float[] hsl = new float[3];
        rgbToHsl(Color.red(color), Color.green(color), Color.blue(color), hsl);
        hue = hsl[0];
        saturation = hsl[1];
        lightness = hsl[2];

        updateAllViews();
        notifyColorChanged();
    }

    public int getColor() {
        int rgb = hslToRgb(hue, saturation, lightness);
        return Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
    }

    private void updateAllViews() {
        isUpdating = true;

        slView.invalidate();
        hueSlider.invalidate();
        alphaSlider.invalidate();

        int color = getColor();

        // Update text fields while preserving cursor position
        updateEditText(redEdit, String.valueOf(Color.red(color)));
        updateEditText(greenEdit, String.valueOf(Color.green(color)));
        updateEditText(blueEdit, String.valueOf(Color.blue(color)));
        updateEditText(alphaEdit, String.valueOf((int)(Color.alpha(color) / 2.55f)));
        updateEditText(hexEdit, String.format("#%08X", color));

        isUpdating = false;
    }

    private void updateEditText(EditText editText, String newText) {
        if (!editText.getText().toString().equals(newText)) {
            int cursorPosition = editText.getSelectionStart();
            editText.setText(newText);
            // Restore cursor position, clamped to new text length
            int newPosition = Math.min(cursorPosition, newText.length());
            editText.setSelection(newPosition);
        }
    }

    private void notifyColorChanged() {
        if (listener != null && !isUpdating) {
            listener.onColorChanged(getColor());
        }
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    // HSL conversion helpers
    private static void rgbToHsl(int r, int g, int b, float[] hsl) {
        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        // Lightness
        hsl[2] = (max + min) / 2f;

        if (delta == 0) {
            hsl[0] = 0; // Hue
            hsl[1] = 0; // Saturation
        } else {
            // Saturation
            hsl[1] = delta / (1 - Math.abs(2 * hsl[2] - 1));

            // Hue
            if (max == rf) {
                hsl[0] = 60 * (((gf - bf) / delta) % 6);
            } else if (max == gf) {
                hsl[0] = 60 * (((bf - rf) / delta) + 2);
            } else {
                hsl[0] = 60 * (((rf - gf) / delta) + 4);
            }

            if (hsl[0] < 0) {
                hsl[0] += 360;
            }
        }
    }

    private static int hslToRgb(float h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = l - c / 2;

        float r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        return Color.rgb(
                (int) ((r + m) * 255),
                (int) ((g + m) * 255),
                (int) ((b + m) * 255)
        );
    }

    // Inner class: Saturation/Lightness picker
    private class SaturationLightnessView extends View {
        private Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;

        public SaturationLightnessView(Context context) {
            super(context);
            dotPaint.setColor(Color.WHITE);
            dotPaint.setStyle(Paint.Style.STROKE);
            dotPaint.setStrokeWidth(3);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            generateBitmap(w, h);
        }

        private void generateBitmap(int w, int h) {
            if (w <= 0 || h <= 0) return;

            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.setHasAlpha(false);

            // Get the pure hue color at full saturation and 50% lightness
            int pureColor = hslToRgb(hue, 1f, 0.5f);
            int pureR = Color.red(pureColor);
            int pureG = Color.green(pureColor);
            int pureB = Color.blue(pureColor);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float saturationRatio = x / (float) w;  // 0 (left) to 1 (right)
                    float lightnessRatio = 1 - (y / (float) h);  // 1 (top) to 0 (bottom)

                    // Interpolate between white (top-left), pure color (top-right), and black (bottom)
                    // Top row: white to pure color
                    // Bottom row: black to black
                    int r, g, b;

                    if (lightnessRatio == 0) {
                        // Bottom row is always black
                        r = g = b = 0;
                    } else {
                        // Interpolate based on saturation (left to right)
                        float whiteAmount = (1 - saturationRatio) * lightnessRatio;
                        float colorAmount = saturationRatio * lightnessRatio;

                        r = (int) (255 * whiteAmount + pureR * colorAmount);
                        g = (int) (255 * whiteAmount + pureG * colorAmount);
                        b = (int) (255 * whiteAmount + pureB * colorAmount);
                    }

                    bitmap.setPixel(x, y, Color.rgb(r, g, b));
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, 0, 0, null);

                // Draw selection dot
                float x = saturation * getWidth();
                float y = (1 - lightness) * getHeight();
                dotPaint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, 10, dotPaint);
                dotPaint.setColor(Color.BLACK);
                canvas.drawCircle(x, y, 8, dotPaint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                float y = Math.max(0, Math.min(event.getY(), getHeight()));

                saturation = x / getWidth();
                lightness = 1 - (y / getHeight());

                invalidate();
                updateAllViews();
                notifyColorChanged();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // Inner class: Hue slider
    private class HueSliderView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public HueSliderView(Context context) {
            super(context);
            indicatorPaint.setColor(Color.WHITE);
            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setStrokeWidth(3);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();

            // Draw rainbow gradient
            int[] colors = new int[7];
            colors[0] = Color.rgb(255, 0, 0);     // Red
            colors[1] = Color.rgb(255, 255, 0);   // Yellow
            colors[2] = Color.rgb(0, 255, 0);     // Green
            colors[3] = Color.rgb(0, 255, 255);   // Cyan
            colors[4] = Color.rgb(0, 0, 255);     // Blue
            colors[5] = Color.rgb(255, 0, 255);   // Magenta
            colors[6] = Color.rgb(255, 0, 0);     // Red

            LinearGradient gradient = new LinearGradient(
                    0, 0, w, 0, colors, null, Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            canvas.drawRect(0, 0, w, h, paint);

            // Draw indicator
            float x = (hue / 360f) * w;
            indicatorPaint.setColor(Color.WHITE);
            canvas.drawLine(x, 0, x, h, indicatorPaint);
            indicatorPaint.setColor(Color.BLACK);
            canvas.drawLine(x - 1, 0, x - 1, h, indicatorPaint);
            canvas.drawLine(x + 1, 0, x + 1, h, indicatorPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                hue = (x / getWidth()) * 360f;

                slView.generateBitmap(slView.getWidth(), slView.getHeight());
                invalidate();
                updateAllViews();
                notifyColorChanged();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    // Inner class: Alpha slider
    private class AlphaSliderView extends View {
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint checkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public AlphaSliderView(Context context) {
            super(context);
            indicatorPaint.setColor(Color.WHITE);
            indicatorPaint.setStyle(Paint.Style.STROKE);
            indicatorPaint.setStrokeWidth(3);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();

            // Draw checker background
            checkerPaint.setColor(0xFFCCCCCC);
            for (int y = 0; y < h; y += CHECKER_SIZE) {
                for (int x = 0; x < w; x += CHECKER_SIZE) {
                    if ((x / CHECKER_SIZE + y / CHECKER_SIZE) % 2 == 0) {
                        canvas.drawRect(x, y, x + CHECKER_SIZE, y + CHECKER_SIZE, checkerPaint);
                    }
                }
            }
            checkerPaint.setColor(0xFF999999);
            for (int y = 0; y < h; y += CHECKER_SIZE) {
                for (int x = 0; x < w; x += CHECKER_SIZE) {
                    if ((x / CHECKER_SIZE + y / CHECKER_SIZE) % 2 == 1) {
                        canvas.drawRect(x, y, x + CHECKER_SIZE, y + CHECKER_SIZE, checkerPaint);
                    }
                }
            }

            // Draw alpha gradient
            int rgb = hslToRgb(hue, saturation, lightness);
            int transparentColor = Color.argb(0, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
            int opaqueColor = Color.argb(255, Color.red(rgb), Color.green(rgb), Color.blue(rgb));

            LinearGradient gradient = new LinearGradient(
                    0, 0, w, 0,
                    new int[]{transparentColor, opaqueColor},
                    null,
                    Shader.TileMode.CLAMP);
            paint.setShader(gradient);
            canvas.drawRect(0, 0, w, h, paint);

            // Draw indicator
            float x = (alpha / 255f) * w;
            indicatorPaint.setColor(Color.WHITE);
            canvas.drawLine(x, 0, x, h, indicatorPaint);
            indicatorPaint.setColor(Color.BLACK);
            canvas.drawLine(x - 1, 0, x - 1, h, indicatorPaint);
            canvas.drawLine(x + 1, 0, x + 1, h, indicatorPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN ||
                event.getAction() == MotionEvent.ACTION_MOVE) {

                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                alpha = (int) ((x / getWidth()) * 255);

                invalidate();
                updateAllViews();
                notifyColorChanged();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
