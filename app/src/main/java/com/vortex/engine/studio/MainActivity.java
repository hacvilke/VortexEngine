package com.vortex.engine.studio;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.ProjectTemplates;
import com.vortex.engine.runtime.StandaloneGameActivity;
import java.util.List;

/**
 * Vortex Engine Studio v4.1 — Responsive Portrait/Landscape Project Hub
 * Unified Obsidian + Electric Cyan Design System, Custom Dropdowns & Modals.
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
        VortexMobileUi.applyDarkSystemBars(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(VortexMobileUi.C_VOID);
        root.setFitsSystemWindows(true);

        // ============================================================
        // Responsive Header (Clean 360dp Portrait & Landscape Fit)
        // ============================================================
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(12));
        header.setBackgroundColor(VortexMobileUi.C_HEADER);

        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("VORTEX ENGINE");
        title.setTextColor(VortexMobileUi.C_CYAN);
        title.setTextSize(19.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setLetterSpacing(0.03f);
        title.setSingleLine(true);
        brandRow.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView verChip = VortexMobileUi.makeBadge(this, "v4.3 STUDIO", true);
        brandRow.addView(verChip);
        header.addView(brandRow);

        TextView subtitle = new TextView(this);
        subtitle.setText("Native Java • ES6 JS • Lua 5.2 • LRM P2P • Forge AI • 2D/3D Studio");
        subtitle.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        subtitle.setTextSize(11.5f);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setPadding(0, dp(3), 0, dp(11));
        header.addView(subtitle);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView setupBtn = VortexMobileUi.makeButton(this, "🔧 TERMUX SETUP", VortexMobileUi.ButtonVariant.SECONDARY, false);
        setupBtn.setOnClickListener(v -> showTermuxSetupDialog());
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sLp.rightMargin = dp(8);
        actionRow.addView(setupBtn, sLp);

        TextView newBtn = VortexMobileUi.makeButton(this, "＋ NEW GAME", VortexMobileUi.ButtonVariant.PRIMARY, false);
        newBtn.setOnClickListener(v -> showNewProjectDialog());
        actionRow.addView(newBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        header.addView(actionRow);
        root.addView(header);

        // ============================================================
        // Subtle 1px Cyan-Slate Divider + Smart Toolchain Banner
        // ============================================================
        View headerLine = new View(this);
        headerLine.setBackgroundColor(VortexMobileUi.C_BORDER);
        root.addView(headerLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding(dp(16), dp(9), dp(16), dp(9));
        statusCard.setBackgroundColor(VortexMobileUi.C_PANEL);

        termuxPrimaryStatus = new TextView(this);
        termuxPrimaryStatus.setTextSize(11.8f);
        termuxPrimaryStatus.setTypeface(Typeface.DEFAULT_BOLD);
        statusCard.addView(termuxPrimaryStatus);

        LinearLayout permRow = new LinearLayout(this);
        permRow.setOrientation(LinearLayout.HORIZONTAL);
        permRow.setGravity(Gravity.CENTER_VERTICAL);
        permRow.setPadding(0, dp(3), 0, 0);

        termuxPermStatus = new TextView(this);
        termuxPermStatus.setTextSize(11f);
        permRow.addView(termuxPermStatus, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        permActionBtn = VortexMobileUi.makeButton(this, "ENABLE 1-TAP", VortexMobileUi.ButtonVariant.ACCENT, true);
        permActionBtn.setPadding(dp(9), dp(4), dp(9), dp(4));
        permActionBtn.setTextSize(10f);
        permActionBtn.setOnClickListener(v -> showRunCommandPermissionHelper());
        permRow.addView(permActionBtn);

        statusCard.addView(permRow);
        root.addView(statusCard);

        // ============================================================
        // Scrollable Game Project Cards
        // ============================================================
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
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
            termuxPrimaryStatus.setText("• Termux Optional — Design & Play 2D/3D Games Instantly Inside Studio");
            termuxPrimaryStatus.setTextColor(VortexMobileUi.C_CYAN);
            termuxPermStatus.setText("Install Termux anytime for on-device Android .apk & LRM P2P builds");
            termuxPermStatus.setTextColor(VortexMobileUi.C_TEXT_MUTED);
            permActionBtn.setVisibility(View.GONE);
        } else if (perm) {
            termuxPrimaryStatus.setText("✓ Termux Connected — 1-Tap APK & LRM P2P Active");
            termuxPrimaryStatus.setTextColor(VortexMobileUi.C_CYAN);
            termuxPermStatus.setText("Both automatic background builds & clipboard fallback ready");
            termuxPermStatus.setTextColor(VortexMobileUi.C_TEXT_MUTED);
            permActionBtn.setVisibility(View.GONE);
        } else {
            termuxPrimaryStatus.setText("✓ Termux Installed — Clipboard APK Build Ready");
            termuxPrimaryStatus.setTextColor(VortexMobileUi.C_CYAN);
            termuxPermStatus.setText("Optional: allow RUN_COMMAND for 1-tap auto-trigger");
            termuxPermStatus.setTextColor(VortexMobileUi.C_TEXT_MUTED);
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
                "• Running pkg install inside Termux already configured your compilers (aapt2, d8, apksigner, lrm) — so 'EXPORT + COPY CMD' works 100% right now.\n\n" +
                "• For optional 1-Tap Auto-Builds without pasting, grant the permission manually in Android Settings:\n\n" +
                "1. Tap 'APP SETTINGS' below.\n" +
                "2. Tap 'Permissions' → 'Additional permissions' (or ⋮ → 'All permissions').\n" +
                "3. Enable 'Run commands in Termux environment'.";

        VortexMobileUi.showMessageModal(
                this,
                "Enable 1-Tap Termux Permission",
                msg,
                "APP SETTINGS",
                () -> TermuxBridge.openAppPermissionSettings(this),
                null,
                null,
                "Close");
    }

    private void refreshProjectList() {
        projectListContainer.removeAllViews();
        List<GameProject> projects = ProjectRepository.listProjects(this);

        TextView secHead = new TextView(this);
        secHead.setText("YOUR GAME PROJECTS (" + projects.size() + ")");
        secHead.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        secHead.setTextSize(11f);
        secHead.setTypeface(Typeface.DEFAULT_BOLD);
        secHead.setLetterSpacing(0.06f);
        secHead.setPadding(dp(2), 0, 0, dp(8));
        projectListContainer.addView(secHead);

        int idx = 0;
        for (GameProject p : projects) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(VortexMobileUi.C_SURFACE);
            bg.setCornerRadius(dp(13));
            bg.setStroke(dp(1), VortexMobileUi.C_BORDER);
            card.setBackground(bg);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardLp.bottomMargin = dp(10);

            // Row 1: Unified Badges + Title & Metadata + Subtle Delete
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            boolean is3D = "3D".equalsIgnoreCase(p.dimensionMode);
            TextView dimBadge = VortexMobileUi.makeBadge(this, is3D ? "3D GL" : "2D", true);
            topRow.addView(dimBadge);

            boolean isJs = "JS".equalsIgnoreCase(p.scriptLanguage);
            boolean isLua = "LUA".equalsIgnoreCase(p.scriptLanguage);
            TextView langBadge = VortexMobileUi.makeBadge(
                    this, isJs ? "ES6 JS" : (isLua ? "LUA 5.2" : "VXS"), false);
            LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lbLp.leftMargin = dp(5);
            topRow.addView(langBadge, lbLp);

            LinearLayout infoCol = new LinearLayout(this);
            infoCol.setOrientation(LinearLayout.VERTICAL);
            infoCol.setPadding(dp(10), 0, dp(6), 0);

            TextView nameTv = new TextView(this);
            nameTv.setText(p.title);
            nameTv.setTextColor(VortexMobileUi.C_TEXT);
            nameTv.setTextSize(15f);
            nameTv.setTypeface(Typeface.DEFAULT_BOLD);
            nameTv.setSingleLine(true);
            nameTv.setEllipsize(TextUtils.TruncateAt.END);
            infoCol.addView(nameTv);

            TextView metaTv = new TextView(this);
            metaTv.setText(p.entities.size() + " entities"
                    + (!is3D && p.useBox2D ? " • JBox2D" : "")
                    + (p.musicEnabled ? " • Synth" : "")
                    + " • " + p.packageName);
            metaTv.setTextColor(VortexMobileUi.C_TEXT_MUTED);
            metaTv.setTextSize(11f);
            metaTv.setSingleLine(true);
            metaTv.setEllipsize(TextUtils.TruncateAt.END);
            infoCol.addView(metaTv);

            topRow.addView(infoCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView delBtn = VortexMobileUi.makeButton(this, "✕", VortexMobileUi.ButtonVariant.DANGER, true);
            delBtn.setPadding(dp(9), dp(4), dp(9), dp(4));
            delBtn.setOnClickListener(v -> confirmDelete(p));
            topRow.addView(delBtn);

            card.addView(topRow);

            // Row 2: Cohesive 3-Action Bar (Unified Theme — Zero Rainbow Clutter!)
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setPadding(0, dp(10), 0, 0);

            TextView playBtn = VortexMobileUi.makeButton(this, "▶ PLAY", VortexMobileUi.ButtonVariant.ACCENT, true);
            playBtn.setOnClickListener(v -> {
                Intent i = new Intent(this, StandaloneGameActivity.class);
                i.putExtra(StandaloneGameActivity.EXTRA_PROJECT_JSON, p.toJsonString());
                startActivity(i);
            });
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            b1.rightMargin = dp(6);
            btnRow.addView(playBtn, b1);

            TextView editBtn = VortexMobileUi.makeButton(this, "OPEN STUDIO", VortexMobileUi.ButtonVariant.PRIMARY, true);
            editBtn.setOnClickListener(v -> openEditor(p.id, null));
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.25f);
            b2.rightMargin = dp(6);
            btnRow.addView(editBtn, b2);

            TextView apkBtn = VortexMobileUi.makeButton(this, "EXPORT", VortexMobileUi.ButtonVariant.SECONDARY, true);
            apkBtn.setOnClickListener(v -> openEditor(p.id, "BUILD"));
            btnRow.addView(apkBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.95f));

            card.addView(btnRow);
            card.setOnClickListener(v -> openEditor(p.id, null));
            projectListContainer.addView(card, cardLp);

            VortexMobileUi.animateEntrance(card, Math.min(idx * 22L, 160L));
            idx++;
        }
    }

    private void openEditor(String projectId, String initialTab) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId);
        if (initialTab != null) i.putExtra(EditorActivity.EXTRA_INITIAL_TAB, initialTab);
        startActivity(i);
    }

    private void confirmDelete(GameProject p) {
        VortexMobileUi.showMessageModal(
                this,
                "Delete '" + p.title + "'?",
                "Remove this game project permanently from Vortex Studio?",
                "Delete Project",
                () -> {
                    ProjectRepository.deleteProject(this, p.id);
                    refreshProjectList();
                },
                null,
                null,
                "Cancel");
    }

    private void showNewProjectDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        form.addView(makeFieldLabel("Game Title"));
        EditText titleInput = VortexMobileUi.createInput(this, "Cyber Odyssey", false, false);
        form.addView(titleInput);

        form.addView(makeFieldLabel("Android Package ID"));
        EditText pkgInput = VortexMobileUi.createInput(this, "com.vortex.game.cyberodyssey", false, false);
        form.addView(pkgInput);

        form.addView(makeFieldLabel("Starter Template (ES6 JS, Lua 5.2, JBox2D, 3D GL)"));
        VortexMobileUi.VortexDropdown templateDrop = new VortexMobileUi.VortexDropdown(
                this, ProjectTemplates.TEMPLATE_NAMES, ProjectTemplates.TEMPLATE_NAMES[0]);
        form.addView(templateDrop);

        VortexMobileUi.showModal(
                this,
                "Create New 2D / 3D Game",
                form,
                "Create & Open",
                () -> {
                    String t = titleInput.getText().toString().trim();
                    if (t.isEmpty()) t = "Untitled Game";
                    String pk = pkgInput.getText().toString().trim();
                    if (pk.isEmpty()) pk = "com.vortex.game.custom";
                    String key = ProjectTemplates.TEMPLATE_KEYS[templateDrop.getSelectedItemPosition()];
                    GameProject created = ProjectRepository.createTemplate(key, t, pk);
                    ProjectRepository.saveProject(this, created);
                    openEditor(created.id, null);
                },
                null,
                null,
                "Cancel");
    }

    private void showTermuxSetupDialog() {
        String oneLiner = TermuxBridge.getBootstrapOneLiner();
        String msg =
                "1) Termux Toolchain + lrm-mobile:\n" +
                "Installs openjdk-21, aapt2, d8, apksigner, and hacvilke/lrm-mobile (Android 14/15/16 PIE binary) in one command.\n\n" +
                "2) Optional 1-Tap Auto-Build Permission:\n" +
                "Tap 'App Permissions' → Permissions → Additional permissions → enable 'Run commands in Termux environment'.";

        VortexMobileUi.showMessageModal(
                this,
                "Termux Toolchain & LRM Setup",
                msg,
                "Copy Cmd & Open Termux",
                () -> {
                    TermuxBridge.copyToClipboard(this, "Vortex Termux Setup", oneLiner);
                    Toast.makeText(this, "Copied setup command to clipboard!", Toast.LENGTH_LONG).show();
                    TermuxBridge.openTermuxApp(this);
                },
                "App Permissions",
                () -> TermuxBridge.openAppPermissionSettings(this),
                "Close");
    }

    private TextView makeFieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(10), 0, dp(4));
        return tv;
    }

    private int dp(int v) {
        return VortexMobileUi.dp(this, v);
    }
}
