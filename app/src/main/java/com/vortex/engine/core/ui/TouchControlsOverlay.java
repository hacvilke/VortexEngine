package com.vortex.engine.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import com.vortex.engine.core.script.VortexScriptEngine;

/**
 * Multi-touch virtual analog joystick + non-overlapping dual arcade buttons (A / B) + live HUD overlay.
 */
public class TouchControlsOverlay extends View {

    private final VortexScriptEngine scriptEngine;
    private boolean showJoystick = true;
    private boolean showActionButtons = true;
    private boolean hudVisible = true;

    private int fps = 60;
    private int score = 0;
    private int health = 100;
    private String banner = "";

    private int joyPointerId = -1;
    private float joyCenterX, joyCenterY, joyRadius;
    private float joyKnobX, joyKnobY;

    private int btnAPointerId = -1;
    private int btnBPointerId = -1;
    private float btnAX, btnAY, btnBX, btnBY, btnRadius;

    private final Paint paintFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF hpRect = new RectF();

    public TouchControlsOverlay(Context context, VortexScriptEngine scriptEngine) {
        super(context);
        this.scriptEngine = scriptEngine;
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setStrokeWidth(4f);
        paintText.setColor(Color.WHITE);
        paintText.setFakeBoldText(true);
    }

    public void configure(boolean showJoy, boolean showButtons, boolean showHud) {
        this.showJoystick = showJoy;
        this.showActionButtons = showButtons;
        this.hudVisible = showHud;
        if (!showJoy && scriptEngine != null) {
            scriptEngine.inputJoyX = 0f;
            scriptEngine.inputJoyY = 0f;
        }
        invalidate();
    }

    public void updateHud(int fps, int score, int health, String banner) {
        this.fps = fps;
        this.score = score;
        this.health = health;
        this.banner = banner == null ? "" : banner;
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float minDim = Math.min(w, h);
        joyRadius = Math.max(72f, minDim * 0.135f);
        joyCenterX = joyRadius * 1.45f;
        joyCenterY = h - joyRadius * 1.40f;
        joyKnobX = joyCenterX;
        joyKnobY = joyCenterY;

        btnRadius = Math.max(52f, minDim * 0.09f);
        // Clean diagonal arcade spacing with guaranteed gap between A and B!
        btnAX = w - btnRadius * 3.55f;
        btnAY = h - btnRadius * 1.45f;
        btnBX = w - btnRadius * 1.40f;
        btnBY = h - btnRadius * 2.75f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();

        if (hudVisible) {
            paintFill.setColor(Color.argb(165, 11, 15, 23));
            hpRect.set(20f, 18f, Math.min(w - 20f, 540f), 84f);
            canvas.drawRoundRect(hpRect, 18f, 18f, paintFill);

            paintText.setTextSize(28f);
            paintText.setColor(Color.rgb(0, 229, 255));
            canvas.drawText("SCORE " + score, 38f, 59f, paintText);

            paintText.setColor(health > 30 ? Color.rgb(0, 230, 118) : Color.rgb(255, 82, 82));
            canvas.drawText("HP " + health, 235f, 59f, paintText);

            paintText.setTextSize(23f);
            paintText.setColor(Color.rgb(160, 180, 205));
            canvas.drawText(fps + " FPS", 395f, 58f, paintText);

            if (banner != null && !banner.isEmpty()) {
                paintText.setTextSize(32f);
                paintText.setColor(Color.rgb(255, 213, 79));
                float tw = paintText.measureText(banner);
                canvas.drawText(banner, Math.max(24f, (w - tw) * 0.5f), 132f, paintText);
            }
        }

        if (showJoystick) {
            paintFill.setColor(Color.argb(65, 20, 32, 48));
            canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintFill);
            paintStroke.setColor(Color.argb(150, 0, 229, 255));
            canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintStroke);

            paintFill.setColor(joyPointerId != -1
                    ? Color.argb(215, 0, 229, 255)
                    : Color.argb(145, 0, 190, 225));
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.42f, paintFill);
        }

        if (showActionButtons) {
            paintFill.setColor(btnAPointerId != -1
                    ? Color.argb(225, 0, 230, 118)
                    : Color.argb(120, 0, 200, 105));
            canvas.drawCircle(btnAX, btnAY, btnRadius, paintFill);
            paintStroke.setColor(Color.argb(210, 0, 230, 118));
            canvas.drawCircle(btnAX, btnAY, btnRadius, paintStroke);
            paintText.setColor(Color.WHITE);
            paintText.setTextSize(32f);
            canvas.drawText("A", btnAX - 11f, btnAY + 11f, paintText);

            paintFill.setColor(btnBPointerId != -1
                    ? Color.argb(225, 124, 77, 255)
                    : Color.argb(120, 105, 65, 225));
            canvas.drawCircle(btnBX, btnBY, btnRadius, paintFill);
            paintStroke.setColor(Color.argb(210, 160, 125, 255));
            canvas.drawCircle(btnBX, btnBY, btnRadius, paintStroke);
            canvas.drawText("B", btnBX - 11f, btnBY + 11f, paintText);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!showJoystick && !showActionButtons) return false;
        int action = event.getActionMasked();
        int idx = event.getActionIndex();
        int pid = event.getPointerId(idx);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                float tx = event.getX(idx);
                float ty = event.getY(idx);
                if (showJoystick && dist(tx, ty, joyCenterX, joyCenterY) <= joyRadius * 1.45f && joyPointerId == -1) {
                    joyPointerId = pid;
                    updateJoyKnob(tx, ty);
                    invalidate();
                    return true;
                }
                if (showActionButtons && dist(tx, ty, btnAX, btnAY) <= btnRadius * 1.25f && btnAPointerId == -1) {
                    btnAPointerId = pid;
                    if (scriptEngine != null) {
                        scriptEngine.inputBtnA = true;
                        scriptEngine.notifyButton("A");
                    }
                    invalidate();
                    return true;
                }
                if (showActionButtons && dist(tx, ty, btnBX, btnBY) <= btnRadius * 1.25f && btnBPointerId == -1) {
                    btnBPointerId = pid;
                    if (scriptEngine != null) {
                        scriptEngine.inputBtnB = true;
                        scriptEngine.notifyButton("B");
                    }
                    invalidate();
                    return true;
                }
                return false;
            }
            case MotionEvent.ACTION_MOVE: {
                boolean handled = false;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    if (id == joyPointerId) {
                        updateJoyKnob(event.getX(i), event.getY(i));
                        handled = true;
                    }
                }
                if (handled) invalidate();
                return handled || btnAPointerId != -1 || btnBPointerId != -1;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean cancelAll = (action == MotionEvent.ACTION_CANCEL);
                if (cancelAll || pid == joyPointerId) {
                    joyPointerId = -1;
                    joyKnobX = joyCenterX;
                    joyKnobY = joyCenterY;
                    if (scriptEngine != null) {
                        scriptEngine.inputJoyX = 0f;
                        scriptEngine.inputJoyY = 0f;
                    }
                }
                if (cancelAll || pid == btnAPointerId) {
                    btnAPointerId = -1;
                    if (scriptEngine != null) scriptEngine.inputBtnA = false;
                }
                if (cancelAll || pid == btnBPointerId) {
                    btnBPointerId = -1;
                    if (scriptEngine != null) scriptEngine.inputBtnB = false;
                }
                invalidate();
                return true;
            }
        }
        return false;
    }

    private void updateJoyKnob(float tx, float ty) {
        float dx = tx - joyCenterX;
        float dy = ty - joyCenterY;
        float d = (float) Math.hypot(dx, dy);
        if (d > joyRadius) {
            dx = (dx / d) * joyRadius;
            dy = (dy / d) * joyRadius;
        }
        joyKnobX = joyCenterX + dx;
        joyKnobY = joyCenterY + dy;
        if (scriptEngine != null) {
            scriptEngine.inputJoyX = dx / joyRadius;
            scriptEngine.inputJoyY = -dy / joyRadius;
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }
}
