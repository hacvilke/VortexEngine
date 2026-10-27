package com.vortex.engine.studio;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private VortexMobileUi.VortexDropdown langDropdown;
    private LinearLayout snippetBar;
    private HorizontalScrollView topTabScroll;
    private boolean inspectorExpandedMobile = false;
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
        VortexMobileUi.applyDarkSystemBars(this);
        VortexSaveStore.setSaveRootDir(new File(getFilesDir(), "vortex_saves"));

        String id = getIntent().getStringExtra(EXTRA_PROJECT_ID);
        project = ProjectRepository.loadProject(this, id);
        if (project == null) {
            project = ProjectRepository.listProjects(this).get(0);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(VortexMobileUi.C_VOID);
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
        applyInspectorLayoutParams();
        workspace.addView(inspectorDrawer);

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

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyInspectorLayoutParams();
    }

    private void applyInspectorLayoutParams() {
        if (inspectorDrawer == null) return;
        int screenWidthDp = getResources().getConfiguration().screenWidthDp;
        int screenHeightPx = getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams inspLp;
        if (screenWidthDp < 540) {
            // Mobile Portrait Split-Preview: keep 2D/3D Viewport visible above Inspector!
            int h = inspectorExpandedMobile
                    ? FrameLayout.LayoutParams.MATCH_PARENT
                    : Math.round(screenHeightPx * 0.56f);
            inspLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, h, Gravity.BOTTOM);
        } else {
            inspLp = new FrameLayout.LayoutParams(
                    dp(336), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END);
        }
        inspectorDrawer.setLayoutParams(inspLp);
    }

    private View buildTopBar() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(VortexMobileUi.C_HEADER);

        // Row 1: Back, Single-line Title, Unified 2D/3D Toggle, Undo/Redo, Save
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setPadding(dp(8), dp(7), dp(8), dp(5));

        TextView backBtn = VortexMobileUi.makeButton(this, "← HUB", VortexMobileUi.ButtonVariant.SECONDARY, true);
        backBtn.setOnClickListener(v -> { saveProjectQuiet(); finish(); });
        row1.addView(backBtn);

        TextView titleTv = new TextView(this);
        titleTv.setText(project.title);
        titleTv.setTextColor(VortexMobileUi.C_TEXT);
        titleTv.setTextSize(14f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setSingleLine(true);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        titleTv.setPadding(dp(9), 0, dp(6), 0);
        row1.addView(titleTv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        dimToggleBtn = VortexMobileUi.makeButton(this, project.dimensionMode, VortexMobileUi.ButtonVariant.ACCENT, true);
        dimToggleBtn.setOnClickListener(v -> {
            pushUndoSnapshot();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimToggleBtn.setText(project.dimensionMode);
            viewportSurface.renderer.resetEditorCamera();
            rebuildSceneToolbarButtons();
            saveProjectQuiet();
        });
        row1.addView(dimToggleBtn, rightMarginLp(4));

        TextView undoBtn = VortexMobileUi.makeButton(this, "↶", VortexMobileUi.ButtonVariant.SECONDARY, true);
        undoBtn.setOnClickListener(v -> performUndo());
        row1.addView(undoBtn, rightMarginLp(4));

        TextView redoBtn = VortexMobileUi.makeButton(this, "↷", VortexMobileUi.ButtonVariant.SECONDARY, true);
        redoBtn.setOnClickListener(v -> performRedo());
        row1.addView(redoBtn, rightMarginLp(4));

        TextView saveBtn = VortexMobileUi.makeButton(this, "SAVE", VortexMobileUi.ButtonVariant.PRIMARY, true);
        saveBtn.setOnClickListener(v -> {
            syncCodeFromEditor();
            ProjectRepository.saveProject(this, project);
            Toast.makeText(this, "✓ Saved '" + project.title + "'", Toast.LENGTH_SHORT).show();
        });
        row1.addView(saveBtn);
        container.addView(row1);

        // Row 2: Unified Animated Mode Tabs
        topTabScroll = new HorizontalScrollView(this);
        topTabScroll.setHorizontalScrollBarEnabled(false);
        topTabScroll.setBackgroundColor(VortexMobileUi.C_PANEL);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(5), dp(8), dp(6));

        String[][] modes = {
                {"SCENE", "🌐 SCENE"},
                {"INSPECTOR", "🎛 INSPECT"},
                {"SCRIPT", "📜 CODE"},
                {"FLOW", "⚡ FLOW"},
                {"LRM", "🔄 LRM P2P"},
                {"PIXEL", "🎨 PIXEL"},
                {"SFX", "🎵 AUDIO"},
                {"PLAY", "▶ PLAY"},
                {"BUILD", "📦 EXPORT"}
        };
        for (String[] m : modes) {
            TextView tb = VortexMobileUi.makeButton(this, m[1], VortexMobileUi.ButtonVariant.SECONDARY, true);
            tb.setTag(m[0]);
            tb.setOnClickListener(v -> switchMode(m[0]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            tabs.addView(tb, lp);
            tabButtons.add(tb);
        }
        topTabScroll.addView(tabs);
        container.addView(topTabScroll);

        View divider = new View(this);
        divider.setBackgroundColor(VortexMobileUi.C_BORDER);
        container.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
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

        boolean snapOn = viewportSurface == null || viewportSurface.isGridSnap();
        TextView snapBtn = VortexMobileUi.makeButton(
                this, snapOn ? "🧲 SNAP: ON" : "🧲 SNAP: OFF",
                snapOn ? VortexMobileUi.ButtonVariant.ACCENT : VortexMobileUi.ButtonVariant.SECONDARY, true);
        snapBtn.setOnClickListener(v -> {
            boolean next = !viewportSurface.isGridSnap();
            viewportSurface.setGridSnap(next);
            snapBtn.setText(next ? "🧲 SNAP: ON" : "🧲 SNAP: OFF");
            VortexMobileUi.applyButtonVariant(this, snapBtn,
                    next ? VortexMobileUi.ButtonVariant.ACCENT : VortexMobileUi.ButtonVariant.SECONDARY, true);
        });
        row.addView(snapBtn, rightMarginLp(6));

        addToolBtn(row, "⎘ CLONE", this::duplicateSelected);
        TextView delBtn = VortexMobileUi.makeButton(this, "✕ DEL", VortexMobileUi.ButtonVariant.DANGER, true);
        delBtn.setOnClickListener(v -> deleteSelected());
        row.addView(delBtn, rightMarginLp(6));
        addToolBtn(row, "⌖ CAM", () -> viewportSurface.renderer.resetEditorCamera());
        addToolBtn(row, "📋 ENTITIES", this::showEntityPickerDialog);

        hsv.addView(row);
        wrap.addView(hsv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void addToolBtn(LinearLayout row, String label, Runnable action) {
        TextView btn = VortexMobileUi.makeButton(this, label, VortexMobileUi.ButtonVariant.SECONDARY, true);
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
        syncLangDropdownUi();
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
        int activeIdx = 0;
        String selId = viewportSurface.renderer.getSelectedEntityId();
        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            names[i] = e.name + "  [" + e.primitive + " • " + e.bodyType + "]";
            if (e.id.equals(selId)) activeIdx = i;
        }
        VortexMobileUi.showPickerModal(this, "Scene Entities (" + names.length + ")", names, activeIdx,
                (which, val) -> {
                    Entity chosen = project.entities.get(which);
                    viewportSurface.renderer.setSelectedEntityId(chosen.id);
                    if ("INSPECTOR".equals(currentMode)) populateInspector();
                });
    }

    private ScrollView buildInspectorDrawer() {
        ScrollView sv = new ScrollView(this);
        GradientDrawable drawerBg = new GradientDrawable();
        drawerBg.setColor(Color.argb(246, 13, 19, 32));
        drawerBg.setStroke(dp(1), VortexMobileUi.C_BORDER);
        sv.setBackground(drawerBg);
        inspectorContent = new LinearLayout(this);
        inspectorContent.setOrientation(LinearLayout.VERTICAL);
        inspectorContent.setPadding(dp(14), dp(10), dp(14), dp(26));
        sv.addView(inspectorContent);
        return sv;
    }

    private void populateInspector() {
        inspectorContent.removeAllViews();

        // Top Header + Portrait Split/Full Toggle!
        LinearLayout topHead = new LinearLayout(this);
        topHead.setOrientation(LinearLayout.HORIZONTAL);
        topHead.setGravity(Gravity.CENTER_VERTICAL);
        topHead.setPadding(0, 0, 0, dp(6));

        TextView head = new TextView(this);
        head.setText("ENTITY & PHYSICS INSPECTOR");
        head.setTextColor(VortexMobileUi.C_CYAN);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setTextSize(12.5f);
        topHead.addView(head, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (getResources().getConfiguration().screenWidthDp < 540) {
            TextView sizeToggle = VortexMobileUi.makeButton(
                    this, inspectorExpandedMobile ? "⤡ SPLIT VIEW" : "⤢ FULL HEIGHT",
                    VortexMobileUi.ButtonVariant.SECONDARY, true);
            sizeToggle.setPadding(dp(8), dp(4), dp(8), dp(4));
            sizeToggle.setTextSize(10f);
            sizeToggle.setOnClickListener(v -> {
                inspectorExpandedMobile = !inspectorExpandedMobile;
                applyInspectorLayoutParams();
                populateInspector();
            });
            topHead.addView(sizeToggle);
        }
        inspectorContent.addView(topHead);

        if (!project.entities.isEmpty()) {
            String[] entityNames = new String[project.entities.size()];
            String curVal = "";
            String curId = viewportSurface.renderer.getSelectedEntityId();
            for (int i = 0; i < project.entities.size(); i++) {
                Entity item = project.entities.get(i);
                entityNames[i] = item.name + " [" + item.primitive + "]";
                if (item.id.equals(curId)) curVal = entityNames[i];
            }
            VortexMobileUi.VortexDropdown pickDrop = new VortexMobileUi.VortexDropdown(this, entityNames, curVal);
            pickDrop.setOnSelectionListener((pos, val) -> {
                if (pos >= 0 && pos < project.entities.size()) {
                    viewportSurface.renderer.setSelectedEntityId(project.entities.get(pos).id);
                    populateInspector();
                }
            });
            inspectorContent.addView(pickDrop);
        }

        Entity e = project.findById(viewportSurface.renderer.getSelectedEntityId());
        if (e == null) {
            TextView empty = new TextView(this);
            empty.setText("Tap any object in the 2D/3D viewport or use 📋 ENTITIES to inspect.");
            empty.setTextColor(VortexMobileUi.C_TEXT_MUTED);
            empty.setPadding(0, dp(12), 0, dp(12));
            inspectorContent.addView(empty);
            return;
        }

        EditText nameEt = addLabeledEdit(inspectorContent, "Name", e.name, false);
        EditText tagEt = addLabeledEdit(inspectorContent, "Tag (player/solid/coin/enemy/goal)", e.tag, false);

        String[] prims = "3D".equalsIgnoreCase(project.dimensionMode)
                ? new String[]{"CUBE", "SPHERE", "PYRAMID", "CYLINDER", "PLANE", "TERRAIN", "CUSTOM_OBJ"}
                : new String[]{"RECT", "CIRCLE", "TRIANGLE", "CUSTOM_SPRITE", "SPRITE_HERO", "SPRITE_SLIME", "SPRITE_COIN", "SPRITE_SHIP"};
        VortexMobileUi.VortexDropdown primSpin = addLabeledSpinner(inspectorContent, "Visual / Mesh", prims, e.primitive);

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

        // Multi-Frame Flipbook Sprite Animation Row (2D)
        EditText framesEt = addLabeledEdit(inspectorContent, "Sprite Flipbook Frames (e.g. knight_1,knight_2)", e.spriteFrames, false);
        EditText animFpsEt = addLabeledEdit(inspectorContent, "Flipbook Animation FPS", String.valueOf(e.animFps), true);

        String[] bodies = {"NONE", "STATIC", "DYNAMIC", "KINEMATIC"};
        VortexMobileUi.VortexDropdown bodySpin = addLabeledSpinner(inspectorContent, "Physics Body Type", bodies, e.bodyType);

        VortexMobileUi.VortexToggle triggerCb = new VortexMobileUi.VortexToggle(this, "Trigger Sensor", e.isTrigger);
        inspectorContent.addView(triggerCb);

        VortexMobileUi.VortexToggle fixedRotCb = new VortexMobileUi.VortexToggle(this, "Lock Rotation (Platformer Hero)", e.fixedRotation);
        inspectorContent.addView(fixedRotCb);

        VortexMobileUi.VortexToggle checkerCb = new VortexMobileUi.VortexToggle(this, "Procedural Checker Texture", e.checkerTexture);
        inspectorContent.addView(checkerCb);

        VortexMobileUi.VortexToggle glowCb = new VortexMobileUi.VortexToggle(this, "Neon Emissive Shading", e.emissive);
        inspectorContent.addView(glowCb);

        TextView applyBtn = VortexMobileUi.makeButton(this, "✓ APPLY ENTITY CHANGES", VortexMobileUi.ButtonVariant.PRIMARY, false);
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
                e.spriteFrames = framesEt.getText().toString().trim();
                e.animFps = Math.max(0.5f, parseF(animFpsEt, e.animFps));
                e.bodyType = bodies[bodySpin.getSelectedItemPosition()];
                e.isTrigger = triggerCb.isChecked();
                e.fixedRotation = fixedRotCb.isChecked();
                e.checkerTexture = checkerCb.isChecked();
                e.emissive = glowCb.isChecked();
            }
            saveProjectQuiet();
            Toast.makeText(this, "✓ Updated " + e.name, Toast.LENGTH_SHORT).show();
        });
        inspectorContent.addView(applyBtn, apLp);

        // World Settings (Unified Cyan Accent — No Random Purple!)
        TextView worldHead = new TextView(this);
        worldHead.setText("WORLD & ENGINE SETTINGS");
        worldHead.setTextColor(VortexMobileUi.C_CYAN);
        worldHead.setTypeface(Typeface.DEFAULT_BOLD);
        worldHead.setTextSize(12.5f);
        worldHead.setPadding(0, dp(16), 0, dp(4));
        inspectorContent.addView(worldHead);

        EditText skyEt = addLabeledEdit(inspectorContent, "Sky Hex", project.skyColorHex, false);
        EditText gravEt = addLabeledEdit(inspectorContent, "Gravity Y", String.valueOf(project.gravityY), true);
        VortexMobileUi.VortexToggle box2dCb = new VortexMobileUi.VortexToggle(this, "Enable JBox2D Rigid-Body Solver (2D)", project.useBox2D);
        inspectorContent.addView(box2dCb);

        TextView saveWorldBtn = VortexMobileUi.makeButton(this, "✓ APPLY WORLD SETTINGS", VortexMobileUi.ButtonVariant.ACCENT, false);
        saveWorldBtn.setOnClickListener(v -> {
            project.skyColorHex = skyEt.getText().toString().trim();
            project.gravityY = parseF(gravEt, project.gravityY);
            project.useBox2D = box2dCb.isChecked();
            saveProjectQuiet();
            Toast.makeText(this, "✓ World settings saved!", Toast.LENGTH_SHORT).show();
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

        spriteNameEdit = VortexMobileUi.createInput(this, "hero_sprite", false, false);
        top.addView(spriteNameEdit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView mirrorBtn = VortexMobileUi.makeButton(this, "⇄ MIRROR: OFF", VortexMobileUi.ButtonVariant.SECONDARY, true);
        mirrorBtn.setOnClickListener(v -> {
            boolean next = !pixelArtCanvas.isMirrorX();
            pixelArtCanvas.setMirrorX(next);
            mirrorBtn.setText(next ? "⇄ MIRROR: ON" : "⇄ MIRROR: OFF");
            VortexMobileUi.applyButtonVariant(this, mirrorBtn,
                    next ? VortexMobileUi.ButtonVariant.ACCENT : VortexMobileUi.ButtonVariant.SECONDARY, true);
        });
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mLp.leftMargin = dp(6);
        top.addView(mirrorBtn, mLp);

        TextView clearBtn = VortexMobileUi.makeButton(this, "CLEAR", VortexMobileUi.ButtonVariant.DANGER, true);
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

        // Bottom Actions: Custom Sprite Preset Dropdown + Save & Spawn
        LinearLayout bot = new LinearLayout(this);
        bot.setOrientation(LinearLayout.HORIZONTAL);
        bot.setGravity(Gravity.CENTER_VERTICAL);
        bot.setPadding(0, dp(8), 0, 0);

        String[] spPresets = {"Preset: Knight 1", "Preset: Knight 2", "Preset: Drone", "Preset: Crate", "Preset: Gem"};
        VortexMobileUi.VortexDropdown spDrop = new VortexMobileUi.VortexDropdown(this, spPresets, spPresets[0]);
        spDrop.setOnSelectionListener((idx, val) -> {
            if (idx == 0) { spriteNameEdit.setText("knight_1"); pixelArtCanvas.loadEncoded(PixelSpriteCodec.createDefaultKnightSprite()); }
            else if (idx == 1) { spriteNameEdit.setText("knight_2"); pixelArtCanvas.loadEncoded(PixelSpriteCodec.createKnightRunFrame2Sprite()); }
            else if (idx == 2) { spriteNameEdit.setText("drone_1"); pixelArtCanvas.loadEncoded(PixelSpriteCodec.createDroneFrame1Sprite()); }
            else if (idx == 3) { spriteNameEdit.setText("crate"); pixelArtCanvas.loadEncoded(PixelSpriteCodec.createCrateSprite()); }
            else { spriteNameEdit.setText("gem"); pixelArtCanvas.loadEncoded(PixelSpriteCodec.createGemSprite()); }
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.05f);
        b1.rightMargin = dp(6);
        bot.addView(spDrop, b1);

        TextView saveSpawnBtn = VortexMobileUi.makeButton(this, "✓ SAVE & SPAWN", VortexMobileUi.ButtonVariant.PRIMARY, false);
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

        // Row 1: Unified Action Toolbar + Custom VM Dropdown!
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolsScroll.setBackgroundColor(VortexMobileUi.C_PANEL);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(10), dp(7), dp(10), dp(7));

        String[] vmLabels = {"VM: ES6 JS", "VM: LUA 5.2", "VM: VXS"};
        String[] vmCodes = {"JS", "LUA", "VXS"};
        String initVmLabel = "JS".equalsIgnoreCase(project.scriptLanguage) ? vmLabels[0]
                : ("LUA".equalsIgnoreCase(project.scriptLanguage) ? vmLabels[1] : vmLabels[2]);
        langDropdown = new VortexMobileUi.VortexDropdown(this, vmLabels, initVmLabel);
        langDropdown.setMinimumHeight(dp(34));
        langDropdown.setPadding(dp(10), dp(6), dp(10), dp(6));
        langDropdown.setOnSelectionListener((idx, val) -> {
            syncCodeFromEditor();
            project.scriptLanguage = vmCodes[idx];
            rebuildSnippetBar();
            scriptStatusTv.setText("Switched VM to " + describeVm(project.scriptLanguage) + " — tap CHECK SYNTAX.");
            scriptStatusTv.setTextColor(VortexMobileUi.C_CYAN);
            saveProjectQuiet();
        });
        toolbar.addView(langDropdown, rightMarginLp(6));

        TextView checkBtn = VortexMobileUi.makeButton(this, "✓ CHECK", VortexMobileUi.ButtonVariant.ACCENT, true);
        checkBtn.setOnClickListener(v -> validateCurrentScript());
        toolbar.addView(checkBtn, rightMarginLp(6));

        TextView forgeBtn = VortexMobileUi.makeButton(this, "🤖 FORGE AI", VortexMobileUi.ButtonVariant.SECONDARY, true);
        forgeBtn.setOnClickListener(v -> showForgeAiDialog());
        toolbar.addView(forgeBtn, rightMarginLp(6));

        TextView objBtn = VortexMobileUi.makeButton(this, "🧊 .OBJ MESH", VortexMobileUi.ButtonVariant.SECONDARY, true);
        objBtn.setOnClickListener(v -> showCustomObjEditorDialog());
        toolbar.addView(objBtn, rightMarginLp(6));

        TextView helpBtn = VortexMobileUi.makeButton(this, "📖 API DOCS", VortexMobileUi.ButtonVariant.SECONDARY, true);
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
            TextView sb = VortexMobileUi.makeButton(this, s.replace("\n", " "), VortexMobileUi.ButtonVariant.SECONDARY, true);
            sb.setTextSize(10.8f);
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

    private void syncLangDropdownUi() {
        if (langDropdown == null || project == null) return;
        int idx = "JS".equalsIgnoreCase(project.scriptLanguage) ? 0
                : ("LUA".equalsIgnoreCase(project.scriptLanguage) ? 1 : 2);
        langDropdown.setSelectedIndex(idx, false);
        rebuildSnippetBar();
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

        TextView info = new TextView(this);
        info.setText("Inspired by hacvilke/roforge: local-first BYOK + instant Offline Generator. Automatically creates an Undo checkpoint before modifying your scene.");
        info.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        info.setTextSize(11.5f);
        form.addView(info);

        VortexMobileUi.VortexDropdown provSpin = addLabeledSpinner(form, "AI Engine Mode", VortexForgeAgent.PROVIDERS, VortexForgeAgent.PROVIDERS[0]);
        EditText keyEt = addLabeledEdit(form, "Optional API Key (blank = Offline Generator)", "", false);
        EditText promptEt = addLabeledEdit(form, "What should Forge AI build?", "2D cyber swarm arena with animated drones and auto-aim blaster", false);

        VortexMobileUi.showModal(
                this,
                "🤖 Vortex Forge AI Agent",
                form,
                "⚡ Generate (+ Undo)",
                () -> {
                    pushUndoSnapshot();
                    final int pIdx = provSpin.getSelectedItemPosition();
                    final String apiKey = keyEt.getText().toString().trim();
                    final String prompt = promptEt.getText().toString().trim();
                    new Thread(() -> {
                        try {
                            final String summary = VortexForgeAgent.applyPromptToProject(project, prompt, pIdx, apiKey);
                            runOnUiThread(() -> applyRestoredProject("🤖 " + summary));
                        } catch (Exception ex) {
                            runOnUiThread(() -> Toast.makeText(EditorActivity.this, "Forge AI error: " + ex.getMessage(), Toast.LENGTH_LONG).show());
                        }
                    }).start();
                },
                null,
                null,
                "Cancel");
    }

    private void showCustomObjEditorDialog() {
        String existing = project.customObjMeshes.isEmpty()
                ? "# Wavefront .obj vertices & faces\nv 0 1 0\nv -1 -1 1\nv 1 -1 1\nv 0 -1 -1\nf 1 2 3\nf 1 3 4\nf 1 4 2\nf 2 4 3\n"
                : project.customObjMeshes.values().iterator().next();
        EditText objEt = VortexMobileUi.createInput(this, existing, false, true);
        objEt.setTypeface(Typeface.MONOSPACE);
        objEt.setTextSize(12f);
        objEt.setMinLines(8);

        VortexMobileUi.showModal(
                this,
                "Custom 3D .OBJ Mesh Asset",
                objEt,
                "Save Mesh",
                () -> {
                    String key = project.customObjMeshes.isEmpty() ? "custom" : project.customObjMeshes.keySet().iterator().next();
                    project.customObjMeshes.put(key, objEt.getText().toString());
                    viewportSurface.loadProjectForEdit(project);
                    saveProjectQuiet();
                },
                null,
                null,
                "Cancel");
    }

    private void showApiDocsDialog() {
        String docs =
                "=== ES6+ JAVASCRIPT (Mozilla Rhino / Web V8) ===\n" +
                "  class WaveDirector { constructor() {} }\n" +
                "  function onStart() {}\n" +
                "  function onUpdate(dt) {}\n" +
                "  function onButton(btn) {} // 'A' or 'B'\n" +
                "  function onCollide(a, b, tagA, tagB) {}\n\n" +
                "Engine API (`vortex.*` or global functions):\n" +
                "  vortex.joyX(), vortex.joyY(), vortex.btnA(), vortex.btnB()\n" +
                "  vortex.setPos(n, x, y [, z]) | vortex.setVel(n, vx, vy [, vz])\n" +
                "  vortex.findNearestTag('Player', 'enemy', 30)\n" +
                "  vortex.setSpriteAnim('Player', 'knight_1,knight_2', 7)\n" +
                "  vortex.saveData('best', 500) | vortex.loadNum('best', 0)\n" +
                "  vortex.chaseTagAI('enemy', 'Player', 4.5)  // Theta* AI\n" +
                "  vortex.tweenTagYoyo('coin', 0.4, 0.9, 'SINE')\n" +
                "  vortex.raycast(ox,oy,oz, dx,dy,dz, maxDist)\n" +
                "  vortex.playMusic('CYBERPUNK'|'DUNGEON'|'BOSS'|'SKYCHILL', bpm)\n" +
                "  vortex.playSound('jump'|'coin'|'laser'|'explosion'|'hit'|'powerup')\n\n" +
                "=== LUA 5.2 (LuaJ) & VORTEXSCRIPT (.vxs) ===\n" +
                "Identical `vortex.*` methods in Lua 5.2 and direct globals in .vxs!";
        VortexMobileUi.showMessageModal(
                this,
                "Scripting API Reference (ES6 JS • Lua 5.2 • VXS)",
                docs,
                "Got It",
                null,
                null,
                null,
                null);
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

        TextView compileJsBtn = VortexMobileUi.makeButton(this, "⚡ COMPILE ➔ ES6 JS", VortexMobileUi.ButtonVariant.PRIMARY, false);
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
            syncLangDropdownUi();
            saveProjectQuiet();
            Toast.makeText(this, "✓ Compiled " + project.flowRules.size() + " Flow rules into ES6 JavaScript!", Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams cjLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cjLp.rightMargin = dp(6);
        compileRow.addView(compileJsBtn, cjLp);

        TextView compileBtn = VortexMobileUi.makeButton(this, "⚡ COMPILE ➔ LUA 5.2", VortexMobileUi.ButtonVariant.SECONDARY, false);
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
            syncLangDropdownUi();
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
        cardBg.setColor(VortexMobileUi.C_SURFACE);
        cardBg.setCornerRadius(dp(11));
        cardBg.setStroke(dp(1), VortexMobileUi.C_BORDER);
        builderCard.setBackground(cardBg);
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bcLp.topMargin = dp(10);
        bcLp.bottomMargin = dp(12);

        TextView addTitle = new TextView(this);
        addTitle.setText("➕ CREATE EVENT ➔ ACTION RULE");
        addTitle.setTextColor(VortexMobileUi.C_CYAN);
        addTitle.setTextSize(12.5f);
        addTitle.setTypeface(Typeface.DEFAULT_BOLD);
        builderCard.addView(addTitle);

        VortexMobileUi.VortexDropdown evSpin = addLabeledSpinner(builderCard, "WHEN Event Happens:",
                VortexFlowCompiler.EVENT_LABELS, VortexFlowCompiler.EVENT_LABELS[0]);

        LinearLayout evRow = new LinearLayout(this);
        evRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText evP1 = addLabeledEditCompact(evRow, "Tag A / Seconds (e.g. player)", "player");
        EditText evP2 = addLabeledEditCompact(evRow, "Tag B (e.g. coin / enemy)", "coin");
        builderCard.addView(evRow);

        VortexMobileUi.VortexDropdown actSpin = addLabeledSpinner(builderCard, "DO Game Action:",
                VortexFlowCompiler.ACTION_LABELS, VortexFlowCompiler.ACTION_LABELS[0]);

        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText actP1 = addLabeledEditCompact(actRow, "Target / Tag / Score / Preset", "Player");
        EditText actP2 = addLabeledEditCompact(actRow, "Speed / Force / SFX / BPM", "10");
        builderCard.addView(actRow);

        TextView addRuleBtn = VortexMobileUi.makeButton(this, "＋ ADD RULE TO EVENT SHEET", VortexMobileUi.ButtonVariant.ACCENT, false);
        addRuleBtn.setOnClickListener(v -> {
            pushUndoSnapshot();
            String evKey = VortexFlowCompiler.EVENT_KEYS[evSpin.getSelectedItemPosition()];
            String actKey = VortexFlowCompiler.ACTION_KEYS[actSpin.getSelectedItemPosition()];
            project.flowRules.add(new VortexFlowCompiler.FlowRule(
                    evKey, evP1.getText().toString().trim(), evP2.getText().toString().trim(),
                    actKey, actP1.getText().toString().trim(), actP2.getText().toString().trim()));
            refreshFlowRuleList();
            saveProjectQuiet();
            Toast.makeText(this, "✓ Added Flow rule!", Toast.LENGTH_SHORT).show();
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
        col.setPadding(0, dp(6), dp(6), 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        tv.setTextSize(10.5f);
        tv.setPadding(0, 0, 0, dp(3));
        col.addView(tv);
        EditText et = VortexMobileUi.createInput(this, val, false, false);
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

            TextView del = VortexMobileUi.makeButton(this, "✕", VortexMobileUi.ButtonVariant.DANGER, true);
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

        TextView commitBtn = VortexMobileUi.makeButton(this, "📤 COMMIT TO LRM", VortexMobileUi.ButtonVariant.PRIMARY, false);
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

        TextView shareBtn = VortexMobileUi.makeButton(this, "🔑 LRM SHARE KEY", VortexMobileUi.ButtonVariant.ACCENT, false);
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

        TextView joinBtn = VortexMobileUi.makeButton(this, "🌐 JOIN PORT KEY", VortexMobileUi.ButtonVariant.SECONDARY, false);
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

        TextView dropBtn = VortexMobileUi.makeButton(this, "📥 WI-FI DROP", VortexMobileUi.ButtonVariant.SECONDARY, false);
        dropBtn.setOnClickListener(v -> {
            File dir = exportUnpackedLrmRepo();
            if (dir != null) {
                dispatchOrCopyLrmTermux(dir, "( [ -d .lrm ] || lrm init --user mobile ) && lrm receive --port 8788");
            }
        });
        r2.addView(dropBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(r2);

        // Row 3: Reload Synced LRM Repo & Inbox Back into Studio!
        TextView reloadBtn = VortexMobileUi.makeButton(this, "🔄 RELOAD SYNCED LRM REPO & INBOX INTO STUDIO", VortexMobileUi.ButtonVariant.ACCENT, false);
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
        musicHead.setTextColor(VortexMobileUi.C_CYAN);
        musicHead.setTextSize(15.5f);
        musicHead.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(musicHead);

        TextView musicSub = new TextView(this);
        musicSub.setText("4-Channel Polyphonic Synth (Square Lead + Triangle Bass + Kick/Hat). Loops in Android APKs, Desktop .exe & HTML5 Web exports with zero MP3 bloat!");
        musicSub.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        musicSub.setTextSize(11.5f);
        musicSub.setPadding(0, dp(3), 0, dp(8));
        box.addView(musicSub);

        VortexMobileUi.VortexToggle musicEnableCb = new VortexMobileUi.VortexToggle(
                this, "Auto-play Chiptune Soundtrack when game starts (" + project.musicBpm + " BPM)", project.musicEnabled);
        musicEnableCb.setOnToggleListener(() -> {
            project.musicEnabled = musicEnableCb.isChecked();
            saveProjectQuiet();
        });
        box.addView(musicEnableCb);

        final TextView[] stepBtns = new TextView[16];
        final Runnable refreshStepUi = () -> {
            int[] steps = ChiptuneGenerator.parseSteps(project.musicPattern);
            for (int i = 0; i < 16; i++) {
                int idx = Math.max(0, Math.min(ChiptuneGenerator.NOTE_LABELS.length - 1, steps[i]));
                String lbl = idx == 0 ? "-" : ChiptuneGenerator.NOTE_LABELS[idx];
                if (stepBtns[i] != null) {
                    stepBtns[i].setText((i + 1) + ":" + lbl);
                    boolean active = idx > 0;
                    VortexMobileUi.applyButtonVariant(this, stepBtns[i],
                            active ? VortexMobileUi.ButtonVariant.ACCENT : VortexMobileUi.ButtonVariant.SECONDARY, true);
                }
            }
        };

        String[] genreLabels = {
                "Soundtrack Preset: ⚡ Cyberpunk Synth (134 BPM)",
                "Soundtrack Preset: 🏰 Dungeon Crawl (112 BPM)",
                "Soundtrack Preset: 🔥 Boss Overdrive (148 BPM)",
                "Soundtrack Preset: ☁️ Sky Islands Chill (118 BPM)"
        };
        String[] genrePatterns = {
                ChiptuneGenerator.PRESET_CYBERPUNK,
                ChiptuneGenerator.PRESET_DUNGEON,
                ChiptuneGenerator.PRESET_BOSS,
                ChiptuneGenerator.PRESET_SKYCHILL
        };
        int[] genreBpms = {134, 112, 148, 118};
        VortexMobileUi.VortexDropdown genreDrop = new VortexMobileUi.VortexDropdown(this, genreLabels, genreLabels[0]);
        genreDrop.setOnSelectionListener((idx, val) -> {
            pushUndoSnapshot();
            project.musicPattern = genrePatterns[idx];
            project.musicBpm = genreBpms[idx];
            project.musicEnabled = true;
            musicEnableCb.setChecked(true);
            refreshStepUi.run();
            sfxPreview.startMusicLoop(project.musicPattern, project.musicBpm);
            saveProjectQuiet();
        });
        LinearLayout.LayoutParams gdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gdLp.topMargin = dp(6);
        gdLp.bottomMargin = dp(8);
        box.addView(genreDrop, gdLp);

        // 16-Step Interactive Lead Note Grid (4 rows x 4 steps — super clean on 360dp portrait!)
        for (int r = 0; r < 4; r++) {
            LinearLayout sRow = new LinearLayout(this);
            sRow.setOrientation(LinearLayout.HORIZONTAL);
            sRow.setPadding(0, dp(3), 0, dp(3));
            for (int c = 0; c < 4; c++) {
                final int stepIdx = r * 4 + c;
                TextView sb = VortexMobileUi.makeButton(this, (stepIdx + 1) + ":-", VortexMobileUi.ButtonVariant.SECONDARY, true);
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

        TextView playLoopBtn = VortexMobileUi.makeButton(this, "▶ PREVIEW LOOP", VortexMobileUi.ButtonVariant.PRIMARY, false);
        playLoopBtn.setOnClickListener(v -> sfxPreview.startMusicLoop(project.musicPattern, project.musicBpm));
        LinearLayout.LayoutParams plLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        plLp.rightMargin = dp(6);
        transportRow.addView(playLoopBtn, plLp);

        TextView stopLoopBtn = VortexMobileUi.makeButton(this, "■ STOP MUSIC", VortexMobileUi.ButtonVariant.SECONDARY, false);
        stopLoopBtn.setOnClickListener(v -> sfxPreview.stopMusicLoop());
        transportRow.addView(stopLoopBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(transportRow);

        // --- SECTION 2: ONE-SHOT ARCADE SFX SYNTH ---
        TextView head = new TextView(this);
        head.setText("🔊 PROCEDURAL RETRO SFX SYNTHESIZER");
        head.setTextColor(VortexMobileUi.C_CYAN);
        head.setTextSize(15.5f);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(head);

        TextView desc = new TextView(this);
        desc.setText("Real-time 22.05kHz PCM wave synthesizer bundled inside every exported Android, Desktop & Web game. Tap any preset to audition or insert:");
        desc.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        desc.setTextSize(11.5f);
        desc.setPadding(0, dp(4), 0, dp(12));
        box.addView(desc);

        String[][] presets = {
                {"jump", "🚀 JUMP SWEEP", "Rising square/sine arcade jump sweep"},
                {"coin", "🪙 CRYSTAL / COIN", "Dual-tone B5 -> E6 chime pickup"},
                {"laser", "⚡ PLASMA LASER", "Fast frequency-drop sci-fi blaster"},
                {"explosion", "💥 EXPLOSION", "Low-rumble white-noise burst"},
                {"hit", "🛡 DAMAGE / HIT", "Crunch impact feedback"},
                {"powerup", "✨ LEVEL CLEAR / WARP", "4-note ascending arpeggio fanfare"}
        };

        for (String[] pr : presets) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(VortexMobileUi.C_SURFACE);
            bg.setCornerRadius(dp(10));
            bg.setStroke(dp(1), VortexMobileUi.C_BORDER);
            row.setBackground(bg);

            LinearLayout txtCol = new LinearLayout(this);
            txtCol.setOrientation(LinearLayout.VERTICAL);
            TextView t1 = new TextView(this);
            t1.setText(pr[1]);
            t1.setTextColor(VortexMobileUi.C_TEXT);
            t1.setTypeface(Typeface.DEFAULT_BOLD);
            t1.setTextSize(13f);
            txtCol.addView(t1);

            TextView t2 = new TextView(this);
            t2.setText(pr[2]);
            t2.setTextColor(VortexMobileUi.C_TEXT_MUTED);
            t2.setTextSize(11f);
            txtCol.addView(t2);
            row.addView(txtCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView playBtn = VortexMobileUi.makeButton(this, "▶ PLAY", VortexMobileUi.ButtonVariant.ACCENT, true);
            playBtn.setOnClickListener(v -> sfxPreview.play(pr[0]));
            row.addView(playBtn, rightMarginLp(6));

            TextView insBtn = VortexMobileUi.makeButton(this, "＋ CODE", VortexMobileUi.ButtonVariant.SECONDARY, true);
            insBtn.setOnClickListener(v -> {
                String call = "JS".equalsIgnoreCase(project.scriptLanguage)
                        ? "\n  vortex.playSound('" + pr[0] + "');\n"
                        : ("LUA".equalsIgnoreCase(project.scriptLanguage)
                        ? "\n    vortex.playSound('" + pr[0] + "')\n"
                        : "\n    playSound('" + pr[0] + "');\n");
                scriptEditor.getText().append(call);
                Toast.makeText(this, "✓ Appended " + pr[0] + " to Script!", Toast.LENGTH_SHORT).show();
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
        TextView webExportBtn = VortexMobileUi.makeButton(this, "🌐 EXPORT PLAYABLE HTML5 WEB GAME (.HTML)", VortexMobileUi.ButtonVariant.ACCENT, false);
        webExportBtn.setOnClickListener(v -> exportPlayableHtml5Web());
        LinearLayout.LayoutParams webBtnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        webBtnLp.bottomMargin = dp(10);
        box.addView(webExportBtn, webBtnLp);

        EditText titleEt = addLabeledEdit(box, "APK Display Name", project.title, false);
        EditText pkgEt = addLabeledEdit(box, "Android Package ID (unique per game)", project.packageName, false);
        EditText verEt = addLabeledEdit(box, "Version Name", project.versionName, false);

        String[] orients = {"landscape", "portrait", "sensor"};
        VortexMobileUi.VortexDropdown orientSpin = addLabeledSpinner(box, "Screen Orientation", orients, project.orientation);

        String[] glyphs = {"ROCKET", "CUBE", "SWORD", "STAR"};
        VortexMobileUi.VortexDropdown glyphSpin = addLabeledSpinner(box, "Procedural App Icon Style", glyphs, project.iconGlyph);

        VortexMobileUi.VortexToggle bgBuildCb = new VortexMobileUi.VortexToggle(this, "Run 1-Tap Termux build silently in background", true);
        box.addView(bgBuildCb);

        VortexMobileUi.VortexToggle fromSourceCb = new VortexMobileUi.VortexToggle(this, "Compile full Engine + Rhino ES6 + LuaJ + JBox2D Java sources", false);
        box.addView(fromSourceCb);

        // Primary Universal Build Button (Unified Signature Cyan CTA!)
        TextView copyExportBtn = VortexMobileUi.makeButton(this, "🚀 EXPORT + COPY CMD (OPEN TERMUX)", VortexMobileUi.ButtonVariant.PRIMARY, false);
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

        TextView oneTapBtn = VortexMobileUi.makeButton(this, "⚡ 1-TAP BUILD", VortexMobileUi.ButtonVariant.ACCENT, false);
        oneTapBtn.setOnClickListener(v -> {
            applyExportFields(titleEt, pkgEt, verEt, orients[orientSpin.getSelectedItemPosition()], glyphs[glyphSpin.getSelectedItemPosition()]);
            runExportAndBuild(true, bgBuildCb.isChecked(), fromSourceCb.isChecked());
        });
        LinearLayout.LayoutParams otLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        otLp.rightMargin = dp(6);
        row2.addView(oneTapBtn, otLp);

        TextView permSettingsBtn = VortexMobileUi.makeButton(this, "🔓 PERMISSIONS", VortexMobileUi.ButtonVariant.SECONDARY, false);
        permSettingsBtn.setOnClickListener(v -> TermuxBridge.openAppPermissionSettings(this));
        row2.addView(permSettingsBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(row2);

        // Row 3: Shared Storage + Install Built APK
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.setPadding(0, dp(8), 0, 0);

        TextView storageBtn = VortexMobileUi.makeButton(this, "📂 STORAGE", VortexMobileUi.ButtonVariant.SECONDARY, false);
        storageBtn.setOnClickListener(v -> TermuxBridge.requestAllFilesAccess(this, 402));
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        stLp.rightMargin = dp(6);
        row3.addView(storageBtn, stLp);

        TextView installBtn = VortexMobileUi.makeButton(this, "📲 INSTALL APK", VortexMobileUi.ButtonVariant.SECONDARY, false);
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
            VortexMobileUi.applyButtonVariant(this, tb,
                    active ? VortexMobileUi.ButtonVariant.PRIMARY : VortexMobileUi.ButtonVariant.SECONDARY, true);
            if (active && topTabScroll != null) {
                topTabScroll.post(() -> {
                    int targetX = (tb.getLeft() + tb.getRight()) / 2 - topTabScroll.getWidth() / 2;
                    topTabScroll.smoothScrollTo(Math.max(0, targetX), 0);
                });
            }
        }

        sceneBottomToolbar.setVisibility("SCENE".equals(mode) ? View.VISIBLE : View.GONE);
        inspectorDrawer.setVisibility("INSPECTOR".equals(mode) ? View.VISIBLE : View.GONE);
        flowPanel.setVisibility("FLOW".equals(mode) ? View.VISIBLE : View.GONE);
        lrmPanel.setVisibility("LRM".equals(mode) ? View.VISIBLE : View.GONE);
        pixelArtPanel.setVisibility("PIXEL".equals(mode) ? View.VISIBLE : View.GONE);
        scriptPanel.setVisibility("SCRIPT".equals(mode) ? View.VISIBLE : View.GONE);
        sfxPanel.setVisibility("SFX".equals(mode) ? View.VISIBLE : View.GONE);
        buildPanel.setVisibility("BUILD".equals(mode) ? View.VISIBLE : View.GONE);

        View activePanel = null;
        if ("SCENE".equals(mode)) activePanel = sceneBottomToolbar;
        else if ("INSPECTOR".equals(mode)) activePanel = inspectorDrawer;
        else if ("FLOW".equals(mode)) activePanel = flowPanel;
        else if ("LRM".equals(mode)) activePanel = lrmPanel;
        else if ("PIXEL".equals(mode)) activePanel = pixelArtPanel;
        else if ("SCRIPT".equals(mode)) activePanel = scriptPanel;
        else if ("SFX".equals(mode)) activePanel = sfxPanel;
        else if ("BUILD".equals(mode)) activePanel = buildPanel;
        if (activePanel != null) {
            VortexMobileUi.animateEntrance(activePanel, 0L);
        }

        if ("FLOW".equals(mode)) refreshFlowRuleList();

        if ("PLAY".equals(mode)) {
            if (!validateCurrentScript()) {
                Toast.makeText(this, "Script syntax error — check Code tab!", Toast.LENGTH_LONG).show();
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
        tv.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        tv.setTextSize(11.5f);
        tv.setPadding(0, dp(8), 0, dp(3));
        parent.addView(tv);

        EditText et = VortexMobileUi.createInput(this, val, numeric, false);
        parent.addView(et);
        return et;
    }

    private EditText addCompactNum(LinearLayout row, String label, float val) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(6), dp(5), 0);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        tv.setTextSize(10.5f);
        tv.setPadding(0, 0, 0, dp(3));
        col.addView(tv);

        EditText et = VortexMobileUi.createInput(this, String.format(Locale.US, "%.2f", val), true, false);
        col.addView(et);

        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return et;
    }

    private VortexMobileUi.VortexDropdown addLabeledSpinner(LinearLayout parent, String label, String[] items, String current) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(VortexMobileUi.C_TEXT_MUTED);
        tv.setTextSize(11.5f);
        tv.setPadding(0, dp(8), 0, dp(3));
        parent.addView(tv);

        VortexMobileUi.VortexDropdown dd = new VortexMobileUi.VortexDropdown(this, items, current);
        parent.addView(dd);
        return dd;
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
