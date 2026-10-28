package com.vortex.engine.studio;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import com.vortex.engine.core.model.GameProject;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Termux Bridge v2.0:
 *  - Exports LuaJ + JBox2D + FastNoiseLite + OpenGL ES standalone projects
 *  - Supports direct com.termux.RUN_COMMAND + App Details Permission Settings helper (for Samsung One UI / Android 14+)
 *  - Supports zero-permission MediaStore Downloads ZIP export + Clipboard command fallback
 */
public class TermuxBridge {

    public static final String TERMUX_PACKAGE = "com.termux";
    public static final String TERMUX_SERVICE = "com.termux.app.RunCommandService";
    public static final String ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND";
    public static final String PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";

    private static final AtomicInteger EXEC_ID = new AtomicInteger(1000);

    public static class ExportResult {
        public String slug;
        public File internalProjectDir;
        public File sharedProjectDir;
        public String sharedZipPath;
        public String termuxBuildCmd;
        public String expectedSharedApkPath;
    }

    public static boolean isTermuxInstalled(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(TERMUX_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasRunCommandPermission(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ctx.checkSelfPermission(PERMISSION_RUN_COMMAND) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void openAppPermissionSettings(Activity activity) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
            activity.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static boolean hasAllFilesAccess(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ctx.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static void requestAllFilesAccess(Activity activity, int reqCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                i.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(i, reqCode);
                return;
            } catch (Exception ignored) {
                Intent i = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                activity.startActivityForResult(i, reqCode);
                return;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
            }, reqCode);
        }
    }

    public static ExportResult exportStandaloneProject(Context ctx, GameProject project) throws Exception {
        String slug = project.getSlug();
        File stageRoot = new File(ctx.getCacheDir(), "export_stage/" + slug);
        deleteRecursive(stageRoot);
        stageRoot.mkdirs();

        writeText(new File(stageRoot, "AndroidManifest.xml"), generateStandaloneManifest(project));

        File resDir = new File(stageRoot, "res");
        File valuesDir = new File(resDir, "values");
        valuesDir.mkdirs();
        writeText(new File(valuesDir, "strings.xml"),
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<resources>\n" +
                "    <string name=\"app_name\">" + escapeXml(project.title) + "</string>\n" +
                "</resources>\n");

        IconGenerator.generateLauncherIcons(
                resDir, project.iconBgHex, project.iconAccentHex, project.iconGlyph, project.title);

        File assetsDir = new File(stageRoot, "assets");
        File scriptsDir = new File(assetsDir, "scripts");
        scriptsDir.mkdirs();
        writeText(new File(assetsDir, "project.vortex.json"), project.toJsonString());

        project.ensureScriptFilesInitialized();
        for (Map.Entry<String, String> sf : project.scriptFiles.entrySet()) {
            String rel = GameProject.normalizeScriptPath(sf.getKey());
            if (rel.startsWith("scripts/")) rel = rel.substring("scripts/".length());
            File destScript = new File(scriptsDir, rel);
            if (destScript.getParentFile() != null) destScript.getParentFile().mkdirs();
            writeText(destScript, sf.getValue() == null ? "" : sf.getValue());
        }

        if (!project.customObjMeshes.isEmpty()) {
            File meshesDir = new File(assetsDir, "meshes");
            meshesDir.mkdirs();
            for (Map.Entry<String, String> entry : project.customObjMeshes.entrySet()) {
                String safeName = entry.getKey().replaceAll("[^a-zA-Z0-9_.-]", "_");
                if (!safeName.endsWith(".obj")) safeName += ".obj";
                writeText(new File(meshesDir, safeName), entry.getValue());
            }
        }

        File prebuiltDir = new File(stageRoot, "prebuilt");
        prebuiltDir.mkdirs();
        copyAssetIfPresent(ctx, "toolchain/vortex-runtime.dex", new File(prebuiltDir, "vortex-runtime.dex"));
        copyAssetIfPresent(ctx, "toolchain/vortex-runtime-2.dex", new File(prebuiltDir, "vortex-runtime-2.dex"));
        copyAssetIfPresent(ctx, "toolchain/vortex-runtime-3.dex", new File(prebuiltDir, "vortex-runtime-3.dex"));
        copyAssetIfPresent(ctx, "toolchain/build_termux.sh", new File(stageRoot, "build_termux.sh"));
        copyAssetIfPresent(ctx, "toolchain/setup_termux.sh", new File(stageRoot, "setup_termux.sh"));
        unzipAssetIfPresent(ctx, "toolchain/vortex-runtime-src.zip", stageRoot);

        ExportResult res = new ExportResult();
        res.slug = slug;
        res.internalProjectDir = stageRoot;
        res.expectedSharedApkPath = "/sdcard/Download/VortexEngine/Builds/" + slug + "-release.apk";

        String zipFileName = "VortexProject_" + slug + ".zip";
        File localZip = new File(ctx.getCacheDir(), zipFileName);
        zipDirectory(stageRoot, localZip);
        if (saveZipToDownloadsMediaStore(ctx, localZip, zipFileName)) {
            res.sharedZipPath = "/sdcard/Download/" + zipFileName;
        }

        try {
            File dlRoot = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File directDir = new File(dlRoot, "VortexEngine/" + slug);
            deleteRecursive(directDir);
            if (directDir.mkdirs() || directDir.exists()) {
                copyDirectory(stageRoot, directDir);
                res.sharedProjectDir = directDir;
                if (res.sharedZipPath == null) {
                    File directZip = new File(dlRoot, zipFileName);
                    copyFile(localZip, directZip);
                    res.sharedZipPath = "/sdcard/Download/" + zipFileName;
                }
            }
        } catch (Throwable ignored) {
        }

        if (res.sharedProjectDir != null) {
            res.termuxBuildCmd = "bash /sdcard/Download/VortexEngine/" + slug + "/build_termux.sh";
        } else {
            res.termuxBuildCmd = "mkdir -p ~/vortex-builds/" + slug
                    + " && unzip -qo /sdcard/Download/" + zipFileName + " -d ~/vortex-builds/" + slug
                    + " && bash ~/vortex-builds/" + slug + "/build_termux.sh";
        }
        return res;
    }

    public static String triggerTermuxBuild(
            Activity activity,
            ExportResult export,
            boolean runInBackground,
            boolean forceFromSource
    ) {
        if (!isTermuxInstalled(activity)) return "ERR_NO_TERMUX";
        if (!hasRunCommandPermission(activity)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                activity.requestPermissions(new String[]{PERMISSION_RUN_COMMAND}, 401);
            }
            return "REQUESTED_PERMISSION";
        }

        String flag = forceFromSource ? " --from-source" : "";
        String cmd = export.termuxBuildCmd + flag;

        Intent intent = new Intent();
        intent.setClassName(TERMUX_PACKAGE, TERMUX_SERVICE);
        intent.setAction(ACTION_RUN_COMMAND);
        intent.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", cmd});
        intent.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", runInBackground);
        if (!runInBackground) {
            intent.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0");
        }

        Intent resultIntent = new Intent(activity, TermuxResultService.class);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            piFlags |= PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pi = PendingIntent.getService(activity, EXEC_ID.incrementAndGet(), resultIntent, piFlags);
        intent.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activity.startForegroundService(intent);
            } else {
                activity.startService(intent);
            }
            return "STARTED";
        } catch (Throwable t) {
            return "ERR: " + t.getMessage();
        }
    }

    public static String getBootstrapOneLiner() {
        return "mkdir -p ~/.termux && "
                + "(grep -q '^allow-external-apps' ~/.termux/termux.properties 2>/dev/null "
                + "&& sed -i 's/^.*allow-external-apps.*/allow-external-apps = true/' ~/.termux/termux.properties "
                + "|| echo 'allow-external-apps = true' >> ~/.termux/termux.properties) && "
                + "(termux-reload-settings || true) && "
                + "( [ -d ~/storage ] || termux-setup-storage ) && "
                + "(command -v aapt2 >/dev/null && command -v apksigner >/dev/null || "
                + "(pkg update -y && pkg install -y openjdk-21 aapt aapt2 d8 apksigner zip unzip)) && "
                + "echo 'Vortex Termux Toolchain 100% Ready!'";
    }

    public static void copyToClipboard(Context ctx, String label, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, text));
    }

    public static boolean openTermuxApp(Context ctx) {
        Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(launch);
            return true;
        }
        return false;
    }

    public static String promptInstallApk(Activity activity, File apkFile) {
        if (apkFile == null || !apkFile.exists()) {
            return "APK file not found yet at:\n" + (apkFile != null ? apkFile.getAbsolutePath() : "unknown")
                    + "\n\nTip: Grant Shared Files Access or open Downloads/VortexEngine/Builds/ in your file manager.";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                return "Please allow 'Install unknown apps' for Vortex Engine, then tap Install APK again.";
            }
        }
        Uri apkUri = VortexFileProvider.getUriForFile(activity, apkFile);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(apkUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(install);
        return "OPENED_INSTALLER";
    }

    private static String generateStandaloneManifest(GameProject p) {
        String pkg = sanitizePackage(p.packageName);
        String screenOrient = "sensorLandscape";
        if ("portrait".equalsIgnoreCase(p.orientation)) screenOrient = "portrait";
        else if ("sensor".equalsIgnoreCase(p.orientation)) screenOrient = "sensor";

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                "    package=\"" + pkg + "\"\n" +
                "    android:versionCode=\"" + Math.max(1, p.versionCode) + "\"\n" +
                "    android:versionName=\"" + escapeXml(p.versionName) + "\">\n\n" +
                "    <uses-sdk android:minSdkVersion=\"24\" android:targetSdkVersion=\"34\" />\n" +
                "    <uses-feature android:glEsVersion=\"0x00020000\" android:required=\"true\" />\n\n" +
                "    <application\n" +
                "        android:allowBackup=\"true\"\n" +
                "        android:hardwareAccelerated=\"true\"\n" +
                "        android:icon=\"@mipmap/ic_launcher\"\n" +
                "        android:label=\"@string/app_name\"\n" +
                "        android:theme=\"@android:style/Theme.NoTitleBar.Fullscreen\">\n\n" +
                "        <activity\n" +
                "            android:name=\"com.vortex.engine.runtime.StandaloneGameActivity\"\n" +
                "            android:configChanges=\"orientation|screenSize|keyboardHidden\"\n" +
                "            android:screenOrientation=\"" + screenOrient + "\"\n" +
                "            android:exported=\"true\">\n" +
                "            <intent-filter>\n" +
                "                <action android:name=\"android.intent.action.MAIN\" />\n" +
                "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
                "            </intent-filter>\n" +
                "        </activity>\n" +
                "    </application>\n" +
                "</manifest>\n";
    }

    private static String sanitizePackage(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "com.vortex.game.exported";
        String cleaned = raw.trim().toLowerCase().replaceAll("[^a-z0-9_.]", "");
        if (!cleaned.contains(".")) cleaned = "com.vortex." + cleaned;
        String[] parts = cleaned.split("\\.");
        StringBuilder sb = new StringBuilder();
        for (String pt : parts) {
            if (pt.isEmpty()) continue;
            if (Character.isDigit(pt.charAt(0))) pt = "g" + pt;
            if (sb.length() > 0) sb.append('.');
            sb.append(pt);
        }
        String res = sb.toString();
        return res.contains(".") ? res : "com.vortex.game.exported";
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static boolean saveZipToDownloadsMediaStore(Context ctx, File zipFile, String displayName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        try {
            ContentResolver resolver = ctx.getContentResolver();
            try {
                resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        MediaStore.MediaColumns.DISPLAY_NAME + "=?", new String[]{displayName});
            } catch (Throwable ignored) {}

            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            try (OutputStream os = resolver.openOutputStream(uri);
                 FileInputStream fis = new FileInputStream(zipFile)) {
                if (os == null) return false;
                byte[] buf = new byte[16384]; int n;
                while ((n = fis.read(buf)) > 0) os.write(buf, 0, n);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void writeText(File f, String content) throws IOException {
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void copyAssetIfPresent(Context ctx, String assetName, File dest) {
        try (InputStream is = ctx.getAssets().open(assetName)) {
            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                byte[] buf = new byte[16384]; int r;
                while ((r = is.read(buf)) > 0) fos.write(buf, 0, r);
            }
        } catch (IOException ignored) {}
    }

    private static void unzipAssetIfPresent(Context ctx, String assetName, File destDir) {
        try (InputStream is = ctx.getAssets().open(assetName);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry ze; byte[] buf = new byte[16384];
            while ((ze = zis.getNextEntry()) != null) {
                File out = new File(destDir, ze.getName());
                if (ze.isDirectory()) out.mkdirs();
                else {
                    if (out.getParentFile() != null) out.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int n; while ((n = zis.read(buf)) > 0) fos.write(buf, 0, n);
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException ignored) {}
    }

    private static void zipDirectory(File rootDir, File outZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outZip))) {
            zipWalk(rootDir, rootDir, zos);
        }
    }

    private static void zipWalk(File root, File cur, ZipOutputStream zos) throws IOException {
        File[] children = cur.listFiles();
        if (children == null) return;
        byte[] buf = new byte[16384];
        for (File child : children) {
            String rel = root.toURI().relativize(child.toURI()).getPath();
            if (child.isDirectory()) zipWalk(root, child, zos);
            else {
                zos.putNextEntry(new ZipEntry(rel));
                try (FileInputStream fis = new FileInputStream(child)) {
                    int n; while ((n = fis.read(buf)) > 0) zos.write(buf, 0, n);
                }
                zos.closeEntry();
            }
        }
    }

    private static void copyDirectory(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            dst.mkdirs();
            File[] files = src.listFiles();
            if (files != null) for (File f : files) copyDirectory(f, new File(dst, f.getName()));
        } else copyFile(src, dst);
    }

    private static void copyFile(File src, File dst) throws IOException {
        if (dst.getParentFile() != null) dst.getParentFile().mkdirs();
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384]; int n;
            while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
        }
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
