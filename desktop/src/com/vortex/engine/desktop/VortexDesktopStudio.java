package com.vortex.engine.desktop;

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

    public static void main(String[] args) throws Exception {
        // 1) Headless CI / Self-Test Mode (verifies LuaJ + JBox2D + FastNoiseLite + VortexScript + Exporters!)
        for (String arg : args) {
            if ("--headless-test".equals(arg)) {
                runHeadlessSelfTests();
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
        super("Vortex Engine Studio v2.0.0 — 2D & 3D Game Engine (Lua 5.2 • JBox2D • FastNoiseLite • Android/PC)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1380, 840);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(9, 13, 22));
        setLayout(new BorderLayout());

        project = ProjectTemplates.create("ANGRY_BOX2D_LUA", "Angry Crates Siege", "com.vortex.game.angrycrates");

        add(buildTopToolbar(), BorderLayout.NORTH);

        // Left: Scene Hierarchy + Entity Inspector
        JPanel leftPanel = buildHierarchyAndInspector();

        // Center: 60 FPS 2D/3D Viewport + Console Log
        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(6, 9, 15));
        consoleArea.setForeground(new Color(167, 243, 208));
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setText("Vortex Engine v2.0.0 Desktop Studio Initialized.\n"
                + "• Scripting: Lua 5.2 (LuaJ 3.0.1) + VortexScript (.vxs)\n"
                + "• Physics  : JBox2D 2.2.1.1 Rigid-Body Solver + 3D AABB & FastNoiseLite Terrain\n"
                + "• Controls : Play Mode uses WASD / Arrows + Space/J (Button A) + Shift/K (Button B) or Mouse Clicks!\n");
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

        JButton saveBtn = styledBtn("💾 Save .json", new Color(6, 78, 59), Color.WHITE);
        saveBtn.addActionListener(e -> saveProjectJson());
        bar.add(saveBtn);

        dimBtn = styledBtn("Mode: 2D", new Color(30, 27, 75), new Color(190, 200, 255));
        dimBtn.addActionListener(e -> {
            syncCode();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimBtn.setText("Mode: " + project.dimensionMode);
            viewport.resetCamera();
            viewport.repaint();
        });
        bar.add(dimBtn);

        JButton addObjBtn = styledBtn("＋ Add Entity ▾", new Color(30, 41, 59), new Color(56, 189, 248));
        addObjBtn.addActionListener(e -> showAddEntityMenu(addObjBtn));
        bar.add(addObjBtn);

        JButton pixelBtn = styledBtn("🎨 Pixel Art", new Color(45, 25, 65), new Color(225, 190, 255));
        pixelBtn.addActionListener(e -> openPixelArtStudio());
        bar.add(pixelBtn);

        JButton sfxBtn = styledBtn("🔊 SFX Lab", new Color(30, 41, 59), new Color(255, 213, 79));
        sfxBtn.addActionListener(e -> showSfxPopup(sfxBtn));
        bar.add(sfxBtn);

        playStopBtn = styledBtn("▶ PLAY GAME", new Color(0, 200, 83), Color.BLACK);
        playStopBtn.addActionListener(e -> togglePlayMode());
        bar.add(playStopBtn);

        JButton exportBtn = styledBtn("📦 Export (.exe / .jar / Android) ▾", new Color(88, 28, 135), Color.WHITE);
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

        langBtn = styledBtn("Lang: LUA", new Color(16, 37, 58), new Color(0, 229, 255));
        langBtn.addActionListener(e -> {
            syncCode();
            project.scriptLanguage = "LUA".equalsIgnoreCase(project.scriptLanguage) ? "VXS" : "LUA";
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
        addMenuAction(m, "🪟 Export Standalone Windows Game (.exe)", () -> exportWithDialog("exe"));
        addMenuAction(m, "🐧 Export Standalone Linux Binary", () -> exportWithDialog("linux"));
        addMenuAction(m, "☕ Export Cross-Platform Desktop Game (.jar)", () -> exportWithDialog("jar"));
        addMenuAction(m, "🤖 Export Android Termux APK Project Bundle (.zip)", () -> exportWithDialog("android"));
        m.show(invoker, 0, invoker.getHeight());
    }

    private void exportWithDialog(String kind) {
        JFileChooser fc = new JFileChooser();
        String slug = project.getSlug();
        String defaultName = slug + ("exe".equals(kind) ? "-Windows-x64.exe"
                : "linux".equals(kind) ? "-Linux-x86_64"
                : "jar".equals(kind) ? "-Desktop.jar"
                : "-Android-Termux.zip");
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = fc.getSelectedFile();
            try {
                if ("exe".equals(kind)) DesktopExporter.exportStandaloneWindowsExe(project, out);
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
        VortexScriptEngine tester = new VortexScriptEngine();
        tester.attach(project, null, null);
        boolean ok = tester.compileAndInit(project.scriptCode);
        if (ok) {
            syntaxStatusLabel.setText("✓ Syntax Valid (" + project.scriptLanguage + " VM)");
            syntaxStatusLabel.setForeground(new Color(0, 230, 118));
        } else {
            syntaxStatusLabel.setText("✕ " + tester.getLastError());
            syntaxStatusLabel.setForeground(new Color(255, 82, 82));
        }
        return ok;
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
        System.out.println("=== Vortex Engine v2.0.0 Headless Verification Suite ===");

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

        System.out.println("=== ALL VORTEX v2.0.0 HEADLESS TESTS PASSED 100%! ===");
    }
}
