package com.vortex.engine.core.docs;

import com.vortex.engine.core.script.VortexScriptEngine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Renders the offline documentation for this exact build — no network, no CDN, no build step.
 *
 * <p>v4.9.2 split it in two, because the old single page opened with an API wall:
 * <ul>
 *   <li>{@code index.html} — <b>how to use Vortex</b>: a guided tour of the studio and of a real
 *       game's on-screen UI, with the regions highlighted and explained, a four-step first game,
 *       the theme changer, Forge AI and healthy audio levels.</li>
 *   <li>{@code api.html} — the API reference: the topic corpus plus the generated index of every
 *       {@code vortex.*} call the running build exposes.</li>
 * </ul>
 *
 * <p>The teaching figures are real captures of this engine, embedded as data URIs so the whole
 * page is one self-contained file that works in the desktop browser, the Android WebView and a
 * sandboxed preview alike. The captures come from {@code scripts/make_docs_figures.py}.
 */
public final class DocsSite {

    private DocsSite() { }

    // ------------------------------------------------------------------ assets

    /** Where the docs figures come from (classpath on desktop, {@code assets/docs} on Android). */
    public interface AssetSource {
        InputStream open(String name) throws IOException;
    }

    private static AssetSource assetSource = new AssetSource() {
        @Override public InputStream open(String name) throws IOException {
            InputStream in = DocsSite.class.getResourceAsStream("/" + name);
            if (in == null) throw new IOException("missing docs asset: " + name);
            return in;
        }
    };

    /** Lets the Android studio read the same figures out of {@code assets/docs}. */
    public static void setAssetSource(AssetSource src) {
        if (src != null) assetSource = src;
    }

    private static final char[] B64 =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    /** Hand-rolled base64 (no javax.xml.bind on Android, no android.util in shared core). */
    private static String base64(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4 + 4);
        int i = 0;
        while (i + 2 < data.length) {
            int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8) | (data[i + 2] & 0xFF);
            sb.append(B64[(v >> 18) & 63]).append(B64[(v >> 12) & 63])
              .append(B64[(v >> 6) & 63]).append(B64[v & 63]);
            i += 3;
        }
        int rest = data.length - i;
        if (rest == 1) {
            int v = (data[i] & 0xFF) << 16;
            sb.append(B64[(v >> 18) & 63]).append(B64[(v >> 12) & 63]).append("==");
        } else if (rest == 2) {
            int v = ((data[i] & 0xFF) << 16) | ((data[i + 1] & 0xFF) << 8);
            sb.append(B64[(v >> 18) & 63]).append(B64[(v >> 12) & 63])
              .append(B64[(v >> 6) & 63]).append('=');
        }
        return sb.toString();
    }

    /** Cached data URI per asset (images and fonts). */
    private static final java.util.Map<String, String> IMAGE_CACHE = new java.util.HashMap<>();

    private static String figure(String name) {
        return dataUri("docs/" + name, name.toLowerCase(java.util.Locale.US).endsWith(".png")
                ? "image/png" : "image/jpeg");
    }

    /** Cached @font-face rules for the studio's own hand-lettering (subset WOFF2, ~128 KB total). */
    private static String FONTFACES = null;

    private static String fontFaces() {
        if (FONTFACES != null) return FONTFACES;
        StringBuilder css = new StringBuilder(200 * 1024);
        String[][] faces = {
                {"docs/fonts/Caveat.woff2", "Caveat", "700"},
                {"docs/fonts/PatrickHand.woff2", "Patrick Hand", "400"},
                {"docs/fonts/SpecialElite.woff2", "Special Elite", "400"},
        };
        for (String[] f : faces) {
            String uri = dataUri(f[0], "font/woff2");
            if (uri.isEmpty()) continue;
            css.append("@font-face{font-family:'").append(f[1]).append("';font-style:normal;font-weight:")
               .append(f[2]).append(";font-display:swap;src:url(").append(uri)
               .append(") format('woff2')}");
        }
        FONTFACES = css.toString();
        return FONTFACES;
    }

    /** Reads a bundled asset (docs figure, font) and returns it as a data URI, or "" if absent. */
    private static String dataUri(String path, String mime) {
        String key = "uri:" + path;
        synchronized (IMAGE_CACHE) {
            String cached = IMAGE_CACHE.get(key);
            if (cached != null) return cached;
        }
        String uri = "";
        InputStream in = null;
        try {
            in = assetSource.open(path);
            ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            uri = "data:" + mime + ";base64," + base64(out.toByteArray());
        } catch (Throwable ex) {
            uri = "";
        } finally {
            try { if (in != null) in.close(); } catch (IOException ignored) { }
        }
        synchronized (IMAGE_CACHE) {
            IMAGE_CACHE.put(key, uri);
        }
        return uri;
    }

    // =====================================================================
    // LANDING PAGE — how to use Vortex
    // =====================================================================

    public static String html() {
        StringBuilder sb = new StringBuilder(512 * 1024);
        head(sb, "How to use Vortex Engine",
                "A guided tour of the studio, the game UI and the engine — for this build, offline.");

        // ---- hero -------------------------------------------------------
        sb.append("<section class=\"hero\">")
          .append("<div class=\"hero-txt\">")
          .append("<p class=\"lead\">Make a 2D or 3D game on your phone or your laptop: place objects, "
                  + "write a few lines of JavaScript, Lua or Java, hit <b>Play</b>. This page teaches the "
                  + "engine in the order you will actually use it.</p>")
          .append("<ol class=\"path\">")
          .append("<li><a href=\"#tour\">Take the studio tour</a></li>")
          .append("<li><a href=\"#first\">Build your first game</a></li>")
          .append("<li><a href=\"#gameui\">Style the game's own UI</a></li>")
          .append("<li><a href=\"#theme\">Change the palette</a></li>")
          .append("<li><a href=\"#forge\">Let Forge AI edit your project</a></li>")
          .append("</ol></div>")
          .append("<div class=\"hero-mark\">").append(markImg()).append("</div>")
          .append("</section>");

        // ---- 1. studio tour --------------------------------------------
        section(sb, "tour", "1 · The studio, piece by piece",
                "This is the real editor, not a diagram. Every gold box below is a live part of the window; "
                + "the numbers match the list underneath.");
        shot(sb, "studio-tour.jpg", "Vortex Engine Studio with the paper palette",
                1440, 904, new int[][]{
                        {1, 2, 64}, {2, 932, 712}, {3, 2, 318},
                        {4, 948, 88}, {5, 1430, 132}, {6, 314, 724}, {7, 1308, 2}});
        sb.append("<div class=\"legend\">");
        legend(sb, 1, "Scene Hierarchy", "Every object in the scene, nested like folders. "
                + "Drag one onto another to parent it; the arrow keys nudge, <kbd>Del</kbd> deletes. "
                + "The tags you see (<code>STATIC</code>, <code>DYNAMIC</code>) are what the physics solver uses.");
        legend(sb, 2, "Viewport", "The world itself, 2D or 3D depending on the project. Drag with the left "
                + "button to move an object, wheel to zoom, middle-drag to pan. Blue diamond = spawn, "
                + "gold bar = goal. Everything you place is immediately playable.");
        legend(sb, 3, "Inspector", "The selected object, as a form. Position, scale, rotation, sprite, "
                + "tint, body type, collider, material. Nothing here needs code — it is all saved into the project file.");
        legend(sb, 4, "Dock tabs", "<b>Scene</b> · <b>Script</b> · <b>Logic</b> (blueprint graph) · "
                + "<b>AI</b> (Forge) · <b>Assets</b> · <b>Build</b>. Same project, six ways to look at it.");
        legend(sb, 5, "Code editor", "Your real files: <code>scripts/main.js</code>, "
                + "<code>objects/*.js</code>, <code>ui/hud.js</code>… They all share one scope and their "
                + "<code>onStart</code> / <code>onUpdate</code> hooks run in order, so you can split a game across files.");
        legend(sb, 6, "Console ⇄ Terminal", "The console prints build results, script errors and playtest "
                + "output. The terminal tab is a real shell: run <code>gradle</code>, <code>git</code>, "
                + "or your own tooling without leaving the studio.");
        legend(sb, 7, "Palette + Build", "The swatch strip is the theme changer (see §4): pick a palette, "
                + "import one, or flip to dark mode. <b>Check</b> validates your scripts, <b>Build</b> packages "
                + "an APK, a Windows .exe, Linux binary or an HTML5 page.");
        sb.append("</div>");

        // ---- 2. first game ---------------------------------------------
        section(sb, "first", "2 · Your first game in four steps",
                "Nothing to install, nothing to configure. The starter projects already run — change them.");
        steps(sb, new String[][]{
                {"Pick a starter", "File ▸ New Project, or the ＋ button. <b>Blank 2D</b> gives you a floor, a "
                        + "player and a camera. <b>Blockfall 3D</b> gives you a walkable voxel world. Both are real, "
                        + "runnable projects — not templates to fill in."},
                {"Place things", "Drag in the viewport, then fix the numbers in the Inspector. Two objects with "
                        + "tags <code>player</code> and <code>solid</code> are already a platformer."},
                {"Write the rules", "Open <code>scripts/main.js</code>. This is the whole loop:"},
                {"Press Play", "▶ plays inside the studio; Build packages it. The same script runs on desktop, "
                        + "Android and the exported HTML5 page — no per-platform edits."},
        });
        code(sb, "javascript",
                "function onStart() {\n"
                + "  vortex.setBanner(\"Collect the coins!\");      // title card\n"
                + "  vortex.addScore(0);                          // draws SCORE in the HUD\n"
                + "}\n\n"
                + "function onUpdate(dt) {\n"
                + "  // the joystick on a phone, the arrow keys on a laptop — same call\n"
                + "  vortex.setVel(\"Player\", vortex.joyX() * 8, vortex.getVY(\"Player\"));\n"
                + "  if (vortex.joyY() > 0.3) vortex.jump(\"Player\", 11);\n\n"
                + "  if (vortex.collidesWithTag(\"Player\", \"coin\")) {\n"
                + "    vortex.addScore(10);\n"
                + "    vortex.playSound(\"coin\");\n"
                + "  }\n"
                + "}\n");

        // ---- 3. game UI ------------------------------------------------
        section(sb, "gameui", "3 · The game's own UI, element by element",
                "A running game is not just a scene. This is an actual Vortex session — every box is a UI element "
                + "the engine gives you, and every one of them is a single call.");
        shot(sb, "game-ui.jpg", "A real Blockfall session showing the game UI",
                1200, 720, new int[][]{
                        {1, 2, 2}, {2, 898, 12}, {3, 396, 38},
                        {4, 272, 524}, {5, 336, 624}, {6, 362, 652}});
        sb.append("<div class=\"legend wide\">");
        legend(sb, 1, "HUD row — score, health, fps",
                "<span class=\"hl\">vortex.addScore(10)</span> draws and updates <code>SCORE</code>; "
                + "<span class=\"hl\">vortex.addHealth(-15)</span> animates the health bar colour; FPS is free.");
        legend(sb, 2, "Objective chip", "Top-right status text: <span class=\"hl\">vortex.setObjective(\"Survive the night\")</span>.");
        legend(sb, 3, "Banner", "The title card, fades on its own: <span class=\"hl\">vortex.setBanner(\"BLOCKFALL — mine the hills\")</span>.");
        legend(sb, 4, "Dialog box", "Speaker + typewriter text. "
                + "<span class=\"hl\">vortex.showDialog(\"OPERATOR\", \"Systems online.\", 4.0)</span> — it types "
                + "itself out and auto-hides, and on a phone the same string is used for the touch prompt.");
        legend(sb, 5, "Hint strip", "One-line controls reminder, generated from the project's input mode: "
                + "<span class=\"hl\">vortex.setHint(\"WASD move · SPACE jump\")</span>.");
        legend(sb, 6, "Hotbar", "The first nine entries of your palette/inventory, bound to keys 1–9 and tappable "
                + "on touch: <span class=\"hl\">vortex.setHotbar([\"Grass\",\"Dirt\",\"Stone\"])</span>.");
        sb.append("</div>");
        code(sb, "javascript",
                "function onStart() {\n"
                + "  vortex.setObjective(\"Survive the night\");\n"
                + "  vortex.setHint(\"WASD move · SPACE jump · 1-9 pick\");\n"
                + "  vortex.setHotbar([\"Grass\", \"Dirt\", \"Stone\", \"Sand\", \"Wood\"]);\n"
                + "  vortex.showDialog(\"OPERATOR\", \"Systems online.\", 4.0);\n"
                + "}\n\n"
                + "function onCollide(a, b, tagA, tagB) {\n"
                + "  if (tagB === \"coin\") { vortex.addScore(10); vortex.playSound(\"coin\"); }\n"
                + "  if (tagB === \"hazard\") { vortex.addHealth(-15); vortex.playSound(\"hit\"); }\n"
                + "}\n");
        sb.append("<p class=\"note\">Tip: these elements are the same objects the inspector edits and the "
                + "blueprint graph wires, so a beginner can build a whole menu by dragging nodes and never "
                + "touch the keyboard.</p>");

        // ---- 4. themes --------------------------------------------------
        section(sb, "theme", "4 · Change how it all looks",
                "Vortex ships three palettes and accepts your own. The swatch strip in the top-right "
                + "(<b>Palette</b>) is the theme changer: pick a theme, flip dark mode, or import one.");
        shot(sb, "dark-mode.jpg", "The same project after switching the palette",
                1440, 904, new int[][]{{1, 948, 88}, {2, 2, 318}, {3, 932, 712}});
        sb.append("<div class=\"legend\">");
        legend(sb, 1, "Palette dropdown", "Lists every installed theme with its swatches. "
                + "<b>Dark mode</b> is one click; <b>Import theme…</b> accepts a file or a paste; "
                + "<b>Export</b> writes the current theme back out as JSON.");
        legend(sb, 2, "Everything follows", "Inspector, docks, console, menus, the 2D paper grid and the "
                + "blueprint sheet all read from the same 25 colour slots — no half-themed screens.");
        legend(sb, 3, "The game is untouched", "The editor palette never leaks into your game. Game colours "
                + "live in the project (<code>skyColorHex</code>, entity tints, sun rig), so a theme change "
                + "cannot break the look you shipped.");
        sb.append("</div>");
        sb.append("<p class=\"sub-h\">A theme is just twenty-five colours:</p>");
        code(sb, "json",
                "{\n"
                + "  \"name\": \"Ledger\",\n"
                + "  \"sheet\": \"#fbf6ea\",   \"panel\": \"#f4ecda\",  \"header\": \"#eadfc6\",\n"
                + "  \"ink\": \"#33291d\",     \"textMuted\": \"#6f6558\",\n"
                + "  \"accent\": \"#ffee9c\",  \"ok\": \"#1e7a44\",   \"danger\": \"#c0392b\",\n"
                + "  \"blueprint\": \"#123363\", \"viewport\": \"#fffdf6\"\n"
                + "}\n");
        sb.append("<p class=\"note\">Short on time? Paste a bare list of colours — "
                + "<code>#fffdf6 #f3ebd6 #33291d #ffee9c #b8860b</code> — and Vortex maps them onto the "
                + "25 slots by lightness and saturation. Themes live in <code>~/.vortex/themes/</code>, so "
                + "you can hand them to a friend as one file.</p>");

        // ---- 5. forge ---------------------------------------------------
        section(sb, "forge", "5 · Forge AI works on <i>your</i> project",
                "Forge is not a code generator bolted on the side. When you talk to it, it receives the whole "
                + "workspace — every file's source, every object, the blueprint graph and the runtime — and it "
                + "edits those files.");
        sb.append("<div class=\"grid2\">")
          .append("<div class=\"card\"><h4>What it reads</h4><ul class=\"ticks\">")
          .append("<li>Every script file, in full, not a preview</li>")
          .append("<li>Every scene object: tag, mesh, position, scale, physics, material</li>")
          .append("<li>Blueprint nodes and wires, plus flow rules</li>")
          .append("<li>Project facts: mode, VM, gravity, sun, camera, audio, voxel seed</li>")
          .append("<li>The environment it is running in: desktop vs phone, which VMs exist</li>")
          .append("</ul></div>")
          .append("<div class=\"card\"><h4>What it does with it</h4><ul class=\"ticks\">")
          .append("<li>Extends the file that already holds the feature (<code>edit_file</code>)</li>")
          .append("<li>Keeps your names, tags, hooks and file layout</li>")
          .append("<li>Adds concrete actions, then runs a headless playtest</li>")
          .append("<li>Asks one-tap questions when a decision is genuinely yours</li>")
          .append("<li>Never replaces your game with a fresh sample</li>")
          .append("</ul></div></div>");
        sb.append("<p class=\"sub-h\">Good prompts, and why they work:</p>");
        sb.append("<table class=\"prompts\"><tr><th>Instead of…</th><th>Say…</th></tr>");
        prompt(sb, "make a game",
                "\"add a double jump to the player in <code>scripts/main.js</code> and put a coin counter in the HUD\"");
        prompt(sb, "make it better",
                "\"the ArenaN walls feel floaty — make them bouncy and raise the sun a little\"");
        prompt(sb, "add enemies",
                "\"add three drones that chase the player, spawn them from the wave loop, and give them a hit sound\"");
        sb.append("</table>");

        // ---- 6. audio ---------------------------------------------------
        section(sb, "audio", "6 · Audio that does not blow the doors off",
                "Chiptune synthesis is loud by nature. Vortex mixes to game-audio practice instead of "
                + "slamming 0 dBFS, and v4.9.2 turned the whole bed down.");
        sb.append("<table class=\"mix\"><tr><th>Bus</th><th>Target</th><th>Why</th></tr>")
          .append(mixRow("Whole game mix", "≈ −23 LUFS, −2 dBTP ceiling",
                  "Console guidance (SCEA) — leaves room for the player's own music"))
          .append(mixRow("Effects", "peaks ≈ −20 dBFS",
                  "One-shots must be audible without clipping the sum"))
          .append(mixRow("Music bed", "6–12 LU under the effects",
                  "Music supports the scene; it should never fight a hit or a dialog line"))
          .append("</table>")
          .append("<p class=\"note\">Practically: the synth's voices were trimmed ~6 dB, the music bus sits at "
                  + "42% of the effects bus, and a soft-knee limiter rounds transients instead of clipping them. "
                  + "If you want a louder game, raise the bus — not the individual voices.</p>");

        // ---- api pointer ------------------------------------------------
        sb.append("<section id=\"api\" class=\"cta\">")
          .append("<h2>Ready for the full reference?</h2>")
          .append("<p>Every call, every topic, generated from this build.</p>")
          .append("<a class=\"button\" href=\"api.html\">Open the API reference →</a>")
          .append("</section>");

        footer(sb);
        return close(sb);
    }

    // =====================================================================
    // API REFERENCE — the old landing page, now a second file
    // =====================================================================

    public static String apiHtml() {
        StringBuilder sb = new StringBuilder(128 * 1024);
        head(sb, "Vortex Engine — API Reference",
                "Every topic and every engine call, generated from this build.");
        sb.append("<p class=\"backrow\"><a class=\"button ghost\" href=\"index.html\">← How to use Vortex</a></p>");

        List<VortexDocs.Topic> topics = VortexDocs.all();
        sb.append("<nav class=\"toc\"><b>Topics</b><ul>");
        for (VortexDocs.Topic t : topics) {
            sb.append("<li><a href=\"#").append(esc(t.id)).append("\">").append(esc(t.title)).append("</a></li>");
        }
        sb.append("<li><a href=\"#api-index\">Full API index</a></li></ul></nav>");

        for (VortexDocs.Topic t : topics) {
            sb.append("<section id=\"").append(esc(t.id)).append("\">")
              .append("<h2>").append(esc(t.title)).append("</h2>")
              .append("<p class=\"summary\">").append(esc(t.summary)).append("</p>")
              .append(markdownish(t.body))
              .append("</section>");
        }

        sb.append("<section id=\"api-index\"><h2>Full API index</h2>")
          .append("<p class=\"summary\">Every call the script engine exposes, generated from the ")
          .append("engine source so it cannot drift from the build.</p><div class=\"api\">");
        String[] names = VortexScriptEngine.ENGINE_API_NAMES;
        for (int i = 0; i < names.length; i++) {
            sb.append("<code>vortex.").append(esc(names[i])).append("()</code>");
            if (i % 4 == 3) sb.append("<br>");
        }
        sb.append("</div></section>");
        footer(sb);
        return close(sb);
    }

    // ------------------------------------------------------------------ writing

    /** Writes {@code index.html} and {@code api.html} into {@code dir} and returns the landing page. */
    public static File write(File dir) throws IOException {
        if (dir == null) throw new IOException("no directory");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        writeFile(new File(dir, "index.html"), html());
        writeFile(new File(dir, "api.html"), apiHtml());
        return new File(dir, "index.html");
    }

    private static void writeFile(File out, String content) throws IOException {
        Writer w = new OutputStreamWriter(new FileOutputStream(out), Charset.forName("UTF-8"));
        try {
            w.write(content);
        } finally {
            try { w.close(); } catch (IOException ignored) { }
        }
    }

    /** Where the IDE keeps its generated copy. */
    public static File defaultFile() {
        File dir = new File(System.getProperty("user.home", "."), ".vortex-docs");
        try {
            return write(dir);
        } catch (IOException ex) {
            File tmp = new File(System.getProperty("java.io.tmpdir", "."), "vortex-docs-index.html");
            try {
                writeFile(tmp, html());
            } catch (IOException ignored) { }
            return tmp;
        }
    }

    // ------------------------------------------------------------------ page pieces

    private static void head(StringBuilder sb, String title, String blurb) {
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>").append(esc(title)).append("</title><style>").append(css())
          .append("</style></head><body><div class=\"sheet\">")
          .append("<header><span class=\"build\">v4.9.2 · offline</span>")
          .append("<h1>").append(esc(title)).append("</h1>")
          .append("<p class=\"sub\">").append(esc(blurb)).append("</p></header>");
    }

    private static void footer(StringBuilder sb) {
        sb.append("<footer>Vortex Engine — generated from this build on ")
          .append(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date()))
          .append(". Works with the network off.</footer></div></body></html>");
    }

    private static String close(StringBuilder sb) {
        return sb.toString();
    }

    private static void section(StringBuilder sb, String id, String title, String lede) {
        sb.append("<section id=\"").append(id).append("\"><h2>").append(title).append("</h2>")
          .append("<p class=\"summary\">").append(lede).append("</p>");
    }

    /** A screenshot with numbered pins positioned in percentage coordinates over the image. */
    private static void shot(StringBuilder sb, String name, String alt, int w, int h, int[][] pins) {
        String uri = figure(name);
        if (uri.isEmpty()) {
            sb.append("<p class=\"note\">(figure missing — the numbered explanation below still applies)</p>");
        } else {
            sb.append("<figure class=\"shot\"><img alt=\"").append(esc(alt)).append("\" src=\"").append(uri).append("\">");
        }
        sb.append("<div class=\"pins\">");
        for (int[] p : pins) {
            double left = p[1] * 100.0 / w;
            double top = p[2] * 100.0 / h;
            sb.append("<span class=\"pin\" style=\"left:").append(fmt(left)).append("%;top:")
              .append(fmt(top)).append("%\">").append(p[0]).append("</span>");
        }
        sb.append("</div>");
        if (!uri.isEmpty()) sb.append("</figure>");
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.US, "%.2f", v);
    }

    private static void legend(StringBuilder sb, int n, String title, String body) {
        sb.append("<div class=\"item\"><span class=\"num\">").append(n).append("</span>")
          .append("<div><b>").append(title).append("</b><br><span class=\"body\">")
          .append(body).append("</span></div></div>");
    }

    private static void steps(StringBuilder sb, String[][] rows) {
        sb.append("<ol class=\"steps\">");
        for (String[] r : rows) {
            sb.append("<li><b>").append(r[0]).append("</b> ").append(r[1]).append("</li>");
        }
        sb.append("</ol>");
    }

    private static void code(StringBuilder sb, String lang, String body) {
        sb.append("<pre class=\"code\"><span class=\"lang\">").append(lang).append("</span>")
          .append(esc(body)).append("</pre>");
    }

    private static void prompt(StringBuilder sb, String bad, String good) {
        sb.append("<tr><td class=\"bad\">“").append(bad).append("”</td><td class=\"good\">")
          .append(good).append("</td></tr>");
    }

    private static String mixRow(String bus, String target, String why) {
        return "<tr><td>" + bus + "</td><td class=\"mono\">" + target + "</td><td>" + why + "</td></tr>";
    }

    /** The real app icon (the same PNG the launcher and taskbar use). */
    private static String markImg() {
        String uri = figure("mark.png");
        if (uri.isEmpty()) return logoSvg();
        return "<img width=\"176\" height=\"176\" alt=\"Vortex Engine\" src=\"" + uri + "\">";
    }

    /** Fallback mark, inline so it needs no file. */
    private static String logoSvg() {
        return "<svg viewBox=\"0 0 200 200\" width=\"176\" height=\"176\" role=\"img\" aria-label=\"Vortex mark\">"
                + "<rect x=\"4\" y=\"4\" width=\"192\" height=\"192\" rx=\"40\" fill=\"#fffdf6\" stroke=\"#33291d\" stroke-width=\"3\" opacity=\".55\"/>"
                + "<path d=\"M92 132 C60 132 44 108 52 84 C60 58 92 52 112 68 C132 84 128 116 108 132"
                + " C88 148 58 140 52 118\" fill=\"none\" stroke=\"#c99a1e\" stroke-width=\"14\" stroke-linecap=\"round\"/>"
                + "<path d=\"M112 68 C140 74 156 100 150 126 C144 150 118 162 98 152\" fill=\"none\" "
                + "stroke=\"#33291d\" stroke-width=\"9\" stroke-linecap=\"round\"/>"
                + "<circle cx=\"96\" cy=\"104\" r=\"13\" fill=\"#33291d\"/><circle cx=\"96\" cy=\"104\" r=\"5\" fill=\"#e2b83c\"/>"
                + "</svg>";
    }

    // ------------------------------------------------------------------ helpers

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String markdownish(String body) {
        if (body == null) return "";
        StringBuilder out = new StringBuilder(body.length() + 64);
        String[] lines = body.split("\n", -1);
        boolean inList = false;
        boolean inCode = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            if (line.trim().startsWith("```")) {
                if (inCode) {
                    out.append("</pre>");
                    inCode = false;
                } else {
                    out.append("<pre class=\"code\">");
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                out.append(esc(line)).append("\n");
                continue;
            }
            String t = line.trim();
            if (t.startsWith("- ") || t.startsWith("* ")) {
                if (!inList) { out.append("<ul>"); inList = true; }
                out.append("<li>").append(inline(t.substring(2))).append("</li>");
                continue;
            }
            if (inList) { out.append("</ul>"); inList = false; }
            if (t.startsWith("### ")) {
                out.append("<h3>").append(inline(t.substring(4))).append("</h3>");
            } else if (t.startsWith("## ")) {
                out.append("<h3>").append(inline(t.substring(3))).append("</h3>");
            } else if (t.startsWith("# ")) {
                out.append("<h3>").append(inline(t.substring(2))).append("</h3>");
            } else if (t.isEmpty()) {
                out.append("");
            } else {
                out.append("<p>").append(inline(t)).append("</p>");
            }
        }
        if (inList) out.append("</ul>");
        if (inCode) out.append("</pre>");
        return out.toString();
    }

    private static String inline(String s) {
        String out = esc(s);
        out = out.replaceAll("`([^`]+)`", "<code>$1</code>");
        out = out.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        return out;
    }

    // ------------------------------------------------------------------ style

    // ------------------------------------------------------------------ style
    //
    // The paper-trader look the docs already had: a wooden desk under a cream sheet with ruled
    // lines, Caveat headings, Patrick Hand body, Special Elite code, red-ink section rules and a
    // dashed kraft TOC. Kept verbatim as the base (v4.9.2 only *added* the annotated-figure,
    // legend, step and card pieces below it) so the docs still read as the same document.
    // The three hand faces are the studio's own bundled TTFs, subset to WOFF2 and inlined, so the
    // lettering actually renders offline instead of falling back to Comic Sans.
    private static final String CSS_BASE =
            ":root{--paper:#fffdf6;--paper2:#fbf6ea;--deep:#f3ebd6;--ink:#33291d;--pencil:#6f6558;"
            + "--green:#1e7a44;--red:#c0392b;--blue:#2456a6;--gold:#b8860b;--highlight:#ffee9c;}"
            + "*{box-sizing:border-box}"
            + "html{background:#6b4523}"
            + "body{margin:0;padding:26px 14px 60px;background:#6b4523;"
            + "font-family:'Patrick Hand','Segoe Print','Comic Sans MS',ui-rounded,cursive,sans-serif;"
            + "color:var(--ink);font-size:19px;line-height:1.6;}"
            + ".sheet{max-width:920px;margin:0 auto;background:var(--paper);"
            + "padding:34px 38px 44px;border-radius:3px;"
            + "box-shadow:0 1px 0 #e5dac2 inset,0 18px 45px rgba(28,15,3,.45);"
            + "background-image:repeating-linear-gradient(0deg,rgba(58,118,197,.055) 0 1px,transparent 1px 28px),"
            + "radial-gradient(120% 90% at 50% 0,transparent 60%,rgba(90,60,20,.10) 100%);}"
            + "h1,h2,h3{font-family:'Caveat','Segoe Print','Comic Sans MS',cursive;font-weight:700;margin:0}"
            + "h1{font-size:46px;letter-spacing:.5px}"
            + "h2{font-size:30px;margin:34px 0 6px;border-bottom:2px solid rgba(203,67,53,.45);"
            + "padding-bottom:4px;display:inline-block}"
            + "h3{font-size:22px;margin:20px 0 4px;color:var(--pencil)}"
            + ".sub{margin:2px 0 0;color:var(--pencil)}"
            + "header{border-bottom:1px solid rgba(203,67,53,.35);padding-bottom:14px;margin-bottom:18px}"
            + "nav.toc{background:var(--paper2);border:1px dashed #cbb894;border-radius:6px;padding:12px 16px;"
            + "margin:0 0 8px}"
            + "nav.toc ul{margin:6px 0 0;padding-left:20px;columns:2}"
            + "nav.toc a{color:var(--blue);text-decoration:none}"
            + "nav.toc a:hover{text-decoration:underline}"
            + "section{margin-bottom:26px;scroll-margin-top:12px}"
            + ".summary{color:var(--pencil);font-style:italic;margin:6px 0 10px}"
            + "p{margin:6px 0}"
            + "ul{margin:6px 0 6px 4px;padding-left:20px}"
            + "li{margin:2px 0}"
            + "code{font-family:'Special Elite','Courier New',monospace;font-size:16px;"
            + "background:var(--deep);border-radius:3px;padding:1px 5px}"
            + "pre{background:var(--paper2);border:1px solid #ddd0b4;border-left:4px solid var(--green);"
            + "border-radius:4px;padding:12px 14px;overflow:auto;margin:10px 0}"
            + "pre code{background:none;padding:0;font-size:16px;line-height:1.55}"
            + ".api code{display:inline-block;min-width:210px;margin:3px 8px 3px 0}"
            + ".trow{display:grid;grid-template-columns:1fr 2fr;gap:6px;border-bottom:1px dotted #d8c9a8;"
            + "padding:3px 0}"
            + "footer{margin-top:36px;padding-top:12px;border-top:1px solid rgba(203,67,53,.35);"
            + "color:var(--pencil);font-size:14px}"
            + "@media (max-width:640px){body{padding:10px 6px 40px}.sheet{padding:20px 16px 30px}"
            + "nav.toc ul{columns:1}h1{font-size:36px}}";

    // ---- v4.9.2 additions, drawn in the same ink: annotated figures, legend chips,
    //      steps, cards, tables and the hand-lettered call-to-action.
    private static final String CSS_ADD =
            ".shot{position:relative;margin:12px 0 16px;border:1px solid #d8c9a8;border-radius:4px;"
            + "overflow:hidden;background:var(--paper);"
            + "box-shadow:0 1px 0 #e5dac2 inset,0 10px 26px rgba(28,15,3,.22)}"
            + ".shot img{display:block;width:100%;height:auto}"
            + ".pins{position:absolute;inset:0;pointer-events:none}"
            + ".pin{position:absolute;transform:translate(-50%,-50%);width:26px;height:26px;border-radius:50%;"
            + "background:var(--ink);color:var(--highlight);border:2px solid var(--gold);"
            + "font-family:'Special Elite',monospace;font-size:14px;line-height:22px;text-align:center;"
            + "box-shadow:0 2px 6px rgba(0,0,0,.35)}"
            + ".legend{display:grid;grid-template-columns:1fr 1fr;gap:8px 14px;margin:4px 0 10px}"
            + ".legend.wide{grid-template-columns:1fr}"
            + "@media (max-width:640px){.legend{grid-template-columns:1fr}}"
            + ".item{display:flex;gap:9px;align-items:flex-start;background:var(--paper2);"
            + "border:1px dashed #cbb894;border-radius:6px;padding:9px 12px}"
            + ".num{flex:0 0 25px;width:25px;height:25px;border-radius:50%;background:var(--ink);"
            + "color:var(--highlight);font-family:'Special Elite',monospace;font-size:14px;line-height:25px;"
            + "text-align:center}"
            + ".body{font-size:18px;color:#463a29}"
            + ".steps{margin:8px 0 8px 4px;padding-left:22px}"
            + ".steps li{margin:7px 0}"
            + ".note{background:var(--paper2);border:1px dashed #cbb894;border-radius:6px;padding:10px 14px;"
            + "font-size:18px}"
            + ".grid2{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:10px 0}"
            + "@media (max-width:640px){.grid2{grid-template-columns:1fr}}"
            + ".card{background:var(--paper2);border:1px solid #e3d7bd;border-left:4px solid var(--gold);"
            + "border-radius:5px;padding:10px 14px}"
            + ".card h4{margin:0 0 6px;font-family:'Caveat',cursive;font-size:22px;font-weight:700}"
            + ".ticks{margin:0;padding-left:18px}"
            + ".ticks li{margin:3px 0;font-size:18px}"
            + "table{border-collapse:collapse;width:100%;margin:10px 0;font-size:18px}"
            + "th,td{text-align:left;padding:6px 8px;border-bottom:1px dotted #d8c9a8;vertical-align:top}"
            + "th{font-family:'Special Elite',monospace;font-size:13px;letter-spacing:.06em;"
            + "text-transform:uppercase;color:var(--pencil)}"
            + ".prompts .bad{color:var(--red);text-decoration:line-through;width:33%}"
            + ".prompts .good{color:#2b5d34}"
            + ".mono{font-family:'Special Elite',monospace;font-size:15px}"
            + ".cta{margin:30px 0 0;padding:18px 20px;background:var(--paper2);border:1px dashed #cbb894;"
            + "border-radius:6px;text-align:center}"
            + ".cta h2{margin:0 0 4px}"
            + ".button{display:inline-block;margin-top:8px;background:var(--ink);color:var(--paper);"
            + "font-family:'Caveat',cursive;font-size:24px;padding:6px 18px;border-radius:4px;"
            + "text-decoration:none}"
            + ".button.ghost{background:transparent;color:var(--ink);border:1px solid var(--ink);font-size:20px}"
            + ".backrow{margin:10px 0 0}"
            + ".code{position:relative;padding-top:16px}"
            + ".code .lang{position:absolute;top:0;right:0;background:var(--deep);color:var(--pencil);"
            + "font-family:'Special Elite',monospace;font-size:11px;padding:3px 8px;border-radius:0 4px 0 4px;"
            + "letter-spacing:.08em;text-transform:uppercase;border-left:1px solid #ddd0b4;"
            + "border-bottom:1px solid #ddd0b4}"
            + ".hl{background:var(--highlight);padding:0 5px;border-radius:3px;"
            + "font-family:'Special Elite',monospace;font-size:15.5px;box-shadow:0 1px 0 rgba(184,134,11,.35)}"
            + "kbd{font-family:'Special Elite',monospace;font-size:14px;border:1px solid #cbb894;"
            + "border-bottom-width:2px;border-radius:3px;padding:1px 5px;background:var(--paper2)}"
            + ".hero{display:flex;gap:22px;align-items:center;margin:14px 0 4px;flex-wrap:wrap}"
            + ".hero-txt{flex:1 1 320px}"
            + ".hero-mark{flex:0 0 auto}"
            + ".build{float:right;color:var(--pencil);font-family:'Special Elite',monospace;font-size:13px}"
            + ".lead{font-size:19px}"
            + ".path{margin:8px 0 0;padding-left:20px}"
            + ".path li{margin:3px 0}"
            + ".path a{color:var(--blue);text-decoration:none}"
            + ".path a:hover{text-decoration:underline}";

    private static String css() {
        return fontFaces() + CSS_BASE + CSS_ADD;
    }
}
