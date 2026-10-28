package com.vortex.engine.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Godot-style scene tree.
 *
 * Design constraint: every renderer (GL, software 3D viewport, HTML5 exporter) and the physics
 * solver keep reading {@code Entity.x/y/z/rotX..scaleZ} as WORLD transforms. Parenting therefore
 * lives in two mirrored sets of fields:
 *
 *   - world  : x / y / z, rotX..rotZ, scaleX..scaleZ  (what everything else consumes)
 *   - local  : localX..localZ, localRotX..localRotZ, localScaleX..localScaleZ (relative to parent)
 *
 * {@link #propagate(List)} pushes parent -> child (called every frame before render + physics),
 * {@link #syncLocalsFromWorld(List)} pulls physics/script results back into the locals.
 * No other subsystem needed changing, so hierarchy is cheap and safe.
 */
public final class SceneHierarchy {

    /** Hard depth cap: guards against accidental deep/cyclic graphs. */
    public static final int MAX_DEPTH = 32;

    private SceneHierarchy() {}

    // ------------------------------------------------------------------ lookups

    public static Entity find(List<Entity> all, String id) {
        if (all == null || id == null || id.isEmpty()) return null;
        for (Entity e : all) if (e != null && id.equals(e.id)) return e;
        return null;
    }

    public static Entity parentOf(List<Entity> all, Entity child) {
        if (all == null || child == null || child.parentId == null || child.parentId.isEmpty()) return null;
        Entity p = find(all, child.parentId);
        // Defensive: stale parent ids resolve to "no parent".
        if (p == null || p == child) return null;
        return p;
    }

    public static List<Entity> childrenOf(List<Entity> all, Entity parent) {
        List<Entity> out = new ArrayList<>();
        if (all == null) return out;
        String pid = parent == null ? "" : parent.id;
        for (Entity e : all) {
            if (e == null) continue;
            String ep = e.parentId == null ? "" : e.parentId;
            if (parent == null) {
                if (ep.isEmpty() || find(all, ep) == null) out.add(e);
            } else if (ep.equals(pid)) {
                out.add(e);
            }
        }
        return out;
    }

    public static List<Entity> roots(List<Entity> all) {
        List<Entity> out = new ArrayList<>();
        if (all == null) return out;
        for (Entity e : all) {
            if (e == null) continue;
            if (e.parentId == null || e.parentId.isEmpty() || find(all, e.parentId) == null) out.add(e);
        }
        return out;
    }

    public static boolean hasChildren(List<Entity> all, Entity e) {
        if (all == null || e == null) return false;
        for (Entity c : all) if (c != null && e.id.equals(c.parentId)) return true;
        return false;
    }

    public static int depth(List<Entity> all, Entity e) {
        int d = 0;
        Entity cur = e;
        Set<String> seen = new HashSet<>();
        while (cur != null && d < MAX_DEPTH) {
            if (!seen.add(cur.id)) return d;
            Entity p = parentOf(all, cur);
            if (p == null) break;
            d++;
            cur = p;
        }
        return d;
    }

    /** True when {@code maybeAncestor} is {@code node} itself or sits above it in the tree. */
    public static boolean isAncestor(List<Entity> all, Entity maybeAncestor, Entity node) {
        if (maybeAncestor == null || node == null) return false;
        if (maybeAncestor.id.equals(node.id)) return true;
        Entity cur = node;
        int guard = 0;
        while (cur != null && guard++ < MAX_DEPTH) {
            Entity p = parentOf(all, cur);
            if (p == null) return false;
            if (p.id.equals(maybeAncestor.id)) return true;
            cur = p;
        }
        return false;
    }

    /** Every descendant of {@code root} (not including root), parent-before-child order. */
    public static List<Entity> subtree(List<Entity> all, Entity root) {
        List<Entity> out = new ArrayList<>();
        if (root == null) return out;
        List<Entity> queue = new ArrayList<>();
        queue.add(root);
        Set<String> seen = new HashSet<>();
        seen.add(root.id);
        while (!queue.isEmpty() && out.size() < 4096) {
            Entity cur = queue.remove(0);
            for (Entity c : all) {
                if (c == null || !seen.add(c.id)) continue;
                if (cur.id.equals(c.parentId)) {
                    out.add(c);
                    queue.add(c);
                }
            }
        }
        return out;
    }

    /** Tree order (roots first, children directly under their parent) for the scene-tree UI. */
    public static List<Entity> flattened(List<Entity> all) {
        List<Entity> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entity root : roots(all)) walk(all, root, out, seen);
        // Orphans left over from a broken/cyclic graph still need to show up.
        for (Entity e : all) if (e != null && seen.add(e.id)) walk(all, e, out, seen);
        return out;
    }

    private static void walk(List<Entity> all, Entity node, List<Entity> out, Set<String> seen) {
        if (node == null || !seen.add(node.id) || out.size() >= 4096) return;
        out.add(node);
        for (Entity c : all) {
            if (c != null && node.id.equals(c.parentId)) walk(all, c, out, seen);
        }
    }

    /** Display path, Godot-style: {@code Player/Weapon/Muzzle}. */
    public static String path(List<Entity> all, Entity e) {
        if (e == null) return "";
        StringBuilder sb = new StringBuilder(e.name);
        Entity p = parentOf(all, e);
        int guard = 0;
        while (p != null && guard++ < MAX_DEPTH) {
            sb.insert(0, p.name + "/");
            p = parentOf(all, p);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ reparenting

    /**
     * Moves {@code child} under {@code newParent} (null = root), preserving its on-screen world
     * transform. Returns false when the move would create a cycle (or is a no-op self-parent).
     */
    public static boolean reparent(List<Entity> all, Entity child, Entity newParent) {
        if (all == null || child == null) return false;
        if (newParent != null && (newParent == child || isAncestor(all, child, newParent))) {
            return false; // would detach the tree from its root / infinite loop
        }
        if (newParent == null) {
            clearParent(child);
            return true;
        }
        child.parentId = newParent.id;
        captureLocal(child, newParent);
        return true;
    }

    public static void clearParent(Entity child) {
        if (child == null) return;
        child.localX = child.x;
        child.localY = child.y;
        child.localZ = child.z;
        child.localRotX = child.rotX;
        child.localRotY = child.rotY;
        child.localRotZ = child.rotZ;
        child.localScaleX = child.scaleX;
        child.localScaleY = child.scaleY;
        child.localScaleZ = child.scaleZ;
        child.parentId = "";
    }

    /** world -> local (inverse of {@link #applyLocalToWorld}). */
    public static void captureLocal(Entity child, Entity parent) {
        if (child == null) return;
        if (parent == null) {
            clearParent(child);
            return;
        }
        float sx = parent.scaleX == 0f ? 1f : parent.scaleX;
        float sy = parent.scaleY == 0f ? 1f : parent.scaleY;
        float sz = parent.scaleZ == 0f ? 1f : parent.scaleZ;
        float dx = (child.x - parent.x) / sx;
        float dy = (child.y - parent.y) / sy;
        float dz = (child.z - parent.z) / sz;
        // undo parent rotation (rotate by -rot, reverse order of applyLocalToWorld)
        float[] v = rotateEuler(dx, dy, dz,
                -parent.rotX, -parent.rotY, -parent.rotZ);
        child.localX = v[0];
        child.localY = v[1];
        child.localZ = v[2];
        child.localRotX = child.rotX - parent.rotX;
        child.localRotY = child.rotY - parent.rotY;
        child.localRotZ = child.rotZ - parent.rotZ;
        child.localScaleX = sx == 0f ? 1f : child.scaleX / sx;
        child.localScaleY = sy == 0f ? 1f : child.scaleY / sy;
        child.localScaleZ = sz == 0f ? 1f : child.scaleZ / sz;
    }

    /** local -> world (parent must already hold its final world transform). */
    public static void applyLocalToWorld(Entity child, Entity parent) {
        if (child == null || parent == null) return;
        float lx = child.localX * parent.scaleX;
        float ly = child.localY * parent.scaleY;
        float lz = child.localZ * parent.scaleZ;
        float[] v = rotateEuler(lx, ly, lz, parent.rotX, parent.rotY, parent.rotZ);
        child.parentId = parent.id;
        child.x = parent.x + v[0];
        child.y = parent.y + v[1];
        child.z = parent.z + v[2];
        child.rotX = parent.rotX + child.localRotX;
        child.rotY = parent.rotY + child.localRotY;
        child.rotZ = parent.rotZ + child.localRotZ;
        child.scaleX = parent.scaleX * child.localScaleX;
        child.scaleY = parent.scaleY * child.localScaleY;
        child.scaleZ = parent.scaleZ * child.localScaleZ;
    }

    // ------------------------------------------------------------------ frame sync

    /**
     * Push parent world transforms down the tree. Call before render + before physics.
     *
     * Reconciles two writers: an externally edited world transform (gizmo drag, inspector scrubber,
     * script, physics solver) wins and refreshes the local transform; otherwise the parent drives
     * the child. Without this, dragging a child would snap back on the next frame.
     */
    public static void propagate(List<Entity> all) {
        if (all == null || all.isEmpty()) return;
        List<Entity> ordered = sortedByDepth(all);
        for (Entity e : ordered) {
            Entity p = parentOf(all, e);
            if (p == null) {
                e.parentId = "";
                publish(e);
                continue;
            }
            if (worldEditedSinceLastPublish(e)) {
                captureLocal(e, p);   // external edit is authoritative
            }
            applyLocalToWorld(e, p);
            publish(e);
        }
    }

    private static boolean worldEditedSinceLastPublish(Entity e) {
        if (Float.isNaN(e.pubX)) return false; // first publish: trust the locals
        return !near(e.x, e.pubX) || !near(e.y, e.pubY) || !near(e.z, e.pubZ)
                || !near(e.rotX, e.pubRotX) || !near(e.rotY, e.pubRotY) || !near(e.rotZ, e.pubRotZ)
                || !near(e.scaleX, e.pubScaleX) || !near(e.scaleY, e.pubScaleY) || !near(e.scaleZ, e.pubScaleZ);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) <= 1e-4f;
    }

    private static void publish(Entity e) {
        e.pubX = e.x; e.pubY = e.y; e.pubZ = e.z;
        e.pubRotX = e.rotX; e.pubRotY = e.rotY; e.pubRotZ = e.rotZ;
        e.pubScaleX = e.scaleX; e.pubScaleY = e.scaleY; e.pubScaleZ = e.scaleZ;
    }

    /** Pull world transforms (physics / scripts) back into the locals. Call after stepping. */
    public static void syncLocalsFromWorld(List<Entity> all) {
        if (all == null || all.isEmpty()) return;
        List<Entity> ordered = sortedByDepth(all);
        Collections.reverse(ordered);
        for (Entity e : ordered) {
            Entity p = parentOf(all, e);
            if (p != null) captureLocal(e, p);
        }
    }

    private static List<Entity> sortedByDepth(List<Entity> all) {
        List<Entity> ordered = new ArrayList<>(all);
        final List<Entity> src = all;
        Collections.sort(ordered, new Comparator<Entity>() {
            @Override public int compare(Entity a, Entity b) {
                return Integer.compare(depth(src, a), depth(src, b));
            }
        });
        return ordered;
    }

    /** Drops parent links that point at missing entities (used after load / delete). */
    public static void heal(List<Entity> all) {
        if (all == null) return;
        for (Entity e : all) {
            if (e == null) continue;
            if (e.parentId != null && !e.parentId.isEmpty() && find(all, e.parentId) == null) {
                clearParent(e);
            }
        }
    }

    /** Euler rotation (Z -> X -> Y, matching the engine's model matrices). */
    static float[] rotateEuler(float x, float y, float z, float rxDeg, float ryDeg, float rzDeg) {
        double rx = Math.toRadians(rxDeg), ry = Math.toRadians(ryDeg), rz = Math.toRadians(rzDeg);
        double cy = Math.cos(ry), sy = Math.sin(ry);
        double cx = Math.cos(rx), sx = Math.sin(rx);
        double cz = Math.cos(rz), sz = Math.sin(rz);
        // model = Ry * Rx * Rz
        double x1 = cz * x - sz * y;
        double y1 = sz * x + cz * y;
        double z1 = z;
        double x2 = x1;
        double y2 = cx * y1 - sx * z1;
        double z2 = sx * y1 + cx * z1;
        double x3 = cy * x2 + sy * z2;
        double y3 = y2;
        double z3 = -sy * x2 + cy * z2;
        return new float[]{(float) x3, (float) y3, (float) z3};
    }
}
