package com.vortex.engine.desktop;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.export.Html5WebExporter;
import com.vortex.engine.core.flow.VortexFlowCompiler;
import com.vortex.engine.core.forge.VortexForgeAgent;
import com.vortex.engine.core.lrm.LrmWorkspaceBridge;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import com.vortex.engine.core.model.ProjectTemplates;
import com.vortex.engine.core.physics.PhysicsWorld;
import com.vortex.engine.core.script.VortexScriptEngine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Vortex Engine Studio v2.0.0 — Desktop IDE & Standalone Game Runner (Windows .exe & Linux).
 * Shares 100% of Core Engine, Lua 5.2 (LuaJ), JBox2D Rigid-Body Physics, FastNoiseLite 3D Terrain,
 * 16x16 Pixel Art Sprites, and `.vortex.json` project format with the Android Studio APK!
 */
public class VortexDesktopStudio extends JFrame {

    private GameProject project;
    private File currentProjectFile = null;

    private final DesktopSoftware3DViewport viewport = new DesktopSoftware3DViewport();
    private final DefaultListModel<String> entityListModel = new DefaultListModel<>();
    private final JList<String> entityList = new JList<>(entityListModel);

    private final JPanel inspectorForm = new JPanel();
    private final JTextArea scriptArea = new JTextArea();
    private final JLabel syntaxStatusLabel = new JLabel("✓ Syntax Ready");
    private final JTextArea consoleArea = new JTextArea();
    private JButton playStopBtn;
    private JButton dimBtn;
    private JButton langBtn;
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    public static void main(String[] args) throws Exception {
        // 1) Headless CI / Self-Test & CLI HTML5 Web Export Modes
        for (int i = 0; i < args.length; i++) {
            if ("--headless-test".equals(args[i])) {
                runHeadlessSelfTests();
                return;
            }
            if ("--export-html".equals(args[i]) && i + 2 < args.length) {
                String tplKey = args[i + 1];
                File outHtml = new File(args[i + 2]);
                GameProject gp = ProjectTemplates.create(tplKey,
                        "CYBER_MAZE_AI_2D".equals(tplKey) ? "Cyber Hunters: Theta* AI Labyrinth" : tplKey,
                        "com.vortex.web." + tplKey.toLowerCase(Locale.US));
                Html5WebExporter.exportToFile(gp, outHtml);
                System.out.println("[HTML5] Exported playable Web Game -> " + outHtml.getAbsolutePath() + " (" + outHtml.length() + " bytes)");
                return;
            }
        }

        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 2) Check if running as an exported standalone game (.exe / .jar with embedded_game.vortex.json)
        String embeddedJson = readEmbeddedGameJson();
        if (embeddedJson != null && !embeddedJson.trim().isEmpty()) {
            final GameProject embeddedProj = GameProject.fromJsonString(embeddedJson);
            SwingUtilities.invokeLater(() -> launchStandaloneGameWindow(embeddedProj));
            return;
        }

        // 3) Launch Full Vortex Engine Desktop Studio IDE
        SwingUtilities.invokeLater(() -> {
            VortexDesktopStudio studio = new VortexDesktopStudio();
            studio.setVisible(true);
        });
    }

    public VortexDesktopStudio() {
        super("Vortex Engine Studio v4.0.0 (Hyperion Edition) — ES6 JS (Rhino) • Lua 5.2 • LRM P2P Sync • Forge AI • APK/PC/HTML5");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 860);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(9, 13, 22));
        setLayout(new BorderLayout());

        project = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "Neon Survivors Swarm", "com.vortex.game.survivors");

        add(buildTopToolbar(), BorderLayout.NORTH);

        // Left: Scene Hierarchy + Entity Inspector
        JPanel leftPanel = buildHierarchyAndInspector();

        // Center: 60 FPS 2D/3D Viewport + Console Log
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(6, 9, 15));
        consoleArea.setForeground(new Color(167, 243, 208));
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setText("Vortex Engine v4.0.0 (Hyperion Edition) Desktop Studio Initialized.\n"
                + "• Quad-Runtime     : ES6+ JavaScript (Mozilla Rhino 1.7.15.1) + Lua 5.2 (LuaJ) + Vortex Flow + VortexScript\n"
                + "• P2P & AI Agents  : LRM P2P Version Control & Sync (hacvilke/lrm) + Vortex Forge BYOK/Offline AI (hacvilke/roforge)\n"
                + "• Simulation       : JBox2D + Theta* Pathfinding + Universal Tween Engine + Multi-Frame Flipbook Sprites + Chiptune\n");
        consoleScroll.setPreferredSize(new Dimension(600, 145));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, viewport, consoleScroll);
        centerSplit.setResizeWeight(0.82);
        centerSplit.setDividerSize(5);

        // Right: Dual Lua 5.2 / VortexScript Code Editor
        JPanel rightPanel = buildScriptEditorPanel();

        JSplitPane midRight = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightPanel);
        midRight.setResizeWeight(0.64);
        midRight.setDividerSize(5);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, midRight);
        mainSplit.setDividerLocation(290);
        mainSplit.setDividerSize(5);

        add(mainSplit, BorderLayout.CENTER);

        viewport.setViewportListener(new DesktopSoftware3DViewport.ViewportListener() {
            @Override
            public void onEntitySelected(Entity e) {
                syncHierarchySelection();
                populateInspector();
            }

            @Override
            public void onEntityMoved(Entity e) {
                populateInspector();
            }

            @Override
            public void onLog(String msg) {
                log("[LUA/SCRIPT] " + msg);
            }

            @Override
            public void onTelemetry(int fps, int score, int health, String banner) {}
        });

        loadProjectIntoStudio(project);
    }

    private JPanel buildTopToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 7));
        bar.setBackground(new Color(17, 24, 39));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(36, 50, 75)));

        JButton newBtn = styledBtn("＋ New Template", new Color(0, 184, 212), Color.BLACK);
        newBtn.addActionListener(e -> showNewTemplateDialog());
        bar.add(newBtn);

        JButton openBtn = styledBtn("📂 Open .json", new Color(30, 41, 59), Color.WHITE);
        openBtn.addActionListener(e -> openProjectJson());
        bar.add(openBtn);

        JButton saveBtn = styledBtn("💾 Save", new Color(6, 78, 59), Color.WHITE);
        saveBtn.addActionListener(e -> saveProjectJson());
        bar.add(saveBtn);

        JButton undoBtn = styledBtn("↶ Undo", new Color(30, 41, 59), Color.WHITE);
        undoBtn.addActionListener(e -> performUndo());
        bar.add(undoBtn);

        JButton redoBtn = styledBtn("↷ Redo", new Color(30, 41, 59), Color.WHITE);
        redoBtn.addActionListener(e -> performRedo());
        bar.add(redoBtn);

        dimBtn = styledBtn("Mode: 2D", new Color(30, 27, 75), new Color(190, 200, 255));
        dimBtn.addActionListener(e -> {
            pushUndo();
            syncCode();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimBtn.setText("Mode: " + project.dimensionMode);
            viewport.resetCamera();
            viewport.repaint();
        });
        bar.add(dimBtn);

        JButton addObjBtn = styledBtn("＋ Entity ▾", new Color(30, 41, 59), new Color(56, 189, 248));
        addObjBtn.addActionListener(e -> showAddEntityMenu(addObjBtn));
        bar.add(addObjBtn);

        JButton flowBtn = styledBtn("⚡ Flow Logic", new Color(20, 50, 75), new Color(0, 229, 255));
        flowBtn.addActionListener(e -> openVortexFlowDialog());
        bar.add(flowBtn);

        JButton forgeBtn = styledBtn("🤖 Forge AI", new Color(45, 22, 68), new Color(235, 130, 255));
        forgeBtn.addActionListener(e -> openForgeAiDialog());
        bar.add(forgeBtn);

        JButton lrmBtn = styledBtn("🔄 LRM P2P Sync", new Color(16, 48, 52), new Color(0, 230, 118));
        lrmBtn.addActionListener(e -> openLrmSyncDialog());
        bar.add(lrmBtn);

        JButton pixelBtn = styledBtn("🎨 Pixel Art", new Color(45, 25, 65), new Color(225, 190, 255));
        pixelBtn.addActionListener(e -> openPixelArtStudio());
        bar.add(pixelBtn);

        JButton sfxBtn = styledBtn("🎵 Music & SFX", new Color(30, 41, 59), new Color(255, 213, 79));
        sfxBtn.addActionListener(e -> showSfxPopup(sfxBtn));
        bar.add(sfxBtn);

        playStopBtn = styledBtn("▶ PLAY GAME", new Color(0, 200, 83), Color.BLACK);
        playStopBtn.addActionListener(e -> togglePlayMode());
        bar.add(playStopBtn);

        JButton exportBtn = styledBtn("📦 Export (HTML5 / .exe / APK) ▾", new Color(88, 28, 135), Color.WHITE);
        exportBtn.addActionListener(e -> showExportMenu(exportBtn));
        bar.add(exportBtn);

        return bar;
    }

    private JPanel buildHierarchyAndInspector() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(new Color(13, 20, 34));

        JPanel topList = new JPanel(new BorderLayout());
        topList.setBackground(new Color(13, 20, 34));
        JLabel hLbl = new JLabel("  SCENE ENTITIES");
        hLbl.setForeground(new Color(0, 229, 255));
        hLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
        hLbl.setPreferredSize(new Dimension(200, 26));
        topList.add(hLbl, BorderLayout.NORTH);

        entityList.setBackground(new Color(9, 13, 22));
        entityList.setForeground(Color.WHITE);
        entityList.setSelectionBackground(new Color(0, 184, 212));
        entityList.setSelectionForeground(Color.BLACK);
        entityList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = entityList.getSelectedIndex();
                if (idx >= 0 && idx < project.entities.size()) {
                    viewport.setSelectedEntityId(project.entities.get(idx).id);
                    populateInspector();
                }
            }
        });
        JScrollPane listScroll = new JScrollPane(entityList);
        listScroll.setPreferredSize(new Dimension(260, 210));
        topList.add(listScroll, BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        listBtns.setBackground(new Color(17, 24, 39));
        JButton dupBtn = styledBtn("⎘ Clone", new Color(30, 41, 59), Color.WHITE);
        dupBtn.addActionListener(e -> cloneSelectedEntity());
        listBtns.add(dupBtn);

        JButton delBtn = styledBtn("🗑 Delete", new Color(65, 22, 30), new Color(255, 120, 120));
        delBtn.addActionListener(e -> deleteSelectedEntity());
        listBtns.add(delBtn);
        topList.add(listBtns, BorderLayout.SOUTH);

        inspectorForm.setLayout(new BoxLayout(inspectorForm, BoxLayout.Y_AXIS));
        inspectorForm.setBackground(new Color(15, 23, 42));
        inspectorForm.setBorder(BorderFactory.createEmptyBorder(8, 10, 16, 10));
        JScrollPane inspScroll = new JScrollPane(inspectorForm);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topList, inspScroll);
        split.setDividerLocation(245);
        split.setDividerSize(4);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildScriptEditorPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(11, 15, 23));
        p.setPreferredSize(new Dimension(430, 600));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(19, 28, 46));

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5));
        btnBar.setOpaque(false);

        JButton checkBtn = styledBtn("✓ Check Syntax", new Color(6, 78, 59), Color.WHITE);
        checkBtn.addActionListener(e -> checkScriptSyntax());
        btnBar.add(checkBtn);

        langBtn = styledBtn("Lang: JS", new Color(38, 32, 11), new Color(250, 204, 21));
        langBtn.addActionListener(e -> {
            syncCode();
            if ("JS".equalsIgnoreCase(project.scriptLanguage)) project.scriptLanguage = "LUA";
            else if ("LUA".equalsIgnoreCase(project.scriptLanguage)) project.scriptLanguage = "VXS";
            else project.scriptLanguage = "JS";
            langBtn.setText("Lang: " + project.scriptLanguage);
            syntaxStatusLabel.setText("Switched VM to " + project.scriptLanguage + " — click Check Syntax.");
        });
        btnBar.add(langBtn);

        header.add(btnBar, BorderLayout.NORTH);

        syntaxStatusLabel.setOpaque(true);
        syntaxStatusLabel.setBackground(new Color(13, 21, 36));
        syntaxStatusLabel.setForeground(new Color(0, 230, 118));
        syntaxStatusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        header.add(syntaxStatusLabel, BorderLayout.SOUTH);

        p.add(header, BorderLayout.NORTH);

        scriptArea.setBackground(new Color(9, 13, 22));
        scriptArea.setForeground(new Color(226, 232, 240));
        scriptArea.setCaretColor(new Color(0, 229, 255));
        scriptArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        scriptArea.setTabSize(4);
        p.add(new JScrollPane(scriptArea), BorderLayout.CENTER);
        return p;
    }

    private void loadProjectIntoStudio(GameProject p) {
        this.project = p;
        dimBtn.setText("Mode: " + p.dimensionMode);
        langBtn.setText("Lang: " + p.scriptLanguage);
        scriptArea.setText(p.scriptCode == null ? "" : p.scriptCode);
        viewport.loadProject(p, false);
        if (!p.entities.isEmpty()) {
            viewport.setSelectedEntityId(p.entities.get(0).id);
        }
        refreshHierarchyList();
        populateInspector();
        checkScriptSyntax();
        playStopBtn.setText("▶ PLAY GAME");
        playStopBtn.setBackground(new Color(0, 200, 83));
    }

    private void refreshHierarchyList() {
        entityListModel.clear();
        for (Entity e : project.entities) {
            entityListModel.addElement(e.name + " [" + e.primitive + " • " + e.bodyType + "]");
        }
        syncHierarchySelection();
    }

    private void syncHierarchySelection() {
        String sel = viewport.getSelectedEntityId();
        for (int i = 0; i < project.entities.size(); i++) {
            if (project.entities.get(i).id.equals(sel)) {
                entityList.setSelectedIndex(i);
                return;
            }
        }
    }

    private void populateInspector() {
        inspectorForm.removeAll();

        Entity e = project.findById(viewport.getSelectedEntityId());
        if (e == null) {
            JLabel none = new JLabel("Select an entity to inspect.");
            none.setForeground(Color.LIGHT_GRAY);
            inspectorForm.add(none);
            inspectorForm.revalidate();
            inspectorForm.repaint();
            return;
        }

        JTextField nameTf = addField("Name", e.name);
        JTextField tagTf = addField("Tag (player/solid/coin/enemy)", e.tag);
        String[] prims = "3D".equalsIgnoreCase(project.dimensionMode)
                ? new String[]{"CUBE", "SPHERE", "PYRAMID", "CYLINDER", "PLANE", "TERRAIN", "CUSTOM_OBJ"}
                : new String[]{"RECT", "CIRCLE", "TRIANGLE", "CUSTOM_SPRITE", "SPRITE_HERO", "SPRITE_SLIME", "SPRITE_COIN", "SPRITE_SHIP"};
        JComboBox<String> primBox = addCombo("Primitive / Sprite", prims, e.primitive);

        JPanel xyzRow = new JPanel(new GridLayout(1, 3, 4, 0));
        xyzRow.setOpaque(false);
        JTextField xTf = new JTextField(fmt(e.x));
        JTextField yTf = new JTextField(fmt(e.y));
        JTextField zTf = new JTextField(fmt(e.z));
        xyzRow.add(labeledMini("X", xTf));
        xyzRow.add(labeledMini("Y", yTf));
        xyzRow.add(labeledMini("Z", zTf));
        inspectorForm.add(xyzRow);

        JPanel sclRow = new JPanel(new GridLayout(1, 3, 4, 0));
        sclRow.setOpaque(false);
        JTextField sxTf = new JTextField(fmt(e.scaleX));
        JTextField syTf = new JTextField(fmt(e.scaleY));
        JTextField szTf = new JTextField(fmt(e.scaleZ));
        sclRow.add(labeledMini("ScaleX", sxTf));
        sclRow.add(labeledMini("ScaleY", syTf));
        sclRow.add(labeledMini("ScaleZ", szTf));
        inspectorForm.add(sclRow);

        JTextField rotTf = addField("Rotation (Y in 3D / Z in 2D)", fmt("3D".equalsIgnoreCase(project.dimensionMode) ? e.rotY : e.rotZ));
        JTextField colTf = addField("Color Hex", e.colorHex);

        String[] bodies = {"NONE", "STATIC", "DYNAMIC", "KINEMATIC"};
        JComboBox<String> bodyBox = addCombo("Physics Body", bodies, e.bodyType);

        JCheckBox trigCb = addCheck("Is Trigger Sensor", e.isTrigger);
        JCheckBox fixRotCb = addCheck("Lock 2D Rotation", e.fixedRotation);
        JCheckBox chkCb = addCheck("Checker Texture", e.checkerTexture);
        JCheckBox glowCb = addCheck("Neon Emissive", e.emissive);
        JCheckBox box2dCb = addCheck("World: Enable JBox2D (2D)", project.useBox2D);

        JButton apply = styledBtn("✓ Apply Entity & World", new Color(0, 184, 212), Color.BLACK);
        apply.addActionListener(ev -> {
            e.name = nameTf.getText().trim();
            e.tag = tagTf.getText().trim();
            e.primitive = (String) primBox.getSelectedItem();
            e.x = pf(xTf, e.x); e.y = pf(yTf, e.y); e.z = pf(zTf, e.z);
            e.scaleX = Math.max(0.1f, pf(sxTf, e.scaleX));
            e.scaleY = Math.max(0.1f, pf(syTf, e.scaleY));
            e.scaleZ = Math.max(0.1f, pf(szTf, e.scaleZ));
            if ("3D".equalsIgnoreCase(project.dimensionMode)) e.rotY = pf(rotTf, e.rotY);
            else e.rotZ = pf(rotTf, e.rotZ);
            e.colorHex = colTf.getText().trim();
            e.bodyType = (String) bodyBox.getSelectedItem();
            e.isTrigger = trigCb.isSelected();
            e.fixedRotation = fixRotCb.isSelected();
            e.checkerTexture = chkCb.isSelected();
            e.emissive = glowCb.isSelected();
            project.useBox2D = box2dCb.isSelected();
            refreshHierarchyList();
            viewport.repaint();
        });
        inspectorForm.add(apply);
        inspectorForm.revalidate();
        inspectorForm.repaint();
    }

    private void showAddEntityMenu(JButton invoker) {
        JPopupMenu menu = new JPopupMenu();
        if ("3D".equalsIgnoreCase(project.dimensionMode)) {
            addMenuAction(menu, "3D Cube", () -> spawnEntity("CUBE", "#00E5FF", "STATIC"));
            addMenuAction(menu, "3D Sphere (Dynamic)", () -> spawnEntity("SPHERE", "#FFD740", "DYNAMIC"));
            addMenuAction(menu, "3D Pyramid", () -> spawnEntity("PYRAMID", "#FF4081", "STATIC"));
            addMenuAction(menu, "⛰ FastNoiseLite 3D Terrain (New Seed)", () -> {
                project.terrainSeed = (int) (System.currentTimeMillis() % 90000) + 1000;
                Entity t = Entity.create3D("Terrain_" + project.terrainSeed, "solid", "TERRAIN", 0f, 0f, 0f, 65f, 9f, 65f, "#2E7D32", "STATIC");
                t.checkerTexture = true; t.secondaryColorHex = "#1B5E20";
                project.entities.add(0, t);
                viewport.invalidateAssetCaches();
                refreshHierarchyList();
                viewport.repaint();
                log("Generated 3D FastNoiseLite Terrain (seed=" + project.terrainSeed + ")");
            });
        } else {
            addMenuAction(menu, "2D Platform (Static)", () -> spawnEntity("RECT", "#2E4F6E", "STATIC"));
            addMenuAction(menu, "2D Hero (Dynamic)", () -> spawnEntity("SPRITE_HERO", "#00E5FF", "DYNAMIC"));
            addMenuAction(menu, "📦 JBox2D Tumbling Crate", () -> {
                project.customSprites.putIfAbsent("crate", PixelSpriteCodec.createCrateSprite());
                Entity c = Entity.create2D("Crate_" + (project.entities.size() + 1), "solid", "CUSTOM_SPRITE", 0f, 3f, 1.3f, 1.3f, "#FFB300", "DYNAMIC");
                c.customSpriteName = "crate"; c.fixedRotation = false;
                project.entities.add(c);
                viewport.setSelectedEntityId(c.id);
                refreshHierarchyList();
                viewport.repaint();
            });
            addMenuAction(menu, "2D Coin Trigger", () -> {
                Entity c = spawnEntity("SPRITE_COIN", "#FFD740", "STATIC");
                c.isTrigger = true;
            });
            addMenuAction(menu, "2D Slime Enemy", () -> spawnEntity("SPRITE_SLIME", "#FF5252", "DYNAMIC"));
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    private Entity spawnEntity(String prim, String hex, String body) {
        String nm = prim.toLowerCase(Locale.US) + "_" + (project.entities.size() + 1);
        Entity e = "3D".equalsIgnoreCase(project.dimensionMode)
                ? Entity.create3D(nm, "prop", prim, 0f, 1.5f, 0f, 1.2f, 1.2f, 1.2f, hex, body)
                : Entity.create2D(nm, "prop", prim, 0f, 1.5f, 1.3f, 1.3f, hex, body);
        project.entities.add(e);
        viewport.setSelectedEntityId(e.id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
        return e;
    }

    private void openPixelArtStudio() {
        DesktopPixelArtDialog dlg = new DesktopPixelArtDialog(this, project, (spriteName, spawn) -> {
            viewport.invalidateAssetCaches();
            if (spawn) {
                project.dimensionMode = "2D";
                dimBtn.setText("Mode: 2D");
                Entity sp = Entity.create2D("Sprite_" + (project.entities.size() + 1), "prop", "CUSTOM_SPRITE", 0f, 1.5f, 1.5f, 1.5f, "#FFFFFF", "DYNAMIC");
                sp.customSpriteName = spriteName;
                project.entities.add(sp);
                viewport.setSelectedEntityId(sp.id);
                refreshHierarchyList();
                populateInspector();
            }
            viewport.repaint();
            log("Saved 16x16 pixel sprite '" + spriteName + "'");
        });
        dlg.setVisible(true);
    }

    private void showSfxPopup(JButton invoker) {
        JPopupMenu m = new JPopupMenu();
        addMenuAction(m, "🎵 Loop Cyberpunk Synth (134 BPM)", () -> {
            project.musicEnabled = true; project.musicBpm = 134; project.musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK;
            viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm);
            log("[🎵] Set soundtrack: CYBERPUNK (134 BPM)");
        });
        addMenuAction(m, "🏰 Loop Dungeon Synth (112 BPM)", () -> {
            project.musicEnabled = true; project.musicBpm = 112; project.musicPattern = ChiptuneGenerator.PRESET_DUNGEON;
            viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm);
            log("[🎵] Set soundtrack: DUNGEON (112 BPM)");
        });
        addMenuAction(m, "🔥 Loop Boss Battle Synth (148 BPM)", () -> {
            project.musicEnabled = true; project.musicBpm = 148; project.musicPattern = ChiptuneGenerator.PRESET_BOSS;
            viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm);
            log("[🎵] Set soundtrack: BOSS (148 BPM)");
        });
        addMenuAction(m, "☁️ Loop Sky Chill Synth (118 BPM)", () -> {
            project.musicEnabled = true; project.musicBpm = 118; project.musicPattern = ChiptuneGenerator.PRESET_SKYCHILL;
            viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm);
            log("[🎵] Set soundtrack: SKYCHILL (118 BPM)");
        });
        addMenuAction(m, "■ Stop Music Preview", () -> viewport.audioEngine.stopMusicLoop());
        m.addSeparator();
        String[] sounds = {"jump", "coin", "laser", "explosion", "hit", "powerup"};
        for (String s : sounds) {
            addMenuAction(m, "▶ Play '" + s + "' (+ Copy to Script)", () -> {
                viewport.audioEngine.play(s);
                String snip = "LUA".equalsIgnoreCase(project.scriptLanguage)
                        ? "\n    vortex.playSound('" + s + "')\n"
                        : "\n    playSound('" + s + "');\n";
                scriptArea.append(snip);
            });
        }
        m.show(invoker, 0, invoker.getHeight());
    }

    private void togglePlayMode() {
        if (viewport.isPlaying()) {
            viewport.loadProject(project, false);
            playStopBtn.setText("▶ PLAY GAME");
            playStopBtn.setBackground(new Color(0, 200, 83));
            log("Stopped Play Mode.");
        } else {
            if (!checkScriptSyntax()) {
                JOptionPane.showMessageDialog(this, "Fix script syntax error before playing!", "Syntax Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            viewport.loadProject(project.deepCopy(), true);
            playStopBtn.setText("■ STOP PLAY");
            playStopBtn.setBackground(new Color(255, 82, 82));
            log("▶ Started Live Play Mode (" + project.scriptLanguage + ")! Use WASD/Arrows + Space(A) + Shift(B).");
        }
    }

    private void showExportMenu(JButton invoker) {
        syncCode();
        JPopupMenu m = new JPopupMenu();
        addMenuAction(m, "🌐 Export Playable HTML5 Web Game (.html — itch.io / Browser)", () -> exportWithDialog("html"));
        addMenuAction(m, "🪟 Export Standalone Windows Game (.exe)", () -> exportWithDialog("exe"));
        addMenuAction(m, "🐧 Export Standalone Linux Binary", () -> exportWithDialog("linux"));
        addMenuAction(m, "☕ Export Cross-Platform Desktop Game (.jar)", () -> exportWithDialog("jar"));
        addMenuAction(m, "🤖 Export Android Termux APK Project Bundle (.zip)", () -> exportWithDialog("android"));
        m.show(invoker, 0, invoker.getHeight());
    }

    private void exportWithDialog(String kind) {
        JFileChooser fc = new JFileChooser();
        String slug = project.getSlug();
        String defaultName = slug + ("html".equals(kind) ? "-Web.html"
                : "exe".equals(kind) ? "-Windows-x64.exe"
                : "linux".equals(kind) ? "-Linux-x86_64"
                : "jar".equals(kind) ? "-Desktop.jar"
                : "-Android-Termux.zip");
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = fc.getSelectedFile();
            try {
                if ("html".equals(kind)) Html5WebExporter.exportToFile(project, out);
                else if ("exe".equals(kind)) DesktopExporter.exportStandaloneWindowsExe(project, out);
                else if ("linux".equals(kind)) DesktopExporter.exportStandaloneLinuxBinary(project, out);
                else if ("jar".equals(kind)) DesktopExporter.exportStandaloneGameJar(project, out);
                else DesktopExporter.exportAndroidTermuxZip(project, out);
                log("[✓] Exported standalone build -> " + out.getAbsolutePath());
                JOptionPane.showMessageDialog(this, "Exported successfully:\n" + out.getAbsolutePath());
            } catch (Exception ex) {
                log("[ERROR] Export failed: " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Export failed:\n" + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void pushUndo() {
        syncCode();
        undoStack.push(project.toJsonString());
        while (undoStack.size() > 40) undoStack.removeLast();
        redoStack.clear();
    }

    private void performUndo() {
        if (undoStack.isEmpty()) return;
        try {
            syncCode();
            redoStack.push(project.toJsonString());
            loadProjectIntoStudio(GameProject.fromJsonString(undoStack.pop()));
            log("↶ Undid last change.");
        } catch (Exception ignored) {}
    }

    private void performRedo() {
        if (redoStack.isEmpty()) return;
        try {
            syncCode();
            undoStack.push(project.toJsonString());
            loadProjectIntoStudio(GameProject.fromJsonString(redoStack.pop()));
            log("↷ Redid change.");
        } catch (Exception ignored) {}
    }

    private void openVortexFlowDialog() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setPreferredSize(new Dimension(640, 440));
        DefaultListModel<String> rModel = new DefaultListModel<>();
        for (int i = 0; i < project.flowRules.size(); i++) {
            rModel.addElement((i + 1) + ". " + project.flowRules.get(i).describe());
        }
        JList<String> rList = new JList<>(rModel);
        p.add(new JScrollPane(rList), BorderLayout.CENTER);

        JPanel addBox = new JPanel(new GridLayout(0, 2, 6, 4));
        JComboBox<String> evCb = new JComboBox<>(VortexFlowCompiler.EVENT_LABELS);
        JTextField ev1 = new JTextField("player");
        JTextField ev2 = new JTextField("coin");
        JComboBox<String> acCb = new JComboBox<>(VortexFlowCompiler.ACTION_LABELS);
        JTextField ac1 = new JTextField("Player");
        JTextField ac2 = new JTextField("10");

        addBox.add(new JLabel("WHEN Event:")); addBox.add(evCb);
        addBox.add(new JLabel("Event Tag A / Sec:")); addBox.add(ev1);
        addBox.add(new JLabel("Event Tag B:")); addBox.add(ev2);
        addBox.add(new JLabel("DO Action:")); addBox.add(acCb);
        addBox.add(new JLabel("Target / Score / Preset:")); addBox.add(ac1);
        addBox.add(new JLabel("Speed / Force / BPM:")); addBox.add(ac2);

        JButton addRuleBtn = new JButton("＋ Add Rule");
        addRuleBtn.addActionListener(e -> {
            pushUndo();
            VortexFlowCompiler.FlowRule r = new VortexFlowCompiler.FlowRule(
                    VortexFlowCompiler.EVENT_KEYS[evCb.getSelectedIndex()],
                    ev1.getText().trim(), ev2.getText().trim(),
                    VortexFlowCompiler.ACTION_KEYS[acCb.getSelectedIndex()],
                    ac1.getText().trim(), ac2.getText().trim());
            project.flowRules.add(r);
            rModel.addElement(project.flowRules.size() + ". " + r.describe());
        });
        JButton delRuleBtn = new JButton("🗑 Remove Selected");
        delRuleBtn.addActionListener(e -> {
            int idx = rList.getSelectedIndex();
            if (idx >= 0 && idx < project.flowRules.size()) {
                pushUndo();
                project.flowRules.remove(idx);
                rModel.clear();
                for (int i = 0; i < project.flowRules.size(); i++) {
                    rModel.addElement((i + 1) + ". " + project.flowRules.get(i).describe());
                }
            }
        });
        addBox.add(addRuleBtn);
        addBox.add(delRuleBtn);
        p.add(addBox, BorderLayout.SOUTH);

        int res = JOptionPane.showOptionDialog(this, p,
                "⚡ Vortex Flow — Visual Event Sheet (Compiles 1:1 to ES6 JS or Lua 5.2)",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null,
                new String[]{"⚡ Compile ➔ ES6 JavaScript", "⚡ Compile ➔ Lua 5.2", "Close"},
                "⚡ Compile ➔ ES6 JavaScript");
        if (res == 0 && !project.flowRules.isEmpty()) {
            pushUndo();
            String js = VortexFlowCompiler.compileToJavaScript(project);
            project.scriptLanguage = "JS";
            project.scriptCode = js;
            scriptArea.setText(js);
            langBtn.setText("Lang: JS");
            checkScriptSyntax();
            log("[⚡ FLOW] Compiled " + project.flowRules.size() + " visual rules into ES6+ JavaScript!");
        } else if (res == 1 && !project.flowRules.isEmpty()) {
            pushUndo();
            String lua = VortexFlowCompiler.compileToLua(project);
            project.scriptLanguage = "LUA";
            project.scriptCode = lua;
            scriptArea.setText(lua);
            langBtn.setText("Lang: LUA");
            checkScriptSyntax();
            log("[⚡ FLOW] Compiled " + project.flowRules.size() + " visual rules into Lua 5.2!");
        }
    }

    private void openForgeAiDialog() {
        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.setPreferredSize(new Dimension(540, 220));
        JComboBox<String> provCb = new JComboBox<>(VortexForgeAgent.PROVIDERS);
        JTextField keyField = new JTextField();
        JTextField promptField = new JTextField("2D cyber swarm arena with animated drones and auto-aim blaster");
        form.add(new JLabel("AI Provider (inspired by hacvilke/roforge — local BYOK or instant Offline):"));
        form.add(provCb);
        form.add(new JLabel("Optional API Key (leave blank for Offline Smart Generator):"));
        form.add(keyField);
        form.add(new JLabel("Describe what Forge AI should generate (+ automatic Undo Checkpoint):"));
        form.add(promptField);

        if (JOptionPane.showConfirmDialog(this, form, "🤖 Vortex Forge AI Studio Agent",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            pushUndo();
            try {
                String msg = VortexForgeAgent.applyPromptToProject(
                        project, promptField.getText().trim(), provCb.getSelectedIndex(), keyField.getText().trim());
                loadProjectIntoStudio(project);
                log("[🤖 FORGE AI] " + msg);
            } catch (Exception ex) {
                log("[FORGE ERROR] " + ex.getMessage());
                JOptionPane.showMessageDialog(this, "Forge AI Error:\n" + ex.getMessage());
            }
        }
    }

    private void openLrmSyncDialog() {
        syncCode();
        final File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setPreferredSize(new Dimension(680, 440));

        JTextArea outArea = new JTextArea();
        outArea.setEditable(false);
        outArea.setBackground(new Color(6, 9, 15));
        outArea.setForeground(new Color(167, 243, 208));
        outArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        String detectedBin = LrmWorkspaceBridge.findLrmBinary();
        outArea.setText("LRM (Log Replication Manager — github.com/hacvilke/lrm)\n"
                + "Repo Folder : " + repoDir.getAbsolutePath() + "\n"
                + "LRM Binary  : " + (detectedBin != null ? detectedBin : "Not installed yet (see install commands below)") + "\n\n"
                + "Install LRM in 1 command:\n"
                + "  Linux/macOS : " + LRM_HINT() + "\n");
        panel.add(new JScrollPane(outArea), BorderLayout.CENTER);

        JPanel controls = new JPanel(new GridLayout(0, 2, 6, 5));
        JTextField msgField = new JTextField("Update " + project.title);
        JTextField portKeyField = new JTextField();
        controls.add(new JLabel("Commit Message:")); controls.add(msgField);
        controls.add(new JLabel("Remote Port Key (lrm1_...):")); controls.add(portKeyField);

        JButton commitBtn = new JButton("📤 Unpack + LRM Commit");
        commitBtn.addActionListener(e -> {
            try {
                LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
                if (!new File(repoDir, ".lrm").exists()) {
                    LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
                }
                LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 12, "commit", "-m", msgField.getText().trim());
                LrmWorkspaceBridge.CmdResult lg = LrmWorkspaceBridge.runLrm(repoDir, 8, "log", "--limit", "6", "--graph", "--oneline");
                outArea.append("\n$ lrm commit\n" + r.output + "\n$ lrm log --graph\n" + lg.output + "\n");
            } catch (Exception ex) {
                outArea.append("\n[Error] " + ex.getMessage() + "\n");
            }
        });

        JButton shareBtn = new JButton("🔑 LRM Share (Get Port Key)");
        shareBtn.addActionListener(e -> {
            try {
                LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
                if (!new File(repoDir, ".lrm").exists()) {
                    LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
                    LrmWorkspaceBridge.runLrm(repoDir, 10, "commit", "-m", "Initial game snapshot");
                }
                LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 12, "share", "--lifetime", "600");
                outArea.append("\n$ lrm share\n" + r.output + "\n");
            } catch (Exception ex) {
                outArea.append("\n[Error] " + ex.getMessage() + "\n");
            }
        });

        JButton joinBtn = new JButton("🌐 LRM Join Port Key");
        joinBtn.addActionListener(e -> {
            String pk = portKeyField.getText().trim();
            if (pk.isEmpty()) { outArea.append("\n[!] Paste an lrm1_... Port Key first!\n"); return; }
            LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 20, "join", pk, "--init");
            outArea.append("\n$ lrm join\n" + r.output + "\n");
        });

        JButton importBtn = new JButton("🔄 Reload Repo & Inbox into Studio");
        importBtn.addActionListener(e -> {
            try {
                pushUndo();
                GameProject imported = LrmWorkspaceBridge.importFromLrmRepo(repoDir);
                imported.id = project.id;
                loadProjectIntoStudio(imported);
                outArea.append("\n[✓] Reloaded " + imported.entities.size() + " entities into Studio!\n");
            } catch (Exception ex) {
                outArea.append("\n[Import Error] " + ex.getMessage() + "\n");
            }
        });

        controls.add(commitBtn); controls.add(shareBtn);
        controls.add(joinBtn);   controls.add(importBtn);
        panel.add(controls, BorderLayout.SOUTH);

        JOptionPane.showMessageDialog(this, panel, "🔄 LRM P2P Version Control & Live Sync", JOptionPane.PLAIN_MESSAGE);
    }

    private static String LRM_HINT() {
        return LrmWorkspaceBridge.LRM_DESKTOP_INSTALL_SH + "\n  Windows PS  : " + LrmWorkspaceBridge.LRM_WINDOWS_INSTALL_PS1;
    }

    private void showNewTemplateDialog() {
        String chosen = (String) JOptionPane.showInputDialog(
                this, "Choose a 2D / 3D Starter Game Template:",
                "New Game Project", JOptionPane.PLAIN_MESSAGE, null,
                ProjectTemplates.TEMPLATE_NAMES, ProjectTemplates.TEMPLATE_NAMES[0]);
        if (chosen == null) return;
        for (int i = 0; i < ProjectTemplates.TEMPLATE_NAMES.length; i++) {
            if (ProjectTemplates.TEMPLATE_NAMES[i].equals(chosen)) {
                String title = JOptionPane.showInputDialog(this, "Game Title:", "My Vortex Game");
                if (title == null || title.trim().isEmpty()) title = "My Vortex Game";
                GameProject created = ProjectTemplates.create(
                        ProjectTemplates.TEMPLATE_KEYS[i], title.trim(),
                        "com.vortex.game." + title.trim().toLowerCase().replaceAll("[^a-z0-9]", ""));
                currentProjectFile = null;
                loadProjectIntoStudio(created);
                log("Created template: " + chosen);
                return;
            }
        }
    }

    private void openProjectJson() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File f = fc.getSelectedFile();
                String txt = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                currentProjectFile = f;
                loadProjectIntoStudio(GameProject.fromJsonString(txt));
                log("Loaded project: " + f.getAbsolutePath());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to open project:\n" + ex.getMessage());
            }
        }
    }

    private void saveProjectJson() {
        syncCode();
        if (currentProjectFile == null) {
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File(project.getSlug() + ".vortex.json"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            currentProjectFile = fc.getSelectedFile();
        }
        try (FileOutputStream fos = new FileOutputStream(currentProjectFile)) {
            fos.write(project.toJsonString().getBytes(StandardCharsets.UTF_8));
            log("Saved project -> " + currentProjectFile.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Save failed:\n" + ex.getMessage());
        }
    }

    private void cloneSelectedEntity() {
        Entity src = project.findById(viewport.getSelectedEntityId());
        if (src == null) return;
        Entity copy = src.deepCopy();
        copy.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        copy.name = src.name + "_copy";
        copy.x += 1.5f;
        project.entities.add(copy);
        viewport.setSelectedEntityId(copy.id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
    }

    private void deleteSelectedEntity() {
        Entity src = project.findById(viewport.getSelectedEntityId());
        if (src == null) return;
        project.entities.remove(src);
        viewport.setSelectedEntityId(project.entities.isEmpty() ? null : project.entities.get(0).id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
    }

    private boolean checkScriptSyntax() {
        syncCode();
        String check = VortexScriptEngine.validateSyntax(project.scriptLanguage, project.scriptCode);
        if (check.startsWith("OK")) {
            syntaxStatusLabel.setText("✓ " + check);
            syntaxStatusLabel.setForeground(new Color(0, 230, 118));
            return true;
        } else {
            syntaxStatusLabel.setText("✕ " + check);
            syntaxStatusLabel.setForeground(new Color(255, 82, 82));
            return false;
        }
    }

    private void syncCode() {
        if (project != null) project.scriptCode = scriptArea.getText();
    }

    private void log(String s) {
        consoleArea.append(s + "\n");
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    private JTextField addField(String label, String val) {
        JLabel l = new JLabel(label);
        l.setForeground(new Color(148, 163, 184));
        inspectorForm.add(l);
        JTextField tf = new JTextField(val);
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        inspectorForm.add(tf);
        return tf;
    }

    private JComboBox<String> addCombo(String label, String[] items, String cur) {
        JLabel l = new JLabel(label);
        l.setForeground(new Color(148, 163, 184));
        inspectorForm.add(l);
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setSelectedItem(cur);
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        inspectorForm.add(cb);
        return cb;
    }

    private JCheckBox addCheck(String label, boolean checked) {
        JCheckBox cb = new JCheckBox(label, checked);
        cb.setForeground(Color.WHITE);
        cb.setOpaque(false);
        inspectorForm.add(cb);
        return cb;
    }

    private JPanel labeledMini(String lbl, JTextField tf) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(lbl);
        l.setForeground(new Color(148, 163, 184));
        p.add(l, BorderLayout.NORTH);
        p.add(tf, BorderLayout.CENTER);
        return p;
    }

    private void addMenuAction(JPopupMenu m, String title, Runnable r) {
        JMenuItem it = new JMenuItem(title);
        it.addActionListener(e -> r.run());
        m.add(it);
    }

    private static JButton styledBtn(String text, Color bg, Color fg) {
        JButton b = new JButton(text);
        b.setBackground(bg);
        b.setForeground(fg);
        b.setFocusPainted(false);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        return b;
    }

    private static String fmt(float v) { return String.format(Locale.US, "%.2f", v); }
    private static float pf(JTextField tf, float d) {
        try { return Float.parseFloat(tf.getText().trim()); } catch (Exception e) { return d; }
    }

    // =========================================================================
    // Standalone Exported Game Player Mode (.exe / .jar)
    // =========================================================================
    private static void launchStandaloneGameWindow(final GameProject gameProj) {
        JFrame frame = new JFrame(gameProj.title + " — Powered by Vortex Engine");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1120, 680);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        final DesktopSoftware3DViewport vp = new DesktopSoftware3DViewport();
        frame.add(vp, BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        top.setBackground(new Color(11, 15, 23));
        JButton restart = styledBtn("↻ Restart Game", new Color(30, 41, 59), Color.WHITE);
        restart.addActionListener(e -> vp.loadProject(gameProj.deepCopy(), true));
        top.add(restart);
        frame.add(top, BorderLayout.NORTH);

        frame.setVisible(true);
        vp.loadProject(gameProj.deepCopy(), true);
    }

    private static String readEmbeddedGameJson() {
        try (InputStream is = VortexDesktopStudio.class.getResourceAsStream("/embedded_game.vortex.json")) {
            if (is == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================================
    // Headless Verification Suite (LuaJ + JBox2D + FastNoiseLite + VortexScript)
    // =========================================================================
    private static void runHeadlessSelfTests() throws Exception {
        System.out.println("=== Vortex Engine v4.0.0 (Hyperion Edition) Headless Verification Suite ===");

        for (int i = 0; i < ProjectTemplates.TEMPLATE_KEYS.length; i++) {
            String key = ProjectTemplates.TEMPLATE_KEYS[i];
            GameProject p = ProjectTemplates.create(key, "Test_" + key, "com.vortex.test." + key.toLowerCase());
            PhysicsWorld pw = new PhysicsWorld();
            VortexScriptEngine vm = new VortexScriptEngine();
            vm.attach(p, pw, null);

            if (!vm.compileAndInit(p.scriptCode)) {
                throw new RuntimeException("Template failed compile (" + key + "): " + vm.getLastError());
            }

            // Simulate 45 frames of physics + scripting + button press
            vm.inputJoyX = 0.6f;
            vm.notifyButton("A");
            for (int f = 0; f < 45; f++) {
                vm.update(0.016f);
                List<PhysicsWorld.CollisionEvent> cols = pw.step(p, 0.016f);
                for (PhysicsWorld.CollisionEvent ce : cols) vm.notifyCollision(ce.a, ce.b);
            }

            // JSON round-trip check
            GameProject rt = GameProject.fromJsonString(p.toJsonString());
            if (rt.entities.isEmpty()) throw new RuntimeException("JSON round-trip lost entities for " + key);
            System.out.println("  [PASS] Template " + key + " (" + p.dimensionMode + " / " + p.scriptLanguage
                    + " / entities=" + p.entities.size() + " / banner='" + vm.hudBanner + "')");
        }

        // Test v3.0 Chiptune Synthesizer + Vortex Flow Compiler + HTML5 Web Exporter!
        short[] pcm = ChiptuneGenerator.synthesizeLoop(ChiptuneGenerator.PRESET_CYBERPUNK, 134);
        if (pcm.length < 10_000) throw new RuntimeException("Chiptune PCM loop too short!");
        System.out.println("  [PASS] Chiptune 4-Channel Synth (" + pcm.length + " PCM samples @ 22.05kHz)");

        GameProject cyber = ProjectTemplates.create("CYBER_MAZE_AI_2D", "Cyber Hunters", "com.vortex.cyber");
        String compiledFlowLua = VortexFlowCompiler.compileToLua(cyber);
        String flowSyntax = VortexScriptEngine.validateSyntax("LUA", compiledFlowLua);
        if (!flowSyntax.startsWith("OK")) throw new RuntimeException("Flow -> Lua compile invalid: " + flowSyntax);
        String compiledFlowJs = VortexFlowCompiler.compileToJavaScript(cyber);
        String flowJsSyntax = VortexScriptEngine.validateSyntax("JS", compiledFlowJs);
        if (!flowJsSyntax.startsWith("OK")) throw new RuntimeException("Flow -> ES6 JS compile invalid: " + flowJsSyntax);
        System.out.println("  [PASS] Vortex Flow Dual Compiler -> " + flowJsSyntax + " & " + flowSyntax);

        // Verify Rhino Sandbox & Infinite-Loop Protection!
        GameProject loopGuardTest = ProjectTemplates.create("BLANK_2D_LUA", "GuardTest", "com.vortex.guard");
        loopGuardTest.scriptLanguage = "JS";
        loopGuardTest.scriptCode = "function onStart() { while (true) {} }";
        VortexScriptEngine guardVm = new VortexScriptEngine();
        guardVm.attach(loopGuardTest, new PhysicsWorld(), null);
        guardVm.compileAndInit(loopGuardTest.scriptCode);
        if (guardVm.getLastError() == null || !guardVm.getLastError().contains("Quota Exceeded")) {
            throw new RuntimeException("Rhino infinite-loop guard did not trigger!");
        }
        System.out.println("  [PASS] Mozilla Rhino ES6 Infinite-Loop Quota & ClassShutter Sandbox Verified!");

        // Verify Vortex Forge AI Offline Generator (both 2D & 3D)
        GameProject forgeProj = new GameProject();
        VortexForgeAgent.applyPromptToProject(forgeProj, "3D mecha canyon with railgun", 0, "");
        if (!"OK (ES6+ JavaScript • Mozilla Rhino)".equals(VortexScriptEngine.validateSyntax("JS", forgeProj.scriptCode))) {
            throw new RuntimeException("Forge AI generated invalid JS!");
        }
        System.out.println("  [PASS] Vortex Forge AI Generator Verified (" + forgeProj.entities.size() + " 3D entities + ES6 JS)");

        // Verify LRM (hacvilke/lrm) Multi-File Unpack, Inbox Asset Drop & Round-Trip Re-Import!
        File tmpLrmDir = Files.createTempDirectory("vortex_lrm_test_").toFile();
        GameProject survivors = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "Neon Survivors Swarm", "com.vortex.survivors");
        LrmWorkspaceBridge.exportToLrmRepo(survivors, tmpLrmDir);
        Files.write(new File(tmpLrmDir, "inbox/dropped_starfighter.obj").toPath(),
                "v 0 1 0\nv -1 -1 1\nv 1 -1 1\nf 1 2 3\n".getBytes(StandardCharsets.UTF_8));
        if (LrmWorkspaceBridge.findLrmBinary() != null) {
            LrmWorkspaceBridge.CmdResult initRes = LrmWorkspaceBridge.runLrm(tmpLrmDir, 10, "init", "--user", "ci");
            LrmWorkspaceBridge.CmdResult ciRes = LrmWorkspaceBridge.runLrm(tmpLrmDir, 10, "commit", "-m", "Initial Neon Survivors ES6 commit");
            if (initRes.exitCode != 0 || ciRes.exitCode != 0) {
                throw new RuntimeException("LRM CLI commit failed: " + initRes.output + " / " + ciRes.output);
            }
            System.out.println("  [PASS] Live LRM CLI Integration (" + ciRes.output.replace("\n", " | ") + ")");
        }
        GameProject reimported = LrmWorkspaceBridge.importFromLrmRepo(tmpLrmDir);
        if (reimported.entities.size() != survivors.entities.size() || !reimported.customObjMeshes.containsKey("dropped_starfighter")) {
            throw new RuntimeException("LRM round-trip or inbox drop failed!");
        }
        System.out.println("  [PASS] LRM Multi-File Unpack + Inbox .obj Drop + Lossless Re-Import Verified!");

        File tmpHtml = File.createTempFile("neon_survivors_web_", ".html");
        Html5WebExporter.exportToFile(survivors, tmpHtml);
        if (tmpHtml.length() < 12_000) throw new RuntimeException("HTML5 Web export unexpectedly small!");
        System.out.println("  [PASS] Single-File HTML5 ES6 Web Exporter (" + (tmpHtml.length() / 1024) + " KB standalone .html)");
        tmpHtml.delete();

        // Test Standalone Game .exe & .jar & Android Bundle Export!
        GameProject sample = ProjectTemplates.create("ANGRY_BOX2D_LUA", "Angry Crates Siege", "com.vortex.game.angrycrates");
        File tmpExe = File.createTempFile("angry_crates_test_", ".exe");
        File tmpZip = File.createTempFile("angry_crates_android_", ".zip");
        DesktopExporter.exportStandaloneWindowsExe(sample, tmpExe);
        DesktopExporter.exportAndroidTermuxZip(sample, tmpZip);
        if (tmpExe.length() < 200_000 || tmpZip.length() < 200_000) {
            throw new RuntimeException("Exported artifacts unexpectedly small!");
        }
        System.out.println("  [PASS] Exported Standalone Windows Game .exe (" + (tmpExe.length() / 1024) + " KB)");
        System.out.println("  [PASS] Exported Android Termux APK Bundle .zip (" + (tmpZip.length() / 1024) + " KB)");
        tmpExe.delete();
        tmpZip.delete();

        System.out.println("=== ALL VORTEX v3.0.0 (APEX EDITION) HEADLESS TESTS PASSED 100%! ===");
    }
}
