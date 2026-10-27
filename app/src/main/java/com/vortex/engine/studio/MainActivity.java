package com.vortex.engine.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.ProjectTemplates;
import com.vortex.engine.runtime.StandaloneGameActivity;
import java.util.List;

/**
 * Vortex Engine Studio v2.0 — Responsive Portrait/Landscape Project Hub.
 */
public class MainActivity extends Activity {

    private LinearLayout projectListContainer;
    private TextView termuxPrimaryStatus;
    private TextView termuxPermStatus;
    private TextView permActionBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#090D16"));
        root.setFitsSystemWindows(true);

        // ============================================================
        // Responsive Stacked Header (Zero awkward line-wrapping on 360dp phones!)
        // ============================================================
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackgroundColor(Color.parseColor("#111827"));

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("VORTEX ENGINE");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        brandRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView verChip = new TextView(this);
        verChip.setText("v2.0");
        verChip.setTextColor(Color.parseColor("#00E676"));
        verChip.setTextSize(10.5f);
        verChip.setTypeface(Typeface.DEFAULT_BOLD);
        verChip.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(Color.parseColor("#142E24"));
        chipBg.setCornerRadius(dp(12));
        chipBg.setStroke(dp(1), Color.parseColor("#00E676"));
        verChip.setBackground(chipBg);
        brandRow.addView(verChip);
        header.addView(brandRow);

        TextView subtitle = new TextView(this);
        subtitle.setText("2D & 3D Game Studio • Lua 5.2 (LuaJ) • JBox2D • FastNoiseLite • Termux APK");
        subtitle.setTextColor(Color.parseColor("#94A3B8"));
        subtitle.setTextSize(11.5f);
        subtitle.setPadding(0, dp(3), 0, dp(10));
        header.addView(subtitle);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView setupBtn = makePillButton("🔧 TERMUX SETUP", "#1E293B", "#38BDF8");
        setupBtn.setGravity(Gravity.CENTER);
        setupBtn.setOnClickListener(v -> showTermuxSetupDialog());
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sLp.rightMargin = dp(8);
        actionRow.addView(setupBtn, sLp);

        TextView newBtn = makePillButton("＋ NEW GAME", "#00B8D4", "#00E5FF");
        newBtn.setTextColor(Color.parseColor("#04131A"));
        newBtn.setGravity(Gravity.CENTER);
        newBtn.setOnClickListener(v -> showNewProjectDialog());
        actionRow.addView(newBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(actionRow);
        root.addView(header);

        // ============================================================
        // Smart 2-Line Termux & Permission Banner
        // ============================================================
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        statusCard.setBackgroundColor(Color.parseColor("#0D1524"));

        termuxPrimaryStatus = new TextView(this);
        termuxPrimaryStatus.setTextSize(12f);
        termuxPrimaryStatus.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(termuxPrimaryStatus);

        LinearLayout permRow = new LinearLayout(this);
        permRow.setOrientation(LinearLayout.HORIZONTAL);
        permRow.setGravity(Gravity.CENTER_VERTICAL);
        permRow.setPadding(0, dp(4), 0, 0);

        termuxPermStatus = new TextView(this);
        termuxPermStatus.setTextSize(11.5f);
        permRow.addView(termuxPermStatus, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        permActionBtn = makePillButton("🔓 PERMS", "#1E293B", "#FFB300");
        permActionBtn.setTextSize(10.5f);
        permActionBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
        permActionBtn.setOnClickListener(v -> showRunCommandPermissionHelper());
        permRow.addView(permActionBtn);

        statusCard.addView(permRow);
        root.addView(statusCard);

        // ============================================================
        // Scrollable Game Project Cards
        // ============================================================
        ScrollView scroll = new ScrollView(this);
        projectListContainer = new LinearLayout(this);
        projectListContainer.setOrientation(LinearLayout.VERTICAL);
        projectListContainer.setPadding(dp(14), dp(12), dp(14), dp(28));
        scroll.addView(projectListContainer);

        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatusBanner();
        refreshProjectList();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatusBanner();
        if (requestCode == 401) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "✓ 1-Tap Termux RUN_COMMAND Permission Enabled!", Toast.LENGTH_LONG).show();
            } else {
                showManualPermissionGuideDialog();
            }
        }
    }

    private void refreshStatusBanner() {
        boolean installed = TermuxBridge.isTermuxInstalled(this);
        boolean perm = TermuxBridge.hasRunCommandPermission(this);

        if (!installed) {
            termuxPrimaryStatus.setText("⚠ Termux App Not Installed — Tap 'TERMUX & PERMS' for setup guide");
            termuxPrimaryStatus.setTextColor(Color.parseColor("#FFB300"));
            termuxPermStatus.setText("You can still design & play all 2D/3D games right inside Studio!");
            termuxPermStatus.setTextColor(Color.parseColor("#94A3B8"));
            permActionBtn.setVisibility(View.GONE);
        } else if (perm) {
            termuxPrimaryStatus.setText("✓ Termux Installed & Ready");
            termuxPrimaryStatus.setTextColor(Color.parseColor("#00E676"));
            termuxPermStatus.setText("⚡ 1-Tap Auto-Build & Clipboard Build both active!");
            termuxPermStatus.setTextColor(Color.parseColor("#38BDF8"));
            permActionBtn.setVisibility(View.GONE);
        } else {
            termuxPrimaryStatus.setText("✓ Termux Installed — Clipboard APK Build Ready Right Now!");
            termuxPrimaryStatus.setTextColor(Color.parseColor("#00E676"));
            termuxPermStatus.setText("Optional: Enable RUN_COMMAND for 1-tap auto-trigger");
            termuxPermStatus.setTextColor(Color.parseColor("#CBD5E1"));
            permActionBtn.setVisibility(View.VISIBLE);
        }
    }

    private void showRunCommandPermissionHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !TermuxBridge.hasRunCommandPermission(this)) {
            requestPermissions(new String[]{TermuxBridge.PERMISSION_RUN_COMMAND}, 401);
        } else {
            refreshStatusBanner();
        }
    }

    private void showManualPermissionGuideDialog() {
        String msg =
                "Why did Android show 'Not Granted' even after Termux setup?\n\n" +
                "• Running pkg install inside Termux set up your compilers (aapt2, d8, apksigner) — so 'EXPORT + COPY CMD' ALREADY WORKS 100% right now!\n\n" +
                "• For optional 1-Tap Auto-Builds without pasting, Android/Samsung One UI requires granting the app permission manually if Vortex Engine was installed before Termux:\n\n" +
                "1. Tap 'OPEN APP SETTINGS' below.\n" +
                "2. Tap 'Permissions'.\n" +
                "3. Tap 'Additional permissions' (or the ⋮ menu in top-right → 'All permissions').\n" +
                "4. Allow 'Run commands in Termux environment'.";

        new AlertDialog.Builder(this)
                .setTitle("Enable 1-Tap Termux Permission")
                .setMessage(msg)
                .setPositiveButton("OPEN APP SETTINGS", (d, w) -> TermuxBridge.openAppPermissionSettings(this))
                .setNegativeButton("Close", null)
                .show();
    }

    private void refreshProjectList() {
        projectListContainer.removeAllViews();
        List<GameProject> projects = ProjectRepository.listProjects(this);

        for (GameProject p : projects) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#131C2E"));
            bg.setCornerRadius(dp(14));
            bg.setStroke(dp(1), Color.parseColor("#24324B"));
            card.setBackground(bg);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = dp(12);

            // Row 1: Badges + Full-width Title & Metadata + Delete X
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            boolean is3D = "3D".equalsIgnoreCase(p.dimensionMode);
            TextView dimBadge = makeBadge(is3D ? "3D GL" : "2D",
                    is3D ? "#1B1438" : "#0E2925",
                    is3D ? "#7C4DFF" : "#00E676",
                    is3D ? "#B388FF" : "#00E676");
            topRow.addView(dimBadge);

            boolean isLua = "LUA".equalsIgnoreCase(p.scriptLanguage);
            TextView langBadge = makeBadge(isLua ? "LUA 5.2" : "VXS",
                    isLua ? "#10253A" : "#261C10",
                    isLua ? "#00E5FF" : "#FFB300",
                    isLua ? "#38BDF8" : "#FFD54F");
            LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lbLp.leftMargin = dp(6);
            topRow.addView(langBadge, lbLp);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setPadding(dp(10), 0, dp(6), 0);

            TextView nameTv = new TextView(this);
            nameTv.setText(p.title);
            nameTv.setTextColor(Color.WHITE);
            nameTv.setTextSize(16f);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            nameTv.setSingleLine(true);
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            infoCol.addView(nameTv);

            TextView metaTv = new TextView(this);
            metaTv.setText(p.packageName + " • " + p.entities.size() + " objects"
                    + (!is3D && p.useBox2D ? " • JBox2D" : ""));
            metaTv.setTextColor(Color.parseColor("#94A3B8"));
            metaTv.setTextSize(11.5f);
            metaTv.setSingleLine(true);
            metaTv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            infoCol.addView(metaTv);

            topRow.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView delBtn = makePillButton("✕", "#2D161E", "#FF5252");
            delBtn.setTextSize(11f);
            delBtn.setPadding(dp(10), dp(5), dp(10), dp(5));
            delBtn.setOnClickListener(v -> confirmDelete(p));
            topRow.addView(delBtn);

            card.addView(topRow);

            // Row 2: Clean Full-Width 3-Button Action Bar (Zero squishing!)
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dp(10), 0, 0);

            TextView playBtn = makePillButton("▶ PLAY", "#112D25", "#00E676");
            playBtn.setGravity(Gravity.CENTER);
            playBtn.setOnClickListener(v -> {
                Intent i = new Intent(this, StandaloneGameActivity.class);
                i.putExtra(StandaloneGameActivity.EXTRA_PROJECT_JSON, p.toJsonString());
                startActivity(i);
            });
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            b1.rightMargin = dp(6);
            btnRow.addView(playBtn, b1);

            TextView editBtn = makePillButton("🛠 STUDIO", "#16263D", "#00E5FF");
            editBtn.setGravity(Gravity.CENTER);
            editBtn.setOnClickListener(v -> openEditor(p.id, null));
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.15f);
            b2.rightMargin = dp(6);
            btnRow.addView(editBtn, b2);

            TextView apkBtn = makePillButton("📦 APK", "#261B38", "#B388FF");
            apkBtn.setGravity(Gravity.CENTER);
            apkBtn.setOnClickListener(v -> openEditor(p.id, "BUILD"));
            btnRow.addView(apkBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            card.addView(btnRow);
            card.setOnClickListener(v -> openEditor(p.id, null));
            projectListContainer.addView(card, cardLp);
        }
    }

    private TextView makeBadge(String text, String bgHex, String borderHex, String textHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(10.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Color.parseColor(textHex));
        tv.setPadding(dp(7), dp(4), dp(7), dp(4));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(bgHex));
        g.setCornerRadius(dp(6));
        g.setStroke(dp(1), Color.parseColor(borderHex));
        tv.setBackground(g);
        return tv;
    }

    private void openEditor(String projectId, String initialTab) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId);
        if (initialTab != null) i.putExtra(EditorActivity.EXTRA_INITIAL_TAB, initialTab);
        startActivity(i);
    }

    private void confirmDelete(GameProject p) {
        new AlertDialog.Builder(this)
                .setTitle("Delete '" + p.title + "'?")
                .setMessage("Remove this project from Vortex Studio?")
                .setPositiveButton("Delete", (d, w) -> {
                    ProjectRepository.deleteProject(this, p.id);
                    refreshProjectList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNewProjectDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(14), dp(20), dp(8));

        TextView l1 = new TextView(this);
        l1.setText("Game Title:");
        form.addView(l1);

        EditText titleInput = new EditText(this);
        titleInput.setText("Cyber Odyssey");
        form.addView(titleInput);

        TextView l2 = new TextView(this);
        l2.setText("Android Package ID:");
        l2.setPadding(0, dp(10), 0, 0);
        form.addView(l2);

        EditText pkgInput = new EditText(this);
        pkgInput.setText("com.vortex.game.cyberodyssey");
        form.addView(pkgInput);

        TextView l3 = new TextView(this);
        l3.setText("Starter Template (Lua 5.2, JBox2D, FastNoiseLite 3D, VortexScript):");
        l3.setPadding(0, dp(10), 0, 0);
        form.addView(l3);

        Spinner templateSpinner = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, ProjectTemplates.TEMPLATE_NAMES);
        templateSpinner.setAdapter(ad);
        form.addView(templateSpinner);

        new AlertDialog.Builder(this)
                .setTitle("Create New 2D / 3D Game")
                .setView(form)
                .setPositiveButton("Create & Open", (d, w) -> {
                    String t = titleInput.getText().toString().trim();
                    if (t.isEmpty()) t = "Untitled Game";
                    String pk = pkgInput.getText().toString().trim();
                    if (pk.isEmpty()) pk = "com.vortex.game.custom";
                    String key = ProjectTemplates.TEMPLATE_KEYS[templateSpinner.getSelectedItemPosition()];
                    GameProject created = ProjectRepository.createTemplate(key, t, pk);
                    ProjectRepository.saveProject(this, created);
                    openEditor(created.id, null);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTermuxSetupDialog() {
        String oneLiner = TermuxBridge.getBootstrapOneLiner();
        String msg =
                "1) Termux Compilers (openjdk-21, aapt2, d8, apksigner):\n" +
                "If you already ran the setup command in Termux, your compilers are READY! Tap 'EXPORT + COPY CMD' in any project's BUILD APK tab to build immediately.\n\n" +
                "2) Optional 1-Tap Auto-Build Permission:\n" +
                "If 'RUN_COMMAND' shows Not Granted, tap 'App Permission Settings' below → Permissions → Additional permissions (or ⋮ menu → All permissions) → enable 'Run commands in Termux environment'.";

        new AlertDialog.Builder(this)
                .setTitle("Termux Toolchain & Permissions")
                .setMessage(msg)
                .setPositiveButton("Copy Setup Cmd & Open Termux", (d, w) -> {
                    TermuxBridge.copyToClipboard(this, "Vortex Termux Setup", oneLiner);
                    Toast.makeText(this, "Copied smart setup command!", Toast.LENGTH_LONG).show();
                    TermuxBridge.openTermuxApp(this);
                })
                .setNeutralButton("App Permission Settings", (d, w) -> TermuxBridge.openAppPermissionSettings(this))
                .setNegativeButton("Close", null)
                .show();
    }

    private TextView makePillButton(String label, String bgHex, String strokeHex) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(true);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(9));
        gd.setStroke(dp(1), Color.parseColor(strokeHex));
        tv.setBackground(gd);
        return tv;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
