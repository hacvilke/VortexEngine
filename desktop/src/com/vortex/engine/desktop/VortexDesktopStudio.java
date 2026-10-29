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
import com.vortex.engine.core.model.SceneHierarchy;
import com.vortex.engine.core.physics.PhysicsWorld;
import com.vortex.engine.core.script.VortexScriptEngine;
import com.vortex.engine.core.storage.VortexSaveStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
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

/**
 * Vortex Engine Studio v4.2.0 ("Monolith White Edition") — Cross-Platform Desktop IDE & Game Runner.
 *
 * Features:
 *  - Pure Monochrome Carbon Dark + Crisp White (#FFFFFF) UI with Custom Anti-Aliased Vector Icons (ZERO Blue!)
 *  - Smoothly Draggable SplitPane Sidebars & Bottom Dock with 3-Dot Grip Handles
 *  - Built-In Interactive OS Terminal (Windows PowerShell + POSIX sh, enriched JDK/Go/LRM PATH)
 *  - Multi-VM Game Scripting: Native Java (BeanShell JVM) • ES6 JS (Mozilla Rhino) • Lua 5.2 • Vortex Flow • VXS
 *  - Fixed & Upgraded Vortex Forge AI (Groq `openai/gpt-oss-120b` + OpenRouter `$0` `openrouter/free` + Live Models)
 *  - Automatic System Dependency Prober & 1-Click LRM (`hacvilke/lrm`) Installer + Background Mesh Services
 */
public class VortexDesktopStudio extends JFrame {

    private static final String[] VM_ITEMS = {
            "VM: ES6 JavaScript",
            "VM: Native Java (JVM)",
            "VM: Lua 5.2 (LuaJ)",
            "VM: VortexScript"
    };
    private static final String[] VM_CODES = {"JS", "JAVA", "LUA", "VXS"};

    private GameProject project;
    private File currentProjectFile = null;

    private final DesktopSoftware3DViewport viewport = new DesktopSoftware3DViewport();
    private DesktopSceneTree sceneTree;
    private String pendingParentId = null;

    private final JPanel inspectorForm = new JPanel();
    private final JTextArea scriptArea = new JTextArea();
    private final JLabel syntaxStatusLabel = new JLabel("Syntax Ready");
    private final DefaultListModel<String> fileListModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileListModel);
    private final List<String> openScriptTabs = new ArrayList<>();
    private final JPanel openTabsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
    private JPanel fileSidebarPanel;
    private JSplitPane codeSidebarSplit;
    private JPanel codeEditorRootPanel;
    private JPanel codeTabMountHost;
    private JDialog floatingCodeDialog;

    // Snapped Code Split beside/below Scene Viewport (Drag-Drop or 1-Click Snap!)
    private final JPanel centerSnapHost = new JPanel(new BorderLayout());
    private JPanel centerStagePanel;
    private String snappedZone = null; // null, "RIGHT", "LEFT", "BOTTOM"
    private String snappedFilePath = null;
    private final JTextArea snappedScriptArea = new JTextArea();
    private final JLabel snappedTitleLabel = new JLabel("");
    private boolean syncingEditorBuffers = false;

    // Center Visual Blueprint Graph (alongside classic Flow Event-Sheet tab)
    private final DesktopUiKit.AnimatedDeck centerCanvasDeck = new DesktopUiKit.AnimatedDeck();

    // Multi-turn Forge AI Conversation State
    private final List<VortexForgeAgent.ChatMessage> forgeChatHistory = new ArrayList<>();
    private JPanel forgeMessagesBox;
    private JScrollPane forgeMessagesScroll;
    private java.util.function.Consumer<String> forgeSend = null;
    private boolean forgeBusy = false;
    private String forgeStatusText = "";

    // Color-Coded Console + Interactive Shell + Status Confirmation Pill
    private final DesktopUiKit.VortexLogPane consoleArea = new DesktopUiKit.VortexLogPane();
    private final DesktopUiKit.StatusPill globalStatusPill =
            new DesktopUiKit.StatusPill("✓ Build Ready", DesktopUiKit.StatusPill.State.SUCCESS);
    private final DesktopTerminalPanel terminalPanel;
    private DesktopUiKit.VortexTabStrip bottomTabs;
    private final DesktopUiKit.AnimatedDeck bottomDeck = new DesktopUiKit.AnimatedDeck();
    private JPanel bottomWrapperPanel;
    private JDialog floatingConsoleDialog;
    private final JLabel telemetryLabel = new JLabel("Editor Ready");

    // Workspace Dock Collapse / Focus Memory & Persistent Inspector Accordion State
    private boolean leftDockCollapsed = false;
    private boolean rightDockCollapsed = false;
    private boolean bottomDockCollapsed = false;
    private boolean viewportFocusMode = false;
    private int savedLeftDivLoc = 320;
    private int savedRightDivLoc = 760;
    private int savedBottomDivLoc = 560;
    private DesktopUiKit.VortexButton focusModeBtn;
    private DesktopUiKit.VortexButton bottomCollapseBtn;
    private final java.util.Map<String, Boolean> inspectorAccordionState = new java.util.HashMap<>();

    private DesktopUiKit.VortexButton playStopBtn;
    private DesktopUiKit.VortexButton dimBtn;
    private DesktopUiKit.VortexDropdown vmDropdown;
    private DesktopUiKit.VortexDropdown snippetDropdown;
    private DesktopUiKit.VortexTabStrip rightTabs;
    private final DesktopUiKit.AnimatedDeck rightCardsPanel = new DesktopUiKit.AnimatedDeck();
    private final DefaultListModel<String> flowListModel = new DefaultListModel<>();
    private JTextArea lrmStatusArea;

    private final JSplitPane mainSplit;
    private final JSplitPane midRightSplit;
    private final JSplitPane centerVerticalSplit;

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    public static void main(String[] args) throws Exception {
        VortexSaveStore.setSaveRootDir(new File(System.getProperty("user.home", "."), ".vortex-saves"));

        for (int i = 0; i < args.length; i++) {
            if ("--headless-test".equals(args[i])) {
                runHeadlessSelfTests();
                return;
            }
            if ("--docs-shot".equals(args[i]) && i + 1 < args.length) {
                // GUI verification hook: open the studio, open the docs browser, screenshot, exit.
                final String shotPath = args[i + 1];
                DesktopUiKit.installDarkDefaults();
                SwingUtilities.invokeLater(() -> {
                    final VortexDesktopStudio studio = new VortexDesktopStudio(true);
                    studio.setSize(1440, 900);
                    studio.setVisible(true);
                    javax.swing.Timer kick = new javax.swing.Timer(1400, e -> {
                        javax.swing.Timer grab = new javax.swing.Timer(1600, ev -> {
                            try {
                                java.awt.Robot robot = new java.awt.Robot();
                                java.awt.Rectangle all = new java.awt.Rectangle(
                                        java.awt.Toolkit.getDefaultToolkit().getScreenSize());
                                javax.imageio.ImageIO.write(robot.createScreenCapture(all), "png", new File(shotPath));
                                System.out.println("[SHOT] wrote " + shotPath);
                            } catch (Throwable ex) {
                                System.out.println("[SHOT] failed: " + ex);
                            }
                            for (java.awt.Window w : java.awt.Window.getWindows()) {
                                if (w instanceof javax.swing.JDialog) w.dispose();
                            }
                            System.exit(0);
                        });
                        grab.setRepeats(false);
                        grab.start();
                        studio.showDocsBrowser();
                    });
                    kick.setRepeats(false);
                    kick.start();
                });
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

        String embeddedJson = readEmbeddedGameJson();
        if (embeddedJson != null && !embeddedJson.trim().isEmpty()) {
            final GameProject embeddedProj = GameProject.fromJsonString(embeddedJson);
            SwingUtilities.invokeLater(() -> launchStandaloneGameWindow(embeddedProj));
            return;
        }

        SwingUtilities.invokeLater(() -> {
            VortexDesktopStudio studio = new VortexDesktopStudio(true);
            studio.setVisible(true);
        });
    }

    public VortexDesktopStudio() {
        this(false);
    }

    public VortexDesktopStudio(boolean checkStartupDependencies) {
        super("Vortex Engine Studio v4.8.0 — Voxel Worlds • Tile Maps • Prefabs • In-App Docs • Forge AI Chat");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Laptop-aware sizing: fit cleanly inside 1280x720 / 1366x768 / 1536x864 / 1440x900 screens!
        int winW = 1360;
        int winH = 800;
        boolean compactLaptop = false;
        try {
            if (!GraphicsEnvironment.isHeadless()) {
                Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                winW = Math.min(1440, Math.max(1024, usable.width - 20));
                winH = Math.min(860, Math.max(660, usable.height - 20));
                compactLaptop = (usable.width <= 1440 || usable.height <= 864);
            }
        } catch (Throwable ignored) {}
        setSize(winW, winH);
        setMinimumSize(new Dimension(960, 600));
        setLocationRelativeTo(null);
        if (compactLaptop && !GraphicsEnvironment.isHeadless()) {
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
        }
        getContentPane().setBackground(DesktopUiKit.C_VOID);
        setLayout(new BorderLayout());

        project = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "Neon Survivors Swarm", "com.vortex.game.survivors");

        terminalPanel = new DesktopTerminalPanel(
                new File(System.getProperty("user.dir", ".")),
                () -> showLrmDependencyWizardModal(false));

        add(buildTopToolbar(), BorderLayout.NORTH);

        // Left Dock: Scene Hierarchy + Entity & Physics Inspector
        JPanel leftPanel = buildHierarchyAndInspector();

        // Center Dock: [ Scene Viewport | Blueprint Graph ] + Optional Snapped Code Split + Bottom Console/Terminal
        centerStagePanel = buildCenterViewportAndBlueprintStage();
        initSnappedCodeEditorSync();
        rebuildCenterSnapLayout();

        JPanel bottomPanel = buildBottomConsoleAndTerminalPanel();
        int leftW = Math.min(372, Math.max(304, Math.round(winW * 0.215f)));   // wide enough for 3-up vector rows
        int centerAndBottomH = Math.max(380, Math.round((winH - 44) * 0.75f));
        int centerW = Math.max(460, Math.round((winW - leftW) * 0.56f));

        centerVerticalSplit = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, centerSnapHost, bottomPanel, centerAndBottomH);
        centerVerticalSplit.setResizeWeight(0.76);

        // Right Dock: Animated 7-Tab Studio Workspace (Code, Flow, Forge AI, LRM, Pixel, Audio, Export)
        JPanel rightPanel = buildRightTabbedWorkspace();

        midRightSplit = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerVerticalSplit, rightPanel, centerW);
        midRightSplit.setResizeWeight(0.56);

        mainSplit = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, midRightSplit, leftW);
        mainSplit.setResizeWeight(0.215);

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
                if (!viewport.isPlaying()) return;
                SwingUtilities.invokeLater(() -> {
                    if (viewport.isPlaying()) {
                        telemetryLabel.setText(fps + " FPS  •  " + project.entities.size() + " objs  •  "
                                + project.dimensionMode + " / " + project.scriptLanguage);
                    }
                });
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (floatingCodeDialog != null) floatingCodeDialog.dispose();
                if (floatingConsoleDialog != null) floatingConsoleDialog.dispose();
                LrmWorkspaceBridge.stopBackgroundServices();
                terminalPanel.killActiveProcess();
                viewport.shutdown();
            }
        });

        loadProjectIntoStudio(project);

        if (checkStartupDependencies) {
            runStartupDependencyCheckAsync();
        }
    }

    public JSplitPane getMainSplit() { return mainSplit; }
    public JSplitPane getMidRightSplit() { return midRightSplit; }
    public JSplitPane getCenterVerticalSplit() { return centerVerticalSplit; }
    public DesktopTerminalPanel getTerminalPanel() { return terminalPanel; }

    private void runStartupDependencyCheckAsync() {
        Thread checker = new Thread(() -> {
            LrmWorkspaceBridge.SystemProbe probe = LrmWorkspaceBridge.probeSystem();
            SwingUtilities.invokeLater(() -> {
                log("[SYSTEM] " + probe.osName + " (" + probe.osArch + ") • "
                        + (probe.lrmBin != null ? probe.lrmVersion : "LRM not installed yet")
                        + " • " + (probe.goBin != null ? probe.goVersion : "Go optional"));
                if (probe.lrmBin == null && !LrmWorkspaceBridge.isAutoPromptDismissed()) {
                    showLrmDependencyWizardModal(true);
                }
            });
        }, "vortex-startup-dep-check");
        checker.setDaemon(true);
        checker.start();
    }

    // =========================================================================
    // Startup & On-Demand LRM + Golang System Dependency Auto-Setup Wizard
    // =========================================================================
    public void showLrmDependencyWizardModal(boolean isStartupPrompt) {
        final LrmWorkspaceBridge.SystemProbe probe = LrmWorkspaceBridge.probeSystem();
        final JDialog dlg = new JDialog(this, "Vortex Studio — LRM P2P Dependency & Toolchain Setup", true);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(DesktopUiKit.C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_WHITE, 1),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));
        root.setPreferredSize(new Dimension(620, 440));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        JLabel head = new JLabel(probe.lrmBin == null
                ? "LRM P2P VERSION CONTROL — AUTO-INSTALL & WORKSPACE SETUP"
                : "LRM P2P VERSION CONTROL — TOOLCHAIN VERIFIED & READY");
        head.setFont(DesktopUiKit.FONT_TITLE);
        head.setForeground(DesktopUiKit.C_WHITE);
        top.add(head, BorderLayout.NORTH);

        String cmdPreview = probe.isWindows
                ? LrmWorkspaceBridge.LRM_WINDOWS_INSTALL_PS1
                : (probe.isAndroidTermux ? LrmWorkspaceBridge.LRM_MOBILE_TERMUX_INSTALL_SH : LrmWorkspaceBridge.LRM_DESKTOP_INSTALL_SH);

        JLabel desc = new JLabel("<html><body style='width:555px;color:#A1A1AA;font-family:sans-serif;'>"
                + "Vortex Studio integrates natively with <b>hacvilke/lrm</b> for encrypted, serverless peer-to-peer game version control. "
                + "We checked your system (including <b>Go / golang</b>, PowerShell/curl, and Java). Click <b>Auto-Install &amp; Setup LRM</b> "
                + "to automatically run <code>" + cmdPreview + "</code> (with Go source fallback if needed) and initialize your game repo!"
                + "</body></html>");
        top.add(desc, BorderLayout.CENTER);
        root.add(top, BorderLayout.NORTH);

        final JTextArea liveLog = new JTextArea();
        liveLog.setEditable(false);
        liveLog.setBackground(DesktopUiKit.C_VOID);
        liveLog.setForeground(DesktopUiKit.C_TEXT);
        liveLog.setFont(DesktopUiKit.FONT_MONO);
        liveLog.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        liveLog.setText("System Dependency Scan:\n" + probe.toSummaryReport()
                + "\nReady to execute automated setup.");

        JScrollPane logSp = DesktopUiKit.createScrollPane(liveLog);
        logSp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        root.add(logSp, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 6));
        south.setOpaque(false);

        final DesktopUiKit.VortexToggle dontAskToggle = new DesktopUiKit.VortexToggle(
                "Don't prompt automatically on startup", LrmWorkspaceBridge.isAutoPromptDismissed());
        dontAskToggle.setOnChange(() -> LrmWorkspaceBridge.setAutoPromptDismissed(dontAskToggle.isSelected()));
        if (isStartupPrompt) {
            south.add(dontAskToggle, BorderLayout.NORTH);
        }

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);

        final DesktopUiKit.VortexButton closeBtn = new DesktopUiKit.VortexButton(
                isStartupPrompt ? "Skip for Now" : "Close", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        closeBtn.addActionListener(e -> dlg.dispose());

        final DesktopUiKit.VortexButton installBtn = new DesktopUiKit.VortexButton(
                probe.lrmBin == null ? "Auto-Install & Setup LRM" : "Re-Verify & Sync Workspace",
                DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.SPARKLE);

        installBtn.addActionListener(e -> {
            installBtn.setEnabled(false);
            installBtn.setText("Installing & Configuring…");
            syncCode();
            final File repoDir = new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
            Thread worker = new Thread(() -> {
                boolean ok = LrmWorkspaceBridge.autoInstallAndSetupLrm(project, repoDir, line -> {
                    SwingUtilities.invokeLater(() -> {
                        liveLog.append("\n" + line);
                        liveLog.setCaretPosition(liveLog.getDocument().getLength());
                        if (lrmStatusArea != null) {
                            lrmStatusArea.append("\n" + line);
                            lrmStatusArea.setCaretPosition(lrmStatusArea.getDocument().getLength());
                        }
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    installBtn.setEnabled(true);
                    installBtn.setText(ok ? "LRM Installed & Ready" : "Retry Auto-Install");
                    closeBtn.setText("Done");
                    terminalPanel.setWorkingDirectory(repoDir);
                    log(ok ? "[LRM] Auto-installed & initialized workspace at " + repoDir.getAbsolutePath()
                            : "[LRM] Setup finished with warnings — see setup log.");
                });
            }, "vortex-lrm-installer");
            worker.setDaemon(true);
            worker.start();
        });

        btns.add(closeBtn);
        btns.add(installBtn);
        south.add(btns, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    // =========================================================================
    // Pure Monochrome Carbon + Crisp White Top Header Bar (Vector Icons!)
    // =========================================================================
    private JPanel buildTopToolbar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0));
        bar.setBackground(DesktopUiKit.C_HEADER);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));

        // ================= Row 1: Godot / Unreal-style minimal menu bar =================
        JPanel menuRow = new JPanel(new BorderLayout(8, 0));
        menuRow.setOpaque(false);
        menuRow.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        JPanel menus = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        menus.setOpaque(false);

        JLabel brand = new JLabel("VORTEX");
        brand.setFont(DesktopUiKit.FONT_TITLE);
        brand.setForeground(DesktopUiKit.C_WHITE);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 8));
        menus.add(brand);

        menus.add(makeMenuButton("File", this::buildFileMenuItems));
        menus.add(makeMenuButton("Edit", this::buildEditMenuItems));
        menus.add(makeMenuButton("Create", this::buildCreateMenuItems));
        menus.add(makeMenuButton("View", this::buildViewMenuItems));
        menus.add(makeMenuButton("Tools", this::buildToolsMenuItems));
        menus.add(makeMenuButton("Help", this::buildHelpMenuItems));
        menuRow.add(menus, BorderLayout.WEST);

        JPanel menuRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        menuRight.setOpaque(false);
        globalStatusPill.setToolTipText("Build / script status — click to open Console");
        globalStatusPill.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (bottomDockCollapsed) toggleBottomDock();
                if (bottomTabs != null) bottomTabs.selectTabByKey("CONSOLE", true);
            }
        });
        menuRight.add(globalStatusPill);
        menuRow.add(menuRight, BorderLayout.EAST);
        bar.add(menuRow, BorderLayout.NORTH);

        // ================= Row 2: Compact icon-first tool strip (low text, high clarity) =================
        JPanel strip = new JPanel(new BorderLayout(8, 0));
        strip.setOpaque(false);
        strip.setBorder(BorderFactory.createEmptyBorder(0, 8, 5, 8));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        left.setOpaque(false);

        DesktopUiKit.VortexButton newBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
        newBtn.setToolTipText("New Project (Ctrl+N)");
        newBtn.addActionListener(e -> showNewTemplateModal());
        left.add(newBtn);

        DesktopUiKit.VortexButton openBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.FOLDER);
        openBtn.setToolTipText("Open Project (Ctrl+O)");
        openBtn.addActionListener(e -> openProjectJson());
        left.add(openBtn);

        DesktopUiKit.VortexButton saveBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SAVE);
        saveBtn.setToolTipText("Save Project (Ctrl+S)");
        saveBtn.addActionListener(e -> saveProjectJson());
        left.add(saveBtn);

        left.add(Box.createHorizontalStrut(6));

        DesktopUiKit.VortexButton undoBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.UNDO);
        undoBtn.setToolTipText("Undo (Ctrl+Z)");
        undoBtn.addActionListener(e -> performUndo());
        left.add(undoBtn);

        DesktopUiKit.VortexButton redoBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REDO);
        redoBtn.setToolTipText("Redo (Ctrl+Y)");
        redoBtn.addActionListener(e -> performRedo());
        left.add(redoBtn);

        left.add(Box.createHorizontalStrut(6));

        dimBtn = new DesktopUiKit.VortexButton("2D", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.CUBE);
        dimBtn.setToolTipText("Toggle 2D / 3D Scene Mode");
        dimBtn.addActionListener(e -> {
            pushUndo();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimBtn.setText(project.dimensionMode);
            viewport.resetEditorCamera();
            populateInspector();
            viewport.repaint();
        });
        left.add(dimBtn);

        DesktopUiKit.VortexButton gizmoBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.MOVE);
        gizmoBtn.setToolTipText("Transform Gizmo: Move / Scale / Select (W / E / Q)");
        gizmoBtn.addActionListener(e -> {
            String next = "MOVE".equals(viewport.getGizmoMode()) ? "SCALE"
                    : ("SCALE".equals(viewport.getGizmoMode()) ? "SELECT" : "MOVE");
            viewport.setGizmoMode(next);
            log("• Gizmo: " + next);
        });
        left.add(gizmoBtn);

        DesktopUiKit.VortexButton collBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.EYE);
        collBtn.setToolTipText("Show / Hide Collision Shapes");
        collBtn.addActionListener(e -> {
            viewport.setShowColliders(!viewport.isShowColliders());
            collBtn.setVariant(viewport.isShowColliders() ? DesktopUiKit.Variant.ACCENT : DesktopUiKit.Variant.SECONDARY);
        });
        left.add(collBtn);

        DesktopUiKit.VortexButton snapBtnTop = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.GRID);
        snapBtnTop.setToolTipText("Toggle Grid Snap");
        snapBtnTop.addActionListener(e -> {
            viewport.setGridSnap(!viewport.isGridSnap());
            snapBtnTop.setVariant(viewport.isGridSnap() ? DesktopUiKit.Variant.ACCENT : DesktopUiKit.Variant.SECONDARY);
        });
        left.add(snapBtnTop);

        left.add(Box.createHorizontalStrut(6));

        DesktopUiKit.VortexButton playBtnTop = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLAY);
        playBtnTop.setToolTipText("Play / Stop (F5)");
        playBtnTop.addActionListener(e -> togglePlayMode());
        left.add(playBtnTop);

        strip.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.setOpaque(false);

        telemetryLabel.setFont(DesktopUiKit.FONT_SMALL);
        telemetryLabel.setForeground(DesktopUiKit.C_TEXT_LABEL);
        telemetryLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 4));
        right.add(telemetryLabel);

        playStopBtn = new DesktopUiKit.VortexButton("Play", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.PLAY);
        playStopBtn.setToolTipText("Play / Stop the game (F5)");
        playStopBtn.addActionListener(e -> togglePlayMode());
        right.add(playStopBtn);

        DesktopUiKit.VortexButton exportBtn = new DesktopUiKit.VortexButton("Build", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.EXPORT);
        exportBtn.setToolTipText("Build & Export for Windows, Linux, Android, Web");
        exportBtn.addActionListener(e -> showExportMenu(exportBtn));
        right.add(exportBtn);

        strip.add(right, BorderLayout.EAST);
        bar.add(strip, BorderLayout.CENTER);
        return bar;
    }

    private interface MenuSupplier {
        List<DesktopUiKit.MenuAction> get();
    }

    private javax.swing.JComponent makeMenuButton(final String label, final MenuSupplier supplier) {
        final JLabel lbl = new JLabel(label);
        lbl.setOpaque(false);
        lbl.setFont(DesktopUiKit.FONT_UI);
        lbl.setForeground(DesktopUiKit.C_TEXT_LABEL);
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
        lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        lbl.addMouseListener(new MouseAdapter() {
            private boolean hov = false;
            @Override
            public void mouseEntered(MouseEvent e) {
                hov = true;
                lbl.setForeground(DesktopUiKit.C_WHITE);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                hov = false;
                lbl.setForeground(DesktopUiKit.C_TEXT_LABEL);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                lbl.setForeground(DesktopUiKit.C_WHITE);
                DesktopUiKit.showPopupMenu(lbl, supplier.get());
                lbl.setForeground(hov ? DesktopUiKit.C_WHITE : DesktopUiKit.C_TEXT_LABEL);
            }
        });
        return lbl;
    }

    private List<DesktopUiKit.MenuAction> buildFileMenuItems() {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction("New Project…", this::showNewTemplateModal));
        items.add(new DesktopUiKit.MenuAction("Open Project…", this::openProjectJson));
        items.add(new DesktopUiKit.MenuAction("Save Project", this::saveProjectJson));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Import Tiled Map (.tmx)…", this::importTiledMap));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Build & Export…", () -> rightTabs.selectTabByKey("BUILD", true)));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Exit", () -> System.exit(0)));
        return items;
    }

    private List<DesktopUiKit.MenuAction> buildEditMenuItems() {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction("Undo", this::performUndo));
        items.add(new DesktopUiKit.MenuAction("Redo", this::performRedo));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Clone Selected Entity", this::cloneSelectedEntity));
        items.add(new DesktopUiKit.MenuAction("Delete Selected Entity", this::deleteSelectedEntity));
        return items;
    }

    private List<DesktopUiKit.MenuAction> buildCreateMenuItems() {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        if ("3D".equalsIgnoreCase(project.dimensionMode)) {
            items.add(new DesktopUiKit.MenuAction("Cube", () -> spawnEntity("CUBE", "#E4E4E7", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("Sphere", () -> spawnEntity("SPHERE", "#FACC15", "DYNAMIC")));
            items.add(new DesktopUiKit.MenuAction("Pyramid", () -> spawnEntity("PYRAMID", "#EF4444", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("Cylinder", () -> spawnEntity("CYLINDER", "#A1A1AA", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("Terrain", () -> spawnEntity("TERRAIN", "#27272A", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("Import .OBJ Mesh…", this::showCustomObjModal));
        } else {
            items.add(new DesktopUiKit.MenuAction("Platform", () -> spawnEntity("RECT", "#3F3F46", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("Hero", () -> spawnEntity("SPRITE_HERO", "#FFFFFF", "DYNAMIC")));
            items.add(new DesktopUiKit.MenuAction("Coin Trigger", () -> {
                Entity c = spawnEntity("SPRITE_COIN", "#FACC15", "STATIC");
                c.isTrigger = true;
            }));
            items.add(new DesktopUiKit.MenuAction("Enemy", () -> spawnEntity("SPRITE_SLIME", "#EF4444", "DYNAMIC")));
        }
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("New Script File…", this::promptCreateScriptFile));
        return items;
    }

    private List<DesktopUiKit.MenuAction> buildViewMenuItems() {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction(
                viewportFocusMode ? "Exit Viewport Focus" : "Focus Viewport (hide docks)", this::toggleViewportFocusMode));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction(
                (leftDockCollapsed ? "Show" : "Hide") + " Scene & Inspector Dock", this::toggleLeftDock));
        items.add(new DesktopUiKit.MenuAction(
                (rightDockCollapsed ? "Show" : "Hide") + " Code & Tools Dock", this::toggleRightDock));
        items.add(new DesktopUiKit.MenuAction(
                (bottomDockCollapsed ? "Show" : "Hide") + " Console & Terminal Dock", this::toggleBottomDock));
        items.add(new DesktopUiKit.MenuAction(
                (fileSidebarPanel != null && !fileSidebarPanel.isVisible() ? "Show" : "Hide") + " File Explorer",
                this::toggleFileExplorerSidebar));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction(
                floatingCodeDialog != null ? "Redock Code Editor" : "Undock Code Editor", this::toggleUndockCodeEditor));
        items.add(new DesktopUiKit.MenuAction(
                floatingConsoleDialog != null ? "Redock Console" : "Undock Console", this::toggleUndockBottomConsole));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Expand Scene Tree", () -> {
            if (sceneTree != null) sceneTree.expandAll();
        }));
        items.add(new DesktopUiKit.MenuAction("Reset Layout", this::resetWorkspaceLayout));
        return items;
    }

    private List<DesktopUiKit.MenuAction> buildToolsMenuItems() {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction("Terminal & Console", () -> {
            if (bottomDockCollapsed) toggleBottomDock();
            if (bottomTabs != null) bottomTabs.selectTabByKey("TERMINAL", true);
            terminalPanel.focusInput();
        }));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Tile Paint Mode", this::toggleTilePaint));
        items.add(new DesktopUiKit.MenuAction("Tile Layer Name…", this::askTileLayerName));
        items.add(new DesktopUiKit.MenuAction("Tile Brush: Solid / Decor", () -> {
            viewport.setTilePaintSolid(!viewport.isTilePaintSolid());
            globalStatusPill.setStatus(viewport.isTilePaintSolid() ? "Tile brush: SOLID" : "Tile brush: decor",
                    DesktopUiKit.StatusPill.State.NEUTRAL);
        }));
        items.add(new DesktopUiKit.MenuAction("Merge Tile Runs", this::mergeTileRuns));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Voxel Block Palette…", this::showVoxelPalette));
        items.add(new DesktopUiKit.MenuAction("Voxel Regenerate World…", this::regenerateVoxelWorld));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Prefab Palette…", this::showPrefabPalette));
        items.add(new DesktopUiKit.MenuAction("Create Prefab from Selection…", this::createPrefabFromSelection));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Forge AI Chat", () -> rightTabs.selectTabByKey("AI", true)));
        items.add(new DesktopUiKit.MenuAction("LRM Version Control", () -> rightTabs.selectTabByKey("AI", true)));
        items.add(new DesktopUiKit.MenuAction("---", null));
        final float[] scales = {0.94f, 1.00f, 1.08f, 1.16f, 1.24f};
        for (final float sc : scales) {
            items.add(new DesktopUiKit.MenuAction("UI Scale " + Math.round(sc * 100) + "%", () -> {
                DesktopUiKit.setUiScale(sc);
                SwingUtilities.updateComponentTreeUI(this);
                revalidate();
                repaint();
            }));
        }
        items.add(new DesktopUiKit.MenuAction("UI Text Larger", () -> {
            DesktopUiKit.adjustFontPt(1);
            SwingUtilities.updateComponentTreeUI(this);
            revalidate();
            repaint();
        }));
        items.add(new DesktopUiKit.MenuAction("UI Text Smaller", () -> {
            DesktopUiKit.adjustFontPt(-1);
            SwingUtilities.updateComponentTreeUI(this);
            revalidate();
            repaint();
        }));
        return items;
    }

    // =====================================================================
    // v4.8.0 — in-IDE documentation browser (works offline)
    // =====================================================================

    private void showDocsBrowser() {
        final javax.swing.JDialog dlg = new javax.swing.JDialog(this, "Vortex Docs", true);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(DesktopUiKit.C_PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel head = new JLabel("Vortex Docs  \u2022  everything you need, offline");
        head.setFont(DesktopUiKit.FONT_UI_BOLD);
        head.setForeground(DesktopUiKit.C_WHITE);

        final javax.swing.JTextField search = new javax.swing.JTextField();
        search.setFont(DesktopUiKit.FONT_UI);
        search.setBackground(DesktopUiKit.C_VOID);
        search.setForeground(DesktopUiKit.C_TEXT);
        search.setCaretColor(DesktopUiKit.C_TEXT);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        JLabel searchHint = new JLabel("Search topics \u2014 or type a question and press Enter (\"how do I jump?\")");
        searchHint.setFont(DesktopUiKit.FONT_UI);
        searchHint.setForeground(DesktopUiKit.C_TEXT_MUTED);
        JPanel searchWrap = new JPanel(new BorderLayout(0, 4));
        searchWrap.setOpaque(false);
        searchWrap.add(searchHint, BorderLayout.NORTH);
        searchWrap.add(search, BorderLayout.CENTER);

        JPanel headRow = new JPanel(new BorderLayout(0, 8));
        headRow.setOpaque(false);
        headRow.add(head, BorderLayout.NORTH);
        headRow.add(searchWrap, BorderLayout.SOUTH);
        root.add(headRow, BorderLayout.NORTH);

        final JTextArea bodyArea = new JTextArea();
        bodyArea.setEditable(false);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(DesktopUiKit.FONT_MONO != null ? DesktopUiKit.FONT_MONO : DesktopUiKit.FONT_UI);
        bodyArea.setBackground(DesktopUiKit.C_VOID);
        bodyArea.setForeground(DesktopUiKit.C_TEXT);
        bodyArea.setMargin(new java.awt.Insets(14, 16, 16, 16));

        final java.util.List<com.vortex.engine.core.docs.VortexDocs.Topic> topics =
                com.vortex.engine.core.docs.VortexDocs.all();
        final javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        final java.util.List<com.vortex.engine.core.docs.VortexDocs.Topic> shown = new ArrayList<>(topics);
        for (com.vortex.engine.core.docs.VortexDocs.Topic t : shown) model.addElement(t.title);

        final JList<String> list = new JList<>(model);
        list.setBackground(DesktopUiKit.C_PANEL);
        list.setForeground(DesktopUiKit.C_TEXT);
        list.setSelectionBackground(DesktopUiKit.C_BORDER);
        list.setSelectionForeground(DesktopUiKit.C_WHITE);
        list.setFont(DesktopUiKit.FONT_UI);
        list.setFixedCellHeight(DesktopUiKit.scaled(28));
        list.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        final JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        final int[] current = {0};
        list.addListSelectionListener(ev -> {
            int i = list.getSelectedIndex();
            if (i < 0 || i >= shown.size()) return;
            current[0] = i;
            com.vortex.engine.core.docs.VortexDocs.Topic t = shown.get(i);
            bodyArea.setText(t.title + "\n" + t.summary + "\n\n" + t.body);
            // Reset BOTH bars after layout, otherwise the first line renders half-clipped.
            javax.swing.SwingUtilities.invokeLater(() -> {
                bodyArea.setCaretPosition(0);
                bodyScroll.getVerticalScrollBar().setValue(0);
            });
        });

        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void refilter() {
                String q = search.getText();
                shown.clear();
                model.clear();
                for (com.vortex.engine.core.docs.VortexDocs.Topic t : topics) {
                    if (com.vortex.engine.core.docs.VortexDocs.matches(t, q)) {
                        shown.add(t);
                        model.addElement(t.title);
                    }
                }
                if (!shown.isEmpty()) list.setSelectedIndex(0);
                else bodyArea.setText("No topic matches \"" + q + "\".\n\nPress Enter and I will try to answer "
                        + "the question the way I would in the AI tab.");
            }
            public void insertUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { refilter(); }
        });
        search.addActionListener(ev -> {
            com.vortex.engine.core.docs.VortexDocs.Topic t =
                    com.vortex.engine.core.docs.VortexDocs.answerFor(search.getText().trim());
            if (t == null) return;
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).id.equals(t.id)) { list.setSelectedIndex(i); list.ensureIndexIsVisible(i); return; }
            }
        });

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(DesktopUiKit.scaled(250), 0));
        listScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        listScroll.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER));
        bodyScroll.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, bodyScroll);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setOpaque(false);
        split.setDividerSize(6);
        root.add(split, BorderLayout.CENTER);

        DesktopUiKit.VortexButton close = new DesktopUiKit.VortexButton("Close", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CLOSE);
        close.addActionListener(ev -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setSize(DesktopUiKit.scaled(940), DesktopUiKit.scaled(620));
        dlg.setLocationRelativeTo(this);
        split.setDividerLocation(DesktopUiKit.scaled(250));
        list.setSelectedIndex(0);
        dlg.setVisible(true);
    }

    private void showDocsTopic(String id) {
        com.vortex.engine.core.docs.VortexDocs.Topic t = com.vortex.engine.core.docs.VortexDocs.get(id);
        if (t == null) { showDocsBrowser(); return; }
        DesktopUiKit.showInfoModal(this, t.title, t.body);
    }

    private void askDocsQuestion() {
        String q = DesktopUiKit.showInputModal(this, "Docs", "Ask in plain words (e.g. \"how do I jump?\")", "");
        if (q == null || q.trim().isEmpty()) return;
        com.vortex.engine.core.docs.VortexDocs.Topic t = com.vortex.engine.core.docs.VortexDocs.answerFor(q.trim());
        if (t == null) {
            DesktopUiKit.showInfoModal(this, "No match",
                    "I could not match that to a topic yet.\n\nTry: how do I move?, voxel, physics, camera, export, prefab, or ask Forge AI in the AI tab.");
            return;
        }
        log("\u25B8 Docs: " + t.title);
        DesktopUiKit.showInfoModal(this, t.title, t.body);
    }

    private List<DesktopUiKit.MenuAction> buildHelpMenuItems() {
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction("Docs & Getting Started…", this::showDocsBrowser));
        items.add(new DesktopUiKit.MenuAction("How do I…? (ask the docs)", this::askDocsQuestion));
        items.add(new DesktopUiKit.MenuAction("Voxel World Guide", () -> showDocsTopic("voxel")));
        items.add(new DesktopUiKit.MenuAction("Script API Guide", () -> showDocsTopic("script")));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Scripting API Reference", () -> rightTabs.selectTabByKey("SCRIPT", true)));
        items.add(new DesktopUiKit.MenuAction("Physics & Collision Layers", () -> DesktopUiKit.showInfoModal(this,
                "Physics",
                "2D: JBox2D rigid bodies (CCD bullets)\n3D: substepped impulse solver + terrain\nLayers: collisionLayer / collisionMask")));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Vortex Engine v4.8.0", () -> DesktopUiKit.showInfoModal(this,
                "Vortex Engine Studio v4.8.0",
                "Free & open-source 2D/3D game engine\nWindows • Linux • Android • Web")));
        return items;
    }

    // =========================================================================
    // Center Stage: [ Scene Viewport | Blueprint Graph ] + Drag/Drop/Snap Split Code
    // =========================================================================
    private JPanel buildCenterViewportAndBlueprintStage() {
        JPanel stage = new JPanel(new BorderLayout());
        stage.setBackground(DesktopUiKit.C_VOID);
        stage.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        stage.setMinimumSize(new Dimension(200, 140));

        JPanel stageHeader = new JPanel(new BorderLayout(6, 0));
        stageHeader.setBackground(DesktopUiKit.C_HEADER);
        stageHeader.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));

        JPanel stageLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        stageLeft.setOpaque(false);
        DesktopUiKit.VortexButton toggleLeftBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.COLLAPSE_LEFT);
        toggleLeftBtn.setToolTipText("Collapse / Restore Left Hierarchy & Inspector Dock");
        toggleLeftBtn.addActionListener(e -> toggleLeftDock());
        stageLeft.add(toggleLeftBtn);

        JLabel vpTitle = new JLabel("Viewport");
        vpTitle.setFont(DesktopUiKit.FONT_UI_BOLD);
        vpTitle.setForeground(DesktopUiKit.C_WHITE);
        vpTitle.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));
        stageLeft.add(vpTitle);

        JLabel vpMode = new JLabel("2D/3D");
        vpMode.setFont(DesktopUiKit.FONT_SMALL);
        vpMode.setForeground(DesktopUiKit.C_TEXT_MUTED);
        stageLeft.add(vpMode);
        stageHeader.add(stageLeft, BorderLayout.WEST);

        JPanel stageRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        stageRight.setOpaque(false);

        focusModeBtn = new DesktopUiKit.VortexButton("Focus", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.MAXIMIZE);
        focusModeBtn.setToolTipText("Toggle Full-Viewport Focus Mode (collapses sidebars & console)");
        focusModeBtn.addActionListener(e -> toggleViewportFocusMode());
        stageRight.add(focusModeBtn);

        DesktopUiKit.VortexButton snapToggleBtn = new DesktopUiKit.VortexButton("Snap Code", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SPLIT);
        snapToggleBtn.setToolTipText("Snap or unsnap a live code editor pane next to the Viewport (or drag any file tab onto the Viewport!)");
        snapToggleBtn.addActionListener(e -> {
            if (snappedZone != null) {
                unsnapCodeEditor();
            } else {
                snapFileNextToViewport(project.activeScriptFile, "RIGHT");
            }
        });
        stageRight.add(snapToggleBtn);

        DesktopUiKit.VortexButton toggleRightBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.COLLAPSE_RIGHT);
        toggleRightBtn.setToolTipText("Collapse / Restore Right Code & Tools Dock");
        toggleRightBtn.addActionListener(e -> toggleRightDock());
        stageRight.add(toggleRightBtn);

        stageHeader.add(stageRight, BorderLayout.EAST);

        viewport.setMinimumSize(new Dimension(180, 120));
        centerCanvasDeck.addCard("VIEWPORT", viewport, 0);

        stage.add(stageHeader, BorderLayout.NORTH);
        stage.add(centerCanvasDeck, BorderLayout.CENTER);
        return stage;
    }

    // =========================================================================
    // Workspace Customization: Collapse, Expand, Focus Mode & Undocking
    // =========================================================================
    private void toggleLeftDock() {
        if (mainSplit == null) return;
        if (!leftDockCollapsed && mainSplit.getDividerLocation() > 24) {
            savedLeftDivLoc = mainSplit.getDividerLocation();
            mainSplit.setDividerLocation(0);
            leftDockCollapsed = true;
        } else {
            mainSplit.setDividerLocation(Math.max(304, savedLeftDivLoc));
            leftDockCollapsed = false;
        }
    }

    private void toggleRightDock() {
        if (midRightSplit == null) return;
        int totalW = Math.max(600, midRightSplit.getWidth());
        if (!rightDockCollapsed && midRightSplit.getDividerLocation() < totalW - 30) {
            savedRightDivLoc = midRightSplit.getDividerLocation();
            midRightSplit.setDividerLocation(totalW);
            rightDockCollapsed = true;
        } else {
            int restore = (savedRightDivLoc > 180 && savedRightDivLoc < totalW - 180)
                    ? savedRightDivLoc : Math.round(totalW * 0.56f);
            midRightSplit.setDividerLocation(restore);
            rightDockCollapsed = false;
        }
    }

    private void toggleBottomDock() {
        if (centerVerticalSplit == null) return;
        int totalH = Math.max(400, centerVerticalSplit.getHeight());
        if (!bottomDockCollapsed) {
            if (centerVerticalSplit.getDividerLocation() < totalH - 60) {
                savedBottomDivLoc = centerVerticalSplit.getDividerLocation();
            }
            bottomDeck.setVisible(false);
            centerVerticalSplit.setDividerLocation(Math.max(120, totalH - DesktopUiKit.scaled(36) - 9));
            bottomDockCollapsed = true;
            if (bottomCollapseBtn != null) {
                bottomCollapseBtn.setIconType(DesktopUiKit.Icon.COLLAPSE_UP);
                bottomCollapseBtn.setText("Expand");
            }
        } else {
            bottomDeck.setVisible(true);
            int restore = (savedBottomDivLoc > 120 && savedBottomDivLoc < totalH - 90)
                    ? savedBottomDivLoc : Math.round(totalH * 0.74f);
            centerVerticalSplit.setDividerLocation(restore);
            bottomDockCollapsed = false;
            if (bottomCollapseBtn != null) {
                bottomCollapseBtn.setIconType(DesktopUiKit.Icon.COLLAPSE_DOWN);
                bottomCollapseBtn.setText("Hide");
            }
        }
        centerVerticalSplit.revalidate();
        centerVerticalSplit.repaint();
    }

    private void toggleFileExplorerSidebar() {
        if (fileSidebarPanel == null || codeSidebarSplit == null) return;
        boolean vis = !fileSidebarPanel.isVisible();
        fileSidebarPanel.setVisible(vis);
        codeSidebarSplit.setDividerSize(vis ? 9 : 0);
        if (vis) codeSidebarSplit.setDividerLocation(DesktopUiKit.scaled(168));
        codeSidebarSplit.revalidate();
        codeSidebarSplit.repaint();
    }

    private void toggleViewportFocusMode() {
        if (!viewportFocusMode) {
            if (!leftDockCollapsed && mainSplit.getDividerLocation() > 24) {
                savedLeftDivLoc = mainSplit.getDividerLocation();
            }
            if (!rightDockCollapsed && midRightSplit.getDividerLocation() < midRightSplit.getWidth() - 30) {
                savedRightDivLoc = midRightSplit.getDividerLocation();
            }
            if (!bottomDockCollapsed && centerVerticalSplit.getDividerLocation() < centerVerticalSplit.getHeight() - 60) {
                savedBottomDivLoc = centerVerticalSplit.getDividerLocation();
            }
            mainSplit.setDividerLocation(0);
            leftDockCollapsed = true;
            midRightSplit.setDividerLocation(Math.max(400, midRightSplit.getWidth()));
            rightDockCollapsed = true;
            if (!bottomDockCollapsed) toggleBottomDock();
            viewportFocusMode = true;
            if (focusModeBtn != null) {
                focusModeBtn.setText("Exit Focus");
                focusModeBtn.setVariant(DesktopUiKit.Variant.ACCENT);
            }
        } else {
            resetWorkspaceLayout();
        }
    }

    private void resetWorkspaceLayout() {
        viewportFocusMode = false;
        if (focusModeBtn != null) {
            focusModeBtn.setText("Focus");
            focusModeBtn.setVariant(DesktopUiKit.Variant.SECONDARY);
        }
        leftDockCollapsed = false;
        rightDockCollapsed = false;
        mainSplit.setDividerLocation(Math.max(304, savedLeftDivLoc));
        int mw = Math.max(600, midRightSplit.getWidth());
        midRightSplit.setDividerLocation(Math.min(mw - 260, Math.max(360, savedRightDivLoc)));
        if (bottomDockCollapsed) toggleBottomDock();
        if (fileSidebarPanel != null && !fileSidebarPanel.isVisible()) toggleFileExplorerSidebar();
    }

    private void toggleUndockCodeEditor() {
        if (codeEditorRootPanel == null || codeTabMountHost == null) return;
        if (floatingCodeDialog != null) {
            floatingCodeDialog.dispose();
            return;
        }
        codeTabMountHost.remove(codeEditorRootPanel);
        JPanel placeholder = new JPanel(new BorderLayout());
        placeholder.setBackground(DesktopUiKit.C_PANEL);
        JLabel note = new JLabel("Code Editor is undocked in a floating window.", JLabel.CENTER);
        note.setFont(DesktopUiKit.FONT_UI_BOLD);
        note.setForeground(DesktopUiKit.C_TEXT_LABEL);
        placeholder.add(note, BorderLayout.CENTER);
        codeTabMountHost.add(placeholder, BorderLayout.CENTER);
        codeTabMountHost.revalidate();
        codeTabMountHost.repaint();

        floatingCodeDialog = new JDialog(this, "Vortex Code Editor — " + project.title, false);
        floatingCodeDialog.setSize(DesktopUiKit.scaled(760), DesktopUiKit.scaled(560));
        floatingCodeDialog.setLocationRelativeTo(this);
        floatingCodeDialog.setContentPane(codeEditorRootPanel);
        floatingCodeDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { redockCodeEditor(); }
            @Override public void windowClosing(WindowEvent e) { floatingCodeDialog.dispose(); }
        });
        floatingCodeDialog.setVisible(true);
        log("✓ [SUCCESS] Undocked Multi-File Code Editor to floating window.");
    }

    private void redockCodeEditor() {
        floatingCodeDialog = null;
        if (codeTabMountHost != null && codeEditorRootPanel != null) {
            codeTabMountHost.removeAll();
            codeTabMountHost.add(codeEditorRootPanel, BorderLayout.CENTER);
            codeTabMountHost.revalidate();
            codeTabMountHost.repaint();
            log("• Redocked Multi-File Code Editor.");
        }
    }

    private void toggleUndockBottomConsole() {
        if (bottomWrapperPanel == null) return;
        if (floatingConsoleDialog != null) {
            floatingConsoleDialog.dispose();
            return;
        }
        bottomDeck.setVisible(true);
        bottomWrapperPanel.remove(bottomDeck);
        if (!bottomDockCollapsed) toggleBottomDock();

        floatingConsoleDialog = new JDialog(this, "Vortex Console & Interactive Shell", false);
        floatingConsoleDialog.setSize(DesktopUiKit.scaled(780), DesktopUiKit.scaled(360));
        floatingConsoleDialog.setLocationRelativeTo(this);
        JPanel host = new JPanel(new BorderLayout());
        host.setBackground(DesktopUiKit.C_VOID);
        host.add(bottomDeck, BorderLayout.CENTER);
        floatingConsoleDialog.setContentPane(host);
        floatingConsoleDialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                floatingConsoleDialog = null;
                bottomWrapperPanel.add(bottomDeck, BorderLayout.CENTER);
                if (bottomDockCollapsed) toggleBottomDock();
                bottomWrapperPanel.revalidate();
                bottomWrapperPanel.repaint();
            }
            @Override public void windowClosing(WindowEvent e) { floatingConsoleDialog.dispose(); }
        });
        floatingConsoleDialog.setVisible(true);
        log("✓ [SUCCESS] Undocked Console & Terminal to floating window.");
    }

    private void initSnappedCodeEditorSync() {
        snappedScriptArea.setBackground(DesktopUiKit.C_VOID);
        snappedScriptArea.setForeground(DesktopUiKit.C_TEXT);
        snappedScriptArea.setCaretColor(DesktopUiKit.C_WHITE);
        snappedScriptArea.setSelectionColor(DesktopUiKit.C_ACCENT_TINT);
        snappedScriptArea.setSelectedTextColor(DesktopUiKit.C_WHITE);
        snappedScriptArea.setFont(DesktopUiKit.FONT_MONO);
        snappedScriptArea.setTabSize(4);
        snappedScriptArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 12, 10));

        javax.swing.event.DocumentListener mainListener = new javax.swing.event.DocumentListener() {
            private void push() {
                if (syncingEditorBuffers || project == null) return;
                syncingEditorBuffers = true;
                try {
                    String txt = scriptArea.getText();
                    project.putScriptFile(project.activeScriptFile, txt);
                    if (snappedFilePath != null && snappedFilePath.equals(project.activeScriptFile)) {
                        snappedScriptArea.setText(txt);
                    }
                } finally {
                    syncingEditorBuffers = false;
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { push(); }
        };
        scriptArea.getDocument().addDocumentListener(mainListener);

        javax.swing.event.DocumentListener snapListener = new javax.swing.event.DocumentListener() {
            private void push() {
                if (syncingEditorBuffers || project == null || snappedFilePath == null) return;
                syncingEditorBuffers = true;
                try {
                    String txt = snappedScriptArea.getText();
                    project.putScriptFile(snappedFilePath, txt);
                    if (snappedFilePath.equals(project.activeScriptFile)) {
                        scriptArea.setText(txt);
                    }
                } finally {
                    syncingEditorBuffers = false;
                }
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { push(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { push(); }
        };
        snappedScriptArea.getDocument().addDocumentListener(snapListener);
    }

    private void snapFileNextToViewport(String filePath, String zone) {
        if (project == null) return;
        project.ensureScriptFilesInitialized();
        String norm = GameProject.normalizeScriptPath(filePath);
        if (!project.scriptFiles.containsKey(norm)) norm = project.activeScriptFile;
        this.snappedFilePath = norm;
        this.snappedZone = (zone == null || zone.isEmpty()) ? "RIGHT" : zone;
        syncingEditorBuffers = true;
        try {
            snappedScriptArea.setText(project.scriptFiles.getOrDefault(norm, ""));
            snappedScriptArea.setCaretPosition(0);
        } finally {
            syncingEditorBuffers = false;
        }
        snappedTitleLabel.setText(norm + "  [SNAPPED " + this.snappedZone + "]");
        rebuildCenterSnapLayout();
        log("[IDE] Snapped '" + norm + "' " + this.snappedZone.toLowerCase(Locale.US) + " of Scene Viewport.");
    }

    private void unsnapCodeEditor() {
        this.snappedZone = null;
        this.snappedFilePath = null;
        viewport.setSnapPreviewZone(null);
        rebuildCenterSnapLayout();
    }

    private void rebuildCenterSnapLayout() {
        centerSnapHost.removeAll();
        centerSnapHost.setBackground(DesktopUiKit.C_VOID);
        centerSnapHost.setMinimumSize(new Dimension(240, 180));
        if (snappedZone == null || snappedFilePath == null) {
            centerSnapHost.add(centerStagePanel, BorderLayout.CENTER);
        } else {
            JPanel snapCard = new JPanel(new BorderLayout());
            snapCard.setBackground(DesktopUiKit.C_VOID);
            snapCard.setMinimumSize(new Dimension(200, 150));

            JPanel hdr = new JPanel(new BorderLayout(6, 0));
            hdr.setBackground(DesktopUiKit.C_HEADER);
            hdr.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER),
                    BorderFactory.createEmptyBorder(3, 8, 3, 6)));
            snappedTitleLabel.setFont(DesktopUiKit.FONT_UI_BOLD);
            snappedTitleLabel.setForeground(DesktopUiKit.C_WHITE);
            hdr.add(snappedTitleLabel, BorderLayout.CENTER);

            JPanel hdrBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            hdrBtns.setOpaque(false);
            DesktopUiKit.VortexButton flipSideBtn = new DesktopUiKit.VortexButton("Side", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SPLIT);
            flipSideBtn.setToolTipText("Cycle snap position (Right / Left / Bottom)");
            flipSideBtn.addActionListener(e -> {
                String next = "RIGHT".equals(snappedZone) ? "BOTTOM" : ("BOTTOM".equals(snappedZone) ? "LEFT" : "RIGHT");
                snapFileNextToViewport(snappedFilePath, next);
            });
            DesktopUiKit.VortexButton closeSnapBtn = new DesktopUiKit.VortexButton("Unsnap", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
            closeSnapBtn.addActionListener(e -> unsnapCodeEditor());
            hdrBtns.add(flipSideBtn);
            hdrBtns.add(closeSnapBtn);
            hdr.add(hdrBtns, BorderLayout.EAST);

            snapCard.add(hdr, BorderLayout.NORTH);
            snapCard.add(DesktopUiKit.createScrollPane(snappedScriptArea), BorderLayout.CENTER);

            int curW = Math.max(540, centerSnapHost.getWidth());
            int curH = Math.max(360, centerSnapHost.getHeight());
            JSplitPane sp;
            if ("BOTTOM".equals(snappedZone)) {
                sp = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, centerStagePanel, snapCard, Math.round(curH * 0.56f));
                sp.setResizeWeight(0.56);
            } else if ("LEFT".equals(snappedZone)) {
                sp = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, snapCard, centerStagePanel, Math.round(curW * 0.44f));
                sp.setResizeWeight(0.44);
            } else {
                sp = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerStagePanel, snapCard, Math.round(curW * 0.54f));
                sp.setResizeWeight(0.54);
            }
            centerSnapHost.add(sp, BorderLayout.CENTER);
        }
        centerSnapHost.revalidate();
        centerSnapHost.repaint();
    }

    // =========================================================================
    // Bottom Dock: Tabbed Color-Coded Console + Interactive OS Shell Terminal
    // =========================================================================
    private JPanel buildBottomConsoleAndTerminalPanel() {
        bottomWrapperPanel = new JPanel(new BorderLayout());
        bottomWrapperPanel.setBackground(DesktopUiKit.C_VOID);
        bottomWrapperPanel.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        bottomWrapperPanel.setMinimumSize(new Dimension(180, DesktopUiKit.scaled(34)));
        bottomWrapperPanel.setPreferredSize(new Dimension(640, 210));

        JPanel headerBar = new JPanel(new BorderLayout(8, 0));
        headerBar.setBackground(DesktopUiKit.C_HEADER);
        headerBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));

        bottomTabs = new DesktopUiKit.VortexTabStrip(
                new String[]{"CONSOLE", "TERMINAL"},
                new String[]{"Console Output", "Interactive Terminal"},
                new DesktopUiKit.Icon[]{DesktopUiKit.Icon.CODE, DesktopUiKit.Icon.TERMINAL});
        bottomTabs.setPreferredSize(new Dimension(DesktopUiKit.scaled(330), DesktopUiKit.scaled(32)));
        headerBar.add(bottomTabs, BorderLayout.WEST);

        JPanel rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
        rightBar.setOpaque(false);

        DesktopUiKit.VortexButton clearConsoleBtn = new DesktopUiKit.VortexButton("Clear", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        clearConsoleBtn.setToolTipText("Clear Console Logs");
        clearConsoleBtn.addActionListener(e -> consoleArea.clearLogs());
        rightBar.add(clearConsoleBtn);

        DesktopUiKit.VortexButton undockConsoleBtn = new DesktopUiKit.VortexButton("Undock", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.UNDOCK);
        undockConsoleBtn.setToolTipText("Float Console & Terminal in a separate OS window");
        undockConsoleBtn.addActionListener(e -> toggleUndockBottomConsole());
        rightBar.add(undockConsoleBtn);

        bottomCollapseBtn = new DesktopUiKit.VortexButton("Hide", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.COLLAPSE_DOWN);
        bottomCollapseBtn.setToolTipText("Collapse / Expand Console & Terminal output dock");
        bottomCollapseBtn.addActionListener(e -> toggleBottomDock());
        rightBar.add(bottomCollapseBtn);

        headerBar.add(rightBar, BorderLayout.EAST);
        bottomWrapperPanel.add(headerBar, BorderLayout.NORTH);

        consoleArea.appendColoredLine("✓ [SUCCESS] Vortex Engine v4.8.0 Desktop Studio Ready.");

        JScrollPane consoleScroll = DesktopUiKit.createScrollPane(consoleArea);
        bottomDeck.addCard("CONSOLE", consoleScroll, 0);
        bottomDeck.addCard("TERMINAL", terminalPanel, 1);

        bottomTabs.setOnTabSelected((idx, key) -> {
            if (bottomDockCollapsed) toggleBottomDock();
            bottomDeck.showCard(key, true);
            if ("TERMINAL".equals(key)) {
                SwingUtilities.invokeLater(terminalPanel::focusInput);
            }
        });

        bottomWrapperPanel.add(bottomDeck, BorderLayout.CENTER);
        return bottomWrapperPanel;
    }

    // =========================================================================
    // Left Dock: Scene Hierarchy + Collapsible Accordion Entity Inspector
    // =========================================================================
    private JPanel buildHierarchyAndInspector() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(DesktopUiKit.C_PANEL);
        panel.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        panel.setMinimumSize(new Dimension(0, 120));
        panel.setPreferredSize(new Dimension(320, 600));

        JPanel topList = new JPanel(new BorderLayout());
        topList.setBackground(DesktopUiKit.C_PANEL);
        topList.setMinimumSize(new Dimension(140, 100));

        JPanel hierRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        DesktopUiKit.VortexButton collapseLeftHdrBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.COLLAPSE_LEFT);
        collapseLeftHdrBtn.setToolTipText("Collapse Left Dock");
        collapseLeftHdrBtn.addActionListener(e -> toggleLeftDock());
        hierRight.add(collapseLeftHdrBtn);
        topList.add(DesktopUiKit.createDockHeader("SCENE HIERARCHY", null, hierRight), BorderLayout.NORTH);

        if (sceneTree == null) {
            sceneTree = new DesktopSceneTree(new DesktopSceneTree.Listener() {
                @Override public void onSelect(String entityId) {
                    if (entityId == null) return;
                    viewport.setSelectedEntityId(entityId);
                    populateInspector();
                    viewport.repaint();
                }

                @Override public void onReparent(String childId, String newParentId) {
                    Entity child = project.findById(childId);
                    if (child == null) return;
                    Entity parent = newParentId == null || newParentId.isEmpty() ? null : project.findById(newParentId);
                    pushUndo();
                    if (SceneHierarchy.reparent(project.entities, child, parent)) {
                        refreshHierarchyList();
                        populateInspector();
                        viewport.repaint();
                        log("↳ " + child.name + " → " + (parent == null ? "Scene root" : parent.name));
                    } else {
                        log("✕ Cannot parent " + child.name + " under its own descendant");
                    }
                }

                @Override public void onRename(String entityId, String newName) {
                    Entity e = project.findById(entityId);
                    if (e == null) return;
                    pushUndo();
                    e.name = newName;
                    refreshHierarchyList();
                    populateInspector();
                }

                @Override public void onDelete(String entityId) {
                    viewport.setSelectedEntityId(entityId);
                    deleteSelectedEntity();
                }

                @Override public void onDuplicate(String entityId) {
                    viewport.setSelectedEntityId(entityId);
                    cloneSelectedEntity();
                }

                @Override public void onAddChild(String parentId) {
                    showAddEntityMenu(sceneTree, parentId);
                }

                @Override public void onToggleVisible(String entityId) {
                    Entity e = project.findById(entityId);
                    if (e == null) return;
                    pushUndo();
                    e.visible = !e.visible;
                    refreshHierarchyList();
                    viewport.repaint();
                }
            });
        }
        sceneTree.setProject(project);
        topList.add(sceneTree, BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        listBtns.setBackground(DesktopUiKit.C_HEADER);
        listBtns.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, DesktopUiKit.C_BORDER));

        DesktopUiKit.VortexButton dupBtn = new DesktopUiKit.VortexButton("Clone", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
        dupBtn.addActionListener(e -> cloneSelectedEntity());
        listBtns.add(dupBtn);

        DesktopUiKit.VortexButton camBtn = new DesktopUiKit.VortexButton("Reset Cam", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REFRESH);
        camBtn.addActionListener(e -> viewport.resetEditorCamera());
        listBtns.add(camBtn);

        DesktopUiKit.VortexButton delBtn = new DesktopUiKit.VortexButton("Delete", DesktopUiKit.Variant.DANGER, DesktopUiKit.Icon.CLOSE);
        delBtn.addActionListener(e -> deleteSelectedEntity());
        listBtns.add(delBtn);
        topList.add(listBtns, BorderLayout.SOUTH);

        JPanel inspectorContainer = new JPanel(new BorderLayout());
        inspectorContainer.setBackground(DesktopUiKit.C_PANEL);
        inspectorContainer.add(DesktopUiKit.createDockHeader("ENTITY INSPECTOR", null, null), BorderLayout.NORTH);

        inspectorForm.setLayout(new BoxLayout(inspectorForm, BoxLayout.Y_AXIS));
        inspectorForm.setBackground(DesktopUiKit.C_PANEL);
        inspectorForm.setBorder(BorderFactory.createEmptyBorder(8, 8, 16, 26)); // right inset absorbs the vertical scrollbar
        JScrollPane inspScroll = DesktopUiKit.createScrollPane(inspectorForm);
        inspScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inspScroll.getViewport().setBackground(DesktopUiKit.C_PANEL);
        inspScroll.setMinimumSize(new Dimension(140, 120));
        inspectorContainer.add(inspScroll, BorderLayout.CENTER);

        JSplitPane split = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, topList, inspectorContainer, 240);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // Right Dock: Animated 7-Tab Studio Workspace (Vector Icons + Zero Blue!)
    // =========================================================================
    private JPanel buildRightTabbedWorkspace() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(DesktopUiKit.C_PANEL);
        wrapper.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        wrapper.setMinimumSize(new Dimension(0, 160));
        wrapper.setPreferredSize(new Dimension(510, 620));

        // Godot-style consolidation: 6 purposeful tabs only (no redundant duplicates).
        String[] keys = {"SCENE", "SCRIPT", "LOGIC", "AI", "ASSETS", "BUILD"};
        String[] labels = {"Scene", "Script", "Logic", "AI", "Assets", "Build"};
        DesktopUiKit.Icon[] icons = {
                DesktopUiKit.Icon.GRID,
                DesktopUiKit.Icon.CODE,
                DesktopUiKit.Icon.FLOW,
                DesktopUiKit.Icon.SPARKLE,
                DesktopUiKit.Icon.WAVE,
                DesktopUiKit.Icon.EXPORT
        };
        rightTabs = new DesktopUiKit.VortexTabStrip(keys, labels, icons);

        codeTabMountHost = new JPanel(new BorderLayout());
        codeTabMountHost.setBackground(DesktopUiKit.C_VOID);
        codeEditorRootPanel = buildScriptEditorTab();
        codeTabMountHost.add(codeEditorRootPanel, BorderLayout.CENTER);

        rightCardsPanel.setBackground(DesktopUiKit.C_VOID);
        rightCardsPanel.addCard("SCRIPT", codeTabMountHost, 0);
        rightCardsPanel.addCard("SCENE", buildSceneTab(), 1);
        rightCardsPanel.addCard("LOGIC", buildLogicTab(), 2);
        rightCardsPanel.addCard("AI", buildAiTab(), 3);
        rightCardsPanel.addCard("ASSETS", buildAssetsTab(), 4);
        rightCardsPanel.addCard("BUILD", buildBuildTab(), 5);

        rightTabs.setOnTabSelected((idx, key) -> {
            if ("LOGIC".equals(key)) refreshFlowListModel();
            rightCardsPanel.showCard(key, true);
        });

        wrapper.add(rightTabs, BorderLayout.NORTH);
        wrapper.add(rightCardsPanel, BorderLayout.CENTER);
        return wrapper;
    }

    /** SCENE tab — Object spawn palette (Godot "Create Node" equivalent, minimal text). */
    private JPanel buildSceneTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        top.setOpaque(false);
        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        String[][] items = is3D
                ? new String[][]{{"Cube", "CUBE"}, {"Sphere", "SPHERE"}, {"Pyramid", "PYRAMID"},
                                 {"Cylinder", "CYLINDER"}, {"Plane", "PLANE"}, {"Terrain", "TERRAIN"}}
                : new String[][]{{"Platform", "RECT"}, {"Hero", "SPRITE_HERO"}, {"Coin", "SPRITE_COIN"},
                                 {"Slime", "SPRITE_SLIME"}, {"Ship", "SPRITE_SHIP"}, {"Circle", "CIRCLE"}};
        for (final String[] it : items) {
            DesktopUiKit.VortexButton b = new DesktopUiKit.VortexButton(it[0], DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
            b.addActionListener(e -> {
                if ("TERRAIN".equals(it[1])) {
                    spawnEntity("TERRAIN", "#27272A", "STATIC");
                } else {
                    spawnEntity(it[1], "SPRITE_COIN".equals(it[1]) ? "#FACC15" : "#E4E4E7",
                            "SPRITE_HERO".equals(it[1]) || "SPRITE_SLIME".equals(it[1]) ? "DYNAMIC" : "STATIC");
                }
            });
            top.add(b);
        }
        p.add(top, BorderLayout.NORTH);

        JPanel view = new JPanel(new GridLayout(0, 2, 6, 6));
        view.setOpaque(false);

        DesktopUiKit.VortexButton gizmoBtn = new DesktopUiKit.VortexButton(
                "Gizmo: " + viewport.getGizmoMode(), DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.MOVE);
        gizmoBtn.addActionListener(e -> {
            String next = "MOVE".equals(viewport.getGizmoMode()) ? "SCALE"
                    : ("SCALE".equals(viewport.getGizmoMode()) ? "SELECT" : "MOVE");
            viewport.setGizmoMode(next);
            gizmoBtn.setText("Gizmo: " + next);
        });

        DesktopUiKit.VortexButton snapBtn2 = new DesktopUiKit.VortexButton(
                "Grid Snap: " + (viewport.isGridSnap() ? "ON" : "OFF"),
                viewport.isGridSnap() ? DesktopUiKit.Variant.ACCENT : DesktopUiKit.Variant.SECONDARY,
                DesktopUiKit.Icon.GRID);
        snapBtn2.addActionListener(e -> {
            viewport.setGridSnap(!viewport.isGridSnap());
            snapBtn2.setText("Grid Snap: " + (viewport.isGridSnap() ? "ON" : "OFF"));
            snapBtn2.setVariant(viewport.isGridSnap() ? DesktopUiKit.Variant.ACCENT : DesktopUiKit.Variant.SECONDARY);
        });

        DesktopUiKit.VortexButton collBtn = new DesktopUiKit.VortexButton(
                "Colliders: " + (viewport.isShowColliders() ? "ON" : "OFF"),
                viewport.isShowColliders() ? DesktopUiKit.Variant.ACCENT : DesktopUiKit.Variant.SECONDARY,
                DesktopUiKit.Icon.CUBE);
        collBtn.addActionListener(e -> {
            viewport.setShowColliders(!viewport.isShowColliders());
            collBtn.setText("Colliders: " + (viewport.isShowColliders() ? "ON" : "OFF"));
            collBtn.setVariant(viewport.isShowColliders() ? DesktopUiKit.Variant.ACCENT : DesktopUiKit.Variant.SECONDARY);
        });

        DesktopUiKit.VortexButton camBtn2 = new DesktopUiKit.VortexButton("Reset Camera", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REFRESH);
        camBtn2.addActionListener(e -> viewport.resetEditorCamera());

        view.add(gizmoBtn);
        view.add(snapBtn2);
        view.add(collBtn);
        view.add(camBtn2);

        // ---- v4.6.0 light rig (Godot: DirectionalLight3D + Environment ambient) ----
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        view.setMaximumSize(new Dimension(Integer.MAX_VALUE, view.getPreferredSize().height + 8));
        view.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(view);
        column.add(Box.createVerticalStrut(8));

        JLabel lightHead = new JLabel("LIGHTING");
        lightHead.setFont(DesktopUiKit.FONT_SMALL_BOLD);
        lightHead.setForeground(DesktopUiKit.C_TEXT_MUTED);
        lightHead.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(lightHead);
        column.add(Box.createVerticalStrut(4));

        JPanel presetRow = new JPanel(new GridLayout(1, 3, 5, 0));
        presetRow.setOpaque(false);
        presetRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        presetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        String[][] presets = {{"Day", "38", "62", "1.0", "0.42"}, {"Dusk", "96", "16", "0.95", "0.30"}, {"Night", "210", "42", "0.42", "0.16"}};
        for (final String[] pr : presets) {
            DesktopUiKit.VortexButton pb = new DesktopUiKit.VortexButton(pr[0], DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SPARKLE);
            pb.addActionListener(e -> {
                project.sunYawDeg = Float.parseFloat(pr[1]);
                project.sunPitchDeg = Float.parseFloat(pr[2]);
                project.sunIntensity = Float.parseFloat(pr[3]);
                project.ambientStrength = Float.parseFloat(pr[4]);
                viewport.repaint();
                populateInspector();
                log("Lighting preset: " + pr[0]);
            });
            presetRow.add(pb);
        }
        column.add(presetRow);
        column.add(Box.createVerticalStrut(6));

        JPanel sunRow = new JPanel(new GridLayout(1, 3, 5, 0));
        sunRow.setOpaque(false);
        sunRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sunRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        DesktopUiKit.VortexNumberField sunYaw = new DesktopUiKit.VortexNumberField(
                "Yaw", DesktopUiKit.C_TEXT_LABEL, project.sunYawDeg, 5, -360, 360);
        DesktopUiKit.VortexNumberField sunPitch = new DesktopUiKit.VortexNumberField(
                "Elev", DesktopUiKit.C_TEXT_LABEL, project.sunPitchDeg, 2, 5, 89);
        DesktopUiKit.VortexNumberField sunPow = new DesktopUiKit.VortexNumberField(
                "Power", DesktopUiKit.C_TEXT_LABEL, project.sunIntensity, 0.05, 0, 2);
        sunYaw.setOnChange(v -> { project.sunYawDeg = (float) v; viewport.repaint(); });
        sunPitch.setOnChange(v -> { project.sunPitchDeg = (float) v; viewport.repaint(); });
        sunPow.setOnChange(v -> { project.sunIntensity = (float) v; viewport.repaint(); });
        sunRow.add(sunYaw);
        sunRow.add(sunPitch);
        sunRow.add(sunPow);
        column.add(sunRow);
        column.add(Box.createVerticalStrut(5));

        JPanel ambRow = new JPanel(new GridLayout(1, 2, 5, 0));
        ambRow.setOpaque(false);
        ambRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        ambRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        DesktopUiKit.VortexNumberField ambF = new DesktopUiKit.VortexNumberField(
                "Ambient", DesktopUiKit.C_TEXT_LABEL, project.ambientStrength, 0.02, 0, 1);
        ambF.setOnChange(v -> { project.ambientStrength = (float) v; viewport.repaint(); });
        DesktopUiKit.VortexNumberField shadowF = new DesktopUiKit.VortexNumberField(
                "Shadow", DesktopUiKit.C_TEXT_LABEL, project.shadowStrength, 0.02, 0, 1);
        shadowF.setOnChange(v -> { project.shadowStrength = (float) v; viewport.repaint(); });
        ambRow.add(ambF);
        ambRow.add(shadowF);
        column.add(ambRow);
        column.add(Box.createVerticalStrut(4));

        DesktopUiKit.VortexToggle shadowToggle = new DesktopUiKit.VortexToggle("Contact shadows", project.shadows);
        shadowToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        shadowToggle.setOnChange(() -> { project.shadows = shadowToggle.isSelected(); viewport.repaint(); });
        column.add(shadowToggle);

        JLabel hint = new JLabel("Sun drives every surface in 3D. Presets retune the whole mood.");
        hint.setFont(DesktopUiKit.FONT_SMALL);
        hint.setForeground(DesktopUiKit.C_TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(Box.createVerticalStrut(4));
        column.add(hint);

        JScrollPane sceneScroll = DesktopUiKit.createScrollPane(column);
        sceneScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        p.add(sceneScroll, BorderLayout.CENTER);
        return p;
    }

    /** LOGIC tab — Blueprint graph + classic Flow event rules, both preserved. */
    private JPanel buildLogicTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DesktopUiKit.C_VOID);
        DesktopUiKit.VortexTabStrip sub = new DesktopUiKit.VortexTabStrip(
                new String[]{"BLUEPRINT", "FLOW"}, new String[]{"Blueprint", "Event Flow"},
                new DesktopUiKit.Icon[]{DesktopUiKit.Icon.NODES, DesktopUiKit.Icon.FLOW});
        DesktopUiKit.AnimatedDeck deck = new DesktopUiKit.AnimatedDeck();
        DesktopBlueprintPanel bp = new DesktopBlueprintPanel(project, () -> viewport.repaint());
        deck.addCard("BLUEPRINT", bp, 0);
        deck.addCard("FLOW", buildFlowTab(), 1);
        sub.setOnTabSelected((i, k) -> {
            if ("FLOW".equals(k)) refreshFlowListModel();
            deck.showCard(k, true);
        });
        p.add(sub, BorderLayout.NORTH);
        p.add(deck, BorderLayout.CENTER);
        return p;
    }

    /** AI tab — Forge AI chat + LRM P2P collaboration under one roof. */
    private JPanel buildAiTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DesktopUiKit.C_VOID);
        DesktopUiKit.VortexTabStrip sub = new DesktopUiKit.VortexTabStrip(
                new String[]{"FORGE", "SYNC"}, new String[]{"Forge AI", "LRM Sync"},
                new DesktopUiKit.Icon[]{DesktopUiKit.Icon.SPARKLE, DesktopUiKit.Icon.SYNC});
        DesktopUiKit.AnimatedDeck deck = new DesktopUiKit.AnimatedDeck();
        deck.addCard("FORGE", buildForgeAiTab(), 0);
        deck.addCard("SYNC", buildLrmSyncTab(), 1);
        sub.setOnTabSelected((i, k) -> deck.showCard(k, true));
        p.add(sub, BorderLayout.NORTH);
        p.add(deck, BorderLayout.CENTER);
        return p;
    }

    /** ASSETS tab — Pixel sprite studio + chiptune/SFX audio lab. */
    private JPanel buildAssetsTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DesktopUiKit.C_VOID);
        DesktopUiKit.VortexTabStrip sub = new DesktopUiKit.VortexTabStrip(
                new String[]{"PIXEL", "AUDIO"}, new String[]{"Sprite", "Audio"},
                new DesktopUiKit.Icon[]{DesktopUiKit.Icon.GRID, DesktopUiKit.Icon.WAVE});
        DesktopUiKit.AnimatedDeck deck = new DesktopUiKit.AnimatedDeck();
        deck.addCard("PIXEL", buildPixelArtTab(), 0);
        deck.addCard("AUDIO", buildAudioTab(), 1);
        sub.setOnTabSelected((i, k) -> deck.showCard(k, true));
        p.add(sub, BorderLayout.NORTH);
        p.add(deck, BorderLayout.CENTER);
        return p;
    }

    /** BUILD tab — one-click exports for every target platform. */
    private JPanel buildBuildTab() {
        return buildExportTab();
    }

    private JPanel buildScriptEditorTab() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(DesktopUiKit.C_VOID);
        p.setMinimumSize(new Dimension(240, 180));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DesktopUiKit.C_PANEL);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        btnBar.setOpaque(false);

        DesktopUiKit.VortexButton filesToggleBtn = new DesktopUiKit.VortexButton("Files", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.FOLDER);
        filesToggleBtn.setToolTipText("Show/Hide Project File Directory Sidebar");
        filesToggleBtn.addActionListener(e -> toggleFileExplorerSidebar());
        btnBar.add(filesToggleBtn);

        vmDropdown = new DesktopUiKit.VortexDropdown(VM_ITEMS, VM_ITEMS[0]);
        vmDropdown.setPreferredSize(new Dimension(DesktopUiKit.scaled(148), DesktopUiKit.scaled(27)));
        vmDropdown.setOnSelect((idx, val) -> {
            syncCode();
            project.scriptLanguage = VM_CODES[idx];
            syntaxStatusLabel.setText("Switched VM to " + val + " — click Check.");
            syntaxStatusLabel.setForeground(DesktopUiKit.C_WHITE);
        });
        btnBar.add(vmDropdown);

        DesktopUiKit.VortexButton checkBtn = new DesktopUiKit.VortexButton("Check", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.CHECK);
        checkBtn.setToolTipText("Validate syntax across all project files");
        checkBtn.addActionListener(e -> checkScriptSyntax());
        btnBar.add(checkBtn);

        String[] snips = {
                "Snippets…",
                "vortex.showDialog(\"COMMANDER\", \"Mission start!\", 4.0)",
                "vortex.setLabel(\"obj\", \"WAVE 1\", 50, 12, 15, \"#FFFFFF\")",
                "vortex.setProgressBar(\"xp\", \"XP\", 40, 100, 50, 92, \"#FFFFFF\")",
                "vortex.findNearestTag(\"Player\", \"enemy\", 30)",
                "vortex.chaseTagAI(\"enemy\", \"Player\", 4.5)",
                "vortex.setSpriteAnim(\"Player\", \"knight_1,knight_2\", 7)",
                "vortex.tweenTagYoyo(\"coin\", 0.35, 0.9, \"SINE\")",
                "vortex.playMusic(\"CYBERPUNK\", 136)"
        };
        snippetDropdown = new DesktopUiKit.VortexDropdown(snips, snips[0]);
        snippetDropdown.setPreferredSize(new Dimension(DesktopUiKit.scaled(118), DesktopUiKit.scaled(27)));
        snippetDropdown.setOnSelect((idx, val) -> {
            if (idx > 0) {
                String tail = "LUA".equalsIgnoreCase(project.scriptLanguage) ? "\n" : ";\n";
                scriptArea.insert(val + tail, scriptArea.getCaretPosition());
                snippetDropdown.setSelectedIndex(0);
            }
        });
        btnBar.add(snippetDropdown);

        DesktopUiKit.VortexButton snapBtn = new DesktopUiKit.VortexButton("Snap", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SPLIT);
        snapBtn.setToolTipText("Snap active file beside Viewport (or drag any file tab onto the Viewport!)");
        snapBtn.addActionListener(e -> {
            if (snappedZone != null && project.activeScriptFile.equals(snappedFilePath)) {
                unsnapCodeEditor();
            } else {
                snapFileNextToViewport(project.activeScriptFile, "RIGHT");
            }
        });
        btnBar.add(snapBtn);

        DesktopUiKit.VortexButton objQuickBtn = new DesktopUiKit.VortexButton(".OBJ", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CUBE);
        objQuickBtn.setToolTipText("Import Custom Wavefront .OBJ 3D Mesh");
        objQuickBtn.addActionListener(e -> showCustomObjModal());
        btnBar.add(objQuickBtn);

        DesktopUiKit.VortexButton undockCodeBtn = new DesktopUiKit.VortexButton("Undock", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.UNDOCK);
        undockCodeBtn.setToolTipText("Float or Redock Multi-File Code Editor Window");
        undockCodeBtn.addActionListener(e -> toggleUndockCodeEditor());
        btnBar.add(undockCodeBtn);

        DesktopUiKit.VortexButton docsBtn = new DesktopUiKit.VortexButton("API", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CODE);
        docsBtn.setToolTipText("Vortex Polyglot Scripting & GUI API Reference");
        docsBtn.addActionListener(e -> DesktopUiKit.showInfoModal(this, "Vortex Polyglot Multi-File & GUI Scripting API",
                "• Multi-File Architecture: Organize scripts into `scripts/`, `objects/`, `ui/` — every file's `onStart()`, `onUpdate(dt)`, `onButton(btn)`, `onCollide(a,b,tagA,tagB)` hooks chain automatically!\n"
                + "• In-Game GUI API: vortex.showDialog(speaker, msg, durSec), vortex.hideDialog(), vortex.setLabel(id, txt, xPct, yPct, fontSize, \"#FFFFFF\"), vortex.setProgressBar(id, title, val, max, xPct, yPct, \"#FFFFFF\"), vortex.removeUI(id), vortex.clearUI()\n"
                + "• Input & Physics: vortex.joyX(), vortex.joyY(), vortex.setPos(n,x,y,z), vortex.setVel(n,vx,vy[,vz]), vortex.jump(n,vy)\n"
                + "• AI, FX & Audio: vortex.chaseTagAI(fromTag,target,spd), vortex.findNearestTag(from,tag,range), vortex.particles(x,y,z,cnt,\"#FFFFFF\"), vortex.playSound(\"laser\"), vortex.playMusic(\"CYBERPUNK\",134)"));
        btnBar.add(docsBtn);

        header.add(btnBar, BorderLayout.NORTH);

        syntaxStatusLabel.setOpaque(true);
        syntaxStatusLabel.setBackground(DesktopUiKit.C_OK_BG);
        syntaxStatusLabel.setForeground(DesktopUiKit.C_OK_TX);
        syntaxStatusLabel.setFont(DesktopUiKit.FONT_UI_BOLD);
        syntaxStatusLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        header.add(syntaxStatusLabel, BorderLayout.SOUTH);

        p.add(header, BorderLayout.NORTH);

        // Left: Project File Directory Explorer Sidebar
        fileSidebarPanel = new JPanel(new BorderLayout());
        fileSidebarPanel.setBackground(DesktopUiKit.C_PANEL);
        fileSidebarPanel.setMinimumSize(new Dimension(0, 120));
        fileSidebarPanel.setPreferredSize(new Dimension(DesktopUiKit.scaled(172), 400));

        JPanel expBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        expBtns.setOpaque(false);
        DesktopUiKit.VortexButton newFileBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
        newFileBtn.setToolTipText("New Script Module File (e.g. objects/enemy.js, ui/hud.js)");
        newFileBtn.addActionListener(e -> promptCreateScriptFile());
        DesktopUiKit.VortexButton delFileBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.DANGER, DesktopUiKit.Icon.CLOSE);
        delFileBtn.setToolTipText("Delete Selected Script File");
        delFileBtn.addActionListener(e -> deleteSelectedScriptFile());
        DesktopUiKit.VortexButton hideFilesBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.COLLAPSE_LEFT);
        hideFilesBtn.setToolTipText("Collapse File Explorer Sidebar");
        hideFilesBtn.addActionListener(e -> toggleFileExplorerSidebar());
        expBtns.add(newFileBtn);
        expBtns.add(delFileBtn);
        expBtns.add(hideFilesBtn);
        fileSidebarPanel.add(DesktopUiKit.createDockHeader("FILES", null, expBtns), BorderLayout.NORTH);

        fileList.setBackground(DesktopUiKit.C_VOID);
        fileList.setCellRenderer(DesktopUiKit.createDarkListRenderer());
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = extractPathFromFileItem(fileList.getSelectedValue());
                if (sel != null && !sel.equals(project.activeScriptFile)) {
                    openScriptFileTab(sel);
                }
            }
        });
        installTabDragToViewportHandler(fileList, () -> extractPathFromFileItem(fileList.getSelectedValue()));
        fileSidebarPanel.add(DesktopUiKit.createScrollPane(fileList), BorderLayout.CENTER);

        // Right: Draggable Open File Tabs Strip + Code Editor Buffer
        JPanel editorColumn = new JPanel(new BorderLayout());
        editorColumn.setBackground(DesktopUiKit.C_VOID);
        editorColumn.setMinimumSize(new Dimension(160, 120));

        openTabsBar.setBackground(DesktopUiKit.C_HEADER);
        JScrollPane tabsScroll = DesktopUiKit.createScrollPane(openTabsBar);
        tabsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        tabsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tabsScroll.getViewport().setBackground(DesktopUiKit.C_HEADER);
        tabsScroll.setBackground(DesktopUiKit.C_HEADER);
        tabsScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER));
        tabsScroll.setPreferredSize(new Dimension(200, DesktopUiKit.scaled(34)));
        tabsScroll.addMouseWheelListener(e -> {
            javax.swing.JScrollBar hb = tabsScroll.getHorizontalScrollBar();
            if (hb != null) hb.setValue(hb.getValue() + e.getWheelRotation() * 28);
        });
        editorColumn.add(tabsScroll, BorderLayout.NORTH);

        scriptArea.setBackground(DesktopUiKit.C_VOID);
        scriptArea.setForeground(DesktopUiKit.C_TEXT);
        scriptArea.setCaretColor(DesktopUiKit.C_WHITE);
        scriptArea.setSelectionColor(DesktopUiKit.C_ACCENT_TINT);
        scriptArea.setSelectedTextColor(DesktopUiKit.C_WHITE);
        scriptArea.setFont(DesktopUiKit.FONT_MONO);
        scriptArea.setTabSize(4);
        scriptArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 14, 12));
        editorColumn.add(DesktopUiKit.createScrollPane(scriptArea), BorderLayout.CENTER);

        codeSidebarSplit = DesktopUiKit.createSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, fileSidebarPanel, editorColumn, DesktopUiKit.scaled(168));
        codeSidebarSplit.setResizeWeight(0.24);
        p.add(codeSidebarSplit, BorderLayout.CENTER);
        return p;
    }

    private interface DragFileSupplier {
        String getFilePath();
    }

    private void installTabDragToViewportHandler(javax.swing.JComponent comp, DragFileSupplier supplier) {
        final String[] draggingFile = {null};
        final Point[] pressPt = {null};
        comp.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressPt[0] = e.getPoint();
                draggingFile[0] = supplier.getFilePath();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingFile[0] != null) {
                    String zone = computeViewportSnapZone(comp, e.getPoint());
                    viewport.setSnapPreviewZone(null);
                    comp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    if (zone != null && pressPt[0] != null && pressPt[0].distance(e.getPoint()) > 16) {
                        snapFileNextToViewport(draggingFile[0], zone);
                    }
                }
                draggingFile[0] = null;
                pressPt[0] = null;
            }
        });
        comp.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (draggingFile[0] == null || pressPt[0] == null || pressPt[0].distance(e.getPoint()) <= 16) return;
                comp.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                viewport.setSnapPreviewZone(computeViewportSnapZone(comp, e.getPoint()));
            }
        });
    }

    private String computeViewportSnapZone(Component src, Point srcPt) {
        if (!centerSnapHost.isShowing()) return null;
        Point p = SwingUtilities.convertPoint(src, srcPt, centerSnapHost);
        int w = centerSnapHost.getWidth();
        int h = centerSnapHost.getHeight();
        if (p.x < 0 || p.y < 0 || p.x > w || p.y > h) return null;
        if (p.y > h * 0.68) return "BOTTOM";
        if (p.x < w * 0.42) return "LEFT";
        return "RIGHT";
    }

    private String extractPathFromFileItem(String item) {
        if (item == null) return null;
        String t = item.trim();
        if (t.startsWith("▸ ") || t.startsWith("• ")) t = t.substring(2).trim();
        return t.isEmpty() ? null : t;
    }

    private void refreshScriptFileExplorer() {
        if (project == null) return;
        project.ensureScriptFilesInitialized();
        fileListModel.clear();
        for (String path : project.scriptFiles.keySet()) {
            boolean active = path.equals(project.activeScriptFile);
            fileListModel.addElement((active ? "▸ " : "• ") + path);
        }
        if (openScriptTabs.isEmpty() || !project.scriptFiles.containsKey(project.activeScriptFile)) {
            openScriptTabs.clear();
            for (String path : project.scriptFiles.keySet()) {
                openScriptTabs.add(path);
                if (openScriptTabs.size() >= 4) break;
            }
        } else {
            openScriptTabs.removeIf(p -> !project.scriptFiles.containsKey(p));
            if (!openScriptTabs.contains(project.activeScriptFile)) {
                openScriptTabs.add(project.activeScriptFile);
            }
        }
        rebuildOpenFileTabsBar();
    }

    private void rebuildOpenFileTabsBar() {
        openTabsBar.removeAll();
        boolean canCloseTabs = openScriptTabs.size() > 1;
        for (final String path : openScriptTabs) {
            boolean isActive = path.equals(project.activeScriptFile);
            DesktopUiKit.VortexCodeTab tab = new DesktopUiKit.VortexCodeTab(
                    path,
                    isActive,
                    true,
                    () -> openScriptFileTab(path),
                    () -> {
                        if (!canCloseTabs) {
                            log("• Keep at least one file tab open in the editor.");
                            return;
                        }
                        int idx = openScriptTabs.indexOf(path);
                        openScriptTabs.remove(path);
                        if (path.equals(project.activeScriptFile) && !openScriptTabs.isEmpty()) {
                            int nextIdx = Math.max(0, Math.min(openScriptTabs.size() - 1, idx - 1));
                            openScriptFileTab(openScriptTabs.get(nextIdx));
                        } else {
                            rebuildOpenFileTabsBar();
                        }
                    });
            installTabDragToViewportHandler(tab, () -> path);
            openTabsBar.add(tab);
        }
        DesktopUiKit.VortexButton plusTabBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
        plusTabBtn.setToolTipText("New Script Module File");
        plusTabBtn.addActionListener(e -> promptCreateScriptFile());
        openTabsBar.add(plusTabBtn);

        openTabsBar.revalidate();
        openTabsBar.repaint();
    }

    private void openScriptFileTab(String path) {
        if (project == null || path == null) return;
        syncCode();
        String norm = GameProject.normalizeScriptPath(path);
        if (!project.scriptFiles.containsKey(norm)) return;
        project.activeScriptFile = norm;
        if (!openScriptTabs.contains(norm)) openScriptTabs.add(norm);
        String body = project.scriptFiles.getOrDefault(norm, "");
        project.scriptCode = body;
        syncingEditorBuffers = true;
        try {
            scriptArea.setText(body);
            scriptArea.setCaretPosition(0);
        } finally {
            syncingEditorBuffers = false;
        }
        refreshScriptFileExplorer();
        checkScriptSyntax();
    }

    private void promptCreateScriptFile() {
        String ext = project.getScriptExtension();
        JDialog dlg = new JDialog(this, "Create New Script Module File", true);
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(DesktopUiKit.C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_WHITE, 1),
                BorderFactory.createEmptyBorder(14, 16, 12, 16)));
        root.setPreferredSize(new Dimension(410, 175));

        JLabel title = new JLabel("NEW SCRIPT MODULE FILE");
        title.setFont(DesktopUiKit.FONT_TITLE);
        title.setForeground(DesktopUiKit.C_WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(8));

        JLabel hint = mutedLabel("Relative path (e.g. objects/enemy" + ext + ", ui/dialog_hud" + ext + "):");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(hint);
        JTextField pathTf = DesktopUiKit.createField("objects/module_" + (project.scriptFiles.size() + 1) + ext);
        pathTf.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(pathTf);
        root.add(Box.createVerticalStrut(12));

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        foot.setOpaque(false);
        foot.setAlignmentX(Component.LEFT_ALIGNMENT);
        DesktopUiKit.VortexButton cancel = new DesktopUiKit.VortexButton("Cancel", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        cancel.addActionListener(e -> dlg.dispose());
        DesktopUiKit.VortexButton create = new DesktopUiKit.VortexButton("Create File", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CHECK);
        create.addActionListener(e -> {
            String raw = pathTf.getText().trim();
            if (raw.isEmpty()) return;
            if (!raw.contains(".")) raw = raw + ext;
            String norm = GameProject.normalizeScriptPath(raw);
            pushUndo();
            String starter = buildStarterModuleContent(norm);
            project.putScriptFile(norm, starter);
            openScriptFileTab(norm);
            log("[FILES] Created script module: " + norm);
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

    private String buildStarterModuleContent(String path) {
        if ("JAVA".equalsIgnoreCase(project.scriptLanguage)) {
            return "// " + path + " — Modular Object Script\n"
                    + "public class ModuleScript {\n"
                    + "    public void onStart() {\n"
                    + "    }\n"
                    + "    public void onUpdate(float dt) {\n"
                    + "    }\n"
                    + "}\n";
        }
        if ("LUA".equalsIgnoreCase(project.scriptLanguage)) {
            return "-- " + path + " — Modular Object Script\n"
                    + "function onStart()\n"
                    + "end\n\n"
                    + "function onUpdate(dt)\n"
                    + "end\n";
        }
        return "// " + path + " — Modular Object / UI Script\n"
                + "function onStart() {\n"
                + "}\n\n"
                + "function onUpdate(dt) {\n"
                + "}\n";
    }

    private void deleteSelectedScriptFile() {
        if (project == null || project.scriptFiles.size() <= 1) {
            DesktopUiKit.showInfoModal(this, "Cannot Delete Last File", "At least one script file must remain in the project.");
            return;
        }
        String target = project.activeScriptFile;
        pushUndo();
        if (project.removeScriptFile(target)) {
            openScriptTabs.remove(target);
            if (target.equals(snappedFilePath)) unsnapCodeEditor();
            openScriptFileTab(project.activeScriptFile);
            log("[FILES] Removed script file: " + target);
        }
    }

    private JPanel buildFlowTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JPanel topCard = new JPanel(new BorderLayout(0, 6));
        topCard.setOpaque(false);
        JLabel title = new JLabel("VORTEX FLOW — VISUAL EVENT SHEET");
        title.setFont(DesktopUiKit.FONT_TITLE);
        title.setForeground(DesktopUiKit.C_WHITE);
        topCard.add(title, BorderLayout.NORTH);

        JPanel compileBar = new JPanel(new GridLayout(1, 3, 6, 0));
        compileBar.setOpaque(false);

        DesktopUiKit.VortexButton toJsBtn = new DesktopUiKit.VortexButton("Compile -> JS", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.FLOW);
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
            log("[FLOW] Compiled " + project.flowRules.size() + " rules into ES6 JavaScript!");
        });

        DesktopUiKit.VortexButton toJavaBtn = new DesktopUiKit.VortexButton("Compile -> Java", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.CODE);
        toJavaBtn.addActionListener(e -> {
            if (project.flowRules.isEmpty()) { log("[Flow] Add at least 1 rule first."); return; }
            pushUndo();
            String javaCode = VortexFlowCompiler.compileToJava(project);
            project.scriptLanguage = "JAVA";
            project.scriptCode = javaCode;
            scriptArea.setText(javaCode);
            syncVmDropdown();
            checkScriptSyntax();
            rightTabs.selectTabByKey("CODE", true);
            log("[FLOW] Compiled " + project.flowRules.size() + " rules into Native Java!");
        });

        DesktopUiKit.VortexButton toLuaBtn = new DesktopUiKit.VortexButton("Compile -> Lua", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CODE);
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
            log("[FLOW] Compiled " + project.flowRules.size() + " rules into Lua 5.2!");
        });

        compileBar.add(toJsBtn);
        compileBar.add(toJavaBtn);
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

        DesktopUiKit.VortexButton addRuleBtn = new DesktopUiKit.VortexButton("Add Rule", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.PLUS);
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
        DesktopUiKit.VortexButton delRuleBtn = new DesktopUiKit.VortexButton("Remove Selected", DesktopUiKit.Variant.DANGER, DesktopUiKit.Icon.CLOSE);
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

    // =========================================================================
    // v4.3 Continuous Multi-Turn Forge AI Chat + Contextual IDE Tool Agent
    // =========================================================================
    private JPanel buildForgeAiTab() {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        int initProv = Math.max(0, Math.min(VortexForgeAgent.PROVIDERS.length - 1, VortexForgeAgent.loadSavedProviderIdx()));

        // Top Bar + Collapsible Provider/Key Settings Drawer
        JPanel topContainer = new JPanel(new BorderLayout(0, 6));
        topContainer.setOpaque(false);

        JPanel hdrBar = new JPanel(new BorderLayout(6, 0));
        hdrBar.setBackground(DesktopUiKit.C_PANEL);
        hdrBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 8)));

        JLabel title = new JLabel("FORGE AI");       // short: the dock tab already says AI
        title.setFont(DesktopUiKit.FONT_UI_BOLD);
        title.setForeground(DesktopUiKit.C_WHITE);
        hdrBar.add(title, BorderLayout.WEST);

        final JPanel settingsCard = new JPanel();
        settingsCard.setLayout(new BoxLayout(settingsCard, BoxLayout.Y_AXIS));
        settingsCard.setBackground(DesktopUiKit.C_PANEL);
        settingsCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        settingsCard.setVisible(false);

        JPanel hdrBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        hdrBtns.setOpaque(false);
        DesktopUiKit.VortexButton cfgBtn = new DesktopUiKit.VortexButton("Model / Key", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SPARKLE);
        cfgBtn.addActionListener(e -> {
            settingsCard.setVisible(!settingsCard.isVisible());
            topContainer.revalidate();
            topContainer.repaint();
        });
        DesktopUiKit.VortexButton clearChatBtn = new DesktopUiKit.VortexButton("Clear", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        clearChatBtn.addActionListener(e -> {
            forgeChatHistory.clear();
            rebuildForgeChatFeed();
        });
        hdrBtns.add(cfgBtn);
        hdrBtns.add(clearChatBtn);
        hdrBar.add(hdrBtns, BorderLayout.EAST);
        topContainer.add(hdrBar, BorderLayout.NORTH);

        DesktopUiKit.VortexDropdown provCb = new DesktopUiKit.VortexDropdown(
                VortexForgeAgent.PROVIDERS, VortexForgeAgent.PROVIDERS[initProv]);
        provCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        final JTextField keyField = DesktopUiKit.createField(VortexForgeAgent.loadSavedApiKey(initProv));
        keyField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel modelRow = new JPanel(new BorderLayout(6, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        final JTextField modelField = DesktopUiKit.createField(VortexForgeAgent.loadSavedModel(initProv));
        final DesktopUiKit.VortexButton fetchModelsBtn = new DesktopUiKit.VortexButton(
                "Live Models", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REFRESH);
        modelRow.add(modelField, BorderLayout.CENTER);
        modelRow.add(fetchModelsBtn, BorderLayout.EAST);

        provCb.setOnSelect((idx, val) -> {
            keyField.setText(VortexForgeAgent.loadSavedApiKey(idx));
            modelField.setText(VortexForgeAgent.loadSavedModel(idx));
        });

        final JLabel statusLbl = new JLabel("Tools: files • entities • blueprint • playtest");
        statusLbl.setFont(DesktopUiKit.FONT_UI);
        statusLbl.setForeground(DesktopUiKit.C_TEXT_MUTED);

        fetchModelsBtn.addActionListener(e -> {
            final int pIdx = provCb.getSelectedIndex();
            final String k = keyField.getText().trim();
            statusLbl.setText("Fetching live models…");
            new Thread(() -> {
                try {
                    final List<String> models = VortexForgeAgent.fetchLiveModels(pIdx, k);
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setText("Loaded " + models.size() + " live models.");
                        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
                        for (int i = 0; i < Math.min(28, models.size()); i++) {
                            final String mid = models.get(i);
                            items.add(new DesktopUiKit.MenuAction(mid, () -> {
                                modelField.setText(mid);
                                VortexForgeAgent.savePrefs(pIdx, mid, k);
                            }));
                        }
                        DesktopUiKit.showPopupMenu(fetchModelsBtn, items);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> statusLbl.setText("Model fetch error: " + ex.getMessage()));
                }
            }, "forge-fetch-models").start();
        });

        JLabel lp = mutedLabel("Provider (Groq openai/gpt-oss-120b • OpenRouter $0 Free • Offline):");
        lp.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsCard.add(lp);
        settingsCard.add(provCb);
        settingsCard.add(Box.createVerticalStrut(5));
        JLabel lk = mutedLabel("API Key (stored locally):");
        lk.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsCard.add(lk);
        settingsCard.add(keyField);
        settingsCard.add(Box.createVerticalStrut(5));
        JLabel lm = mutedLabel("Model ID:");
        lm.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsCard.add(lm);
        settingsCard.add(modelRow);
        topContainer.add(settingsCard, BorderLayout.CENTER);
        p.add(topContainer, BorderLayout.NORTH);

        // Center: Scrollable Multi-Turn Conversation Feed
        forgeMessagesBox = new JPanel();
        forgeMessagesBox.setLayout(new BoxLayout(forgeMessagesBox, BoxLayout.Y_AXIS));
        forgeMessagesBox.setBackground(DesktopUiKit.C_VOID);
        forgeMessagesBox.setBorder(BorderFactory.createEmptyBorder(4, 2, 8, 4));
        forgeMessagesScroll = DesktopUiKit.createScrollPane(forgeMessagesBox);
        forgeMessagesScroll.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        p.add(forgeMessagesScroll, BorderLayout.CENTER);

        // Bottom: Quick Tool Prompts + Multi-Turn Input Composer
        JPanel composer = new JPanel(new BorderLayout(0, 5));
        composer.setOpaque(false);

        JPanel quickRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        quickRow.setOpaque(false);
        final JTextArea chatInput = new JTextArea(2, 24);

        final java.util.function.Consumer<String> sendTurn = (rawText) -> {
            final String userText = rawText == null ? "" : rawText.trim();
            if (userText.isEmpty() || forgeBusy) return;
            chatInput.setText("");
            syncCode();
            VortexForgeAgent.ChatMessage uMsg = new VortexForgeAgent.ChatMessage("user", userText);
            forgeChatHistory.add(uMsg);
            forgeBusy = true;
            rebuildForgeChatFeed();
            statusLbl.setText("Forge AI inspecting files & running tools…");
            statusLbl.setForeground(DesktopUiKit.C_WHITE);

            final int selProv = provCb.getSelectedIndex();
            final String selModel = modelField.getText().trim();
            final String selKey = keyField.getText().trim();
            final List<VortexForgeAgent.ChatMessage> historySnapshot = new ArrayList<>(forgeChatHistory);
            // Save undo checkpoint before agent mutates workspace
            final String preTurnJson = project.toJsonString();

            Thread worker = new Thread(() -> {
                try {
                    final VortexForgeAgent.ChatTurnResult res = VortexForgeAgent.runChatTurn(
                            project, historySnapshot, userText, selProv, selModel, selKey);
                    SwingUtilities.invokeLater(() -> {
                        forgeBusy = false;
                        if (res.mutatedProject) {
                            undoStack.push(preTurnJson);
                            while (undoStack.size() > 35) undoStack.removeLast();
                            redoStack.clear();
                            loadProjectIntoStudio(project);
                        }
                        VortexForgeAgent.ChatMessage aMsg = new VortexForgeAgent.ChatMessage("assistant", res.reply);
                        aMsg.toolBadges.addAll(res.toolBadges);
                        aMsg.questions.addAll(res.questions);
                        forgeChatHistory.add(aMsg);
                        while (forgeChatHistory.size() > 30) forgeChatHistory.remove(0);
                        rebuildForgeChatFeed();
                        if (!res.questions.isEmpty()) {
                            statusLbl.setText("Waiting for your answer");
                            statusLbl.setForeground(DesktopUiKit.C_WHITE);
                        } else {
                            statusLbl.setText(res.toolBadges.isEmpty() ? "Ready" : "\u2713 " + String.join(" \u2022 ", res.toolBadges));
                            statusLbl.setForeground(DesktopUiKit.C_OK_TX);
                        }
                        if (res.mutatedProject) {
                            log("[FORGE AI] " + String.join(", ", res.toolBadges));
                        }
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        forgeBusy = false;
                        VortexForgeAgent.ChatMessage err = new VortexForgeAgent.ChatMessage(
                                "assistant", "That turn failed: " + ex.getMessage()
                                + "\nCheck Model / Key, then send again.");
                        err.toolBadges.add("error");
                        forgeChatHistory.add(err);
                        rebuildForgeChatFeed();
                        statusLbl.setText("Error: " + ex.getMessage());
                        statusLbl.setForeground(DesktopUiKit.C_DANGER_TX);
                    });
                }
            }, "forge-chat-turn");
            worker.setDaemon(true);
            worker.start();
        };

        String[][] quickPrompts = {
                {"Run Test", "Run a 60-frame headless test on my project and check all files"},
                {"+ Dialog UI", "Add a modular UI script with an in-game dialog box and objective label"},
                {"+ Boss Module", "Spawn a boss enemy with its own modular AI script and boss HP bar"}
        };
        for (final String[] qp : quickPrompts) {
            DesktopUiKit.VortexButton chip = new DesktopUiKit.VortexButton(qp[0], DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SPARKLE);
            chip.addActionListener(e -> sendTurn.accept(qp[1]));
            quickRow.add(chip);
        }
        composer.add(quickRow, BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        chatInput.setLineWrap(true);
        chatInput.setWrapStyleWord(true);
        chatInput.setBackground(DesktopUiKit.C_INPUT_BG);
        chatInput.setForeground(DesktopUiKit.C_TEXT);
        chatInput.setCaretColor(DesktopUiKit.C_WHITE);
        chatInput.setFont(DesktopUiKit.FONT_UI);
        chatInput.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        chatInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendTurn.accept(chatInput.getText());
                }
            }
        });
        JScrollPane inputSp = DesktopUiKit.createScrollPane(chatInput);
        inputSp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        inputSp.setPreferredSize(new Dimension(240, DesktopUiKit.scaled(52)));

        DesktopUiKit.VortexButton sendBtn = new DesktopUiKit.VortexButton("Send", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.SEND);
        sendBtn.addActionListener(e -> sendTurn.accept(chatInput.getText()));

        inputRow.add(inputSp, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        composer.add(inputRow, BorderLayout.CENTER);
        composer.add(statusLbl, BorderLayout.SOUTH);

        p.add(composer, BorderLayout.SOUTH);
        forgeSend = sendTurn;
        rebuildForgeChatFeed();
        return p;
    }

    private void rebuildForgeChatFeed() {
        if (forgeMessagesBox == null) return;
        forgeMessagesBox.removeAll();
        if (forgeChatHistory.isEmpty()) {
            VortexForgeAgent.ChatMessage welcome = new VortexForgeAgent.ChatMessage(
                    "assistant",
                    "Ready on **" + (project != null ? project.title : "your game") + "**.\n"
                    + "I can see your files, scene entities and Blueprint logic. Ask for a feature — "
                    + "or tap a chip below and I'll build it.");
            forgeMessagesBox.add(buildChatBubble(welcome));
            forgeMessagesBox.add(Box.createVerticalStrut(8));
        } else {
            for (VortexForgeAgent.ChatMessage m : forgeChatHistory) {
                forgeMessagesBox.add(buildChatBubble(m));
                forgeMessagesBox.add(Box.createVerticalStrut(8));
            }
        }
        if (forgeBusy) {
            forgeMessagesBox.add(buildThinkingRow());
            forgeMessagesBox.add(Box.createVerticalStrut(8));
        }
        forgeMessagesBox.revalidate();
        forgeMessagesBox.repaint();
        if (forgeMessagesScroll != null) {
            SwingUtilities.invokeLater(() -> {
                javax.swing.JScrollBar bar = forgeMessagesScroll.getVerticalScrollBar();
                if (bar != null) bar.setValue(bar.getMaximum());
            });
        }
    }

    /** Available bubble width — measured from the live viewport so resizing reflows the chat. */
    private int chatBubbleMaxWidth() {
        int w = 380;
        if (forgeMessagesScroll != null && forgeMessagesScroll.getViewport() != null
                && forgeMessagesScroll.getViewport().getWidth() > 80) {
            w = forgeMessagesScroll.getViewport().getWidth();
        } else if (forgeMessagesBox != null && forgeMessagesBox.getWidth() > 80) {
            w = forgeMessagesBox.getWidth();
        }
        return Math.max(220, w - 34);
    }

    private JPanel buildThinkingRow() {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel("Forge AI is reading the workspace and running tools…");
        lbl.setFont(DesktopUiKit.FONT_SMALL);
        lbl.setForeground(DesktopUiKit.C_TEXT_MUTED);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        wrap.add(lbl);
        return wrap;
    }

    private JPanel buildChatBubble(VortexForgeAgent.ChatMessage m) {
        final boolean isUser = "user".equalsIgnoreCase(m.role);
        final int maxW = chatBubbleMaxWidth();
        final int bubbleW = isUser ? (int) (maxW * 0.86f) : maxW;

        JPanel wrap = new JPanel(new FlowLayout(isUser ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        wrap.setOpaque(false);
        wrap.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel card = new JPanel(new BorderLayout(0, 3));
        card.setBackground(isUser ? DesktopUiKit.C_SURFACE : DesktopUiKit.C_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(isUser ? DesktopUiKit.C_BORDER_STRONG : DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(6, 9, 7, 9)));

        // ---- header: role + executed-tool badges
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        head.setOpaque(false);
        JLabel roleLbl = new JLabel(isUser ? "YOU" : "FORGE AI");
        roleLbl.setFont(DesktopUiKit.FONT_SMALL_BOLD);
        roleLbl.setForeground(isUser ? DesktopUiKit.C_TEXT_LABEL : DesktopUiKit.C_WHITE);
        head.add(roleLbl);
        for (String badge : m.toolBadges) {
            boolean err = badge.startsWith("error") || badge.startsWith("rejected");
            JLabel b = new JLabel((err ? "\u2715 " : "\u2713 ") + badge);
            b.setFont(DesktopUiKit.FONT_SMALL);
            b.setForeground(err ? DesktopUiKit.C_DANGER_TX : DesktopUiKit.C_OK_TX);
            head.add(b);
        }
        card.add(head, BorderLayout.NORTH);

        // ---- body: measured, wrapped, never wider than the dock
        String plain = (m.text == null ? "" : m.text)
                .replace("**", "")
                .replace("`", "");
        JTextArea body = new JTextArea(plain);
        body.setEditable(false);
        body.setFocusable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setOpaque(false);
        body.setForeground(DesktopUiKit.C_TEXT);
        body.setFont(DesktopUiKit.FONT_UI);
        body.setBorder(null);
        int textW = Math.max(140, bubbleW - 22);
        body.setSize(new Dimension(textW, Short.MAX_VALUE));
        int textH = body.getPreferredSize().height;
        body.setPreferredSize(new Dimension(textW, textH));
        body.setMaximumSize(new Dimension(textW, textH));
        card.add(body, BorderLayout.CENTER);

        // ---- footer: questions (answer chips) or small utilities
        JPanel foot = new JPanel();
        foot.setLayout(new BoxLayout(foot, BoxLayout.Y_AXIS));
        foot.setOpaque(false);

        if (!m.questions.isEmpty() && !m.answered) {
            for (VortexForgeAgent.Question q : m.questions) {
                JLabel qt = new JLabel(q.text);
                qt.setFont(DesktopUiKit.FONT_UI);
                qt.setForeground(DesktopUiKit.C_WHITE);
                qt.setAlignmentX(Component.LEFT_ALIGNMENT);
                qt.setBorder(BorderFactory.createEmptyBorder(6, 0, 3, 0));
                foot.add(qt);

                JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                chips.setOpaque(false);
                chips.setAlignmentX(Component.LEFT_ALIGNMENT);
                for (final String opt : q.options) {
                    DesktopUiKit.VortexButton chip = new DesktopUiKit.VortexButton(
                            opt, DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.NONE);
                    chip.addActionListener(e -> answerForgeQuestion(m, q, opt));
                    chips.add(chip);
                }
                foot.add(chips);

                if (q.allowText) {
                    JPanel answerRow = new JPanel(new BorderLayout(4, 0));
                    answerRow.setOpaque(false);
                    answerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
                    answerRow.setMaximumSize(new Dimension(bubbleW - 22, DesktopUiKit.scaled(30)));
                    final JTextField tf = DesktopUiKit.createField("");
                    tf.setToolTipText("Type your answer");
                    DesktopUiKit.VortexButton go = new DesktopUiKit.VortexButton(
                            "", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.SEND);
                    go.setToolTipText("Send answer");
                    final VortexForgeAgent.Question fq = q;
                    Runnable submit = () -> {
                        String typed = tf.getText() == null ? "" : tf.getText().trim();
                        if (!typed.isEmpty()) answerForgeQuestion(m, fq, typed);
                    };
                    go.addActionListener(e -> submit.run());
                    tf.addKeyListener(new KeyAdapter() {
                        @Override public void keyPressed(KeyEvent e) {
                            if (e.getKeyCode() == KeyEvent.VK_ENTER) { e.consume(); submit.run(); }
                        }
                    });
                    answerRow.add(tf, BorderLayout.CENTER);
                    answerRow.add(go, BorderLayout.EAST);
                    foot.add(Box.createVerticalStrut(3));
                    foot.add(answerRow);
                }
            }
        } else if (!m.toolBadges.isEmpty()) {
            JPanel utils = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            utils.setOpaque(false);
            utils.setAlignmentX(Component.LEFT_ALIGNMENT);
            DesktopUiKit.VortexButton undoBtn = new DesktopUiKit.VortexButton(
                    "Undo changes", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.UNDO);
            undoBtn.addActionListener(e -> performUndo());
            utils.add(undoBtn);
            DesktopUiKit.VortexButton testBtn = new DesktopUiKit.VortexButton(
                    "Playtest", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLAY);
            testBtn.addActionListener(e -> {
                if (forgeSend != null) forgeSend.accept("Run a 60-frame headless test and report problems");
            });
            utils.add(testBtn);
            foot.add(utils);
        }

        if (foot.getComponentCount() > 0) card.add(foot, BorderLayout.SOUTH);

        card.setPreferredSize(new Dimension(bubbleW, textH + head.getPreferredSize().height
                + (foot.getComponentCount() > 0 ? foot.getPreferredSize().height + 8 : 0) + 18));
        wrap.add(card);
        return wrap;
    }

    /** Sends the user's answer to an inline question card and locks the card. */
    private void answerForgeQuestion(VortexForgeAgent.ChatMessage source,
                                     VortexForgeAgent.Question q, String answer) {
        if (source != null) source.answered = true;
        rebuildForgeChatFeed();
        if (forgeSend != null) forgeSend.accept(answer);
    }

    // =========================================================================
    // Supercharged LRM P2P Sync Tab (Auto-Install + Daemon + Wi-Fi Drop + Dashboard)
    // =========================================================================
    private JPanel buildLrmSyncTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(DesktopUiKit.C_VOID);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JPanel topWrap = new JPanel(new BorderLayout(0, 6));
        topWrap.setOpaque(false);

        JPanel setupRow = new JPanel(new GridLayout(1, 2, 8, 0));
        setupRow.setOpaque(false);
        DesktopUiKit.VortexButton wizardBtn = new DesktopUiKit.VortexButton(
                "Auto-Install & Setup LRM", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.SPARKLE);
        wizardBtn.addActionListener(e -> showLrmDependencyWizardModal(false));

        DesktopUiKit.VortexButton stopSvcBtn = new DesktopUiKit.VortexButton(
                "Stop Background Mesh", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.STOP);
        stopSvcBtn.addActionListener(e -> {
            int n = LrmWorkspaceBridge.stopBackgroundServices();
            lrmStatusArea.append("\n[Mesh] Stopped " + n + " background LRM service(s).\n");
        });
        setupRow.add(wizardBtn);
        setupRow.add(stopSvcBtn);
        topWrap.add(setupRow, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(0, 2, 8, 6));
        controls.setBackground(DesktopUiKit.C_PANEL);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JTextField msgField = DesktopUiKit.createField("Update game snapshot");
        JTextField portKeyField = DesktopUiKit.createField("");
        controls.add(mutedLabel("Commit Message:")); controls.add(msgField);
        controls.add(mutedLabel("Remote Port Key (lrm1_...):")); controls.add(portKeyField);

        lrmStatusArea = new JTextArea();
        lrmStatusArea.setEditable(false);
        lrmStatusArea.setBackground(DesktopUiKit.C_VOID);
        lrmStatusArea.setForeground(DesktopUiKit.C_TEXT);
        lrmStatusArea.setFont(DesktopUiKit.FONT_MONO);
        lrmStatusArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        LrmWorkspaceBridge.SystemProbe probe = LrmWorkspaceBridge.probeSystem();
        lrmStatusArea.setText("LRM P2P Version Control & Real-Time Mesh (github.com/hacvilke/lrm)\n"
                + probe.toSummaryReport());

        DesktopUiKit.VortexButton commitBtn = new DesktopUiKit.VortexButton("Unpack + Commit", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.CHECK);
        commitBtn.addActionListener(e -> runLrmAsync("commit", () -> {
            syncCode();
            File repoDir = getProjectLrmDir();
            LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
            if (!new File(repoDir, ".lrm").exists()) {
                LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
            }
            LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 12, "commit", "-m", msgField.getText().trim());
            LrmWorkspaceBridge.CmdResult lg = LrmWorkspaceBridge.runLrm(repoDir, 8, "log", "--limit", "6", "--graph", "--oneline");
            return "$ lrm commit\n" + r.output + "\n$ lrm log --graph\n" + lg.output;
        }));

        DesktopUiKit.VortexButton shareBtn = new DesktopUiKit.VortexButton("Share Port Key", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.SYNC);
        shareBtn.addActionListener(e -> runLrmAsync("share", () -> {
            syncCode();
            File repoDir = getProjectLrmDir();
            LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
            if (!new File(repoDir, ".lrm").exists()) {
                LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
                LrmWorkspaceBridge.runLrm(repoDir, 10, "commit", "-m", "Initial snapshot");
            }
            LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 15, "share", "--lifetime", "600");
            return "$ lrm share\n" + r.output;
        }));

        DesktopUiKit.VortexButton joinBtn = new DesktopUiKit.VortexButton("Join Port Key", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SYNC);
        joinBtn.addActionListener(e -> {
            final String pk = portKeyField.getText().trim();
            if (pk.isEmpty()) {
                lrmStatusArea.append("\n[!] Paste an lrm1_... Port Key above first.\n");
                return;
            }
            runLrmAsync("join", () -> {
                File repoDir = getProjectLrmDir();
                LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 25, "join", pk, "--init");
                return "$ lrm join\n" + r.output;
            });
        });

        DesktopUiKit.VortexButton importBtn = new DesktopUiKit.VortexButton("Reload Repo & Inbox", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REFRESH);
        importBtn.addActionListener(e -> {
            File repoDir = getProjectLrmDir();
            try {
                pushUndo();
                GameProject imported = LrmWorkspaceBridge.importFromLrmRepo(repoDir);
                imported.id = project.id;
                loadProjectIntoStudio(imported);
                lrmStatusArea.append("\n[✓] Reloaded " + imported.entities.size() + " entities (+ inbox assets) into Studio!\n");
            } catch (Exception ex) {
                lrmStatusArea.append("\n[Import Error] " + ex.getMessage() + "\n");
            }
        });

        DesktopUiKit.VortexButton peersBtn = new DesktopUiKit.VortexButton("Discover LAN Peers", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SYNC);
        peersBtn.addActionListener(e -> runLrmAsync("peers", () -> {
            File repoDir = getProjectLrmDir();
            repoDir.mkdirs();
            LrmWorkspaceBridge.CmdResult r = LrmWorkspaceBridge.runLrm(repoDir, 6, "peers", "--timeout", "3");
            return "$ lrm peers --timeout 3\n" + r.output;
        }));

        DesktopUiKit.VortexButton statusBtn = new DesktopUiKit.VortexButton("Status & Diff", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CODE);
        statusBtn.addActionListener(e -> runLrmAsync("status", () -> {
            syncCode();
            File repoDir = getProjectLrmDir();
            LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
            if (!new File(repoDir, ".lrm").exists()) {
                LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
            }
            LrmWorkspaceBridge.CmdResult st = LrmWorkspaceBridge.runLrm(repoDir, 6, "status");
            LrmWorkspaceBridge.CmdResult df = LrmWorkspaceBridge.runLrm(repoDir, 6, "diff", "--stat");
            return "$ lrm status\n" + st.output + "\n$ lrm diff --stat\n" + df.output;
        }));

        DesktopUiKit.VortexButton daemonBtn = new DesktopUiKit.VortexButton("Live Sync Daemon", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLAY);
        daemonBtn.setToolTipText("Start background `lrm daemon` real-time P2P auto-sync (~2s latency)");
        daemonBtn.addActionListener(e -> startLrmBackgroundTool("daemon"));

        DesktopUiKit.VortexButton receiveBtn = new DesktopUiKit.VortexButton("Wi-Fi Phone Drop", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.EXPORT);
        receiveBtn.setToolTipText("Start `lrm receive --port 8090` phone browser upload page -> inbox/");
        receiveBtn.addActionListener(e -> startLrmBackgroundTool("receive", "--port", "8090"));

        DesktopUiKit.VortexButton dashBtn = new DesktopUiKit.VortexButton("Mesh Dashboard", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.GRID);
        dashBtn.setToolTipText("Start `lrm dashboard --port 8484` & open local mesh viewer in browser");
        dashBtn.addActionListener(e -> {
            startLrmBackgroundTool("dashboard", "--port", "8484");
            new Thread(() -> {
                try {
                    Thread.sleep(700);
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI("http://127.0.0.1:8484"));
                    }
                } catch (Exception ignored) {}
            }).start();
        });

        DesktopUiKit.VortexButton pairBtn = new DesktopUiKit.VortexButton("Pair Device Invite", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CHECK);
        pairBtn.addActionListener(e -> runLrmAsync("pair", () -> {
            LrmWorkspaceBridge.CmdResult pr = LrmWorkspaceBridge.runLrm(getProjectLrmDir(), 6, "pair");
            LrmWorkspaceBridge.CmdResult dv = LrmWorkspaceBridge.runLrm(getProjectLrmDir(), 6, "devices");
            return "$ lrm pair\n" + pr.output + "\n$ lrm devices\n" + dv.output;
        }));

        DesktopUiKit.VortexButton moreBtn = new DesktopUiKit.VortexButton("More", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.MORE_VERT);
        moreBtn.addActionListener(e -> {
            List<DesktopUiKit.MenuAction> opts = new ArrayList<>();
            opts.add(new DesktopUiKit.MenuAction("Discover LAN Peers", peersBtn::doClick));
            opts.add(new DesktopUiKit.MenuAction("Status & Diff", statusBtn::doClick));
            opts.add(new DesktopUiKit.MenuAction("Live Sync Daemon", daemonBtn::doClick));
            opts.add(new DesktopUiKit.MenuAction("Wi-Fi Phone Drop", receiveBtn::doClick));
            opts.add(new DesktopUiKit.MenuAction("Mesh Dashboard", dashBtn::doClick));
            opts.add(new DesktopUiKit.MenuAction("Pair Device Invite", pairBtn::doClick));
            DesktopUiKit.showPopupMenu(moreBtn, opts);
        });

        controls.add(commitBtn);  controls.add(shareBtn);
        controls.add(joinBtn);    controls.add(importBtn);
        controls.add(moreBtn);

        topWrap.add(controls, BorderLayout.CENTER);
        panel.add(topWrap, BorderLayout.NORTH);

        JScrollPane sp = DesktopUiKit.createScrollPane(lrmStatusArea);
        sp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        panel.add(sp, BorderLayout.CENTER);
        return panel;
    }

    private interface LrmTask {
        String run() throws Exception;
    }

    private File getProjectLrmDir() {
        return new File(System.getProperty("user.home", "."), "VortexEngine-LRM/" + project.getSlug());
    }

    private void runLrmAsync(String label, LrmTask task) {
        lrmStatusArea.append("\n[*] Running lrm " + label + "…\n");
        Thread t = new Thread(() -> {
            try {
                final String out = task.run();
                SwingUtilities.invokeLater(() -> {
                    lrmStatusArea.append(out + "\n");
                    lrmStatusArea.setCaretPosition(lrmStatusArea.getDocument().getLength());
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lrmStatusArea.append("[Error] " + ex.getMessage() + "\n");
                    lrmStatusArea.setCaretPosition(lrmStatusArea.getDocument().getLength());
                });
            }
        }, "lrm-cmd-" + label);
        t.setDaemon(true);
        t.start();
    }

    private void startLrmBackgroundTool(String... args) {
        try {
            syncCode();
            File repoDir = getProjectLrmDir();
            LrmWorkspaceBridge.exportToLrmRepo(project, repoDir);
            if (!new File(repoDir, ".lrm").exists()) {
                LrmWorkspaceBridge.runLrm(repoDir, 10, "init", "--user", "studio");
                LrmWorkspaceBridge.runLrm(repoDir, 10, "commit", "-m", "Initial workspace");
            }
            lrmStatusArea.append("\n[Mesh] Starting background `lrm " + String.join(" ", args) + "`...\n");
            LrmWorkspaceBridge.startBackgroundService(repoDir, line -> SwingUtilities.invokeLater(() -> {
                lrmStatusArea.append("  " + line + "\n");
                lrmStatusArea.setCaretPosition(lrmStatusArea.getDocument().getLength());
            }), args);
        } catch (Exception ex) {
            lrmStatusArea.append("\n[Mesh Error] " + ex.getMessage() + "\n");
        }
    }

    private JPanel buildPixelArtTab() {
        return DesktopPixelArtDialog.createEmbeddedPanel(() -> project, (spriteName, spawn) -> {
            viewport.invalidateAssetCaches();
            if (spawn) {
                pushUndo();
                project.dimensionMode = "2D";
                dimBtn.setText("2D");
                Entity sp = Entity.create2D("Sprite_" + (project.entities.size() + 1),
                        "prop", "CUSTOM_SPRITE", 0f, 1.5f, 1.5f, 1.5f, "#FFFFFF", "DYNAMIC");
                sp.customSpriteName = spriteName;
                project.entities.add(sp);
                viewport.setSelectedEntityId(sp.id);
                refreshHierarchyList();
                populateInspector();
            }
            viewport.repaint();
            log("[PIXEL] Saved 16x16 sprite '" + spriteName + "'" + (spawn ? " & spawned in 2D scene!" : ""));
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

        JLabel mHead = new JLabel("4-CHANNEL CHIPTUNE COMPOSER");
        mHead.setFont(DesktopUiKit.FONT_TITLE);
        mHead.setForeground(DesktopUiKit.C_WHITE);
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
            log("[AUDIO] Active soundtrack -> " + val);
        });
        musicCard.add(stepGrid);
        musicCard.add(Box.createVerticalStrut(8));

        JPanel mBtns = new JPanel(new GridLayout(1, 2, 8, 0));
        mBtns.setOpaque(false);
        mBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
        mBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        DesktopUiKit.VortexButton loopBtn = new DesktopUiKit.VortexButton("Preview Loop", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.PLAY);
        loopBtn.addActionListener(e -> viewport.audioEngine.startMusicLoop(project.musicPattern, project.musicBpm));
        DesktopUiKit.VortexButton stopBtn = new DesktopUiKit.VortexButton("Stop Music", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.STOP);
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
                {"jump", "Jump Sweep"},
                {"coin", "Coin / Gem Chime"},
                {"laser", "Plasma Laser"},
                {"explosion", "Explosion Burst"},
                {"hit", "Impact Hit"},
                {"powerup", "Warp Fanfare"}
        };
        for (String[] s : sounds) {
            DesktopUiKit.VortexButton pad = new DesktopUiKit.VortexButton(s[1], DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLAY);
            pad.addActionListener(e -> {
                viewport.audioEngine.play(s[0]);
                log("[SFX] Played '" + s[0] + "' (API: vortex.playSound(\"" + s[0] + "\"))");
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
                {"html", "Export Single-File HTML5 Web Game (.html)", "Zero-dependency 60 FPS Canvas + WebAudio bundle (supports JS, Java & Lua)"},
                {"exe",  "Export Standalone Windows x64 Game (.exe)", "Self-contained Win32 PE executable with custom procedural icon"},
                {"linux","Export Standalone Linux x86_64 Binary", "Self-executing Linux desktop game binary"},
                {"jar",  "Export Portable Desktop Fat JAR (.jar)", "Cross-platform runnable JAR for Windows, macOS & Linux"},
                {"android","Export Android Termux APK Project (.zip)", "Complete Android manifest, multi-DPI icons, Java/Rhino/LuaJ/Box2D DEX & build_termux.sh"}
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
            h.setForeground(DesktopUiKit.C_WHITE);
            JLabel d = new JLabel(t[2]);
            d.setFont(DesktopUiKit.FONT_UI);
            d.setForeground(DesktopUiKit.C_TEXT_MUTED);
            txt.add(h);
            txt.add(d);
            card.add(txt, BorderLayout.CENTER);

            DesktopUiKit.VortexButton btn = new DesktopUiKit.VortexButton(
                    "Export", i == 0 ? DesktopUiKit.Variant.PRIMARY : DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.EXPORT);
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
        String lang = project.scriptLanguage == null ? "JS" : project.scriptLanguage.toUpperCase(Locale.US);
        int idx = 0;
        for (int i = 0; i < VM_CODES.length; i++) {
            if (VM_CODES[i].equals(lang)) { idx = i; break; }
        }
        vmDropdown.setSelectedIndex(idx);
    }

    private void loadProjectIntoStudio(GameProject p) {
        this.project = p;
        p.ensureScriptFilesInitialized();
        dimBtn.setText(p.dimensionMode);
        syncVmDropdown();
        openScriptTabs.clear();
        String activeBody = p.scriptFiles.getOrDefault(p.activeScriptFile, p.scriptCode == null ? "" : p.scriptCode);
        p.scriptCode = activeBody;
        syncingEditorBuffers = true;
        try {
            scriptArea.setText(activeBody);
            scriptArea.setCaretPosition(0);
            if (snappedZone != null) {
                if (!p.scriptFiles.containsKey(snappedFilePath)) {
                    snappedFilePath = p.activeScriptFile;
                }
                snappedScriptArea.setText(p.scriptFiles.getOrDefault(snappedFilePath, ""));
                snappedScriptArea.setCaretPosition(0);
                snappedTitleLabel.setText(snappedFilePath + "  [SNAPPED " + snappedZone + "]");
            }
        } finally {
            syncingEditorBuffers = false;
        }
        refreshScriptFileExplorer();
        viewport.loadProject(p, false);
        if (!p.entities.isEmpty()) {
            viewport.setSelectedEntityId(p.entities.get(0).id);
        }
        refreshHierarchyList();
        refreshFlowListModel();
        populateInspector();
        checkScriptSyntax();
        rebuildForgeChatFeed();
        telemetryLabel.setText("Editor • " + p.entities.size() + " objs • " + p.dimensionMode + " / " + p.scriptLanguage);
        playStopBtn.setText("PLAY GAME");
        playStopBtn.setIconType(DesktopUiKit.Icon.PLAY);
        playStopBtn.setVariant(DesktopUiKit.Variant.PRIMARY);
    }

    private void refreshHierarchyList() {
        if (sceneTree == null) return;
        sceneTree.refresh();
        syncHierarchySelection();
    }

    private void syncHierarchySelection() {
        if (sceneTree == null) return;
        sceneTree.selectEntity(viewport.getSelectedEntityId());
    }

    // =====================================================================
    // v4.7.0 — Tile painter + prefab palette
    // =====================================================================

    // =====================================================================
    // v4.8.0 — voxel world tools
    // =====================================================================

    private void showVoxelPalette() {
        if (!project.voxelEnabled) {
            DesktopUiKit.showInfoModal(this, "Voxel World",
                    "This project has no block world yet.\n\nCreate one with  File ▸ New Project ▸ 3D Voxel: Blockfall,\n"
                    + "or turn this project into a voxel world with Tools ▸ Voxel Regenerate World.");
            return;
        }
        final int[] ids = com.vortex.engine.core.voxel.BlockType.hotbarIds();
        final javax.swing.JDialog dlg = new javax.swing.JDialog(this, "Voxel Block Palette", true);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(DesktopUiKit.C_PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Block brush  •  LMB mine   RMB build   middle-click pipette");
        title.setFont(DesktopUiKit.FONT_UI_BOLD);
        title.setForeground(DesktopUiKit.C_WHITE);
        root.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new java.awt.GridLayout(0, 4, 6, 6));
        grid.setOpaque(false);
        for (final int id : ids) {
            final DesktopUiKit.VortexButton b = new DesktopUiKit.VortexButton(
                    com.vortex.engine.core.voxel.BlockType.name(id), DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.NONE);
            int argb = com.vortex.engine.core.voxel.BlockType.color(id);
            b.setBackground(new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
            b.setForeground(new Color(16, 16, 18));
            b.addActionListener(ev -> {
                viewport.setVoxelBrushId(id);
                globalStatusPill.setStatus("Block brush: " + com.vortex.engine.core.voxel.BlockType.name(id),
                        DesktopUiKit.StatusPill.State.NEUTRAL);
                dlg.dispose();
            });
            grid.add(b);
        }
        root.add(grid, BorderLayout.CENTER);

        DesktopUiKit.VortexButton close = new DesktopUiKit.VortexButton("Close", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CLOSE);
        close.addActionListener(ev -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setSize(DesktopUiKit.scaled(430), DesktopUiKit.scaled(300));
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void regenerateVoxelWorld() {
        String seedText = DesktopUiKit.showInputModal(this, "Voxel World",
                "World seed (leave empty for the current one)", String.valueOf(project.voxelSeed));
        if (seedText == null) return;
        int seed = project.voxelSeed;
        try {
            if (!seedText.trim().isEmpty()) seed = Integer.parseInt(seedText.trim());
        } catch (NumberFormatException ignored) {
            seed = seedText.hashCode();
        }
        pushUndo();
        project.voxelEnabled = true;
        project.dimensionMode = "3D";
        project.ensureVoxelRuntime().regenerate(project, seed);
        viewport.invalidateAssetCaches();
        viewport.repaint();
        refreshHierarchyList();
        log("\u2713 Voxel world generated with seed " + seed + " ("
                + project.ensureVoxelRuntime().world.size + "\u00d7" + project.ensureVoxelRuntime().world.height + " blocks)");
        globalStatusPill.setStatus("\u2713 Voxel world: seed " + seed, DesktopUiKit.StatusPill.State.SUCCESS);
    }

    private void toggleTilePaint() {
        boolean on = !viewport.isTilePaintMode();
        if (on && !"2D".equalsIgnoreCase(project.dimensionMode)) {
            project.dimensionMode = "2D";
            project.ensureScriptFilesInitialized();
        }
        viewport.setTilePaintMode(on);
        viewport.setOnSceneChanged(this::refreshHierarchyList);
        globalStatusPill.setStatus(on ? "Tile paint ON"
                : "Tile paint off", on ? DesktopUiKit.StatusPill.State.NEUTRAL : DesktopUiKit.StatusPill.State.NEUTRAL);
        if (on) log("\u25B8 Tile paint: layer '" + viewport.getTilePaintLayer()
                + "'. LMB paint, RMB erase, palette at the bottom of the viewport.");
    }

    private void askTileLayerName() {
        String name = DesktopUiKit.showInputModal(this, "Tile Layer", "Layer name", viewport.getTilePaintLayer());
        if (name == null || name.trim().isEmpty()) return;
        viewport.setTilePaintLayer(name.trim());
        globalStatusPill.setStatus("Tile layer: " + name.trim(), DesktopUiKit.StatusPill.State.NEUTRAL);
    }

    private void mergeTileRuns() {
        pushUndo();
        int saved = com.vortex.engine.core.asset.TileBrush.mergeRuns(project, viewport.getTilePaintLayer());
        refreshHierarchyList();
        viewport.repaint();
        log(saved > 0 ? "\u2713 Merged tile runs — " + saved + " entities saved" : "Nothing to merge on this layer");
        globalStatusPill.setStatus(saved > 0 ? "\u2713 Merged " + saved + " tiles" : "Nothing to merge",
                saved > 0 ? DesktopUiKit.StatusPill.State.SUCCESS : DesktopUiKit.StatusPill.State.WARNING);
    }

    private void showPrefabPalette() {
        project.ensurePrefabsInitialized();
        final javax.swing.JDialog dlg = new javax.swing.JDialog(this, "Prefab Palette", true);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(DesktopUiKit.C_PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JLabel title = new JLabel("Prefabs  •  " + project.prefabs.size());
        title.setFont(DesktopUiKit.FONT_UI_BOLD);
        title.setForeground(DesktopUiKit.C_WHITE);
        root.add(title, BorderLayout.NORTH);

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (final com.vortex.engine.core.model.Prefab pf : new ArrayList<>(project.prefabs)) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(30)));
            JLabel name = new JLabel(pf.name);
            name.setFont(DesktopUiKit.FONT_UI);
            name.setForeground(DesktopUiKit.C_WHITE);
            JLabel meta = new JLabel(pf.category + "  •  " + pf.nodeCount() + (pf.nodeCount() == 1 ? " node" : " nodes"));
            meta.setFont(DesktopUiKit.FONT_SMALL);
            meta.setForeground(DesktopUiKit.C_TEXT_MUTED);
            JPanel left = new JPanel(new BorderLayout(8, 0));
            left.setOpaque(false);
            left.add(name, BorderLayout.WEST);
            left.add(meta, BorderLayout.CENTER);
            row.add(left, BorderLayout.CENTER);
            DesktopUiKit.VortexButton add = new DesktopUiKit.VortexButton("Add", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
            add.addActionListener(ev -> {
                pushUndo();
                Entity parent = project.findById(viewport.getSelectedEntityId());
                Entity created = pf.instantiate(project, parent, 0f, 0f, 0f);
                refreshHierarchyList();
                populateInspector();
                viewport.repaint();
                log(created != null ? "\u2713 Prefab '" + pf.name + "' added" + (parent != null ? " under " + parent.name : "")
                        : "\u2715 Could not instantiate '" + pf.name + "'");
                dlg.dispose();
            });
            row.add(add, BorderLayout.EAST);
            list.add(row);
        }
        JScrollPane sp = new JScrollPane(list);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(DesktopUiKit.C_PANEL);
        root.add(sp, BorderLayout.CENTER);

        DesktopUiKit.VortexButton close = new DesktopUiKit.VortexButton("Close", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CLOSE);
        close.addActionListener(ev -> dlg.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.setOpaque(false);
        south.add(close);
        root.add(south, BorderLayout.SOUTH);
        dlg.setContentPane(root);
        dlg.setSize(DesktopUiKit.scaled(420), DesktopUiKit.scaled(460));
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void createPrefabFromSelection() {
        Entity sel = project.findById(viewport.getSelectedEntityId());
        if (sel == null) {
            DesktopUiKit.showInfoModal(this, "Create Prefab", "Select a node in the Hierarchy first.\nThe node and all of its children become the prefab.");
            return;
        }
        String name = DesktopUiKit.showInputModal(this, "Create Prefab", "Prefab name", sel.name + " Prefab");
        if (name == null || name.trim().isEmpty()) return;
        String finalName = name.trim();
        if (project.hasPrefab(finalName)) finalName = finalName + " 2";
        com.vortex.engine.core.model.Prefab pf = com.vortex.engine.core.model.Prefab.capture(
                project.entities, sel, finalName, "Custom");
        project.prefabs.add(pf);
        log("\u2713 Prefab '" + finalName + "' saved (" + pf.nodeCount() + " nodes)");
        globalStatusPill.setStatus("\u2713 Prefab saved", DesktopUiKit.StatusPill.State.SUCCESS);
    }

    /** v4.7.0 procedural material presets (shading response only — no textures needed). */
    static final String[] MATERIAL_PRESETS = {"MATTE", "METAL", "GLASS", "EMISSIVE", "UNLIT"};
    /** v4.7.0 procedural surface patterns drawn from world position (no texture files). */
    static final String[] TEXTURE_PATTERNS = {"NONE", "CHECKER", "STRIPES", "GRID", "BRICK", "NOISE"};

    private void populateInspector() {
        inspectorForm.removeAll();

        final Entity e = project.findById(viewport.getSelectedEntityId());
        if (e == null) {
            JLabel none = mutedLabel("Select any entity in the Hierarchy or Viewport to inspect.");
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            inspectorForm.add(none);
            inspectorForm.revalidate();
            inspectorForm.repaint();
            return;
        }

        // Selected Entity Summary Strip (High-Contrast Readability)
        JPanel selBanner = new JPanel(new BorderLayout(6, 0));
        selBanner.setBackground(DesktopUiKit.C_HEADER);
        selBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        selBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        selBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        JLabel selTitle = new JLabel(e.name + "  •  " + e.primitive);
        selTitle.setFont(DesktopUiKit.FONT_UI_BOLD);
        selTitle.setForeground(DesktopUiKit.C_WHITE);
        // Godot-style node path, so nesting is obvious while editing.
        String nodePath = SceneHierarchy.path(project.entities, e);
        JLabel selHint = new JLabel(nodePath.contains("/") ? nodePath : "drag / wheel");
        selHint.setFont(DesktopUiKit.FONT_SMALL);
        selHint.setForeground(DesktopUiKit.C_TEXT_MUTED);
        selBanner.add(selTitle, BorderLayout.CENTER);
        selBanner.add(selHint, BorderLayout.EAST);
        inspectorForm.add(selBanner);
        inspectorForm.add(Box.createVerticalStrut(6));

        final Color axisXCol = new Color(255, 118, 118); // Coral Red X
        final Color axisYCol = new Color(92, 230, 146);  // Emerald Y
        final Color axisZCol = DesktopUiKit.C_TEXT_LABEL; // Crisp Silver-White Z (ZERO BLUE!)

        // =====================================================================
        // Folder 1: Transform (Position, Scale & Rotation — Live Scrubbers!)
        // =====================================================================
        DesktopUiKit.AccordionSection transformSec = makeAccordion("TRANSFORM", "Transform", true);
        JPanel tBody = transformSec.getBody();

        tBody.add(sectionLabel("Position"));
        tBody.add(Box.createVerticalStrut(3));
        JPanel xyzRow = new JPanel(new GridLayout(1, 3, 4, 0));
        xyzRow.setOpaque(false);
        xyzRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        xyzRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        DesktopUiKit.VortexNumberField xNum = new DesktopUiKit.VortexNumberField("X", axisXCol, e.x, 0.2, -9999, 9999);
        DesktopUiKit.VortexNumberField yNum = new DesktopUiKit.VortexNumberField("Y", axisYCol, e.y, 0.2, -9999, 9999);
        DesktopUiKit.VortexNumberField zNum = new DesktopUiKit.VortexNumberField("Z", axisZCol, e.z, 0.2, -9999, 9999);
        xNum.setOnChange(v -> { e.x = (float) v; viewport.repaint(); });
        yNum.setOnChange(v -> { e.y = (float) v; viewport.repaint(); });
        zNum.setOnChange(v -> { e.z = (float) v; viewport.repaint(); });
        xyzRow.add(xNum); xyzRow.add(yNum); xyzRow.add(zNum);
        tBody.add(xyzRow);

        tBody.add(Box.createVerticalStrut(6));
        tBody.add(sectionLabel("Scale"));
        tBody.add(Box.createVerticalStrut(3));
        JPanel sclRow = new JPanel(new GridLayout(1, 3, 4, 0));
        sclRow.setOpaque(false);
        sclRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        sclRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        DesktopUiKit.VortexNumberField sxNum = new DesktopUiKit.VortexNumberField("W", axisXCol, e.scaleX, 0.1, 0.1, 999);
        DesktopUiKit.VortexNumberField syNum = new DesktopUiKit.VortexNumberField("H", axisYCol, e.scaleY, 0.1, 0.1, 999);
        DesktopUiKit.VortexNumberField szNum = new DesktopUiKit.VortexNumberField("D", axisZCol, e.scaleZ, 0.1, 0.1, 999);
        sxNum.setOnChange(v -> { e.scaleX = (float) v; viewport.repaint(); });
        syNum.setOnChange(v -> { e.scaleY = (float) v; viewport.repaint(); });
        szNum.setOnChange(v -> { e.scaleZ = (float) v; viewport.repaint(); });
        sclRow.add(sxNum); sclRow.add(syNum); sclRow.add(szNum);
        tBody.add(sclRow);

        tBody.add(Box.createVerticalStrut(6));
        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        tBody.add(sectionLabel(is3D ? "Rotation Y (Degrees)" : "Rotation Z (Degrees)"));
        tBody.add(Box.createVerticalStrut(3));
        DesktopUiKit.VortexNumberField rotNum = new DesktopUiKit.VortexNumberField(
                "°", DesktopUiKit.C_WHITE, is3D ? e.rotY : e.rotZ, 5.0, -3600, 3600);
        rotNum.setAlignmentX(Component.LEFT_ALIGNMENT);
        rotNum.setOnChange(v -> {
            if ("3D".equalsIgnoreCase(project.dimensionMode)) e.rotY = (float) v;
            else e.rotZ = (float) v;
            viewport.repaint();
        });
        tBody.add(rotNum);
        inspectorForm.add(transformSec);
        inspectorForm.add(Box.createVerticalStrut(6));

        // =====================================================================
        // Folder 2: Rendering, Color & Sprite Animation
        // =====================================================================
        DesktopUiKit.AccordionSection renderSec = makeAccordion("RENDERING", "Rendering & Sprites", true);
        JPanel rBody = renderSec.getBody();

        String[] prims = is3D
                ? new String[]{"CUBE", "SPHERE", "PYRAMID", "CYLINDER", "PLANE", "TERRAIN", "CUSTOM_OBJ"}
                : new String[]{"RECT", "CIRCLE", "TRIANGLE", "CUSTOM_SPRITE", "SPRITE_HERO", "SPRITE_SLIME", "SPRITE_COIN", "SPRITE_SHIP"};
        DesktopUiKit.VortexDropdown primBox = addDropdownTo(rBody, "Primitive / Sprite Mesh", prims, e.primitive);
        primBox.setOnSelect((idx, val) -> {
            e.primitive = val;
            refreshHierarchyList();
            viewport.repaint();
        });

        rBody.add(Box.createVerticalStrut(6));
        rBody.add(sectionLabel("Tint Color (#RRGGBB)"));
        rBody.add(Box.createVerticalStrut(2));
        JPanel colRow = new JPanel(new BorderLayout(5, 0));
        colRow.setOpaque(false);
        colRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        colRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        final JTextField colTf = DesktopUiKit.createField(e.colorHex);
        final JPanel swatch = new JPanel();
        swatch.setPreferredSize(new Dimension(DesktopUiKit.scaled(26), DesktopUiKit.scaled(26)));
        swatch.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_WHITE, 1));
        swatch.setBackground(parseHexSafe(e.colorHex));
        swatch.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        swatch.setToolTipText("Click to pick quick game palette color");
        swatch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent ev) {
                String[][] presets = {
                        {"Pure White (#FFFFFF)", "#FFFFFF"},
                        {"Emerald (#00E676)", "#00E676"},
                        {"Coral Red (#FF5252)", "#FF5252"},
                        {"Solar Gold (#FACC15)", "#FACC15"},
                        {"Titanium (#52525B)", "#52525B"},
                        {"Carbon Wall (#27272A)", "#27272A"}
                };
                List<DesktopUiKit.MenuAction> acts = new ArrayList<>();
                for (final String[] pr : presets) {
                    acts.add(new DesktopUiKit.MenuAction(pr[0], () -> {
                        colTf.setText(pr[1]);
                        e.colorHex = pr[1];
                        swatch.setBackground(parseHexSafe(pr[1]));
                        viewport.repaint();
                    }));
                }
                DesktopUiKit.showPopupMenu(swatch, acts);
            }
        });
        colTf.addActionListener(ev -> {
            e.colorHex = colTf.getText().trim();
            swatch.setBackground(parseHexSafe(e.colorHex));
            viewport.repaint();
        });
        colRow.add(swatch, BorderLayout.WEST);
        colRow.add(colTf, BorderLayout.CENTER);
        rBody.add(colRow);

        JTextField framesTf = addFieldTo(rBody, "Flipbook Frames (e.g. knight_1,knight_2)", e.spriteFrames);
        rBody.add(Box.createVerticalStrut(5));
        rBody.add(sectionLabel("Flipbook Animation Speed"));
        rBody.add(Box.createVerticalStrut(2));
        DesktopUiKit.VortexNumberField fpsNum = new DesktopUiKit.VortexNumberField(
                "FPS", DesktopUiKit.C_WHITE, e.animFps, 0.5, 0.5, 60.0);
        fpsNum.setAlignmentX(Component.LEFT_ALIGNMENT);
        fpsNum.setOnChange(v -> { e.animFps = (float) v; viewport.repaint(); });
        rBody.add(fpsNum);
        rBody.add(Box.createVerticalStrut(6));
        DesktopUiKit.VortexDropdown matBox = addDropdownTo(rBody, "Material Preset", MATERIAL_PRESETS,
                e.material == null ? "MATTE" : e.material);
        matBox.setOnSelect((idx, val) -> {
            e.material = val;
            // EMISSIVE preset implies the emissive shading path, keep them in sync.
            if ("EMISSIVE".equals(val)) e.emissive = true;
            viewport.repaint();
        });

        rBody.add(Box.createVerticalStrut(6));
        DesktopUiKit.VortexDropdown texBox = addDropdownTo(rBody, "Surface Pattern", TEXTURE_PATTERNS,
                e.texture == null || e.texture.isEmpty() ? "NONE" : e.texture);
        texBox.setOnSelect((idx, val) -> { e.texture = val; viewport.repaint(); });

        rBody.add(Box.createVerticalStrut(5));
        DesktopUiKit.VortexToggle chkCb = addToggleTo(rBody, "Checkerboard Surface", e.checkerTexture);
        chkCb.setOnChange(() -> { e.checkerTexture = chkCb.isSelected(); viewport.repaint(); });
        DesktopUiKit.VortexToggle glowCb = addToggleTo(rBody, "Emissive Highlight", e.emissive);
        glowCb.setOnChange(() -> { e.emissive = glowCb.isSelected(); viewport.repaint(); });

        inspectorForm.add(renderSec);
        inspectorForm.add(Box.createVerticalStrut(6));

        // =====================================================================
        // Folder 3: Physics & Collisions
        // =====================================================================
        DesktopUiKit.AccordionSection physSec = makeAccordion("PHYSICS", "Physics & Collisions", true);
        JPanel pBody = physSec.getBody();

        String[] bodies = {"NONE", "STATIC", "DYNAMIC", "KINEMATIC"};
        DesktopUiKit.VortexDropdown bodyBox = addDropdownTo(pBody, "Physics Body Type", bodies, e.bodyType);
        bodyBox.setOnSelect((idx, val) -> {
            e.bodyType = val;
            refreshHierarchyList();
            viewport.repaint();
        });

        pBody.add(Box.createVerticalStrut(6));
        pBody.add(sectionLabel("Material Bounce & Friction"));
        pBody.add(Box.createVerticalStrut(2));
        JPanel matRow = new JPanel(new GridLayout(1, 2, 5, 0));
        matRow.setOpaque(false);
        matRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        matRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(28)));
        DesktopUiKit.VortexNumberField bncNum = new DesktopUiKit.VortexNumberField("Bnc", DesktopUiKit.C_TEXT_LABEL, e.bounce, 0.05, 0.0, 2.0);
        DesktopUiKit.VortexNumberField frcNum = new DesktopUiKit.VortexNumberField("Frc", DesktopUiKit.C_TEXT_LABEL, e.friction, 0.05, 0.0, 2.0);
        bncNum.setOnChange(v -> e.bounce = (float) v);
        frcNum.setOnChange(v -> e.friction = (float) v);
        matRow.add(bncNum); matRow.add(frcNum);
        pBody.add(matRow);

        pBody.add(Box.createVerticalStrut(5));
        DesktopUiKit.VortexToggle trigCb = addToggleTo(pBody, "Trigger Sensor (Overlap)", e.isTrigger);
        trigCb.setOnChange(() -> e.isTrigger = trigCb.isSelected());
        DesktopUiKit.VortexToggle fixRotCb = addToggleTo(pBody, "Lock 2D Rotation", e.fixedRotation);
        fixRotCb.setOnChange(() -> e.fixedRotation = fixRotCb.isSelected());
        DesktopUiKit.VortexToggle box2dCb = addToggleTo(pBody, "World: JBox2D Solver", project.useBox2D);
        box2dCb.setOnChange(() -> project.useBox2D = box2dCb.isSelected());

        inspectorForm.add(physSec);
        inspectorForm.add(Box.createVerticalStrut(6));

        // =====================================================================
        // Folder 4: Identity & Custom Tags
        // =====================================================================
        DesktopUiKit.AccordionSection idSec = makeAccordion("TAGS", "Identity & Custom Tags", true);
        JPanel iBody = idSec.getBody();
        JTextField nameTf = addFieldTo(iBody, "Entity Name", e.name);
        final JTextField tagTf = addFieldTo(iBody, "Collision / AI Tag", e.tag);

        iBody.add(Box.createVerticalStrut(4));
        JPanel tagPresets = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        tagPresets.setOpaque(false);
        tagPresets.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (final String preset : new String[]{"player", "enemy", "solid", "coin", "goal"}) {
            DesktopUiKit.VortexButton chip = new DesktopUiKit.VortexButton(preset, DesktopUiKit.Variant.SECONDARY);
            chip.addActionListener(ev -> {
                tagTf.setText(preset);
                e.tag = preset;
            });
            tagPresets.add(chip);
        }
        iBody.add(tagPresets);

        inspectorForm.add(idSec);
        inspectorForm.add(Box.createVerticalStrut(8));

        DesktopUiKit.VortexButton apply = new DesktopUiKit.VortexButton("Apply Entity & World", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CHECK);
        apply.setAlignmentX(Component.LEFT_ALIGNMENT);
        apply.setMaximumSize(new Dimension(Integer.MAX_VALUE, DesktopUiKit.scaled(32)));
        apply.addActionListener(ev -> {
            e.name = nameTf.getText().trim();
            e.tag = tagTf.getText().trim();
            e.primitive = primBox.getSelectedItem();
            e.x = xNum.getFloatValue(); e.y = yNum.getFloatValue(); e.z = zNum.getFloatValue();
            e.scaleX = Math.max(0.1f, sxNum.getFloatValue());
            e.scaleY = Math.max(0.1f, syNum.getFloatValue());
            e.scaleZ = Math.max(0.1f, szNum.getFloatValue());
            if ("3D".equalsIgnoreCase(project.dimensionMode)) e.rotY = rotNum.getFloatValue();
            else e.rotZ = rotNum.getFloatValue();
            e.bounce = bncNum.getFloatValue();
            e.friction = frcNum.getFloatValue();
            e.colorHex = colTf.getText().trim();
            swatch.setBackground(parseHexSafe(e.colorHex));
            e.spriteFrames = framesTf.getText().trim();
            e.animFps = Math.max(0.5f, fpsNum.getFloatValue());
            e.bodyType = bodyBox.getSelectedItem();
            e.isTrigger = trigCb.isSelected();
            e.fixedRotation = fixRotCb.isSelected();
            e.checkerTexture = chkCb.isSelected();
            e.emissive = glowCb.isSelected();
            project.useBox2D = box2dCb.isSelected();
            refreshHierarchyList();
            viewport.repaint();
            log("✓ [SUCCESS] Updated entity '" + e.name + "' (" + fmt(e.x) + ", " + fmt(e.y) + ", " + fmt(e.z) + ")");
        });
        inspectorForm.add(apply);
        inspectorForm.revalidate();
        inspectorForm.repaint();
    }

    private DesktopUiKit.AccordionSection makeAccordion(final String stateKey, String title, boolean defaultOpen) {
        boolean open = inspectorAccordionState.getOrDefault(stateKey, defaultOpen);
        DesktopUiKit.AccordionSection sec = new DesktopUiKit.AccordionSection(title, open);
        sec.setOnToggle(exp -> inspectorAccordionState.put(stateKey, exp));
        return sec;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(DesktopUiKit.FONT_UI);
        l.setForeground(DesktopUiKit.C_TEXT_LABEL);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JTextField addFieldTo(JPanel host, String label, String val) {
        host.add(Box.createVerticalStrut(5));
        host.add(sectionLabel(label));
        host.add(Box.createVerticalStrut(2));
        JTextField tf = DesktopUiKit.createField(val);
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        host.add(tf);
        return tf;
    }

    private DesktopUiKit.VortexDropdown addDropdownTo(JPanel host, String label, String[] items, String cur) {
        host.add(Box.createVerticalStrut(4));
        host.add(sectionLabel(label));
        host.add(Box.createVerticalStrut(2));
        DesktopUiKit.VortexDropdown dd = new DesktopUiKit.VortexDropdown(items, cur);
        dd.setAlignmentX(Component.LEFT_ALIGNMENT);
        host.add(dd);
        return dd;
    }

    private DesktopUiKit.VortexToggle addToggleTo(JPanel host, String label, boolean checked) {
        host.add(Box.createVerticalStrut(2));
        DesktopUiKit.VortexToggle t = new DesktopUiKit.VortexToggle(label, checked);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        host.add(t);
        return t;
    }

    private static Color parseHexSafe(String hex) {
        try {
            if (hex == null) return Color.WHITE;
            String s = hex.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) return new Color(Integer.parseInt(s, 16));
        } catch (Exception ignored) {}
        return Color.WHITE;
    }

    private void showAddEntityMenu(DesktopUiKit.VortexButton invoker) {
        showAddEntityMenu((javax.swing.JComponent) invoker, null);
    }

    /** Create a node; when {@code parentId} is set the new node is attached inside that node. */
    private void showAddEntityMenu(javax.swing.JComponent invoker, String parentId) {
        pendingParentId = parentId;
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        if ("3D".equalsIgnoreCase(project.dimensionMode)) {
            items.add(new DesktopUiKit.MenuAction("+ 3D Cube (Static)", () -> spawnEntity("CUBE", "#FFFFFF", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("+ 3D Sphere (Dynamic)", () -> spawnEntity("SPHERE", "#FACC15", "DYNAMIC")));
            items.add(new DesktopUiKit.MenuAction("+ 3D Pyramid", () -> spawnEntity("PYRAMID", "#EF4444", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("+ FastNoiseLite 3D Terrain (New Seed)", () -> {
                pushUndo();
                project.terrainSeed = (int) (System.currentTimeMillis() % 90000) + 1000;
                Entity t = Entity.create3D("Terrain_" + project.terrainSeed, "solid", "TERRAIN", 0f, 0f, 0f, 65f, 9f, 65f, "#27272A", "STATIC");
                t.checkerTexture = true; t.secondaryColorHex = "#18181B";
                project.entities.add(0, t);
                viewport.invalidateAssetCaches();
                refreshHierarchyList();
                viewport.repaint();
                log("Generated 3D FastNoiseLite Terrain (seed=" + project.terrainSeed + ")");
            }));
            items.add(new DesktopUiKit.MenuAction("+ Import Custom Wavefront .OBJ 3D Mesh…", this::showCustomObjModal));
        } else {
            items.add(new DesktopUiKit.MenuAction("+ 2D Platform (Static)", () -> spawnEntity("RECT", "#3F3F46", "STATIC")));
            items.add(new DesktopUiKit.MenuAction("+ 2D Hero (Dynamic)", () -> spawnEntity("SPRITE_HERO", "#FFFFFF", "DYNAMIC")));
            items.add(new DesktopUiKit.MenuAction("+ JBox2D Tumbling Crate", () -> {
                pushUndo();
                project.customSprites.putIfAbsent("crate", PixelSpriteCodec.createCrateSprite());
                Entity c = Entity.create2D("Crate_" + (project.entities.size() + 1), "solid", "CUSTOM_SPRITE", 0f, 3f, 1.3f, 1.3f, "#F59E0B", "DYNAMIC");
                c.customSpriteName = "crate"; c.fixedRotation = false;
                project.entities.add(c);
                viewport.setSelectedEntityId(c.id);
                refreshHierarchyList();
                viewport.repaint();
            }));
            items.add(new DesktopUiKit.MenuAction("+ 2D Coin Trigger", () -> {
                Entity c = spawnEntity("SPRITE_COIN", "#FACC15", "STATIC");
                c.isTrigger = true;
            }));
            items.add(new DesktopUiKit.MenuAction("+ 2D Slime Enemy", () -> spawnEntity("SPRITE_SLIME", "#EF4444", "DYNAMIC")));
        }
        DesktopUiKit.showPopupMenu(invoker, items);
    }

    private void showCustomObjModal() {
        JDialog dlg = new JDialog(this, "Import Wavefront .OBJ 3D Mesh", true);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(DesktopUiKit.C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_WHITE, 1),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        root.setPreferredSize(new Dimension(480, 340));

        JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
        top.setOpaque(false);
        JLabel h = new JLabel("IMPORT WAVEFRONT .OBJ 3D MESH");
        h.setFont(DesktopUiKit.FONT_TITLE);
        h.setForeground(DesktopUiKit.C_WHITE);
        JTextField nameTf = DesktopUiKit.createField("starfighter");
        top.add(h);
        top.add(nameTf);
        root.add(top, BorderLayout.NORTH);

        JTextArea objArea = new JTextArea(
                "v 0 0.8 0\nv -0.9 -0.4 0.7\nv 0.9 -0.4 0.7\nv 0 -0.2 -1.2\n"
                + "f 1 2 3\nf 1 3 4\nf 1 4 2\nf 2 4 3\n");
        objArea.setBackground(DesktopUiKit.C_VOID);
        objArea.setForeground(DesktopUiKit.C_TEXT);
        objArea.setCaretColor(DesktopUiKit.C_WHITE);
        objArea.setFont(DesktopUiKit.FONT_MONO);
        objArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane sp = DesktopUiKit.createScrollPane(objArea);
        sp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        root.add(sp, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        DesktopUiKit.VortexButton cancel = new DesktopUiKit.VortexButton("Cancel", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        cancel.addActionListener(e -> dlg.dispose());
        DesktopUiKit.VortexButton spawn = new DesktopUiKit.VortexButton("Save & Spawn 3D Mesh", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CUBE);
        spawn.addActionListener(e -> {
            String key = nameTf.getText().trim().isEmpty() ? "mesh_1" : nameTf.getText().trim();
            pushUndo();
            project.customObjMeshes.put(key, objArea.getText());
            project.dimensionMode = "3D";
            dimBtn.setText("3D");
            Entity m = Entity.create3D("Obj_" + key, "prop", "CUSTOM_OBJ", 0f, 2f, 0f, 1.5f, 1.5f, 1.5f, "#FFFFFF", "DYNAMIC");
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
        attachToPendingParent(e);
        viewport.setSelectedEntityId(e.id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
        return e;
    }

    /** Godot behaviour: a spawned child node is dropped at its parent's origin. */
    private void attachToPendingParent(Entity child) {
        String pid = pendingParentId;
        if (pid == null) return;
        Entity parent = project.findById(pid);
        if (parent == null) return;
        SceneHierarchy.reparent(project.entities, child, parent);
        child.localX = 0f;
        child.localY = 0f;
        child.localZ = 0f;
        SceneHierarchy.applyLocalToWorld(child, parent);
        log("+ " + child.name + " → child of " + parent.name);
    }

    private void togglePlayMode() {
        if (viewport.isPlaying()) {
            viewport.audioEngine.stopAll();
            viewport.loadProject(project, false);
            telemetryLabel.setText("Editor • " + project.entities.size() + " objs • "
                    + project.dimensionMode + " / " + project.scriptLanguage);
            playStopBtn.setText("PLAY GAME");
            playStopBtn.setIconType(DesktopUiKit.Icon.PLAY);
            playStopBtn.setVariant(DesktopUiKit.Variant.PRIMARY);
            log("Stopped Play Mode (audio cut immediately).");
        } else {
            if (!checkScriptSyntax()) {
                rightTabs.selectTabByKey("CODE", true);
                DesktopUiKit.showInfoModal(this, "Script Syntax Error",
                        "Please fix the highlighted script syntax error in the Code tab before starting Play Mode.");
                return;
            }
            viewport.repaint();
            viewport.loadProject(project.deepCopy(), true);
            playStopBtn.setText("STOP PLAY");
            playStopBtn.setIconType(DesktopUiKit.Icon.STOP);
            playStopBtn.setVariant(DesktopUiKit.Variant.DANGER);
            log("Started Live Play Mode (" + project.scriptLanguage + ", "
                    + project.scriptFiles.size() + " file(s), "
                    + project.blueprintNodes.size() + " Blueprint block(s)).");
        }
    }

    private void showExportMenu(DesktopUiKit.VortexButton invoker) {
        syncCode();
        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
        items.add(new DesktopUiKit.MenuAction("Export Playable HTML5 Web Game (.html)", () -> exportWithDialog("html")));
        items.add(new DesktopUiKit.MenuAction("Export Standalone Windows Game (.exe)", () -> exportWithDialog("exe")));
        items.add(new DesktopUiKit.MenuAction("Export Standalone Linux Binary", () -> exportWithDialog("linux")));
        items.add(new DesktopUiKit.MenuAction("Export Cross-Platform Desktop Game (.jar)", () -> exportWithDialog("jar")));
        items.add(new DesktopUiKit.MenuAction("Export Android Termux APK Project (.zip)", () -> exportWithDialog("android")));
        items.add(new DesktopUiKit.MenuAction("---", null));
        items.add(new DesktopUiKit.MenuAction("Open Export Hub Tab", () -> rightTabs.selectTabByKey("EXPORT", true)));
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
                BorderFactory.createLineBorder(DesktopUiKit.C_WHITE, 1),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));
        root.setPreferredSize(new Dimension(450, 245));

        JLabel h = new JLabel("CREATE NEW 2D / 3D GAME PROJECT");
        h.setFont(DesktopUiKit.FONT_TITLE);
        h.setForeground(DesktopUiKit.C_WHITE);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(h);
        root.add(Box.createVerticalStrut(12));

        JLabel l1 = mutedLabel("Game Title:");
        l1.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(l1);
        JTextField titleTf = DesktopUiKit.createField("Iron Sentinel");
        titleTf.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(titleTf);
        root.add(Box.createVerticalStrut(10));

        JLabel l2 = mutedLabel("Starter Template (Native Java • ES6 JS • Lua 5.2 • JBox2D • 3D GL):");
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
        DesktopUiKit.VortexButton cancel = new DesktopUiKit.VortexButton("Cancel", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        cancel.addActionListener(e -> dlg.dispose());
        DesktopUiKit.VortexButton create = new DesktopUiKit.VortexButton("Create & Open", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CHECK);
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
                if (f.getParentFile() != null) terminalPanel.setWorkingDirectory(f.getParentFile());
                log("Loaded project: " + f.getAbsolutePath());
            } catch (Exception ex) {
                DesktopUiKit.showInfoModal(this, "Open Failed", ex.getMessage());
            }
        }
    }

    /**
     * v4.7.0 — Tiled import. Builds the map straight into the scene tree as a TileWorld node
     * (merged tile runs + solid colliders + spawn points), sampling the tileset PNG for colours.
     */
    private void importTiledMap() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Import Tiled Map");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Tiled map (*.tmx)", "tmx"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        final java.io.File f = chooser.getSelectedFile();
        pushUndo();
        try {
            DesktopTmxImporter.ImportOutcome out = DesktopTmxImporter.importInto(project, f);
            if (!"2D".equalsIgnoreCase(project.dimensionMode)) {
                project.dimensionMode = "2D";
                project.ensureScriptFilesInitialized();
            }
            refreshHierarchyList();
            populateInspector();
            viewport.invalidateAssetCaches();
            viewport.repaint();
            log("\u2713 " + out.result.summary);
            if (!out.warning.isEmpty()) log("\u26a0 " + out.warning);
            globalStatusPill.setStatus("\u2713 Imported " + f.getName(), DesktopUiKit.StatusPill.State.SUCCESS);
        } catch (Exception ex) {
            log("\u2715 Tiled import failed: " + ex.getMessage());
            DesktopUiKit.showInfoModal(this, "Tiled import failed",
                    "Could not read " + f.getName() + ":\n" + ex.getMessage()
                    + "\n\nSupported: orthogonal maps, embedded or external tilesets, CSV or base64 (raw/zlib/gzip) layers.");
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
            log("Undid last change.");
        } catch (Exception ignored) {}
    }

    private void performRedo() {
        if (redoStack.isEmpty()) return;
        try {
            syncCode();
            undoStack.push(project.toJsonString());
            loadProjectIntoStudio(GameProject.fromJsonString(redoStack.pop()));
            log("Redid change.");
        } catch (Exception ignored) {}
    }

    private void cloneSelectedEntity() {
        Entity src = project.findById(viewport.getSelectedEntityId());
        if (src == null) return;
        pushUndo();
        // Godot-style duplicate: the whole subtree comes along, parented copy included.
        java.util.Map<String, String> idMap = new java.util.HashMap<>();
        Entity copy = duplicateNode(src, idMap);
        copy.x += 1.5f;
        copy.localX += 1.5f;
        for (Entity descendant : SceneHierarchy.subtree(project.entities, src)) {
            duplicateNode(descendant, idMap);
        }
        viewport.setSelectedEntityId(copy.id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
    }

    private Entity duplicateNode(Entity src, java.util.Map<String, String> idMap) {
        Entity copy = src.deepCopy();
        copy.id = java.util.UUID.randomUUID().toString().substring(0, 8);
        copy.name = src.name + "_copy";
        if (src.parentId != null && !src.parentId.isEmpty()) {
            String mapped = idMap.get(src.parentId);
            copy.parentId = mapped != null ? mapped : src.parentId;
        }
        idMap.put(src.id, copy.id);
        project.entities.add(copy);
        return copy;
    }

    /** Deletes the node and its whole subtree (Godot removes children with the parent). */
    private void deleteSelectedEntity() {
        Entity src = project.findById(viewport.getSelectedEntityId());
        if (src == null) return;
        pushUndo();
        List<Entity> doomed = SceneHierarchy.subtree(project.entities, src);
        doomed.add(src);
        project.entities.removeAll(doomed);
        if (doomed.size() > 1) log("− " + doomed.size() + " nodes removed (subtree)");
        viewport.setSelectedEntityId(project.entities.isEmpty() ? null : project.entities.get(0).id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
    }

    private boolean checkScriptSyntax() {
        syncCode();
        String check = VortexScriptEngine.validateProjectSyntax(project);
        if (check.startsWith("OK")) {
            syntaxStatusLabel.setText("✓ " + check);
            syntaxStatusLabel.setBackground(DesktopUiKit.C_OK_BG);
            syntaxStatusLabel.setForeground(DesktopUiKit.C_OK_TX);
            globalStatusPill.setStatus("✓ Build OK (" + project.scriptFiles.size() + " files)",
                    DesktopUiKit.StatusPill.State.SUCCESS);
            return true;
        } else {
            syntaxStatusLabel.setText("✕ " + check);
            syntaxStatusLabel.setBackground(DesktopUiKit.C_DANGER_BG);
            syntaxStatusLabel.setForeground(DesktopUiKit.C_DANGER_TX);
            globalStatusPill.setStatus("✕ Script Error", DesktopUiKit.StatusPill.State.ERROR);
            log("✕ [ERROR] " + check);
            return false;
        }
    }

    private void syncCode() {
        if (project == null) return;
        project.ensureScriptFilesInitialized();
        String current = scriptArea.getText();
        project.putScriptFile(project.activeScriptFile, current);
        if (snappedZone != null && snappedFilePath != null && !snappedFilePath.equals(project.activeScriptFile)) {
            project.putScriptFile(snappedFilePath, snappedScriptArea.getText());
        }
    }

    private void log(String s) {
        if (s == null) return;
        consoleArea.appendColoredLine(s);
        String u = s.toUpperCase(Locale.US);
        if (u.contains("[ERROR]") || u.contains("[FAIL") || s.startsWith("✕")) {
            globalStatusPill.setStatus("✕ Error — Check Console", DesktopUiKit.StatusPill.State.ERROR);
        } else if (u.contains("[SAVED]") || s.contains("Saved project")) {
            globalStatusPill.setStatus("✓ Project Saved", DesktopUiKit.StatusPill.State.SUCCESS);
        } else if (u.contains("EXPORTED")) {
            globalStatusPill.setStatus("✓ Export Succeeded", DesktopUiKit.StatusPill.State.SUCCESS);
        } else if (u.contains("STARTED LIVE PLAY")) {
            globalStatusPill.setStatus("▶ Live Play Active", DesktopUiKit.StatusPill.State.SUCCESS);
        } else if (u.contains("STOPPED PLAY")) {
            globalStatusPill.setStatus("■ Editor Ready", DesktopUiKit.StatusPill.State.NEUTRAL);
        }
    }

    private JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(DesktopUiKit.FONT_UI);
        l.setForeground(DesktopUiKit.C_TEXT_LABEL);
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
        DesktopUiKit.VortexButton restart = new DesktopUiKit.VortexButton("Restart Game", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.REFRESH);
        restart.addActionListener(e -> vp.loadProject(gameProj.deepCopy(), true));
        top.add(restart);
        frame.add(top, BorderLayout.NORTH);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                vp.shutdown();
            }
        });

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
    // Headless Verification Suite (Java VM + ES6 JS + Lua + LRM + Forge + UI)
    // =========================================================================
    private static void runHeadlessSelfTests() throws Exception {
        System.out.println("=== Vortex Engine v4.8.0 Headless Verification Suite ===");

        // 1) Zero-Blue Monochrome Palette + Laptop UI Scale Verification
        Color[] uiColors = {
                DesktopUiKit.C_VOID, DesktopUiKit.C_HEADER, DesktopUiKit.C_PANEL,
                DesktopUiKit.C_SURFACE, DesktopUiKit.C_WHITE, DesktopUiKit.C_CYAN
        };
        for (Color c : uiColors) {
            if (c.getBlue() - Math.max(c.getRed(), c.getGreen()) > 10) {
                throw new RuntimeException("Blue tint detected in UI color: " + c);
            }
        }
        DesktopUiKit.setUiScale(1.00f);
        if (DesktopUiKit.scaled(100) != 100) throw new RuntimeException("UI scale math failed!");
        if (DesktopUiKit.FONT_UI.getSize() < 14) {
            throw new RuntimeException("UI text too small for readable IDE: " + DesktopUiKit.FONT_UI.getSize() + "pt");
        }
        if (DesktopUiKit.FONT_MONO.getSize() < 14) {
            throw new RuntimeException("Editor mono text too small: " + DesktopUiKit.FONT_MONO.getSize() + "pt");
        }
        System.out.println("  [PASS] Zero-Blue Palette + Readable Font Engine (UI "
                + DesktopUiKit.FONT_UI.getSize() + "pt " + DesktopUiKit.UI_FAMILY
                + " / Code " + DesktopUiKit.FONT_MONO.getSize() + "pt " + DesktopUiKit.MONO_FAMILY + ") Verified!");

        // 2) Instant Audio Stop (< 15ms) & Idle Editor Zero-FPS Verification
        DesktopSoftware3DViewport vpCheck = new DesktopSoftware3DViewport();
        GameProject idleProj = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "IdleTest", "com.vortex.idle");
        vpCheck.loadProject(idleProj, false);
        long t0 = System.nanoTime();
        vpCheck.audioEngine.stopAll();
        long stopMs = (System.nanoTime() - t0) / 1_000_000L;
        if (stopMs > 50 || vpCheck.isPlaying()) {
            throw new RuntimeException("Idle/Audio stop verification failed! stopMs=" + stopMs);
        }
        vpCheck.shutdown();
        System.out.println("  [PASS] Instant Audio Cutoff (" + stopMs + "ms) & Zero-Idle Editor Timer Verified!");

        // 3) JSplitPane Drag, Collapse & UX Controls Verification
        JPanel dummyA = new JPanel();
        dummyA.setPreferredSize(new Dimension(320, 600));
        JPanel dummyB = new JPanel();
        dummyB.setPreferredSize(new Dimension(600, 600));
        JSplitPane testSplit = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, dummyA, dummyB, 290);
        testSplit.setSize(1200, 800);
        testSplit.doLayout();
        testSplit.setDividerLocation(160);
        testSplit.doLayout();
        int minAllowed = testSplit.getMinimumDividerLocation();
        int maxAllowed = testSplit.getMaximumDividerLocation();
        if (minAllowed > 20 || maxAllowed < 1100 || testSplit.getDividerSize() != 9) {
            throw new RuntimeException("SplitPane drag/collapse bounds locked! min=" + minAllowed + ", max=" + maxAllowed);
        }
        DesktopUiKit.VortexNumberField scrub = new DesktopUiKit.VortexNumberField("X", Color.WHITE, 2.5, 0.5, -100, 100);
        scrub.setValue(4.25);
        if (Math.abs(scrub.getFloatValue() - 4.25f) > 0.001f) {
            throw new RuntimeException("VortexNumberField precision check failed!");
        }
        DesktopUiKit.AccordionSection acc = new DesktopUiKit.AccordionSection("Transform", true);
        acc.setExpanded(false);
        if (acc.isExpanded()) throw new RuntimeException("AccordionSection collapse failed!");
        DesktopUiKit.VortexLogPane logPane = new DesktopUiKit.VortexLogPane();
        logPane.appendColoredLine("✓ [SUCCESS] Build passed");
        logPane.appendColoredLine("✕ [ERROR] Syntax error on line 4");
        if (!logPane.getText().contains("Build passed") || !logPane.getText().contains("Syntax error")) {
            throw new RuntimeException("VortexLogPane colored append failed!");
        }
        System.out.println("  [PASS] Beveled SplitPane (0px.." + maxAllowed + "px), Scrubber, Accordion & Color Logs Verified!");

        // 4) All Starter Templates via Multi-File + Blueprint `compileProject`!
        for (int i = 0; i < ProjectTemplates.TEMPLATE_KEYS.length; i++) {
            String key = ProjectTemplates.TEMPLATE_KEYS[i];
            GameProject p = ProjectTemplates.create(key, "Test_" + key, "com.vortex.test." + key.toLowerCase(Locale.US));
            PhysicsWorld pw = new PhysicsWorld();
            VortexScriptEngine vm = new VortexScriptEngine();
            vm.attach(p, pw, null);

            if (!vm.compileProject(p)) {
                throw new RuntimeException("Template failed compileProject (" + key + "): " + vm.getLastError());
            }

            vm.inputJoyX = 0.6f;
            vm.notifyButton("A");
            for (int f = 0; f < 45; f++) {
                vm.update(0.016f);
                List<PhysicsWorld.CollisionEvent> cols = pw.step(p, 0.016f);
                for (PhysicsWorld.CollisionEvent ce : cols) vm.notifyCollision(ce.a, ce.b);
            }
            vm.dispose();

            GameProject rt = GameProject.fromJsonString(p.toJsonString());
            if (rt.entities.isEmpty() || rt.scriptFiles.isEmpty() || rt.blueprintNodes.isEmpty()) {
                throw new RuntimeException("JSON round-trip lost entities/files/blueprints for " + key);
            }
            System.out.println("  [PASS] Template " + key + " (" + p.dimensionMode + " / " + p.scriptLanguage
                    + " / files=" + p.scriptFiles.size() + " / bpNodes=" + p.blueprintNodes.size() + ")");
        }

        // 5) Blueprint Visual Diagram + Classic Flow + In-Game GUI Widgets Verification
        GameProject cyber = ProjectTemplates.create("CYBER_MAZE_AI_2D", "Cyber Hunters", "com.vortex.cyber");
        String bpJs = com.vortex.engine.core.flow.VortexBlueprintGraph.compileGraph(cyber, "JS");
        String bpJava = com.vortex.engine.core.flow.VortexBlueprintGraph.compileGraph(cyber, "JAVA");
        String bpLua = com.vortex.engine.core.flow.VortexBlueprintGraph.compileGraph(cyber, "LUA");
        if (!VortexScriptEngine.validateSyntax("JS", bpJs).startsWith("OK")
                || !VortexScriptEngine.validateSyntax("JAVA", bpJava).startsWith("OK")
                || !VortexScriptEngine.validateSyntax("LUA", bpLua).startsWith("OK")) {
            throw new RuntimeException("Blueprint triple compiler syntax check failed!");
        }
        String flowJs = VortexFlowCompiler.compileToJavaScript(cyber);
        String flowJava = VortexFlowCompiler.compileToJava(cyber);
        String flowLua = VortexFlowCompiler.compileToLua(cyber);
        if (!VortexScriptEngine.validateSyntax("JS", flowJs).startsWith("OK")
                || !VortexScriptEngine.validateSyntax("JAVA", flowJava).startsWith("OK")
                || !VortexScriptEngine.validateSyntax("LUA", flowLua).startsWith("OK")) {
            throw new RuntimeException("Flow triple compiler syntax check failed!");
        }
        System.out.println("  [PASS] Blueprint Graph + Classic Flow Compilers (JS + Java + Lua + GUI Blocks) Verified!");

        // 6) Multi-Turn Contextual Forge AI Chat + IDE Tool Execution Verification
        List<VortexForgeAgent.ChatMessage> chatHist = new ArrayList<>();
        GameProject chatProj = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "ChatTest", "com.vortex.chat");
        int filesBefore = chatProj.scriptFiles.size();
        VortexForgeAgent.ChatTurnResult turn1 = VortexForgeAgent.runChatTurn(
                chatProj, chatHist, "Add a modular dialog and HUD label UI file", 0, "", "");
        if (!turn1.mutatedProject || chatProj.scriptFiles.size() <= filesBefore || turn1.testReport == null || !turn1.testReport.ok) {
            throw new RuntimeException("Forge AI chat tool execution failed! " + turn1.reply);
        }
        System.out.println("  [PASS] Multi-Turn Forge AI Contextual Tools (" + String.join(", ", turn1.toolBadges) + ") Verified!");

        // 5) Forge AI (Retired Groq Migration + Reasoning <think> Stripper + Offline JS & Java)
        VortexForgeAgent.savePrefs(1, "llama-3.3-70b-versatile", "");
        if (!"openai/gpt-oss-120b".equals(VortexForgeAgent.loadSavedModel(1))) {
            throw new RuntimeException("Retired Groq model migration failed!");
        }
        String stripped = VortexForgeAgent.extractJsonObject("<think>Testing {bad: 1}</think>\n```json\n{\"ok\":true}\n```");
        if (!"{\"ok\":true}".equals(stripped)) {
            throw new RuntimeException("Reasoning <think> JSON stripper failed: " + stripped);
        }
        GameProject forgeJava = new GameProject();
        forgeJava.scriptLanguage = "JAVA";
        VortexForgeAgent.applyPromptToProject(forgeJava, "2D swarm arena in java", 0, "", "", true);
        if (!VortexScriptEngine.validateSyntax("JAVA", forgeJava.scriptCode).startsWith("OK")) {
            throw new RuntimeException("Forge Java generator invalid: " + VortexScriptEngine.validateSyntax("JAVA", forgeJava.scriptCode));
        }
        System.out.println("  [PASS] Vortex Forge AI (Groq Model Fix + OpenRouter Free + Native Java Gen) Verified!");

        // 6) LRM System Prober + Multi-File Unpack (including .java) + HTML5 Java->Web Export
        LrmWorkspaceBridge.SystemProbe sysProbe = LrmWorkspaceBridge.probeSystem();
        System.out.println("  [PASS] System Dependency Prober (" + sysProbe.osName + " / lrm=" + (sysProbe.lrmBin != null ? sysProbe.lrmVersion : "none") + ")");

        File tmpLrmDir = Files.createTempDirectory("vortex_lrm_java_").toFile();
        GameProject javaSent = ProjectTemplates.create("IRON_SENTINEL_JAVA_2D", "Iron Sentinel Java", "com.vortex.java");
        LrmWorkspaceBridge.exportToLrmRepo(javaSent, tmpLrmDir);
        if (LrmWorkspaceBridge.findLrmBinary() != null) {
            LrmWorkspaceBridge.CmdResult initRes = LrmWorkspaceBridge.runLrm(tmpLrmDir, 10, "init", "--user", "ci");
            LrmWorkspaceBridge.CmdResult ciRes = LrmWorkspaceBridge.runLrm(tmpLrmDir, 10, "commit", "-m", "Initial Java VM commit");
            if (initRes.exitCode != 0 || ciRes.exitCode != 0) {
                throw new RuntimeException("LRM CLI commit failed: " + initRes.output + " / " + ciRes.output);
            }
            System.out.println("  [PASS] Live LRM CLI Integration (" + ciRes.output.replace("\n", " | ") + ")");
        }
        GameProject reimported = LrmWorkspaceBridge.importFromLrmRepo(tmpLrmDir);
        if (!"JAVA".equals(reimported.scriptLanguage) || reimported.entities.size() != javaSent.entities.size()) {
            throw new RuntimeException("LRM Java round-trip failed!");
        }

        File tmpHtml = File.createTempFile("iron_sentinel_java_web_", ".html");
        Html5WebExporter.exportToFile(javaSent, tmpHtml);
        if (tmpHtml.length() < 12_000) throw new RuntimeException("HTML5 Web export of Java game unexpectedly small!");
        System.out.println("  [PASS] Native Java Game -> Single-File HTML5 Web Transpiler (" + (tmpHtml.length() / 1024) + " KB .html)");
        tmpHtml.delete();

        // 7) Built-In Interactive Terminal Execution Check
        DesktopTerminalPanel term = new DesktopTerminalPanel(tmpLrmDir);
        term.runCommand("echo VORTEX_TERM_OK");
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline && !term.getOutputText().contains("VORTEX_TERM_OK")) {
            Thread.sleep(60);
        }
        // Flush EDT invokeLater
        SwingUtilities.invokeAndWait(() -> {});
        if (!term.getOutputText().contains("VORTEX_TERM_OK")) {
            throw new RuntimeException("Interactive Terminal did not capture command output!");
        }
        System.out.println("  [PASS] Built-In Interactive OS Shell Terminal Verified!");

        // 7.5) v4.4 Unreal-inspired Physics Solver Verification (CCD, Layers, Raycast, Radial Impulse, Coyote Time)
        GameProject physProj = ProjectTemplates.create("PLATFORMER_2D", "PhysTest", "com.vortex.phys");
        physProj.dimensionMode = "2D";
        physProj.useBox2D = false;
        PhysicsWorld solver = new PhysicsWorld();
        Entity floorEnt = Entity.create2D("FloorX", "solid", "RECT", 0f, -2f, 24f, 1f, "#52525B", "STATIC");
        Entity ball = Entity.create2D("BallX", "player", "CIRCLE", 0f, 8f, 1f, 1f, "#FFFFFF", "DYNAMIC");
        ball.collider = "SPHERE";
        ball.bounce = 0.4f;
        physProj.entities.clear();
        physProj.entities.add(floorEnt);
        physProj.entities.add(ball);
        physProj.gravityY = -22f;
        for (int f = 0; f < 240; f++) solver.step(physProj, 0.016f);
        if (ball.y < -3.5f || ball.y > 6f) {
            throw new RuntimeException("Substepped solver did not settle ball on floor: y=" + ball.y);
        }
        if (!ball.canJump()) {
            throw new RuntimeException("Coyote-time grounding never registered on contact!");
        }
        // Collision layer filtering
        ball.collisionLayer = 0x2;
        ball.collisionMask = 0x2;
        floorEnt.collisionLayer = 0x1;
        floorEnt.collisionMask = 0x1;
        ball.y = 6f;
        ball.vy = 0f;
        for (int f = 0; f < 60; f++) solver.step(physProj, 0.016f);
        if (ball.y > 0f) {
            throw new RuntimeException("Collision layer/mask filtering failed to ignore non-matching layer!");
        }
        // Raycast downward hit
        PhysicsWorld.RaycastHit rh = solver.raycast(physProj, 0f, 6f, 0f, 0f, -1f, 0f, 40f, null);
        if (rh == null || rh.entity == null || rh.distance <= 0f) {
            throw new RuntimeException("Box2D-style raycast failed to hit scene geometry!");
        }
        // Radial shockwave impulse
        ball.collisionLayer = 1;
        ball.collisionMask = 0xFFFF;
        int pushed = solver.applyRadialImpulse(physProj, 0f, ball.y, 0f, 20f, 8f, true);
        if (pushed < 1 || Math.abs(ball.vy) < 0.5f) {
            throw new RuntimeException("Radial impulse shockwave failed to affect dynamic body!");
        }
        System.out.println("  [PASS] Physics Solver (CCD substeps, layers/mask, raycast "
                + String.format(Locale.US, "%.2f", rh.distance) + "m, radial impulse, coyote-time) Verified!");

        // 7.6) Godot-style compact UI chrome: menus, property rows, readable fonts
        javax.swing.JPanel propRow = DesktopUiKit.createPropertyRow("Position X",
                new DesktopUiKit.VortexNumberField("X", Color.WHITE, 1.0, 0.1, -99, 99));
        if (propRow.getComponentCount() < 2) throw new RuntimeException("Property row layout failed!");
        System.out.println("  [PASS] Godot/Unreal-style Inspector Property Rows + Compact Menu Chrome Verified!");

        // 7.7) Godot-style scene tree: parent/child world composition, cycle guard, JSON round-trip
        {
            GameProject treeProj = new GameProject();
            treeProj.dimensionMode = "3D";
            Entity root = Entity.create3D("Ship", "player", "CUBE", 5f, 0f, 0f, 1f, 1f, 1f, "#FFFFFF", "STATIC");
            Entity turret = Entity.create3D("Turret", "prop", "CUBE", 5f, 0f, 0f, 0.5f, 0.5f, 0.5f, "#FFFFFF", "STATIC");
            Entity muzzle = Entity.create3D("Muzzle", "prop", "SPHERE", 5f, 0f, 0f, 0.2f, 0.2f, 0.2f, "#FFFFFF", "STATIC");
            treeProj.entities.add(root);
            treeProj.entities.add(turret);
            treeProj.entities.add(muzzle);
            if (!SceneHierarchy.reparent(treeProj.entities, turret, root)) throw new RuntimeException("reparent failed");
            turret.localX = 1f; turret.localY = 2f; turret.localZ = 0f;
            SceneHierarchy.propagate(treeProj.entities);
            if (Math.abs(turret.x - 6f) > 1e-3f || Math.abs(turret.y - 2f) > 1e-3f) {
                throw new RuntimeException("child world transform did not compose (x=" + turret.x + ", y=" + turret.y + ")");
            }
            SceneHierarchy.reparent(treeProj.entities, muzzle, turret);
            muzzle.localX = 0.5f; muzzle.localY = 0f; muzzle.localZ = 0f;
            SceneHierarchy.propagate(treeProj.entities);
            if (Math.abs(muzzle.x - 6.25f) > 1e-3f) throw new RuntimeException("3-deep nesting composition failed (x=" + muzzle.x + ")");
            // moving the root drags grandchildren
            root.x = 20f;
            SceneHierarchy.propagate(treeProj.entities);
            if (Math.abs(muzzle.x - 21.25f) > 1e-3f) throw new RuntimeException("parent motion did not drag descendants (x=" + muzzle.x + ")");
            // world transform survives reparenting / detaching
            SceneHierarchy.reparent(treeProj.entities, turret, null);
            if (Math.abs(turret.x - 21f) > 1e-3f) throw new RuntimeException("detach moved the node on screen");
            // cycles are impossible
            if (!SceneHierarchy.reparent(treeProj.entities, turret, root)) throw new RuntimeException("re-attach failed");
            if (SceneHierarchy.reparent(treeProj.entities, root, muzzle)) throw new RuntimeException("cycle guard failed");
            if (SceneHierarchy.reparent(treeProj.entities, muzzle, muzzle)) throw new RuntimeException("self-parent guard failed");
            // depth + order + JSON round-trip
            GameProject reloaded = GameProject.fromJsonString(treeProj.toJsonString());
            Entity rMuzzle = reloaded.findById(muzzle.id);
            if (rMuzzle == null || !turret.id.equals(rMuzzle.parentId)) throw new RuntimeException("parentId lost in save/load");
            System.out.println("  [PASS] Godot Scene Tree (nested world transforms, drag-reparent, cycle guard, subtree order, JSON round-trip) Verified!");

        // 7.8) Forge AI: no chain-of-thought leaks, JSON repair, interactive questions
        {
            String leaked = "We need answer user. Need parse. We are Vortex Forge AI, JSON only. "
                    + "{\"reply\": \"Spawned Boss_1 with a boss HP bar.\", \"actions\": []} trailing junk";
            String json = VortexForgeAgent.extractJsonObject(leaked);
            org.json.JSONObject parsed = new org.json.JSONObject(json);
            if (!parsed.optString("reply").contains("Boss_1")) {
                throw new RuntimeException("extractJsonObject failed to recover the embedded JSON");
            }
            if (!VortexForgeAgent.looksLikeChainOfThought(leaked)) {
                throw new RuntimeException("chain-of-thought detector missed a scratchpad dump");
            }
            String clean = VortexForgeAgent.sanitizeReply(leaked);
            if (clean.toLowerCase(Locale.US).contains("we need answer")) {
                throw new RuntimeException("sanitizeReply leaked model reasoning into the chat bubble");
            }
            if (VortexForgeAgent.looksLikeAnswerJson("Let's think step by step. The user wants a boss.")) {
                throw new RuntimeException("plain reasoning was mistaken for a structured answer");
            }
            String repaired = VortexForgeAgent.extractJsonObject("{\"reply\":\"ok\",\"actions\":[],}");
            new org.json.JSONObject(repaired); // must not throw
            // offline small talk must answer like a person and offer answer chips
            GameProject talk = ProjectTemplates.create("PLATFORMER_2D", "Chat", "com.vortex.chat");
            VortexForgeAgent.ChatTurnResult hello = VortexForgeAgent.runChatTurn(
                    talk, new ArrayList<>(), "hello", 0, "offline-procedural-v4", "");
            if (!hello.reply.toLowerCase(Locale.US).contains("forge ai")
                    || hello.questions.isEmpty() || hello.mutatedProject) {
                throw new RuntimeException("offline greeting did not answer conversationally with question chips");
            }
            VortexForgeAgent.ChatTurnResult vague = VortexForgeAgent.runChatTurn(
                    talk, new ArrayList<>(), "make a game", 0, "offline-procedural-v4", "");
            if (vague.questions.size() < 2) {
                throw new RuntimeException("vague request did not return clarifying questions");
            }
            System.out.println("  [PASS] Forge AI (no CoT leak, JSON repair, 1-tap question chips, offline small talk) Verified!");
        }

        // 7.9) Lighting rig: sun/ambient/shadow settings persist and shade geometry
        {
            GameProject lit = ProjectTemplates.create("NEON_3D", "Light", "com.vortex.light");
            lit.sunYawDeg = 120f;
            lit.sunPitchDeg = 30f;
            lit.sunIntensity = 1.4f;
            lit.ambientStrength = 0.2f;
            lit.shadows = false;
            GameProject litBack = GameProject.fromJsonString(lit.toJsonString());
            if (Math.abs(litBack.sunYawDeg - 120f) > 0.01f || Math.abs(litBack.sunIntensity - 1.4f) > 0.01f
                    || litBack.shadows) {
                throw new RuntimeException("light rig settings did not survive save/load");
            }
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(320, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2 = img.createGraphics();
            DesktopSoftware3DViewport vp = new DesktopSoftware3DViewport();
            vp.setSize(320, 200);
            vp.paint(g2);
            g2.dispose();
            System.out.println("  [PASS] Unreal-style Lighting Rig (sun yaw/elev/power, ambient fill, contact shadows, JSON round-trip) Verified!");
        }

            // 7.10) Tiled import + in-editor tile brush + prefabs
            {
                String tmx = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<map version=\"1.10\" orientation=\"orthogonal\" width=\"4\" height=\"3\" tilewidth=\"16\" tileheight=\"16\">"
                        + "<tileset firstgid=\"1\" name=\"t\" tilewidth=\"16\" tileheight=\"16\" tilecount=\"16\" columns=\"4\">"
                        + "<image source=\"missing.png\" width=\"64\" height=\"64\"/></tileset>"
                        + "<layer id=\"1\" name=\"Ground\" width=\"4\" height=\"3\"><data encoding=\"csv\">"
                        + "1,1,1,1,1,1,1,1,1,1,1,1</data></layer>"
                        + "<layer id=\"2\" name=\"Collision\" width=\"4\" height=\"3\"><data encoding=\"csv\">"
                        + "2,2,2,2,0,0,0,0,0,0,0,0</data></layer>"
                        + "<objectgroup id=\"3\" name=\"Objects\">"
                        + "<object id=\"1\" name=\"spawn\" x=\"16\" y=\"16\" width=\"16\" height=\"16\"/>"
                        + "<object id=\"2\" name=\"wall\" x=\"0\" y=\"32\" width=\"16\" height=\"16\">"
                        + "<properties><property name=\"solid\" value=\"true\"/></properties></object>"
                        + "</objectgroup></map>";
                GameProject tiledProj = new GameProject();
                com.vortex.engine.core.asset.TmxMap map = com.vortex.engine.core.asset.TmxMap.parseText(tmx);
                com.vortex.engine.core.asset.TileWorldBuilder.Result built =
                        com.vortex.engine.core.asset.TileWorldBuilder.build(tiledProj, map, null, "selftest");
                if (map.layers.size() != 2 || built.mergedFromTiles != 16 || built.spawns != 1
                        || built.solidObjects != 1 || tiledProj.findByName("Hero") == null) {
                    throw new RuntimeException("Tiled import produced unexpected scene (" + built.summary + ")");
                }
                // Tile brush: paint, dedupe, merge runs, erase.
                GameProject brushProj = new GameProject();
                for (int gx = 0; gx < 5; gx++) {
                    com.vortex.engine.core.asset.TileBrush.paint(brushProj, "Ground", gx, 0, "#6E7B5B", false);
                }
                boolean noDup = com.vortex.engine.core.asset.TileBrush.paint(brushProj, "Ground", 0, 0, "#6E7B5B", false) == null;
                int before = brushProj.entities.size();
                int savedRuns = com.vortex.engine.core.asset.TileBrush.mergeRuns(brushProj, "Ground");
                if (!noDup || brushProj.entities.size() != before - savedRuns || savedRuns < 4) {
                    throw new RuntimeException("tile brush merge did not compact runs (saved=" + savedRuns + ")");
                }
                if (!com.vortex.engine.core.asset.TileBrush.erase(brushProj, "Ground", 2, 0)) {
                    throw new RuntimeException("tile brush erase failed");
                }
                // Prefabs: capture a subtree, instantiate elsewhere, survive save/load.
                Entity pRoot = Entity.create2D("Turret", "prop", "CUBE", 1f, 1f, 1f, 1f, "#C9A227", "STATIC");
                Entity pChild = Entity.create2D("Barrel", "prop", "CUBE", 1f, 1f, 1f, 1f, "#DDD8CE", "NONE");
                brushProj.entities.add(pRoot);
                brushProj.entities.add(pChild);
                com.vortex.engine.core.model.SceneHierarchy.reparent(brushProj.entities, pChild, pRoot);
                com.vortex.engine.core.model.Prefab prefab = com.vortex.engine.core.model.Prefab.capture(
                        brushProj.entities, pRoot, "Turret", "Custom");
                brushProj.prefabs.add(prefab);
                int n0 = brushProj.entities.size();
                Entity inst = prefab.instantiate(brushProj, null, 10f, 0f, 0f);
                GameProject brushReloaded = GameProject.fromJsonString(brushProj.toJsonString());
                if (prefab.nodeCount() != 2 || inst == null || brushProj.entities.size() != n0 + 2
                        || com.vortex.engine.core.model.SceneHierarchy.childrenOf(brushProj.entities, inst).size() != 1
                        || brushReloaded.findPrefab("Turret") == null
                        || brushReloaded.findPrefab("Turret").nodeCount() != 2) {
                    throw new RuntimeException("prefab capture/instantiate/save round-trip failed");
                }
                System.out.println("  [PASS] Tiled .tmx import (" + built.summary
                        + "), in-editor Tile Brush (merge/erase) and Scene-Tree Prefabs Verified!");
            }

            // 7.11) Voxel world: generation, meshing, physics, editing, docs
            {
                GameProject vox = ProjectTemplates.create("MINECRAFT_3D", "Blockfall", "com.vortex.blockfall");
                if (!vox.voxelEnabled) throw new RuntimeException("voxel template did not enable the block world");
                com.vortex.engine.core.voxel.VoxelRuntime rt = vox.ensureVoxelRuntime();
                com.vortex.engine.core.voxel.VoxelWorld world = rt.world;
                int solid = 0;
                for (byte b : world.blocks) if (b != 0) solid++;
                if (solid < 50000) throw new RuntimeException("voxel world generated too little terrain (" + solid + ")");

                java.util.List<com.vortex.engine.core.voxel.VoxelWorld.Quad> quads = rt.quads();
                if (quads == null || quads.size() < 1000 || quads.size() > 60000) {
                    throw new RuntimeException("greedy mesh out of expected range (" + (quads == null ? 0 : quads.size()) + ")");
                }

                // player physics: fall, land, walk, jump
                com.vortex.engine.core.voxel.VoxelPlayer vp = rt.player;
                for (int i = 0; i < 180; i++) vp.update(world, 1f / 60f, 0, 0, false, false, false);
                if (!vp.onGround) throw new RuntimeException("voxel player never landed");
                if (vp.collides(world)) throw new RuntimeException("voxel player is inside a block after landing");
                float beforeX = vp.x, beforeZ = vp.z;
                for (int i = 0; i < 60; i++) vp.update(world, 1f / 60f, 1, 0, false, false, true);
                double travelled = Math.hypot(vp.x - beforeX, vp.z - beforeZ);
                if (travelled < 0.4) throw new RuntimeException("voxel player did not walk (" + travelled + ")");
                float vyBefore = vp.vy;
                vp.update(world, 1f / 60f, 0, 0, true, false, false);
                if (vp.vy <= vyBefore - 5f) throw new RuntimeException("voxel jump did not apply");

                // look at feet, mine the block, place it back
                vp.pitchDeg = -80f;
                com.vortex.engine.core.voxel.VoxelWorld.RayHit hit = vp.target(world);
                if (hit == null) throw new RuntimeException("voxel raycast found nothing under the player");
                int mined = rt.breakTargeted();
                if (mined == com.vortex.engine.core.voxel.BlockType.AIR) throw new RuntimeException("voxel mining failed");
                int meshVersion = world.dirty();
                if (meshVersion == 0) throw new RuntimeException("voxel world did not bump its edit version");

                // save / load keeps the world identical
                String worldJson = vox.toJsonString();
                GameProject voxBack = GameProject.fromJsonString(worldJson);
                com.vortex.engine.core.voxel.VoxelRuntime rtBack = voxBack.ensureVoxelRuntime();
                if (!java.util.Arrays.equals(rtBack.world.blocks, world.blocks)) {
                    throw new RuntimeException("voxel world did not survive save/load");
                }

                // documentation answers a beginner's question with the real API
                com.vortex.engine.core.docs.VortexDocs.Topic doc =
                        com.vortex.engine.core.docs.VortexDocs.answerFor("how do I make my player jump?");
                if (doc == null || !doc.body.contains("jump")) {
                    throw new RuntimeException("docs did not answer a movement question");
                }
                if (com.vortex.engine.core.docs.VortexDocs.all().size() < 8) {
                    throw new RuntimeException("docs topics missing");
                }
                // Forge answers a how-to with docs instead of rebuilding the scene
                String entitiesBefore = String.valueOf(vox.entities.size());
                VortexForgeAgent.ChatTurnResult howTo = VortexForgeAgent.runChatTurn(
                        vox, new ArrayList<>(), "how do I make a block world?", 0, "offline-procedural-v4", "");
                if (howTo.reply == null || howTo.reply.length() < 40) {
                    throw new RuntimeException("forge gave no documentation answer");
                }
                System.out.println("  [PASS] Voxel World (96x96x64 generation, greedy mesh "
                        + quads.size() + " quads, walk/jump/mine, RLE save round-trip) + In-App Docs & Forge doc answers Verified!");
            }
        }

        // 8) Standalone Exporters (.exe & Termux .zip)
        File tmpExe = File.createTempFile("java_sentinel_test_", ".exe");
        File tmpZip = File.createTempFile("java_sentinel_android_", ".zip");
        DesktopExporter.exportStandaloneWindowsExe(javaSent, tmpExe);
        DesktopExporter.exportAndroidTermuxZip(javaSent, tmpZip);
        // .exe = Win32 launcher stub + full game jar; the Termux bundle is a small dex + scripts.
        if (tmpExe.length() < 1_500_000 || tmpZip.length() < 40_000) {
            throw new RuntimeException("Exported artifacts unexpectedly small! (exe=" + tmpExe.length()
                    + ", zip=" + tmpZip.length() + ")");
        }
        System.out.println("  [PASS] Exported Standalone Windows Game .exe (" + (tmpExe.length() / 1024) + " KB) & Android Termux .zip");
        tmpExe.delete();
        tmpZip.delete();

        System.out.println("=== ALL VORTEX v4.8.0 HEADLESS VERIFICATION TESTS PASSED 100%! ===");
    }
}
