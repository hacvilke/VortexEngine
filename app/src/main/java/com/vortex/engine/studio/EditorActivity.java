package com.vortex.engine.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.render.VortexGLRenderer;
import com.vortex.engine.core.script.VortexScriptEngine;
import com.vortex.engine.core.ui.VortexGameSurfaceView;
import com.vortex.engine.runtime.StandaloneGameActivity;
import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * Full On-Device 2D & 3D Game Studio IDE:
 *  - Mode 0: Interactive OpenGL ES 2D/3D Scene Viewport + Gizmo + Quick Entity Spawner
 *  - Mode 1: Entity Hierarchy + Transform / Material / Physics / World Inspector
 *  - Mode 2: VortexScript (.vxs) Code IDE + Syntax Validator + Custom .OBJ 3D Mesh Editor
 *  - Mode 3: Instant 60 FPS Live Play Preview w/ Virtual Gamepad, Synth Audio & Console
 *  - Mode 4: Termux Standalone APK Builder (RUN_COMMAND Service + Terminal + Clipboard Fallback)
 */
public class EditorActivity extends Activity {

    public static final String EXTRA_PROJECT_ID = "extra_project_id";

    private GameProject project;
    private VortexGameSurfaceView gameSurface;

    // Top Navigation Tabs
    private TextView tabScene, tabInspector, tabScript, tabPlay, tabBuild;
    private int currentTab = 0;

    // Scene Overlay Widgets
    private LinearLayout sceneOverlay;
    private TextView selectionBanner;
    private TextView snapToggleBtn;
    private TextView dimModeBtn;

    // Play Overlay Widgets
    private LinearLayout playOverlay;
    private TextView consoleOverlayTv;

    // Inspector Panel
    private ScrollView inspectorPanel;
    private LinearLayout hierarchyChipsLayout;
    private EditText edName, edTag, edCustomObj, edX, edY, edZ, edSX, edSY, edSZ, edRY, edRZ, edSpinY, edColor, edBounce, edGravScale;
    private Spinner spPrimitive, spBodyType;
    private CheckBox cbTrigger, cbChecker, cbEmissive;
    private EditText edWorldSky, edWorldGravityY, edCamDist, edCamHeight, edZoom2D;

    // Script IDE Panel
    private LinearLayout scriptPanel;
    private EditText codeEditor;
    private TextView syntaxStatusTv;

    // Termux APK Builder Panel
    private ScrollView buildPanel;
    private EditText edBuildTitle, edBuildPkg, edBuildVerName, edBuildVerCode, edIconAccent;
    private Spinner spOrientation, spIconGlyph;
    private CheckBox cbCompileJavaSource;
    private ImageView iconPreviewImg;
    private TextView buildConsoleTv;
    private TermuxBridge.ExportResult lastExport;

    private final StringBuilder runtimeConsoleBuffer = new StringBuilder();

    private static final String[] PRIMITIVES_ALL = {
            "CUBE", "SPHERE", "PYRAMID", "CYLINDER", "PLANE", "CUSTOM_OBJ",
            "RECT", "CIRCLE", "TRIANGLE", "SPRITE_HERO", "SPRITE_SHIP", "SPRITE_SLIME", "SPRITE_COIN"
    };
    private static final String[] BODY_TYPES = {"STATIC", "DYNAMIC", "KINEMATIC", "NONE"};
    private static final String[] ORIENTATIONS = {"landscape", "portrait", "sensor"};
    private static final String[] ICON_GLYPHS = {"CUBE", "ROCKET", "STAR"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pid = getIntent() != null ? getIntent().getStringExtra(EXTRA_PROJECT_ID) : null;
        project = ProjectRepository.loadProject(this, pid);
        if (project == null) {
            project = ProjectRepository.listProjects(this).get(0);
        }

        if (!project.entities.isEmpty()) {
            // Preselect first entity
        }
        buildStudioLayout();

        if (!project.entities.isEmpty()) {
            Entity first = project.entities.get(0);
            gameSurface.renderer.setSelectedEntityId(first.id);
        }
        gameSurface.loadProjectForEdit(project);
        switchTab(0);

        TermuxResultService.setListener((exitCode, stdout, stderr, errmsg) -> {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== Termux Build Result (exitCode=").append(exitCode).append(") ===\n");
            if (stdout != null && !stdout.isEmpty()) sb.append(stdout).append("\n");
            if (stderr != null && !stderr.isEmpty()) sb.append("[stderr]\n").append(stderr).append("\n");
            if (errmsg != null && !errmsg.isEmpty()) sb.append("[errmsg] ").append(errmsg).append("\n");
            if (exitCode == 0 && lastExport != null) {
                sb.append("\n[READY] Tap '📲 INSTALL BUILT APK' above to install ")
                        .append(lastExport.expectedSharedApkPath).append("!\n");
            }
            appendBuildLog(sb.toString());
        });
    }

    private void buildStudioLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B0F17"));

        // 1. Top Studio Bar
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(Color.parseColor("#121924"));
        topBar.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView backBtn = makeChip("← HUB", "#1E293B", "#CFD8DC");
        backBtn.setOnClickListener(v -> {
            saveCurrentState();
            finish();
        });
        topBar.addView(backBtn);

        dimModeBtn = makeChip(project.dimensionMode, "#311B92", "#00E5FF");
        dimModeBtn.setOnClickListener(v -> toggle2D3DMode());
        LinearLayout.LayoutParams dmLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dmLp.leftMargin = dp(6);
        topBar.addView(dimModeBtn, dmLp);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setPadding(dp(8), 0, dp(8), 0);

        tabScene = makeTabBtn("🌐 SCENE", 0);
        tabInspector = makeTabBtn("🎛 INSPECTOR", 1);
        tabScript = makeTabBtn("📜 SCRIPT", 2);
        tabPlay = makeTabBtn("▶ PLAY", 3);
        tabBuild = makeTabBtn("📦 BUILD APK", 4);

        tabRow.addView(tabScene);
        tabRow.addView(tabInspector);
        tabRow.addView(tabScript);
        tabRow.addView(tabPlay);
        tabRow.addView(tabBuild);
        tabScroll.addView(tabRow);

        topBar.addView(tabScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView saveBtn = makeChip("💾 SAVE", "#1B3B2B", "#00E676");
        saveBtn.setOnClickListener(v -> {
            saveCurrentState();
            Toast.makeText(this, "Project saved!", Toast.LENGTH_SHORT).show();
        });
        topBar.addView(saveBtn);

        root.addView(topBar);

        // 2. Main Workspace Stack
        FrameLayout workspace = new FrameLayout(this);

        // OpenGL ES 2D/3D Viewport (shared by Scene Mode & Play Mode!)
        gameSurface = new VortexGameSurfaceView(this);
        gameSurface.setSelectionListener(new VortexGameSurfaceView.SelectionListener() {
            @Override
            public void onEntitySelected(Entity entity) {
                runOnUiThread(() -> updateSelectionBanner(entity));
            }

            @Override
            public void onEntityMoved(Entity entity) {
                runOnUiThread(() -> updateSelectionBanner(entity));
            }
        });
        gameSurface.renderer.setTelemetryListener(new VortexGLRenderer.TelemetryListener() {
            @Override
            public void onHudUpdate(int fps, int entityCount, int score, int health, String banner) {
                gameSurface.controlsOverlay.updateHud(fps, score, health, banner);
            }

            @Override
            public void onScriptLog(String line) {
                synchronized (runtimeConsoleBuffer) {
                    runtimeConsoleBuffer.append(line).append("\n");
                    if (runtimeConsoleBuffer.length() > 800) {
                        runtimeConsoleBuffer.delete(0, runtimeConsoleBuffer.length() - 800);
                    }
                }
                runOnUiThread(() -> {
                    if (consoleOverlayTv != null) {
                        synchronized (runtimeConsoleBuffer) {
                            consoleOverlayTv.setText(runtimeConsoleBuffer.toString().trim());
                        }
                    }
                });
            }
        });
        workspace.addView(gameSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Build overlays & panels
        sceneOverlay = buildSceneOverlay();
        playOverlay = buildPlayOverlay();
        inspectorPanel = buildInspectorPanel();
        scriptPanel = buildScriptPanel();
        buildPanel = buildApkCompilerPanel();

        workspace.addView(sceneOverlay);
        workspace.addView(playOverlay);
        workspace.addView(inspectorPanel);
        workspace.addView(scriptPanel);
        workspace.addView(buildPanel);

        root.addView(workspace, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private LinearLayout buildSceneOverlay() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Floating Quick Action Bar
        HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView addEntBtn = makeChip("+ ADD ENTITY", "#00ACC1", "#051016");
        addEntBtn.setOnClickListener(v -> showAddEntityDialog());
        tools.addView(addEntBtn);

        snapToggleBtn = makeChip("SNAP: ON", "#1C2838", "#00E5FF");
        snapToggleBtn.setOnClickListener(v -> {
            boolean next = !gameSurface.isGridSnap();
            gameSurface.setGridSnap(next);
            snapToggleBtn.setText(next ? "SNAP: ON" : "SNAP: OFF");
        });
        addSpaced(tools, snapToggleBtn);

        TextView resetCamBtn = makeChip("🎥 RESET CAM", "#1C2838", "#CFD8DC");
        resetCamBtn.setOnClickListener(v -> gameSurface.renderer.resetEditorCamera());
        addSpaced(tools, resetCamBtn);

        TextView dupBtn = makeChip("⧉ DUPLICATE", "#1C2838", "#FFD54F");
        dupBtn.setOnClickListener(v -> duplicateSelectedEntity());
        addSpaced(tools, dupBtn);

        TextView delBtn = makeChip("🗑 DELETE", "#2A1820", "#FF5252");
        delBtn.setOnClickListener(v -> deleteSelectedEntity());
        addSpaced(tools, delBtn);

        toolsScroll.addView(tools);
        overlay.addView(toolsScroll);

        // Spacer so touch events pass directly through to GLSurfaceView!
        View transparentSpace = new View(this);
        transparentSpace.setClickable(false);
        transparentSpace.setFocusable(false);
        overlay.addView(transparentSpace, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom Selection Status Pill
        selectionBanner = new TextView(this);
        selectionBanner.setText("Tap any 2D/3D object to select • Drag to orbit camera • Pinch to zoom");
        selectionBanner.setTextColor(Color.parseColor("#E0F7FA"));
        selectionBanner.setTextSize(12f);
        selectionBanner.setPadding(dp(14), dp(8), dp(14), dp(8));
        selectionBanner.setBackgroundColor(Color.argb(185, 14, 20, 30));
        selectionBanner.setOnClickListener(v -> switchTab(1));
        overlay.addView(selectionBanner);

        return overlay;
    }

    private LinearLayout buildPlayOverlay() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(12), dp(88), dp(12), dp(12));
        overlay.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.END);

        TextView restartBtn = makeChip("↻ RESTART", "#1E293B", "#00E5FF");
        restartBtn.setOnClickListener(v -> {
            runtimeConsoleBuffer.setLength(0);
            if (consoleOverlayTv != null) consoleOverlayTv.setText("");
            gameSurface.startPlayMode(project.deepCopy());
        });
        bar.addView(restartBtn);

        TextView fullBtn = makeChip("⛶ FULLSCREEN", "#1E293B", "#FFD54F");
        fullBtn.setOnClickListener(v -> {
            saveCurrentState();
            Intent i = new Intent(this, StandaloneGameActivity.class);
            i.putExtra(StandaloneGameActivity.EXTRA_PROJECT_JSON, project.toJsonString());
            startActivity(i);
        });
        addSpaced(bar, fullBtn);

        TextView stopBtn = makeChip("■ STOP", "#3B1820", "#FF5252");
        stopBtn.setOnClickListener(v -> switchTab(0));
        addSpaced(bar, stopBtn);

        overlay.addView(bar);

        consoleOverlayTv = new TextView(this);
        consoleOverlayTv.setTextColor(Color.parseColor("#80CBC4"));
        consoleOverlayTv.setTextSize(11f);
        consoleOverlayTv.setTypeface(Typeface.MONOSPACE);
        consoleOverlayTv.setPadding(dp(8), dp(6), dp(8), dp(6));
        overlay.addView(consoleOverlayTv);

        return overlay;
    }

    private ScrollView buildInspectorPanel() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0E141F"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(14), dp(16), dp(28));

        col.addView(sectionHeader("SCENE HIERARCHY (TAP TO SELECT)"));
        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hierarchyChipsLayout = new LinearLayout(this);
        hierarchyChipsLayout.setOrientation(LinearLayout.HORIZONTAL);
        hScroll.addView(hierarchyChipsLayout);
        col.addView(hScroll);

        col.addView(sectionHeader("SELECTED ENTITY PROPERTIES"));

        LinearLayout r1 = twoColRow(
                edName = inputField("Name"),
                edTag = inputField("Tag (player/solid/coin/enemy/hazard/goal)")
        );
        col.addView(r1);

        spPrimitive = new Spinner(this);
        spPrimitive.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, PRIMITIVES_ALL));
        edCustomObj = inputField("Custom .obj Name (e.g. starfighter)");
        col.addView(twoColRow(spPrimitive, edCustomObj));

        col.addView(fieldLabel("Position (X, Y, Z)"));
        col.addView(threeColRow(
                edX = numField("X"), edY = numField("Y"), edZ = numField("Z")
        ));

        col.addView(fieldLabel("Scale / Size (SX, SY, SZ)"));
        col.addView(threeColRow(
                edSX = numField("SX"), edSY = numField("SY"), edSZ = numField("SZ")
        ));

        col.addView(fieldLabel("Rotation Y, Rotation Z, Auto-Spin Y (deg/s)"));
        col.addView(threeColRow(
                edRY = numField("RotY"), edRZ = numField("RotZ"), edSpinY = numField("SpinY")
        ));

        col.addView(fieldLabel("Material Color (#RRGGBB) & Visual Flags"));
        edColor = inputField("#00E5FF");
        col.addView(edColor);

        // Quick Neon Swatches
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        String[] palette = {"#00E5FF", "#00E676", "#FFD740", "#FF4081", "#7C4DFF", "#FF6E40", "#26A69A", "#ECEFF1"};
        for (final String hex : palette) {
            View sw = new View(this);
            sw.setBackground(roundRect(hex, "#FFFFFF", dp(6)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(26));
            lp.rightMargin = dp(8);
            lp.topMargin = dp(4);
            lp.bottomMargin = dp(6);
            sw.setOnClickListener(v -> edColor.setText(hex));
            swatches.addView(sw, lp);
        }
        col.addView(swatches);

        LinearLayout flagsRow = new LinearLayout(this);
        flagsRow.setOrientation(LinearLayout.HORIZONTAL);
        cbChecker = new CheckBox(this); cbChecker.setText("Checker Grid"); cbChecker.setTextColor(Color.WHITE);
        cbEmissive = new CheckBox(this); cbEmissive.setText("Emissive Glow"); cbEmissive.setTextColor(Color.WHITE);
        cbTrigger = new CheckBox(this); cbTrigger.setText("Is Trigger"); cbTrigger.setTextColor(Color.WHITE);
        flagsRow.addView(cbChecker);
        flagsRow.addView(cbEmissive);
        flagsRow.addView(cbTrigger);
        col.addView(flagsRow);

        col.addView(fieldLabel("Physics BodyType, Bounce & Gravity Scale"));
        spBodyType = new Spinner(this);
        spBodyType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, BODY_TYPES));
        edBounce = numField("Bounce");
        edGravScale = numField("GravScale");
        col.addView(threeColRow(spBodyType, edBounce, edGravScale));

        TextView applyEntBtn = makeChip("✓ APPLY ENTITY CHANGES", "#00ACC1", "#051016");
        applyEntBtn.setGravity(Gravity.CENTER);
        applyEntBtn.setOnClickListener(v -> {
            applyInspectorToSelectedEntity();
            Toast.makeText(this, "Entity updated!", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        apLp.topMargin = dp(10);
        col.addView(applyEntBtn, apLp);

        // World & Camera Section
        col.addView(sectionHeader("WORLD & CAMERA SETTINGS"));
        col.addView(fieldLabel("Sky Hex Color & Gravity Y"));
        col.addView(twoColRow(
                edWorldSky = inputField("#080C1A"),
                edWorldGravityY = numField("-22.0")
        ));
        col.addView(fieldLabel("3D Cam Distance, 3D Cam Height & 2D Ortho Zoom"));
        col.addView(threeColRow(
                edCamDist = numField("10.0"),
                edCamHeight = numField("5.0"),
                edZoom2D = numField("18.0")
        ));

        TextView applyWorldBtn = makeChip("✓ APPLY WORLD SETTINGS", "#1E293B", "#00E5FF");
        applyWorldBtn.setGravity(Gravity.CENTER);
        applyWorldBtn.setOnClickListener(v -> {
            applyWorldSettings();
            Toast.makeText(this, "World settings updated!", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams awLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        awLp.topMargin = dp(8);
        col.addView(applyWorldBtn, awLp);

        sv.addView(col);
        return sv;
    }

    private LinearLayout buildScriptPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(Color.parseColor("#0B0F17"));
        panel.setPadding(dp(12), dp(10), dp(12), dp(12));

        // Top Script Toolbar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView checkBtn = makeChip("✓ CHECK SYNTAX", "#1B3B2B", "#00E676");
        checkBtn.setOnClickListener(v -> validateCurrentScript());
        bar.addView(checkBtn);

        TextView objBtn = makeChip("🧊 .OBJ MESHES", "#281E46", "#B388FF");
        objBtn.setOnClickListener(v -> showObjMeshEditorDialog());
        addSpaced(bar, objBtn);

        TextView apiBtn = makeChip("📖 API DOCS", "#1E293B", "#00E5FF");
        apiBtn.setOnClickListener(v -> showApiDocsDialog());
        addSpaced(bar, apiBtn);

        syntaxStatusTv = new TextView(this);
        syntaxStatusTv.setText("  main.vxs");
        syntaxStatusTv.setTextColor(Color.parseColor("#90A4AE"));
        syntaxStatusTv.setTextSize(12f);
        bar.addView(syntaxStatusTv);

        panel.addView(bar);

        // Quick Snippet Bar
        HorizontalScrollView snipScroll = new HorizontalScrollView(this);
        snipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout snips = new LinearLayout(this);
        snips.setOrientation(LinearLayout.HORIZONTAL);
        snips.setPadding(0, dp(8), 0, dp(8));

        String[][] snippets = {
                {"jump()", "jump('Player', 12.0);\n"},
                {"setVel()", "setVel('Player', joyX() * 9.0, getVY('Player'), 0);\n"},
                {"spawn3D()", "spawn3D('Cube_1', 'solid', 'CUBE', 0, 2, -5, 1, 1, 1, '#00E5FF', 'DYNAMIC');\n"},
                {"spawn2D()", "spawn2D('Laser', 'projectile', 'RECT', getX('Player'), getY('Player') + 1, 0.3, 0.8, '#00E676', 'KINEMATIC');\n"},
                {"particles()", "particles(getX('Player'), getY('Player'), getZ('Player'), 25, '#FFD740');\n"},
                {"playSound()", "playSound('coin');\n"},
                {"if {...}", "if (btnA()) {\n    \n}\n"}
        };
        for (final String[] sn : snippets) {
            TextView b = makeChip(sn[0], "#162030", "#80DEEA");
            b.setOnClickListener(v -> insertCodeSnippet(sn[1]));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            snips.addView(b, lp);
        }
        snipScroll.addView(snips);
        panel.addView(snipScroll);

        // Monospace Code Editor
        codeEditor = new EditText(this);
        codeEditor.setTypeface(Typeface.MONOSPACE);
        codeEditor.setTextSize(13.5f);
        codeEditor.setTextColor(Color.parseColor("#E6EDF3"));
        codeEditor.setBackground(roundRect("#0D131E", "#223046", dp(10)));
        codeEditor.setGravity(Gravity.TOP | Gravity.START);
        codeEditor.setPadding(dp(12), dp(12), dp(12), dp(12));
        codeEditor.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeEditor.setText(project.scriptCode);

        panel.addView(codeEditor, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        return panel;
    }

    private ScrollView buildApkCompilerPanel() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Color.parseColor("#0D121C"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(16), dp(18), dp(28));

        col.addView(sectionHeader("STANDALONE ANDROID APK BUILDER (TERMUX)"));

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        iconPreviewImg = new ImageView(this);
        LinearLayout.LayoutParams ipLp = new LinearLayout.LayoutParams(dp(72), dp(72));
        ipLp.rightMargin = dp(14);
        topRow.addView(iconPreviewImg, ipLp);

        LinearLayout metaCol = new LinearLayout(this);
        metaCol.setOrientation(LinearLayout.VERTICAL);
        metaCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        edBuildTitle = inputField("Game App Name");
        edBuildPkg = inputField("Package ID (e.g. com.user.mygame)");
        metaCol.addView(edBuildTitle);
        metaCol.addView(edBuildPkg);
        topRow.addView(metaCol);

        col.addView(topRow);

        col.addView(fieldLabel("Version Name, Version Code & Screen Orientation"));
        edBuildVerName = inputField("1.0.0");
        edBuildVerCode = numField("1");
        spOrientation = new Spinner(this);
        spOrientation.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ORIENTATIONS));
        col.addView(threeColRow(edBuildVerName, edBuildVerCode, spOrientation));

        col.addView(fieldLabel("Launcher Icon Accent Hex & Glyph"));
        edIconAccent = inputField("#00E5FF");
        spIconGlyph = new Spinner(this);
        spIconGlyph.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ICON_GLYPHS));
        TextView refreshIconBtn = makeChip("↻ PREVIEW ICON", "#1E293B", "#00E5FF");
        refreshIconBtn.setOnClickListener(v -> refreshIconPreview());
        col.addView(threeColRow(edIconAccent, spIconGlyph, refreshIconBtn));

        cbCompileJavaSource = new CheckBox(this);
        cbCompileJavaSource.setText("Compile full Engine Java source with javac + d8 (unchecked = ultra-fast 3s prebuilt runtime DEX)");
        cbCompileJavaSource.setTextColor(Color.parseColor("#B0BEC5"));
        col.addView(cbCompileJavaSource);

        // Build Action Buttons
        TextView btnDirectBg = makeChip("⚡ ONE-TAP BUILD APK IN TERMUX (RUN_COMMAND)", "#00C853", "#041208");
        btnDirectBg.setGravity(Gravity.CENTER);
        btnDirectBg.setPadding(dp(16), dp(14), dp(16), dp(14));
        btnDirectBg.setOnClickListener(v -> runExportAndTermuxBuild(true, true));
        LinearLayout.LayoutParams b1Lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        b1Lp.topMargin = dp(12);
        col.addView(btnDirectBg, b1Lp);

        LinearLayout dualRow = new LinearLayout(this);
        dualRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams drLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drLp.topMargin = dp(8);
        dualRow.setLayoutParams(drLp);

        TextView btnLiveTerm = makeChip("🖥 BUILD IN TERMUX SESSION", "#1E293B", "#00E5FF");
        btnLiveTerm.setGravity(Gravity.CENTER);
        btnLiveTerm.setOnClickListener(v -> runExportAndTermuxBuild(true, false));
        dualRow.addView(btnLiveTerm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnFallback = makeChip("📋 EXPORT + COPY CMD", "#281E46", "#FFD54F");
        btnFallback.setGravity(Gravity.CENTER);
        btnFallback.setOnClickListener(v -> runExportAndTermuxBuild(false, false));
        LinearLayout.LayoutParams bfLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bfLp.leftMargin = dp(8);
        dualRow.addView(btnFallback, bfLp);

        col.addView(dualRow);

        TextView btnInstallApk = makeChip("📲 INSTALL BUILT GAME APK", "#00ACC1", "#051016");
        btnInstallApk.setGravity(Gravity.CENTER);
        btnInstallApk.setPadding(dp(16), dp(13), dp(16), dp(13));
        btnInstallApk.setOnClickListener(v -> installLatestBuiltApk());
        LinearLayout.LayoutParams biLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        biLp.topMargin = dp(8);
        col.addView(btnInstallApk, biLp);

        col.addView(sectionHeader("BUILD & EXPORT CONSOLE"));
        buildConsoleTv = new TextView(this);
        buildConsoleTv.setTypeface(Typeface.MONOSPACE);
        buildConsoleTv.setTextSize(12f);
        buildConsoleTv.setTextColor(Color.parseColor("#80CBC4"));
        buildConsoleTv.setBackground(roundRect("#080C12", "#1D2A3A", dp(10)));
        buildConsoleTv.setPadding(dp(12), dp(12), dp(12), dp(12));
        buildConsoleTv.setText("Ready to export & compile your 2D/3D game into a standalone signed Android .apk.\n"
                + "• Direct Mode uses com.termux.RUN_COMMAND.\n"
                + "• Script Fallback exports to /sdcard/Download/ and copies the exact Termux build command.");
        col.addView(buildConsoleTv);

        sv.addView(col);
        return sv;
    }

    // ===================== TAB & STATE SWITCHING =====================

    private void switchTab(int index) {
        // Persist edits when leaving Inspector / Script / Build tabs
        if (currentTab == 2 && codeEditor != null) {
            project.scriptCode = codeEditor.getText().toString();
        }
        if (currentTab == 3 && index != 3) {
            gameSurface.stopPlayMode(project);
        }

        currentTab = index;
        styleTab(tabScene, index == 0);
        styleTab(tabInspector, index == 1);
        styleTab(tabScript, index == 2);
        styleTab(tabPlay, index == 3);
        styleTab(tabBuild, index == 4);

        sceneOverlay.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        inspectorPanel.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        scriptPanel.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        playOverlay.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        buildPanel.setVisibility(index == 4 ? View.VISIBLE : View.GONE);

        if (index == 0) {
            Entity sel = project.findById(gameSurface.renderer.getSelectedEntityId());
            updateSelectionBanner(sel);
        } else if (index == 1) {
            populateInspector();
        } else if (index == 2) {
            codeEditor.setText(project.scriptCode);
            validateCurrentScript();
        } else if (index == 3) {
            saveCurrentState();
            runtimeConsoleBuffer.setLength(0);
            consoleOverlayTv.setText("");
            gameSurface.startPlayMode(project.deepCopy());
        } else if (index == 4) {
            populateBuildPanel();
        }
    }

    private void toggle2D3DMode() {
        project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
        dimModeBtn.setText(project.dimensionMode);
        ProjectRepository.saveProject(this, project);
        Toast.makeText(this, "Switched viewport to " + project.dimensionMode + " mode", Toast.LENGTH_SHORT).show();
    }

    private void updateSelectionBanner(Entity e) {
        if (selectionBanner == null) return;
        if (e == null) {
            selectionBanner.setText("No entity selected • Tap an object to select • Drag empty space to orbit/pan");
        } else {
            selectionBanner.setText(String.format(Locale.US,
                    "Selected: %s [%s • %s]  Pos(%.1f, %.1f, %.1f) — Drag again to move or tap for Inspector",
                    e.name, e.primitive, e.tag, e.x, e.y, e.z));
        }
    }

    // ===================== SCENE ENTITY OPERATIONS =====================

    private void showAddEntityDialog() {
        final boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        final String[] presets = is3D
                ? new String[]{
                        "3D Cube (Dynamic)", "3D Floor / Platform (Static)", "3D Sphere",
                        "3D Pyramid Hazard", "3D Cylinder Pillar", "3D Collectible Crystal", "3D Custom .OBJ Mesh"
                }
                : new String[]{
                        "2D Platform (Static)", "2D Hero Sprite (Dynamic)", "2D Slime Enemy",
                        "2D Spaceship Sprite", "2D Gold Coin (Trigger)", "2D Circle Ball"
                };

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Add " + project.dimensionMode + " Entity")
                .setItems(presets, (d, which) -> {
                    int idx = project.entities.size() + 1;
                    Entity ne;
                    if (is3D) {
                        switch (which) {
                            case 1:
                                ne = Entity.create3D("Platform_" + idx, "solid", "CUBE", 0, 0, -4, 5, 0.8f, 5, "#26A69A", "STATIC");
                                ne.checkerTexture = true;
                                break;
                            case 2:
                                ne = Entity.create3D("Sphere_" + idx, "decor", "SPHERE", 0, 2, -3, 1.2f, 1.2f, 1.2f, "#7C4DFF", "DYNAMIC");
                                ne.bounce = 0.6f;
                                break;
                            case 3:
                                ne = Entity.create3D("Spike_" + idx, "hazard", "PYRAMID", 0, 1, -4, 1.5f, 1.8f, 1.5f, "#FF4081", "STATIC");
                                ne.emissive = true;
                                break;
                            case 4:
                                ne = Entity.create3D("Pillar_" + idx, "solid", "CYLINDER", 2, 1.5f, -4, 1.2f, 3f, 1.2f, "#546E7A", "STATIC");
                                break;
                            case 5:
                                ne = Entity.create3D("Crystal_" + idx, "coin", "SPHERE", 0, 1.5f, -3, 0.9f, 0.9f, 0.9f, "#FFD740", "STATIC");
                                ne.isTrigger = true; ne.emissive = true; ne.spinY = 135f;
                                break;
                            case 6:
                                if (project.customObjMeshes.isEmpty()) {
                                    project.customObjMeshes.put("gem",
                                            "v 0 0.7 0\nv -0.5 0 0.5\nv 0.5 0 0.5\nv 0.5 0 -0.5\nv -0.5 0 -0.5\nv 0 -0.7 0\n" +
                                            "f 1 2 3\nf 1 3 4\nf 1 4 5\nf 1 5 2\nf 6 3 2\nf 6 4 3\nf 6 5 4\nf 6 2 5\n");
                                }
                                String firstObj = project.customObjMeshes.keySet().iterator().next();
                                ne = Entity.create3D("CustomMesh_" + idx, "decor", "CUSTOM_OBJ", 0, 1.5f, -3, 1.5f, 1.5f, 1.5f, "#00E5FF", "STATIC");
                                ne.customObjName = firstObj;
                                ne.spinY = 60f;
                                break;
                            default:
                                ne = Entity.create3D("Cube_" + idx, "solid", "CUBE", 0, 1.5f, -3, 1.2f, 1.2f, 1.2f, "#00E5FF", "DYNAMIC");
                                break;
                        }
                    } else {
                        switch (which) {
                            case 1: ne = Entity.create2D("Hero_" + idx, "player", "SPRITE_HERO", 0, 1, 1.2f, 1.35f, "#00E5FF", "DYNAMIC"); break;
                            case 2: ne = Entity.create2D("Slime_" + idx, "enemy", "SPRITE_SLIME", 3, 0, 1.2f, 1.0f, "#FF5252", "KINEMATIC"); break;
                            case 3: ne = Entity.create2D("Ship_" + idx, "player", "SPRITE_SHIP", 0, 0, 1.3f, 1.4f, "#00E5FF", "KINEMATIC"); break;
                            case 4:
                                ne = Entity.create2D("Coin_" + idx, "coin", "SPRITE_COIN", 2, 2, 0.85f, 0.85f, "#FFD740", "STATIC");
                                ne.isTrigger = true;
                                break;
                            case 5:
                                ne = Entity.create2D("Ball_" + idx, "decor", "CIRCLE", 0, 3, 1.1f, 1.1f, "#7C4DFF", "DYNAMIC");
                                ne.bounce = 0.7f;
                                break;
                            default:
                                ne = Entity.create2D("Platform_" + idx, "solid", "RECT", 0, 0, 4.5f, 0.8f, "#2E4F6E", "STATIC");
                                break;
                        }
                    }
                    synchronized (gameSurface.renderer) {
                        project.entities.add(ne);
                        gameSurface.renderer.setSelectedEntityId(ne.id);
                    }
                    updateSelectionBanner(ne);
                })
                .show();
    }

    private void duplicateSelectedEntity() {
        Entity sel = project.findById(gameSurface.renderer.getSelectedEntityId());
        if (sel == null) return;
        Entity copy = sel.deepCopy();
        copy.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        copy.name = sel.name + "_copy";
        copy.x += 1.2f;
        synchronized (gameSurface.renderer) {
            project.entities.add(copy);
            gameSurface.renderer.setSelectedEntityId(copy.id);
        }
        updateSelectionBanner(copy);
    }

    private void deleteSelectedEntity() {
        String id = gameSurface.renderer.getSelectedEntityId();
        if (id == null) return;
        synchronized (gameSurface.renderer) {
            for (int i = project.entities.size() - 1; i >= 0; i--) {
                if (id.equals(project.entities.get(i).id)) {
                    project.entities.remove(i);
                    break;
                }
            }
            String nextId = project.entities.isEmpty() ? null : project.entities.get(0).id;
            gameSurface.renderer.setSelectedEntityId(nextId);
        }
        updateSelectionBanner(project.findById(gameSurface.renderer.getSelectedEntityId()));
    }

    // ===================== INSPECTOR POPULATION & SAVING =====================

    private void populateInspector() {
        hierarchyChipsLayout.removeAllViews();
        String selId = gameSurface.renderer.getSelectedEntityId();
        if ((selId == null || project.findById(selId) == null) && !project.entities.isEmpty()) {
            selId = project.entities.get(0).id;
            gameSurface.renderer.setSelectedEntityId(selId);
        }

        for (final Entity e : project.entities) {
            boolean active = e.id.equals(selId);
            TextView chip = makeChip(e.name, active ? "#00ACC1" : "#182232", active ? "#051016" : "#CFD8DC");
            chip.setOnClickListener(v -> {
                gameSurface.renderer.setSelectedEntityId(e.id);
                populateInspector();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(6);
            hierarchyChipsLayout.addView(chip, lp);
        }

        Entity sel = project.findById(selId);
        if (sel != null) {
            edName.setText(sel.name);
            edTag.setText(sel.tag);
            edCustomObj.setText(sel.customObjName);
            selectSpinnerValue(spPrimitive, PRIMITIVES_ALL, sel.primitive);
            edX.setText(fmtF(sel.x)); edY.setText(fmtF(sel.y)); edZ.setText(fmtF(sel.z));
            edSX.setText(fmtF(sel.scaleX)); edSY.setText(fmtF(sel.scaleY)); edSZ.setText(fmtF(sel.scaleZ));
            edRY.setText(fmtF(sel.rotY)); edRZ.setText(fmtF(sel.rotZ)); edSpinY.setText(fmtF(sel.spinY));
            edColor.setText(sel.colorHex);
            cbChecker.setChecked(sel.checkerTexture);
            cbEmissive.setChecked(sel.emissive);
            cbTrigger.setChecked(sel.isTrigger);
            selectSpinnerValue(spBodyType, BODY_TYPES, sel.bodyType);
            edBounce.setText(fmtF(sel.bounce));
            edGravScale.setText(fmtF(sel.gravityScale));
        }

        edWorldSky.setText(project.skyColorHex);
        edWorldGravityY.setText(fmtF(project.gravityY));
        edCamDist.setText(fmtF(project.cameraDistance));
        edCamHeight.setText(fmtF(project.cameraHeight));
        edZoom2D.setText(fmtF(project.zoom2D));
    }

    private void applyInspectorToSelectedEntity() {
        Entity sel = project.findById(gameSurface.renderer.getSelectedEntityId());
        if (sel == null) return;
        synchronized (gameSurface.renderer) {
            sel.name = edName.getText().toString().trim().isEmpty() ? sel.name : edName.getText().toString().trim();
            sel.tag = edTag.getText().toString().trim();
            sel.primitive = PRIMITIVES_ALL[spPrimitive.getSelectedItemPosition()];
            sel.customObjName = edCustomObj.getText().toString().trim();
            sel.x = parseF(edX, sel.x); sel.y = parseF(edY, sel.y); sel.z = parseF(edZ, sel.z);
            sel.scaleX = parseF(edSX, sel.scaleX); sel.scaleY = parseF(edSY, sel.scaleY); sel.scaleZ = parseF(edSZ, sel.scaleZ);
            sel.rotY = parseF(edRY, sel.rotY); sel.rotZ = parseF(edRZ, sel.rotZ); sel.spinY = parseF(edSpinY, sel.spinY);
            sel.colorHex = edColor.getText().toString().trim();
            sel.checkerTexture = cbChecker.isChecked();
            sel.emissive = cbEmissive.isChecked();
            sel.isTrigger = cbTrigger.isChecked();
            sel.bodyType = BODY_TYPES[spBodyType.getSelectedItemPosition()];
            sel.bounce = parseF(edBounce, sel.bounce);
            sel.gravityScale = parseF(edGravScale, sel.gravityScale);
        }
        ProjectRepository.saveProject(this, project);
        populateInspector();
    }

    private void applyWorldSettings() {
        project.skyColorHex = edWorldSky.getText().toString().trim();
        project.gravityY = parseF(edWorldGravityY, project.gravityY);
        project.cameraDistance = parseF(edCamDist, project.cameraDistance);
        project.cameraHeight = parseF(edCamHeight, project.cameraHeight);
        project.zoom2D = parseF(edZoom2D, project.zoom2D);
        gameSurface.renderer.editor2DZoom = project.zoom2D;
        ProjectRepository.saveProject(this, project);
    }

    // ===================== VORTEXSCRIPT IDE & .OBJ MESH EDITOR =====================

    private void validateCurrentScript() {
        String src = codeEditor.getText().toString();
        project.scriptCode = src;
        String res = VortexScriptEngine.validateSyntax(src);
        if ("OK".equals(res)) {
            syntaxStatusTv.setText("  ✓ VortexScript Syntax Valid");
            syntaxStatusTv.setTextColor(Color.parseColor("#00E676"));
        } else {
            syntaxStatusTv.setText("  ✗ " + res);
            syntaxStatusTv.setTextColor(Color.parseColor("#FF5252"));
        }
    }

    private void insertCodeSnippet(String snippet) {
        int start = Math.max(0, codeEditor.getSelectionStart());
        codeEditor.getText().insert(start, snippet);
    }

    private void showObjMeshEditorDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(10), dp(16), dp(8));

        String existingKey = project.customObjMeshes.isEmpty()
                ? "starfighter"
                : project.customObjMeshes.keySet().iterator().next();
        String existingObj = project.customObjMeshes.getOrDefault(existingKey,
                "# Wavefront .OBJ 3D Mesh\nv 0 0.8 0\nv -0.6 -0.4 0.6\nv 0.6 -0.4 0.6\nv 0 -0.4 -0.7\n" +
                "f 1 2 3\nf 1 3 4\nf 1 4 2\nf 2 4 3\n");

        final EditText nameEd = inputField("Mesh Name");
        nameEd.setText(existingKey);
        box.addView(nameEd);

        final EditText objEd = new EditText(this);
        objEd.setTypeface(Typeface.MONOSPACE);
        objEd.setTextSize(12.5f);
        objEd.setMinLines(8);
        objEd.setGravity(Gravity.TOP | Gravity.START);
        objEd.setText(existingObj);
        box.addView(objEd);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Custom 3D Wavefront .OBJ Mesh")
                .setView(box)
                .setPositiveButton("Save 3D Mesh", (d, w) -> {
                    String k = nameEd.getText().toString().trim();
                    if (k.isEmpty()) k = "custom_mesh";
                    project.customObjMeshes.put(k, objEd.getText().toString());
                    gameSurface.loadProjectForEdit(project);
                    Toast.makeText(this, "Saved .obj mesh '" + k + "'!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showApiDocsDialog() {
        String docs =
                "LIFECYCLE HOOKS:\n" +
                "  fn onStart() { ... }\n" +
                "  fn onUpdate(dt) { ... }\n" +
                "  fn onButton(btn) { ... } // 'A' or 'B'\n" +
                "  fn onCollide(a, b, tagA, tagB) { ... }\n" +
                "  fn onTouch(worldX, worldY) { ... }\n\n" +
                "INPUT:\n" +
                "  joyX(), joyY() -> -1.0 .. 1.0\n" +
                "  btnA(), btnB(), touchDown() -> bool\n\n" +
                "ENTITIES & PHYSICS:\n" +
                "  exists(name), isGrounded(name), dist(a, b)\n" +
                "  getX(n), getY(n), getZ(n), getVX(n), getVY(n), getVZ(n)\n" +
                "  setPos(n, x, y, [z]), move(n, dx, dy, [dz])\n" +
                "  setVel(n, vx, vy, [vz]), jump(n, impulse)\n" +
                "  setRot(n, rx, ry, rz), setScale(n, sx, sy, [sz])\n" +
                "  setColor(n, '#HEX'), destroy(n)\n" +
                "  spawn3D(name, tag, prim, x, y, z, sx, sy, sz, hex, body)\n" +
                "  spawn2D(name, tag, prim, x, y, w, h, hex, body)\n\n" +
                "VFX, AUDIO & HUD:\n" +
                "  particles(x, y, z, count, '#HEX')\n" +
                "  playSound('jump'|'coin'|'laser'|'explosion'|'hit'|'powerup')\n" +
                "  shake(intensity, duration)\n" +
                "  addScore(n), getScore(), addHealth(n), getHealth()\n" +
                "  setBanner(text), log(msg), rand(lo, hi)";

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("VortexScript (.vxs) Engine API")
                .setMessage(docs)
                .setPositiveButton("Got It", null)
                .show();
    }

    // ===================== TERMUX APK COMPILER PANEL =====================

    private void populateBuildPanel() {
        edBuildTitle.setText(project.title);
        edBuildPkg.setText(project.packageName);
        edBuildVerName.setText(project.versionName);
        edBuildVerCode.setText(String.valueOf(project.versionCode));
        edIconAccent.setText(project.iconAccentHex);
        selectSpinnerValue(spOrientation, ORIENTATIONS, project.orientation);
        selectSpinnerValue(spIconGlyph, ICON_GLYPHS, project.iconGlyph);
        refreshIconPreview();
    }

    private void syncBuildFieldsToProject() {
        project.title = edBuildTitle.getText().toString().trim().isEmpty() ? project.title : edBuildTitle.getText().toString().trim();
        project.packageName = edBuildPkg.getText().toString().trim().isEmpty() ? project.packageName : edBuildPkg.getText().toString().trim();
        project.versionName = edBuildVerName.getText().toString().trim().isEmpty() ? "1.0.0" : edBuildVerName.getText().toString().trim();
        project.versionCode = Math.max(1, (int) parseF(edBuildVerCode, 1f));
        project.orientation = ORIENTATIONS[spOrientation.getSelectedItemPosition()];
        project.iconAccentHex = edIconAccent.getText().toString().trim();
        project.iconGlyph = ICON_GLYPHS[spIconGlyph.getSelectedItemPosition()];
        ProjectRepository.saveProject(this, project);
    }

    private void refreshIconPreview() {
        syncBuildFieldsToProject();
        int bg = safeCol(project.iconBgHex, Color.rgb(13, 19, 33));
        int acc = safeCol(project.iconAccentHex, Color.rgb(0, 229, 255));
        Bitmap bmp = IconGenerator.renderIcon(144, bg, acc, project.iconGlyph, project.title);
        iconPreviewImg.setImageBitmap(bmp);
    }

    private void runExportAndTermuxBuild(boolean invokeRunCommand, boolean runBackground) {
        saveCurrentState();
        syncBuildFieldsToProject();
        refreshIconPreview();

        try {
            appendBuildLog("\n[*] Exporting standalone Android project for '" + project.title + "'...");
            lastExport = TermuxBridge.exportStandaloneProject(this, project);

            String locInfo = (lastExport.sharedProjectDir != null)
                    ? lastExport.sharedProjectDir.getAbsolutePath()
                    : (lastExport.sharedZipPath != null ? lastExport.sharedZipPath : lastExport.internalProjectDir.getAbsolutePath());
            appendBuildLog("[✓] Exported Manifest, Icons, 2D/3D Scenes, VortexScript & Runtime to:\n    " + locInfo);

            if (!invokeRunCommand) {
                String cmd = lastExport.termuxBuildCmd + (cbCompileJavaSource.isChecked() ? " --from-source" : "");
                TermuxBridge.copyToClipboard(this, "Vortex Build APK", cmd);
                appendBuildLog("[✓] Copied Termux Build Command to Clipboard:\n    " + cmd);
                boolean opened = TermuxBridge.openTermuxApp(this);
                if (opened) {
                    Toast.makeText(this, "Build command copied! Paste in Termux to compile APK.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Project exported & Termux command copied to clipboard!", Toast.LENGTH_LONG).show();
                }
                return;
            }

            String triggerStatus = TermuxBridge.triggerTermuxBuild(
                    this, lastExport, runBackground, cbCompileJavaSource.isChecked());

            if ("STARTED".equals(triggerStatus)) {
                appendBuildLog("[⚡] Dispatched com.termux.RUN_COMMAND to Termux! Compiling & signing APK...");
                if (runBackground) {
                    appendBuildLog("    Waiting for Termux PendingIntent callback (stdout/stderr)...");
                }
            } else if ("REQUESTED_PERMISSION".equals(triggerStatus)) {
                appendBuildLog("[!] Requested com.termux.permission.RUN_COMMAND — tap Allow, then tap Build again.");
            } else if ("ERR_NO_TERMUX".equals(triggerStatus)) {
                TermuxBridge.copyToClipboard(this, "Vortex Build APK", lastExport.termuxBuildCmd);
                appendBuildLog("[!] Termux (com.termux) is not installed yet. Exported project & copied build command to clipboard.");
            } else {
                TermuxBridge.copyToClipboard(this, "Vortex Build APK", lastExport.termuxBuildCmd);
                appendBuildLog("[!] Direct RUN_COMMAND returned: " + triggerStatus
                        + "\n    (Tip: Run Termux Setup from Hub so allow-external-apps=true is set. Build command copied to clipboard!)");
            }
        } catch (Exception ex) {
            appendBuildLog("[ERROR] Export failed: " + ex.getMessage());
        }
    }

    private void installLatestBuiltApk() {
        String slug = project.getSlug();
        File sharedApk = new File("/sdcard/Download/VortexEngine/Builds/" + slug + "-release.apk");
        if (!sharedApk.exists() && lastExport != null && lastExport.sharedProjectDir != null) {
            File alt = new File(lastExport.sharedProjectDir, "dist/" + slug + "-release.apk");
            if (alt.exists()) sharedApk = alt;
        }
        String res = TermuxBridge.promptInstallApk(this, sharedApk);
        if (!"OPENED_INSTALLER".equals(res)) {
            appendBuildLog("[Installer] " + res);
            Toast.makeText(this, res, Toast.LENGTH_LONG).show();
        }
    }

    private void appendBuildLog(String msg) {
        if (buildConsoleTv == null) return;
        buildConsoleTv.setText(buildConsoleTv.getText().toString() + "\n" + msg);
    }

    private void saveCurrentState() {
        if (codeEditor != null && currentTab == 2) {
            project.scriptCode = codeEditor.getText().toString();
        }
        ProjectRepository.saveProject(this, project);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (gameSurface != null) gameSurface.onResume();
    }

    @Override
    protected void onPause() {
        saveCurrentState();
        if (gameSurface != null) gameSurface.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        TermuxResultService.setListener(null);
        if (gameSurface != null) gameSurface.onDestroy();
        super.onDestroy();
    }

    // ===================== UI HELPERS =====================

    private TextView makeTabBtn(String title, final int idx) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(v -> switchTab(idx));
        return tv;
    }

    private void styleTab(TextView tv, boolean active) {
        if (tv == null) return;
        tv.setTextColor(Color.parseColor(active ? "#051016" : "#B0BEC5"));
        tv.setBackground(roundRect(active ? "#00E5FF" : "#1A2434", active ? "#00E5FF" : "#26354B", dp(8)));
    }

    private TextView makeChip(String text, String bgHex, String fgHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(fgHex));
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setBackground(roundRect(bgHex, fgHex, dp(8)));
        return tv;
    }

    private void addSpaced(LinearLayout row, View child) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(8);
        row.addView(child, lp);
    }

    private TextView sectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#00E5FF"));
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, dp(14), 0, dp(6));
        return tv;
    }

    private TextView fieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#90A4AE"));
        tv.setTextSize(11.5f);
        tv.setPadding(0, dp(8), 0, dp(2));
        return tv;
    }

    private EditText inputField(String hint) {
        EditText ed = new EditText(this);
        ed.setHint(hint);
        ed.setHintTextColor(Color.parseColor("#546E7A"));
        ed.setTextColor(Color.WHITE);
        ed.setTextSize(13.5f);
        ed.setSingleLine(true);
        ed.setPadding(dp(10), dp(8), dp(10), dp(8));
        ed.setBackground(roundRect("#141D2B", "#26364D", dp(8)));
        return ed;
    }

    private EditText numField(String hint) {
        EditText ed = inputField(hint);
        ed.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        return ed;
    }

    private LinearLayout twoColRow(View a, View b) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(6);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r.addView(a, lp1);
        r.addView(b, lp2);
        return r;
    }

    private LinearLayout threeColRow(View a, View b, View c) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(6);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp2.rightMargin = dp(6);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        r.addView(a, lp1);
        r.addView(b, lp2);
        r.addView(c, lp3);
        return r;
    }

    private static void selectSpinnerValue(Spinner sp, String[] items, String val) {
        if (val == null) return;
        for (int i = 0; i < items.length; i++) {
            if (items[i].equalsIgnoreCase(val)) {
                sp.setSelection(i);
                return;
            }
        }
    }

    private static float parseF(EditText ed, float def) {
        try {
            return Float.parseFloat(ed.getText().toString().trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static String fmtF(float v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static int safeCol(String hex, int fallback) {
        try {
            return Color.parseColor(hex.startsWith("#") ? hex : ("#" + hex));
        } catch (Exception e) {
            return fallback;
        }
    }

    private GradientDrawable roundRect(String fillHex, String strokeHex, int rad) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(rad);
        gd.setStroke(2, Color.parseColor(strokeHex));
        return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
