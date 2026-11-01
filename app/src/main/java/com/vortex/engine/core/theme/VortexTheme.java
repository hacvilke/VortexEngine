package com.vortex.engine.core.theme;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A Vortex colour theme: 20 semantic slots, all as {@code #rrggbb} strings so a theme is plain,
 * human-editable data — the same object drives the desktop studio, the phone studio and the
 * generated documentation site.
 *
 * Nothing in the engine hardcodes a colour any more: UI code asks the theme for "ink" or "sheet",
 * so a Dark theme or an imported palette repaints everything without touching a single view.
 */
public final class VortexTheme {

    // ------------------------------------------------------------------ semantic slots
    public static final String INK          = "ink";           // primary text / strongest foreground
    public static final String TEXT_MUTED   = "textMuted";     // secondary text, metadata
    public static final String SHEET        = "sheet";         // main working surface
    public static final String SHEET_2      = "sheet2";        // alternating surface
    public static final String PANEL        = "panel";         // docks
    public static final String PANEL_ALT    = "panelAlt";      // cards inside docks
    public static final String HEADER       = "header";        // dock headers / chrome bands
    public static final String SURFACE      = "surface";       // interactive controls
    public static final String SURFACE_HOV  = "surfaceHover";
    public static final String INPUT_BG     = "inputBg";
    public static final String BORDER       = "border";
    public static final String BORDER_SUB   = "borderSubtle";
    public static final String BORDER_STR   = "borderStrong";
    public static final String ACCENT       = "accent";        // selection marker / highlight
    public static final String ON_ACCENT    = "onAccent";      // text on the accent fill
    public static final String OK           = "ok";
    public static final String OK_BG        = "okBg";
    public static final String WARN         = "warn";
    public static final String WARN_BG      = "warnBg";
    public static final String DANGER       = "danger";
    public static final String DANGER_BG    = "dangerBg";
    public static final String VIEWPORT     = "viewport";      // 2D scene backdrop
    public static final String BLUEPRINT    = "blueprint";     // logic graph backdrop
    public static final String BLUEPRINT_DK = "blueprintDeep";
    public static final String BLUEPRINT_RL = "blueprintRule";

    /** Slot order used by the editor UI and the JSON writer (stable, diff-friendly). */
    public static final String[] SLOTS = {
            INK, TEXT_MUTED, SHEET, SHEET_2, PANEL, PANEL_ALT, HEADER, SURFACE, SURFACE_HOV, INPUT_BG,
            BORDER, BORDER_SUB, BORDER_STR, ACCENT, ON_ACCENT, OK, OK_BG, WARN, WARN_BG,
            DANGER, DANGER_BG, VIEWPORT, BLUEPRINT, BLUEPRINT_DK, BLUEPRINT_RL
    };

    public String id;
    public String name;
    /** True for themes whose sheet is dark — used to flip native window hints. */
    public boolean dark;
    /** Optional: body / heading / monospace family overrides (empty = engine defaults). */
    public String bodyFont = "";
    public String handFont = "";
    public String monoFont = "";
    public final Map<String, String> colors = new LinkedHashMap<>();

    public VortexTheme(String id, String name, boolean dark) {
        this.id = id;
        this.name = name;
        this.dark = dark;
    }

    public VortexTheme set(String slot, String hex) {
        colors.put(slot, normalize(hex));
        return this;
    }

    public String get(String slot) {
        String v = colors.get(slot);
        if (v != null) return v;
        VortexTheme base = dark ? ThemeRegistry.builtin("dark") : ThemeRegistry.builtin("paper");
        String fallback = base == null ? null : base.colors.get(slot);
        return fallback != null ? fallback : "#ff00ff";   // magenta = an obvious "slot missing"
    }

    public int getRgb(String slot) {
        return parseInt(get(slot));
    }

    public int getArgb(String slot, int alpha) {
        return (alpha << 24) | getRgb(slot);
    }

    public String copyIdSuffix(String suffix) {
        VortexTheme t = copy();
        t.id = id + "-" + suffix;
        t.name = name + " " + suffix;
        return t.id;
    }

    public VortexTheme copy() {
        VortexTheme t = new VortexTheme(id, name, dark);
        t.bodyFont = bodyFont;
        t.handFont = handFont;
        t.monoFont = monoFont;
        t.colors.putAll(colors);
        return t;
    }

    // ------------------------------------------------------------------ helpers

    public static String normalize(String hex) {
        if (hex == null) return "#000000";
        String h = hex.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (h.length() == 8) h = h.substring(2);   // tolerate #aarrggbb
        if (h.length() != 6) return "#000000";
        return "#" + h.toLowerCase(Locale.US);
    }

    public static int parseInt(String hex) {
        String h = normalize(hex).substring(1);
        return (int) Long.parseLong(h, 16);
    }

    /** True when a theme looks usable (a handful of slots are required). */
    public boolean isComplete() {
        return colors.containsKey(SHEET) && colors.containsKey(INK) && colors.containsKey(PANEL);
    }

    /** One-line palette preview used by the picker: "▮▮▮▮▮ name". */
    public String swatchSummary() {
        return get(SHEET) + " " + get(PANEL) + " " + get(HEADER) + " " + get(INK) + " " + get(ACCENT);
    }
}
