package com.vortex.engine.core.asset;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Tiled (.tmx / .tsx) map reader — implements the open TMX format, no Tiled code involved.
 *
 * Supported: orthogonal + isometric metadata, embedded and external tilesets, layers encoded as
 * CSV or base64 (raw / zlib / gzip), global tile IDs with the three flip flags, object groups
 * (rectangles, ellipses, points, polygons reported as bounding boxes).
 *
 * Deliberately dependency-free and Android-safe: no AWT/Base64/XML-schema APIs, only DOM +
 * java.util.zip, so the same parser runs on the phone, on the desktop build and in HTML5 export.
 */
public final class TmxMap {

    public static final long FLIP_H = 0x80000000L;
    public static final long FLIP_V = 0x40000000L;
    public static final long FLIP_D = 0x20000000L;
    public static final long GID_MASK = 0x0FFFFFFFL;

    public String name = "map";
    public int width, height, tileWidth = 16, tileHeight = 16;
    public String orientation = "orthogonal";
    public String renderOrder = "right-down";
    public String backgroundHex = "";
    public final List<Tileset> tilesets = new ArrayList<>();
    public final List<Layer> layers = new ArrayList<>();
    public final List<ObjectGroup> objectGroups = new ArrayList<>();
    /** Properties on the map itself (Tiled "Custom Properties"), e.g. `theme=forest`. */
    public final Map<String, String> properties = new LinkedHashMap<>();

    // ------------------------------------------------------------------ model

    public static final class Tileset {
        public int firstGid = 1;
        public String name = "tiles";
        public int tileWidth = 16, tileHeight = 16, columns = 1, tileCount = 0;
        public int margin = 0, spacing = 0;
        public int imageWidth, imageHeight;
        public String imageSource = "";
        /** Absolute path to the tileset image, resolved while parsing ("" when unavailable). */
        public String imagePath = "";
        public final Map<String, String> properties = new LinkedHashMap<>();
    }

    public static final class Layer {
        public String name = "layer";
        public int width, height;
        public float offsetX, offsetY;
        public float opacity = 1f;
        public boolean visible = true;
        public long[] gids = new long[0];
    }

    public static final class MapObject {
        public String id = "";
        public String name = "";
        public String type = "";
        public String cls = "";
        public float x, y, w, h, rotation;
        public boolean isPoint, isEllipse, isPolygon;
        public long gid = -1; // tile objects
        public final Map<String, String> properties = new LinkedHashMap<>();

        /** Best-effort classification without asking the user. */
        public boolean looksSolid() {
            String hay = (name + " " + type + " " + cls + " " + propertyValue("solid")).toLowerCase(Locale.US);
            return hay.contains("solid") || hay.contains("collision") || hay.contains("collider")
                    || hay.contains("wall") || hay.contains("block") || hay.contains("platform");
        }

        public boolean looksSpawn() {
            String hay = (name + " " + type + " " + cls).toLowerCase(Locale.US);
            return hay.contains("spawn") || hay.contains("player") || hay.contains("start");
        }

        public String propertyValue(String key) {
            String v = properties.get(key);
            return v == null ? "" : v;
        }
    }

    public static final class ObjectGroup {
        public String name = "objects";
        public float opacity = 1f;
        public boolean visible = true;
        public final List<MapObject> objects = new ArrayList<>();
    }

    // ------------------------------------------------------------------ helpers

    public int tilePixelWidth() { return width * tileWidth; }
    public int tilePixelHeight() { return height * tileHeight; }

    public Tileset tilesetFor(long gid) {
        long id = gid & GID_MASK;
        Tileset best = null;
        for (Tileset ts : tilesets) {
            if (ts.firstGid <= id && (best == null || ts.firstGid > best.firstGid)) best = ts;
        }
        return best;
    }

    public static boolean isFlippedH(long gid) { return (gid & FLIP_H) != 0; }
    public static boolean isFlippedV(long gid) { return (gid & FLIP_V) != 0; }
    public static boolean isFlippedD(long gid) { return (gid & FLIP_D) != 0; }
    public static long rawGid(long gid) { return gid & GID_MASK; }

    // ------------------------------------------------------------------ parsing

    public static TmxMap parse(File file) throws Exception {
        try (InputStream in = new FileInputStream(file)) {
            TmxMap map = parse(in, file.getAbsoluteFile().getParentFile());
            if (map.name == null || map.name.isEmpty() || "map".equals(map.name)) {
                String n = file.getName();
                int dot = n.lastIndexOf('.');
                map.name = dot > 0 ? n.substring(0, dot) : n;
            }
            return map;
        }
    }

    public static TmxMap parseText(String xml) throws Exception {
        return parse(new ByteArrayInputStream(xml.getBytes("UTF-8")), null);
    }

    public static TmxMap parse(InputStream in, File baseDir) throws Exception {
        DocumentBuilderSafe db = new DocumentBuilderSafe();
        Document doc = db.parse(in);
        Element root = doc.getDocumentElement();
        if (root == null || !"map".equalsIgnoreCase(root.getTagName())) {
            throw new IllegalArgumentException("Not a Tiled .tmx map (missing <map>)");
        }
        TmxMap map = new TmxMap();
        map.width = intAttr(root, "width", 0);
        map.height = intAttr(root, "height", 0);
        map.tileWidth = intAttr(root, "tilewidth", 16);
        map.tileHeight = intAttr(root, "tileheight", 16);
        map.orientation = attr(root, "orientation", "orthogonal");
        map.renderOrder = attr(root, "renderorder", "right-down");
        map.backgroundHex = attr(root, "backgroundcolor", "");

        for (Element child : children(root)) {
            String tag = child.getTagName().toLowerCase(Locale.US);
            switch (tag) {
                case "properties":
                    readProperties(child, map.properties);
                    break;
                case "tileset":
                    map.tilesets.add(readTileset(child, baseDir));
                    break;
                case "layer":
                    map.layers.add(readLayer(child, map));
                    break;
                case "objectgroup":
                    map.objectGroups.add(readObjectGroup(child));
                    break;
                case "group":
                    // nested groups: flatten layers/objects one level (Tiled group = folder)
                    for (Element sub : children(child)) {
                        String st = sub.getTagName().toLowerCase(Locale.US);
                        if ("layer".equals(st)) map.layers.add(readLayer(sub, map));
                        else if ("objectgroup".equals(st)) map.objectGroups.add(readObjectGroup(sub));
                    }
                    break;
                default:
                    break;
            }
        }
        if (map.tilesets.isEmpty()) {
            throw new IllegalArgumentException("Map has no tileset");
        }
        return map;
    }

    private static Tileset readTileset(Element el, File baseDir) throws Exception {
        String source = attr(el, "source", "");
        if (!source.isEmpty()) {
            // External .tsx: parse that document's <tileset> root; image paths resolve next to the
            // .tsx file (which is not always the .tmx folder).
            File tsx = baseDir == null ? new File(source) : new File(baseDir, source);
            File tsxDir = tsx.getAbsoluteFile().getParentFile();
            Document doc;
            try (InputStream in = new FileInputStream(tsx)) {
                doc = new DocumentBuilderSafe().parse(in);
            }
            Element tsRoot = doc.getDocumentElement();
            if (tsRoot == null || !"tileset".equalsIgnoreCase(tsRoot.getTagName())) {
                throw new IllegalArgumentException("Not a Tiled .tsx tileset: " + tsx.getName());
            }
            Tileset ts = readTilesetElement(tsRoot, tsxDir);
            ts.firstGid = intAttr(el, "firstgid", 1);
            return ts;
        }
        Tileset ts = readTilesetElement(el, baseDir);
        ts.firstGid = intAttr(el, "firstgid", 1);
        return ts;
    }

    /** Reads an inline <tileset> element (embedded in a map or the root of a .tsx file). */
    private static Tileset readTilesetElement(Element el, File baseDir) {
        Tileset ts = new Tileset();
        ts.name = attr(el, "name", "tiles");
        ts.tileWidth = intAttr(el, "tilewidth", 16);
        ts.tileHeight = intAttr(el, "tileheight", 16);
        ts.columns = intAttr(el, "columns", 1);
        ts.tileCount = intAttr(el, "tilecount", 0);
        ts.margin = intAttr(el, "margin", 0);
        ts.spacing = intAttr(el, "spacing", 0);
        for (Element child : children(el)) {
            String tag = child.getTagName().toLowerCase(Locale.US);
            if ("image".equals(tag)) {
                ts.imageSource = attr(child, "source", "");
                ts.imageWidth = intAttr(child, "width", 0);
                ts.imageHeight = intAttr(child, "height", 0);
                if (!ts.imageSource.isEmpty()) {
                    File img = baseDir == null ? new File(ts.imageSource) : new File(baseDir, ts.imageSource);
                    ts.imagePath = img.isAbsolute() ? img.getPath() : img.getAbsolutePath();
                }
            } else if ("properties".equals(tag)) {
                readProperties(child, ts.properties);
            }
        }
        return ts;
    }

    private static Layer readLayer(Element el, TmxMap map) {
        Layer layer = new Layer();
        layer.name = attr(el, "name", "layer");
        layer.width = intAttr(el, "width", map.width);
        layer.height = intAttr(el, "height", map.height);
        layer.offsetX = floatAttr(el, "offsetx", 0);
        layer.offsetY = floatAttr(el, "offsety", 0);
        layer.opacity = floatAttr(el, "opacity", 1);
        layer.visible = !"0".equals(attr(el, "visible", "1"));
        layer.gids = new long[Math.max(0, layer.width * layer.height)];
        for (Element child : children(el)) {
            if (!"data".equalsIgnoreCase(child.getTagName())) continue;
            decodeData(child, layer);
        }
        return layer;
    }

    private static void decodeData(Element data, Layer layer) {
        String encoding = attr(data, "encoding", "").toLowerCase(Locale.US);
        String compression = attr(data, "compression", "").toLowerCase(Locale.US);
        if ("csv".equals(encoding)) {
            // Tiled writes one CSV row per line; splitting on "," alone would glue the last cell of
            // a row to the first cell of the next ("2\n2"), so split on commas AND whitespace.
            String text = data.getTextContent();
            String[] parts = text.trim().split("[,\\s]+");
            int i = 0;
            for (String part : parts) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                if (i >= layer.gids.length) break;
                layer.gids[i++] = parseGid(t);
            }
        } else if ("base64".equals(encoding)) {
            String text = data.getTextContent().replaceAll("\\s", "");
            byte[] bytes = base64Decode(text);
            try {
                if (compression.contains("gzip")) {
                    bytes = readAll(new GZIPInputStream(new ByteArrayInputStream(bytes)));
                } else if (compression.contains("zlib")) {
                    bytes = readAll(new InflaterInputStream(new ByteArrayInputStream(bytes)));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Cannot decompress layer '" + layer.name + "': " + e.getMessage(), e);
            }
            int count = Math.min(layer.gids.length, bytes.length / 4);
            for (int i = 0; i < count; i++) {
                long v = ((long) (bytes[i * 4] & 0xFF))
                        | ((long) (bytes[i * 4 + 1] & 0xFF) << 8)
                        | ((long) (bytes[i * 4 + 2] & 0xFF) << 16)
                        | ((long) (bytes[i * 4 + 3] & 0xFF) << 24);
                layer.gids[i] = v;
            }
        } else {
            // Legacy per-tile <tile gid=".."/> elements
            int i = 0;
            for (Element t : children(data)) {
                if (!"tile".equalsIgnoreCase(t.getTagName())) continue;
                if (i >= layer.gids.length) break;
                layer.gids[i++] = (long) intAttr(t, "gid", 0);
            }
        }
    }

    private static ObjectGroup readObjectGroup(Element el) {
        ObjectGroup g = new ObjectGroup();
        g.name = attr(el, "name", "objects");
        g.opacity = floatAttr(el, "opacity", 1);
        g.visible = !"0".equals(attr(el, "visible", "1"));
        for (Element child : children(el)) {
            if (!"object".equalsIgnoreCase(child.getTagName())) continue;
            MapObject o = new MapObject();
            o.id = attr(child, "id", "");
            o.name = attr(child, "name", "");
            o.type = attr(child, "type", "");
            o.cls = attr(child, "class", "");
            o.x = floatAttr(child, "x", 0);
            o.y = floatAttr(child, "y", 0);
            o.w = floatAttr(child, "width", 0);
            o.h = floatAttr(child, "height", 0);
            o.rotation = floatAttr(child, "rotation", 0);
            String gidAttr = attr(child, "gid", "");
            if (!gidAttr.isEmpty()) o.gid = parseGid(gidAttr);
            for (Element sub : children(child)) {
                String tag = sub.getTagName().toLowerCase(Locale.US);
                if ("ellipse".equals(tag)) o.isEllipse = true;
                else if ("point".equals(tag)) o.isPoint = true;
                else if ("polygon".equals(tag) || "polyline".equals(tag)) {
                    o.isPolygon = true;
                    // Bounding box of the polygon so it still becomes a usable collider.
                    String[] pts = attr(sub, "points", "").trim().split("\\s+");
                    float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
                    for (String p : pts) {
                        String[] xy = p.split(",");
                        if (xy.length != 2) continue;
                        try {
                            float px = Float.parseFloat(xy[0]), py = Float.parseFloat(xy[1]);
                            minX = Math.min(minX, px); minY = Math.min(minY, py);
                            maxX = Math.max(maxX, px); maxY = Math.max(maxY, py);
                        } catch (NumberFormatException ignored) { }
                    }
                    if (maxX > minX) {
                        o.x += minX; o.y += minY;
                        o.w = maxX - minX;
                        o.h = maxY - minY;
                    }
                } else if ("properties".equals(tag)) {
                    readProperties(sub, o.properties);
                }
            }
            g.objects.add(o);
        }
        return g;
    }

    private static void readProperties(Element el, Map<String, String> out) {
        for (Element p : children(el)) {
            if (!"property".equalsIgnoreCase(p.getTagName())) continue;
            String key = attr(p, "name", "");
            if (key.isEmpty()) continue;
            String value = attr(p, "value", null);
            if (value == null) value = p.getTextContent();
            out.put(key, value == null ? "" : value.trim());
        }
    }

    // ------------------------------------------------------------------ small utilities

    private static final class DocumentBuilderSafe {
        Document parse(InputStream in) throws Exception {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            try {
                f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Exception ignored) { }
            f.setExpandEntityReferences(false);
            javax.xml.parsers.DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(new org.xml.sax.InputSource(new java.io.InputStreamReader(in, "UTF-8")));
        }
    }

    private static List<Element> children(Element el) {
        List<Element> out = new ArrayList<>();
        NodeList list = el.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            Node n = list.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) out.add((Element) n);
        }
        return out;
    }

    private static String attr(Element el, String key, String def) {
        return el.hasAttribute(key) ? el.getAttribute(key) : def;
    }

    private static int intAttr(Element el, String key, int def) {
        String v = attr(el, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        try { return (int) Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static float floatAttr(Element el, String key, float def) {
        String v = attr(el, key, null);
        if (v == null || v.trim().isEmpty()) return def;
        try { return Float.parseFloat(v.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static long parseGid(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Self-contained base64 decoder (java.util.Base64 needs API 26 on Android). */
    static byte[] base64Decode(String s) {
        final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int len = s.length();
        while (len > 0 && s.charAt(len - 1) == '=') len--;
        int outLen = (len * 3) / 4;
        byte[] out = new byte[outLen];
        int buffer = 0, bits = 0, idx = 0;
        for (int i = 0; i < len; i++) {
            int v = alphabet.indexOf(s.charAt(i));
            if (v < 0) continue;
            buffer = (buffer << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                if (idx < outLen) out[idx++] = (byte) ((buffer >> bits) & 0xFF);
            }
        }
        return out;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }
}
