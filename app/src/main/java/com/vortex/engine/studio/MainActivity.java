package com.vortex.engine.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.runtime.StandaloneGameActivity;
import java.util.List;

public class MainActivity extends Activity {

    private LinearLayout projectsContainer;
    private TextView statusBanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatusAndProjects();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F17"));
        root.setPadding(dp(18), dp(18), dp(18), dp(18));

        // Top Header Card
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(roundRect("#141B26", "#233248", dp(16)));
        header.setPadding(dp(18), dp(16), dp(18), dp(16));

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleCol.setLayoutParams(tcLp);

        TextView title = new TextView(this);
        title.setText("VORTEX ENGINE");
        title.setTextColor(Color.parseColor("#00E5FF"));
        title.setTextSize(22f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("2D & 3D OpenGL ES Game Studio • On-Device Termux APK Builder");
        subtitle.setTextColor(Color.parseColor("#90A4AE"));
        subtitle.setTextSize(12.5f);
        titleCol.addView(subtitle);

        titleRow.addView(titleCol);

        TextView termuxBtn = makeButton("🔧 TERMUX SETUP", "#1E293B", "#00E5FF");
        termuxBtn.setOnClickListener(v -> showTermuxDoctorDialog());
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tbLp.leftMargin = dp(8);
        titleRow.addView(termuxBtn, tbLp);

        TextView newBtn = makeButton("+ NEW GAME", "#00B8D4", "#061018");
        newBtn.setOnClickListener(v -> showNewProjectDialog());
        LinearLayout.LayoutParams nbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nbLp.leftMargin = dp(10);
        titleRow.addView(newBtn, nbLp);

        header.addView(titleRow);

        statusBanner = new TextView(this);
        statusBanner.setTextSize(12f);
        statusBanner.setTextColor(Color.parseColor("#B0BEC5"));
        statusBanner.setPadding(0, dp(10), 0, 0);
        header.addView(statusBanner);

        root.addView(header);

        // Section Label
        TextView secLabel = new TextView(this);
        secLabel.setText("YOUR GAME PROJECTS");
        secLabel.setTextColor(Color.parseColor("#78909C"));
        secLabel.setTextSize(12f);
        secLabel.setTypeface(Typeface.DEFAULT_BOLD);
        secLabel.setPadding(dp(4), dp(16), dp(4), dp(8));
        root.addView(secLabel);

        ScrollView scroll = new ScrollView(this);
        projectsContainer = new LinearLayout(this);
        projectsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(projectsContainer);

        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void refreshStatusAndProjects() {
        boolean termuxInstalled = TermuxBridge.isTermuxInstalled(this);
        boolean runPerm = TermuxBridge.hasRunCommandPermission(this);
        boolean filesAccess = TermuxBridge.hasAllFilesAccess(this);

        String status = (termuxInstalled ? "✓ Termux Installed" : "⚠ Termux Not Installed (Install F-Droid Termux for APK export)")
                + "   •   "
                + (runPerm ? "✓ RUN_COMMAND Ready" : "○ RUN_COMMAND Not Granted (Script Fallback Active)")
                + "   •   "
                + (filesAccess ? "✓ Shared Folder Ready" : "✓ MediaStore ZIP Export Ready");
        statusBanner.setText(status);

        projectsContainer.removeAllViews();
        List<GameProject> projects = ProjectRepository.listProjects(this);
        for (GameProject p : projects) {
            projectsContainer.addView(buildProjectCard(p));
        }
    }

    private View buildProjectCard(final GameProject p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundRect("#141B26", "#1F2C3F", dp(14)));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        // Mode Badge (3D / 2D)
        boolean is3D = "3D".equalsIgnoreCase(p.dimensionMode);
        TextView badge = new TextView(this);
        badge.setText(is3D ? "3D\nGLES" : "2D\nGLES");
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(11f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setTextColor(Color.WHITE);
        badge.setBackground(roundRect(is3D ? "#512DA8" : "#00838F", is3D ? "#7C4DFF" : "#00E5FF", dp(10)));
        badge.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(badge);

        // Info Column
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(14);
        infoLp.rightMargin = dp(10);
        info.setLayoutParams(infoLp);

        TextView nameTv = new TextView(this);
        nameTv.setText(p.title);
        nameTv.setTextColor(Color.WHITE);
        nameTv.setTextSize(16.5f);
        nameTv.setTypeface(Typeface.DEFAULT_BOLD);
        info.addView(nameTv);

        TextView metaTv = new TextView(this);
        metaTv.setText(p.packageName + "  •  " + p.entities.size() + " entities  •  v" + p.versionName);
        metaTv.setTextColor(Color.parseColor("#8B9BB4"));
        metaTv.setTextSize(12f);
        info.addView(metaTv);

        card.addView(info);

        // Action Buttons
        TextView playBtn = makeButton("▶ PLAY", "#1B3B2B", "#00E676");
        playBtn.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, StandaloneGameActivity.class);
            i.putExtra(StandaloneGameActivity.EXTRA_PROJECT_JSON, p.toJsonString());
            startActivity(i);
        });
        card.addView(playBtn);

        TextView editBtn = makeButton("OPEN STUDIO", "#00ACC1", "#07121A");
        editBtn.setOnClickListener(v -> openProjectInEditor(p.id));
        LinearLayout.LayoutParams ebLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ebLp.leftMargin = dp(8);
        card.addView(editBtn, ebLp);

        TextView delBtn = makeButton("✕", "#2A1820", "#FF5252");
        delBtn.setOnClickListener(v -> confirmDelete(p));
        LinearLayout.LayoutParams dbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dbLp.leftMargin = dp(8);
        card.addView(delBtn, dbLp);

        return card;
    }

    private void openProjectInEditor(String projectId) {
        Intent i = new Intent(this, EditorActivity.class);
        i.putExtra(EditorActivity.EXTRA_PROJECT_ID, projectId);
        startActivity(i);
    }

    private void showNewProjectDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), dp(8));

        TextView tLabel = new TextView(this);
        tLabel.setText("Starter Template (2D & 3D OpenGL ES):");
        tLabel.setTextColor(Color.LTGRAY);
        box.addView(tLabel);

        final String[] keys = {
                "NEON_3D", "SKY_ISLANDS_3D", "PLATFORMER_2D", "SHOOTER_2D", "BLANK_3D", "BLANK_2D"
        };
        final String[] names = {
                "3D: Neon Horizon Runner (Custom .obj Ship + Obstacles)",
                "3D: Sky Islands Parkour (3D Physics & Floating Platforms)",
                "2D: Starbound Cavern (Platformer + Enemy AI + Coins)",
                "2D: Nova Blaster (Top-Down Space Shooter + Lasers)",
                "3D: Blank OpenGL ES Sandbox",
                "2D: Blank 2D Sandbox"
        };
        final Spinner templateSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
        templateSpinner.setAdapter(adapter);
        box.addView(templateSpinner);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("Game Title (e.g. Cyber Drift 3D)");
        titleInput.setText("Cyber Drift 3D");
        box.addView(titleInput);

        final EditText pkgInput = new EditText(this);
        pkgInput.setHint("Package ID (e.g. com.mygames.cyberdrift)");
        pkgInput.setText("com.vortex.game.cyberdrift");
        box.addView(pkgInput);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Create New Game Project")
                .setView(box)
                .setPositiveButton("Create & Open", (d, w) -> {
                    int idx = templateSpinner.getSelectedItemPosition();
                    String title = titleInput.getText().toString().trim();
                    if (title.isEmpty()) title = "My Vortex Game";
                    String pkg = pkgInput.getText().toString().trim();
                    if (pkg.isEmpty()) pkg = "com.vortex.game.custom";

                    GameProject created = ProjectRepository.createTemplate(keys[idx], title, pkg);
                    ProjectRepository.saveProject(MainActivity.this, created);
                    openProjectInEditor(created.id);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTermuxDoctorDialog() {
        boolean installed = TermuxBridge.isTermuxInstalled(this);
        boolean runPerm = TermuxBridge.hasRunCommandPermission(this);
        boolean allFiles = TermuxBridge.hasAllFilesAccess(this);

        String msg = "Vortex Engine compiles standalone Android APKs right on your device using Termux!\n\n"
                + "1. Termux App: " + (installed ? "✓ Installed" : "✗ Not detected (Install Termux from F-Droid or GitHub)") + "\n"
                + "2. RUN_COMMAND Permission: " + (runPerm ? "✓ Granted" : "○ Not yet granted") + "\n"
                + "3. Shared Folder Access: " + (allFiles ? "✓ Full Access" : "○ Using MediaStore ZIP mode (or tap Grant Files)") + "\n\n"
                + "First-time setup installs openjdk-17, aapt, aapt2, d8, and apksigner inside Termux and enables allow-external-apps=true.";

        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Termux APK Toolchain Setup")
                .setMessage(msg)
                .setPositiveButton("Copy Setup Cmd & Open Termux", (d, w) -> {
                    TermuxBridge.copyToClipboard(MainActivity.this, "Vortex Termux Setup", TermuxBridge.getBootstrapOneLiner());
                    Toast.makeText(MainActivity.this, "Setup command copied! Paste in Termux & press Enter.", Toast.LENGTH_LONG).show();
                    TermuxBridge.openTermuxApp(MainActivity.this);
                })
                .setNeutralButton("Grant Permissions", (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !TermuxBridge.hasRunCommandPermission(MainActivity.this)) {
                        requestPermissions(new String[]{TermuxBridge.PERMISSION_RUN_COMMAND}, 301);
                    }
                    if (!TermuxBridge.hasAllFilesAccess(MainActivity.this)) {
                        TermuxBridge.requestAllFilesAccess(MainActivity.this, 302);
                    }
                })
                .setNegativeButton("Close", null);
        b.show();
    }

    private void confirmDelete(final GameProject p) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete '" + p.title + "'?")
                .setMessage("This removes the local project from Vortex Studio.")
                .setPositiveButton("Delete", (d, w) -> {
                    ProjectRepository.deleteProject(MainActivity.this, p.id);
                    refreshStatusAndProjects();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private TextView makeButton(String text, String bgHex, String fgHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(fgHex));
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        tv.setBackground(roundRect(bgHex, fgHex, dp(10)));
        return tv;
    }

    private GradientDrawable roundRect(String fillHex, String strokeHex, int radiusPx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(radiusPx);
        gd.setStroke(2, Color.parseColor(strokeHex));
        return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
