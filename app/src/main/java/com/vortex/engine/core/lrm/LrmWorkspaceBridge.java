package com.vortex.engine.core.lrm;

import com.vortex.engine.core.flow.VortexFlowCompiler;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Native Integration with LRM (Log Replication Manager — github.com/hacvilke/lrm & hacvilke/lrm-mobile).
 *
 * Unpacks any Vortex GameProject into a clean, diff-friendly multi-file LRM repository so creators
 * can version-control, branch, merge, Wi-Fi drop (`lrm receive`), and stream real-time P2P changes
 * (`lrm share` / `lrm join` / `lrm daemon`) across Android (Termux PIE `lrm-mobile`), Windows & Linux
 * with ZERO central servers!
 */
public class LrmWorkspaceBridge {

    public static final String LRM_DESKTOP_INSTALL_SH =
            "curl -fsSL https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.sh | sh";
    public static final String LRM_WINDOWS_INSTALL_PS1 =
            "irm https://raw.githubusercontent.com/hacvilke/lrm/main/scripts/install.ps1 | iex";
    public static final String LRM_MOBILE_TERMUX_INSTALL_SH =
            "curl -fsSL https://raw.githubusercontent.com/hacvilke/lrm-mobile/main/scripts/install.sh | sh";

    public static class CmdResult {
        public final int exitCode;
        public final String output;
        public CmdResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    /**
     * Unpacks a GameProject into diff-friendly files inside `repoDir`:
     *   - vortex.manifest.json
     *   - scene/entities.json
     *   - scripts/main.{js|lua|vxs}
     *   - flow/rules.json
     *   - assets/sprites/*.sprite
     *   - assets/meshes/*.obj
     *   - inbox/ (for `lrm receive` Wi-Fi browser drops & `lrm send`)
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

        JSONArray ents = new JSONArray();
        for (Entity e : p.entities) {
            ents.put(e.toJson());
        }
        writeUtf8(new File(repoDir, "scene/entities.json"), ents.toString(2));

        String ext = "JS".equalsIgnoreCase(p.scriptLanguage) ? "js"
                : ("LUA".equalsIgnoreCase(p.scriptLanguage) ? "lua" : "vxs");
        writeUtf8(new File(repoDir, "scripts/main." + ext), p.scriptCode == null ? "" : p.scriptCode);

        writeUtf8(new File(repoDir, "flow/rules.json"), VortexFlowCompiler.toJsonArray(p.flowRules).toString(2));

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
            // Check if a monolithic .vortex.json exists in repoDir or inbox/
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
        p.iconBgHex = m.optString("iconBgHex", "#0D1321");
        p.iconAccentHex = m.optString("iconAccentHex", "#00E5FF");
        p.iconGlyph = m.optString("iconGlyph", "CUBE");
        p.skyColorHex = m.optString("skyColorHex", "#0B1020");
        p.ambientColorHex = m.optString("ambientColorHex", "#24344D");
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

        String ext = "JS".equalsIgnoreCase(p.scriptLanguage) ? "js"
                : ("LUA".equalsIgnoreCase(p.scriptLanguage) ? "lua" : "vxs");
        File primaryScript = new File(repoDir, "scripts/main." + ext);
        if (primaryScript.exists()) {
            p.scriptCode = readUtf8(primaryScript);
        } else {
            for (String cand : new String[]{"js", "lua", "vxs"}) {
                File cf = new File(repoDir, "scripts/main." + cand);
                if (cf.exists()) {
                    p.scriptLanguage = cand.toUpperCase(Locale.US);
                    p.scriptCode = readUtf8(cf);
                    break;
                }
            }
        }

        File flowFile = new File(repoDir, "flow/rules.json");
        if (flowFile.exists()) {
            p.flowRules.addAll(VortexFlowCompiler.fromJsonArray(new JSONArray(readUtf8(flowFile))));
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

    /**
     * Automatically merges `.obj`, `.sprite`, `.js`, `.lua` files dropped into `inbox/` via `lrm receive`!
     */
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

    public static String findLrmBinary() {
        String home = System.getProperty("user.home", ".");
        boolean isWin = System.getProperty("os.name", "").toLowerCase(Locale.US).contains("win");
        String binName = isWin ? "lrm.exe" : "lrm";

        String[] candidates = new String[]{
                new File(home, ".local/bin/" + binName).getAbsolutePath(),
                new File(home, "bin/" + binName).getAbsolutePath(),
                "/data/data/com.termux/files/home/.local/bin/lrm",
                "/tmp/lrm_bin/lrm",
                "/usr/local/bin/lrm",
                binName
        };
        for (String c : candidates) {
            try {
                Process proc = new ProcessBuilder(c, "--help").redirectErrorStream(true).start();
                boolean done = proc.waitFor(3, TimeUnit.SECONDS);
                if (done) {
                    String out = readStream(proc);
                    if (out.contains("Log Replication Manager") || out.contains("P2P mesh")) {
                        return c;
                    }
                } else {
                    proc.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static CmdResult runLrm(File repoDir, int timeoutSec, String... args) {
        String bin = findLrmBinary();
        if (bin == null) {
            return new CmdResult(127, "LRM binary not found on PATH.\nInstall LRM in 1 command:\n  Linux/macOS: "
                    + LRM_DESKTOP_INSTALL_SH + "\n  Windows PS : " + LRM_WINDOWS_INSTALL_PS1
                    + "\n  Termux     : " + LRM_MOBILE_TERMUX_INSTALL_SH);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(bin);
        cmd.addAll(Arrays.asList(args));
        try {
            if (!repoDir.exists()) repoDir.mkdirs();
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(repoDir);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            boolean finished = proc.waitFor(Math.max(2, timeoutSec), TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                return new CmdResult(124, readStream(proc) + "\n[Timed out after " + timeoutSec + "s]");
            }
            return new CmdResult(proc.exitValue(), readStream(proc));
        } catch (Exception ex) {
            return new CmdResult(1, "Failed to invoke LRM: " + ex.getMessage());
        }
    }

    public static String buildTermuxShellSequence(String sharedRepoPath, String subCmd) {
        return "export PATH=\"$HOME/.local/bin:$PATH\"; "
                + "if ! command -v lrm >/dev/null 2>&1; then " + LRM_MOBILE_TERMUX_INSTALL_SH + "; fi; "
                + "mkdir -p \"" + sharedRepoPath + "\" && cd \"" + sharedRepoPath + "\" && " + subCmd;
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

    private static String readUtf8(File f) throws Exception {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = fis.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }

    private static String readStream(Process p) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Exception ignored) {
        }
        return sb.toString().trim();
    }
}
