package com.vortex.engine.core.asset;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.SceneHierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns a Tiled map into a live Vortex scene: decorative tile runs become merged RECT entities,
 * solid objects become static colliders, spawn objects become a playable hero.
 *
 * Everything lands inside one root node so the scene tree stays readable and the whole world can be
 * moved / hidden / deleted as a unit. One tile = one world unit by default.
 */
public final class TileWorldBuilder {

    /** Supplies the average colour of a tile (ARGB). Return 0 when the image is unavailable. */
    public interface TileColorSource {
        int colorAt(TmxMap.Tileset tileset, long localTileId);
    }

    public static final class Result {
        public Entity root;
        public int tileRects;
        public int mergedFromTiles;
        public int solidObjects;
        public int spawns;
        public String summary = "";
    }

    private TileWorldBuilder() { }

    public static Result build(GameProject project, TmxMap map, TileColorSource colors, String sourceName) {
        Result res = new Result();
        String label = sourceName == null || sourceName.isEmpty() ? map.name : sourceName;
        float tileW = Math.max(1, map.tileWidth);
        float tileH = Math.max(1, map.tileHeight);
        float unitX = 1f;                       // 1 tile = 1 unit horizontally
        float unitY = tileH / tileW;            // keep aspect when tiles aren't square
        boolean anyTileSolidLayer = false;
        for (TmxMap.Layer l : map.layers) {
            String n = l.name.toLowerCase(Locale.US);
            if (n.contains("collision") || n.contains("solid") || n.contains("wall") || n.contains("block")) {
                anyTileSolidLayer = true;
                break;
            }
        }
        boolean hasSolidObjects = false;
        for (TmxMap.ObjectGroup g : map.objectGroups) {
            for (TmxMap.MapObject o : g.objects) {
                if (o.looksSolid()) { hasSolidObjects = true; break; }
            }
            if (hasSolidObjects) break;
        }
        // If the map encodes collision as objects we keep every tile decorative; otherwise tiles on
        // "collision"-ish layers are the colliders.
        boolean tileLayersCollide = anyTileSolidLayer || !hasSolidObjects;

        Entity root = Entity.create2D("TileWorld_" + sanitize(label), "world", "RECT", 0f, 0f, 0.01f, 0.01f, "#000000", "NONE");
        root.visible = false;
        root.alpha = 0f;
        project.entities.add(root);
        res.root = root;

        for (TmxMap.Layer layer : map.layers) {
            if (!layer.visible || layer.gids.length == 0) continue;
            String ln = layer.name.toLowerCase(Locale.US);
            boolean solidLayer = tileLayersCollide
                    && (ln.contains("collision") || ln.contains("solid") || ln.contains("wall") || ln.contains("block"));
            Entity layerRoot = Entity.create2D("Layer_" + sanitize(layer.name), "layer", "RECT",
                    0f, 0f, 0.01f, 0.01f, "#000000", "NONE");
            layerRoot.visible = false;
            layerRoot.alpha = 0f;
            project.entities.add(layerRoot);
            SceneHierarchy.reparent(project.entities, layerRoot, root);

            for (int row = 0; row < layer.height; row++) {
                int col = 0;
                while (col < layer.width) {
                    long gid = layer.gids[row * layer.width + col];
                    if (TmxMap.rawGid(gid) == 0) { col++; continue; }
                    int color = colorFor(map, gid, colors);
                    int runStart = col;
                    int runLen = 1;
                    col++;
                    while (col < layer.width) {
                        long next = layer.gids[row * layer.width + col];
                        if (TmxMap.rawGid(next) == 0) break;
                        if (colorFor(map, next, colors) != color) break;
                        runLen++;
                        col++;
                    }
                    res.mergedFromTiles += runLen;
                    res.tileRects++;
                    float cx = (runStart + runLen * 0.5f) * unitX + layer.offsetX / tileW;
                    float cy = (layer.height - row - 0.5f) * unitY - layer.offsetY / tileH;
                    Entity tile = Entity.create2D(
                            sanitize(layer.name) + "_" + row + "_" + runStart, solidLayer ? "solid" : "decor",
                            "RECT", cx, cy, runLen * unitX, unitY, argbToHex(color), solidLayer ? "STATIC" : "NONE");
                    tile.collider = "BOX";
                    tile.friction = 0.85f;
                    project.entities.add(tile);
                    SceneHierarchy.reparent(project.entities, tile, layerRoot);
                }
            }
        }

        for (TmxMap.ObjectGroup group : map.objectGroups) {
            if (!group.visible) continue;
            for (TmxMap.MapObject o : group.objects) {
                float wpx = o.w <= 0 ? tileW : o.w;
                float hpx = o.h <= 0 ? tileH : o.h;
                float cx = (o.x + wpx * 0.5f) / tileW;
                float cy = (map.height * tileH - (o.y + hpx * 0.5f)) / tileH;
                if (o.looksSpawn()) {
                    Entity hero = Entity.create2D("Hero", "player",
                            "SPRITE_HERO", cx, cy, 1.4f, 1.6f, "#F5F3EF", "DYNAMIC");
                    hero.fixedRotation = true;
                    project.entities.add(hero);
                    SceneHierarchy.reparent(project.entities, hero, res.root);
                    res.spawns++;
                    continue;
                }
                boolean solid = o.looksSolid();
                Entity e = Entity.create2D(
                        (o.name == null || o.name.isEmpty() ? "obj_" + sanitize(group.name) : o.name),
                        solid ? "solid" : "decor",
                        o.isEllipse ? "CIRCLE" : "RECT",
                        cx, cy, wpx / tileW, hpx / tileH,
                        solid ? "#4B5563" : "#6B7280",
                        solid ? "STATIC" : "NONE");
                e.rotZ = o.rotation;
                project.entities.add(e);
                SceneHierarchy.reparent(project.entities, e, res.root);
                if (solid) res.solidObjects++;
            }
        }

        res.summary = map.name + ": " + res.tileRects + " merged tile rows (" + res.mergedFromTiles
                + " tiles), " + res.solidObjects + " solid objects, " + res.spawns + " spawns";
        return res;
    }

    /** Palette fallback so an import still looks deliberate when the tileset PNG is missing. */
    static int colorFor(TmxMap map, long gid, TileColorSource colors) {
        TmxMap.Tileset ts = map.tilesetFor(gid);
        long local = TmxMap.rawGid(gid) - (ts == null ? 0 : ts.firstGid);
        if (colors != null && ts != null) {
            int c = colors.colorAt(ts, local);
            if (c != 0 && (c >>> 24) > 8) return c;
        }
        return paletteColor((int) (TmxMap.rawGid(gid) % 12));
    }

    private static int paletteColor(int index) {
        float h = (index * 30f + 12f) % 360f;
        return 0xFF000000 | hsvToRgb(h, 0.35f, 0.72f);
    }

    static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs(((h / 60f) % 2) - 1));
        float m = v - c;
        float r, g, b;
        int seg = (int) (h / 60f) % 6;
        switch (seg) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        int ri = Math.round((r + m) * 255), gi = Math.round((g + m) * 255), bi = Math.round((b + m) * 255);
        return (clamp255(ri) << 16) | (clamp255(gi) << 8) | clamp255(bi);
    }

    private static int clamp255(int v) { return Math.max(0, Math.min(255, v)); }

    public static String argbToHex(int argb) {
        return String.format("#%02X%02X%02X", (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "map";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
        }
        String out = sb.toString();
        if (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out.isEmpty() ? "map" : out;
    }

    /** Convenience: build + return just the entity list (kept for script/agent use). */
    public static List<Entity> buildEntities(GameProject project, TmxMap map, TileColorSource colors, String name) {
        Result r = build(project, map, colors, name);
        List<Entity> out = new ArrayList<>();
        if (r.root != null) out.add(r.root);
        return out;
    }
}
