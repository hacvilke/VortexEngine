package com.vortex.engine.studio;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * VortexMobileUi — PAPER UI kit for the phone studio (cream stock, ink, pencil rules).
 *
 * Replaces raw OS Spinners, CheckBoxes, unstyled EditTexts, and white AlertDialogs
 * with cohesive, custom-drawn, touch-friendly components & micro-animations.
 */
public final class VortexMobileUi {

    // =====================================================================
    // PAPER THEME (same palette as hacvilke/paper-trader): cream stock, ink text,
    // pencil rules. No pure white, no pure black — contrast comes from ink weight.
    // =====================================================================
    // Mutable on purpose: the active VortexTheme rewrites these before any view is built, so a
    // palette import or dark mode repaints the whole phone studio (Activities recreate()).
    public static int C_PAPER        = Color.parseColor("#FFFDF6");
    public static int C_PAPER_2      = Color.parseColor("#FBF6EA");
    public static int C_PAPER_DEEP   = Color.parseColor("#F3EBD6");
    public static int C_INK          = Color.parseColor("#33291D");
    public static int C_PENCIL       = Color.parseColor("#6F6558");
    public static int C_INK_GREEN    = Color.parseColor("#1E7A44");
    public static int C_INK_RED      = Color.parseColor("#C0392B");

    public static int C_VOID         = C_PAPER;
    public static int C_HEADER       = C_PAPER_DEEP;
    public static int C_PANEL        = C_PAPER_2;
    public static int C_SURFACE      = Color.parseColor("#F7EFDC");
    public static int C_SURFACE_HI   = Color.parseColor("#EFE3C6");
    public static int C_INPUT_BG     = Color.parseColor("#FFFDF7");
    public static int C_BORDER       = Color.parseColor("#D2C29F");
    public static int C_CYAN         = C_INK;
    public static int C_CYAN_TINT    = Color.parseColor("#FFEE9C");
    public static int C_CYAN_DARK_TX = C_PAPER;
    public static int C_TEXT         = C_INK;
    public static int C_TEXT_MUTED   = Color.parseColor("#4A3E2E");
    public static int C_TEXT_DIM     = C_PENCIL;
    public static int C_DANGER_BG    = Color.parseColor("#F7E3DF");
    public static int C_DANGER_BDR   = Color.parseColor("#D9A79F");
    public static int C_DANGER_TX    = C_INK_RED;
    public static int C_OK_TX        = C_INK_GREEN;

    /** True when the active theme is dark (the desk tone and bars follow it). */
    public static boolean DARK = false;

    /**
     * Reads the active palette and rewrites the whole colour set. Call it at the top of every
     * Activity's onCreate, before any view is built; after changing the theme call recreate().
     */
    public static void applyActiveTheme(Context ctx) {
        com.vortex.engine.core.theme.VortexTheme t = com.vortex.engine.core.theme.ThemeRegistry.active();
        if (t == null) return;
        DARK = t.dark;
        C_PAPER        = argb(t.get(com.vortex.engine.core.theme.VortexTheme.SHEET));
        C_PAPER_2      = argb(t.get(com.vortex.engine.core.theme.VortexTheme.SHEET_2));
        C_PAPER_DEEP   = argb(t.get(com.vortex.engine.core.theme.VortexTheme.HEADER));
        C_INK          = argb(t.get(com.vortex.engine.core.theme.VortexTheme.INK));
        C_PENCIL       = argb(t.get(com.vortex.engine.core.theme.VortexTheme.TEXT_MUTED));
        C_INK_GREEN    = argb(t.get(com.vortex.engine.core.theme.VortexTheme.OK));
        C_INK_RED      = argb(t.get(com.vortex.engine.core.theme.VortexTheme.DANGER));

        C_VOID         = C_PAPER;
        C_HEADER       = C_PAPER_DEEP;
        C_PANEL        = C_PAPER_2;
        C_SURFACE      = argb(t.get(com.vortex.engine.core.theme.VortexTheme.SURFACE));
        C_SURFACE_HI   = argb(t.get(com.vortex.engine.core.theme.VortexTheme.SURFACE_HOV));
        C_INPUT_BG     = argb(t.get(com.vortex.engine.core.theme.VortexTheme.INPUT_BG));
        C_BORDER       = argb(t.get(com.vortex.engine.core.theme.VortexTheme.BORDER));
        C_CYAN         = C_INK;
        C_CYAN_TINT    = argb(t.get(com.vortex.engine.core.theme.VortexTheme.ACCENT));
        C_CYAN_DARK_TX = argb(t.get(com.vortex.engine.core.theme.VortexTheme.ON_ACCENT));
        C_TEXT         = C_INK;
        C_TEXT_MUTED   = C_PENCIL;
        C_TEXT_DIM     = argb(com.vortex.engine.core.theme.ThemeRegistry.mix(
                t.get(com.vortex.engine.core.theme.VortexTheme.SHEET),
                t.get(com.vortex.engine.core.theme.VortexTheme.INK), 0.62));
        C_DANGER_BG    = argb(t.get(com.vortex.engine.core.theme.VortexTheme.DANGER_BG));
        C_DANGER_BDR   = argb(com.vortex.engine.core.theme.ThemeRegistry.mix(
                t.get(com.vortex.engine.core.theme.VortexTheme.DANGER_BG),
                t.get(com.vortex.engine.core.theme.VortexTheme.DANGER), 0.42));
        C_DANGER_TX    = argb(t.get(com.vortex.engine.core.theme.VortexTheme.DANGER));
        C_OK_TX        = argb(t.get(com.vortex.engine.core.theme.VortexTheme.OK));

        deskTile = null;   // the wooden desk tone is baked, so drop the cached tile
    }

    private static int argb(String hex) {
        return Color.parseColor(hex == null ? "#FFFDF6" : hex);
    }

    // ---- bundled hand lettering (assets/fonts) ----
    private static Typeface FACE_HAND = null;
    private static Typeface FACE_BODY = null;
    private static Typeface FACE_TYPE = null;

    private static Typeface loadFace(Context ctx, String path, int fallbackStyle) {
        try {
            return Typeface.createFromAsset(ctx.getAssets(), path);
        } catch (Throwable t) {
            return Typeface.create(Typeface.DEFAULT, fallbackStyle);
        }
    }

    /** Call once per Activity before building views. */
    public static void loadPaperFonts(Context ctx) {
        if (FACE_HAND != null) return;
        FACE_HAND = loadFace(ctx, "fonts/Caveat.ttf", Typeface.BOLD);
        FACE_BODY = loadFace(ctx, "fonts/PatrickHand.ttf", Typeface.NORMAL);
        FACE_TYPE = loadFace(ctx, "fonts/SpecialElite.ttf", Typeface.NORMAL);
    }

    public static Typeface handTypeface() { return FACE_HAND != null ? FACE_HAND : Typeface.DEFAULT_BOLD; }
    public static Typeface bodyTypeface() { return FACE_BODY != null ? FACE_BODY : Typeface.DEFAULT; }
    public static Typeface typeTypeface() { return FACE_TYPE != null ? FACE_TYPE : Typeface.MONOSPACE; }



    public enum ButtonVariant {
        PRIMARY,   // Solid #00E5FF fill, dark bold text (1-2 main CTAs per screen)
        ACCENT,    // Tinted #0D2838 fill + #00E5FF border + #00E5FF text (active tabs / highlighted actions)
        SECONDARY, // Unified #141E30 fill + #24334D border + crisp light text (all standard studio tools)
        DANGER     // Subtle dark rose surface for destructive delete actions
    }

    public interface SelectionListener {
        void onSelected(int index, String value);
    }

    private VortexMobileUi() {}

    public static int dp(Context ctx, float v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    /**
     * Styles Android status & navigation bars to match Vortex Obsidian (#090D16) with zero white flashes.
     */
    public static void applyDarkSystemBars(Activity act) {
        try {
            Window w = act.getWindow();
            if (w == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                w.setStatusBarColor(C_HEADER);
                w.setNavigationBarColor(C_PAPER_DEEP);
                // the whole studio sits on the wooden desk — cream sheets pop on it
                try {
                    w.getDecorView().setBackgroundDrawable(deskDrawable(act));
                } catch (Throwable ignored) {
                }
                // Paper is a LIGHT surface: ask for dark status/nav icons.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    View dv = w.getDecorView();
                    // paper wants dark glyphs; a dark theme wants light ones
                    dv.setSystemUiVisibility(DARK ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    View dv = w.getDecorView();
                    dv.setSystemUiVisibility(dv.getSystemUiVisibility()
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    // TACTILE PAPER: the wooden desk under the studio, washi tape, torn edges.
    // Same recipe as hacvilke/paper-trader: sheets never float on nothing.
    // =====================================================================
    private static BitmapDrawable deskTile = null;

    /** A tiling wooden-desk texture (planks + grain), generated once. */
    public static Drawable deskDrawable(Context ctx) {
        if (deskTile != null) return deskTile;
        final int n = dp(ctx, 144);
        Bitmap bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // warm desk base
        p.setColor(Color.parseColor("#8A5F36"));
        c.drawRect(0, 0, n, n, p);
        java.util.Random rnd = new java.util.Random(20260101L);
        // grain speckle
        for (int i = 0; i < n * 14; i++) {
            int x = rnd.nextInt(n), y = rnd.nextInt(n);
            p.setColor(rnd.nextBoolean() ? Color.argb(14, 0x2B, 0x18, 0x08) : Color.argb(10, 0xFF, 0xE8, 0xC8));
            c.drawPoint(x, y, p);
        }
        // plank seams (vertical) + fibre lines (horizontal)
        p.setColor(Color.argb(38, 0x2B, 0x18, 0x08));
        c.drawRect(n / 2 - dp(ctx, 1), 0, n / 2 + dp(ctx, 1), n, p);
        p.setColor(Color.argb(16, 0x2B, 0x18, 0x08));
        for (int y = 0; y < n; y += dp(ctx, 9)) c.drawRect(0, y, n, y + 1, p);
        BitmapDrawable bd = new BitmapDrawable(ctx.getResources(), bm);
        bd.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        deskTile = bd;
        return deskTile;
    }

    /** Washi tape strips on the top corners of a sheet — pure decoration. */
    public static final class TapeDrawable extends Drawable {
        private final float len;
        public TapeDrawable(float cornerLenDp) { super(); len = cornerLenDp; }
        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty()) return;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float w = len * 1.9f, h = len * 0.6f;
            for (int i = 0; i < 2; i++) {
                c.save();
                float cx = i == 0 ? b.left + len * 0.55f : b.right - len * 0.55f;
                c.rotate(i == 0 ? -7 : 6, cx, b.top + len * 0.2f);
                p.setColor(Color.argb(150, 0xFF, 0xF6, 0xDE));
                c.drawRect(cx - w / 2, b.top - h * 0.25f, cx + w / 2, b.top - h * 0.25f + h, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(1.2f);
                p.setColor(Color.argb(90, 0xB4, 0xA0, 0x6E));
                c.drawRect(cx - w / 2, b.top - h * 0.25f, cx + w / 2, b.top - h * 0.25f + h, p);
                c.restore();
            }
        }
        @Override
        public void setAlpha(int a) {}
        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {}
        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** A hand-torn paper edge (jagged ink line) — for accents, not rules. */
    public static final class TornLineDrawable extends Drawable {
        private final int color;
        public TornLineDrawable(int inkColor) { super(); color = inkColor; }
        @Override
        public void draw(Canvas c) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty()) return;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(color);
            p.setStrokeWidth(Math.max(1.5f, b.height() * 0.28f));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            android.graphics.Path path = new android.graphics.Path();
            java.util.Random rnd = new java.util.Random(b.top * 31 + b.width());
            float mid = b.height() * 0.5f;
            path.moveTo(0, mid);
            float seg = Math.max(6f, b.width() / 26f);
            for (float x = seg; x <= b.width(); x += seg) {
                path.lineTo(x, mid + (rnd.nextFloat() * 2f - 1f) * b.height() * 0.42f);
            }
            c.drawPath(path, p);
        }
        @Override
        public void setAlpha(int a) {}
        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {}
        @Override
        public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /**
     * Attaches tactile 60 FPS spring scale micro-animation + border highlight on touch.
     */
    public static void attachPressFeedback(final View view) {
        view.setOnTouchListener((v, ev) -> {
            int act = ev.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN) {
                v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(85)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(165)
                        .setInterpolator(new OvershootInterpolator(1.6f)).start();
            }
            return false;
        });
    }

    /**
     * Smooth entrance fade + subtle upward glide when switching Studio tabs or loading cards.
     */
    public static void animateEntrance(View panel, long delayMs) {
        if (panel == null) return;
        float offset = dp(panel.getContext(), 8);
        panel.setAlpha(0f);
        panel.setTranslationY(offset);
        panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(160)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * Creates a unified Vortex Studio button/chip with tactile spring animation.
     */
    public static TextView makeButton(Context ctx, String label, ButtonVariant variant, boolean compact) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(handTypeface());
        tv.setTextSize(compact ? 13.0f : 14.0f);
        int padH = dp(ctx, compact ? 11 : 14);
        int padV = dp(ctx, compact ? 7 : 10);
        tv.setPadding(padH, padV, padH, padV);
        applyButtonVariant(ctx, tv, variant, compact);
        attachPressFeedback(tv);
        return tv;
    }

    public static void applyButtonVariant(Context ctx, TextView tv, ButtonVariant variant, boolean compact) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(ctx, compact ? 8 : 10));
        switch (variant) {
            case PRIMARY:
                gd.setColor(C_CYAN);
                gd.setStroke(dp(ctx, 1), C_CYAN);
                tv.setTextColor(C_CYAN_DARK_TX);
                break;
            case ACCENT:
                gd.setColor(C_CYAN_TINT);
                gd.setStroke(dp(ctx, 1.2f), C_CYAN);
                tv.setTextColor(C_CYAN);
                break;
            case DANGER:
                gd.setColor(C_DANGER_BG);
                gd.setStroke(dp(ctx, 1), C_DANGER_BDR);
                tv.setTextColor(C_DANGER_TX);
                break;
            case SECONDARY:
            default:
                gd.setColor(C_SURFACE);
                gd.setStroke(dp(ctx, 1), C_BORDER);
                tv.setTextColor(C_TEXT);
                break;
        }
        tv.setBackground(gd);
    }

    /**
     * Creates a unified badge chip (for 2D/3D, ES6 JS / LUA / VXS).
     */
    public static TextView makeBadge(Context ctx, String label, boolean highlighted) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(11.5f);
        tv.setTypeface(handTypeface());
        tv.setSingleLine(true);
        tv.setPadding(dp(ctx, 7), dp(ctx, 3.5f), dp(ctx, 7), dp(ctx, 3.5f));
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(ctx, 6));
        if (highlighted) {
            gd.setColor(C_CYAN_TINT);
            gd.setStroke(dp(ctx, 1), C_CYAN);
            tv.setTextColor(C_CYAN);
        } else {
            gd.setColor(C_SURFACE_HI);
            gd.setStroke(dp(ctx, 1), C_BORDER);
            tv.setTextColor(C_TEXT_MUTED);
        }
        tv.setBackground(gd);
        return tv;
    }

    /**
     * Creates a sleek dark rounded EditText with animated Cyan focus border glow.
     */
    public static EditText createInput(Context ctx, String value, boolean numeric, boolean multiline) {
        EditText et = new EditText(ctx);
        et.setText(value == null ? "" : value);
        et.setTextColor(C_TEXT);
        et.setHintTextColor(C_TEXT_DIM);
        et.setTextSize(14.5f);
        int padH = dp(ctx, 11);
        int padV = dp(ctx, 9);
        et.setPadding(padH, padV, padH, padV);

        final GradientDrawable bg = new GradientDrawable();
        bg.setColor(C_INPUT_BG);
        bg.setCornerRadius(dp(ctx, 9));
        bg.setStroke(dp(ctx, 1), C_BORDER);
        et.setBackground(bg);

        if (numeric) {
            et.setSingleLine(true);
            et.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else if (!multiline) {
            et.setSingleLine(true);
        } else {
            et.setGravity(Gravity.TOP | Gravity.START);
        }

        et.setOnFocusChangeListener((v, hasFocus) -> {
            ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(),
                    hasFocus ? C_BORDER : C_CYAN,
                    hasFocus ? C_CYAN : C_BORDER);
            anim.setDuration(150);
            anim.addUpdateListener(a -> bg.setStroke(dp(ctx, hasFocus ? 1.5f : 1f), (Integer) a.getAnimatedValue()));
            anim.start();
        });
        return et;
    }

    // =========================================================================
    // Custom VortexDropdown — Replaces android.widget.Spinner completely!
    // =========================================================================
    public static final class VortexDropdown extends LinearLayout {
        private final String[] items;
        private int selectedIndex = 0;
        private final TextView valueTv;
        private final TextView chevronTv;
        private final GradientDrawable fieldBg;
        private SelectionListener listener;

        public VortexDropdown(Context ctx, String[] options, String initialValue) {
            super(ctx);
            this.items = options != null && options.length > 0 ? options : new String[]{"—"};
            for (int i = 0; i < this.items.length; i++) {
                if (this.items[i].equalsIgnoreCase(initialValue)) {
                    this.selectedIndex = i;
                    break;
                }
            }
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(dp(ctx, 42));
            setPadding(dp(ctx, 12), dp(ctx, 9), dp(ctx, 12), dp(ctx, 9));

            fieldBg = new GradientDrawable();
            fieldBg.setColor(C_SURFACE);
            fieldBg.setCornerRadius(dp(ctx, 9));
            fieldBg.setStroke(dp(ctx, 1), C_BORDER);
            setBackground(fieldBg);

            valueTv = new TextView(ctx);
            valueTv.setText(this.items[selectedIndex]);
            valueTv.setTextColor(C_TEXT);
            valueTv.setTextSize(14.5f);
            valueTv.setSingleLine(true);
            valueTv.setEllipsize(TextUtils.TruncateAt.END);
            addView(valueTv, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            chevronTv = new TextView(ctx);
            chevronTv.setText("▾");
            chevronTv.setTextColor(C_CYAN);
            chevronTv.setTextSize(15.5f);
            chevronTv.setTypeface(Typeface.DEFAULT_BOLD);
            chevronTv.setPadding(dp(ctx, 8), 0, 0, 0);
            addView(chevronTv);

            attachPressFeedback(this);
            setOnClickListener(v -> openDropdownPopup());
        }

        public int getSelectedItemPosition() {
            return selectedIndex;
        }

        public String getSelectedItem() {
            return items[selectedIndex];
        }

        public void setSelectedIndex(int idx, boolean fireCallback) {
            if (idx >= 0 && idx < items.length) {
                selectedIndex = idx;
                valueTv.setText(items[idx]);
                if (fireCallback && listener != null) {
                    listener.onSelected(idx, items[idx]);
                }
            }
        }

        public void setOnSelectionListener(SelectionListener l) {
            this.listener = l;
        }

        private void openDropdownPopup() {
            Context ctx = getContext();
            chevronTv.animate().rotation(180f).setDuration(160).start();
            fieldBg.setStroke(dp(ctx, 1.5f), C_CYAN);

            ScrollView sv = new ScrollView(ctx);
            GradientDrawable menuBg = new GradientDrawable();
            menuBg.setColor(C_HEADER);
            menuBg.setCornerRadius(dp(ctx, 11));
            menuBg.setStroke(dp(ctx, 1.2f), C_CYAN);
            sv.setBackground(menuBg);

            LinearLayout list = new LinearLayout(ctx);
            list.setOrientation(VERTICAL);
            int pad = dp(ctx, 6);
            list.setPadding(pad, pad, pad, pad);

            DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
            int popupW = Math.max(getWidth(), dp(ctx, 210));
            popupW = Math.min(popupW, dm.widthPixels - dp(ctx, 24));
            int maxH = Math.min(dp(ctx, 290), (int) (dm.heightPixels * 0.48f));

            final PopupWindow popup = new PopupWindow(sv, popupW, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                popup.setElevation(dp(ctx, 12));
            }

            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                boolean active = (i == selectedIndex);
                LinearLayout row = new LinearLayout(ctx);
                row.setOrientation(HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(ctx, 12), dp(ctx, 10), dp(ctx, 12), dp(ctx, 10));

                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setCornerRadius(dp(ctx, 7));
                rowBg.setColor(active ? C_CYAN_TINT : Color.TRANSPARENT);
                row.setBackground(rowBg);

                TextView label = new TextView(ctx);
                label.setText(items[i]);
                label.setTextSize(14.3f);
                label.setSingleLine(true);
                label.setEllipsize(TextUtils.TruncateAt.END);
                label.setTextColor(active ? C_CYAN : C_TEXT);
                if (active) label.setTypeface(Typeface.DEFAULT_BOLD);
                row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

                if (active) {
                    TextView check = new TextView(ctx);
                    check.setText("✓");
                    check.setTextColor(C_CYAN);
                    check.setTypeface(Typeface.DEFAULT_BOLD);
                    check.setPadding(dp(ctx, 8), 0, 0, 0);
                    row.addView(check);
                }

                row.setOnClickListener(v -> {
                    boolean changed = (selectedIndex != idx);
                    selectedIndex = idx;
                    valueTv.setText(items[idx]);
                    popup.dismiss();
                    if (changed && listener != null) {
                        listener.onSelected(idx, items[idx]);
                    }
                });
                list.addView(row);
            }

            sv.addView(list);
            // Measure and cap height cleanly for 12-item template menus on compact phones
            sv.measure(MeasureSpec.makeMeasureSpec(popupW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST));
            if (sv.getMeasuredHeight() >= maxH) {
                popup.setHeight(maxH);
            }

            popup.setOnDismissListener(() -> {
                chevronTv.animate().rotation(0f).setDuration(150).start();
                fieldBg.setStroke(dp(ctx, 1), C_BORDER);
            });

            sv.setAlpha(0f);
            sv.animate().alpha(1f).setDuration(130).start();
            popup.showAsDropDown(this, 0, dp(ctx, 4));
        }
    }

    // =========================================================================
    // Custom VortexToggle — Animated Pill Switch (Replaces raw CheckBox!)
    // =========================================================================
    public static final class VortexToggle extends LinearLayout {
        private boolean checked;
        private final SwitchTrackView trackView;
        private Runnable toggleListener;

        public VortexToggle(Context ctx, String labelText, boolean initialChecked) {
            super(ctx);
            this.checked = initialChecked;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(ctx, 2), dp(ctx, 7), dp(ctx, 2), dp(ctx, 7));

            TextView label = new TextView(ctx);
            label.setText(labelText);
            label.setTextColor(C_TEXT);
            label.setTextSize(14.0f);
            addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            trackView = new SwitchTrackView(ctx, initialChecked);
            addView(trackView, new LayoutParams(dp(ctx, 44), dp(ctx, 24)));

            setOnClickListener(v -> {
                setChecked(!checked);
                if (toggleListener != null) toggleListener.run();
            });
        }

        public void setOnToggleListener(Runnable listener) {
            this.toggleListener = listener;
        }

        public boolean isChecked() {
            return checked;
        }

        public void setChecked(boolean next) {
            if (this.checked == next) return;
            this.checked = next;
            trackView.animateTo(next);
        }

        private static final class SwitchTrackView extends View {
            private float progress;
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            private final ArgbEvaluator eval = new ArgbEvaluator();

            SwitchTrackView(Context ctx, boolean init) {
                super(ctx);
                this.progress = init ? 1f : 0f;
            }

            void animateTo(boolean on) {
                ValueAnimator va = ValueAnimator.ofFloat(progress, on ? 1f : 0f);
                va.setDuration(170);
                va.setInterpolator(new OvershootInterpolator(1.2f));
                va.addUpdateListener(a -> {
                    progress = (Float) a.getAnimatedValue();
                    invalidate();
                });
                va.start();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                float w = getWidth();
                float h = getHeight();
                float r = h * 0.5f;
                float clamped = Math.max(0f, Math.min(1f, progress));
                int trackCol = (Integer) eval.evaluate(clamped, C_SURFACE_HI, C_CYAN);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(trackCol);
                rect.set(1f, 1f, w - 1f, h - 1f);
                canvas.drawRoundRect(rect, r, r, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f);
                paint.setColor((Integer) eval.evaluate(clamped, C_BORDER, C_CYAN));
                canvas.drawRoundRect(rect, r, r, paint);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(clamped > 0.5f ? C_CYAN_DARK_TX : C_TEXT);
                float pad = h * 0.2f;
                float thumbR = (h - pad * 2f) * 0.5f;
                float cx = pad + thumbR + progress * (w - pad * 2f - thumbR * 2f);
                canvas.drawCircle(cx, h * 0.5f, thumbR, paint);
            }
        }
    }

    // =========================================================================
    // Custom VortexModal — Unified Dark Obsidian + Cyan Modal Dialog
    // =========================================================================
    public static Dialog showModal(
            Activity act,
            String title,
            View bodyView,
            String primaryLabel,
            Runnable onPrimary,
            String neutralLabel,
            Runnable onNeutral,
            String cancelLabel) {

        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        DisplayMetrics dm = act.getResources().getDisplayMetrics();
        int cardWidth = Math.min(dm.widthPixels - dp(act, 28), dp(act, 460));
        int maxScrollH = (int) (dm.heightPixels * 0.62f);

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(C_HEADER);
        cardBg.setCornerRadius(dp(act, 14));
        cardBg.setStroke(dp(act, 1.2f), C_BORDER);
        card.setBackground(cardBg);
        // washi tape on the corners — this is a sheet of paper, not a panel
        card.setForeground(new TapeDrawable(dp(act, 14)));

        // Torn accent edge under the header — ripped, not ruled
        View accentLine = new View(act);
        accentLine.setBackground(new TornLineDrawable(C_INK));
        card.addView(accentLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 5)));

        // Header Row
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(act, 16), dp(act, 14), dp(act, 14), dp(act, 8));

        TextView titleTv = new TextView(act);
        titleTv.setText(title);
        titleTv.setTextColor(C_TEXT);
        titleTv.setTextSize(16.5f);
        titleTv.setTypeface(handTypeface());
        header.addView(titleTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeX = makeButton(act, "✕", ButtonVariant.SECONDARY, true);
        closeX.setPadding(dp(act, 9), dp(act, 4), dp(act, 9), dp(act, 4));
        closeX.setOnClickListener(v -> dlg.dismiss());
        header.addView(closeX);
        card.addView(header);

        // Scrollable Body (Safe on 360dp portrait + soft keyboard!)
        ScrollView bodyScroll = new ScrollView(act) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int cappedH = MeasureSpec.makeMeasureSpec(maxScrollH, MeasureSpec.AT_MOST);
                super.onMeasure(widthMeasureSpec, cappedH);
            }
        };
        FrameLayout bodyHolder = new FrameLayout(act);
        bodyHolder.setPadding(dp(act, 16), dp(act, 4), dp(act, 16), dp(act, 10));
        bodyHolder.addView(bodyView);
        bodyScroll.addView(bodyHolder);
        card.addView(bodyScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Footer Buttons
        LinearLayout footer = new LinearLayout(act);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        footer.setPadding(dp(act, 14), dp(act, 8), dp(act, 14), dp(act, 14));

        if (cancelLabel != null) {
            TextView cBtn = makeButton(act, cancelLabel, ButtonVariant.SECONDARY, true);
            cBtn.setOnClickListener(v -> dlg.dismiss());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(act, 6);
            footer.addView(cBtn, lp);
        }
        if (neutralLabel != null && onNeutral != null) {
            TextView nBtn = makeButton(act, neutralLabel, ButtonVariant.ACCENT, true);
            nBtn.setOnClickListener(v -> {
                dlg.dismiss();
                onNeutral.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(act, 6);
            footer.addView(nBtn, lp);
        }
        if (primaryLabel != null) {
            ButtonVariant pVar = primaryLabel.toLowerCase().contains("delete")
                    ? ButtonVariant.DANGER : ButtonVariant.PRIMARY;
            TextView pBtn = makeButton(act, primaryLabel, pVar, true);
            pBtn.setOnClickListener(v -> {
                dlg.dismiss();
                if (onPrimary != null) onPrimary.run();
            });
            footer.addView(pBtn);
        }
        card.addView(footer);

        FrameLayout outer = new FrameLayout(act);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        outer.addView(card, cardLp);

        dlg.setContentView(outer);
        card.setScaleX(0.93f);
        card.setScaleY(0.93f);
        card.setAlpha(0f);
        dlg.show();
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(175)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
        return dlg;
    }


    // =====================================================================
    // THEME PICKER (v4.9.2) — palette sheet + import, then recreate() the activity
    // =====================================================================

    /** Opens the palette sheet: built-in themes, dark mode and custom-theme import. */
    public static void showThemeSheet(final Activity act) {
        final com.vortex.engine.core.theme.VortexTheme cur =
                com.vortex.engine.core.theme.ThemeRegistry.active();

        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView hint = new TextView(act);
        hint.setText("Pick a palette. Import accepts a Vortex theme JSON, a slot map, or a plain list of #hex colours.");
        hint.setTextColor(C_TEXT_MUTED);
        hint.setTextSize(12.5f);
        hint.setPadding(0, 0, 0, dp(act, 10));
        body.addView(hint);

        for (final com.vortex.engine.core.theme.VortexTheme t
                : com.vortex.engine.core.theme.ThemeRegistry.all()) {
            body.addView(themeRow(act, t, t.id.equals(cur.id)));
        }

        TextView importBtn = makeButton(act, "＋ IMPORT THEME", ButtonVariant.ACCENT, false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = dp(act, 12);
        importBtn.setOnClickListener(v -> showThemeImport(act));
        body.addView(importBtn, ip);

        showModal(act, "Palette", body, "CLOSE", null, null, null, null);
    }

    private static View themeRow(final Activity act, final com.vortex.engine.core.theme.VortexTheme t,
                                 boolean isActive) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 10), dp(act, 9), dp(act, 10), dp(act, 9));
        row.setBackgroundColor(isActive ? C_CYAN_TINT : C_PANEL);

        LinearLayout chips = new LinearLayout(act);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] slots = {com.vortex.engine.core.theme.VortexTheme.SHEET,
                com.vortex.engine.core.theme.VortexTheme.PANEL,
                com.vortex.engine.core.theme.VortexTheme.SURFACE,
                com.vortex.engine.core.theme.VortexTheme.ACCENT,
                com.vortex.engine.core.theme.VortexTheme.INK};
        for (String slot : slots) {
            View chip = new View(act);
            chip.setBackgroundColor(argb(t.get(slot)));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(act, 16), dp(act, 16));
            clp.rightMargin = dp(act, 3);
            chips.addView(chip, clp);
        }
        row.addView(chips);

        TextView name = new TextView(act);
        name.setText((isActive ? "● " : "") + t.name);
        name.setTextColor(C_TEXT);
        name.setTextSize(14.0f);
        name.setTypeface(bodyTypeface());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(act, 10);
        row.addView(name, nlp);

        row.setOnClickListener(v -> {
            com.vortex.engine.core.theme.ThemeRegistry.setActive(t.id);
            Toast.makeText(act, "Palette: " + t.name, Toast.LENGTH_SHORT).show();
            act.recreate();
        });

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(act, 6);
        row.setLayoutParams(rlp);
        return row;
    }

    /** Paste-a-theme dialog (no file picker needed on a phone). */
    public static void showThemeImport(final Activity act) {
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView hint = new TextView(act);
        hint.setText("Paste theme JSON or colours:");
        hint.setTextColor(C_TEXT_MUTED);
        hint.setTextSize(12.5f);
        body.addView(hint);

        final EditText input = createInput(act,
                "{\"sheet\":\"#fffdf6\",\"ink\":\"#33291d\",\"accent\":\"#ffee9c\"}", false, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 120));
        lp.topMargin = dp(act, 8);
        body.addView(input, lp);

        showModal(act, "Import palette", body, "IMPORT & APPLY", () -> {
            try {
                com.vortex.engine.core.theme.VortexTheme t =
                        com.vortex.engine.core.theme.ThemeRegistry.importText(
                                input.getText().toString(), "Imported palette");
                com.vortex.engine.core.theme.ThemeRegistry.setActive(t.id);
                Toast.makeText(act, "Imported " + t.name, Toast.LENGTH_SHORT).show();
                act.recreate();
            } catch (Exception ex) {
                Toast.makeText(act, "Could not import: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, null, null, "CANCEL");
    }

    /** Header chip that opens the palette sheet; shows the active palette (or DARK). */
    public static TextView makePaletteChip(final Activity act) {
        com.vortex.engine.core.theme.VortexTheme t = com.vortex.engine.core.theme.ThemeRegistry.active();
        TextView chip = new TextView(act);
        chip.setText("◐ " + (t.dark ? "DARK" : shortThemeName(t)));
        chip.setTextColor(C_TEXT);
        chip.setTextSize(11.5f);
        chip.setTypeface(bodyTypeface());
        chip.setSingleLine(true);
        chip.setPadding(dp(act, 9), dp(act, 5), dp(act, 9), dp(act, 5));
        chip.setBackgroundColor(C_SURFACE);
        chip.setOnClickListener(v -> showThemeSheet(act));
        return chip;
    }

    private static String shortThemeName(com.vortex.engine.core.theme.VortexTheme t) {
        String n = t.name == null ? "PAPER" : t.name.toUpperCase(java.util.Locale.US);
        int cut = n.indexOf(' ');
        return cut > 0 ? n.substring(0, cut) : n;
    }

    public static Dialog showMessageModal(
            Activity act, String title, String message,
            String primaryLabel, Runnable onPrimary,
            String neutralLabel, Runnable onNeutral,
            String cancelLabel) {
        TextView tv = new TextView(act);
        tv.setText(message);
        tv.setTextColor(C_TEXT_MUTED);
        tv.setTextSize(14.0f);
        tv.setLineSpacing(dp(act, 3), 1f);
        return showModal(act, title, tv, primaryLabel, onPrimary, neutralLabel, onNeutral, cancelLabel);
    }

    public static Dialog showPickerModal(
            Activity act, String title, String[] items, int activeIndex, SelectionListener onPick) {
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        final Dialog[] ref = new Dialog[1];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            boolean active = (i == activeIndex);
            TextView row = makeButton(act, items[i],
                    active ? ButtonVariant.ACCENT : ButtonVariant.SECONDARY, false);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(act, 6);
            row.setOnClickListener(v -> {
                if (ref[0] != null) ref[0].dismiss();
                if (onPick != null) onPick.onSelected(idx, items[idx]);
            });
            list.addView(row, lp);
        }
        ref[0] = showModal(act, title, list, null, null, null, null, "Close");
        return ref[0];
    }
}
