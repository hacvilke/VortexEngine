package com.vortex.engine.core.theme;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Built-in themes + import/export/persistence. Themes are JSON on disk:
 *
 * <pre>
 * { "id":"my-palette", "name":"My palette", "dark":false,
 *   "colors": { "sheet":"#fffdf6", "ink":"#33291d", ... } }
 * </pre>
 *
 * The picker accepts three shapes, because people bring palettes in different forms:
 * <ul>
 *   <li>a Vortex theme JSON file (the shape above);</li>
 *   <li>a bare slot map ({@code {"sheet":"#fffdf6", ...}});</li>
 *   <li>a flat list of hex colours (pasted from a design tool) — the engine maps them by
 *       brightness/chroma onto the semantic slots and names the result "Imported palette".</li>
 * </ul>
 */
public final class ThemeRegistry {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Map<String, VortexTheme> BUILTINS = new LinkedHashMap<>();
    private static final Map<String, VortexTheme> CUSTOM = new LinkedHashMap<>();
    private static VortexTheme active = null;

    private ThemeRegistry() { }

    static {
        // ---- Paper (default): hacvilke/paper-trader palette, cream stock + ink + marker yellow
        BUILTINS.put("paper", new VortexTheme("paper", "Paper (light)", false)
                .set(VortexTheme.INK, "#33291d")
                .set(VortexTheme.TEXT_MUTED, "#6f6558")
                .set(VortexTheme.SHEET, "#fffdf6")
                .set(VortexTheme.SHEET_2, "#fbf6ea")
                .set(VortexTheme.PANEL, "#fbf6ea")
                .set(VortexTheme.PANEL_ALT, "#fffdf3")
                .set(VortexTheme.HEADER, "#f3ebd6")
                .set(VortexTheme.SURFACE, "#f7efdc")
                .set(VortexTheme.SURFACE_HOV, "#efe3c6")
                .set(VortexTheme.INPUT_BG, "#fffef8")
                .set(VortexTheme.BORDER, "#d9c9a6")
                .set(VortexTheme.BORDER_SUB, "#e9dec6")
                .set(VortexTheme.BORDER_STR, "#b09b74")
                .set(VortexTheme.ACCENT, "#ffee9c")
                .set(VortexTheme.ON_ACCENT, "#33291d")
                .set(VortexTheme.OK, "#1e7a44").set(VortexTheme.OK_BG, "#e4efe2")
                .set(VortexTheme.WARN, "#b8860b").set(VortexTheme.WARN_BG, "#f6ecd5")
                .set(VortexTheme.DANGER, "#c0392b").set(VortexTheme.DANGER_BG, "#f7e3df")
                .set(VortexTheme.VIEWPORT, "#fffdf6")
                .set(VortexTheme.BLUEPRINT, "#123363")
                .set(VortexTheme.BLUEPRINT_DK, "#0b2245")
                .set(VortexTheme.BLUEPRINT_RL, "#ffffff"));

        // ---- Midnight Ink: the dark mode, same ink-and-paper logic inverted (no blue chrome)
        BUILTINS.put("dark", new VortexTheme("dark", "Midnight ink (dark)", true)
                .set(VortexTheme.INK, "#f4efe4")
                .set(VortexTheme.TEXT_MUTED, "#a79e8e")
                .set(VortexTheme.SHEET, "#15130f")
                .set(VortexTheme.SHEET_2, "#1b1815")
                .set(VortexTheme.PANEL, "#1b1815")
                .set(VortexTheme.PANEL_ALT, "#221e19")
                .set(VortexTheme.HEADER, "#100e0b")
                .set(VortexTheme.SURFACE, "#28231d")
                .set(VortexTheme.SURFACE_HOV, "#332c24")
                .set(VortexTheme.INPUT_BG, "#0e0c0a")
                .set(VortexTheme.BORDER, "#3a332a")
                .set(VortexTheme.BORDER_SUB, "#2b2620")
                .set(VortexTheme.BORDER_STR, "#585045")
                .set(VortexTheme.ACCENT, "#c9a227")
                .set(VortexTheme.ON_ACCENT, "#1a1712")
                .set(VortexTheme.OK, "#4ade80").set(VortexTheme.OK_BG, "#14261a")
                .set(VortexTheme.WARN, "#fbbf24").set(VortexTheme.WARN_BG, "#2a2110")
                .set(VortexTheme.DANGER, "#f87171").set(VortexTheme.DANGER_BG, "#2b1516")
                .set(VortexTheme.VIEWPORT, "#141210")
                .set(VortexTheme.BLUEPRINT, "#0f2c56")
                .set(VortexTheme.BLUEPRINT_DK, "#081a33")
                .set(VortexTheme.BLUEPRINT_RL, "#dfe9ff"));

        // ---- Drafting: greyscale studio palette for people who dislike warm paper
        BUILTINS.put("drafting", new VortexTheme("drafting", "Drafting grey", false)
                .set(VortexTheme.INK, "#1f2328")
                .set(VortexTheme.TEXT_MUTED, "#5c6672")
                .set(VortexTheme.SHEET, "#f6f7f9")
                .set(VortexTheme.SHEET_2, "#eef0f3")
                .set(VortexTheme.PANEL, "#eef0f3")
                .set(VortexTheme.PANEL_ALT, "#f9fafb")
                .set(VortexTheme.HEADER, "#e3e6ea")
                .set(VortexTheme.SURFACE, "#e8ebef")
                .set(VortexTheme.SURFACE_HOV, "#dadee4")
                .set(VortexTheme.INPUT_BG, "#ffffff")
                .set(VortexTheme.BORDER, "#c8ced6")
                .set(VortexTheme.BORDER_SUB, "#dde1e6")
                .set(VortexTheme.BORDER_STR, "#9aa4b0")
                .set(VortexTheme.ACCENT, "#ffd966")
                .set(VortexTheme.ON_ACCENT, "#1f2328")
                .set(VortexTheme.OK, "#116b36").set(VortexTheme.OK_BG, "#e2f0e6")
                .set(VortexTheme.WARN, "#8a5a00").set(VortexTheme.WARN_BG, "#f7edd8")
                .set(VortexTheme.DANGER, "#b3261e").set(VortexTheme.DANGER_BG, "#f8e4e2")
                .set(VortexTheme.VIEWPORT, "#ffffff")
                .set(VortexTheme.BLUEPRINT, "#1b3f74")
                .set(VortexTheme.BLUEPRINT_DK, "#122c52")
                .set(VortexTheme.BLUEPRINT_RL, "#ffffff"));
    }

    // ------------------------------------------------------------------ access

    public static List<VortexTheme> builtins() {
        return new ArrayList<>(BUILTINS.values());
    }

    public static VortexTheme builtin(String id) {
        return BUILTINS.get(id == null ? "" : id.toLowerCase(Locale.US));
    }

    public static VortexTheme custom(String id) {
        loadCustom();
        return CUSTOM.get(id == null ? "" : id.toLowerCase(Locale.US));
    }

    public static List<VortexTheme> customThemes() {
        loadCustom();
        return new ArrayList<>(CUSTOM.values());
    }

    public static List<VortexTheme> all() {
        List<VortexTheme> out = builtins();
        out.addAll(customThemes());
        return out;
    }

    public static VortexTheme find(String id) {
        VortexTheme b = builtin(id);
        if (b != null) return b;
        return custom(id);
    }

    public static VortexTheme active() {
        if (active == null) {
            String saved = Settings.get("theme", "paper");
            active = find(saved);
            if (active == null) active = builtin("paper");
        }
        return active;
    }

    /** Selects a theme and persists the choice. */
    public static VortexTheme setActive(String id) {
        VortexTheme t = find(id);
        if (t == null) t = builtin("paper");
        active = t;
        Settings.set("theme", t.id);
        return t;
    }

    // ------------------------------------------------------------------ import

    /**
     * Imports a theme from raw text: Vortex JSON, a bare slot map, or a flat hex list.
     * Returns the imported theme (already saved under {@code ~/.vortex/themes}).
     */
    public static VortexTheme importText(String text, String fallbackName) throws IOException {
        if (text == null || text.trim().isEmpty()) throw new IOException("empty theme text");
        String trimmed = text.trim();
        VortexTheme t;
        if (trimmed.startsWith("{")) {
            try {
                t = fromJson(new JSONObject(trimmed));
            } catch (Exception ex) {
                // Android's org.json throws a checked JSONException, desktop's does not
                throw new IOException("not a valid theme JSON: " + ex.getMessage());
            }
        } else {
            List<String> hexes = new ArrayList<>();
            for (String token : trimmed.split("[^#0-9a-fA-F]+")) {
                if (token.startsWith("#") && (token.length() == 4 || token.length() == 7 || token.length() == 9)) {
                    hexes.add(token);
                }
            }
            if (hexes.isEmpty()) throw new IOException("no #rrggbb colours found in that text");
            t = fromPalette(hexes);
        }
        if (t.name == null || t.name.trim().isEmpty()) t.name = fallbackName == null ? "Imported theme" : fallbackName;
        t.id = slug(t.name);
        save(t);
        return t;
    }

    /** Builds a full theme from a loose list of colours by sorting them by lightness. */
    public static VortexTheme fromPalette(List<String> hexes) {
        List<String> sorted = new ArrayList<>(hexes);
        sorted.sort((a, b) -> Double.compare(lightness(a), lightness(b)));
        String darkest = sorted.get(0);
        String lightest = sorted.get(sorted.size() - 1);
        String mid = sorted.get(sorted.size() / 2);
        boolean darkSheet = lightness(darkest) < 0.35 && lightness(lightest) < 0.7;
        VortexTheme t = new VortexTheme("imported", "Imported palette", darkSheet);
        // copy a base so every slot exists, then overwrite what the palette gives us
        VortexTheme base = darkSheet ? builtin("dark") : builtin("paper");
        t.colors.putAll(base.colors);

        t.set(VortexTheme.SHEET, darkSheet ? mix(darkest, lightest, 0.10) : mix(lightest, darkest, 0.02));
        t.set(VortexTheme.SHEET_2, darkSheet ? mix(darkest, lightest, 0.16) : mid);
        t.set(VortexTheme.PANEL, t.get(VortexTheme.SHEET_2));
        t.set(VortexTheme.PANEL_ALT, darkSheet ? mix(darkest, lightest, 0.20) : mix(lightest, mid, 0.5));
        t.set(VortexTheme.HEADER, darkSheet ? mix(darkest, lightest, 0.06) : mix(mid, darkest, 0.12));
        t.set(VortexTheme.SURFACE, darkSheet ? mix(mid, lightest, 0.25) : mix(mid, darkest, 0.06));
        t.set(VortexTheme.SURFACE_HOV, darkSheet ? mix(mid, lightest, 0.45) : mix(mid, darkest, 0.16));
        t.set(VortexTheme.INPUT_BG, darkSheet ? mix(darkest, lightest, 0.04) : lightest);
        t.set(VortexTheme.INK, darkSheet ? lightest : darkest);
        t.set(VortexTheme.TEXT_MUTED, darkSheet ? mix(lightest, mid, 0.55) : mix(darkest, mid, 0.55));
        t.set(VortexTheme.BORDER, darkSheet ? mix(mid, lightest, 0.35) : mix(mid, darkest, 0.22));
        t.set(VortexTheme.BORDER_SUB, darkSheet ? mix(mid, lightest, 0.22) : mix(mid, darkest, 0.10));
        t.set(VortexTheme.BORDER_STR, darkSheet ? mix(mid, lightest, 0.55) : mix(mid, darkest, 0.40));
        t.set(VortexTheme.ACCENT, saturated(mid, sorted));
        t.set(VortexTheme.ON_ACCENT, darkSheet ? darkest : lightest);
        t.set(VortexTheme.VIEWPORT, t.get(VortexTheme.SHEET));
        return t;
    }

    // ------------------------------------------------------------------ persistence

    public static File themesDir() {
        File dir = new File(Settings.root(), "themes");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File save(VortexTheme t) throws IOException {
        File f = new File(themesDir(), slug(t.id) + ".theme.json");
        Writer w = new OutputStreamWriter(new FileOutputStream(f), UTF8);
        try {
            String body;
            try {
                body = toJson(t).toString(2);
            } catch (Exception ex) {
                throw new IOException("could not serialise theme: " + ex.getMessage());
            }
            w.write(body);
        } finally {
            try { w.close(); } catch (IOException ignored) { }
        }
        CUSTOM.put(t.id.toLowerCase(Locale.US), t);
        return f;
    }

    public static void delete(VortexTheme t) {
        if (t == null) return;
        File f = new File(themesDir(), slug(t.id) + ".theme.json");
        if (f.exists()) f.delete();
        CUSTOM.remove(t.id.toLowerCase(Locale.US));
    }

    private static void loadCustom() {
        File dir = themesDir();
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.getName().endsWith(".json")) continue;
            try {
                String text = readFile(f);
                VortexTheme t = fromJson(new JSONObject(text));
                if (t.isComplete()) {
                    if (t.id == null || t.id.trim().isEmpty()) t.id = slug(t.name);
                    CUSTOM.put(t.id.toLowerCase(Locale.US), t);
                }
            } catch (Exception ignored) { }
        }
    }

    private static String readFile(File f) throws IOException {
        Reader r = new InputStreamReader(new FileInputStream(f), UTF8);
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        try {
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        } finally {
            try { r.close(); } catch (IOException ignored) { }
        }
        return sb.toString();
    }

    public static JSONObject toJson(VortexTheme t) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("dark", t.dark);
            if (t.bodyFont != null && !t.bodyFont.isEmpty()) o.put("bodyFont", t.bodyFont);
            if (t.handFont != null && !t.handFont.isEmpty()) o.put("handFont", t.handFont);
            if (t.monoFont != null && !t.monoFont.isEmpty()) o.put("monoFont", t.monoFont);
            JSONObject cols = new JSONObject();
            for (Map.Entry<String, String> e : t.colors.entrySet()) cols.put(e.getKey(), e.getValue());
            o.put("colors", cols);
        } catch (Exception ignored) { }
        return o;
    }

    public static VortexTheme fromJson(JSONObject o) {
        JSONObject cols = o.optJSONObject("colors");
        if (cols == null && o.has(VortexTheme.SHEET)) cols = o;   // bare slot map
        if (cols == null) throw new IllegalArgumentException("theme JSON has no \"colors\" object");

        boolean dark = o.optBoolean("dark", false);
        if (!o.has("dark")) {
            String sheet = cols.optString(VortexTheme.SHEET, "#ffffff");
            dark = lightness(sheet) < 0.45;
        }
        VortexTheme t = new VortexTheme(
                o.optString("id", ""),
                o.optString("name", "Imported theme"),
                dark);
        t.bodyFont = o.optString("bodyFont", "");
        t.handFont = o.optString("handFont", "");
        t.monoFont = o.optString("monoFont", "");
        VortexTheme base = dark ? builtin("dark") : builtin("paper");
        if (base != null) t.colors.putAll(base.colors);
        for (String slot : VortexTheme.SLOTS) {
            String v = cols.optString(slot, null);
            if (v != null && !v.trim().isEmpty()) t.set(slot, v);
        }
        if (t.id == null || t.id.trim().isEmpty()) t.id = slug(t.name);
        return t;
    }

    // ------------------------------------------------------------------ math helpers

    public static double lightness(String hex) {
        int rgb = VortexTheme.parseInt(hex);
        return (0.2126 * ((rgb >> 16) & 0xFF) + 0.7152 * ((rgb >> 8) & 0xFF) + 0.0722 * (rgb & 0xFF)) / 255.0;
    }

    private static int sat(String hex) {
        int rgb = VortexTheme.parseInt(hex);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        return max == 0 ? 0 : (max - min);
    }

    /** Picks the most chromatic colour of the palette as the selection accent. */
    private static String saturated(String fallback, List<String> all) {
        String best = fallback;
        int bestSat = 0;
        for (String h : all) {
            int s = sat(h);
            if (s > bestSat) { bestSat = s; best = h; }
        }
        return bestSat > 40 ? best : fallback;
    }

    public static String mix(String a, String b, double t) {
        int ca = VortexTheme.parseInt(a), cb = VortexTheme.parseInt(b);
        int r = (int) Math.round(((ca >> 16) & 0xFF) * (1 - t) + ((cb >> 16) & 0xFF) * t);
        int g = (int) Math.round(((ca >> 8) & 0xFF) * (1 - t) + ((cb >> 8) & 0xFF) * t);
        int bl = (int) Math.round((ca & 0xFF) * (1 - t) + (cb & 0xFF) * t);
        return String.format(Locale.US, "#%02x%02x%02x", clamp(r), clamp(g), clamp(bl));
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    public static String slug(String s) {
        String out = s == null ? "theme" : s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "-");
        out = out.replaceAll("^-+|-+$", "");
        return out.isEmpty() ? "theme" : out;
    }

    /** Tiny persistent key/value store shared by desktop and Android (a properties file). */
    public static final class Settings {
        private static java.util.Properties props = null;

        private static synchronized java.util.Properties loaded() {
            if (props == null) {
                props = new java.util.Properties();
                File f = file();
                if (f.exists()) {
                    InputStreamReader r = null;
                    try {
                        r = new InputStreamReader(new FileInputStream(f), UTF8);
                        props.load(r);
                    } catch (Exception ignored) {
                    } finally {
                        try { if (r != null) r.close(); } catch (IOException ignored) { }
                    }
                }
            }
            return props;
        }

        public static File root() {
            File dir = new File(System.getProperty("user.home", "."), ".vortex");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }

        private static File file() { return new File(root(), "settings.properties"); }

        public static String get(String key, String def) {
            return loaded().getProperty(key, def);
        }

        public static void set(String key, String value) {
            loaded().setProperty(key, value == null ? "" : value);
            try {
                FileOutputStream out = new FileOutputStream(file());
                try {
                    loaded().store(out, "Vortex Engine Studio settings");
                } finally {
                    try { out.close(); } catch (IOException ignored) { }
                }
            } catch (IOException ignored) { }
        }
    }
}
