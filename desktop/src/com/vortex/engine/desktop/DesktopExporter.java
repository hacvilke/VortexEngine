package com.vortex.engine.desktop;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Multi-Target Game Exporter for Windows .exe & Linux Desktop Studio:
 *  1) Standalone Windows x64 Game Executable (.exe) — Zero compiler required on user's PC!
 *  2) Standalone Linux x86_64 Game Binary
 *  3) Standalone Cross-Platform Runnable Game (.jar)
 *  4) Android Termux APK Project Bundle (.zip) — Transfer to phone & run build_termux.sh!
 */
public class DesktopExporter {

    /**
     * Builds a self-contained runnable game .jar with embedded_game.vortex.json inside,
     * cloning all engine classes (Core + LuaJ + JBox2D + FastNoiseLite + Desktop Runner)
     * directly from the running Studio archive!
     */
    public static void exportStandaloneGameJar(GameProject project, File outJar) throws Exception {
        File selfJar = getRunningArchiveFile();
        if (selfJar == null || !selfJar.isFile()) {
            throw new IllegalStateException("Run from packaged VortexEngine Studio .exe/.jar to export standalone games.");
        }

        if (outJar.getParentFile() != null) outJar.getParentFile().mkdirs();
        Set<String> seen = new HashSet<>();

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(selfJar);
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outJar))) {

            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            byte[] buf = new byte[16384];
            while (entries.hasMoreElements()) {
                ZipEntry ze = entries.nextElement();
                String name = ze.getName();
                if ("embedded_game.vortex.json".equals(name) || seen.contains(name)) {
                    continue;
                }
                seen.add(name);
                zos.putNextEntry(new ZipEntry(name));
                if (!ze.isDirectory()) {
                    try (InputStream is = zf.getInputStream(ze)) {
                        int n;
                        while ((n = is.read(buf)) > 0) zos.write(buf, 0, n);
                    }
                }
                zos.closeEntry();
            }

            // Inject embedded_game.vortex.json -> triggers instant Game Player Mode on startup!
            zos.putNextEntry(new ZipEntry("embedded_game.vortex.json"));
            zos.write(project.toJsonString().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /**
     * Prepends the embedded native Win32 x64 PE launcher stub (`toolchain/win-launcher-stub.exe`)
     * onto the standalone game JAR, creating a genuine native Windows `.exe` game!
     */
    public static void exportStandaloneWindowsExe(GameProject project, File outExe) throws Exception {
        byte[] stub = readResourceBytes("/toolchain/win-launcher-stub.exe");
        if (stub == null || stub.length < 1024) {
            throw new IllegalStateException("Embedded Win32 PE launcher stub not found in resources.");
        }

        File tempJar = File.createTempFile("vortex_game_", ".jar");
        try {
            exportStandaloneGameJar(project, tempJar);
            if (outExe.getParentFile() != null) outExe.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(outExe);
                 FileInputStream fis = new FileInputStream(tempJar)) {
                fos.write(stub);
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            }
        } finally {
            tempJar.delete();
        }
    }

    /**
     * Prepends a POSIX shell launcher header onto the standalone game JAR for Linux/macOS!
     */
    public static void exportStandaloneLinuxBinary(GameProject project, File outBin) throws Exception {
        String header = "#!/usr/bin/env sh\n"
                + "exec java -Xmx1024m -jar \"$0\" \"$@\"\n";
        File tempJar = File.createTempFile("vortex_linux_", ".jar");
        try {
            exportStandaloneGameJar(project, tempJar);
            if (outBin.getParentFile() != null) outBin.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(outBin);
                 FileInputStream fis = new FileInputStream(tempJar)) {
                fos.write(header.getBytes(StandardCharsets.UTF_8));
                byte[] buf = new byte[16384];
                int n;
                while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            }
            outBin.setExecutable(true);
        } finally {
            tempJar.delete();
        }
    }

    /**
     * Exports a complete Android Termux APK Project Bundle (.zip) with AndroidManifest.xml,
     * multi-DPI icons, prebuilt/vortex-runtime.dex, build_termux.sh, and project.vortex.json!
     */
    public static void exportAndroidTermuxZip(GameProject p, File outZip) throws Exception {
        if (outZip.getParentFile() != null) outZip.getParentFile().mkdirs();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outZip))) {
            putUtf8(zos, "AndroidManifest.xml", buildAndroidManifest(p));
            putUtf8(zos, "res/values/strings.xml",
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n" +
                    "    <string name=\"app_name\">" + escXml(p.title) + "</string>\n</resources>\n");

            String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
            int[] sizes = {48, 72, 96, 144, 192};
            for (int i = 0; i < dirs.length; i++) {
                putBytes(zos, "res/" + dirs[i] + "/ic_launcher.png", renderDesktopIconPng(sizes[i], p));
            }

            putUtf8(zos, "assets/project.vortex.json", p.toJsonString());
            String scriptExt = "JAVA".equalsIgnoreCase(p.scriptLanguage) ? "main.java"
                    : "JS".equalsIgnoreCase(p.scriptLanguage) ? "main.js"
                    : ("LUA".equalsIgnoreCase(p.scriptLanguage) ? "main.lua" : "main.vxs");
            putUtf8(zos, "assets/scripts/" + scriptExt, p.scriptCode == null ? "" : p.scriptCode);

            for (Map.Entry<String, String> m : p.customObjMeshes.entrySet()) {
                putUtf8(zos, "assets/meshes/" + m.getKey() + ".obj", m.getValue());
            }

            byte[] dex = readResourceBytes("/toolchain/vortex-runtime.dex");
            if (dex != null) putBytes(zos, "prebuilt/vortex-runtime.dex", dex);
            byte[] dex2 = readResourceBytes("/toolchain/vortex-runtime-2.dex");
            if (dex2 != null) putBytes(zos, "prebuilt/vortex-runtime-2.dex", dex2);
            byte[] dex3 = readResourceBytes("/toolchain/vortex-runtime-3.dex");
            if (dex3 != null) putBytes(zos, "prebuilt/vortex-runtime-3.dex", dex3);

            byte[] buildSh = readResourceBytes("/toolchain/build_termux.sh");
            if (buildSh != null) putBytes(zos, "build_termux.sh", buildSh);

            byte[] setupSh = readResourceBytes("/toolchain/setup_termux.sh");
            if (setupSh != null) putBytes(zos, "setup_termux.sh", setupSh);
        }
    }

    private static byte[] renderDesktopIconPng(int s, GameProject p) throws Exception {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int bg = Entity.hexToArgbInt(p.iconBgHex, 0xFF101013);
        int ac = Entity.hexToArgbInt(p.iconAccentHex, 0xFFFFFFFF);
        g.setColor(new Color((bg >> 16) & 0xFF, (bg >> 8) & 0xFF, bg & 0xFF));
        g.fill(new RoundRectangle2D.Float(0, 0, s, s, s * 0.22f, s * 0.22f));
        g.setColor(new Color((ac >> 16) & 0xFF, (ac >> 8) & 0xFF, ac & 0xFF));
        g.setStroke(new BasicStroke(Math.max(2f, s * 0.06f)));
        float pad = s * 0.1f;
        g.draw(new RoundRectangle2D.Float(pad, pad, s - pad * 2, s - pad * 2, s * 0.15f, s * 0.15f));
        g.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, Math.round(s * 0.34f))));
        FontMetrics fm = g.getFontMetrics();
        String ch = (p.title != null && !p.title.isEmpty()) ? p.title.substring(0, 1).toUpperCase() : "V";
        g.drawString(ch, (s - fm.stringWidth(ch)) / 2, Math.round(s * 0.62f));
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private static String buildAndroidManifest(GameProject p) {
        String pkg = (p.packageName == null || !p.packageName.contains("."))
                ? "com.vortex.game.exported" : p.packageName.trim().toLowerCase();
        String orient = "portrait".equalsIgnoreCase(p.orientation) ? "portrait" : "sensorLandscape";
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"" + pkg + "\" android:versionCode=\"1\" android:versionName=\"" + escXml(p.versionName) + "\">\n" +
                "    <uses-sdk android:minSdkVersion=\"24\" android:targetSdkVersion=\"34\" />\n" +
                "    <uses-feature android:glEsVersion=\"0x00020000\" android:required=\"true\" />\n" +
                "    <application android:allowBackup=\"true\" android:hardwareAccelerated=\"true\"\n" +
                "        android:icon=\"@mipmap/ic_launcher\" android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@android:style/Theme.NoTitleBar.Fullscreen\">\n" +
                "        <activity android:name=\"com.vortex.engine.runtime.StandaloneGameActivity\"\n" +
                "            android:configChanges=\"orientation|screenSize|keyboardHidden\"\n" +
                "            android:screenOrientation=\"" + orient + "\" android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>\n";
    }

    private static String escXml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void putUtf8(ZipOutputStream zos, String path, String text) throws Exception {
        putBytes(zos, path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void putBytes(ZipOutputStream zos, String path, byte[] data) throws Exception {
        zos.putNextEntry(new ZipEntry(path));
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readResourceBytes(String resPath) {
        try (InputStream is = DesktopExporter.class.getResourceAsStream(resPath)) {
            if (is == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = is.read(buf)) > 0) baos.write(buf, 0, n);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static File getRunningArchiveFile() {
        try {
            URI uri = DesktopExporter.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            return new File(uri);
        } catch (Exception e) {
            return null;
        }
    }
}
