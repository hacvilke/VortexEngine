package com.vortex.engine.core.lrm;

import com.vortex.engine.core.flow.VortexFlowCompiler;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.storage.VortexSaveStore;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Native Integration with LRM (Log Replication Manager — github.com/hacvilke/lrm & hacvilke/lrm-mobile).
 *
 * v4.2 Upgrades:
 *  - Full System Toolchain & Dependency Prober (detects LRM, Go / golang, PowerShell, curl, Git, Java)
 *  - Windows %LOCALAPPDATA%\Programs\lrm\lrm.exe + Go GOBIN path discovery & enriched PATH injection
 *  - One-Click Auto-Installer (`irm https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.ps1 | iex`
 *    on Windows, `install.sh` on Linux/macOS, `lrm-mobile` on Termux, plus `go install` source-build fallback!)
 *  - Non-deadlocking concurrent stream reader + Managed Background Services (`lrm daemon`, `lrm receive`, `lrm dashboard`)
 */
public class LrmWorkspaceBridge {

    public static final String PREFS_KEY = "__vortex_lrm_prefs__";

    public static final String LRM_DESKTOP_INSTALL_SH =
            "curl -fsSL https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.sh | sh";
    public static final String LRM_WINDOWS_INSTALL_PS1 =
            "irm https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.ps1 | iex";
    public static final String LRM_MOBILE_TERMUX_INSTALL_SH =
            "curl -fsSL https://raw.githubusercontent.com/hacvilke/lrm-mobile/main/scripts/install.sh | sh";
    public static final String LRM_GO_SOURCE_INSTALL =
            "go install github.com/hacvilke/lrm/cmd/lrm@latest";

    private static final List<Process> BACKGROUND_SERVICES =
            Collections.synchronizedList(new ArrayList<Process>());

    public interface LogListener {
        void onLog(String line);
    }

    public static class CmdResult {
        public final int exitCode;
        public final String output;
        public CmdResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    public static class SystemProbe {
        public boolean isWindows;
        public boolean isAndroidTermux;
        public String osName = "";
        public String osArch = "";
        public String lrmBin;
        public String lrmVersion = "";
        public String goBin;
        public String goVersion = "";
        public String javaBin;
        public String javaVersion = "";
        public String shellInstallerTool = "";
        public String gitVersion = "";

        public String toSummaryReport() {
            StringBuilder sb = new StringBuilder();
            sb.append("• Platform     : ").append(osName).append(" (").append(osArch).append(")\n");
            sb.append("• LRM Binary   : ").append(lrmBin != null
                    ? lrmVersion + "  [" + lrmBin + "]"
                    : "NOT INSTALLED (ready for 1-click auto-setup)").append('\n');
            sb.append("• Go (golang)  : ").append(goBin != null
                    ? goVersion + "  [" + goBin + "]"
                    : "Not detected (optional — LRM ships a zero-dep prebuilt binary)").append('\n');
            sb.append("• Java Runtime : ").append(javaVersion.isEmpty() ? "Active JVM" : javaVersion).append('\n');
            sb.append("• Installer    : ").append(shellInstallerTool).append('\n');
            if (!gitVersion.isEmpty()) {
                sb.append("• Git CLI      : ").append(gitVersion).append('\n');
            }
            return sb.toString();
        }
    }

    public static boolean isAutoPromptDismissed() {
        return "1".equals(VortexSaveStore.loadString(PREFS_KEY, "skipStartupPrompt", "0"));
    }

    public static void setAutoPromptDismissed(boolean skip) {
        VortexSaveStore.save(PREFS_KEY, "skipStartupPrompt", skip ? "1" : "0");
    }

    /**
     * Inspects the host machine for LRM, Go (golang), Java, PowerShell/curl, and Git.
     */
    public static SystemProbe probeSystem() {
        SystemProbe p = new SystemProbe();
        p.osName = System.getProperty("os.name", "Unknown");
        p.osArch = System.getProperty("os.arch", "amd64");
        p.isWindows = p.osName.toLowerCase(Locale.US).contains("win");
        p.isAndroidTermux = new File("/data/data/com.termux/files/usr").exists();

        p.lrmBin = findLrmBinary();
        if (p.lrmBin != null) {
            CmdResult ver = runExecQuick(new String[]{p.lrmBin, "version"}, 3);
            p.lrmVersion = ver.output.isEmpty() ? "installed" : firstLine(ver.output);
        }

        p.goBin = findGoBinary(p.isWindows);
        if (p.goBin != null) {
            CmdResult gv = runExecQuick(new String[]{p.goBin, "version"}, 3);
            p.goVersion = firstLine(gv.output);
        }

        String javaHome = System.getProperty("java.home", "");
        File jExe = new File(javaHome, "bin/" + (p.isWindows ? "java.exe" : "java"));
        p.javaBin = jExe.isFile() ? jExe.getAbsolutePath() : (p.isWindows ? "java.exe" : "java");
        p.javaVersion = "Java " + System.getProperty("java.version", "17")
                + " (" + System.getProperty("java.vendor", "OpenJDK") + ")";

        if (p.isWindows) {
            p.shellInstallerTool = "Windows PowerShell (`irm .../install.ps1 | iex`)";
        } else if (p.isAndroidTermux) {
            p.shellInstallerTool = "Termux (`curl -fsSL .../lrm-mobile/.../install.sh | sh`)";
        } else {
            p.shellInstallerTool = "POSIX Shell (`curl -fsSL .../install.sh | sh`)";
        }

        CmdResult gitRes = runExecQuick(new String[]{p.isWindows ? "git.exe" : "git", "--version"}, 2);
        if (gitRes.exitCode == 0 && !gitRes.output.isEmpty()) {
            p.gitVersion = firstLine(gitRes.output);
        }
        return p;
    }

    /**
     * Builds an enriched PATH string containing the active JDK/JRE bin, Windows %LOCALAPPDATA%\Programs\lrm,
     * Go bin directories, and ~/.local/bin so terminal & LRM subprocesses always resolve tools immediately!
     */
    public static String buildEnrichedPath() {
        boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
        String sep = File.pathSeparator;
        String home = System.getProperty("user.home", ".");
        String javaHome = System.getProperty("java.home", "");

        List<String> extra = new ArrayList<>();
        if (!javaHome.isEmpty()) {
            extra.add(new File(javaHome, "bin").getAbsolutePath());
        }
        if (isWin) {
            String localApp = System.getenv("LOCALAPPDATA");
            if (localApp != null && !localApp.isEmpty()) {
                extra.add(new File(localApp, "Programs\\lrm").getAbsolutePath());
                extra.add(new File(localApp, "Programs\\Go\\bin").getAbsolutePath());
            }
            extra.add(new File(home, ".local\\bin").getAbsolutePath());
            extra.add(new File(home, "go\\bin").getAbsolutePath());
            extra.add("C:\\Program Files\\Go\\bin");
        } else {
            extra.add(new File(home, ".local/bin").getAbsolutePath());
            extra.add(new File(home, "bin").getAbsolutePath());
            extra.add(new File(home, "go/bin").getAbsolutePath());
            extra.add("/tmp/lrm_bin");
            extra.add("/usr/local/go/bin");
            extra.add("/usr/local/bin");
        }

        StringBuilder sb = new StringBuilder();
        for (String d : extra) {
            if (d != null && !d.isEmpty() && new File(d).isDirectory()) {
                if (sb.length() > 0) sb.append(sep);
                sb.append(d);
            }
        }
        String sysPath = System.getenv("PATH");
        if (sysPath == null || sysPath.isEmpty()) sysPath = System.getenv("Path");
        if (sysPath != null && !sysPath.isEmpty()) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(sysPath);
        }
        return sb.toString();
    }

    public static String findGoBinary(boolean isWin) {
        String home = System.getProperty("user.home", ".");
        String exe = isWin ? "go.exe" : "go";
        List<String> cands = new ArrayList<>();
        if (isWin) {
            String localApp = System.getenv("LOCALAPPDATA");
            cands.add("C:\\Program Files\\Go\\bin\\go.exe");
            if (localApp != null) cands.add(new File(localApp, "Programs\\Go\\bin\\go.exe").getAbsolutePath());
            cands.add(new File(home, "sdk\\go\\bin\\go.exe").getAbsolutePath());
        } else {
            cands.add("/usr/local/go/bin/go");
            cands.add("/opt/go/bin/go");
            cands.add(new File(home, "go/bin/go").getAbsolutePath());
            cands.add("/usr/bin/go");
        }
        cands.add(exe);

        for (String c : cands) {
            CmdResult r = runExecQuick(new String[]{c, "version"}, 3);
            if (r.exitCode == 0 && r.output.toLowerCase(Locale.US).contains("go version")) {
                return c;
            }
        }
        return null;
    }

    public static String findLrmBinary() {
        String home = System.getProperty("user.home", ".");
        boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
        String binName = isWin ? "lrm.exe" : "lrm";

        List<String> candidates = new ArrayList<>();
        if (isWin) {
            String localApp = System.getenv("LOCALAPPDATA");
            if (localApp != null && !localApp.isEmpty()) {
                candidates.add(new File(localApp, "Programs\\lrm\\lrm.exe").getAbsolutePath());
            }
            candidates.add(new File(home, ".vortex-tools\\lrm\\lrm.exe").getAbsolutePath());
            candidates.add(new File(home, ".local\\bin\\lrm.exe").getAbsolutePath());
            candidates.add(new File(home, "go\\bin\\lrm.exe").getAbsolutePath());
        } else {
            candidates.add(new File(home, ".local/bin/lrm").getAbsolutePath());
            candidates.add(new File(home, "bin/lrm").getAbsolutePath());
            candidates.add(new File(home, "go/bin/lrm").getAbsolutePath());
            candidates.add("/data/data/com.termux/files/home/.local/bin/lrm");
            candidates.add("/tmp/lrm_bin/lrm");
            candidates.add("/usr/local/bin/lrm");
        }
        candidates.add(binName);

        for (String c : candidates) {
            if (c.contains(File.separator) && !new File(c).isFile()) continue;
            CmdResult ver = runExecQuick(new String[]{c, "version"}, 3);
            String low = ver.output.toLowerCase(Locale.US);
            // Guard against unrelated npm 'lrm' package that throws MODULE_NOT_FOUND
            if (low.contains("module_not_found") || low.contains("cannot find module")) continue;
            if (ver.exitCode == 0 && low.contains("lrm")) {
                return c;
            }
            CmdResult hlp = runExecQuick(new String[]{c, "--help"}, 3);
            String hlow = hlp.output.toLowerCase(Locale.US);
            if (!hlow.contains("module_not_found")
                    && (hlow.contains("log replication manager") || hlow.contains("p2p mesh"))) {
                return c;
            }
        }
        return null;
    }

    /**
     * Checks host dependencies (OS, Arch, Go / golang, PowerShell / curl), downloads & verifies
     * LRM via official installer (`irm .../install.ps1 | iex` on Windows or `install.sh` on POSIX,
     * with automatic `go install` source-build fallback when Go is installed!), and initializes the
     * active Vortex game workspace!
     */
    public static boolean autoInstallAndSetupLrm(GameProject project, File repoDir, LogListener log) {
        emit(log, "=== Vortex Studio — LRM Dependency & Auto-Setup Wizard ===");
        SystemProbe probe = probeSystem();
        for (String ln : probe.toSummaryReport().split("\n")) {
            if (!ln.trim().isEmpty()) emit(log, ln);
        }

        if (probe.goBin != null) {
            emit(log, "[✓] Go toolchain detected (" + probe.goVersion + ") — source-build fallback ready if needed.");
        } else {
            emit(log, "[i] Go not installed — using official SHA-256 verified prebuilt LRM release binary.");
        }

        String bin = probe.lrmBin;
        if (bin == null) {
            if (probe.isWindows) {
                emit(log, "\n[1/3] Running Windows PowerShell LRM Installer (`irm https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.ps1`)...");
                String psCmd = "[Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12; "
                        + "$script = irm 'https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.ps1' -UseBasicParsing; "
                        + "& ([scriptblock]::Create($script)) -Force";
                runStreamingCommand(new String[]{
                        "powershell.exe", "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", psCmd
                }, null, 120, log);
            } else if (probe.isAndroidTermux) {
                emit(log, "\n[1/3] Running Termux PIE LRM Mobile Installer (`hacvilke/lrm-mobile`)...");
                runStreamingCommand(new String[]{"/bin/sh", "-c", LRM_MOBILE_TERMUX_INSTALL_SH}, null, 120, log);
            } else {
                emit(log, "\n[1/3] Running POSIX LRM Installer (`hacvilke/lrm/scripts/install.sh`)...");
                runStreamingCommand(new String[]{"/bin/sh", "-c",
                        "curl -fsSL https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.sh | sh -s -- --force || "
                        + LRM_DESKTOP_INSTALL_SH}, null, 120, log);
            }

            bin = findLrmBinary();

            // Fallback: if binary download failed/unsupported arch AND Go (golang) is installed on system, compile from source!
            if (bin == null && probe.goBin != null) {
                emit(log, "\n[Fallback] Building `hacvilke/lrm` from pure Go stdlib source via `" + probe.goBin + "`...");
                File targetDir = probe.isWindows && System.getenv("LOCALAPPDATA") != null
                        ? new File(System.getenv("LOCALAPPDATA"), "Programs\\lrm")
                        : new File(System.getProperty("user.home", "."), ".local/bin");
                targetDir.mkdirs();
                try {
                    ProcessBuilder pb = new ProcessBuilder(probe.goBin, "install", "github.com/hacvilke/lrm/cmd/lrm@latest");
                    pb.environment().put("GOBIN", targetDir.getAbsolutePath());
                    pb.environment().put("PATH", buildEnrichedPath());
                    pb.redirectErrorStream(true);
                    Process gp = pb.start();
                    streamProcessLines(gp, log);
                    gp.waitFor(180, TimeUnit.SECONDS);
                } catch (Exception ex) {
                    emit(log, "[Go Build Warning] " + ex.getMessage());
                }
                bin = findLrmBinary();
            }
        } else {
            emit(log, "\n[1/3] LRM is already installed at: " + bin + " (" + probe.lrmVersion + ")");
        }

        if (bin == null) {
            emit(log, "\n[✕] Could not verify `lrm` binary after installation. Check internet connection or run `"
                    + (probe.isWindows ? LRM_WINDOWS_INSTALL_PS1 : LRM_DESKTOP_INSTALL_SH) + "` in the Studio Terminal.");
            return false;
        }

        CmdResult verCheck = runExecQuick(new String[]{bin, "version"}, 5);
        emit(log, "[2/3] Verified LRM executable: " + firstLine(verCheck.output) + " (" + bin + ")");

        if (project != null && repoDir != null) {
            try {
                emit(log, "[3/3] Unpacking & initializing Vortex workspace -> " + repoDir.getAbsolutePath());
                exportToLrmRepo(project, repoDir);
                File ignore = new File(repoDir, ".lrmignore");
                if (!ignore.exists()) {
                    writeUtf8(ignore, "*.tmp\n*.bak\n.DS_Store\nThumbs.db\n");
                }
                if (!new File(repoDir, ".lrm").exists()) {
                    String user = sanitizeName(System.getProperty("user.name", "creator"));
                    CmdResult ir = runLrm(repoDir, 10, "init", "--user", user);
                    emit(log, "$ lrm init --user " + user + "\n" + ir.output);
                }
                CmdResult cr = runLrm(repoDir, 12, "commit", "-m", "Initialize Vortex Studio workspace");
                if (!cr.output.isEmpty()) emit(log, "$ lrm commit\n" + cr.output);
                CmdResult sr = runLrm(repoDir, 8, "status");
                if (!sr.output.isEmpty()) emit(log, "$ lrm status\n" + sr.output);
            } catch (Exception ex) {
                emit(log, "[Workspace Warning] " + ex.getMessage());
            }
        }
        emit(log, "\n[✓] LRM P2P Version Control & Mesh Sync is 100% READY!");
        return true;
    }

    /**
     * Starts a long-lived background LRM mesh service (`daemon`, `receive`, or `dashboard`)
     * and streams its live output without blocking Studio!
     */
    public static Process startBackgroundService(File repoDir, LogListener log, String... args) throws Exception {
        String bin = findLrmBinary();
        if (bin == null) {
            throw new IllegalStateException("LRM is not installed yet — click 'Auto-Install / Setup LRM' first.");
        }
        if (repoDir != null && !repoDir.exists()) repoDir.mkdirs();
        List<String> cmd = new ArrayList<>();
        cmd.add(bin);
        cmd.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (repoDir != null) pb.directory(repoDir);
        pb.environment().put("PATH", buildEnrichedPath());
        pb.redirectErrorStream(true);
        final Process proc = pb.start();
        proc.getOutputStream().close();
        BACKGROUND_SERVICES.add(proc);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                streamProcessLines(proc, log);
                BACKGROUND_SERVICES.remove(proc);
            }
        }, "lrm-bg-service");
        t.setDaemon(true);
        t.start();
        return proc;
    }

    public static int stopBackgroundServices() {
        int stopped = 0;
        synchronized (BACKGROUND_SERVICES) {
            for (Process p : BACKGROUND_SERVICES) {
                if (p != null && p.isAlive()) {
                    p.destroy();
                    stopped++;
                }
            }
            BACKGROUND_SERVICES.clear();
        }
        return stopped;
    }

    public static int getActiveBackgroundServiceCount() {
        int alive = 0;
        synchronized (BACKGROUND_SERVICES) {
            for (Process p : BACKGROUND_SERVICES) {
                if (p != null && p.isAlive()) alive++;
            }
        }
        return alive;
    }

    /**
     * Unpacks a GameProject into diff-friendly files inside `repoDir`.
     */
    public static void exportToLrmRepo(GameProject p, File repoDir) throws Exception {
        if (!repoDir.exists()) repoDir.mkdirs();
        new File(repoDir, "scene").mkdirs();
        new File(repoDir, "scripts").mkdirs();
        new File(repoDir, "flow").mkdirs();
        new File(repoDir, "assets/sprites").mkdirs();
        new File(repoDir, "assets/meshes").mkdirs();
        new File(repoDir, "inbox").mkdirs();

        JSONObject manifest = new JSONObject();
        try {
            manifest.put("id", p.id);
            manifest.put("title", p.title);
            manifest.put("packageName", p.packageName);
            manifest.put("versionName", p.versionName);
            manifest.put("versionCode", p.versionCode);
            manifest.put("orientation", p.orientation);
            manifest.put("dimensionMode", p.dimensionMode);
            manifest.put("scriptLanguage", p.scriptLanguage);
            manifest.put("useBox2D", p.useBox2D);
            manifest.put("terrainSeed", p.terrainSeed);
            manifest.put("terrainFreq", p.terrainFreq);
            manifest.put("musicEnabled", p.musicEnabled);
            manifest.put("musicBpm", p.musicBpm);
            manifest.put("musicPattern", p.musicPattern);
            manifest.put("iconBgHex", p.iconBgHex);
            manifest.put("iconAccentHex", p.iconAccentHex);
            manifest.put("iconGlyph", p.iconGlyph);
            manifest.put("skyColorHex", p.skyColorHex);
            manifest.put("ambientColorHex", p.ambientColorHex);
            manifest.put("fogEnabled", p.fogEnabled);
            manifest.put("fogNear", p.fogNear);
            manifest.put("fogFar", p.fogFar);
            manifest.put("gravityX", p.gravityX);
            manifest.put("gravityY", p.gravityY);
            manifest.put("gravityZ", p.gravityZ);
            manifest.put("cameraMode", p.cameraMode);
            manifest.put("cameraTargetName", p.cameraTargetName);
            manifest.put("cameraDistance", p.cameraDistance);
            manifest.put("cameraHeight", p.cameraHeight);
            manifest.put("cameraFov", p.cameraFov);
            manifest.put("zoom2D", p.zoom2D);
            writeUtf8(new File(repoDir, "vortex.manifest.json"), manifest.toString(2));
        } catch (JSONException ignored) {}

        JSONArray ents = new JSONArray();
        for (Entity e : p.entities) {
            ents.put(e.toJson());
        }
        try {
            writeUtf8(new File(repoDir, "scene/entities.json"), ents.toString(2));
        } catch (JSONException ignored) {}

        p.ensureScriptFilesInitialized();
        for (Map.Entry<String, String> sf : p.scriptFiles.entrySet()) {
            String rel = GameProject.normalizeScriptPath(sf.getKey());
            if (!rel.startsWith("scripts/")) rel = "scripts/" + rel;
            writeUtf8(new File(repoDir, rel), sf.getValue() == null ? "" : sf.getValue());
        }

        try {
            writeUtf8(new File(repoDir, "flow/rules.json"), VortexFlowCompiler.toJsonArray(p.flowRules).toString(2));
            if (!p.blueprintNodes.isEmpty() || !p.blueprintLinks.isEmpty()) {
                JSONObject bp = new JSONObject();
                bp.put("nodes", com.vortex.engine.core.flow.VortexBlueprintGraph.nodesToJson(p.blueprintNodes));
                bp.put("links", com.vortex.engine.core.flow.VortexBlueprintGraph.linksToJson(p.blueprintLinks));
                writeUtf8(new File(repoDir, "flow/blueprint.json"), bp.toString(2));
            }
        } catch (JSONException ignored) {}

        for (Map.Entry<String, String> sp : p.customSprites.entrySet()) {
            String safe = sanitizeName(sp.getKey());
            writeUtf8(new File(repoDir, "assets/sprites/" + safe + ".sprite"), sp.getValue());
        }
        for (Map.Entry<String, String> mesh : p.customObjMeshes.entrySet()) {
            String safe = sanitizeName(mesh.getKey());
            writeUtf8(new File(repoDir, "assets/meshes/" + safe + ".obj"), mesh.getValue());
        }
    }

    /**
     * Reconstructs a GameProject from an unpacked LRM repository directory AND ingests any assets
     * dropped into `inbox/` via `lrm receive` or `lrm send`!
     */
    public static GameProject importFromLrmRepo(File repoDir) throws Exception {
        File manifestFile = new File(repoDir, "vortex.manifest.json");
        if (!manifestFile.exists()) {
            File[] fallbacks = repoDir.listFiles((d, n) -> n.endsWith(".json"));
            if (fallbacks != null && fallbacks.length > 0) {
                return GameProject.fromJsonString(readUtf8(fallbacks[0]));
            }
            throw new IllegalArgumentException("No vortex.manifest.json found in " + repoDir.getAbsolutePath());
        }

        JSONObject m = new JSONObject(readUtf8(manifestFile));
        GameProject p = new GameProject();
        p.id = m.optString("id", p.id);
        p.title = m.optString("title", "LRM Synced Game");
        p.packageName = m.optString("packageName", "com.vortex.game.synced");
        p.versionName = m.optString("versionName", "1.0.0");
        p.versionCode = m.optInt("versionCode", 1);
        p.orientation = m.optString("orientation", "landscape");
        p.dimensionMode = m.optString("dimensionMode", "2D");
        p.scriptLanguage = m.optString("scriptLanguage", "JS");
        p.useBox2D = m.optBoolean("useBox2D", true);
        p.terrainSeed = m.optInt("terrainSeed", 1337);
        p.terrainFreq = (float) m.optDouble("terrainFreq", 0.065);
        p.musicEnabled = m.optBoolean("musicEnabled", false);
        p.musicBpm = m.optInt("musicBpm", 128);
        p.musicPattern = m.optString("musicPattern", p.musicPattern);
        p.iconBgHex = m.optString("iconBgHex", "#101013");
        p.iconAccentHex = m.optString("iconAccentHex", "#FFFFFF");
        p.iconGlyph = m.optString("iconGlyph", "CUBE");
        p.skyColorHex = m.optString("skyColorHex", "#0B0B0E");
        p.ambientColorHex = m.optString("ambientColorHex", "#2A2A30");
        p.fogEnabled = m.optBoolean("fogEnabled", true);
        p.fogNear = (float) m.optDouble("fogNear", 18.0);
        p.fogFar = (float) m.optDouble("fogFar", 68.0);
        p.gravityX = (float) m.optDouble("gravityX", 0.0);
        p.gravityY = (float) m.optDouble("gravityY", -22.0);
        p.gravityZ = (float) m.optDouble("gravityZ", 0.0);
        p.cameraMode = m.optString("cameraMode", "FOLLOW");
        p.cameraTargetName = m.optString("cameraTargetName", "Player");
        p.cameraDistance = (float) m.optDouble("cameraDistance", 11.0);
        p.cameraHeight = (float) m.optDouble("cameraHeight", 5.5);
        p.cameraFov = (float) m.optDouble("cameraFov", 60.0);
        p.zoom2D = (float) m.optDouble("zoom2D", 18.0);

        File entFile = new File(repoDir, "scene/entities.json");
        if (entFile.exists()) {
            JSONArray arr = new JSONArray(readUtf8(entFile));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject eo = arr.optJSONObject(i);
                if (eo != null) p.entities.add(Entity.fromJson(eo));
            }
        }

        File scriptsRoot = new File(repoDir, "scripts");
        if (scriptsRoot.exists() && scriptsRoot.isDirectory()) {
            collectScriptFilesRecursive(scriptsRoot, "scripts", p);
        }
        p.ensureScriptFilesInitialized();
        p.scriptCode = p.getActiveScriptContent();

        File flowFile = new File(repoDir, "flow/rules.json");
        if (flowFile.exists()) {
            p.flowRules.addAll(VortexFlowCompiler.fromJsonArray(new JSONArray(readUtf8(flowFile))));
        }
        File bpFile = new File(repoDir, "flow/blueprint.json");
        if (bpFile.exists()) {
            JSONObject bpo = new JSONObject(readUtf8(bpFile));
            p.blueprintNodes.addAll(com.vortex.engine.core.flow.VortexBlueprintGraph.nodesFromJson(bpo.optJSONArray("nodes")));
            p.blueprintLinks.addAll(com.vortex.engine.core.flow.VortexBlueprintGraph.linksFromJson(bpo.optJSONArray("links")));
        }

        File spDir = new File(repoDir, "assets/sprites");
        File[] spFiles = spDir.listFiles((d, n) -> n.endsWith(".sprite"));
        if (spFiles != null) {
            for (File sf : spFiles) {
                String key = sf.getName().substring(0, sf.getName().length() - 7);
                p.customSprites.put(key, readUtf8(sf).trim());
            }
        }

        File meshDir = new File(repoDir, "assets/meshes");
        File[] meshFiles = meshDir.listFiles((d, n) -> n.endsWith(".obj"));
        if (meshFiles != null) {
            for (File mf : meshFiles) {
                String key = mf.getName().substring(0, mf.getName().length() - 4);
                p.customObjMeshes.put(key, readUtf8(mf));
            }
        }

        ingestInboxAssets(p, new File(repoDir, "inbox"));
        return p;
    }

    public static int ingestInboxAssets(GameProject p, File inboxDir) {
        if (p == null || inboxDir == null || !inboxDir.exists()) return 0;
        File[] dropped = inboxDir.listFiles();
        if (dropped == null) return 0;
        int imported = 0;
        for (File f : dropped) {
            if (!f.isFile()) continue;
            try {
                String nm = f.getName();
                int dot = nm.lastIndexOf('.');
                String stem = dot > 0 ? sanitizeName(nm.substring(0, dot)) : sanitizeName(nm);
                if (nm.endsWith(".obj")) {
                    p.customObjMeshes.put(stem, readUtf8(f));
                    imported++;
                } else if (nm.endsWith(".sprite")) {
                    p.customSprites.put(stem, readUtf8(f).trim());
                    imported++;
                } else if (nm.endsWith(".java")) {
                    p.scriptLanguage = "JAVA";
                    p.scriptCode = readUtf8(f);
                    imported++;
                } else if (nm.endsWith(".js")) {
                    p.scriptLanguage = "JS";
                    p.scriptCode = readUtf8(f);
                    imported++;
                } else if (nm.endsWith(".lua")) {
                    p.scriptLanguage = "LUA";
                    p.scriptCode = readUtf8(f);
                    imported++;
                }
            } catch (Exception ignored) {
            }
        }
        return imported;
    }

    public static CmdResult runLrm(File repoDir, int timeoutSec, String... args) {
        String bin = findLrmBinary();
        if (bin == null) {
            return new CmdResult(127, "LRM binary not found yet.\nClick 'Auto-Install & Setup LRM' above, or run:\n  Windows PS : "
                    + LRM_WINDOWS_INSTALL_PS1 + "\n  Linux/macOS: " + LRM_DESKTOP_INSTALL_SH
                    + "\n  Termux     : " + LRM_MOBILE_TERMUX_INSTALL_SH);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(bin);
        cmd.addAll(Arrays.asList(args));
        try {
            if (repoDir != null && !repoDir.exists()) repoDir.mkdirs();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (repoDir != null) pb.directory(repoDir);
            pb.environment().put("PATH", buildEnrichedPath());
            pb.redirectErrorStream(true);
            final Process proc = pb.start();
            proc.getOutputStream().close();

            final StringBuilder outBuf = new StringBuilder();
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                        String ln;
                        while ((ln = br.readLine()) != null) {
                            outBuf.append(ln).append('\n');
                        }
                    } catch (Exception ignored) {}
                }
            }, "lrm-stdout-drain");
            reader.setDaemon(true);
            reader.start();

            boolean finished = proc.waitFor(Math.max(2, timeoutSec), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                reader.join(500);
                return new CmdResult(124, outBuf.toString().trim() + "\n[Timed out after " + timeoutSec + "s]");
            }
            reader.join(1000);
            return new CmdResult(proc.exitValue(), outBuf.toString().trim());
        } catch (Exception ex) {
            return new CmdResult(1, "Failed to invoke LRM: " + ex.getMessage());
        }
    }

    public static String buildTermuxShellSequence(String sharedRepoPath, String subCmd) {
        return "export PATH=\"$HOME/.local/bin:$PATH\"; "
                + "if ! command -v lrm >/dev/null 2>&1; then " + LRM_MOBILE_TERMUX_INSTALL_SH + "; fi; "
                + "mkdir -p \"" + sharedRepoPath + "\" && cd \"" + sharedRepoPath + "\" && " + subCmd;
    }

    private static CmdResult runExecQuick(String[] cmd, int timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            final Process proc = pb.start();
            proc.getOutputStream().close();
            final StringBuilder sb = new StringBuilder();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                        String ln;
                        while ((ln = br.readLine()) != null) sb.append(ln).append('\n');
                    } catch (Exception ignored) {}
                }
            });
            t.setDaemon(true);
            t.start();
            boolean ok = proc.waitFor(Math.max(1, timeoutSec), TimeUnit.SECONDS);
            if (!ok) {
                proc.destroyForcibly();
                return new CmdResult(124, "");
            }
            t.join(400);
            return new CmdResult(proc.exitValue(), sb.toString().trim());
        } catch (Exception e) {
            return new CmdResult(127, "");
        }
    }

    private static int runStreamingCommand(String[] cmd, File dir, int timeoutSec, final LogListener log) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (dir != null && dir.isDirectory()) pb.directory(dir);
            pb.environment().put("PATH", buildEnrichedPath());
            pb.redirectErrorStream(true);
            final Process proc = pb.start();
            proc.getOutputStream().close();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    streamProcessLines(proc, log);
                }
            });
            t.setDaemon(true);
            t.start();
            boolean done = proc.waitFor(Math.max(5, timeoutSec), TimeUnit.SECONDS);
            if (!done) {
                proc.destroyForcibly();
                emit(log, "[Timed out after " + timeoutSec + "s]");
                return 124;
            }
            t.join(1000);
            return proc.exitValue();
        } catch (Exception ex) {
            emit(log, "[Exec Error] " + ex.getMessage());
            return 1;
        }
    }

    private static void streamProcessLines(Process proc, LogListener log) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                emit(log, line);
            }
        } catch (Exception ignored) {}
    }

    private static void emit(LogListener log, String msg) {
        if (log != null && msg != null) log.onLog(msg);
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int nl = s.indexOf('\n');
        return (nl >= 0 ? s.substring(0, nl) : s).trim();
    }

    private static String sanitizeName(String s) {
        if (s == null || s.isEmpty()) return "asset";
        String out = s.replaceAll("[^a-zA-Z0-9_-]", "_");
        return out.isEmpty() ? "asset" : out;
    }

    private static void writeUtf8(File f, String content) throws Exception {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void collectScriptFilesRecursive(File dir, String relPrefix, GameProject p) throws Exception {
        File[] list = dir.listFiles();
        if (list == null) return;
        java.util.Arrays.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : list) {
            if (f.isDirectory()) {
                collectScriptFilesRecursive(f, relPrefix + "/" + f.getName(), p);
            } else {
                String nm = f.getName().toLowerCase(Locale.US);
                if (nm.endsWith(".js") || nm.endsWith(".java") || nm.endsWith(".lua") || nm.endsWith(".vxs")) {
                    String rel = relPrefix + "/" + f.getName();
                    p.scriptFiles.put(rel, readUtf8(f));
                    if (nm.startsWith("main.")) {
                        p.activeScriptFile = rel;
                        if (nm.endsWith(".java")) p.scriptLanguage = "JAVA";
                        else if (nm.endsWith(".js")) p.scriptLanguage = "JS";
                        else if (nm.endsWith(".lua")) p.scriptLanguage = "LUA";
                        else p.scriptLanguage = "VXS";
                    }
                }
            }
        }
    }

    private static String readUtf8(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = fis.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }
}
