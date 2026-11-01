package com.vortex.engine.core.docs;

import com.vortex.engine.core.script.VortexScriptEngine;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;

/**
 * Renders the whole documentation corpus into ONE self-contained {@code index.html} — no network, no
 * CDN, no build step. The IDE's "API" button writes this file and opens it in the browser (desktop)
 * or an in-app WebView (Android), so "API Reference" always shows the docs that shipped with the
 * exact engine build the user is running.
 */
public final class DocsSite {

    private DocsSite() { }

    /** Full standalone HTML document. */
    public static String html() {
        StringBuilder sb = new StringBuilder(64 * 1024);
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
          .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
          .append("<title>Vortex Engine — API Reference</title><style>")
          .append(CSS)
          .append("</style></head><body><div class=\"sheet\">")
          .append("<header><h1>Vortex Engine</h1>")
          .append("<p class=\"sub\">API reference &amp; guide — generated from this build, works offline.</p></header>")
          .append("<nav class=\"toc\"><b>Topics</b><ul>");

        List<VortexDocs.Topic> topics = VortexDocs.all();
        for (VortexDocs.Topic t : topics) {
            sb.append("<li><a href=\"#").append(esc(t.id)).append("\">").append(esc(t.title)).append("</a></li>");
        }
        sb.append("<li><a href=\"#api\">Full API index</a></li></ul></nav>");

        for (VortexDocs.Topic t : topics) {
            sb.append("<section id=\"").append(esc(t.id)).append("\">")
              .append("<h2>").append(esc(t.title)).append("</h2>")
              .append("<p class=\"summary\">").append(esc(t.summary)).append("</p>")
              .append(markdownish(t.body))
              .append("</section>");
        }

        sb.append("<section id=\"api\"><h2>Full API index</h2>")
          .append("<p class=\"summary\">Every call the script engine exposes, generated from the ")
          .append("engine source so it cannot drift from the build.</p><div class=\"api\">");
        String[] names = VortexScriptEngine.ENGINE_API_NAMES;
        for (int i = 0; i < names.length; i++) {
            sb.append("<code>vortex.").append(esc(names[i])).append("()</code>");
            if (i % 4 == 3) sb.append("<br>");
        }
        sb.append("</div></section>");

        sb.append("<footer>Vortex Engine — offline documentation. ")
          .append("Generated ").append(new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                  .format(new java.util.Date()))
          .append(". Nothing here needs a connection.</footer>")
          .append("</div></body></html>");
        return sb.toString();
    }

    /** Writes {@code index.html} into {@code dir} (created if needed) and returns the file. */
    public static File write(File dir) throws IOException {
        if (dir == null) throw new IOException("no directory");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File out = new File(dir, "index.html");
        Writer w = new OutputStreamWriter(new FileOutputStream(out), Charset.forName("UTF-8"));
        try {
            w.write(html());
        } finally {
            try { w.close(); } catch (IOException ignored) { }
        }
        return out;
    }

    /** Where the IDE keeps its generated copy. */
    public static File defaultFile() {
        File dir = new File(System.getProperty("user.home", "."), ".vortex-docs");
        try {
            return write(dir);
        } catch (IOException ex) {
            File tmp = new File(System.getProperty("java.io.tmpdir", "."), "vortex-docs-index.html");
            try {
                Writer w = new OutputStreamWriter(new FileOutputStream(tmp), Charset.forName("UTF-8"));
                try { w.write(html()); } finally { try { w.close(); } catch (IOException ignored) { } }
            } catch (IOException ignored) { }
            return tmp;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Tiny renderer for the doc bodies: headings, bullets, fenced code and paragraphs. */
    private static String markdownish(String body) {
        if (body == null) return "";
        StringBuilder out = new StringBuilder();
        String[] lines = body.split("\n", -1);
        boolean inCode = false;
        boolean inList = false;
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            boolean fence = line.trim().startsWith("```");
            if (fence) {
                if (inList) { out.append("</ul>"); inList = false; }
                out.append(inCode ? "</pre>" : "<pre>");
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                out.append(esc(line)).append('\n');
                continue;
            }
            String t = line.trim();
            if (t.isEmpty()) {
                if (inList) { out.append("</ul>"); inList = false; }
                continue;
            }
            if (t.startsWith("### ")) { out.append("<h3>").append(esc(t.substring(4))).append("</h3>"); continue; }
            if (t.startsWith("## "))  { out.append("<h3>").append(esc(t.substring(3))).append("</h3>"); continue; }
            if (t.startsWith("# "))   { continue; }   // page title already rendered
            if (t.startsWith("- ") || t.startsWith("* ")) {
                if (!inList) { out.append("<ul>"); inList = true; }
                out.append("<li>").append(inline(t.substring(2))).append("</li>");
                continue;
            }
            if (inList) { out.append("</ul>"); inList = false; }
            out.append("<p>").append(inline(t)).append("</p>");
        }
        if (inList) out.append("</ul>");
        if (inCode) out.append("</pre>");
        return out.toString();
    }

    /** `code`, **bold** and the odd table row, without pulling in a markdown library. */
    private static String inline(String s) {
        String e = esc(s);
        e = e.replaceAll("`([^`]+)`", "<code>$1</code>");
        e = e.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        if (e.startsWith("|")) {
            String[] cells = e.split("\\|");
            StringBuilder row = new StringBuilder("<span class=\"trow\">");
            for (String c : cells) {
                String cc = c.trim();
                if (cc.isEmpty() || cc.matches("[-: ]+")) continue;
                row.append("<span>").append(cc).append("</span>");
            }
            row.append("</span>");
            return row.toString();
        }
        return e;
    }

    /** Paper theme: cream stock, ink text, pencil rules — same palette as the studio. */
    private static final String CSS =
            ":root{--paper:#fbf6ea;--paper2:#f4ecda;--deep:#eadfc6;--ink:#33291d;--pencil:#6f6558;" +
            "--green:#1e7a44;--red:#c0392b;--blue:#2456a6;}" +
            "*{box-sizing:border-box}" +
            "html{background:#6b4523}" +
            "body{margin:0;padding:26px 14px 60px;background:#6b4523;" +
            "font-family:'Patrick Hand','Segoe Print','Comic Sans MS',ui-rounded,cursive,sans-serif;" +
            "color:var(--ink);font-size:17px;line-height:1.55;}" +
            ".sheet{max-width:920px;margin:0 auto;background:var(--paper);" +
            "padding:34px 38px 44px;border-radius:3px;" +
            "box-shadow:0 1px 0 #e5dac2 inset,0 18px 45px rgba(28,15,3,.45);" +
            "background-image:repeating-linear-gradient(0deg,rgba(58,118,197,.055) 0 1px,transparent 1px 28px)," +
            "radial-gradient(120% 90% at 50% 0,transparent 60%,rgba(90,60,20,.10) 100%);}" +
            "h1,h2,h3{font-family:'Caveat','Segoe Print','Comic Sans MS',cursive;font-weight:700;margin:0}" +
            "h1{font-size:46px;letter-spacing:.5px}" +
            "h2{font-size:30px;margin:34px 0 6px;border-bottom:2px solid rgba(203,67,53,.45);" +
            "padding-bottom:4px;display:inline-block}" +
            "h3{font-size:22px;margin:20px 0 4px;color:var(--pencil)}" +
            ".sub{margin:2px 0 0;color:var(--pencil)}" +
            "header{border-bottom:1px solid rgba(203,67,53,.35);padding-bottom:14px;margin-bottom:18px}" +
            "nav.toc{background:var(--paper2);border:1px dashed #cbb894;border-radius:6px;padding:12px 16px;" +
            "margin:0 0 8px}" +
            "nav.toc ul{margin:6px 0 0;padding-left:20px;columns:2}" +
            "nav.toc a{color:var(--blue);text-decoration:none}" +
            "nav.toc a:hover{text-decoration:underline}" +
            "section{margin-bottom:26px}" +
            ".summary{color:var(--pencil);font-style:italic;margin:6px 0 10px}" +
            "p{margin:6px 0}" +
            "ul{margin:6px 0 6px 4px;padding-left:20px}" +
            "li{margin:2px 0}" +
            "code{font-family:'Special Elite','Courier New',monospace;font-size:14.5px;" +
            "background:var(--deep);border-radius:3px;padding:1px 5px}" +
            "pre{background:var(--paper2);border:1px solid #ddd0b4;border-left:4px solid var(--green);" +
            "border-radius:4px;padding:12px 14px;overflow:auto;margin:10px 0}" +
            "pre code{background:none;padding:0;font-size:14px;line-height:1.5}" +
            ".api code{display:inline-block;min-width:210px;margin:3px 8px 3px 0}" +
            ".trow{display:grid;grid-template-columns:1fr 2fr;gap:6px;border-bottom:1px dotted #d8c9a8;" +
            "padding:3px 0}" +
            "footer{margin-top:36px;padding-top:12px;border-top:1px solid rgba(203,67,53,.35);" +
            "color:var(--pencil);font-size:14px}" +
            "@media (max-width:640px){body{padding:10px 6px 40px}.sheet{padding:20px 16px 30px}" +
            "nav.toc ul{columns:1}h1{font-size:36px}}";
}
