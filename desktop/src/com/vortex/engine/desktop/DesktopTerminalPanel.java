package com.vortex.engine.desktop;

import com.vortex.engine.core.lrm.LrmWorkspaceBridge;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Interactive Cross-Platform OS Shell Terminal embedded directly inside Vortex Studio.
 * Uses PowerShell on Windows (so `irm ... | iex`, `ls`, `go`, `lrm`, `java` all work natively!)
 * and `/bin/sh` on Linux/macOS, with enriched PATH injection for Java, Go, and LRM.
 */
public final class DesktopTerminalPanel extends JPanel {

    private final DesktopUiKit.VortexLogPane outputArea = new DesktopUiKit.VortexLogPane();
    private final DesktopUiKit.StatusPill statusPill =
            new DesktopUiKit.StatusPill("● Shell Ready", DesktopUiKit.StatusPill.State.NEUTRAL);
    private final JTextField inputField;
    private final JLabel cwdLabel;
    private final DesktopUiKit.VortexButton stopBtn;
    private final Runnable onSetupLrmRequested;

    private File cwd;
    private volatile Process activeProcess;
    private final List<String> history = new ArrayList<>();
    private int historyIdx = -1;

    public DesktopTerminalPanel(File initialDir) {
        this(initialDir, null);
    }

    public DesktopTerminalPanel(File initialDir, Runnable onSetupLrmRequested) {
        super(new BorderLayout(0, 0));
        this.onSetupLrmRequested = onSetupLrmRequested;
        setBackground(DesktopUiKit.C_VOID);
        this.cwd = (initialDir != null && initialDir.isDirectory())
                ? initialDir.getAbsoluteFile()
                : new File(System.getProperty("user.dir", ".")).getAbsoluteFile();

        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setBackground(DesktopUiKit.C_HEADER);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));

        cwdLabel = new JLabel();
        cwdLabel.setFont(DesktopUiKit.FONT_MONO);
        cwdLabel.setForeground(DesktopUiKit.C_WHITE);
        cwdLabel.setMinimumSize(new Dimension(60, 22));
        updateCwdLabel();
        topBar.add(cwdLabel, BorderLayout.CENTER);

        JPanel quickBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        quickBox.setOpaque(false);

        DesktopUiKit.VortexButton javaVerBtn = new DesktopUiKit.VortexButton("Java", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.TERMINAL);
        javaVerBtn.setToolTipText("Run java -version");
        javaVerBtn.addActionListener(e -> runCommand("java -version"));

        DesktopUiKit.VortexButton goVerBtn = new DesktopUiKit.VortexButton("Go", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CODE);
        goVerBtn.setToolTipText("Run go version");
        goVerBtn.addActionListener(e -> runCommand("go version"));

        DesktopUiKit.VortexButton lrmBtn = new DesktopUiKit.VortexButton("LRM", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.SYNC);
        lrmBtn.setToolTipText("Run lrm version / status");
        lrmBtn.addActionListener(e -> {
            String bin = LrmWorkspaceBridge.findLrmBinary();
            if (bin != null) {
                runCommand("\"" + bin + "\" version");
            } else if (this.onSetupLrmRequested != null) {
                this.onSetupLrmRequested.run();
            } else {
                runCommand(isWindows() ? LrmWorkspaceBridge.LRM_WINDOWS_INSTALL_PS1 : LrmWorkspaceBridge.LRM_DESKTOP_INSTALL_SH);
            }
        });

        DesktopUiKit.VortexButton setupLrmBtn = new DesktopUiKit.VortexButton("Setup LRM", DesktopUiKit.Variant.ACCENT, DesktopUiKit.Icon.SPARKLE);
        setupLrmBtn.setToolTipText("Auto-install & setup LRM workspace");
        setupLrmBtn.addActionListener(e -> {
            if (this.onSetupLrmRequested != null) {
                this.onSetupLrmRequested.run();
            } else {
                runCommand(isWindows() ? LrmWorkspaceBridge.LRM_WINDOWS_INSTALL_PS1 : LrmWorkspaceBridge.LRM_DESKTOP_INSTALL_SH);
            }
        });

        DesktopUiKit.VortexButton lsBtn = new DesktopUiKit.VortexButton(isWindows() ? "Dir" : "Files", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.FOLDER);
        lsBtn.addActionListener(e -> runCommand(isWindows() ? "Get-ChildItem -Force" : "ls -la"));

        stopBtn = new DesktopUiKit.VortexButton("Stop", DesktopUiKit.Variant.DANGER, DesktopUiKit.Icon.STOP);
        stopBtn.setEnabled(false);
        stopBtn.addActionListener(e -> killActiveProcess());

        DesktopUiKit.VortexButton clearBtn = new DesktopUiKit.VortexButton("Clear", DesktopUiKit.Variant.SECONDARY, DesktopUiKit.Icon.CLOSE);
        clearBtn.addActionListener(e -> outputArea.clearLogs());

        quickBox.add(statusPill);
        quickBox.add(javaVerBtn);
        quickBox.add(goVerBtn);
        quickBox.add(lrmBtn);
        quickBox.add(setupLrmBtn);
        quickBox.add(lsBtn);
        quickBox.add(stopBtn);
        quickBox.add(clearBtn);
        topBar.add(quickBox, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        outputArea.appendColoredLine("✓ Shell Ready (" + (isWindows() ? "PowerShell" : "POSIX sh") + ")");

        JScrollPane scroll = DesktopUiKit.createScrollPane(outputArea);
        add(scroll, BorderLayout.CENTER);

        JPanel inputBar = new JPanel(new BorderLayout(8, 0));
        inputBar.setBackground(DesktopUiKit.C_HEADER);
        inputBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DesktopUiKit.C_BORDER),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));

        JLabel promptGlyph = new JLabel(isWindows() ? "PS>" : "$");
        promptGlyph.setFont(DesktopUiKit.FONT_MONO);
        promptGlyph.setForeground(DesktopUiKit.C_WHITE);
        inputBar.add(promptGlyph, BorderLayout.WEST);

        inputField = DesktopUiKit.createField("");
        inputField.setFont(DesktopUiKit.FONT_MONO);
        inputField.setPreferredSize(new Dimension(320, 28));
        inputField.addActionListener(e -> submitTypedCommand());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_UP && !history.isEmpty()) {
                    historyIdx = Math.max(0, historyIdx - 1);
                    inputField.setText(history.get(historyIdx));
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN && !history.isEmpty()) {
                    historyIdx = Math.min(history.size(), historyIdx + 1);
                    inputField.setText(historyIdx < history.size() ? history.get(historyIdx) : "");
                    e.consume();
                }
            }
        });
        inputBar.add(inputField, BorderLayout.CENTER);

        DesktopUiKit.VortexButton runBtn = new DesktopUiKit.VortexButton("Run", DesktopUiKit.Variant.PRIMARY, DesktopUiKit.Icon.PLAY);
        runBtn.addActionListener(e -> submitTypedCommand());
        inputBar.add(runBtn, BorderLayout.EAST);

        add(inputBar, BorderLayout.SOUTH);
    }

    public void setWorkingDirectory(File dir) {
        if (dir != null && dir.isDirectory()) {
            this.cwd = dir.getAbsoluteFile();
            updateCwdLabel();
        }
    }

    public File getWorkingDirectory() {
        return cwd;
    }

    public String getOutputText() {
        return outputArea.getText();
    }

    public void appendExternalLog(final String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendLine(text);
        } else {
            SwingUtilities.invokeLater(() -> appendLine(text));
        }
    }

    public void focusInput() {
        inputField.requestFocusInWindow();
    }

    private void submitTypedCommand() {
        String raw = inputField.getText();
        if (raw == null) return;
        String cmd = raw.trim();
        inputField.setText("");
        if (cmd.isEmpty()) return;
        history.add(cmd);
        historyIdx = history.size();
        runCommand(cmd);
    }

    public void runCommand(String rawCmd) {
        if (rawCmd == null || rawCmd.trim().isEmpty()) return;
        final String cmd = rawCmd.trim();

        appendLine("\n" + shortCwd() + (isWindows() ? " PS> " : " $ ") + cmd);

        if ("clear".equalsIgnoreCase(cmd) || "cls".equalsIgnoreCase(cmd)) {
            outputArea.clearLogs();
            statusPill.setStatus("● Cleared", DesktopUiKit.StatusPill.State.NEUTRAL);
            return;
        }
        if ("pwd".equalsIgnoreCase(cmd)) {
            appendLine(cwd.getAbsolutePath());
            return;
        }
        if ("help".equalsIgnoreCase(cmd)) {
            appendLine("Built-ins: cd <dir>, pwd, clear / cls, help. All other commands execute via "
                    + (isWindows() ? "Windows PowerShell" : "/bin/sh") + " with Java, Go, and LRM on PATH.");
            return;
        }
        if (cmd.equals("cd") || cmd.startsWith("cd ")) {
            String target = cmd.length() <= 2 ? System.getProperty("user.home", ".") : cmd.substring(3).trim();
            if (target.startsWith("\"") && target.endsWith("\"") && target.length() >= 2) {
                target = target.substring(1, target.length() - 1);
            }
            if ("~".equals(target)) {
                target = System.getProperty("user.home", ".");
            } else if (target.startsWith("~/") || target.startsWith("~\\")) {
                target = new File(System.getProperty("user.home", "."), target.substring(2)).getAbsolutePath();
            }
            File cand = new File(target);
            if (!cand.isAbsolute()) cand = new File(cwd, target);
            try {
                cand = cand.getCanonicalFile();
            } catch (Exception ignored) {
                cand = cand.getAbsoluteFile();
            }
            if (cand.isDirectory()) {
                cwd = cand;
                updateCwdLabel();
                appendLine("✓ [SUCCESS] cwd -> " + cwd.getAbsolutePath());
                statusPill.setStatus("✓ cd OK", DesktopUiKit.StatusPill.State.SUCCESS);
            } else {
                appendLine("✕ [ERROR] cd: directory not found: " + cand.getAbsolutePath());
                statusPill.setStatus("✕ Not Found", DesktopUiKit.StatusPill.State.ERROR);
            }
            return;
        }

        if (activeProcess != null && activeProcess.isAlive()) {
            appendLine("[WARN] Another command is still running — click Stop first.");
            return;
        }

        stopBtn.setEnabled(true);
        statusPill.setStatus("● Running…", DesktopUiKit.StatusPill.State.WARNING);
        Thread worker = new Thread(() -> {
            try {
                ProcessBuilder pb;
                if (isWindows()) {
                    pb = new ProcessBuilder(
                            "powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", cmd);
                } else {
                    pb = new ProcessBuilder("/bin/sh", "-c", cmd);
                }
                pb.directory(cwd);
                String enriched = LrmWorkspaceBridge.buildEnrichedPath();
                Map<String, String> env = pb.environment();
                env.put("PATH", enriched);
                if (isWindows()) {
                    env.put("Path", enriched);
                }
                pb.redirectErrorStream(true);
                Process proc;
                try {
                    proc = pb.start();
                } catch (Exception winFallback) {
                    if (isWindows()) {
                        ProcessBuilder cmdPb = new ProcessBuilder("cmd.exe", "/c", cmd);
                        cmdPb.directory(cwd);
                        cmdPb.environment().put("Path", enriched);
                        cmdPb.redirectErrorStream(true);
                        proc = cmdPb.start();
                    } else {
                        throw winFallback;
                    }
                }
                activeProcess = proc;
                proc.getOutputStream().close();

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        final String outLine = line;
                        SwingUtilities.invokeLater(() -> appendLine(outLine));
                    }
                }
                int code = proc.waitFor();
                if (code == 0) {
                    SwingUtilities.invokeLater(() -> {
                        statusPill.setStatus("✓ Exit 0 • OK", DesktopUiKit.StatusPill.State.SUCCESS);
                        appendLine("✓ [SUCCESS] Command finished (exit 0)");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        statusPill.setStatus("✕ Exit " + code, DesktopUiKit.StatusPill.State.ERROR);
                        appendLine("✕ [ERROR] Command failed (exit " + code + ")");
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusPill.setStatus("✕ Exec Error", DesktopUiKit.StatusPill.State.ERROR);
                    appendLine("✕ [ERROR] " + ex.getMessage());
                });
            } finally {
                activeProcess = null;
                SwingUtilities.invokeLater(() -> stopBtn.setEnabled(false));
            }
        }, "vortex-terminal-exec");
        worker.setDaemon(true);
        worker.start();
    }

    public void killActiveProcess() {
        Process p = activeProcess;
        if (p != null && p.isAlive()) {
            p.destroyForcibly();
            appendLine("[WARN] Process terminated by user.");
            statusPill.setStatus("■ Stopped", DesktopUiKit.StatusPill.State.WARNING);
        }
        stopBtn.setEnabled(false);
    }

    private void appendLine(String text) {
        outputArea.appendColoredLine(text);
    }

    private void updateCwdLabel() {
        cwdLabel.setText("CWD: " + shortCwd());
        cwdLabel.setToolTipText(cwd.getAbsolutePath());
    }

    private String shortCwd() {
        String p = cwd.getAbsolutePath();
        String home = System.getProperty("user.home", "");
        if (!home.isEmpty() && p.startsWith(home)) {
            p = "~" + p.substring(home.length());
        }
        if (p.length() > 42) {
            p = "..." + p.substring(p.length() - 39);
        }
        return p;
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }
}
