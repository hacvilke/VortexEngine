package com.vortex.engine.core.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/**
 * Pure-Java Cross-Platform Persistent Save-Data & High-Score Store (`vortex.saveData` / `vortex.loadData`).
 * Works identically on Android (app files dir), Termux APKs, Windows .exe & Linux (`~/.vortex-saves/`).
 */
public class VortexSaveStore {

    private static volatile File customRootDir = null;
    private static final Map<String, JSONObject> cache = new ConcurrentHashMap<>();

    public static void setSaveRootDir(File dir) {
        if (dir != null) customRootDir = dir;
    }

    private static File resolveSaveFile(String projectSlug) {
        String slug = (projectSlug == null || projectSlug.isEmpty()) ? "default_game" : projectSlug;
        File base = customRootDir;
        if (base == null) {
            String home = System.getProperty("user.home", ".");
            base = new File(home, ".vortex-saves");
        }
        if (!base.exists()) base.mkdirs();
        return new File(base, slug + ".save.json");
    }

    private static synchronized JSONObject getBucket(String slug) {
        JSONObject existing = cache.get(slug);
        if (existing != null) return existing;
        File f = resolveSaveFile(slug);
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] bytes = new byte[(int) f.length()];
                int n = fis.read(bytes);
                if (n > 0) {
                    JSONObject parsed = new JSONObject(new String(bytes, 0, n, StandardCharsets.UTF_8));
                    cache.put(slug, parsed);
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        JSONObject fresh = new JSONObject();
        cache.put(slug, fresh);
        return fresh;
    }

    public static synchronized void save(String projectSlug, String key, String value) {
        if (key == null || key.isEmpty()) return;
        String slug = (projectSlug == null || projectSlug.isEmpty()) ? "default_game" : projectSlug;
        JSONObject bucket = getBucket(slug);
        try {
            bucket.put(key, value == null ? "" : value);
            File f = resolveSaveFile(slug);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(bucket.toString(2).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    public static synchronized String load(String projectSlug, String key, String defaultValue) {
        if (key == null || key.isEmpty()) return defaultValue;
        String slug = (projectSlug == null || projectSlug.isEmpty()) ? "default_game" : projectSlug;
        JSONObject bucket = getBucket(slug);
        return bucket.has(key) ? bucket.optString(key, defaultValue) : defaultValue;
    }

    public static String loadString(String projectSlug, String key, String defaultValue) {
        return load(projectSlug, key, defaultValue);
    }

    public static double loadNumber(String projectSlug, String key, double defaultValue) {
        String s = load(projectSlug, key, null);
        if (s == null || s.trim().isEmpty()) return defaultValue;
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
