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
 * Multi-touch virtual analog joystick + unified Obsidian/Cyan arcade buttons (A / B) + density-scaled HUD.
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
    private final float density;
    private float pulseA = 0f;
    private float pulseB = 0f;

    public TouchControlsOverlay(Context context, VortexScriptEngine scriptEngine) {
        super(context);
        this.scriptEngine = scriptEngine;
        this.density = context.getResources().getDisplayMetrics().density;
        paintStroke.setStyle(Paint.Style.STROKE);
        paintStroke.setStrokeWidth(2.0f * density);
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
        joyRadius = Math.max(54f * density, minDim * 0.135f);
        joyCenterX = joyRadius * 1.42f;
        joyCenterY = h - joyRadius * 1.38f;
        joyKnobX = joyCenterX;
        joyKnobY = joyCenterY;

        btnRadius = Math.max(36f * density, minDim * 0.088f);
        btnAX = w - btnRadius * 3.45f;
        btnAY = h - btnRadius * 1.42f;
        btnBX = w - btnRadius * 1.38f;
        btnBY = h - btnRadius * 2.70f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();

        if (hudVisible) {
            float pad = 12f * density;
            float pillH = 34f * density;
            float pillW = Math.min(w - pad * 2f, 290f * density);
            hpRect.set(pad, pad, pad + pillW, pad + pillH);
            paintFill.setColor(Color.argb(210, 14, 14, 17));
            canvas.drawRoundRect(hpRect, 10f * density, 10f * density, paintFill);
            paintStroke.setStrokeWidth(1.2f * density);
            paintStroke.setColor(Color.argb(175, 64, 64, 72));
            canvas.drawRoundRect(hpRect, 10f * density, 10f * density, paintStroke);

            float baseline = pad + 21.5f * density;
            paintText.setTextSize(12.2f * density);
            paintText.setColor(Color.rgb(255, 255, 255));
            canvas.drawText("SCORE " + score, pad + 11f * density, baseline, paintText);

            paintText.setColor(health > 30 ? Color.rgb(134, 239, 172) : Color.rgb(252, 165, 165));
            canvas.drawText("HP " + health, pad + pillW * 0.45f, baseline, paintText);

            paintText.setTextSize(11f * density);
            paintText.setColor(Color.rgb(161, 161, 170));
            canvas.drawText(fps + " FPS", pad + pillW * 0.74f, baseline, paintText);

            if (banner != null && !banner.isEmpty()) {
                float dynSize = Math.min(14f * density, Math.max(10f * density, (w * 0.88f) / Math.max(12, banner.length()) * 1.65f));
                paintText.setTextSize(dynSize);
                paintText.setColor(Color.rgb(255, 255, 255));
                float tw = paintText.measureText(banner);
                canvas.drawText(banner, Math.max(pad, (w - tw) * 0.5f), pad + pillH + 21f * density, paintText);
            }

            // v4.3 In-Game Custom GUI Widgets (Labels & Progress/Chart Bars) + Story Dialog Box
            if (scriptEngine != null) {
                int h = getHeight();
                for (VortexScriptEngine.UiWidget wg : scriptEngine.uiWidgets.values()) {
                    float wx = (wg.xPct / 100f) * w;
                    float wy = (wg.yPct / 100f) * h;
                    int wc = parseHexSafe(wg.colorHex, Color.WHITE);
                    if ("BAR".equalsIgnoreCase(wg.kind)) {
                        float bw = Math.min(220f * density, w * 0.45f);
                        float bh = 12f * density;
                        float bx = wx - bw * 0.5f;
                        float ratio = Math.max(0f, Math.min(1f, wg.value / Math.max(0.0001f, wg.maxValue)));
                        paintText.setTextSize(10.5f * density);
                        paintText.setColor(Color.WHITE);
                        canvas.drawText(wg.text + " (" + Math.round(wg.value) + "/" + Math.round(wg.maxValue) + ")",
                                bx, wy - 4f * density, paintText);
                        hpRect.set(bx, wy, bx + bw, wy + bh);
                        paintFill.setColor(Color.argb(215, 14, 14, 18));
                        canvas.drawRoundRect(hpRect, 5f * density, 5f * density, paintFill);
                        hpRect.set(bx + 2f * density, wy + 2f * density,
                                bx + 2f * density + Math.max(0f, (bw - 4f * density) * ratio), wy + bh - 2f * density);
                        paintFill.setColor(wc);
                        canvas.drawRoundRect(hpRect, 4f * density, 4f * density, paintFill);
                    } else {
                        paintText.setTextSize(wg.fontSize * density * 0.92f);
                        float tw = paintText.measureText(wg.text);
                        hpRect.set(wx - tw * 0.5f - 8f * density, wy - wg.fontSize * density,
                                wx + tw * 0.5f + 8f * density, wy + 5f * density);
                        paintFill.setColor(Color.argb(185, 10, 10, 14));
                        canvas.drawRoundRect(hpRect, 6f * density, 6f * density, paintFill);
                        paintText.setColor(wc);
                        canvas.drawText(wg.text, wx - tw * 0.5f, wy, paintText);
                    }
                }

                VortexScriptEngine.DialogState dlg = scriptEngine.activeDialog;
                if (dlg != null && dlg.active && dlg.message != null && !dlg.message.isEmpty()) {
                    float dw = Math.min(520f * density, w - 32f * density);
                    float dh = 76f * density;
                    float dx = (w - dw) * 0.5f;
                    float dy = showJoystick ? (pad + pillH + 34f * density) : (h - dh - 18f * density);
                    hpRect.set(dx, dy, dx + dw, dy + dh);
                    paintFill.setColor(Color.argb(236, 11, 11, 14));
                    canvas.drawRoundRect(hpRect, 11f * density, 11f * density, paintFill);
                    paintStroke.setStrokeWidth(1.4f * density);
                    paintStroke.setColor(Color.argb(225, 255, 255, 255));
                    canvas.drawRoundRect(hpRect, 11f * density, 11f * density, paintStroke);

                    if (dlg.speaker != null && !dlg.speaker.isEmpty()) {
                        paintText.setTextSize(10f * density);
                        float sw = paintText.measureText(dlg.speaker.toUpperCase()) + 16f * density;
                        hpRect.set(dx + 12f * density, dy - 9f * density, dx + 12f * density + sw, dy + 10f * density);
                        paintFill.setColor(Color.WHITE);
                        canvas.drawRoundRect(hpRect, 5f * density, 5f * density, paintFill);
                        paintText.setColor(Color.rgb(10, 10, 12));
                        canvas.drawText(dlg.speaker.toUpperCase(), dx + 20f * density, dy + 4f * density, paintText);
                    }
                    paintText.setTextSize(12.2f * density);
                    paintText.setColor(Color.rgb(248, 248, 250));
                    canvas.drawText(dlg.message, dx + 14f * density, dy + 34f * density, paintText);
                }
            }
        }

        paintStroke.setStrokeWidth(2.0f * density);
        if (showJoystick) {
            paintFill.setColor(Color.argb(110, 18, 18, 22));
            canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintFill);
            paintStroke.setColor(joyPointerId != -1
                    ? Color.argb(245, 255, 255, 255)
                    : Color.argb(150, 210, 210, 218));
            canvas.drawCircle(joyCenterX, joyCenterY, joyRadius, paintStroke);

            paintFill.setColor(joyPointerId != -1
                    ? Color.argb(245, 255, 255, 255)
                    : Color.argb(185, 230, 230, 236));
            canvas.drawCircle(joyKnobX, joyKnobY, joyRadius * 0.40f, paintFill);
        }

        if (showActionButtons) {
            boolean aDown = btnAPointerId != -1;
            boolean bDown = btnBPointerId != -1;
            if (aDown) pulseA = 1f; else if (pulseA > 0.02f) { pulseA *= 0.82f; postInvalidateOnAnimation(); } else pulseA = 0f;
            if (bDown) pulseB = 1f; else if (pulseB > 0.02f) { pulseB *= 0.82f; postInvalidateOnAnimation(); } else pulseB = 0f;

            drawArcadeButton(canvas, btnAX, btnAY, btnRadius, "A", aDown, pulseA);
            drawArcadeButton(canvas, btnBX, btnBY, btnRadius, "B", bDown, pulseB);
        }
    }

    private void drawArcadeButton(Canvas canvas, float cx, float cy, float r, String label, boolean down, float pulse) {
        float scale = down ? 0.93f : 1.0f;
        float rad = r * scale;
        if (pulse > 0.05f) {
            paintStroke.setColor(Color.argb((int) (120 * pulse), 255, 255, 255));
            canvas.drawCircle(cx, cy, r * (1.0f + (1f - pulse) * 0.28f), paintStroke);
        }
        paintFill.setColor(down ? Color.argb(240, 255, 255, 255) : Color.argb(165, 26, 26, 31));
        canvas.drawCircle(cx, cy, rad, paintFill);
        paintStroke.setColor(down ? Color.rgb(255, 255, 255) : Color.argb(215, 220, 220, 228));
        canvas.drawCircle(cx, cy, rad, paintStroke);
        paintText.setColor(down ? Color.rgb(10, 10, 12) : Color.rgb(250, 250, 250));
        paintText.setTextSize(15f * density);
        float tw = paintText.measureText(label);
        canvas.drawText(label, cx - tw * 0.5f, cy + 5.5f * density, paintText);
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

    private static int parseHexSafe(String hex, int fallback) {
        try {
            return Color.parseColor(hex);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }
}
