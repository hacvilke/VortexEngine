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
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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
    private final DefaultListModel<String> entityListModel = new DefaultListModel<>();
    private final JList<String> entityList = new JList<>(entityListModel);

    private final JPanel inspectorForm = new JPanel();
    private final JTextArea scriptArea = new JTextArea();
    private final JLabel syntaxStatusLabel = new JLabel("Syntax Ready");
    private final JTextArea consoleArea = new JTextArea();
    private final DesktopTerminalPanel terminalPanel;
    private DesktopUiKit.VortexTabStrip bottomTabs;
    private final DesktopUiKit.AnimatedDeck bottomDeck = new DesktopUiKit.AnimatedDeck();
    private final JLabel telemetryLabel = new JLabel("60 FPS • Ready");

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
        super("Vortex Engine Studio v4.2.0 — Native Java • ES6 JS • Lua 5.2 • LRM P2P Sync • Forge AI • APK/PC/HTML5");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1480, 880);
        setLocationRelativeTo(null);
        getContentPane().setBackground(DesktopUiKit.C_VOID);
        setLayout(new BorderLayout());

        project = ProjectTemplates.create("NEON_SURVIVORS_JS_2D", "Neon Survivors Swarm", "com.vortex.game.survivors");

        terminalPanel = new DesktopTerminalPanel(
                new File(System.getProperty("user.dir", ".")),
                () -> showLrmDependencyWizardModal(false));

        add(buildTopToolbar(), BorderLayout.NORTH);

        // Left Dock: Scene Hierarchy + Entity & Physics Inspector
        JPanel leftPanel = buildHierarchyAndInspector();

        // Center Dock: 60 FPS 2D/3D Viewport + Tabbed Console & Interactive OS Terminal
        JPanel bottomPanel = buildBottomConsoleAndTerminalPanel();
        centerVerticalSplit = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, viewport, bottomPanel, 610);
        centerVerticalSplit.setResizeWeight(0.78);

        // Right Dock: Animated 7-Tab Studio Workspace (Code, Flow, Forge AI, LRM, Pixel, Audio, Export)
        JPanel rightPanel = buildRightTabbedWorkspace();

        midRightSplit = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerVerticalSplit, rightPanel, 680);
        midRightSplit.setResizeWeight(0.60);

        mainSplit = DesktopUiKit.createSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, midRightSplit, 290);
        mainSplit.setResizeWeight(0.18);

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

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
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
        liveLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
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
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(DesktopUiKit.C_HEADER);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);

        JLabel brand = new JLabel("VORTEX STUDIO");
        brand.setFont(new Font("SansSerif", Font.BOLD, 14));
        brand.setForeground(DesktopUiKit.C_WHITE);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 6));
        left.add(brand);

        DesktopUiKit.VortexDropdown tplQuickDrop = new DesktopUiKit.VortexDropdown(
                ProjectTemplates.TEMPLATE_NAMES, ProjectTemplates.TEMPLATE_NAMES[0]);
        tplQuickDrop.setPreferredSize(new Dimension(190, 30));
        tplQuickDrop.setToolTipText("Switch starter game template (JS, Java, Lua, 3D, Box2D)");
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

        DesktopUiKit.VortexButton newBtn = new DesktopUiKit.VortexButton("New", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
        newBtn.addActionListener(e -> showNewTemplateModal());
        left.add(newBtn);

        DesktopUiKit.VortexButton openBtn = new DesktopUiKit.VortexButton("Open", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.FOLDER);
        openBtn.addActionListener(e -> openProjectJson());
        left.add(openBtn);

        DesktopUiKit.VortexButton saveBtn = new DesktopUiKit.VortexButton("Save", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SAVE);
        saveBtn.addActionListener(e -> saveProjectJson());
        left.add(saveBtn);

        DesktopUiKit.VortexButton undoBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.UNDO);
        undoBtn.setToolTipText("Undo");
        undoBtn.addActionListener(e -> performUndo());
        left.add(undoBtn);

        DesktopUiKit.VortexButton redoBtn = new DesktopUiKit.VortexButton("", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REDO);
        redoBtn.setToolTipText("Redo");
        redoBtn.addActionListener(e -> performRedo());
        left.add(redoBtn);

        dimBtn = new DesktopUiKit.VortexButton("Mode: 2D", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.CUBE);
        dimBtn.addActionListener(e -> {
            pushUndo();
            project.dimensionMode = "3D".equalsIgnoreCase(project.dimensionMode) ? "2D" : "3D";
            dimBtn.setText("Mode: " + project.dimensionMode);
            viewport.resetEditorCamera();
            populateInspector();
            viewport.repaint();
        });
        left.add(dimBtn);

        DesktopUiKit.VortexButton addObjBtn = new DesktopUiKit.VortexButton("Entity", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.PLUS);
        addObjBtn.addActionListener(e -> showAddEntityMenu(addObjBtn));
        left.add(addObjBtn);

        DesktopUiKit.VortexButton termJumpBtn = new DesktopUiKit.VortexButton("Terminal", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.TERMINAL);
        termJumpBtn.setToolTipText("Open built-in interactive OS shell terminal");
        termJumpBtn.addActionListener(e -> {
            if (bottomTabs != null) bottomTabs.selectTabByKey("TERMINAL", true);
            if (centerVerticalSplit.getDividerLocation() > getHeight() - 180) {
                centerVerticalSplit.setDividerLocation(Math.max(300, getHeight() - 260));
            }
            terminalPanel.focusInput();
        });
        left.add(termJumpBtn);

        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        right.setOpaque(false);

        telemetryLabel.setFont(DesktopUiKit.FONT_UI);
        telemetryLabel.setForeground(DesktopUiKit.C_TEXT_MUTED);
        telemetryLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
        right.add(telemetryLabel);

        playStopBtn = new DesktopUiKit.VortexButton("PLAY GAME", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.PLAY);
        playStopBtn.addActionListener(e -> togglePlayMode());
        right.add(playStopBtn);

        DesktopUiKit.VortexButton exportBtn = new DesktopUiKit.VortexButton("Export", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.EXPORT);
        exportBtn.addActionListener(e -> showExportMenu(exportBtn));
        right.add(exportBtn);

        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // =========================================================================
    // Bottom Dock: Tabbed Console Output + Interactive OS Shell Terminal
    // =========================================================================
    private JPanel buildBottomConsoleAndTerminalPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(DesktopUiKit.C_VOID);
        wrapper.setMinimumSize(new Dimension(240, 110));
        wrapper.setPreferredSize(new Dimension(640, 210));

        JPanel headerBar = new JPanel(new BorderLayout(8, 0));
        headerBar.setBackground(DesktopUiKit.C_HEADER);
        headerBar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopUiKit.C_BORDER));

        bottomTabs = new DesktopUiKit.VortexTabStrip(
                new String[]{"CONSOLE", "TERMINAL"},
                new String[]{"Console Output", "Interactive Terminal"},
                new DesktopUiKit.Icon[]{DesktopUiKit.Icon.CODE, DesktopUiKit.Icon.TERMINAL});
        bottomTabs.setPreferredSize(new Dimension(340, 34));
        headerBar.add(bottomTabs, BorderLayout.WEST);

        JPanel rightBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        rightBar.setOpaque(false);
        DesktopUiKit.VortexButton clearConsoleBtn = new DesktopUiKit.VortexButton("Clear Logs", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        clearConsoleBtn.addActionListener(e -> consoleArea.setText(""));
        rightBar.add(clearConsoleBtn);
        headerBar.add(rightBar, BorderLayout.EAST);

        wrapper.add(headerBar, BorderLayout.NORTH);

        consoleArea.setEditable(false);
        consoleArea.setBackground(DesktopUiKit.C_VOID);
        consoleArea.setForeground(DesktopUiKit.C_TEXT);
        consoleArea.setCaretColor(DesktopUiKit.C_WHITE);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        consoleArea.setText("Vortex Engine v4.2.0 Desktop Studio Ready.\n"
                + "• Polyglot VMs  : Native Java (BeanShell JVM) + ES6 JavaScript (Mozilla Rhino) + Lua 5.2 + Vortex Flow + VXS\n"
                + "• P2P & AI      : LRM P2P Version Control (hacvilke/lrm) + Vortex Forge AI (Groq / OpenRouter Free / Offline)\n"
                + "• Tip           : Switch to 'Interactive Terminal' tab next door to run PowerShell / sh / java / go / lrm commands!\n");

        JScrollPane consoleScroll = DesktopUiKit.createScrollPane(consoleArea);
        bottomDeck.addCard("CONSOLE", consoleScroll, 0);
        bottomDeck.addCard("TERMINAL", terminalPanel, 1);

        bottomTabs.setOnTabSelected((idx, key) -> {
            bottomDeck.showCard(key, true);
            if ("TERMINAL".equals(key)) {
                SwingUtilities.invokeLater(terminalPanel::focusInput);
            }
        });

        wrapper.add(bottomDeck, BorderLayout.CENTER);
        return wrapper;
    }

    // =========================================================================
    // Left Dock: Scene Hierarchy + Custom Entity & Physics Inspector
    // =========================================================================
    private JPanel buildHierarchyAndInspector() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(DesktopUiKit.C_PANEL);
        panel.setMinimumSize(new Dimension(180, 200));
        panel.setPreferredSize(new Dimension(290, 600));

        JPanel topList = new JPanel(new BorderLayout());
        topList.setBackground(DesktopUiKit.C_PANEL);
        topList.setMinimumSize(new Dimension(160, 120));

        JLabel hLbl = new JLabel("SCENE HIERARCHY");
        hLbl.setForeground(DesktopUiKit.C_WHITE);
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
        listScroll.setPreferredSize(new Dimension(265, 205));
        topList.add(listScroll, BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
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

        inspectorForm.setLayout(new BoxLayout(inspectorForm, BoxLayout.Y_AXIS));
        inspectorForm.setBackground(DesktopUiKit.C_PANEL);
        inspectorForm.setBorder(BorderFactory.createEmptyBorder(8, 10, 16, 10));
        JScrollPane inspScroll = DesktopUiKit.createScrollPane(inspectorForm);
        inspScroll.getViewport().setBackground(DesktopUiKit.C_PANEL);
        inspScroll.setMinimumSize(new Dimension(160, 120));

        JSplitPane split = DesktopUiKit.createSplitPane(JSplitPane.VERTICAL_SPLIT, topList, inspScroll, 250);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // Right Dock: Animated 7-Tab Studio Workspace (Vector Icons + Zero Blue!)
    // =========================================================================
    private JPanel buildRightTabbedWorkspace() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(DesktopUiKit.C_VOID);
        wrapper.setMinimumSize(new Dimension(260, 200));
        wrapper.setPreferredSize(new Dimension(500, 620));

        String[] keys = {"CODE", "FLOW", "FORGE", "LRM", "PIXEL", "AUDIO", "EXPORT"};
        String[] labels = {"Code", "Flow", "Forge", "LRM", "Pixel", "Audio", "Export"};
        DesktopUiKit.Icon[] icons = {
                DesktopUiKit.Icon.CODE,
                DesktopUiKit.Icon.FLOW,
                DesktopUiKit.Icon.SPARKLE,
                DesktopUiKit.Icon.SYNC,
                DesktopUiKit.Icon.GRID,
                DesktopUiKit.Icon.WAVE,
                DesktopUiKit.Icon.EXPORT
        };
        rightTabs = new DesktopUiKit.VortexTabStrip(keys, labels, icons);

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

        vmDropdown = new DesktopUiKit.VortexDropdown(VM_ITEMS, VM_ITEMS[0]);
        vmDropdown.setPreferredSize(new Dimension(168, 30));
        vmDropdown.setOnSelect((idx, val) -> {
            syncCode();
            project.scriptLanguage = VM_CODES[idx];
            syntaxStatusLabel.setText("Switched VM to " + val + " — click Check Syntax.");
            syntaxStatusLabel.setForeground(DesktopUiKit.C_WHITE);
        });
        btnBar.add(vmDropdown);

        DesktopUiKit.VortexButton checkBtn = new DesktopUiKit.VortexButton("Check Syntax", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.CHECK);
        checkBtn.addActionListener(e -> checkScriptSyntax());
        btnBar.add(checkBtn);

        String[] snips = {
                "Insert API Snippet…",
                "vortex.findNearestTag(\"Player\", \"enemy\", 30)",
                "vortex.chaseTagAI(\"enemy\", \"Player\", 4.5)",
                "vortex.setSpriteAnim(\"Player\", \"knight_1,knight_2\", 7)",
                "vortex.tweenTagYoyo(\"coin\", 0.35, 0.9, \"SINE\")",
                "vortex.saveData(\"best\", vortex.getScore())",
                "vortex.playMusic(\"CYBERPUNK\", 136)",
                "vortex.particles(0, 1, 0, 22, \"#FFFFFF\")"
        };
        snippetDropdown = new DesktopUiKit.VortexDropdown(snips, snips[0]);
        snippetDropdown.setPreferredSize(new Dimension(165, 30));
        snippetDropdown.setOnSelect((idx, val) -> {
            if (idx > 0) {
                String tail = "LUA".equalsIgnoreCase(project.scriptLanguage) ? "\n" : ";\n";
                scriptArea.insert(val + tail, scriptArea.getCaretPosition());
                snippetDropdown.setSelectedIndex(0);
            }
        });
        btnBar.add(snippetDropdown);

        DesktopUiKit.VortexButton objQuickBtn = new DesktopUiKit.VortexButton(".OBJ", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CUBE);
        objQuickBtn.setToolTipText("Import Custom Wavefront .OBJ 3D Mesh");
        objQuickBtn.addActionListener(e -> showCustomObjModal());
        btnBar.add(objQuickBtn);

        DesktopUiKit.VortexButton docsBtn = new DesktopUiKit.VortexButton("API", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CODE);
        docsBtn.setToolTipText("Vortex Scripting API Reference (Java • JS • Lua • VXS)");
        docsBtn.addActionListener(e -> DesktopUiKit.showInfoModal(this, "Vortex Polyglot Scripting API (Java • ES6 JS • Lua 5.2 • VXS)",
                "• Native Java Class or Top-Level Methods: public void onStart(), onUpdate(float dt), onButton(String btn), onCollide(String a, String b, String tagA, String tagB)\n"
                + "• ES6 JS / Lua Hooks: onStart(), onUpdate(dt), onButton(btn), onCollide(a, b, tagA, tagB)\n"
                + "• Input: vortex.joyX(), vortex.joyY(), vortex.isDown(\"A\"), vortex.isDown(\"B\")\n"
                + "• Transform & Physics: vortex.setPos(n,x,y,z), vortex.setVel(n,vx,vy[,vz]), vortex.jump(n,vy), vortex.applyImpulse(n,ix,iy)\n"
                + "• AI & Queries: vortex.chaseTagAI(fromTag,target,spd), vortex.findNearestTag(from,tag,range), vortex.raycast(x,y,z,dx,dy,dz,max)\n"
                + "• Animation & FX: vortex.setSpriteAnim(n,\"f1,f2\",fps), vortex.tweenTagYoyo(tag,dy,dur,\"SINE\"), vortex.particles(x,y,z,cnt,\"#FFFFFF\")\n"
                + "• Save & Audio: vortex.saveData(k,v), vortex.loadNum(k,0), vortex.playSound(\"laser\"), vortex.playMusic(\"CYBERPUNK\",134)"));
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
        scriptArea.setCaretColor(DesktopUiKit.C_WHITE);
        scriptArea.setSelectionColor(DesktopUiKit.C_ACCENT_TINT);
        scriptArea.setSelectedTextColor(DesktopUiKit.C_WHITE);
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
    // Upgraded Vortex Forge AI Tab (Fixed Groq + OpenRouter Free + Live Models!)
    // =========================================================================
    private JPanel buildForgeAiTab() {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(DesktopUiKit.C_VOID);
        p.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(DesktopUiKit.C_PANEL);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));

        JLabel title = new JLabel("VORTEX FORGE AI STUDIO AGENT");
        title.setFont(DesktopUiKit.FONT_TITLE);
        title.setForeground(DesktopUiKit.C_WHITE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(title);

        JLabel sub = new JLabel("<html><body style='width:385px;color:#A1A1AA;margin-top:4px;'>"
                + "Local-first BYOK Studio Agent (<b>hacvilke/roforge</b>). Groq uses active <code>openai/gpt-oss-120b</code> "
                + "(with auto <code>/models</code> fallback) and OpenRouter defaults to <code>openrouter/free</code> ($0 cost). "
                + "API keys save locally on your machine.</body></html>");
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sub);
        form.add(Box.createVerticalStrut(10));

        int initProv = Math.max(0, Math.min(VortexForgeAgent.PROVIDERS.length - 1, VortexForgeAgent.loadSavedProviderIdx()));

        JLabel l1 = mutedLabel("AI Provider:");
        l1.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l1);
        DesktopUiKit.VortexDropdown provCb = new DesktopUiKit.VortexDropdown(
                VortexForgeAgent.PROVIDERS, VortexForgeAgent.PROVIDERS[initProv]);
        provCb.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(provCb);
        form.add(Box.createVerticalStrut(8));

        JLabel lKey = mutedLabel("API Key (saved locally; leave blank for Offline Smart Generator):");
        lKey.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lKey);
        final JTextField keyField = DesktopUiKit.createField(VortexForgeAgent.loadSavedApiKey(initProv));
        keyField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(keyField);
        form.add(Box.createVerticalStrut(8));

        JLabel lModel = mutedLabel("Model ID (editable or click Fetch Live Models):");
        lModel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lModel);

        JPanel modelRow = new JPanel(new BorderLayout(6, 0));
        modelRow.setOpaque(false);
        modelRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        final JTextField modelField = DesktopUiKit.createField(VortexForgeAgent.loadSavedModel(initProv));
        final DesktopUiKit.VortexButton fetchModelsBtn = new DesktopUiKit.VortexButton(
                "Live Models", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.REFRESH);
        modelRow.add(modelField, BorderLayout.CENTER);
        modelRow.add(fetchModelsBtn, BorderLayout.EAST);
        form.add(modelRow);
        form.add(Box.createVerticalStrut(8));

        provCb.setOnSelect((idx, val) -> {
            keyField.setText(VortexForgeAgent.loadSavedApiKey(idx));
            modelField.setText(VortexForgeAgent.loadSavedModel(idx));
        });

        final JLabel statusLbl = new JLabel("Ready — Undo checkpoint automatic.");
        statusLbl.setForeground(DesktopUiKit.C_TEXT_MUTED);
        statusLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        fetchModelsBtn.addActionListener(e -> {
            final int pIdx = provCb.getSelectedIndex();
            final String k = keyField.getText().trim();
            statusLbl.setText("Fetching live models from provider API…");
            statusLbl.setForeground(DesktopUiKit.C_WHITE);
            new Thread(() -> {
                try {
                    final List<String> models = VortexForgeAgent.fetchLiveModels(pIdx, k);
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setText("Loaded " + models.size() + " live models — choose one:");
                        statusLbl.setForeground(DesktopUiKit.C_OK_TX);
                        List<DesktopUiKit.MenuAction> items = new ArrayList<>();
                        int max = Math.min(28, models.size());
                        for (int i = 0; i < max; i++) {
                            final String mid = models.get(i);
                            items.add(new DesktopUiKit.MenuAction(mid, () -> {
                                modelField.setText(mid);
                                VortexForgeAgent.savePrefs(pIdx, mid, k);
                            }));
                        }
                        DesktopUiKit.showPopupMenu(fetchModelsBtn, items);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setText("Fetch error: " + ex.getMessage());
                        statusLbl.setForeground(DesktopUiKit.C_DANGER_TX);
                    });
                }
            }, "forge-fetch-models").start();
        });

        final DesktopUiKit.VortexToggle replaceToggle = new DesktopUiKit.VortexToggle(
                "Replace current scene entities cleanly", true);
        replaceToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(replaceToggle);

        final DesktopUiKit.VortexToggle javaGenToggle = new DesktopUiKit.VortexToggle(
                "Generate script in Native Java (off = ES6 JavaScript)", false);
        javaGenToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(javaGenToggle);
        form.add(Box.createVerticalStrut(8));

        JLabel l3 = mutedLabel("Describe the 2D or 3D Game Mechanic / Scene to Build:");
        l3.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l3);

        JTextArea promptArea = new JTextArea("2D cyber swarm arena with animated drones, XP gems, and auto-aim blaster", 4, 28);
        promptArea.setLineWrap(true);
        promptArea.setWrapStyleWord(true);
        promptArea.setBackground(DesktopUiKit.C_INPUT_BG);
        promptArea.setForeground(DesktopUiKit.C_TEXT);
        promptArea.setCaretColor(DesktopUiKit.C_WHITE);
        promptArea.setFont(DesktopUiKit.FONT_UI);
        promptArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane promptSp = DesktopUiKit.createScrollPane(promptArea);
        promptSp.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        promptSp.setAlignmentX(Component.LEFT_ALIGNMENT);
        promptSp.setPreferredSize(new Dimension(360, 82));
        form.add(promptSp);
        form.add(Box.createVerticalStrut(10));

        DesktopUiKit.VortexButton genBtn = new DesktopUiKit.VortexButton(
                "Generate Scene & Code (+ Undo Checkpoint)", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.SPARKLE);
        genBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        genBtn.addActionListener(e -> {
            pushUndo();
            if (javaGenToggle.isSelected()) {
                project.scriptLanguage = "JAVA";
            } else if ("JAVA".equalsIgnoreCase(project.scriptLanguage)) {
                project.scriptLanguage = "JS";
            }
            statusLbl.setText("Generating with Forge AI…");
            statusLbl.setForeground(DesktopUiKit.C_WHITE);
            final int selProv = provCb.getSelectedIndex();
            final String selModel = modelField.getText().trim();
            final String selKey = keyField.getText().trim();
            final String selPrompt = promptArea.getText().trim();
            final boolean rep = replaceToggle.isSelected();
            new Thread(() -> {
                try {
                    String msg = VortexForgeAgent.applyPromptToProject(
                            project, selPrompt, selProv, selModel, selKey, rep);
                    SwingUtilities.invokeLater(() -> {
                        modelField.setText(VortexForgeAgent.loadSavedModel(selProv));
                        loadProjectIntoStudio(project);
                        statusLbl.setText("✓ " + msg);
                        statusLbl.setForeground(DesktopUiKit.C_OK_TX);
                        log("[FORGE AI] " + msg);
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        statusLbl.setText("Error: " + ex.getMessage());
                        statusLbl.setForeground(DesktopUiKit.C_DANGER_TX);
                        log("[FORGE ERROR] " + ex.getMessage());
                    });
                }
            }, "forge-generate").start();
        });
        form.add(genBtn);
        form.add(Box.createVerticalStrut(6));
        form.add(statusLbl);

        JScrollPane outerScroll = DesktopUiKit.createScrollPane(form);
        outerScroll.getViewport().setBackground(DesktopUiKit.C_VOID);
        p.add(outerScroll, BorderLayout.CENTER);
        return p;
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
        lrmStatusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
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

        controls.add(commitBtn);  controls.add(shareBtn);
        controls.add(joinBtn);    controls.add(importBtn);
        controls.add(peersBtn);   controls.add(statusBtn);
        controls.add(daemonBtn);  controls.add(receiveBtn);
        controls.add(dashBtn);    controls.add(pairBtn);

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
        playStopBtn.setText("PLAY GAME");
        playStopBtn.setIconType(DesktopUiKit.Icon.PLAY);
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
        sec.setForeground(DesktopUiKit.C_WHITE);
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
        DesktopUiKit.VortexToggle glowCb = addToggle("Emissive Highlight", e.emissive);
        DesktopUiKit.VortexToggle box2dCb = addToggle("World: JBox2D Solver", project.useBox2D);

        inspectorForm.add(Box.createVerticalStrut(10));
        DesktopUiKit.VortexButton apply = new DesktopUiKit.VortexButton("Apply Entity & World", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.CHECK);
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
            dimBtn.setText("Mode: 3D");
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
        viewport.setSelectedEntityId(e.id);
        refreshHierarchyList();
        populateInspector();
        viewport.repaint();
        return e;
    }

    private void togglePlayMode() {
        if (viewport.isPlaying()) {
            viewport.loadProject(project, false);
            playStopBtn.setText("PLAY GAME");
            playStopBtn.setIconType(DesktopUiKit.Icon.PLAY);
            playStopBtn.setVariant(DesktopUiKit.Variant.PRIMARY);
            log("Stopped Play Mode.");
        } else {
            if (!checkScriptSyntax()) {
                rightTabs.selectTabByKey("CODE", true);
                DesktopUiKit.showInfoModal(this, "Script Syntax Error",
                        "Please fix the highlighted script syntax error in the Code tab before starting Play Mode.");
                return;
            }
            viewport.loadProject(project.deepCopy(), true);
            playStopBtn.setText("STOP PLAY");
            playStopBtn.setIconType(DesktopUiKit.Icon.STOP);
            playStopBtn.setVariant(DesktopUiKit.Variant.DANGER);
            log("Started Live Play Mode (" + project.scriptLanguage + ") — Controls: WASD/Arrows + Space(A) + Shift/K(B).");
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
        DesktopUiKit.VortexButton restart = new DesktopUiKit.VortexButton("Restart Game", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.REFRESH);
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
    // Headless Verification Suite (Java VM + ES6 JS + Lua + LRM + Forge + UI)
    // =========================================================================
    private static void runHeadlessSelfTests() throws Exception {
        System.out.println("=== Vortex Engine v4.2.0 Headless Verification Suite ===");

        // 1) Zero-Blue Monochrome Palette Verification
        Color[] uiColors = {
                DesktopUiKit.C_VOID, DesktopUiKit.C_HEADER, DesktopUiKit.C_PANEL,
                DesktopUiKit.C_SURFACE, DesktopUiKit.C_WHITE, DesktopUiKit.C_CYAN
        };
        for (Color c : uiColors) {
            if (c.getBlue() - Math.max(c.getRed(), c.getGreen()) > 10) {
                throw new RuntimeException("Blue tint detected in UI color: " + c);
            }
        }
        System.out.println("  [PASS] Zero-Blue Monochrome Dark + Crisp White UI Palette Verified!");

        // 2) JSplitPane Drag & Resize Verification
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
        if (minAllowed > 160 || maxAllowed < 900 || testSplit.getDividerSize() != 8) {
            throw new RuntimeException("SplitPane drag bounds locked! min=" + minAllowed + ", max=" + maxAllowed);
        }
        System.out.println("  [PASS] SplitPane Draggable Divider Range Verified (min=" + minAllowed + "px .. max=" + maxAllowed + "px)");

        // 3) All Starter Templates (including Native JAVA VM!)
        for (int i = 0; i < ProjectTemplates.TEMPLATE_KEYS.length; i++) {
            String key = ProjectTemplates.TEMPLATE_KEYS[i];
            GameProject p = ProjectTemplates.create(key, "Test_" + key, "com.vortex.test." + key.toLowerCase(Locale.US));
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

        // 4) Vortex Flow Triple Compiler (JS + JAVA + LUA)
        GameProject cyber = ProjectTemplates.create("CYBER_MAZE_AI_2D", "Cyber Hunters", "com.vortex.cyber");
        String flowJs = VortexFlowCompiler.compileToJavaScript(cyber);
        String flowJava = VortexFlowCompiler.compileToJava(cyber);
        String flowLua = VortexFlowCompiler.compileToLua(cyber);
        if (!VortexScriptEngine.validateSyntax("JS", flowJs).startsWith("OK")
                || !VortexScriptEngine.validateSyntax("JAVA", flowJava).startsWith("OK")
                || !VortexScriptEngine.validateSyntax("LUA", flowLua).startsWith("OK")) {
            throw new RuntimeException("Flow triple compiler syntax check failed!");
        }
        System.out.println("  [PASS] Vortex Flow Triple Compiler (JS + Native Java + Lua 5.2) Verified!");

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

        // 8) Standalone Exporters (.exe & Termux .zip)
        File tmpExe = File.createTempFile("java_sentinel_test_", ".exe");
        File tmpZip = File.createTempFile("java_sentinel_android_", ".zip");
        DesktopExporter.exportStandaloneWindowsExe(javaSent, tmpExe);
        DesktopExporter.exportAndroidTermuxZip(javaSent, tmpZip);
        if (tmpExe.length() < 200_000 || tmpZip.length() < 200_000) {
            throw new RuntimeException("Exported artifacts unexpectedly small!");
        }
        System.out.println("  [PASS] Exported Standalone Windows Game .exe (" + (tmpExe.length() / 1024) + " KB) & Android Termux .zip");
        tmpExe.delete();
        tmpZip.delete();

        System.out.println("=== ALL VORTEX v4.2.0 HEADLESS VERIFICATION TESTS PASSED 100%! ===");
    }
}
