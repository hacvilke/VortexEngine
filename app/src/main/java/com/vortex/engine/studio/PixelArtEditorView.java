package com.vortex.engine.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import com.vortex.engine.core.model.PixelSpriteCodec;

/**
 * Interactive 16x16 Touchscreen Pixel Art Canvas with Mirror-X symmetry & Transparent Checkerboard.
 */
public class PixelArtEditorView extends View {

    private final int[] pixels = new int[PixelSpriteCodec.SIZE * PixelSpriteCodec.SIZE];
    private int brushArgb = 0xFF00E5FF; // 0x00000000 = Eraser
    private boolean mirrorX = false;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PixelArtEditorView(Context context) {
        super(context);
        loadEncoded(PixelSpriteCodec.createDefaultKnightSprite());
    }

    public void loadEncoded(String encoded) {
        int[] decoded = PixelSpriteCodec.decodeToArgbPixels(encoded);
        System.arraycopy(decoded, 0, pixels, 0, pixels.length);
        invalidate();
    }

    public String exportEncoded() {
        return PixelSpriteCodec.encodeFromArgbPixels(pixels);
    }

    public void setBrushColor(int argb) {
        this.brushArgb = argb;
    }

    public int getBrushColor() {
        return brushArgb;
    }

    public void setMirrorX(boolean mirror) {
        this.mirrorX = mirror;
    }

    public boolean isMirrorX() {
        return mirrorX;
    }

    public void clearCanvas() {
        for (int i = 0; i < pixels.length; i++) pixels[i] = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float board = Math.min(w, h) * 0.96f;
        float ox = (w - board) * 0.5f;
        float oy = (h - board) * 0.5f;
        float cell = board / PixelSpriteCodec.SIZE;

        for (int py = 0; py < PixelSpriteCodec.SIZE; py++) {
            for (int px = 0; px < PixelSpriteCodec.SIZE; px++) {
                float x0 = ox + px * cell;
                float y0 = oy + py * cell;

                // Dark checkerboard backdrop for transparent pixels
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(((px + py) % 2 == 0) ? 0xFF151E2D : 0xFF1C283B);
                canvas.drawRect(x0, y0, x0 + cell, y0 + cell, paint);

                int c = pixels[py * PixelSpriteCodec.SIZE + px];
                if (((c >>> 24) & 0xFF) > 32) {
                    paint.setColor(c);
                    canvas.drawRect(x0, y0, x0 + cell, y0 + cell, paint);
                }

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1f);
                paint.setColor(0x33455A64);
                canvas.drawRect(x0, y0, x0 + cell, y0 + cell, paint);
            }
        }

        // Border frame
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        paint.setColor(Color.parseColor("#FFFFFF"));
        canvas.drawRect(ox, oy, ox + board, oy + board, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int act = event.getActionMasked();
        if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
            int w = getWidth(), h = getHeight();
            float board = Math.min(w, h) * 0.96f;
            float ox = (w - board) * 0.5f;
            float oy = (h - board) * 0.5f;
            float cell = board / PixelSpriteCodec.SIZE;

            int gx = (int) Math.floor((event.getX() - ox) / cell);
            int gy = (int) Math.floor((event.getY() - oy) / cell);
            if (gx >= 0 && gx < PixelSpriteCodec.SIZE && gy >= 0 && gy < PixelSpriteCodec.SIZE) {
                pixels[gy * PixelSpriteCodec.SIZE + gx] = brushArgb;
                if (mirrorX) {
                    int mx = (PixelSpriteCodec.SIZE - 1) - gx;
                    pixels[gy * PixelSpriteCodec.SIZE + mx] = brushArgb;
                }
                invalidate();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
