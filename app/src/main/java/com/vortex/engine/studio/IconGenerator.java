package com.vortex.engine.studio;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Generates custom multi-density launcher PNG icons on-device for exported standalone Game APKs.
 */
public class IconGenerator {

    public static void generateLauncherIcons(
            File resDir,
            String bgHex,
            String accentHex,
            String glyph,
            String title
    ) throws Exception {
        int[][] specs = new int[][]{
                {48}, {72}, {96}, {144}
        };
        String[] folders = new String[]{
                "mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi"
        };

        int bgCol = safeColor(bgHex, Color.rgb(13, 19, 33));
        int accCol = safeColor(accentHex, Color.rgb(255, 255, 255));

        for (int i = 0; i < folders.length; i++) {
            int sz = specs[i][0];
            File dir = new File(resDir, folders[i]);
            if (!dir.exists()) dir.mkdirs();
            Bitmap bmp = renderIcon(sz, bgCol, accCol, glyph, title);
            try (FileOutputStream fos = new FileOutputStream(new File(dir, "ic_launcher.png"))) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bmp.recycle();
        }
    }

    public static Bitmap renderIcon(int size, int bgCol, int accCol, String glyph, String title) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Squircle background
        p.setStyle(Paint.Style.FILL);
        p.setColor(bgCol);
        float r = size * 0.22f;
        c.drawRoundRect(new RectF(0, 0, size, size), r, r, p);

        // Glowing accent ring
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, size * 0.055f));
        p.setColor(accCol);
        p.setAlpha(190);
        c.drawCircle(size * 0.5f, size * 0.5f, size * 0.36f, p);

        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        String g = glyph == null ? "CUBE" : glyph.toUpperCase();
        float cx = size * 0.5f;
        float cy = size * 0.5f;

        if ("STAR".equals(g)) {
            Path star = new Path();
            for (int i = 0; i < 10; i++) {
                double ang = -Math.PI / 2 + i * Math.PI / 5.0;
                float rad = (i % 2 == 0) ? size * 0.26f : size * 0.11f;
                float x = cx + (float) Math.cos(ang) * rad;
                float y = cy + (float) Math.sin(ang) * rad;
                if (i == 0) star.moveTo(x, y);
                else star.lineTo(x, y);
            }
            star.close();
            c.drawPath(star, p);
        } else if ("ROCKET".equals(g)) {
            Path tri = new Path();
            tri.moveTo(cx, cy - size * 0.26f);
            tri.lineTo(cx - size * 0.19f, cy + size * 0.20f);
            tri.lineTo(cx, cy + size * 0.11f);
            tri.lineTo(cx + size * 0.19f, cy + size * 0.20f);
            tri.close();
            c.drawPath(tri, p);
        } else {
            // Isometric 3D Cube + Monogram
            float s = size * 0.22f;
            Path hex = new Path();
            hex.moveTo(cx, cy - s);
            hex.lineTo(cx + s * 0.88f, cy - s * 0.5f);
            hex.lineTo(cx + s * 0.88f, cy + s * 0.5f);
            hex.lineTo(cx, cy + s);
            hex.lineTo(cx - s * 0.88f, cy + s * 0.5f);
            hex.lineTo(cx - s * 0.88f, cy - s * 0.5f);
            hex.close();
            c.drawPath(hex, p);

            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            p.setFakeBoldText(true);
            p.setTextSize(size * 0.24f);
            String letter = (title != null && !title.trim().isEmpty())
                    ? title.trim().substring(0, 1).toUpperCase()
                    : "V";
            c.drawText(letter, cx, cy + size * 0.085f, p);
        }
        return bmp;
    }

    private static int safeColor(String hex, int fallback) {
        try {
            if (hex == null || hex.isEmpty()) return fallback;
            return Color.parseColor(hex.startsWith("#") ? hex : ("#" + hex));
        } catch (Exception e) {
            return fallback;
        }
    }
}
