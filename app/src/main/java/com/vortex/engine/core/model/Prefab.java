package com.vortex.engine.core.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * v4.7.0 — reusable scene-tree prefab.
 *
 * A prefab is the serialized subtree of any node (root + all descendants, keeping parent links and
 * local offsets) plus a name and category. Dropping one into a scene clones the whole limb of the
 * tree, re-keys every entity id, and parents the clone under the requested node.
 *
 * Stored on the project as a small JSON array so prefabs travel with the .vortex file and survive
 * the Android build with zero extra assets.
 */
public final class Prefab {

    public String name;
    public String category = "Custom";
    public String treeJson = "";

    public Prefab() { }

    public Prefab(String name, String category, String treeJson) {
        this.name = name;
        this.category = category;
        this.treeJson = treeJson;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("category", category);
        o.put("tree", treeJson);
        return o;
    }

    public static Prefab fromJson(JSONObject o) {
        Prefab p = new Prefab();
        p.name = o.optString("name", "Prefab");
        p.category = o.optString("category", "Custom");
        p.treeJson = o.optString("tree", "");
        return p;
    }

    /** Serializes {@code root} and its descendants into a prefab. */
    public static Prefab capture(List<Entity> all, Entity root, String name, String category) {
        // SceneHierarchy.subtree() returns descendants only — the prefab root must come first.
        List<Entity> nodes = new ArrayList<>();
        nodes.add(root);
        nodes.addAll(SceneHierarchy.subtree(all, root));
        JSONArray arr = new JSONArray();
        for (Entity e : nodes) {
            try {
                JSONObject o = e.toJson();
                o.put("_prefabLocalX", e.localX);
                o.put("_prefabLocalY", e.localY);
                o.put("_prefabLocalZ", e.localZ);
                arr.put(o);
            } catch (Exception ignored) { }
        }
        return new Prefab(name, category, arr.toString());
    }

    /** Number of nodes this prefab will add. */
    public int nodeCount() {
        try {
            return new JSONArray(treeJson).length();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Instantiates the prefab into {@code project}, parented under {@code parent} (may be null for
     * a scene root). Returns the cloned root entity, or null when the prefab is empty.
     */
    public Entity instantiate(GameProject project, Entity parent, float x, float y, float z) {
        List<Entity> all = project.entities;
        List<Entity> created = new ArrayList<>();
        Entity newRoot = null;
        try {
            JSONArray arr = new JSONArray(treeJson);
            // Pass 1: clone every node, remap ids.
            java.util.Map<String, Entity> idMap = new java.util.HashMap<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Entity copy = Entity.fromJson(o);
                copy.id = java.util.UUID.randomUUID().toString().substring(0, 8);
                String oldId = o.optString("id", "");
                idMap.put(oldId, copy);
                created.add(copy);
                if (newRoot == null) newRoot = copy;
            }
            // Pass 2: re-link parents through the id map, preserving hierarchy.
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Entity copy = created.get(i);
                String oldParent = o.optString("parentId", "");
                Entity mapped = oldParent.isEmpty() ? null : idMap.get(oldParent);
                if (mapped != null) {
                    copy.parentId = mapped.id;
                    copy.localX = (float) o.optDouble("_prefabLocalX", 0);
                    copy.localY = (float) o.optDouble("_prefabLocalY", 0);
                    copy.localZ = (float) o.optDouble("_prefabLocalZ", 0);
                } else {
                    copy.parentId = "";
                    copy.localX = 0f; copy.localY = 0f; copy.localZ = 0f;
                    // Offset the whole prefab from the current selection / drop point.
                    copy.x += x;
                    copy.y += y;
                    copy.z += z;
                }
                project.entities.add(copy);
            }
            if (newRoot != null && parent != null) {
                SceneHierarchy.reparent(project.entities, newRoot, parent);
            }
        } catch (Exception e) {
            return null;
        }
        return newRoot;
    }

    /** Built-in prefabs seeded into every new project so the palette is never empty. */
    public static List<Prefab> defaults() {
        List<Prefab> list = new ArrayList<>();
        list.add(fromTemplate("Player Starter", "Gameplay", "#F5F3EF", "SPRITE_HERO", "DYNAMIC", true));
        list.add(fromTemplate("Enemy Walker", "Gameplay", "#C86B6B", "SPRITE_SLIME", "DYNAMIC", true));
        list.add(fromTemplate("Pickup Coin", "Gameplay", "#FACC15", "SPRITE_COIN", "TRIGGER", false));
        list.add(fromTemplate("Platform Block", "Level", "#8B8F98", "RECT", "STATIC", false));
        list.add(fromTemplate("Light Marker", "Lighting", "#FFF6E5", "CIRCLE", "NONE", false));
        list.add(fromTemplate("Crate (3D)", "Props", "#C9A227", "CUBE", "DYNAMIC", false));
        list.add(fromTemplate("Pillar (3D)", "Props", "#B8BCC4", "CYLINDER", "STATIC", false));
        return list;
    }

    private static Prefab fromTemplate(String name, String category, String color, String prim,
                                       String body, boolean hero) {
        Entity e = Entity.create2D(name, hero ? "player" : "prefab", prim, 0f, 0f, 1f, 1f, color, body);
        e.collider = "NONE".equalsIgnoreCase(body) ? "NONE" : "BOX";
        e.material = "Lighting".equals(category) ? "EMISSIVE" : "MATTE";
        List<Entity> tmp = new ArrayList<>();
        tmp.add(e);
        return capture(tmp, e, name, category);
    }
}
