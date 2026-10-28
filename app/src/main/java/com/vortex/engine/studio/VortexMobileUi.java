package com.vortex.engine.studio;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
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

/**
 * VortexMobileUi — Unified Dark Obsidian + Electric Cyan Custom Mobile UI Kit.
 *
 * Replaces raw OS Spinners, CheckBoxes, unstyled EditTexts, and white AlertDialogs
 * with cohesive, custom-drawn, touch-friendly components & micro-animations.
 */
public final class VortexMobileUi {

    // High-Contrast 5-Tier Neutral Dark + Crisp White Studio Palette (ZERO BLUE!)
    public static final int C_VOID         = Color.parseColor("#070709");
    public static final int C_HEADER       = Color.parseColor("#212129");
    public static final int C_PANEL        = Color.parseColor("#16161C");
    public static final int C_SURFACE      = Color.parseColor("#282832");
    public static final int C_SURFACE_HI   = Color.parseColor("#363644");
    public static final int C_INPUT_BG     = Color.parseColor("#0C0C10");
    public static final int C_BORDER       = Color.parseColor("#424252");
    public static final int C_CYAN         = Color.parseColor("#FFFFFF");
    public static final int C_CYAN_TINT    = Color.parseColor("#30303B");
    public static final int C_CYAN_DARK_TX = Color.parseColor("#09090B");
    public static final int C_TEXT         = Color.parseColor("#FAFAFC");
    public static final int C_TEXT_MUTED   = Color.parseColor("#CFCFD9");
    public static final int C_TEXT_DIM     = Color.parseColor("#A4A4B4");
    public static final int C_DANGER_BG    = Color.parseColor("#301419");
    public static final int C_DANGER_BDR   = Color.parseColor("#9C3844");
    public static final int C_DANGER_TX    = Color.parseColor("#FF6B6B");
    public static final int C_OK_TX        = Color.parseColor("#00E676");

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
                w.setNavigationBarColor(C_VOID);
            }
        } catch (Exception ignored) {
        }
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
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextSize(compact ? 11.5f : 12.5f);
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
        tv.setTextSize(10f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
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
        et.setTextSize(13f);
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
            valueTv.setTextSize(13f);
            valueTv.setSingleLine(true);
            valueTv.setEllipsize(TextUtils.TruncateAt.END);
            addView(valueTv, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

            chevronTv = new TextView(ctx);
            chevronTv.setText("▾");
            chevronTv.setTextColor(C_CYAN);
            chevronTv.setTextSize(14f);
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
                label.setTextSize(12.8f);
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
            label.setTextSize(12.5f);
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

        // Top 2dp Cyan Accent Line
        View accentLine = new View(act);
        GradientDrawable lineBg = new GradientDrawable();
        lineBg.setColor(C_CYAN);
        lineBg.setCornerRadii(new float[]{dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 14), 0, 0, 0, 0});
        accentLine.setBackground(lineBg);
        card.addView(accentLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 3)));

        // Header Row
        LinearLayout header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(act, 16), dp(act, 14), dp(act, 14), dp(act, 8));

        TextView titleTv = new TextView(act);
        titleTv.setText(title);
        titleTv.setTextColor(C_TEXT);
        titleTv.setTextSize(15.5f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
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

    public static Dialog showMessageModal(
            Activity act, String title, String message,
            String primaryLabel, Runnable onPrimary,
            String neutralLabel, Runnable onNeutral,
            String cancelLabel) {
        TextView tv = new TextView(act);
        tv.setText(message);
        tv.setTextColor(C_TEXT_MUTED);
        tv.setTextSize(12.5f);
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
