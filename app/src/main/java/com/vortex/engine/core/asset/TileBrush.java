package com.vortex.engine.core.asset;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.SceneHierarchy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * v4.7.0 — the in-editor tile painter. No Tiled required.
 *
 * A tile layer is a normal scene-tree node whose children are 1x1 RECT entities named
 * {@code t_<gx>_<gy>}, one per grid cell, so painted cells are selectable, tintable and scriptable
 * like any other entity. {@link #mergeRuns} compacts a layer into horizontal runs to keep the entity
 * count (and therefore the save file and the physics broadphase) small.
 */
public final class TileBrush {

    public static final String ROOT_NAME = "TileLayer";
    public static final float CELL = 1f;

    private TileBrush() { }

    /** Finds or creates the tile layer root (a visible, non-colliding scene root). */
    public static Entity ensureLayer(GameProject project, String layerName) {
        String name = ROOT_NAME + "_" + sanitize(layerName);
        for (Entity e : project.entities) {
            if (name.equals(e.name) && e.alive) return e;
        }
        Entity root = Entity.create2D(name, "tilelayer", "RECT", 0f, 0f, 0.01f, 0.01f, "#1A1A1D", "NONE");
        root.visible = false;
        root.alpha = 0f;
        project.entities.add(root);
        return root;
    }

    public static Entity findLayer(GameProject project, String layerName) {
        String name = ROOT_NAME + "_" + sanitize(layerName);
        for (Entity e : project.entities) {
            if (name.equals(e.name) && e.alive) return e;
        }
        return null;
    }

    /** Cell name for a grid coordinate. */
    public static String cellName(int gx, int gy) {
        return "t_" + gx + "_" + gy;
    }

    /** True when a cell is already filled in this layer. */
    public static boolean isFilled(GameProject project, String layerName, int gx, int gy) {
        Entity layer = findLayer(project, layerName);
        if (layer == null) return false;
        String n = cellName(gx, gy);
        for (Entity e : project.entities) {
            if (e.alive && n.equals(e.name) && layer.id.equals(e.parentId)) return true;
        }
        return false;
    }

    /**
     * Paints one cell. Returns the created entity, or null when the cell was already filled or the
     * colour is invalid. {@code solid} marks the cell as a static collider (walls / platforms).
     */
    public static Entity paint(GameProject project, String layerName, int gx, int gy,
                               String colorHex, boolean solid) {
        if (colorHex == null || !colorHex.matches("#[0-9A-Fa-f]{6}")) return null;
        Entity layer = ensureLayer(project, layerName);
        if (isFilled(project, layerName, gx, gy)) return null;
        Entity cell = Entity.create2D(cellName(gx, gy), solid ? "solid" : "decor", "RECT",
                (gx + 0.5f) * CELL, (gy + 0.5f) * CELL, CELL, CELL, colorHex, solid ? "STATIC" : "NONE");
        cell.collider = solid ? "BOX" : "NONE";
        cell.friction = 0.85f;
        project.entities.add(cell);
        SceneHierarchy.reparent(project.entities, cell, layer);
        return cell;
    }

    /** Erases one cell. Returns true when something was removed. */
    public static boolean erase(GameProject project, String layerName, int gx, int gy) {
        Entity layer = findLayer(project, layerName);
        if (layer == null) return false;
        String n = cellName(gx, gy);
        boolean removed = false;
        for (Iterator<Entity> it = project.entities.iterator(); it.hasNext(); ) {
            Entity e = it.next();
            if (e.alive && n.equals(e.name) && layer.id.equals(e.parentId)) {
                it.remove();
                removed = true;
            }
        }
        // also drop merged runs that cover this cell
        for (Iterator<Entity> it = project.entities.iterator(); it.hasNext(); ) {
            Entity e = it.next();
            if (!e.alive || !layer.id.equals(e.parentId)) continue;
            if (covers(e, gx, gy)) { it.remove(); removed = true; }
        }
        return removed;
    }

    /** True when an entity (cell or merged run) covers grid cell (gx, gy). */
    public static boolean covers(Entity e, int gx, int gy) {
        float x0 = e.x - e.scaleX * 0.5f, x1 = e.x + e.scaleX * 0.5f;
        float y0 = e.y - e.scaleY * 0.5f, y1 = e.y + e.scaleY * 0.5f;
        float px = gx + 0.5f, py = gy + 0.5f;
        return px >= x0 && px <= x1 && py >= y0 && py <= y1;
    }

    /**
     * Compacts the layer: drops the individual cells and rewrites them as horizontal runs of equal
     * colour. Returns the number of entities removed (net saving).
     */
    public static int mergeRuns(GameProject project, String layerName) {
        Entity layer = findLayer(project, layerName);
        if (layer == null) return 0;
        List<Entity> owned = new ArrayList<>();
        for (Entity e : project.entities) {
            if (e.alive && layer.id.equals(e.parentId)) owned.add(e);
        }
        if (owned.isEmpty()) return 0;

        // Rasterize into a cell map, then rebuild as runs.
        java.util.TreeMap<Integer, java.util.TreeMap<Integer, String>> rows = new java.util.TreeMap<>();
        java.util.TreeMap<Integer, Boolean> solidByRow = new java.util.TreeMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Entity e : owned) {
            int w = Math.max(1, Math.round(e.scaleX / CELL));
            int h = Math.max(1, Math.round(e.scaleY / CELL));
            int gx0 = Math.round(e.x / CELL - w * 0.5f);
            int gy0 = Math.round(e.y / CELL - h * 0.5f);
            boolean solid = e.bodyType != null && "STATIC".equalsIgnoreCase(e.bodyType);
            for (int yy = 0; yy < h; yy++) {
                for (int xx = 0; xx < w; xx++) {
                    int gx = gx0 + xx, gy = gy0 + yy;
                    if (!seen.add(gx + ":" + gy)) continue;
                    java.util.TreeMap<Integer, String> row = rows.get(gy);
                    if (row == null) { row = new java.util.TreeMap<>(); rows.put(gy, row); }
                    row.put(gx, e.colorHex + "|" + (solid ? 1 : 0));
                }
            }
        }
        for (Entity e : owned) project.entities.remove(e);

        int created = 0;
        for (java.util.Map.Entry<Integer, java.util.TreeMap<Integer, String>> rowEntry : rows.entrySet()) {
            int gy = rowEntry.getKey();
            java.util.TreeMap<Integer, String> row = rowEntry.getValue();
            Integer start = null, prev = null;
            String runVal = null;
            for (java.util.Map.Entry<Integer, String> cell : row.entrySet()) {
                int gx = cell.getKey();
                String val = cell.getValue();
                boolean contiguous = prev != null && gx == prev + 1 && val.equals(runVal);
                if (!contiguous) {
                    if (runVal != null) created += emitRun(project, layer, start, prev, gy, runVal);
                    start = gx; runVal = val;
                }
                prev = gx;
            }
            if (runVal != null) created += emitRun(project, layer, start, prev, gy, runVal);
        }
        return owned.size() - created;
    }

    private static int emitRun(GameProject project, Entity layer, int x0, int x1, int gy, String val) {
        String[] bits = val.split("\\|");
        String color = bits[0];
        boolean solid = bits.length > 1 && "1".equals(bits[1]);
        int len = x1 - x0 + 1;
        Entity run = Entity.create2D(sanitize(layer.name) + "_" + gy + "_" + x0, solid ? "solid" : "decor",
                "RECT", x0 + len * 0.5f, gy + 0.5f, len * CELL, CELL, color, solid ? "STATIC" : "NONE");
        run.collider = solid ? "BOX" : "NONE";
        project.entities.add(run);
        SceneHierarchy.reparent(project.entities, run, layer);
        return 1;
    }

    /** Bounding box of the painted layer in grid cells: {minX, minY, maxX, maxY} or null. */
    public static int[] bounds(GameProject project, String layerName) {
        Entity layer = findLayer(project, layerName);
        if (layer == null) return null;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (Entity e : project.entities) {
            if (!e.alive || !layer.id.equals(e.parentId)) continue;
            any = true;
            minX = Math.min(minX, Math.round(e.x - e.scaleX * 0.5f));
            maxX = Math.max(maxX, Math.round(e.x + e.scaleX * 0.5f) - 1);
            minY = Math.min(minY, Math.round(e.y - e.scaleY * 0.5f));
            maxY = Math.max(maxY, Math.round(e.y + e.scaleY * 0.5f) - 1);
        }
        return any ? new int[]{minX, minY, maxX, maxY} : null;
    }

    private static String sanitize(String s) {
        if (s == null || s.isEmpty()) return "Ground";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isLetterOrDigit(c)) sb.append(c);
            else if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') sb.append('_');
        }
        String out = sb.toString();
        if (out.endsWith("_")) out = out.substring(0, out.length() - 1);
        return out.isEmpty() ? "Ground" : out;
    }
}
