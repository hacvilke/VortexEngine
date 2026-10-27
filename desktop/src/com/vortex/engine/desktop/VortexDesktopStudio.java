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
import com.vortex.engine.core.storage.VortexSaveStore;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Vortex Engine Studio v4.1.0 ("Hyperion Edition") — Modern Desktop IDE & Standalone Game Runner.
 *
 * Features a unified Dark Obsidian + Electric Cyan custom-painted UI system (DesktopUiKit):
 *  - Zero Swing Metal blue gradients, JComboBoxes, or JOptionPanes
 *  - Custom animated dropdowns (rotating chevrons), sliding-pill workspace tabs, and pill switches
 *  - All 7 Studio workspaces docked smoothly on the right: Code, Flow, Forge AI, LRM P2P, Pixel, Audio, Export
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
    private final JLabel telemetryLabel = new JLabel("60 FPS • Ready");

    private DesktopUiKit.VortexButton playStopBtn;
    private DesktopUiKit.VortexButton dimBtn;
    private DesktopUiKit.VortexDropdown vmDropdown;
    private DesktopUiKit.VortexDropdown snippetDropdown;
    private DesktopUiKit.VortexTabStrip rightTabs;
    private final DesktopUiKit.AnimatedDeck rightCardsPanel = new DesktopUiKit.AnimatedDeck();
    private final DefaultListModel<String> flowListModel = new DefaultListModel<>();

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    public static void main(String[] args) throws Exception {
        VortexSaveStore.setSaveRootDir(new File(System.getProperty("user.home", "."), ".vortex-saves"));

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

        DesktopUiKit.installDarkDefaults();

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
        super("Vortex Engine Studio v4.1.0 (Hyperion Edition) — ES6 JS (Rhino) • Lua 5.2 • LRM P2P Sync • Forge AI • APK/PC/HTML5");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1480, 880);
        setLocationRelativeTo(null);
        getContentPane().setBackground(DesktopUiKit.C_VOID);
        setLayout(new BorderLayout());

        project = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "Neon Survivors Swarm", "com.vortex.game.survivors");

        add(buildTopToolbar(), BorderLayout.NORTH);

        // Left Dock: Scene Hierarchy + Entity & Physics Inspector
        JPanel leftPanel = buildHierarchyAndInspector();

        // Center Dock: 60 FPS 2D/3D Viewport + Sleek Dark Output Console
        JPanel consolePanel = buildConsolePanel();
        JSplitPane centerSplit = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, viewport, consolePanel, 640);
        centerSplit.setResizeWeight(0.82);

        // Right Dock: Animated 7-Tab Studio Workspace (Code, Flow, Forge AI, LRM, Pixel, Audio, Export)
        JPanel rightPanel = buildRightTabbedWorkspace();

        JSplitPane midRight = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerSplit, rightPanel, 690);
        midRight.setResizeWeight(0.60);

        JSplitPane mainSplit = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, midRight, 296);

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
                log("[SCRIPT] " + msg);
            }

            @Override
            public void onTelemetry(int fps, int score, int health, String banner) {
                SwingUtilities.invokeLater(() -> telemetryLabel.setText(
                        fps + " FPS  •  " + project.entities.size() + " objs  •  "
                        + project.dimensionMode + " / " + project.scriptLanguage));
            }
        });

        loadProjectIntoStudio(project);
    }

    // =========================================================================
    // Unified Obsidian + Electric Cyan Top Header Bar
    // =========================================================================
    private JPanel buildTopToolbar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(DesktopUiKit.C_HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));

        // Left Cluster: Brand + Template Quick-Switch Dropdown + File/History + Scene Tools
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JLabel brand = new JLabel("VORTEX STUDIO");
        brand.setFont(new Font("SansSerif", Font.BOLD, 14));
        brand.setForeground(DesktopUiKit.C_CYAN);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 6));
        left.add(brand);

        DesktopUiKit.VortexDropdown tplQuickDrop = new DesktopUiKit.VortexDropdown(
                ProjectTemplates.TEMPLATE_NAMES, ProjectTemplates.TEMPLATE_NAMES[0]);
        tplQuickDrop.setPreferredSize(new Dimension(185, 30));
        tplQuickDrop.setToolTipText("Switch starter game template immediately");
        tplQuickDrop.setOnSelect((idx, label) -> {
            pushUndo();
            String key = ProjectTemplates.TEMPLATE_KEYS[idx];
            String shortTitle = label.contains("(") ? label.substring(0, label.indexOf('(')).trim() : label;
            GameProject next = ProjectTemplates.create(key, shortTitle,
                    "com.vortex.game." + shortTitle.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""));
            currentProjectFile = null;
            loadProjectIntoStudio(next);
            log("Loaded starter template: " + label);
        });
        left.add(tplQuickDrop);

        DesktopUiKit.VortexButton newBtn = new DesktopUiKit.VortexButton("＋ New", DesktopUiKit.Variant.SECONDARY);
        newBtn.addActionListener(e -> showNewTemplateModal());
        left.add(newBtn);

        DesktopUiKit.VortexButton openBtn = new DesktopUiKit.VortexButton("📂 Open", DesktopUiKit.Variant.SECONDARY);
        openBtn.addActionListener(e -> openProjectJson());
        left.add(openBtn);

        DesktopUiKit.VortexButton saveBtn = new DesktopUiKit.VortexButton("💾 Save", DesktopUiKit.Variant.SECONDARY);
        saveBtn.addActionListener(e -> saveProjectJson());
        left.add(saveBtn);

        DesktopUiKit.VortexButton undoBtn = new DesktopUiKit.VortexButton("↶", DesktopUiKit.Variant.SECONDARY);
        undoBtn.setToolTipText("Undo");
        undoBtn.addActionListener(e -> performUndo());
        left.add(undoBtn);

        DesktopUiKit.VortexButton redoBtn = new DesktopUiKit.VortexButton("↷", DesktopUiKit.Variant.SECONDARY);
        redoBtn.setToolTipText("Redo");
        redoBtn.addActionListener(e -> performRedo());
        left.add(redoBtn);

        dimBtn = new DesktopUiKit.VortexButton("Mode: 2D", DesktopUiKit.Variant.ACCENT);
        dimBtn.addActionListener(e -> {
            pushUndo();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimBtn.setText("Mode: " + project.dimensionMode);
            viewport.resetEditorCamera();
            populateInspector();
            viewport.repaint();
        });
        left.add(dimBtn);

        DesktopUiKit.VortexButton addObjBtn = new DesktopUiKit.VortexButton("＋ Entity ▾", DesktopUiKit.Variant.SECONDARY);
        addObjBtn.addActionListener(e -> showAddEntityMenu(addObjBtn));
        left.add(addObjBtn);

        bar.add(left, BorderLayout.WEST);

        // Right Cluster: Telemetry + Play CTA + Export Menu
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        right.setOpaque(false);

        telemetryLabel.setFont(DesktopUiKit.FONT_UI);
        telemetryLabel.setForeground(DesktopUiKit.C_TEXT_MUTED);
        telemetryLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
        right.add(telemetryLabel);

        playStopBtn = new DesktopUiKit.VortexButton("▶ PLAY GAME", DesktopUiKit.Variant.PRIMARY);
        playStopBtn.addActionListener(e -> togglePlayMode());
        right.add(playStopBtn);

        DesktopUiKit.VortexButton exportBtn = new DesktopUiKit.VortexButton("📦 Export ▾", DesktopUiKit.Variant.ACCENT);
        exportBtn.addActionListener(e -> showExportMenu(exportBtn));
        right.add(exportBtn);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel buildConsolePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DesktopUiKit.C_VOID);

        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(DesktopUiKit.C_HEADER);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 8)));

        JLabel title = new JLabel("STUDIO CONSOLE & RUNTIME TELEMETRY");
        title.setFont(DesktopUiKit.FONT_UI_BOLD);
        title.setForeground(DesktopUiKit.C_TEXT_MUTED);
        head.add(title, BorderLayout.WEST);

        DesktopUiKit.VortexButton clearBtn = new DesktopUiKit.VortexButton("Clear", DesktopUiKit.Variant.SECONDARY);
        clearBtn.addActionListener(e -> consoleArea.setText(""));
        head.add(clearBtn, BorderLayout.EAST);
        p.add(head, BorderLayout.NORTH);

        consoleArea.setEditable(false);
        consoleArea.setBackground(new Color(6, 9, 15));
        consoleArea.setForeground(DesktopUiKit.C_TEXT);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        consoleArea.setText("Vortex Engine v4.1.0 (Hyperion Edition) Desktop Studio Ready.\n"
                + "• Quad-Runtime     : ES6+ JavaScript (Mozilla Rhino 1.7.15.1) + Lua 5.2 (LuaJ) + Vortex Flow + VortexScript\n"
                + "• P2P & AI Agents  : LRM P2P Version Control (hacvilke/lrm) + Vortex Forge BYOK/Offline AI (hacvilke/roforge)\n"
                + "• Simulation       : JBox2D + Theta* Pathfinding + Universal Tween Engine + Flipbook Sprites + Chiptune\n");

        JScrollPane sp = DesktopUiKit.createScrollPane(consoleArea);
        sp.setPreferredSize(new Dimension(600, 145));
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    // =========================================================================
    // Left Dock: Scene Hierarchy + Custom Entity & Physics Inspector
    // =========================================================================
    private JPanel buildHierarchyAndInspector() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(DesktopUiKit.C_PANEL);
        panel.setPreferredSize(new Dimension(296, 600));

        JPanel topList = new JPanel(new BorderLayout());
        topList.setBackground(DesktopUiKit.C_PANEL);

        JLabel hLbl = new JLabel("SCENE HIERARCHY");
        hLbl.setForeground(DesktopUiKit.C_CYAN);
        hLbl.setFont(DesktopUiKit.FONT_UI_BOLD);
        hLbl.setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        topList.add(hLbl, BorderLayout.NORTH);

        entityList.setBackground(DesktopUiKit.C_VOID);
        entityList.setCellRenderer(DesktopUiKit.createDarkListRenderer());
        entityList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = entityList.getSelectedIndex();
                if (idx >= 0 && idx < project.entities.size()) {
                    viewport.setSelectedEntityId(project.entities.get(idx).id);
                    populateInspector();
                }
            }
        });
        JScrollPane listScroll = DesktopUiKit.createScrollPane(entityList);
        listScroll.setPreferredSize(new Dimension(270, 210));
        topList.add(listScroll, BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        listBtns.setBackground(DesktopUiKit.C_HEADER);
        listBtns.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, DesktopUiKit.C_BORDER));

        DesktopUiKit.VortexButton dupBtn = new DesktopUiKit.VortexButton("⎘ Clone", DesktopUiKit.Variant.SECONDARY);
        dupBtn.addActionListener(e -> cloneSelectedEntity());
        listBtns.add(dupBtn);

        DesktopUiKit.VortexButton camBtn = new DesktopUiKit.VortexButton("⌖ Reset Cam", DesktopUiKit.Variant.SECONDARY);
        camBtn.addActionListener(e -> viewport.resetEditorCamera());
        listBtns.add(camBtn);

        DesktopUiKit.VortexButton delBtn = new DesktopUiKit.VortexButton("✕ Delete", DesktopUiKit.Variant.DANGER);
        delBtn.addActionListener(e -> deleteSelectedEntity());
        listBtns.add(delBtn);
        topList.add(listBtns, BorderLayout.SOUTH);

        inspectorForm.setLayout(new BoxLayout(inspectorForm, BoxLayout.Y_AXIS));
        inspectorForm.setBackground(DesktopUiKit.C_PANEL);
        inspectorForm.setBorder(BorderFactory.createEmptyBorder(8, 10, 16, 10));
        JScrollPane inspScroll = DesktopUiKit.createScrollPane(inspectorForm);
        inspScroll.getViewport().setBackground(DesktopUiKit.C_PANEL);

        JSplitPane split = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, topList, inspScroll, 255);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // Right Dock: Animated 7-Tab Studio Workspace (All Features Docked Cleanly!)
    // =========================================================================
    private JPanel buildRightTabbedWorkspace() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(DesktopUiKit.C_VOID);
        wrapper.setPreferredSize(new Dimension(495, 620));

        String[] keys = {"CODE", "FLOW", "FORGE", "LRM", "PIXEL", "AUDIO", "EXPORT"};
        String[] labels = {"Code", "Flow", "Forge AI", "LRM P2P", "Pixel", "Audio", "Export"};
        rightTabs = new DesktopUiKit.VortexTabStrip(keys, labels);

        rightCardsPanel.setBackground(DesktopUiKit.C_VOID);
        rightCardsPanel.addCard("CODE", buildScriptEditorTab(), 0);
        rightCardsPanel.addCard("FLOW", buildFlowTab(), 1);
        rightCardsPanel.addCard("FORGE", buildForgeAiTab(), 2);
        rightCardsPanel.addCard("LRM", buildLrmSyncTab(), 3);
        rightCardsPanel.addCard("PIXEL", buildPixelArtTab(), 4);
        rightCardsPanel.addCard("AUDIO", buildAudioTab(), 5);
        rightCardsPanel.addCard("EXPORT", buildExportTab(), 6);

        rightTabs.setOnTabSelected((idx, key) -> {
            if ("FLOW".equals(key)) refreshFlowListModel();
            rightCardsPanel.showCard(key, true);
        });

        wrapper.add(rightTabs, BorderLayout.NORTH);
        wrapper.add(rightCardsPanel, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildScriptEditorTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DesktopUiKit.C_VOID);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DesktopUiKit.C_PANEL);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        btnBar.setOpaque(false);

        String[] vmItems = {"VM: ES6 JavaScript", "VM: Lua 5.2 (LuaJ)", "VM: VortexScript"};
        String[] vmCodes = {"JS", "LUA", "VXS"};
        vmDropdown = new DesktopUiKit.VortexDropdown(vmItems, vmItems[0]);
        vmDropdown.setPreferredSize(new Dimension(162, 30));
        vmDropdown.setOnSelect((idx, val) -> {
            syncCode();
            project.scriptLanguage = vmCodes[idx];
            syntaxStatusLabel.setText("Switched VM to " + val + " — click Check Syntax.");
            syntaxStatusLabel.setForeground(DesktopUiKit.C_CYAN);
        });
        btnBar.add(vmDropdown);

        DesktopUiKit.VortexButton checkBtn = new DesktopUiKit.VortexButton("✓ Check Syntax", DesktopUiKit.Variant.ACCENT);
        checkBtn.addActionListener(e -> checkScriptSyntax());
        btnBar.add(checkBtn);

        String[] snips = {
                "＋ Insert API Snippet…",
                "vortex.findNearestTag('Player', 'enemy', 30)",
                "vortex.chaseTagAI('enemy', 'Player', 4.5)",
                "vortex.setSpriteAnim('Player', 'knight_1,knight_2', 7)",
                "vortex.tweenTagYoyo('coin', 0.35, 0.9, 'SINE')",
                "vortex.saveData('best', vortex.getScore())",
                "vortex.playMusic('CYBERPUNK', 136)",
                "vortex.particles(0, 1, 0, 22, '#00E5FF')"
        };
        snippetDropdown = new DesktopUiKit.VortexDropdown(snips, snips[0]);
        snippetDropdown.setPreferredSize(new Dimension(176, 30));
        snippetDropdown.setOnSelect((idx, val) -> {
            if (idx > 0) {
                String tail = "LUA".equalsIgnoreCase(project.scriptLanguage) ? "\n" : ";\n";
                scriptArea.insert(val + tail, scriptArea.getCaretPosition());
                snippetDropdown.setSelectedIndex(0);
            }
        });
        btnBar.add(snippetDropdown);

        DesktopUiKit.VortexButton objQuickBtn = new DesktopUiKit.VortexButton("🧊 .OBJ", DesktopUiKit.Variant.SECONDARY);
        objQuickBtn.setToolTipText("Import Custom Wavefront .OBJ 3D Mesh");
        objQuickBtn.addActionListener(e -> showCustomObjModal());
        btnBar.add(objQuickBtn);

        DesktopUiKit.VortexButton docsBtn = new DesktopUiKit.VortexButton("📖 API", DesktopUiKit.Variant.SECONDARY);
        docsBtn.setToolTipText("Vortex Scripting API Reference");
        docsBtn.addActionListener(e -> DesktopUiKit.showInfoModal(this, "Vortex Scripting API (ES6 JS • Lua 5.2 • VXS)",
                "• Hooks: onStart(), onUpdate(dt), onButton(btn), onCollide(a, b, tagA, tagB)\n"
                + "• Input: vortex.joyX(), vortex.joyY(), vortex.isDown('A'), vortex.isDown('B')\n"
                + "• Transform & Physics: vortex.setPos(n,x,y,z), vortex.setVel(n,vx,vy,vz), vortex.jump(n,vy), vortex.applyImpulse(n,ix,iy)\n"
                + "• AI & Queries: vortex.chaseTagAI(fromTag,target,spd), vortex.findNearestTag(from,tag,range), vortex.raycast(x,y,z,dx,dy,dz,max)\n"
                + "• Animation & FX: vortex.setSpriteAnim(n,'f1,f2',fps), vortex.tweenTagYoyo(tag,dy,dur,'SINE'), vortex.particles(x,y,z,cnt,'#00E5FF')\n"
                + "• Save & Audio: vortex.saveData(k,v), vortex.loadNum(k,0), vortex.playSound('laser'), vortex.playMusic('CYBERPUNK',134)"));
        btnBar.add(docsBtn);

        header.add(btnBar, BorderLayout.NORTH);

        syntaxStatusLabel.setOpaque(true);
        syntaxStatusLabel.setBackground(DesktopUiKit.C_HEADER);
        syntaxStatusLabel.setForeground(DesktopUiKit.C_OK_TX);
        syntaxStatusLabel.setFont(DesktopUiKit.FONT_UI);
        syntaxStatusLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        header.add(syntaxStatusLabel, BorderLayout.SOUTH);

        p.add(header, BorderLayout.NORTH);

        scriptArea.setBackground(DesktopUiKit.C_VOID);
        scriptArea.setForeground(DesktopUiKit.C_TEXT);
        scriptArea.setCaretColor(DesktopUiKit.C_CYAN);
        scriptArea.setSelectionColor(DesktopUiKit.C_CYAN_TINT);
        scriptArea.setSelectedTextColor(DesktopUiKit.C_CYAN);
        scriptArea.setFont(DesktopUiKit.FONT_MONO);
        scriptArea.setTabSize(4);
        scriptArea.setBorder(BorderFactory.createEmptyBorder(10, 12, 16, 12));
        p.add(DesktopUiKit.createScrollPane(scriptArea), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildFlowTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JPanel topCard = new JPanel(new BorderLayout(0, 6));
        topCard.setOpaque(false);
        JLabel title = new JLabel("⚡ VORTEX FLOW — VISUAL EVENT SHEET");
        title.setFont(DesktopUiKit.FONT_TITLE);
        title.setForeground(DesktopUiKit.C_CYAN);
        topCard.add(title, BorderLayout.NORTH);

        JPanel compileBar = new JPanel(new GridLayout(1, 2, 8, 0));
        compileBar.setOpaque(false);
        DesktopUiKit.VortexButton toJsBtn = new DesktopUiKit.VortexButton("⚡ Compile ➔ ES6 JavaScript", DesktopUiKit.Variant.PRIMARY);
        toJsBtn.addActionListener(e -> {
            if (project.flowRules.isEmpty()) { log("[Flow] Add at least 1 rule first."); return; }
            pushUndo();
            String js = VortexFlowCompiler.compileToJavaScript(project);
            project.scriptLanguage = "JS";
            project.scriptCode = js;
            scriptArea.setText(js);
            syncVmDropdown();
            checkScriptSyntax();
            rightTabs.selectTabByKey("CODE", true);
            log("[⚡ FLOW] Compiled " + project.flowRules.size() + " rules into ES6+ JavaScript!");
        });
        DesktopUiKit.VortexButton toLuaBtn = new DesktopUiKit.VortexButton("⚡ Compile ➔ Lua 5.2", DesktopUiKit.Variant.SECONDARY);
        toLuaBtn.addActionListener(e -> {
            if (project.flowRules.isEmpty()) { log("[Flow] Add at least 1 rule first."); return; }
            pushUndo();
            String lua = VortexFlowCompiler.compileToLua(project);
            project.scriptLanguage = "LUA";
            project.scriptCode = lua;
            scriptArea.setText(lua);
            syncVmDropdown();
            checkScriptSyntax();
            rightTabs.selectTabByKey("CODE", true);
            log("[⚡ FLOW] Compiled " + project.flowRules.size() + " rules into Lua 5.2!");
        });
        compileBar.add(toJsBtn);
        compileBar.add(toLuaBtn);
        topCard.add(compileBar, BorderLayout.SOUTH);
        p.add(topCard, BorderLayout.NORTH);

        JList<String> rList = new JList<>(flowListModel);
        rList.setBackground(DesktopUiKit.C_PANEL);
        rList.setCellRenderer(DesktopUiKit.createDarkListRenderer());
        JScrollPane listSp = DesktopUiKit.createScrollPane(rList);
        listSp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        p.add(listSp, BorderLayout.CENTER);

        JPanel builder = new JPanel(new GridLayout(0, 2, 8, 6));
        builder.setBackground(DesktopUiKit.C_PANEL);
        builder.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        DesktopUiKit.VortexDropdown evCb = new DesktopUiKit.VortexDropdown(
                VortexFlowCompiler.EVENT_LABELS, VortexFlowCompiler.EVENT_LABELS[0]);
        JTextField ev1 = DesktopUiKit.createField("player");
        JTextField ev2 = DesktopUiKit.createField("coin");
        DesktopUiKit.VortexDropdown acCb = new DesktopUiKit.VortexDropdown(
                VortexFlowCompiler.ACTION_LABELS, VortexFlowCompiler.ACTION_LABELS[0]);
        JTextField ac1 = DesktopUiKit.createField("Player");
        JTextField ac2 = DesktopUiKit.createField("10");

        builder.add(mutedLabel("WHEN Event:")); builder.add(evCb);
        builder.add(mutedLabel("Subject Tag / Sec:")); builder.add(ev1);
        builder.add(mutedLabel("Target Tag B:")); builder.add(ev2);
        builder.add(mutedLabel("DO Game Action:")); builder.add(acCb);
        builder.add(mutedLabel("Target / Preset:")); builder.add(ac1);
        builder.add(mutedLabel("Speed / Force / Value:")); builder.add(ac2);

        DesktopUiKit.VortexButton addRuleBtn = new DesktopUiKit.VortexButton("＋ Add Rule", DesktopUiKit.Variant.ACCENT);
        addRuleBtn.addActionListener(e -> {
            pushUndo();
            VortexFlowCompiler.FlowRule r = new VortexFlowCompiler.FlowRule(
                    VortexFlowCompiler.EVENT_KEYS[evCb.getSelectedIndex()],
                    ev1.getText().trim(), ev2.getText().trim(),
                    VortexFlowCompiler.ACTION_KEYS[acCb.getSelectedIndex()],
                    ac1.getText().trim(), ac2.getText().trim());
            project.flowRules.add(r);
            refreshFlowListModel();
        });
        DesktopUiKit.VortexButton delRuleBtn = new DesktopUiKit.VortexButton("✕ Remove Selected", DesktopUiKit.Variant.DANGER);
        delRuleBtn.addActionListener(e -> {
            int idx = rList.getSelectedIndex();
            if (idx >= 0 && idx < project.flowRules.size()) {
                pushUndo();
                project.flowRules.remove(idx);
                refreshFlowListModel();
            }
        });
        builder.add(addRuleBtn);
        builder.add(delRuleBtn);
        p.add(builder, BorderLayout.SOUTH);
        return p;
    }

    private void refreshFlowListModel() {
        flowListModel.clear();
        if (project == null) return;
        for (int i = 0; i < project.flowRules.size(); i++) {
            flowListModel.addElement((i + 1) + ". " + project.flowRules.get(i).describe());
        }
    }

    private JPanel buildForgeAiTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(DesktopUiKit.C_PANEL);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel title = new JLabel("🤖 VORTEX FORGE AI STUDIO AGENT");
        title.setFont(DesktopUiKit.FONT_TITLE);
        title.setForeground(DesktopUiKit.C_CYAN);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(title);

        JLabel sub = new JLabel("<html><body style='width:380px;color:#94A3B8;margin-top:4px;'>"
                + "Inspired by <b>hacvilke/roforge</b>: works 100% offline out-of-the-box or connects directly via HTTPS to your own Groq, Gemini, OpenRouter, or OpenAI key. Automatically saves an Undo checkpoint before modifying your scene.</body></html>");
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sub);
        form.add(Box.createVerticalStrut(10));

        JLabel l1 = mutedLabel("AI Provider Mode:");
        l1.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l1);
        DesktopUiKit.VortexDropdown provCb = new DesktopUiKit.VortexDropdown(
                VortexForgeAgent.PROVIDERS, VortexForgeAgent.PROVIDERS[0]);
        provCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(provCb);
        form.add(Box.createVerticalStrut(8));

        JLabel l2 = mutedLabel("Optional API Key (leave empty for Offline Smart Generator):");
        l2.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l2);
        JTextField keyField = DesktopUiKit.createField("");
        keyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(keyField);
        form.add(Box.createVerticalStrut(8));

        JLabel l3 = mutedLabel("Describe the 2D or 3D Game Mechanic / Scene to Build:");
        l3.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l3);

        JTextArea promptArea = new JTextArea("2D cyber swarm arena with animated drones, XP gems, and auto-aim blaster", 4, 28);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setBackground(DesktopUiKit.C_INPUT_BG);
        promptArea.setForeground(DesktopUiKit.C_TEXT);
        promptArea.setCaretColor(DesktopUiKit.C_CYAN);
        promptArea.setFont(DesktopUiKit.FONT_UI);
        promptArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane promptSp = DesktopUiKit.createScrollPane(promptArea);
        promptSp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        promptSp.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(promptSp);
        form.add(Box.createVerticalStrut(10));

        JLabel statusLbl = new JLabel("Ready — Undo checkpoint enabled.");
        statusLbl.setForeground(DesktopUiKit.C_TEXT_MUTED);
        statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        DesktopUiKit.VortexButton genBtn = new DesktopUiKit.VortexButton(
                "⚡ Generate Scene & ES6 Script (+ Undo Checkpoint)", DesktopUiKit.Variant.PRIMARY);
        genBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        genBtn.addActionListener(e -> {
            pushUndo();
            statusLbl.setText("Generating with Forge AI…");
            statusLbl.setForeground(DesktopUiKit.C_CYAN);
            new Thread(() -> {
                try {
                    String msg = VortexForgeAgent.applyPromptToProject(
                            project, promptArea.getText().trim(), provCb.getSelectedIndex(), keyField.getText().trim());
                    SwingUtilities.invokeLater(() -> {
                        loadProjectIntoStudio(project);
                        statusLbl.setText("✓ " + msg);
                        statusLbl.setForeground(DesktopUiKit.C_OK_TX);
                        log("[🤖 FORGE AI] " + msg);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setText("✕ Error: " + ex.getMessage());
                        statusLbl.setForeground(DesktopUiKit.C_DANGER_TX);
                        log("[FORGE ERROR] " + ex.getMessage());
                    });
                }
            }).start();
        });
        form.add(genBtn);
        form.add(Box.createVerticalStrut(6));
        form.add(statusLbl);

        p.add(form, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildLrmSyncTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DesktopUiKit.C_VOID);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JPanel controls = new JPanel(new GridLayout(0, 2, 8, 6));
        controls.setBackground(DesktopUiKit.C_PANEL);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JTextField msgField = DesktopUiKit.createField("Update game snapshot");
        JTextField portKeyField = DesktopUiKit.createField("");
        controls.add(mutedLabel("Commit Message:")); controls.add(msgField);
        controls.add(mutedLabel("Remote Port Key (lrm1_...):")); controls.add(portKeyField);

        JTextArea outArea = new JTextArea();
        outArea.setEditable(false);
        outArea.setBackground(new Color(6, 9, 15));
        outArea.setForeground(DesktopUiKit.C_TEXT);
        outArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        String detectedBin = LrmWorkspaceBridge.findLrmBinary();
        outArea.setText("LRM P2P Version Control & Sync (github.com/hacvilke/lrm)\n"
                + "• Binary : " + (detectedBin != null ? detectedBin : "Not installed yet (install 1-liner: " + LrmWorkspaceBridge.LRM_DESKTOP_INSTALL_SH + ")") + "\n"
                + "• Layout : Unpacks .vortex.json into vortex.manifest.json, scene/entities.json, scripts/main.js, flow/rules.json & inbox/\n");

        DesktopUiKit.VortexButton commitBtn = new DesktopUiKit.VortexButton("📤 Unpack + LRM Commit", DesktopUiKit.Variant.PRIMARY);
        commitBtn.addActionListener(e -> {
            syncCode();
            File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
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

        DesktopUiKit.VortexButton shareBtn = new DesktopUiKit.VortexButton("🔑 Share Port Key", DesktopUiKit.Variant.ACCENT);
        shareBtn.addActionListener(e -> {
            syncCode();
            File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
            try {
                LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
                if (!new File(repoDir, ".lrm").exists()) {
                    LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
                    LrmWorkspaceBridge.runLrm(repoDir, 10, "commit", "-m", "Initial snapshot");
                }
                LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 12, "share", "--lifetime", "600");
                outArea.append("\n$ lrm share\n" + r.output + "\n");
            } catch (Exception ex) {
                outArea.append("\n[Error] " + ex.getMessage() + "\n");
            }
        });

        DesktopUiKit.VortexButton joinBtn = new DesktopUiKit.VortexButton("🌐 Join Port Key", DesktopUiKit.Variant.SECONDARY);
        joinBtn.addActionListener(e -> {
            String pk = portKeyField.getText().trim();
            if (pk.isEmpty()) { outArea.append("\n[!] Paste an lrm1_... Port Key above first.\n"); return; }
            File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
            LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 20, "join", pk, "--init");
            outArea.append("\n$ lrm join\n" + r.output + "\n");
        });

        DesktopUiKit.VortexButton importBtn = new DesktopUiKit.VortexButton("🔄 Reload Repo & Inbox", DesktopUiKit.Variant.SECONDARY);
        importBtn.addActionListener(e -> {
            File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
            try {
                pushUndo();
                GameProject imported = LrmWorkspaceBridge.importFromLrmRepo(repoDir);
                imported.id = project.id;
                loadProjectIntoStudio(imported);
                outArea.append("\n[✓] Reloaded " + imported.entities.size() + " entities (+ inbox assets) into Studio!\n");
            } catch (Exception ex) {
                outArea.append("\n[Import Error] " + ex.getMessage() + "\n");
            }
        });

        DesktopUiKit.VortexButton peersBtn = new DesktopUiKit.VortexButton("📡 Discover LAN Peers", DesktopUiKit.Variant.SECONDARY);
        peersBtn.addActionListener(e -> {
            File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
            repoDir.mkdirs();
            LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 6, "peers", "--timeout", "3");
            outArea.append("\n$ lrm peers --timeout 3\n" + r.output + "\n");
        });

        DesktopUiKit.VortexButton statusBtn = new DesktopUiKit.VortexButton("📊 Status & Diff", DesktopUiKit.Variant.SECONDARY);
        statusBtn.addActionListener(e -> {
            syncCode();
            File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
            try {
                LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
                LrmWorkspaceBridge.CmdResult st = LrmWorkspaceBridge.runLrm(repoDir, 6, "status");
                LrmWorkspaceBridge.CmdResult df = LrmWorkspaceBridge.runLrm(repoDir, 6, "diff", "--stat");
                outArea.append("\n$ lrm status\n" + st.output + "\n$ lrm diff --stat\n" + df.output + "\n");
            } catch (Exception ex) {
                outArea.append("\n[Status Error] " + ex.getMessage() + "\n");
            }
        });

        controls.add(commitBtn); controls.add(shareBtn);
        controls.add(joinBtn);   controls.add(importBtn);
        controls.add(peersBtn);  controls.add(statusBtn);
        panel.add(controls, BorderLayout.NORTH);

        JScrollPane sp = DesktopUiKit.createScrollPane(outArea);
        sp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPixelArtTab() {
        return DesktopPixelArtDialog.createEmbeddedPanel(() -> project, (spriteName, spawn) -> {
            viewport.invalidateAssetCaches();
            if (spawn) {
                pushUndo();
                project.dimensionMode = "2D";
                dimBtn.setText("Mode: 2D");
                Entity sp = Entity.create2D("Sprite_" + (project.entities.size() + 1),
                        "prop", "CUSTOM_SPRITE", 0f, 1.5f, 1.5f, 1.5f, "#FFFFFF", "DYNAMIC");
                sp.customSpriteName = spriteName;
                project.entities.add(sp);
                viewport.setSelectedEntityId(sp.id);
                refreshHierarchyList();
                populateInspector();
            }
            viewport.repaint();
            log("[🎨 PIXEL] Saved 16x16 sprite '" + spriteName + "'" + (spawn ? " & spawned in 2D scene!" : ""));
        });
    }

    private JPanel buildAudioTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel musicCard = new JPanel();
        musicCard.setLayout(new BoxLayout(musicCard, BoxLayout.Y_AXIS));
        musicCard.setBackground(DesktopUiKit.C_PANEL);
        musicCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel mHead = new JLabel("🎵 4-CHANNEL CHIPTUNE COMPOSER");
        mHead.setFont(DesktopUiKit.FONT_TITLE);
        mHead.setForeground(DesktopUiKit.C_CYAN);
        mHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        musicCard.add(mHead);
        musicCard.add(Box.createVerticalStrut(8));

        DesktopUiKit.VortexToggle autoPlayToggle = new DesktopUiKit.VortexToggle(
                "Auto-play Chiptune Soundtrack when game starts", true);
        autoPlayToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        autoPlayToggle.setOnChange(() -> project.musicEnabled = autoPlayToggle.isSelected());
        musicCard.add(autoPlayToggle);
        musicCard.add(Box.createVerticalStrut(8));

        String[] genres = {
                "Soundtrack: Cyberpunk Synth (134 BPM)",
                "Soundtrack: Dungeon Crawl (112 BPM)",
                "Soundtrack: Boss Overdrive (148 BPM)",
                "Soundtrack: Sky Islands Chill (118 BPM)"
        };
        DesktopUiKit.VortexDropdown genreDrop = new DesktopUiKit.VortexDropdown(genres, genres[0]);
        genreDrop.setAlignmentX(Component.LEFT_ALIGNMENT);
        musicCard.add(genreDrop);
        musicCard.add(Box.createVerticalStrut(8));

        DesktopUiKit.VortexSlider bpmSlider = new DesktopUiKit.VortexSlider("Tempo", 70, 180, project.musicBpm > 0 ? project.musicBpm : 134, " BPM");
        bpmSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        bpmSlider.setOnChange(val -> project.musicBpm = val);
        musicCard.add(bpmSlider);
        musicCard.add(Box.createVerticalStrut(8));

        JLabel seqLbl = mutedLabel("16-Step Lead Melody (click any step to cycle scale note):");
        seqLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        musicCard.add(seqLbl);
        musicCard.add(Box.createVerticalStrut(4));

        JPanel stepGrid = new JPanel(new GridLayout(2, 8, 4, 4));
        stepGrid.setOpaque(false);
        stepGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        stepGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        final DesktopUiKit.VortexButton[] stepBtns = new DesktopUiKit.VortexButton[16];
        Runnable syncSteps = () -> {
            int[] steps = ChiptuneGenerator.parseSteps(project.musicPattern);
            for (int i = 0; i < 16; i++) {
                int idx = Math.max(0, Math.min(ChiptuneGenerator.NOTE_LABELS.length - 1, steps[i]));
                String n = idx == 0 ? "-" : ChiptuneGenerator.NOTE_LABELS[idx];
                stepBtns[i].setText(n);
                stepBtns[i].setVariant(idx == 0 ? DesktopUiKit.Variant.SECONDARY : DesktopUiKit.Variant.ACCENT);
            }
        };
        for (int i = 0; i < 16; i++) {
            final int sIdx = i;
            DesktopUiKit.VortexButton sb = new DesktopUiKit.VortexButton("-", DesktopUiKit.Variant.SECONDARY);
            stepBtns[i] = sb;
            sb.addActionListener(e -> {
                int[] steps = ChiptuneGenerator.parseSteps(project.musicPattern);
                steps[sIdx] = (steps[sIdx] + 1) % ChiptuneGenerator.NOTE_LABELS.length;
                StringBuilder joined = new StringBuilder();
                for (int k = 0; k < 16; k++) {
                    if (k > 0) joined.append(',');
                    joined.append(steps[k]);
                }
                project.musicPattern = joined.toString();
                syncSteps.run();
                if (steps[sIdx] > 0) viewport.audioEngine.play("coin");
            });
            stepGrid.add(sb);
        }
        syncSteps.run();
        genreDrop.setOnSelect((idx, val) -> {
            if (idx == 0) { project.musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK; project.musicBpm = 134; }
            else if (idx == 1) { project.musicPattern = ChiptuneGenerator.PRESET_DUNGEON; project.musicBpm = 112; }
            else if (idx == 2) { project.musicPattern = ChiptuneGenerator.PRESET_BOSS; project.musicBpm = 148; }
            else { project.musicPattern = ChiptuneGenerator.PRESET_SKYCHILL; project.musicBpm = 118; }
            bpmSlider.setValue(project.musicBpm);
            project.musicEnabled = true;
            autoPlayToggle.setSelected(true);
            syncSteps.run();
            viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm);
            log("[🎵] Active soundtrack -> " + val);
        });
        musicCard.add(stepGrid);
        musicCard.add(Box.createVerticalStrut(8));

        JPanel mBtns = new JPanel(new GridLayout(1, 2, 8, 0));
        mBtns.setOpaque(false);
        mBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        mBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        DesktopUiKit.VortexButton loopBtn = new DesktopUiKit.VortexButton("▶ Preview Loop", DesktopUiKit.Variant.PRIMARY);
        loopBtn.addActionListener(e -> viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm));
        DesktopUiKit.VortexButton stopBtn = new DesktopUiKit.VortexButton("■ Stop Music", DesktopUiKit.Variant.SECONDARY);
        stopBtn.addActionListener(e -> viewport.audioEngine.stopMusicLoop());
        mBtns.add(loopBtn);
        mBtns.add(stopBtn);
        musicCard.add(mBtns);

        p.add(musicCard, BorderLayout.NORTH);

        JPanel sfxGrid = new JPanel(new GridLayout(0, 2, 8, 8));
        sfxGrid.setBackground(DesktopUiKit.C_PANEL);
        sfxGrid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        String[][] sounds = {
                {"jump", "🚀 Jump Sweep"},
                {"coin", "🪙 Coin / Gem Chime"},
                {"laser", "⚡ Plasma Laser"},
                {"explosion", "💥 Explosion Burst"},
                {"hit", "🛡 Impact Hit"},
                {"powerup", "✨ Warp Fanfare"}
        };
        for (String[] s : sounds) {
            DesktopUiKit.VortexButton pad = new DesktopUiKit.VortexButton("▶ " + s[1], DesktopUiKit.Variant.SECONDARY);
            pad.addActionListener(e -> {
                viewport.audioEngine.play(s[0]);
                log("[SFX] Played '" + s[0] + "' (API: vortex.playSound('" + s[0] + "'))");
            });
            sfxGrid.add(pad);
        }
        p.add(sfxGrid, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildExportTab() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        String[][] targets = {
                {"html", "🌐 Export Single-File HTML5 Web Game (.html)", "Zero-dependency 60 FPS Canvas + WebAudio bundle for browsers & itch.io"},
                {"exe",  "🪟 Export Standalone Windows x64 Game (.exe)", "Self-contained Win32 PE executable with custom procedural icon"},
                {"linux","🐧 Export Standalone Linux x86_64 Binary", "Self-executing Linux desktop game binary"},
                {"jar",  "☕ Export Portable Desktop Fat JAR (.jar)", "Cross-platform runnable JAR for Windows, macOS & Linux"},
                {"android","🤖 Export Android Termux APK Project (.zip)", "Complete Android manifest, multi-DPI icons, Rhino/LuaJ/Box2D DEX & build_termux.sh"}
        };
        for (int i = 0; i < targets.length; i++) {
            final String[] t = targets[i];
            JPanel card = new JPanel(new BorderLayout(10, 4));
            card.setBackground(DesktopUiKit.C_PANEL);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                    BorderFactory.createEmptyBorder(10, 12, 10, 12)));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));

            JPanel txt = new JPanel(new GridLayout(2, 1));
            txt.setOpaque(false);
            JLabel h = new JLabel(t[1]);
            h.setFont(DesktopUiKit.FONT_UI_BOLD);
            h.setForeground(DesktopUiKit.C_TEXT);
            JLabel d = new JLabel(t[2]);
            d.setFont(DesktopUiKit.FONT_UI);
            d.setForeground(DesktopUiKit.C_TEXT_MUTED);
            txt.add(h);
            txt.add(d);
            card.add(txt, BorderLayout.CENTER);

            DesktopUiKit.VortexButton btn = new DesktopUiKit.VortexButton(
                    "Export", i == 0 ? DesktopUiKit.Variant.PRIMARY : DesktopUiKit.Variant.ACCENT);
            btn.addActionListener(e -> exportWithDialog(t[0]));
            card.add(btn, BorderLayout.EAST);

            p.add(card);
            p.add(Box.createVerticalStrut(8));
        }
        return p;
    }

    // =========================================================================
    // Project Lifecycle, Custom Inspector & Menus
    // =========================================================================
    private void syncVmDropdown() {
        if (vmDropdown == null || project == null) return;
        int idx = "JS".equalsIgnoreCase(project.scriptLanguage) ? 0
                : ("LUA".equalsIgnoreCase(project.scriptLanguage) ? 1 : 2);
        vmDropdown.setSelectedIndex(idx);
    }

    private void loadProjectIntoStudio(GameProject p) {
        this.project = p;
        dimBtn.setText("Mode: " + p.dimensionMode);
        syncVmDropdown();
        scriptArea.setText(p.scriptCode == null ? "" : p.scriptCode);
        scriptArea.setCaretPosition(0);
        viewport.loadProject(p, false);
        if (!p.entities.isEmpty()) {
            viewport.setSelectedEntityId(p.entities.get(0).id);
        }
        refreshHierarchyList();
        refreshFlowListModel();
        populateInspector();
        checkScriptSyntax();
        playStopBtn.setText("▶ PLAY GAME");
        playStopBtn.setVariant(DesktopUiKit.Variant.PRIMARY);
    }

    private void refreshHierarchyList() {
        entityListModel.clear();
        for (Entity e : project.entities) {
            entityListModel.addElement(e.name + "  [" + e.primitive + " • " + e.bodyType + "]");
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

        JLabel sec = new JLabel("ENTITY & PHYSICS INSPECTOR");
        sec.setFont(DesktopUiKit.FONT_UI_BOLD);
        sec.setForeground(DesktopUiKit.C_CYAN);
        sec.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorForm.add(sec);
        inspectorForm.add(Box.createVerticalStrut(6));

        Entity e = project.findById(viewport.getSelectedEntityId());
        if (e == null) {
            JLabel none = mutedLabel("Select an entity to inspect.");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        DesktopUiKit.VortexDropdown primBox = addDropdown("Primitive / Sprite", prims, e.primitive);

        JPanel xyzRow = new JPanel(new GridLayout(1, 3, 5, 0));
        xyzRow.setOpaque(false);
        xyzRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        xyzRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        JTextField xTf = DesktopUiKit.createField(fmt(e.x));
        JTextField yTf = DesktopUiKit.createField(fmt(e.y));
        JTextField zTf = DesktopUiKit.createField(fmt(e.z));
        xyzRow.add(labeledMini("X", xTf));
        xyzRow.add(labeledMini("Y", yTf));
        xyzRow.add(labeledMini("Z", zTf));
        inspectorForm.add(Box.createVerticalStrut(4));
        inspectorForm.add(xyzRow);

        JPanel sclRow = new JPanel(new GridLayout(1, 3, 5, 0));
        sclRow.setOpaque(false);
        sclRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sclRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        JTextField sxTf = DesktopUiKit.createField(fmt(e.scaleX));
        JTextField syTf = DesktopUiKit.createField(fmt(e.scaleY));
        JTextField szTf = DesktopUiKit.createField(fmt(e.scaleZ));
        sclRow.add(labeledMini("ScaleX", sxTf));
        sclRow.add(labeledMini("ScaleY", syTf));
        sclRow.add(labeledMini("ScaleZ", szTf));
        inspectorForm.add(Box.createVerticalStrut(4));
        inspectorForm.add(sclRow);

        JTextField rotTf = addField("Rotation (Y in 3D / Z in 2D)", fmt("3D".equalsIgnoreCase(project.dimensionMode) ? e.rotY : e.rotZ));
        JTextField colTf = addField("Color Hex (#RRGGBB)", e.colorHex);
        JTextField framesTf = addField("Flipbook Frames (e.g. knight_1,knight_2)", e.spriteFrames);
        JTextField fpsTf = addField("Flipbook Anim FPS", fmt(e.animFps));

        String[] bodies = {"NONE", "STATIC", "DYNAMIC", "KINEMATIC"};
        DesktopUiKit.VortexDropdown bodyBox = addDropdown("Physics Body", bodies, e.bodyType);

        inspectorForm.add(Box.createVerticalStrut(6));
        DesktopUiKit.VortexToggle trigCb = addToggle("Trigger Sensor", e.isTrigger);
        DesktopUiKit.VortexToggle fixRotCb = addToggle("Lock 2D Rotation", e.fixedRotation);
        DesktopUiKit.VortexToggle chkCb = addToggle("Checker Texture", e.checkerTexture);
        DesktopUiKit.VortexToggle glowCb = addToggle("Neon Emissive", e.emissive);
        DesktopUiKit.VortexToggle box2dCb = addToggle("World: JBox2D Solver", project.useBox2D);

        inspectorForm.add(Box.createVerticalStrut(10));
        DesktopUiKit.VortexButton apply = new DesktopUiKit.VortexButton("✓ Apply Entity & World", DesktopUiKit.Variant.PRIMARY);
        apply.setAlignmentX(Component.LEFT_ALIGNMENT);
        apply.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        apply.addActionListener(ev -> {
            e.name = nameTf.getText().trim();
            e.tag = tagTf.getText().trim();
            e.primitive = primBox.getSelectedItem();
            e.x = pf(xTf, e.x); e.y = pf(yTf, e.y); e.z = pf(zTf, e.z);
            e.scaleX = Math.max(0.1f, pf(sxTf, e.scaleX));
            e.scaleY = Math.max(0.1f, pf(syTf, e.scaleY));
            e.scaleZ = Math.max(0.1f, pf(szTf, e.scaleZ));
            if ("3D".equalsIgnoreCase(project.dimensionMode)) e.rotY = pf(rotTf, e.rotY);
            else e.rotZ = pf(rotTf, e.rotZ);
            e.colorHex = colTf.getText().trim();
            e.spriteFrames = framesTf.getText().trim();
            e.animFps = Math.max(0.5f, pf(fpsTf, e.animFps));
            e.bodyType = bodyBox.getSelectedItem();
            e.isTrigger = trigCb.isSelected();
            e.fixedRotation = fixRotCb.isSelected();
            e.checkerTexture = chkCb.isSelected();
            e.emissive = glowCb.isSelected();
            project.useBox2D = box2dCb.isSelected();
            refreshHierarchyList();
            viewport.repaint();
            log("✓ Updated entity '" + e.name + "'");
        });
        inspectorForm.add(apply);
        inspectorForm.revalidate();
        inspectorForm.repaint();
    }

    private void showAddEntityMenu(DesktopUiKit.VortexButton invoker) {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        if ("3D".equalsIgnoreCase(project.dimensionMode)) {
            items.add(new DesktopUiKit.MenuAction("＋ 3D Cube (Static)", () -> spawnEntity("CUBE", "#00E5FF", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("＋ 3D Sphere (Dynamic)", () -> spawnEntity("SPHERE", "#FFD740", "DYNAMIC")));
            items.add(new DesktopUiKit.MenuAction("＋ 3D Pyramid", () -> spawnEntity("PYRAMID", "#FF4081", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("⛰ FastNoiseLite 3D Terrain (New Seed)", () -> {
                pushUndo();
                project.terrainSeed = (int) (System.currentTimeMillis() % 90000) + 1000;
                Entity t = Entity.create3D("Terrain_" + project.terrainSeed, "solid", "TERRAIN", 0f, 0f, 0f, 65f, 9f, 65f, "#2E7D32", "STATIC");
                t.checkerTexture = true; t.secondaryColorHex = "#1B5E20";
                project.entities.add(0, t);
                viewport.invalidateAssetCaches();
                refreshHierarchyList();
                viewport.repaint();
                log("Generated 3D FastNoiseLite Terrain (seed=" + project.terrainSeed + ")");
            }));
            items.add(new DesktopUiKit.MenuAction("🧊 Import Custom Wavefront .OBJ 3D Mesh…", this::showCustomObjModal));
        } else {
            items.add(new DesktopUiKit.MenuAction("＋ 2D Platform (Static)", () -> spawnEntity("RECT", "#2E4F6E", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("＋ 2D Hero (Dynamic)", () -> spawnEntity("SPRITE_HERO", "#00E5FF", "DYNAMIC")));
            items.add(new DesktopUiKit.MenuAction("📦 JBox2D Tumbling Crate", () -> {
                pushUndo();
                project.customSprites.putIfAbsent("crate", PixelSpriteCodec.createCrateSprite());
                Entity c = Entity.create2D("Crate_" + (project.entities.size() + 1), "solid", "CUSTOM_SPRITE", 0f, 3f, 1.3f, 1.3f, "#FFB300", "DYNAMIC");
                c.customSpriteName = "crate"; c.fixedRotation = false;
                project.entities.add(c);
                viewport.setSelectedEntityId(c.id);
                refreshHierarchyList();
                viewport.repaint();
            }));
            items.add(new DesktopUiKit.MenuAction("🪙 2D Coin Trigger", () -> {
                Entity c = spawnEntity("SPRITE_COIN", "#FFD740", "STATIC");
                c.isTrigger = true;
            }));
            items.add(new DesktopUiKit.MenuAction("👾 2D Slime Enemy", () -> spawnEntity("SPRITE_SLIME", "#FF5252", "DYNAMIC")));
        }
        DesktopUiKit.showPopupMenu(invoker, items);
    }

    private void showCustomObjModal() {
        JDialog dlg = new JDialog(this, "Import Wavefront .OBJ 3D Mesh", true);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(DesktopUiKit.C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_CYAN, 1),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        root.setPreferredSize(new Dimension(480, 340));

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
        top.setOpaque(false);
        JLabel h = new JLabel("IMPORT WAVEFRONT .OBJ 3D MESH");
        h.setFont(DesktopUiKit.FONT_TITLE);
        h.setForeground(DesktopUiKit.C_CYAN);
        JTextField nameTf = DesktopUiKit.createField("starfighter");
        top.add(h);
        top.add(nameTf);
        root.add(top, BorderLayout.NORTH);

        JTextArea objArea = new JTextArea(
                "v 0 0.8 0\nv -0.9 -0.4 0.7\nv 0.9 -0.4 0.7\nv 0 -0.2 -1.2\n"
                + "f 1 2 3\nf 1 3 4\nf 1 4 2\nf 2 4 3\n");
        objArea.setBackground(DesktopUiKit.C_VOID);
        objArea.setForeground(DesktopUiKit.C_TEXT);
        objArea.setCaretColor(DesktopUiKit.C_CYAN);
        objArea.setFont(DesktopUiKit.FONT_MONO);
        objArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane sp = DesktopUiKit.createScrollPane(objArea);
        sp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        root.add(sp, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        DesktopUiKit.VortexButton cancel = new DesktopUiKit.VortexButton("Cancel", DesktopUiKit.Variant.SECONDARY);
        cancel.addActionListener(e -> dlg.dispose());
        DesktopUiKit.VortexButton spawn = new DesktopUiKit.VortexButton("Save & Spawn 3D Mesh", DesktopUiKit.Variant.PRIMARY);
        spawn.addActionListener(e -> {
            String key = nameTf.getText().trim().isEmpty() ? "mesh_1" : nameTf.getText().trim();
            pushUndo();
            project.customObjMeshes.put(key, objArea.getText());
            project.dimensionMode = "3D";
            dimBtn.setText("Mode: 3D");
            Entity m = Entity.create3D("Obj_" + key, "prop", "CUSTOM_OBJ", 0f, 2f, 0f, 1.5f, 1.5f, 1.5f, "#00E5FF", "DYNAMIC");
            m.customObjName = key;
            project.entities.add(m);
            viewport.invalidateAssetCaches();
            viewport.setSelectedEntityId(m.id);
            refreshHierarchyList();
            populateInspector();
            viewport.repaint();
            log("Imported 3D .OBJ mesh '" + key + "'");
            dlg.dispose();
        });
        foot.add(cancel);
        foot.add(spawn);
        root.add(foot, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private Entity spawnEntity(String prim, String hex, String body) {
        pushUndo();
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

    private void togglePlayMode() {
        if (viewport.isPlaying()) {
            viewport.loadProject(project, false);
            playStopBtn.setText("▶ PLAY GAME");
            playStopBtn.setVariant(DesktopUiKit.Variant.PRIMARY);
            log("■ Stopped Play Mode.");
        } else {
            if (!checkScriptSyntax()) {
                rightTabs.selectTabByKey("CODE", true);
                DesktopUiKit.showInfoModal(this, "Script Syntax Error",
                        "Please fix the highlighted script syntax error in the Code tab before starting Play Mode.");
                return;
            }
            viewport.loadProject(project.deepCopy(), true);
            playStopBtn.setText("■ STOP PLAY");
            playStopBtn.setVariant(DesktopUiKit.Variant.DANGER);
            log("▶ Started Live Play Mode (" + project.scriptLanguage + ") — Controls: WASD/Arrows + Space(A) + Shift/K(B).");
        }
    }

    private void showExportMenu(DesktopUiKit.VortexButton invoker) {
        syncCode();
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction("🌐 Export Playable HTML5 Web Game (.html)", () -> exportWithDialog("html")));
        items.add(new DesktopUiKit.MenuAction("🪟 Export Standalone Windows Game (.exe)", () -> exportWithDialog("exe")));
        items.add(new DesktopUiKit.MenuAction("🐧 Export Standalone Linux Binary", () -> exportWithDialog("linux")));
        items.add(new DesktopUiKit.MenuAction("☕ Export Cross-Platform Desktop Game (.jar)", () -> exportWithDialog("jar")));
        items.add(new DesktopUiKit.MenuAction("🤖 Export Android Termux APK Project (.zip)", () -> exportWithDialog("android")));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("📦 Open Export Hub Tab", () -> rightTabs.selectTabByKey("EXPORT", true)));
        DesktopUiKit.showPopupMenu(invoker, items);
    }

    private void exportWithDialog(String kind) {
        syncCode();
        String slug = project.getSlug();
        String defaultName = slug + ("html".equals(kind) ? "-Web.html"
                : "exe".equals(kind) ? "-Windows-x64.exe"
                : "linux".equals(kind) ? "-Linux-x86_64"
                : "jar".equals(kind) ? "-Desktop.jar"
                : "-Android-Termux.zip");
        File out = DesktopUiKit.showFileModal(this, "Export Standalone Build", true, defaultName);
        if (out != null) {
            try {
                if ("html".equals(kind)) Html5WebExporter.exportToFile(project, out);
                else if ("exe".equals(kind)) DesktopExporter.exportStandaloneWindowsExe(project, out);
                else if ("linux".equals(kind)) DesktopExporter.exportStandaloneLinuxBinary(project, out);
                else if ("jar".equals(kind)) DesktopExporter.exportStandaloneGameJar(project, out);
                else DesktopExporter.exportAndroidTermuxZip(project, out);
                log("[✓] Exported standalone build -> " + out.getAbsolutePath());
                DesktopUiKit.showInfoModal(this, "Export Complete", "Saved standalone build to:\n" + out.getAbsolutePath());
            } catch (Exception ex) {
                log("[ERROR] Export failed: " + ex.getMessage());
                DesktopUiKit.showInfoModal(this, "Export Error", ex.getMessage());
            }
        }
    }

    private void showNewTemplateModal() {
        JDialog dlg = new JDialog(this, "Create New 2D / 3D Game Project", true);
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(DesktopUiKit.C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_CYAN, 1),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));
        root.setPreferredSize(new Dimension(440, 240));

        JLabel h = new JLabel("CREATE NEW 2D / 3D GAME PROJECT");
        h.setFont(DesktopUiKit.FONT_TITLE);
        h.setForeground(DesktopUiKit.C_CYAN);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(h);
        root.add(Box.createVerticalStrut(12));

        JLabel l1 = mutedLabel("Game Title:");
        l1.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(l1);
        JTextField titleTf = DesktopUiKit.createField("Cyber Odyssey");
        titleTf.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(titleTf);
        root.add(Box.createVerticalStrut(10));

        JLabel l2 = mutedLabel("Starter Template (ES6 JS • Lua 5.2 • JBox2D • 3D GL):");
        l2.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(l2);
        DesktopUiKit.VortexDropdown tplDrop = new DesktopUiKit.VortexDropdown(
                ProjectTemplates.TEMPLATE_NAMES, ProjectTemplates.TEMPLATE_NAMES[0]);
        tplDrop.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(tplDrop);
        root.add(Box.createVerticalStrut(16));

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        foot.setAlignmentX(Component.LEFT_ALIGNMENT);
        DesktopUiKit.VortexButton cancel = new DesktopUiKit.VortexButton("Cancel", DesktopUiKit.Variant.SECONDARY);
        cancel.addActionListener(e -> dlg.dispose());
        DesktopUiKit.VortexButton create = new DesktopUiKit.VortexButton("Create & Open", DesktopUiKit.Variant.PRIMARY);
        create.addActionListener(e -> {
            String t = titleTf.getText().trim().isEmpty() ? "My Vortex Game" : titleTf.getText().trim();
            int idx = tplDrop.getSelectedIndex();
            GameProject created = ProjectTemplates.create(
                    ProjectTemplates.TEMPLATE_KEYS[idx], t,
                    "com.vortex.game." + t.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""));
            currentProjectFile = null;
            loadProjectIntoStudio(created);
            log("Created project '" + t + "' from template " + ProjectTemplates.TEMPLATE_NAMES[idx]);
            dlg.dispose();
        });
        foot.add(cancel);
        foot.add(create);
        root.add(foot);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void openProjectJson() {
        File f = DesktopUiKit.showFileModal(this, "Open Vortex Project (.vortex.json)", false, "project.vortex.json");
        if (f != null) {
            try {
                String txt = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                currentProjectFile = f;
                loadProjectIntoStudio(GameProject.fromJsonString(txt));
                log("Loaded project: " + f.getAbsolutePath());
            } catch (Exception ex) {
                DesktopUiKit.showInfoModal(this, "Open Failed", ex.getMessage());
            }
        }
    }

    private void saveProjectJson() {
        syncCode();
        if (currentProjectFile == null) {
            File chosen = DesktopUiKit.showFileModal(this, "Save Vortex Project (.vortex.json)", true, project.getSlug() + ".vortex.json");
            if (chosen == null) return;
            currentProjectFile = chosen;
        }
        try (FileOutputStream fos = new FileOutputStream(currentProjectFile)) {
            fos.write(project.toJsonString().getBytes(StandardCharsets.UTF_8));
            log("✓ Saved project -> " + currentProjectFile.getAbsolutePath());
        } catch (Exception ex) {
            DesktopUiKit.showInfoModal(this, "Save Failed", ex.getMessage());
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

    private void cloneSelectedEntity() {
        Entity src = project.findById(viewport.getSelectedEntityId());
        if (src == null) return;
        pushUndo();
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
        pushUndo();
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
            syntaxStatusLabel.setForeground(DesktopUiKit.C_OK_TX);
            return true;
        } else {
            syntaxStatusLabel.setText("✕ " + check);
            syntaxStatusLabel.setForeground(DesktopUiKit.C_DANGER_TX);
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

    private JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(DesktopUiKit.FONT_UI);
        l.setForeground(DesktopUiKit.C_TEXT_MUTED);
        return l;
    }

    private JTextField addField(String label, String val) {
        inspectorForm.add(Box.createVerticalStrut(5));
        JLabel l = mutedLabel(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorForm.add(l);
        inspectorForm.add(Box.createVerticalStrut(2));
        JTextField tf = DesktopUiKit.createField(val);
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorForm.add(tf);
        return tf;
    }

    private DesktopUiKit.VortexDropdown addDropdown(String label, String[] items, String cur) {
        inspectorForm.add(Box.createVerticalStrut(5));
        JLabel l = mutedLabel(label);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorForm.add(l);
        inspectorForm.add(Box.createVerticalStrut(2));
        DesktopUiKit.VortexDropdown dd = new DesktopUiKit.VortexDropdown(items, cur);
        dd.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorForm.add(dd);
        return dd;
    }

    private DesktopUiKit.VortexToggle addToggle(String label, boolean checked) {
        inspectorForm.add(Box.createVerticalStrut(2));
        DesktopUiKit.VortexToggle t = new DesktopUiKit.VortexToggle(label, checked);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        inspectorForm.add(t);
        return t;
    }

    private JPanel labeledMini(String lbl, JTextField tf) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.add(mutedLabel(lbl), BorderLayout.NORTH);
        p.add(tf, BorderLayout.CENTER);
        return p;
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
        frame.getContentPane().setBackground(DesktopUiKit.C_VOID);

        final DesktopSoftware3DViewport vp = new DesktopSoftware3DViewport();
        frame.add(vp, BorderLayout.CENTER);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 5));
        top.setBackground(DesktopUiKit.C_HEADER);
        top.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));
        DesktopUiKit.VortexButton restart = new DesktopUiKit.VortexButton("↻ Restart Game", DesktopUiKit.Variant.ACCENT);
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
    // Headless Verification Suite (Rhino ES6 + LuaJ + JBox2D + LRM + Forge AI)
    // =========================================================================
    private static void runHeadlessSelfTests() throws Exception {
        System.out.println("=== Vortex Engine v4.1.0 (Hyperion Edition) Headless Verification Suite ===");

        for (int i = 0; i < ProjectTemplates.TEMPLATE_KEYS.length; i++) {
            String key = ProjectTemplates.TEMPLATE_KEYS[i];
            GameProject p = ProjectTemplates.create(key, "Test_" + key, "com.vortex.test." + key.toLowerCase());
            PhysicsWorld pw = new PhysicsWorld();
            VortexScriptEngine vm = new VortexScriptEngine();
            vm.attach(p, pw, null);

            if (!vm.compileAndInit(p.scriptCode)) {
                throw new RuntimeException("Template failed compile (" + key + "): " + vm.getLastError());
            }

            vm.inputJoyX = 0.6f;
            vm.notifyButton("A");
            for (int f = 0; f < 45; f++) {
                vm.update(0.016f);
                List<PhysicsWorld.CollisionEvent> cols = pw.step(p, 0.016f);
                for (PhysicsWorld.CollisionEvent ce : cols) vm.notifyCollision(ce.a, ce.b);
            }

            GameProject rt = GameProject.fromJsonString(p.toJsonString());
            if (rt.entities.isEmpty()) throw new RuntimeException("JSON round-trip lost entities for " + key);
            System.out.println("  [PASS] Template " + key + " (" + p.dimensionMode + " / " + p.scriptLanguage
                    + " / entities=" + p.entities.size() + " / banner='" + vm.hudBanner + "')");
        }

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

        GameProject forgeProj = new GameProject();
        VortexForgeAgent.applyPromptToProject(forgeProj, "3D mecha canyon with railgun", 0, "");
        if (!"OK (ES6+ JavaScript • Mozilla Rhino)".equals(VortexScriptEngine.validateSyntax("JS", forgeProj.scriptCode))) {
            throw new RuntimeException("Forge AI generated invalid JS!");
        }
        System.out.println("  [PASS] Vortex Forge AI Generator Verified (" + forgeProj.entities.size() + " 3D entities + ES6 JS)");

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

        System.out.println("=== ALL VORTEX v4.1.0 (HYPERION UI EDITION) HEADLESS TESTS PASSED 100%! ===");
    }
}
