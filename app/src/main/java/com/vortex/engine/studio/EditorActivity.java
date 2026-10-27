package com.vortex.engine.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.audio.SynthAudioEngine;
import com.vortex.engine.core.export.Html5WebExporter;
import com.vortex.engine.core.flow.VortexFlowCompiler;
import com.vortex.engine.core.forge.VortexForgeAgent;
import com.vortex.engine.core.lrm.LrmWorkspaceBridge;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import com.vortex.engine.core.render.VortexGLRenderer;
import com.vortex.engine.core.script.VortexScriptEngine;
import com.vortex.engine.core.storage.VortexSaveStore;
import com.vortex.engine.core.ui.VortexGameSurfaceView;
import android.os.Environment;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vortex Engine Studio v2.0 — Full 2D/3D IDE:
 *  - Responsive 2-Row Portrait/Landscape Header
 *  - 2D/3D OpenGL Scene Editor + FastNoiseLite 3D Terrain Spawner
 *  - Entity & JBox2D Inspector
 *  - 16x16 Touchscreen Pixel Art Sprite Studio
 *  - Dual Lua 5.2 (LuaJ) & VortexScript Code IDE with Full-Width Syntax Banner
 *  - Procedural SFX Synth Soundboard
 *  - Instant Play Mode & Termux Standalone APK Builder
 */
public class EditorActivity extends Activity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";
    public static final String EXTRA_INITIAL_TAB = "extra_initial_tab";

    private GameProject project;
    private VortexGameSurfaceView viewportSurface;

    private LinearLayout sceneBottomToolbar;
    private ScrollView inspectorDrawer;
    private LinearLayout inspectorContent;
    private LinearLayout pixelArtPanel;
    private PixelArtEditorView pixelArtCanvas;
    private EditText spriteNameEdit;
    private LinearLayout scriptPanel;
    private EditText scriptEditor;
    private TextView scriptStatusTv;
    private TextView langToggleBtn;
    private LinearLayout snippetBar;
    private ScrollView flowPanel;
    private LinearLayout flowRuleListContainer;
    private ScrollView lrmPanel;
    private TextView lrmLogTv;
    private ScrollView sfxPanel;
    private ScrollView buildPanel;
    private TextView buildLogTv;
    private TextView telemetryChip;
    private TextView dimToggleBtn;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    private final List<TextView> tabButtons = new ArrayList<>();
    private String currentMode = "SCENE";
    private TermuxBridge.ExportResult lastExport;
    private final SynthAudioEngine sfxPreview = new SynthAudioEngine();

    private final TermuxResultService.ResultListener termuxListener = (code, out, err, errmsg) -> {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== TERMUX BUILD FINISHED (exit=").append(code).append(") ===\n");
        if (out != null && !out.isEmpty()) sb.append(out).append("\n");
        if (err != null && !err.isEmpty()) sb.append("[STDERR]\n").append(err).append("\n");
        if (errmsg != null && !errmsg.isEmpty()) sb.append("[ERRMSG] ").append(errmsg).append("\n");
        if (code == 0 && lastExport != null) {
            sb.append("\n[✓] READY TO INSTALL: ").append(lastExport.expectedSharedApkPath).append("\n");
            Toast.makeText(EditorActivity.this, "Standalone APK Built! Tap INSTALL APK.", Toast.LENGTH_LONG).show();
        }
        appendBuildLog(sb.toString());
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        VortexSaveStore.setSaveRootDir(new File(getFilesDir(), "vortex_saves"));

        String id = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        project = ProjectRepository.loadProject(this, id);
        if (project == null) {
            project = ProjectRepository.listProjects(this).get(0);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F17"));
        root.setFitsSystemWindows(true);

        // 2-Row Compact Top Bar (Zero Portrait Cramping!)
        root.addView(buildTopBar());

        // Main Workspace Area
        FrameLayout workspace = new FrameLayout(this);

        viewportSurface = new VortexGameSurfaceView(this);
        viewportSurface.setSelectionListener(new VortexGameSurfaceView.SelectionListener() {
            @Override
            public void onEntitySelected(Entity entity) {
                runOnUiThread(() -> {
                    if ("INSPECTOR".equals(currentMode)) populateInspector();
                    if (entity != null) {
                        telemetryChip.setText("Selected: " + entity.name + " [" + entity.primitive + "]");
                    }
                });
            }

            @Override
            public void onEntityMoved(Entity entity) {
                runOnUiThread(() -> {
                    if ("INSPECTOR".equals(currentMode)) populateInspector();
                });
            }
        });
        viewportSurface.renderer.setTelemetryListener(new VortexGLRenderer.TelemetryListener() {
            @Override
            public void onHudUpdate(int fps, int entityCount, int score, int health, String banner) {
                viewportSurface.controlsOverlay.updateHud(fps, score, health, banner);
                runOnUiThread(() -> {
                    if (!viewportSurface.renderer.isPlaying()) {
                        telemetryChip.setText(fps + " FPS • " + entityCount + " objs • "
                                + project.dimensionMode + " • " + project.scriptLanguage);
                    }
                });
            }

            @Override
            public void onScriptLog(String line) {
                runOnUiThread(() -> appendBuildLog("[SCRIPT] " + line));
            }
        });
        workspace.addView(viewportSurface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        telemetryChip = new TextView(this);
        telemetryChip.setText("60 FPS");
        telemetryChip.setTextColor(Color.parseColor("#CBD5E1"));
        telemetryChip.setTextSize(11f);
        telemetryChip.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(Color.argb(175, 15, 23, 42));
        chipBg.setCornerRadius(dp(12));
        telemetryChip.setBackground(chipBg);
        FrameLayout.LayoutParams chipLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        chipLp.setMargins(dp(10), dp(10), dp(10), dp(10));
        workspace.addView(telemetryChip, chipLp);

        sceneBottomToolbar = buildSceneBottomToolbar();
        FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        workspace.addView(sceneBottomToolbar, bottomLp);

        inspectorDrawer = buildInspectorDrawer();
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int drawerWidth = screenWidthDp < 540
                ? FrameLayout.LayoutParams.MATCH_PARENT
                : dp(330);
        FrameLayout.LayoutParams inspLp = new FrameLayout.LayoutParams(
                drawerWidth, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        workspace.addView(inspectorDrawer, inspLp);

        pixelArtPanel = buildPixelArtPanel();
        workspace.addView(pixelArtPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        scriptPanel = buildScriptEditorPanel();
        workspace.addView(scriptPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        flowPanel = buildFlowPanel();
        workspace.addView(flowPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        lrmPanel = buildLrmSyncPanel();
        workspace.addView(lrmPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        sfxPanel = buildSfxLabPanel();
        workspace.addView(sfxPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        buildPanel = buildApkExportPanel();
        workspace.addView(buildPanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        root.addView(workspace, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        viewportSurface.loadProjectForEdit(project);
        if (!project.entities.isEmpty()) {
            viewportSurface.renderer.setSelectedEntityId(project.entities.get(0).id);
        }

        String initTab = getIntent().getStringExtra(EXTRA_INITIAL_TAB);
        switchMode(initTab != null ? initTab : "SCENE");
    }

    private View buildTopBar() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#111827"));

        // Row 1: Back, Single-line Title, 2D/3D Toggle, Save
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setPadding(dp(8), dp(7), dp(8), dp(5));

        TextView backBtn = makeChip("← HUB", "#1E293B", "#475569");
        backBtn.setOnClickListener(v -> { saveProjectQuiet(); finish(); });
        row1.addView(backBtn);

        TextView titleTv = new TextView(this);
        titleTv.setText(project.title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTextSize(14.5f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setPadding(dp(10), 0, dp(8), 0);
        row1.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        dimToggleBtn = makeChip(project.dimensionMode, "#1E1B4B", "#818CF8");
        dimToggleBtn.setOnClickListener(v -> {
            pushUndoSnapshot();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimToggleBtn.setText(project.dimensionMode);
            viewportSurface.renderer.resetEditorCamera();
            rebuildSceneToolbarButtons();
            saveProjectQuiet();
        });
        row1.addView(dimToggleBtn, rightMarginLp(4));

        TextView undoBtn = makeChip("↶", "#1E293B", "#475569");
        undoBtn.setOnClickListener(v -> performUndo());
        row1.addView(undoBtn, rightMarginLp(4));

        TextView redoBtn = makeChip("↷", "#1E293B", "#475569");
        redoBtn.setOnClickListener(v -> performRedo());
        row1.addView(redoBtn, rightMarginLp(4));

        TextView saveBtn = makeChip("💾", "#064E3B", "#10B981");
        saveBtn.setOnClickListener(v -> {
            syncCodeFromEditor();
            ProjectRepository.saveProject(this, project);
            Toast.makeText(this, "Saved '" + project.title + "'", Toast.LENGTH_SHORT).show();
        });
        row1.addView(saveBtn);
        container.addView(row1);

        // Row 2: Full-width Scrollable Mode Tabs
        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.parseColor("#0D1422"));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(5), dp(8), dp(6));

        String[][] modes = {
                {"SCENE", "🌐 SCENE"},
                {"INSPECTOR", "🎛 INSPECT"},
                {"FLOW", "⚡ FLOW"},
                {"SCRIPT", "📜 SCRIPT"},
                {"PIXEL", "🎨 PIXEL"},
                {"SFX", "🎵 MUSIC/SFX"},
                {"LRM", "🔄 LRM P2P"},
                {"PLAY", "▶ PLAY"},
                {"BUILD", "📦 EXPORT"}
        };
        for (String[] m : modes) {
            TextView tb = makeChip(m[1], "#162032", "#334155");
            tb.setTag(m[0]);
            tb.setOnClickListener(v -> switchMode(m[0]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            tabs.addView(tb, lp);
            tabButtons.add(tb);
        }
        tabScroll.addView(tabs);
        container.addView(tabScroll);
        return container;
    }

    private LinearLayout buildSceneBottomToolbar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setBackgroundColor(Color.argb(225, 15, 23, 42));
        wrap.setPadding(dp(8), dp(8), dp(8), dp(8));
        rebuildSceneToolbarInto(wrap);
        return wrap;
    }

    private void rebuildSceneToolbarButtons() {
        if (sceneBottomToolbar != null) {
            sceneBottomToolbar.removeAllViews();
            rebuildSceneToolbarInto(sceneBottomToolbar);
        }
    }

    private void rebuildSceneToolbarInto(LinearLayout wrap) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        if (is3D) {
            addToolBtn(row, "+ CUBE", () -> spawnPrimitive("CUBE", "#00E5FF"));
            addToolBtn(row, "+ SPHERE", () -> spawnPrimitive("SPHERE", "#FFD740"));
            addToolBtn(row, "+ PYRAMID", () -> spawnPrimitive("PYRAMID", "#FF4081"));
            addToolBtn(row, "+ CYLINDER", () -> spawnPrimitive("CYLINDER", "#7C4DFF"));
            addToolBtn(row, "⛰ + 3D TERRAIN", this::spawnOrRandomizeTerrain3D);
            addToolBtn(row, "+ .OBJ", () -> spawnPrimitive("CUSTOM_OBJ", "#00E676"));
        } else {
            addToolBtn(row, "+ PLATFORM", () -> spawnPrimitive("RECT", "#2E4F6E"));
            addToolBtn(row, "+ HERO", () -> spawnPrimitive("SPRITE_HERO", "#00E5FF"));
            addToolBtn(row, "+ PIXEL SPRITE", () -> spawnPrimitive("CUSTOM_SPRITE", "#FFFFFF"));
            addToolBtn(row, "+ CRATE (BOX2D)", this::spawnBox2DCrate);
            addToolBtn(row, "+ COIN", () -> spawnPrimitive("SPRITE_COIN", "#FFD740"));
            addToolBtn(row, "+ SLIME", () -> spawnPrimitive("SPRITE_SLIME", "#FF5252"));
        }

        TextView snapBtn = makeChip(
                viewportSurface != null && viewportSurface.isGridSnap() ? "🧲 SNAP: ON" : "🧲 SNAP: OFF",
                "#1E293B", "#38BDF8");
        snapBtn.setOnClickListener(v -> {
            boolean next = !viewportSurface.isGridSnap();
            viewportSurface.setGridSnap(next);
            snapBtn.setText(next ? "🧲 SNAP: ON" : "🧲 SNAP: OFF");
        });
        row.addView(snapBtn, rightMarginLp(6));

        addToolBtn(row, "⎘ CLONE", this::duplicateSelected);
        addToolBtn(row, "🗑 DEL", this::deleteSelected);
        addToolBtn(row, "⌖ CAM", () -> viewportSurface.renderer.resetEditorCamera());
        addToolBtn(row, "📋 ENTITIES", this::showEntityPickerDialog);

        hsv.addView(row);
        wrap.addView(hsv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addToolBtn(LinearLayout row, String label, Runnable action) {
        TextView btn = makeChip(label, "#1E293B", "#38BDF8");
        btn.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        row.addView(btn, lp);
    }

    private void pushUndoSnapshot() {
        if (project == null) return;
        syncCodeFromEditor();
        undoStack.push(project.toJsonString());
        while (undoStack.size() > 40) undoStack.removeLast();
        redoStack.clear();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            syncCodeFromEditor();
            redoStack.push(project.toJsonString());
            project = GameProject.fromJsonString(undoStack.pop());
            applyRestoredProject("↶ Undid last change");
        } catch (Exception ignored) {
        }
    }

    private void performRedo() {
        if (redoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to redo", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            syncCodeFromEditor();
            undoStack.push(project.toJsonString());
            project = GameProject.fromJsonString(redoStack.pop());
            applyRestoredProject("↷ Redid change");
        } catch (Exception ignored) {
        }
    }

    private void applyRestoredProject(String msg) {
        dimToggleBtn.setText(project.dimensionMode);
        if (scriptEditor != null) scriptEditor.setText(project.scriptCode);
        if (langToggleBtn != null) langToggleBtn.setText("LANG: " + project.scriptLanguage);
        viewportSurface.loadProjectForEdit(project);
        rebuildSceneToolbarButtons();
        if ("INSPECTOR".equals(currentMode)) populateInspector();
        if ("FLOW".equals(currentMode)) refreshFlowRuleList();
        saveProjectQuiet();
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void spawnPrimitive(String prim, String color) {
        pushUndoSnapshot();
        String name = prim.toLowerCase(Locale.US) + "_" + (project.entities.size() + 1);
        Entity e;
        if ("3D".equalsIgnoreCase(project.dimensionMode)) {
            e = Entity.create3D(name, "prop", prim, 0f, 1.2f, 0f, 1.2f, 1.2f, 1.2f, color, "STATIC");
            if ("CUSTOM_OBJ".equals(prim) && project.customObjMeshes.isEmpty()) {
                project.customObjMeshes.put("diamond",
                        "v 0 1 0\nv -0.7 0 0.7\nv 0.7 0 0.7\nv 0.7 0 -0.7\nv -0.7 0 -0.7\nv 0 -1 0\n" +
                        "f 1 2 3\nf 1 3 4\nf 1 4 5\nf 1 5 2\nf 6 3 2\nf 6 4 3\nf 6 5 4\nf 6 2 5\n");
                e.customObjName = "diamond";
            }
        } else {
            e = Entity.create2D(name, "prop", prim, 0f, 1.0f, 1.4f, 1.4f, color, "STATIC");
            if ("CUSTOM_SPRITE".equals(prim)) {
                if (project.customSprites.isEmpty()) {
                    project.customSprites.put("knight", PixelSpriteCodec.createDefaultKnightSprite());
                }
                e.customSpriteName = project.customSprites.keySet().iterator().next();
            }
        }
        synchronized (viewportSurface.renderer) {
            project.entities.add(e);
            viewportSurface.renderer.setSelectedEntityId(e.id);
        }
        saveProjectQuiet();
        Toast.makeText(this, "Spawned " + e.name, Toast.LENGTH_SHORT).show();
    }

    private void spawnOrRandomizeTerrain3D() {
        pushUndoSnapshot();
        Entity existing = null;
        for (Entity e : project.entities) {
            if ("TERRAIN".equalsIgnoreCase(e.primitive)) { existing = e; break; }
        }
        project.terrainSeed = (int) (System.currentTimeMillis() % 90000) + 1000;
        if (existing == null) {
            Entity t = Entity.create3D("WorldTerrain", "solid", "TERRAIN", 0f, 0f, 0f, 65f, 9f, 65f, "#2E7D32", "STATIC");
            t.checkerTexture = true;
            t.secondaryColorHex = "#1B5E20";
            synchronized (viewportSurface.renderer) {
                project.entities.add(0, t);
                viewportSurface.renderer.setSelectedEntityId(t.id);
                viewportSurface.renderer.invalidateSpriteCache();
            }
            Toast.makeText(this, "Generated FastNoiseLite 3D Terrain (Seed " + project.terrainSeed + ")!", Toast.LENGTH_SHORT).show();
        } else {
            synchronized (viewportSurface.renderer) {
                viewportSurface.renderer.invalidateSpriteCache();
            }
            Toast.makeText(this, "New FastNoiseLite Seed: " + project.terrainSeed, Toast.LENGTH_SHORT).show();
        }
        saveProjectQuiet();
    }

    private void spawnBox2DCrate() {
        pushUndoSnapshot();
        if (!project.customSprites.containsKey("crate")) {
            project.customSprites.put("crate", PixelSpriteCodec.createCrateSprite());
        }
        Entity crate = Entity.create2D("Crate_" + (project.entities.size() + 1),
                "solid", "CUSTOM_SPRITE", 0f, 3.0f, 1.3f, 1.3f, "#FFB300", "DYNAMIC");
        crate.customSpriteName = "crate";
        crate.fixedRotation = false;
        crate.bounce = 0.25f;
        synchronized (viewportSurface.renderer) {
            project.entities.add(crate);
            viewportSurface.renderer.setSelectedEntityId(crate.id);
        }
        saveProjectQuiet();
        Toast.makeText(this, "Spawned Dynamic JBox2D Crate!", Toast.LENGTH_SHORT).show();
    }

    private void duplicateSelected() {
        pushUndoSnapshot();
        String selId = viewportSurface.renderer.getSelectedEntityId();
        Entity src = project.findById(selId);
        if (src == null) return;
        Entity copy = src.deepCopy();
        copy.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        copy.name = src.name + "_copy";
        copy.x += 1.5f;
        synchronized (viewportSurface.renderer) {
            project.entities.add(copy);
            viewportSurface.renderer.setSelectedEntityId(copy.id);
        }
        saveProjectQuiet();
    }

    private void deleteSelected() {
        String selId = viewportSurface.renderer.getSelectedEntityId();
        Entity src = project.findById(selId);
        if (src == null) return;
        synchronized (viewportSurface.renderer) {
            project.entities.remove(src);
            viewportSurface.renderer.setSelectedEntityId(
                    project.entities.isEmpty() ? null : project.entities.get(0).id);
        }
        if ("INSPECTOR".equals(currentMode)) populateInspector();
        saveProjectQuiet();
    }

    private void showEntityPickerDialog() {
        String[] names = new String[project.entities.size()];
        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            names[i] = e.name + "  [" + e.primitive + " • " + e.bodyType + "]";
        }
        new AlertDialog.Builder(this)
                .setTitle("Scene Entities (" + names.length + ")")
                .setItems(names, (d, which) -> {
                    Entity chosen = project.entities.get(which);
                    viewportSurface.renderer.setSelectedEntityId(chosen.id);
                    if ("INSPECTOR".equals(currentMode)) populateInspector();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private ScrollView buildInspectorDrawer() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.argb(242, 15, 23, 42));
        inspectorContent = new LinearLayout(this);
        inspectorContent.setOrientation(LinearLayout.VERTICAL);
        inspectorContent.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(inspectorContent);
        return sv;
    }

    private void populateInspector() {
        inspectorContent.removeAllViews();

        TextView head = new TextView(this);
        head.setText("ENTITY & PHYSICS INSPECTOR");
        head.setTextColor(Color.parseColor("#00E5FF"));
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextSize(13f);
        inspectorContent.addView(head);

        if (!project.entities.isEmpty()) {
            String[] entityNames = new String[project.entities.size()];
            int selIndex = 0;
            String curId = viewportSurface.renderer.getSelectedEntityId();
            for (int i = 0; i < project.entities.size(); i++) {
                Entity item = project.entities.get(i);
                entityNames[i] = item.name + " [" + item.primitive + "]";
                if (item.id.equals(curId)) selIndex = i;
            }
            Spinner pickSpin = new Spinner(this);
            ArrayAdapter<String> pickAd = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, entityNames);
            pickSpin.setAdapter(pickAd);
            pickSpin.setSelection(selIndex, false);
            final int initialIdx = selIndex;
            pickSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    if (pos != initialIdx && pos >= 0 && pos < project.entities.size()) {
                        viewportSurface.renderer.setSelectedEntityId(project.entities.get(pos).id);
                        populateInspector();
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            inspectorContent.addView(pickSpin);
        }

        Entity e = project.findById(viewportSurface.renderer.getSelectedEntityId());
        if (e == null) {
            TextView empty = new TextView(this);
            empty.setText("Tap any object in the 2D/3D viewport or use 📋 ENTITIES to inspect.");
            empty.setTextColor(Color.LTGRAY);
            empty.setPadding(0, dp(12), 0, dp(12));
            inspectorContent.addView(empty);
            return;
        }

        EditText nameEt = addLabeledEdit(inspectorContent, "Name", e.name, false);
        EditText tagEt = addLabeledEdit(inspectorContent, "Tag (player/solid/coin/enemy/goal)", e.tag, false);

        String[] prims = "3D".equalsIgnoreCase(project.dimensionMode)
                ? new String[]{"CUBE", "SPHERE", "PYRAMID", "CYLINDER", "PLANE", "TERRAIN", "CUSTOM_OBJ"}
                : new String[]{"RECT", "CIRCLE", "TRIANGLE", "CUSTOM_SPRITE", "SPRITE_HERO", "SPRITE_SLIME", "SPRITE_COIN", "SPRITE_SHIP"};
        Spinner primSpin = addLabeledSpinner(inspectorContent, "Visual / Mesh", prims, e.primitive);

        LinearLayout posRow = new LinearLayout(this);
        posRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText xEt = addCompactNum(posRow, "X", e.x);
        EditText yEt = addCompactNum(posRow, "Y", e.y);
        EditText zEt = addCompactNum(posRow, "Z", e.z);
        inspectorContent.addView(posRow);

        LinearLayout sclRow = new LinearLayout(this);
        sclRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText sxEt = addCompactNum(sclRow, "ScaleX", e.scaleX);
        EditText syEt = addCompactNum(sclRow, "ScaleY", e.scaleY);
        EditText szEt = addCompactNum(sclRow, "ScaleZ", e.scaleZ);
        inspectorContent.addView(sclRow);

        LinearLayout rotRow = new LinearLayout(this);
        rotRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText ryEt = addCompactNum(rotRow, "RotY/Z", "3D".equalsIgnoreCase(project.dimensionMode) ? e.rotY : e.rotZ);
        EditText bncEt = addCompactNum(rotRow, "Bounce", e.bounce);
        EditText frcEt = addCompactNum(rotRow, "Friction", e.friction);
        inspectorContent.addView(rotRow);

        EditText colorEt = addLabeledEdit(inspectorContent, "Color Hex (#RRGGBB)", e.colorHex, false);

        String[] bodies = {"NONE", "STATIC", "DYNAMIC", "KINEMATIC"};
        Spinner bodySpin = addLabeledSpinner(inspectorContent, "Physics Body Type", bodies, e.bodyType);

        CheckBox triggerCb = new CheckBox(this);
        triggerCb.setText("Is Trigger Sensor");
        triggerCb.setTextColor(Color.WHITE);
        triggerCb.setChecked(e.isTrigger);
        inspectorContent.addView(triggerCb);

        CheckBox fixedRotCb = new CheckBox(this);
        fixedRotCb.setText("Lock Rotation (Platformer Hero)");
        fixedRotCb.setTextColor(Color.WHITE);
        fixedRotCb.setChecked(e.fixedRotation);
        inspectorContent.addView(fixedRotCb);

        CheckBox checkerCb = new CheckBox(this);
        checkerCb.setText("Procedural Checker Texture");
        checkerCb.setTextColor(Color.WHITE);
        checkerCb.setChecked(e.checkerTexture);
        inspectorContent.addView(checkerCb);

        CheckBox glowCb = new CheckBox(this);
        glowCb.setText("Neon Emissive Shading");
        glowCb.setTextColor(Color.WHITE);
        glowCb.setChecked(e.emissive);
        inspectorContent.addView(glowCb);

        TextView applyBtn = makeChip("✓ APPLY CHANGES", "#00B8D4", "#00E5FF");
        applyBtn.setTextColor(Color.parseColor("#04131A"));
        applyBtn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        apLp.topMargin = dp(10);
        applyBtn.setOnClickListener(v -> {
            synchronized (viewportSurface.renderer) {
                e.name = nameEt.getText().toString().trim();
                e.tag = tagEt.getText().toString().trim();
                e.primitive = prims[primSpin.getSelectedItemPosition()];
                e.x = parseF(xEt, e.x); e.y = parseF(yEt, e.y); e.z = parseF(zEt, e.z);
                e.scaleX = Math.max(0.1f, parseF(sxEt, e.scaleX));
                e.scaleY = Math.max(0.1f, parseF(syEt, e.scaleY));
                e.scaleZ = Math.max(0.1f, parseF(szEt, e.scaleZ));
                if ("3D".equalsIgnoreCase(project.dimensionMode)) e.rotY = parseF(ryEt, e.rotY);
                else e.rotZ = parseF(ryEt, e.rotZ);
                e.bounce = parseF(bncEt, e.bounce);
                e.friction = parseF(frcEt, e.friction);
                e.colorHex = colorEt.getText().toString().trim();
                e.bodyType = bodies[bodySpin.getSelectedItemPosition()];
                e.isTrigger = triggerCb.isChecked();
                e.fixedRotation = fixedRotCb.isChecked();
                e.checkerTexture = checkerCb.isChecked();
                e.emissive = glowCb.isChecked();
            }
            saveProjectQuiet();
            Toast.makeText(this, "Updated " + e.name, Toast.LENGTH_SHORT).show();
        });
        inspectorContent.addView(applyBtn, apLp);

        // World Settings
        TextView worldHead = new TextView(this);
        worldHead.setText("WORLD & ENGINE SETTINGS");
        worldHead.setTextColor(Color.parseColor("#B388FF"));
        worldHead.setTypeface(Typeface.DEFAULT_BOLD);
        worldHead.setPadding(0, dp(16), 0, dp(4));
        inspectorContent.addView(worldHead);

        EditText skyEt = addLabeledEdit(inspectorContent, "Sky Hex", project.skyColorHex, false);
        EditText gravEt = addLabeledEdit(inspectorContent, "Gravity Y", String.valueOf(project.gravityY), true);
        CheckBox box2dCb = new CheckBox(this);
        box2dCb.setText("Enable JBox2D Rigid-Body Solver (2D)");
        box2dCb.setTextColor(Color.WHITE);
        box2dCb.setChecked(project.useBox2D);
        inspectorContent.addView(box2dCb);

        TextView saveWorldBtn = makeChip("✓ APPLY WORLD", "#311B92", "#B388FF");
        saveWorldBtn.setGravity(Gravity.CENTER);
        saveWorldBtn.setOnClickListener(v -> {
            project.skyColorHex = skyEt.getText().toString().trim();
            project.gravityY = parseF(gravEt, project.gravityY);
            project.useBox2D = box2dCb.isChecked();
            saveProjectQuiet();
            Toast.makeText(this, "World settings saved!", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        swLp.topMargin = dp(8);
        inspectorContent.addView(saveWorldBtn, swLp);
    }

    // =========================================================================
    // 16x16 Touchscreen Pixel Art Sprite Editor Panel
    // =========================================================================
    private LinearLayout buildPixelArtPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#0B0F17"));
        panel.setPadding(dp(12), dp(8), dp(12), dp(10));

        // Top Bar: Sprite Name + Mirror-X + Presets + Save & Spawn
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        spriteNameEdit = new EditText(this);
        spriteNameEdit.setText("hero_sprite");
        spriteNameEdit.setTextColor(Color.WHITE);
        spriteNameEdit.setTextSize(13f);
        spriteNameEdit.setSingleLine(true);
        top.addView(spriteNameEdit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView mirrorBtn = makeChip("⇄ MIRROR: OFF", "#1E293B", "#38BDF8");
        mirrorBtn.setOnClickListener(v -> {
            boolean next = !pixelArtCanvas.isMirrorX();
            pixelArtCanvas.setMirrorX(next);
            mirrorBtn.setText(next ? "⇄ MIRROR: ON" : "⇄ MIRROR: OFF");
        });
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLp.leftMargin = dp(6);
        top.addView(mirrorBtn, mLp);

        TextView clearBtn = makeChip("CLEAR", "#2D161E", "#FF5252");
        clearBtn.setOnClickListener(v -> pixelArtCanvas.clearCanvas());
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cLp.leftMargin = dp(6);
        top.addView(clearBtn, cLp);

        panel.addView(top);

        // Color Palette Strip
        HorizontalScrollView palScroll = new HorizontalScrollView(this);
        palScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout palRow = new LinearLayout(this);
        palRow.setOrientation(LinearLayout.HORIZONTAL);
        palRow.setPadding(0, dp(6), 0, dp(6));

        int[] colors = {
                0xFF00E5FF, 0xFF00E676, 0xFFFFD740, 0xFFFF5252,
                0xFFFF4081, 0xFF7C4DFF, 0xFFFF9100, 0xFF8D6E63,
                0xFFFFFFFF, 0xFF94A3B8, 0xFF334155, 0xFF090D16,
                0x00000000 // Eraser
        };
        for (int col : colors) {
            TextView swatch = new TextView(this);
            swatch.setText(col == 0 ? "✕ ERASE" : "   ");
            swatch.setTextColor(Color.WHITE);
            swatch.setTextSize(10.5f);
            swatch.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable g = new GradientDrawable();
            g.setColor(col == 0 ? Color.parseColor("#1E293B") : col);
            g.setCornerRadius(dp(6));
            g.setStroke(dp(1), Color.WHITE);
            swatch.setBackground(g);
            swatch.setOnClickListener(v -> pixelArtCanvas.setBrushColor(col));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            palRow.addView(swatch, lp);
        }
        palScroll.addView(palRow);
        panel.addView(palScroll);

        // 16x16 Pixel Canvas
        pixelArtCanvas = new PixelArtEditorView(this);
        if (!project.customSprites.isEmpty()) {
            Map.Entry<String, String> first = project.customSprites.entrySet().iterator().next();
            spriteNameEdit.setText(first.getKey());
            pixelArtCanvas.loadEncoded(first.getValue());
        }
        panel.addView(pixelArtCanvas, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom Actions: Presets + Save & Spawn
        LinearLayout bot = new LinearLayout(this);
        bot.setOrientation(LinearLayout.HORIZONTAL);
        bot.setPadding(0, dp(8), 0, 0);

        TextView knightBtn = makeChip("KNIGHT PRESET", "#1E293B", "#64748B");
        knightBtn.setOnClickListener(v -> {
            spriteNameEdit.setText("knight");
            pixelArtCanvas.loadEncoded(PixelSpriteCodec.createDefaultKnightSprite());
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        b1.rightMargin = dp(6);
        bot.addView(knightBtn, b1);

        TextView saveSpawnBtn = makeChip("✓ SAVE & SPAWN IN 2D", "#00C853", "#69F0AE");
        saveSpawnBtn.setTextColor(Color.parseColor("#041A0B"));
        saveSpawnBtn.setGravity(Gravity.CENTER);
        saveSpawnBtn.setOnClickListener(v -> {
            String sName = spriteNameEdit.getText().toString().trim();
            if (sName.isEmpty()) sName = "sprite_1";
            project.customSprites.put(sName, pixelArtCanvas.exportEncoded());
            viewportSurface.renderer.invalidateSpriteCache();
            if (!"2D".equalsIgnoreCase(project.dimensionMode)) {
                project.dimensionMode = "2D";
                dimToggleBtn.setText("2D");
                rebuildSceneToolbarButtons();
            }
            Entity sp = Entity.create2D("Sprite_" + (project.entities.size() + 1),
                    "prop", "CUSTOM_SPRITE", 0f, 1.5f, 1.6f, 1.6f, "#FFFFFF", "DYNAMIC");
            sp.customSpriteName = sName;
            synchronized (viewportSurface.renderer) {
                project.entities.add(sp);
                viewportSurface.renderer.setSelectedEntityId(sp.id);
            }
            saveProjectQuiet();
            Toast.makeText(this, "Saved '" + sName + "' & spawned in 2D scene!", Toast.LENGTH_SHORT).show();
            switchMode("SCENE");
        });
        bot.addView(saveSpawnBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));

        panel.addView(bot);
        return panel;
    }

    // =========================================================================
    // Dual Lua 5.2 (LuaJ) & VortexScript Code Editor (Portrait-Fixed!)
    // =========================================================================
    private LinearLayout buildScriptEditorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#0B0F17"));

        // Row 1: Scrollable Action Toolbar
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsScroll.setBackgroundColor(Color.parseColor("#131C2E"));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(10), dp(7), dp(10), dp(7));

        TextView checkBtn = makeChip("✓ CHECK SYNTAX", "#064E3B", "#10B981");
        checkBtn.setOnClickListener(v -> validateCurrentScript());
        toolbar.addView(checkBtn, rightMarginLp(6));

        langToggleBtn = makeChip("LANG: " + project.scriptLanguage, "#10253A", "#00E5FF");
        langToggleBtn.setOnClickListener(v -> toggleScriptLanguage());
        toolbar.addView(langToggleBtn, rightMarginLp(6));

        TextView forgeBtn = makeChip("🤖 FORGE AI", "#261B38", "#E040FB");
        forgeBtn.setOnClickListener(v -> showForgeAiDialog());
        toolbar.addView(forgeBtn, rightMarginLp(6));

        TextView objBtn = makeChip("🧊 .OBJ MESHES", "#1E1B4B", "#818CF8");
        objBtn.setOnClickListener(v -> showCustomObjEditorDialog());
        toolbar.addView(objBtn, rightMarginLp(6));

        TextView helpBtn = makeChip("📖 API DOCS", "#1E293B", "#38BDF8");
        helpBtn.setOnClickListener(v -> showApiDocsDialog());
        toolbar.addView(helpBtn);

        toolsScroll.addView(toolbar);
        panel.addView(toolsScroll);

        // Row 2: Dedicated Full-Width Syntax Status Bar (Fixes Screenshot #1!)
        scriptStatusTv = new TextView(this);
        scriptStatusTv.setText("✓ Syntax Ready (" + describeVm(project.scriptLanguage) + ")");
        scriptStatusTv.setTextColor(Color.parseColor("#00E676"));
        scriptStatusTv.setBackgroundColor(Color.parseColor("#0D1524"));
        scriptStatusTv.setTextSize(12f);
        scriptStatusTv.setPadding(dp(12), dp(6), dp(12), dp(6));
        panel.addView(scriptStatusTv);

        // Row 3: Dynamic Quick-Insert Snippet Bar
        HorizontalScrollView snipScroll = new HorizontalScrollView(this);
        snipScroll.setHorizontalScrollBarEnabled(false);
        snipScroll.setBackgroundColor(Color.parseColor("#0F172A"));
        snippetBar = new LinearLayout(this);
        snippetBar.setOrientation(LinearLayout.HORIZONTAL);
        snippetBar.setPadding(dp(8), dp(5), dp(8), dp(5));
        rebuildSnippetBar();
        snipScroll.addView(snippetBar);
        panel.addView(snipScroll);

        // Row 4: Full-Screen Monospace Code Editor
        scriptEditor = new EditText(this);
        scriptEditor.setText(project.scriptCode);
        scriptEditor.setTypeface(Typeface.MONOSPACE);
        scriptEditor.setTextColor(Color.parseColor("#E2E8F0"));
        scriptEditor.setTextSize(13.5f);
        scriptEditor.setGravity(Gravity.TOP | Gravity.START);
        scriptEditor.setBackgroundColor(Color.parseColor("#090D16"));
        scriptEditor.setPadding(dp(14), dp(12), dp(14), dp(24));
        scriptEditor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

        ScrollView codeScroll = new ScrollView(this);
        codeScroll.setFillViewport(true);
        codeScroll.addView(scriptEditor);
        panel.addView(codeScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return panel;
    }

    private String describeVm(String lang) {
        if ("JS".equalsIgnoreCase(lang)) return "ES6+ JavaScript • Mozilla Rhino VM";
        if ("LUA".equalsIgnoreCase(lang)) return "Lua 5.2 • LuaJ VM";
        return "VortexScript (.vxs) VM";
    }

    private void rebuildSnippetBar() {
        if (snippetBar == null) return;
        snippetBar.removeAllViews();
        boolean isJs = "JS".equalsIgnoreCase(project.scriptLanguage);
        boolean isLua = "LUA".equalsIgnoreCase(project.scriptLanguage);
        String[] snippets = isJs
                ? new String[]{
                        "const t = vortex.findNearestTag('Player', 'enemy');",
                        "vortex.chaseTagAI('enemy', 'Player', 4.5);",
                        "vortex.setSpriteAnim('Player', 'knight_1,knight_2', 7);",
                        "vortex.tweenTagYoyo('coin', 0.35, 0.9, 'SINE');",
                        "vortex.saveData('best', vortex.getScore());",
                        "vortex.playMusic('CYBERPUNK', 136);",
                        "vortex.particles(0, 1, 0, 22, '#00E5FF');",
                        "class WaveDirector {\n  constructor() {}\n}"}
                : isLua
                ? new String[]{
                        "vortex.joyX()", "vortex.chaseTagAI('enemy', 'Player', 4.5)",
                        "vortex.findNearestTag('Player', 'enemy')",
                        "vortex.tweenTagYoyo('coin', 0.4, 0.9, 'SINE')",
                        "vortex.playMusic('CYBERPUNK', 132)",
                        "vortex.raycast(x, y, z, 0, 0, -1, 35)",
                        "vortex.jump('Player', 12)", "vortex.particles(0, 1, 0, 20, '#00E5FF')"}
                : new String[]{
                        "joyX()", "jump('Player', 12.0);", "setVel('Player', 0, 0, 0);",
                        "particles(0, 1, 0, 20, '#00E5FF');", "playSound('coin');",
                        "addScore(100);", "fn onCollide(a, b, tagA, tagB) {\n}"};
        for (String s : snippets) {
            TextView sb = makeChip(s.replace("\n", " "), "#1E293B", "#475569");
            sb.setTextSize(11f);
            sb.setOnClickListener(v -> {
                int start = scriptEditor.getSelectionStart();
                if (!scriptEditor.hasFocus() || start <= 0) {
                    scriptEditor.append("\n" + s);
                } else {
                    scriptEditor.getText().insert(start, s);
                }
            });
            snippetBar.addView(sb, rightMarginLp(6));
        }
    }

    private void toggleScriptLanguage() {
        syncCodeFromEditor();
        if ("JS".equalsIgnoreCase(project.scriptLanguage)) project.scriptLanguage = "LUA";
        else if ("LUA".equalsIgnoreCase(project.scriptLanguage)) project.scriptLanguage = "VXS";
        else project.scriptLanguage = "JS";
        langToggleBtn.setText("LANG: " + project.scriptLanguage);
        rebuildSnippetBar();
        scriptStatusTv.setText("Switched VM to " + describeVm(project.scriptLanguage) + " — tap CHECK SYNTAX to verify.");
        scriptStatusTv.setTextColor(Color.parseColor("#38BDF8"));
        saveProjectQuiet();
    }

    private boolean validateCurrentScript() {
        syncCodeFromEditor();
        String check = VortexScriptEngine.validateSyntax(project.scriptLanguage, project.scriptCode);
        if (check.startsWith("OK")) {
            scriptStatusTv.setText("✓ " + check);
            scriptStatusTv.setTextColor(Color.parseColor("#00E676"));
            return true;
        } else {
            scriptStatusTv.setText("✕ " + check);
            scriptStatusTv.setTextColor(Color.parseColor("#FF5252"));
            return false;
        }
    }

    private void showForgeAiDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(10), dp(16), dp(6));

        TextView info = new TextView(this);
        info.setText("Inspired by hacvilke/roforge: local-first BYOK + instant Offline Generator! Automatically creates an Undo checkpoint before editing your project.");
        info.setTextColor(Color.parseColor("#94A3B8"));
        info.setTextSize(11.5f);
        form.addView(info);

        Spinner provSpin = addLabeledSpinner(form, "AI Engine Mode", VortexForgeAgent.PROVIDERS, VortexForgeAgent.PROVIDERS[0]);
        EditText keyEt = addLabeledEdit(form, "Optional API Key (leave blank for Offline Generator)", "", false);
        EditText promptEt = addLabeledEdit(form, "What should Forge AI build?", "2D cyber swarm arena with animated drones and auto-aim blaster", false);

        new AlertDialog.Builder(this)
                .setTitle("🤖 Vortex Forge AI Agent")
                .setView(form)
                .setPositiveButton("⚡ Generate (+ Undo Checkpoint)", (d, w) -> {
                    pushUndoSnapshot();
                    final int pIdx = provSpin.getSelectedItemPosition();
                    final String apiKey = keyEt.getText().toString().trim();
                    final String prompt = promptEt.getText().toString().trim();
                    new Thread(() -> {
                        try {
                            final String summary = VortexForgeAgent.applyPromptToProject(project, prompt, pIdx, apiKey);
                            runOnUiThread(() -> {
                                applyRestoredProject("🤖 " + summary);
                            });
                        } catch (Exception ex) {
                            runOnUiThread(() -> Toast.makeText(EditorActivity.this, "Forge AI error: " + ex.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomObjEditorDialog() {
        EditText objEt = new EditText(this);
        objEt.setTypeface(Typeface.MONOSPACE);
        objEt.setTextSize(12f);
        objEt.setMinLines(8);
        String existing = project.customObjMeshes.isEmpty()
                ? "# Wavefront .obj vertices & faces\nv 0 1 0\nv -1 -1 1\nv 1 -1 1\nv 0 -1 -1\nf 1 2 3\nf 1 3 4\nf 1 4 2\nf 2 4 3\n"
                : project.customObjMeshes.values().iterator().next();
        objEt.setText(existing);

        new AlertDialog.Builder(this)
                .setTitle("Custom 3D .OBJ Mesh Asset")
                .setView(objEt)
                .setPositiveButton("Save Mesh", (d, w) -> {
                    String key = project.customObjMeshes.isEmpty() ? "custom" : project.customObjMeshes.keySet().iterator().next();
                    project.customObjMeshes.put(key, objEt.getText().toString());
                    viewportSurface.loadProjectForEdit(project);
                    saveProjectQuiet();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showApiDocsDialog() {
        String docs =
                "=== LUA 5.2 (LuaJ) API (`vortex.*`) ===\n" +
                "Callbacks:\n" +
                "  function onStart() end\n" +
                "  function onUpdate(dt) end\n" +
                "  function onButton(btn) end -- 'A' or 'B'\n" +
                "  function onCollide(a, b, tagA, tagB) end\n\n" +
                "Controls & Physics:\n" +
                "  vortex.joyX(), vortex.joyY(), vortex.btnA(), vortex.btnB()\n" +
                "  vortex.getX(n), vortex.getY(n), vortex.getZ(n)\n" +
                "  vortex.setPos(n, x, y [, z])\n" +
                "  vortex.setVel(n, vx, vy [, vz])\n" +
                "  vortex.applyImpulse(n, ix, iy)  -- JBox2D!\n" +
                "  vortex.jump(n, impulse)\n" +
                "  vortex.noise2D(x, z)            -- FastNoiseLite!\n" +
                "  vortex.terrainHeight(x, z)      -- 3D Height!\n" +
                "  vortex.spawn2D(name, tag, prim, x, y, w, h, hex, body)\n" +
                "  vortex.spawn3D(name, tag, prim, x, y, z, sx, sy, sz, hex, body)\n" +
                "  vortex.particles(x, y, z, count, hex)\n" +
                "  vortex.playSound('jump'|'coin'|'laser'|'explosion'|'hit'|'powerup')\n\n" +
                "=== v3.0 AI, TWEENS & CHIPTUNE ===\n" +
                "  vortex.chaseAI(seeker, target, speed)   -- Theta* A*!\n" +
                "  vortex.chaseTagAI('enemy', 'Player', 4.5)\n" +
                "  vortex.raycast(ox,oy,oz, dx,dy,dz, maxDist)\n" +
                "  vortex.tweenPos(n, tx,ty,tz, dur, 'BOUNCE', -1)\n" +
                "  vortex.tweenScale(n, sx,sy,sz, dur, 'ELASTIC', 1)\n" +
                "  vortex.tweenTagYoyo('coin', 0.4, 0.9, 'SINE')\n" +
                "  vortex.playMusic('CYBERPUNK'|'DUNGEON'|'BOSS'|'SKYCHILL', bpm)\n\n" +
                "=== VORTEXSCRIPT (.vxs) ===\n" +
                "Same functions called directly: joyX(), setVel(...), jump(...), playSound(...);";
        new AlertDialog.Builder(this)
                .setTitle("Lua 5.2 & VortexScript Reference")
                .setMessage(docs)
                .setPositiveButton("Got It", null)
                .show();
    }

    // =========================================================================
    // ⚡ VORTEX FLOW: Visual No-Code Event-Sheet Builder (Compiles 1:1 to Lua 5.2)
    // =========================================================================
    private ScrollView buildFlowPanel() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0B0F17"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(28));

        TextView head = new TextView(this);
        head.setText("⚡ VORTEX FLOW — NO-CODE VISUAL LOGIC");
        head.setTextColor(Color.parseColor("#00E5FF"));
        head.setTextSize(15.5f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView desc = new TextView(this);
        desc.setText("Build complete 2D & 3D game rules without writing code (like Construct / GDevelop), then compile 1-to-1 into clean Lua 5.2 anytime!");
        desc.setTextColor(Color.parseColor("#94A3B8"));
        desc.setTextSize(11.5f);
        desc.setPadding(0, dp(3), 0, dp(10));
        box.addView(desc);

        // Dual-Target Compile Row: ES6 JavaScript (Rhino/Web) OR Lua 5.2!
        LinearLayout compileRow = new LinearLayout(this);
        compileRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView compileJsBtn = makeChip("⚡ COMPILE ➔ ES6 JS", "#26200B", "#FACC15");
        compileJsBtn.setGravity(Gravity.CENTER);
        compileJsBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        compileJsBtn.setOnClickListener(v -> {
            if (project.flowRules.isEmpty()) {
                Toast.makeText(this, "Add at least 1 Flow rule below first!", Toast.LENGTH_SHORT).show();
                return;
            }
            pushUndoSnapshot();
            String js = VortexFlowCompiler.compileToJavaScript(project);
            project.scriptLanguage = "JS";
            project.scriptCode = js;
            if (scriptEditor != null) scriptEditor.setText(js);
            if (langToggleBtn != null) langToggleBtn.setText("LANG: JS");
            rebuildSnippetBar();
            saveProjectQuiet();
            Toast.makeText(this, "✓ Compiled " + project.flowRules.size() + " Flow rules into ES6 JavaScript!", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams cjLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cjLp.rightMargin = dp(6);
        compileRow.addView(compileJsBtn, cjLp);

        TextView compileBtn = makeChip("⚡ COMPILE ➔ LUA 5.2", "#064E3B", "#00E676");
        compileBtn.setGravity(Gravity.CENTER);
        compileBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        compileBtn.setOnClickListener(v -> {
            if (project.flowRules.isEmpty()) {
                Toast.makeText(this, "Add at least 1 Flow rule below first!", Toast.LENGTH_SHORT).show();
                return;
            }
            pushUndoSnapshot();
            String lua = VortexFlowCompiler.compileToLua(project);
            project.scriptLanguage = "LUA";
            project.scriptCode = lua;
            if (scriptEditor != null) scriptEditor.setText(lua);
            if (langToggleBtn != null) langToggleBtn.setText("LANG: LUA");
            rebuildSnippetBar();
            saveProjectQuiet();
            Toast.makeText(this, "✓ Compiled " + project.flowRules.size() + " Flow rules into Lua 5.2!", Toast.LENGTH_LONG).show();
        });
        compileRow.addView(compileBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(compileRow);

        // Add New Rule Card
        LinearLayout builderCard = new LinearLayout(this);
        builderCard.setOrientation(LinearLayout.VERTICAL);
        builderCard.setPadding(dp(12), dp(10), dp(12), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.parseColor("#111A2C"));
        cardBg.setCornerRadius(dp(10));
        cardBg.setStroke(dp(1), Color.parseColor("#243656"));
        builderCard.setBackground(cardBg);
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bcLp.topMargin = dp(10);
        bcLp.bottomMargin = dp(12);

        TextView addTitle = new TextView(this);
        addTitle.setText("➕ CREATE EVENT ➔ ACTION RULE");
        addTitle.setTextColor(Color.parseColor("#FFD54F"));
        addTitle.setTextSize(12.5f);
        addTitle.setTypeface(Typeface.DEFAULT_BOLD);
        builderCard.addView(addTitle);

        Spinner evSpin = addLabeledSpinner(builderCard, "WHEN Event Happens:",
                VortexFlowCompiler.EVENT_LABELS, VortexFlowCompiler.EVENT_LABELS[0]);

        LinearLayout evRow = new LinearLayout(this);
        evRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText evP1 = addLabeledEditCompact(evRow, "Tag A / Seconds (e.g. player)", "player");
        EditText evP2 = addLabeledEditCompact(evRow, "Tag B (e.g. coin / enemy)", "coin");
        builderCard.addView(evRow);

        Spinner actSpin = addLabeledSpinner(builderCard, "DO Game Action:",
                VortexFlowCompiler.ACTION_LABELS, VortexFlowCompiler.ACTION_LABELS[0]);

        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText actP1 = addLabeledEditCompact(actRow, "Target / Tag / Score / Preset", "Player");
        EditText actP2 = addLabeledEditCompact(actRow, "Speed / Force / SFX / BPM", "10");
        builderCard.addView(actRow);

        TextView addRuleBtn = makeChip("＋ ADD RULE TO EVENT SHEET", "#16263D", "#00E5FF");
        addRuleBtn.setGravity(Gravity.CENTER);
        addRuleBtn.setPadding(dp(10), dp(10), dp(10), dp(10));
        addRuleBtn.setOnClickListener(v -> {
            pushUndoSnapshot();
            String evKey = VortexFlowCompiler.EVENT_KEYS[evSpin.getSelectedItemPosition()];
            String actKey = VortexFlowCompiler.ACTION_KEYS[actSpin.getSelectedItemPosition()];
            project.flowRules.add(new VortexFlowCompiler.FlowRule(
                    evKey, evP1.getText().toString().trim(), evP2.getText().toString().trim(),
                    actKey, actP1.getText().toString().trim(), actP2.getText().toString().trim()));
            refreshFlowRuleList();
            saveProjectQuiet();
            Toast.makeText(this, "Added Flow rule!", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams arLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        arLp.topMargin = dp(8);
        builderCard.addView(addRuleBtn, arLp);
        box.addView(builderCard, bcLp);

        TextView listHead = new TextView(this);
        listHead.setText("ACTIVE EVENT SHEET RULES:");
        listHead.setTextColor(Color.parseColor("#CBD5E1"));
        listHead.setTextSize(12f);
        listHead.setTypeface(Typeface.DEFAULT_BOLD);
        listHead.setPadding(0, 0, 0, dp(6));
        box.addView(listHead);

        flowRuleListContainer = new LinearLayout(this);
        flowRuleListContainer.setOrientation(LinearLayout.VERTICAL);
        box.addView(flowRuleListContainer);
        refreshFlowRuleList();

        sv.addView(box);
        return sv;
    }

    private EditText addLabeledEditCompact(LinearLayout row, String label, String val) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4), dp(6), 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(10.5f);
        col.addView(tv);
        EditText et = new EditText(this);
        et.setText(val);
        et.setTextColor(Color.WHITE);
        et.setTextSize(12.5f);
        et.setSingleLine(true);
        col.addView(et);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return et;
    }

    private void refreshFlowRuleList() {
        if (flowRuleListContainer == null) return;
        flowRuleListContainer.removeAllViews();
        if (project.flowRules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No visual rules yet — add rules above or open 'Cyber Hunters AI Maze' to inspect a complete Flow sheet!");
            empty.setTextColor(Color.parseColor("#64748B"));
            empty.setTextSize(12f);
            flowRuleListContainer.addView(empty);
            return;
        }
        for (int i = 0; i < project.flowRules.size(); i++) {
            final int idx = i;
            VortexFlowCompiler.FlowRule r = project.flowRules.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor("#131C2E"));
            gd.setCornerRadius(dp(8));
            gd.setStroke(dp(1), Color.parseColor("#2A3B59"));
            card.setBackground(gd);

            TextView tv = new TextView(this);
            tv.setText((idx + 1) + ". " + r.describe());
            tv.setTextColor(Color.parseColor("#E2E8F0"));
            tv.setTextSize(12f);
            card.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = makeChip("✕", "#2D161E", "#FF5252");
            del.setPadding(dp(8), dp(4), dp(8), dp(4));
            del.setOnClickListener(v -> {
                pushUndoSnapshot();
                project.flowRules.remove(idx);
                refreshFlowRuleList();
                saveProjectQuiet();
            });
            card.addView(del);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            flowRuleListContainer.addView(card, lp);
        }
    }

    // =========================================================================
    // 🔄 LRM P2P SYNC & VERSION CONTROL (hacvilke/lrm & hacvilke/lrm-mobile)
    // =========================================================================
    private ScrollView buildLrmSyncPanel() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0B0F17"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(12), dp(14), dp(28));

        TextView head = new TextView(this);
        head.setText("🔄 LRM P2P LIVE SYNC & VERSION CONTROL");
        head.setTextColor(Color.parseColor("#00E5FF"));
        head.setTextSize(15.5f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView desc = new TextView(this);
        desc.setText("Powered by hacvilke/lrm & hacvilke/lrm-mobile (PIE e_type:3 for Termux): sync your game peer-to-peer between Android, Windows & Linux with encrypted Port Keys (lrm1_...) and zero servers!");
        desc.setTextColor(Color.parseColor("#94A3B8"));
        desc.setTextSize(11.5f);
        desc.setPadding(0, dp(3), 0, dp(10));
        box.addView(desc);

        EditText commitMsgEt = addLabeledEdit(box, "LRM Commit Message", "Update " + project.title, false);
        EditText portKeyEt = addLabeledEdit(box, "Remote Peer Port Key (paste lrm1_... to Join & Pull)", "", false);

        // Row 1: Unpack + Commit | Share Port Key
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setPadding(0, dp(8), 0, 0);

        TextView commitBtn = makeChip("📤 COMMIT TO LRM", "#064E3B", "#00E676");
        commitBtn.setGravity(Gravity.CENTER);
        commitBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        commitBtn.setOnClickListener(v -> {
            File dir = exportUnpackedLrmRepo();
            if (dir != null) {
                String msg = commitMsgEt.getText().toString().trim().replace("\"", "'");
                if (msg.isEmpty()) msg = "Update game";
                String sub = "( [ -d .lrm ] || lrm init --user mobile ) && lrm commit -m \"" + msg + "\" && lrm log --limit 5 --oneline";
                dispatchOrCopyLrmTermux(dir, sub);
            }
        });
        LinearLayout.LayoutParams c1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        c1.rightMargin = dp(6);
        r1.addView(commitBtn, c1);

        TextView shareBtn = makeChip("🔑 LRM SHARE KEY", "#16263D", "#00E5FF");
        shareBtn.setGravity(Gravity.CENTER);
        shareBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        shareBtn.setOnClickListener(v -> {
            File dir = exportUnpackedLrmRepo();
            if (dir != null) {
                dispatchOrCopyLrmTermux(dir, "( [ -d .lrm ] || (lrm init --user mobile && lrm commit -m 'Initial snapshot') ) && lrm share");
            }
        });
        r1.addView(shareBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(r1);

        // Row 2: Join Port Key | Wi-Fi Asset Drop (lrm receive)
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setPadding(0, dp(8), 0, 0);

        TextView joinBtn = makeChip("🌐 JOIN PORT KEY", "#261B38", "#B388FF");
        joinBtn.setGravity(Gravity.CENTER);
        joinBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        joinBtn.setOnClickListener(v -> {
            String pk = portKeyEt.getText().toString().trim();
            if (pk.isEmpty()) {
                Toast.makeText(this, "Paste an lrm1_... Port Key above first!", Toast.LENGTH_SHORT).show();
                return;
            }
            File dir = getSharedLrmRepoDir();
            dispatchOrCopyLrmTermux(dir, "lrm join \"" + pk + "\" --init");
        });
        LinearLayout.LayoutParams c2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        c2.rightMargin = dp(6);
        r2.addView(joinBtn, c2);

        TextView dropBtn = makeChip("📥 WI-FI DROP", "#1E293B", "#FFD54F");
        dropBtn.setGravity(Gravity.CENTER);
        dropBtn.setPadding(dp(8), dp(11), dp(8), dp(11));
        dropBtn.setOnClickListener(v -> {
            File dir = exportUnpackedLrmRepo();
            if (dir != null) {
                dispatchOrCopyLrmTermux(dir, "( [ -d .lrm ] || lrm init --user mobile ) && lrm receive --port 8788");
            }
        });
        r2.addView(dropBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(r2);

        // Row 3: Reload Synced LRM Repo & Inbox Back into Studio!
        TextView reloadBtn = makeChip("🔄 RELOAD SYNCED LRM REPO & INBOX INTO STUDIO", "#102A43", "#38BDF8");
        reloadBtn.setGravity(Gravity.CENTER);
        reloadBtn.setPadding(dp(10), dp(12), dp(10), dp(12));
        reloadBtn.setOnClickListener(v -> {
            try {
                File dir = getSharedLrmRepoDir();
                pushUndoSnapshot();
                GameProject synced = LrmWorkspaceBridge.importFromLrmRepo(dir);
                synced.id = project.id;
                project = synced;
                applyRestoredProject("✓ Imported LRM Repo & Inbox (" + project.entities.size() + " entities)!");
                appendLrmLog("[✓] Reloaded project from LRM repo: " + dir.getAbsolutePath());
            } catch (Exception ex) {
                appendLrmLog("[!] Could not import repo yet: " + ex.getMessage());
                Toast.makeText(this, "LRM import: " + ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams rlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlLp.topMargin = dp(8);
        box.addView(reloadBtn, rlLp);

        lrmLogTv = new TextView(this);
        lrmLogTv.setTypeface(Typeface.MONOSPACE);
        lrmLogTv.setTextSize(11.5f);
        lrmLogTv.setTextColor(Color.parseColor("#A7F3D0"));
        lrmLogTv.setBackgroundColor(Color.parseColor("#06090F"));
        lrmLogTv.setPadding(dp(12), dp(12), dp(12), dp(12));
        lrmLogTv.setText("LRM Console Ready (hacvilke/lrm-mobile).\n"
                + "• Unpacks your game into diffable files: vortex.manifest.json, scene/entities.json, scripts/main.js, flow/rules.json, assets/* & inbox/.\n"
                + "• Tap COMMIT, SHARE KEY, JOIN, or WI-FI DROP to copy & run in Termux!\n");
        LinearLayout.LayoutParams lgLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lgLp.topMargin = dp(10);
        box.addView(lrmLogTv, lgLp);

        sv.addView(box);
        return sv;
    }

    private File getSharedLrmRepoDir() {
        File dl = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "VortexEngine/LRM/" + project.getSlug());
        return dl;
    }

    private File exportUnpackedLrmRepo() {
        try {
            syncCodeFromEditor();
            File dir = getSharedLrmRepoDir();
            try {
                LrmWorkspaceBridge.exportToLrmRepo(project, dir);
            } catch (Exception fallback) {
                dir = new File(getExternalFilesDir(null), "LRM/" + project.getSlug());
                LrmWorkspaceBridge.exportToLrmRepo(project, dir);
            }
            appendLrmLog("[✓] Unpacked multi-file LRM workspace -> " + dir.getAbsolutePath());
            return dir;
        } catch (Exception ex) {
            appendLrmLog("[ERROR] Unpack failed: " + ex.getMessage());
            return null;
        }
    }

    private void dispatchOrCopyLrmTermux(File repoDir, String subCmd) {
        String fullCmd = LrmWorkspaceBridge.buildTermuxShellSequence(repoDir.getAbsolutePath(), subCmd);
        TermuxBridge.copyToClipboard(this, "Vortex LRM Command", fullCmd);
        appendLrmLog("[CMD COPIED]\n  " + fullCmd);
        Toast.makeText(this, "Copied LRM command! Paste in Termux (auto-installs lrm-mobile if needed).", Toast.LENGTH_LONG).show();
        TermuxBridge.openTermuxApp(this);
    }

    private void appendLrmLog(String msg) {
        if (lrmLogTv != null) lrmLogTv.append("\n" + msg);
    }

    // =========================================================================
    // 🎵 16-Step Chiptune Tracker & Procedural SFX Lab Panel
    // =========================================================================
    private ScrollView buildSfxLabPanel() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0B0F17"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(24));

        // --- SECTION 1: 16-STEP POLYPHONIC CHIPTUNE COMPOSER ---
        TextView musicHead = new TextView(this);
        musicHead.setText("🎵 16-STEP CHIPTUNE MUSIC COMPOSER");
        musicHead.setTextColor(Color.parseColor("#00E676"));
        musicHead.setTextSize(15.5f);
        musicHead.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(musicHead);

        TextView musicSub = new TextView(this);
        musicSub.setText("4-Channel Polyphonic Synth (Square Lead + Triangle Bass + Kick/Hat). Loops in Android APKs, Desktop .exe & HTML5 Web exports with zero MP3 bloat!");
        musicSub.setTextColor(Color.parseColor("#94A3B8"));
        musicSub.setTextSize(11.5f);
        musicSub.setPadding(0, dp(3), 0, dp(8));
        box.addView(musicSub);

        CheckBox musicEnableCb = new CheckBox(this);
        musicEnableCb.setText("Auto-play Chiptune Soundtrack when game starts (" + project.musicBpm + " BPM)");
        musicEnableCb.setTextColor(Color.WHITE);
        musicEnableCb.setChecked(project.musicEnabled);
        musicEnableCb.setOnCheckedChangeListener((b, checked) -> {
            project.musicEnabled = checked;
            saveProjectQuiet();
        });
        box.addView(musicEnableCb);

        // Genre Preset Row
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        presetRow.setPadding(0, dp(6), 0, dp(6));

        final TextView[] stepBtns = new TextView[16];
        final Runnable refreshStepUi = () -> {
            int[] steps = ChiptuneGenerator.parseSteps(project.musicPattern);
            for (int i = 0; i < 16; i++) {
                int idx = Math.max(0, Math.min(ChiptuneGenerator.NOTE_LABELS.length - 1, steps[i]));
                String lbl = idx == 0 ? "-" : ChiptuneGenerator.NOTE_LABELS[idx];
                if (stepBtns[i] != null) {
                    stepBtns[i].setText((i + 1) + ":" + lbl);
                    boolean active = idx > 0;
                    styleChip(stepBtns[i], active ? "#142E24" : "#162032", active ? "#00E676" : "#334155");
                }
            }
            musicEnableCb.setText("Auto-play Chiptune Soundtrack when game starts (" + project.musicBpm + " BPM)");
        };

        String[][] genrePresets = {
                {"⚡ CYBER", ChiptuneGenerator.PRESET_CYBERPUNK, "134"},
                {"🏰 DUNG", ChiptuneGenerator.PRESET_DUNGEON, "112"},
                {"🔥 BOSS", ChiptuneGenerator.PRESET_BOSS, "148"},
                {"☁️ CHILL", ChiptuneGenerator.PRESET_SKYCHILL, "118"}
        };
        for (int g = 0; g < genrePresets.length; g++) {
            final String[] gp = genrePresets[g];
            TextView pb = makeChip(gp[0], "#16263D", "#00E5FF");
            pb.setGravity(Gravity.CENTER);
            pb.setOnClickListener(v -> {
                pushUndoSnapshot();
                project.musicPattern = gp[1];
                project.musicBpm = Integer.parseInt(gp[2]);
                project.musicEnabled = true;
                musicEnableCb.setChecked(true);
                refreshStepUi.run();
                sfxPreview.startMusicLoop(project.musicPattern, project.musicBpm);
                saveProjectQuiet();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            if (g < 3) lp.rightMargin = dp(5);
            presetRow.addView(pb, lp);
        }
        box.addView(presetRow);

        // 16-Step Interactive Lead Note Grid (4 rows x 4 steps — super clean on 360dp portrait!)
        for (int r = 0; r < 4; r++) {
            LinearLayout sRow = new LinearLayout(this);
            sRow.setOrientation(LinearLayout.HORIZONTAL);
            sRow.setPadding(0, dp(3), 0, dp(3));
            for (int c = 0; c < 4; c++) {
                final int stepIdx = r * 4 + c;
                TextView sb = makeChip((stepIdx + 1) + ":-", "#162032", "#334155");
                sb.setGravity(Gravity.CENTER);
                sb.setPadding(dp(4), dp(7), dp(4), dp(7));
                sb.setOnClickListener(v -> {
                    int[] steps = ChiptuneGenerator.parseSteps(project.musicPattern);
                    steps[stepIdx] = (steps[stepIdx] + 1) % ChiptuneGenerator.NOTE_LABELS.length;
                    StringBuilder joined = new StringBuilder();
                    for (int k = 0; k < 16; k++) {
                        if (k > 0) joined.append(',');
                        joined.append(steps[k]);
                    }
                    project.musicPattern = joined.toString();
                    refreshStepUi.run();
                    saveProjectQuiet();
                });
                stepBtns[stepIdx] = sb;
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (c < 3) slp.rightMargin = dp(5);
                sRow.addView(sb, slp);
            }
            box.addView(sRow);
        }
        refreshStepUi.run();

        // Loop Preview / Stop Row
        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setPadding(0, dp(8), 0, dp(16));

        TextView playLoopBtn = makeChip("▶ PREVIEW LOOP", "#064E3B", "#00E676");
        playLoopBtn.setGravity(Gravity.CENTER);
        playLoopBtn.setOnClickListener(v -> sfxPreview.startMusicLoop(project.musicPattern, project.musicBpm));
        LinearLayout.LayoutParams plLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        plLp.rightMargin = dp(6);
        transportRow.addView(playLoopBtn, plLp);

        TextView stopLoopBtn = makeChip("■ STOP MUSIC", "#2D161E", "#FF5252");
        stopLoopBtn.setGravity(Gravity.CENTER);
        stopLoopBtn.setOnClickListener(v -> sfxPreview.stopMusicLoop());
        transportRow.addView(stopLoopBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(transportRow);

        // --- SECTION 2: ONE-SHOT ARCADE SFX SYNTH ---
        TextView head = new TextView(this);
        head.setText("🔊 PROCEDURAL RETRO SFX SYNTHESIZER");
        head.setTextColor(Color.parseColor("#00E5FF"));
        head.setTextSize(16f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView desc = new TextView(this);
        desc.setText("Real-time 22.05kHz PCM wave synthesizer bundled inside every exported Android & Desktop game (zero external audio files needed!). Tap any preset to audition or insert into your script:");
        desc.setTextColor(Color.parseColor("#94A3B8"));
        desc.setTextSize(12f);
        desc.setPadding(0, dp(4), 0, dp(12));
        box.addView(desc);

        String[][] presets = {
                {"jump", "🚀 JUMP SWEEP", "Rising square/sine arcade jump sweep", "#00E5FF"},
                {"coin", "🪙 CRYSTAL / COIN", "Dual-tone B5 -> E6 chime pickup", "#FFD740"},
                {"laser", "⚡ PLASMA LASER", "Fast frequency-drop sci-fi blaster", "#00E676"},
                {"explosion", "💥 EXPLOSION", "Low-rumble white-noise burst", "#FF5252"},
                {"hit", "🛡 DAMAGE / HIT", "Crunch impact feedback", "#FF4081"},
                {"powerup", "✨ LEVEL CLEAR / WARP", "4-note ascending arpeggio fanfare", "#B388FF"}
        };

        for (String[] pr : presets) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#131C2E"));
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), Color.parseColor("#24324B"));
            row.setBackground(bg);

            LinearLayout txtCol = new LinearLayout(this);
            txtCol.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText(pr[1]);
            t1.setTextColor(Color.parseColor(pr[3]));
            t1.setTypeface(Typeface.DEFAULT_BOLD);
            t1.setTextSize(13.5f);
            txtCol.addView(t1);

            TextView t2 = new TextView(this);
            t2.setText(pr[2]);
            t2.setTextColor(Color.parseColor("#94A3B8"));
            t2.setTextSize(11.5f);
            txtCol.addView(t2);
            row.addView(txtCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView playBtn = makeChip("▶ PLAY", "#16263D", pr[3]);
            playBtn.setOnClickListener(v -> sfxPreview.play(pr[0]));
            row.addView(playBtn, rightMarginLp(6));

            TextView insBtn = makeChip("＋ SCRIPT", "#1E293B", "#64748B");
            insBtn.setOnClickListener(v -> {
                String call = "LUA".equalsIgnoreCase(project.scriptLanguage)
                        ? "\n    vortex.playSound('" + pr[0] + "')\n"
                        : "\n    playSound('" + pr[0] + "');\n";
                scriptEditor.getText().append(call);
                Toast.makeText(this, "Appended " + pr[0] + " to Script!", Toast.LENGTH_SHORT).show();
            });
            row.addView(insBtn);

            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = dp(8);
            box.addView(row, rLp);
        }

        sv.addView(box);
        return sv;
    }

    // =========================================================================
    // Standalone APK Export & Termux Builder Panel
    // =========================================================================
    private ScrollView buildApkExportPanel() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0B0F17"));

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(32));

        TextView head = new TextView(this);
        head.setText("📦 MULTI-PLATFORM EXPORT (ANDROID APK + HTML5 WEB)");
        head.setTextColor(Color.parseColor("#00E5FF"));
        head.setTextSize(15.5f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView sub = new TextView(this);
        sub.setText("Export a 1-file playable HTML5 Web Game (.html for itch.io / Chrome / sharing) OR build a signed standalone Android .apk with LuaJ + JBox2D + Theta* AI + Tween Engine!");
        sub.setTextColor(Color.parseColor("#94A3B8"));
        sub.setTextSize(12f);
        sub.setPadding(0, dp(4), 0, dp(10));
        box.addView(sub);

        // Instant Single-File HTML5 Web Exporter Button!
        TextView webExportBtn = makeChip("🌐 EXPORT PLAYABLE HTML5 WEB GAME (.HTML)", "#102A43", "#00E5FF");
        webExportBtn.setGravity(Gravity.CENTER);
        webExportBtn.setPadding(dp(10), dp(12), dp(10), dp(12));
        webExportBtn.setOnClickListener(v -> exportPlayableHtml5Web());
        LinearLayout.LayoutParams webBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        webBtnLp.bottomMargin = dp(10);
        box.addView(webExportBtn, webBtnLp);

        EditText titleEt = addLabeledEdit(box, "APK Display Name", project.title, false);
        EditText pkgEt = addLabeledEdit(box, "Android Package ID (unique per game)", project.packageName, false);
        EditText verEt = addLabeledEdit(box, "Version Name", project.versionName, false);

        String[] orients = {"landscape", "portrait", "sensor"};
        Spinner orientSpin = addLabeledSpinner(box, "Screen Orientation", orients, project.orientation);

        String[] glyphs = {"ROCKET", "CUBE", "SWORD", "STAR"};
        Spinner glyphSpin = addLabeledSpinner(box, "Procedural App Icon Style", glyphs, project.iconGlyph);

        CheckBox bgBuildCb = new CheckBox(this);
        bgBuildCb.setText("Run 1-Tap Termux build silently in background");
        bgBuildCb.setTextColor(Color.WHITE);
        bgBuildCb.setChecked(true);
        box.addView(bgBuildCb);

        CheckBox fromSourceCb = new CheckBox(this);
        fromSourceCb.setText("Compile full Engine + LuaJ + JBox2D Java sources (javac + d8)");
        fromSourceCb.setTextColor(Color.WHITE);
        fromSourceCb.setChecked(false);
        box.addView(fromSourceCb);

        // Primary Universal Build Button (Fits cleanly on 360dp portrait screens!)
        TextView copyExportBtn = makeChip("🚀 EXPORT + COPY CMD (OPEN TERMUX)", "#00C853", "#69F0AE");
        copyExportBtn.setTextColor(Color.parseColor("#041A0B"));
        copyExportBtn.setGravity(Gravity.CENTER);
        copyExportBtn.setPadding(dp(10), dp(12), dp(10), dp(12));
        copyExportBtn.setOnClickListener(v -> {
            applyExportFields(titleEt, pkgEt, verEt, orients[orientSpin.getSelectedItemPosition()], glyphs[glyphSpin.getSelectedItemPosition()]);
            runExportAndBuild(false, false);
        });
        LinearLayout.LayoutParams mainBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mainBtnLp.topMargin = dp(12);
        box.addView(copyExportBtn, mainBtnLp);

        // Secondary Row: 1-Tap Auto-Build + Permission Settings Helper
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(8), 0, 0);

        TextView oneTapBtn = makeChip("⚡ 1-TAP BUILD", "#16263D", "#00E5FF");
        oneTapBtn.setGravity(Gravity.CENTER);
        oneTapBtn.setOnClickListener(v -> {
            applyExportFields(titleEt, pkgEt, verEt, orients[orientSpin.getSelectedItemPosition()], glyphs[glyphSpin.getSelectedItemPosition()]);
            runExportAndBuild(true, bgBuildCb.isChecked(), fromSourceCb.isChecked());
        });
        LinearLayout.LayoutParams otLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        otLp.rightMargin = dp(6);
        row2.addView(oneTapBtn, otLp);

        TextView permSettingsBtn = makeChip("🔓 PERMISSIONS", "#1E293B", "#FFB300");
        permSettingsBtn.setGravity(Gravity.CENTER);
        permSettingsBtn.setOnClickListener(v -> TermuxBridge.openAppPermissionSettings(this));
        row2.addView(permSettingsBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row2);

        // Row 3: Shared Storage + Install Built APK
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(8), 0, 0);

        TextView storageBtn = makeChip("📂 STORAGE", "#1E293B", "#64748B");
        storageBtn.setGravity(Gravity.CENTER);
        storageBtn.setOnClickListener(v -> TermuxBridge.requestAllFilesAccess(this, 402));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stLp.rightMargin = dp(6);
        row3.addView(storageBtn, stLp);

        TextView installBtn = makeChip("📲 INSTALL APK", "#311B92", "#B388FF");
        installBtn.setGravity(Gravity.CENTER);
        installBtn.setOnClickListener(v -> {
            String path = lastExport != null
                    ? lastExport.expectedSharedApkPath
                    : "/sdcard/Download/VortexEngine/Builds/" + project.getSlug() + "-release.apk";
            String res = TermuxBridge.promptInstallApk(this, new File(path));
            if (!"OPENED_INSTALLER".equals(res)) appendBuildLog("[INSTALL] " + res);
        });
        row3.addView(installBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row3);

        buildLogTv = new TextView(this);
        buildLogTv.setTypeface(Typeface.MONOSPACE);
        buildLogTv.setTextSize(11.5f);
        buildLogTv.setTextColor(Color.parseColor("#A7F3D0"));
        buildLogTv.setBackgroundColor(Color.parseColor("#06090F"));
        buildLogTv.setPadding(dp(12), dp(12), dp(12), dp(12));
        buildLogTv.setText(
                "Build Console Ready.\n" +
                "• Method 1 (Works Immediately!): Tap green 'EXPORT PROJECT + COPY BUILD CMD' → paste in Termux & press Enter!\n" +
                "• Method 2 (1-Tap Auto): Enable 'Run commands in Termux environment' in App Permissions, then tap '1-TAP AUTO BUILD'.\n");
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        logLp.topMargin = dp(12);
        box.addView(buildLogTv, logLp);

        sv.addView(box);
        return sv;
    }

    private void applyExportFields(EditText titleEt, EditText pkgEt, EditText verEt, String orient, String glyph) {
        syncCodeFromEditor();
        project.title = titleEt.getText().toString().trim();
        project.packageName = pkgEt.getText().toString().trim();
        project.versionName = verEt.getText().toString().trim();
        project.orientation = orient;
        project.iconGlyph = glyph;
        ProjectRepository.saveProject(this, project);
    }

    private void runExportAndBuild(boolean directTermux, boolean background) {
        runExportAndBuild(directTermux, background, false);
    }

    private void runExportAndBuild(boolean directTermux, boolean background, boolean fromSource) {
        try {
            appendBuildLog("\n[*] Exporting standalone v2.0 bundle (LuaJ + JBox2D + FastNoiseLite)...");
            lastExport = TermuxBridge.exportStandaloneProject(this, project);
            if (lastExport.sharedZipPath != null) {
                appendBuildLog("[✓] Exported ZIP: " + lastExport.sharedZipPath);
            }
            if (lastExport.sharedProjectDir != null) {
                appendBuildLog("[✓] Exported Folder: " + lastExport.sharedProjectDir.getAbsolutePath());
            }

            String cmd = lastExport.termuxBuildCmd + (fromSource ? " --from-source" : "");
            TermuxBridge.copyToClipboard(this, "Vortex Build Command", cmd);
            appendBuildLog("[✓] Copied Termux command:\n    " + cmd);

            if (directTermux) {
                String status = TermuxBridge.triggerTermuxBuild(this, lastExport, background, fromSource);
                if ("STARTED".equals(status)) {
                    appendBuildLog("[⚡] Dispatched 1-Tap RUN_COMMAND to Termux! Waiting for APK...");
                } else if ("REQUESTED_PERMISSION".equals(status)) {
                    appendBuildLog("[!] Requested RUN_COMMAND permission. On Samsung/Android 14+, if no popup appeared, tap '🔓 APP PERMISSIONS' → Permissions → Additional permissions → enable 'Run commands in Termux environment'.\n[TIP] Meanwhile, the build command is ALREADY copied — just paste in Termux!");
                } else if ("ERR_NO_TERMUX".equals(status)) {
                    appendBuildLog("[!] Termux app not found on device.");
                } else {
                    appendBuildLog("[!] Termux error: " + status + "\nPaste the copied command directly inside Termux.");
                }
            } else {
                Toast.makeText(this, "Exported & copied build command! Long-press -> Paste in Termux.", Toast.LENGTH_LONG).show();
                TermuxBridge.openTermuxApp(this);
            }
        } catch (Exception ex) {
            appendBuildLog("[ERROR] Export failed: " + ex.getMessage());
        }
    }

    private void exportPlayableHtml5Web() {
        try {
            syncCodeFromEditor();
            ProjectRepository.saveProject(this, project);
            File dlDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "VortexEngine/Web");
            File outFile = new File(dlDir, project.getSlug() + "-web.html");
            try {
                Html5WebExporter.exportToFile(project, outFile);
            } catch (Exception fallbackEx) {
                File appDir = new File(getExternalFilesDir(null), "Web");
                outFile = new File(appDir, project.getSlug() + "-web.html");
                Html5WebExporter.exportToFile(project, outFile);
            }
            appendBuildLog("\n[🌐 HTML5] Exported standalone 60FPS Web Game:\n    " + outFile.getAbsolutePath());
            Toast.makeText(this, "✓ Exported HTML5 Game: " + outFile.getName(), Toast.LENGTH_LONG).show();

            try {
                android.net.Uri uri = VortexFileProvider.getUriForFile(this, outFile);
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("text/html");
                share.putExtra(Intent.EXTRA_STREAM, uri);
                share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "Share or Open HTML5 Web Game"));
            } catch (Exception ignored) {
            }
        } catch (Exception ex) {
            appendBuildLog("[HTML5 ERROR] " + ex.getMessage());
            Toast.makeText(this, "HTML5 export failed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void appendBuildLog(String msg) {
        if (buildLogTv != null) buildLogTv.append("\n" + msg);
    }

    private void switchMode(String mode) {
        syncCodeFromEditor();
        sfxPreview.stopMusicLoop();
        currentMode = mode;

        for (TextView tb : tabButtons) {
            boolean active = mode.equals(tb.getTag());
            styleChip(tb, active ? "#00B8D4" : "#162032", active ? "#00E5FF" : "#334155");
            tb.setTextColor(active ? Color.parseColor("#04131A") : Color.WHITE);
        }

        sceneBottomToolbar.setVisibility("SCENE".equals(mode) ? View.VISIBLE : View.GONE);
        inspectorDrawer.setVisibility("INSPECTOR".equals(mode) ? View.VISIBLE : View.GONE);
        flowPanel.setVisibility("FLOW".equals(mode) ? View.VISIBLE : View.GONE);
        lrmPanel.setVisibility("LRM".equals(mode) ? View.VISIBLE : View.GONE);
        pixelArtPanel.setVisibility("PIXEL".equals(mode) ? View.VISIBLE : View.GONE);
        scriptPanel.setVisibility("SCRIPT".equals(mode) ? View.VISIBLE : View.GONE);
        sfxPanel.setVisibility("SFX".equals(mode) ? View.VISIBLE : View.GONE);
        buildPanel.setVisibility("BUILD".equals(mode) ? View.VISIBLE : View.GONE);

        if ("FLOW".equals(mode)) refreshFlowRuleList();

        if ("PLAY".equals(mode)) {
            if (!validateCurrentScript()) {
                Toast.makeText(this, "Script syntax error — check Script tab!", Toast.LENGTH_LONG).show();
                switchMode("SCRIPT");
                return;
            }
            viewportSurface.startPlayMode(project.deepCopy());
            telemetryChip.setText("▶ LIVE PLAY (" + project.scriptLanguage + ") — Tap 🌐 SCENE to stop");
        } else {
            if (viewportSurface.renderer.isPlaying()) {
                viewportSurface.loadProjectForEdit(project);
            }
            if ("INSPECTOR".equals(mode)) populateInspector();
        }
    }

    private void syncCodeFromEditor() {
        if (scriptEditor != null && project != null) {
            project.scriptCode = scriptEditor.getText().toString();
        }
    }

    private void saveProjectQuiet() {
        syncCodeFromEditor();
        ProjectRepository.saveProject(this, project);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 401) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                appendBuildLog("[✓] Termux RUN_COMMAND permission granted! Tap ⚡ 1-TAP AUTO BUILD again.");
            } else {
                appendBuildLog("[i] Permission not granted via popup. Tap '🔓 APP PERMISSIONS' → Permissions → Additional permissions → enable 'Run commands in Termux environment'.");
            }
        }
    }

    private EditText addLabeledEdit(LinearLayout parent, String label, String val, boolean numeric) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(11.5f);
        tv.setPadding(0, dp(6), 0, dp(2));
        parent.addView(tv);

        EditText et = new EditText(this);
        et.setText(val);
        et.setTextColor(Color.WHITE);
        et.setTextSize(13f);
        et.setSingleLine(true);
        if (numeric) {
            et.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        parent.addView(et);
        return et;
    }

    private EditText addCompactNum(LinearLayout row, String label, float val) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(10.5f);
        col.addView(tv);

        EditText et = new EditText(this);
        et.setText(String.format(Locale.US, "%.2f", val));
        et.setTextColor(Color.WHITE);
        et.setTextSize(12.5f);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        col.addView(et);

        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return et;
    }

    private Spinner addLabeledSpinner(LinearLayout parent, String label, String[] items, String current) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#94A3B8"));
        tv.setTextSize(11.5f);
        tv.setPadding(0, dp(6), 0, dp(2));
        parent.addView(tv);

        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        sp.setAdapter(ad);
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(current)) { sp.setSelection(i); break; }
        }
        parent.addView(sp);
        return sp;
    }

    private LinearLayout.LayoutParams rightMarginLp(int dpVal) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(dpVal);
        return lp;
    }

    private float parseF(EditText et, float def) {
        try { return Float.parseFloat(et.getText().toString().trim()); }
        catch (Exception e) { return def; }
    }

    private TextView makeChip(String text, String bgHex, String borderHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(true);
        tv.setPadding(dp(10), dp(6), dp(10), dp(6));
        styleChip(tv, bgHex, borderHex);
        return tv;
    }

    private void styleChip(TextView tv, String bgHex, String borderHex) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(8));
        gd.setStroke(dp(1), Color.parseColor(borderHex));
        tv.setBackground(gd);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewportSurface != null) viewportSurface.onResume();
        TermuxResultService.setListener(termuxListener);
    }

    @Override
    protected void onPause() {
        saveProjectQuiet();
        TermuxResultService.setListener(null);
        if (viewportSurface != null) viewportSurface.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        sfxPreview.shutdown();
        if (viewportSurface != null) viewportSurface.onDestroy();
        super.onDestroy();
    }
}
