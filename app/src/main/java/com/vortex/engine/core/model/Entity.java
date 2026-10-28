package com.vortex.engine.core.model;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class Entity {
    // 3D Primitives: CUBE, SPHERE, PYRAMID, CYLINDER, PLANE, TERRAIN, CUSTOM_OBJ
    // 2D Primitives: RECT, CIRCLE, TRIANGLE, STAR, SPRITE_HERO, SPRITE_SHIP, SPRITE_SLIME, SPRITE_COIN, CUSTOM_SPRITE
    public String id;
    public String name;
    public String tag;        // player, solid, coin, enemy, hazard, goal, projectile, decor
    public boolean is3D;
    public String primitive;
    public String customObjName = "";
    public String customSpriteName = "";
    // v4.0 Multi-frame Flipbook Sprite Animation (comma-separated sprite keys, e.g. "knight_1,knight_2")
    public String spriteFrames = "";
    public float animFps = 6.0f;

    // Transform
    public float x = 0f;
    public float y = 0f;
    public float z = 0f;
    public float rotX = 0f;
    public float rotY = 0f;
    public float rotZ = 0f;
    public float scaleX = 1f;
    public float scaleY = 1f;
    public float scaleZ = 1f;

    // Appearance
    public String colorHex = "#FFFFFF";
    public String secondaryColorHex = "#27272A";
    public float alpha = 1.0f;
    public boolean checkerTexture = false;
    public boolean emissive = false;
    public boolean visible = true;
    // v4.7.0 material preset: MATTE (default), METAL, GLASS, EMISSIVE, UNLIT
    public String material = "MATTE";
    // v4.7.0 procedural surface pattern: NONE, CHECKER, STRIPES, NOISE, GRID, BRICK
    public String texture = "NONE";

    // Physics & Motion (JBox2D + Unreal-Inspired Substepped 3D Rigid-Body Solver)
    public String bodyType = "STATIC"; // STATIC, DYNAMIC, KINEMATIC, NONE
    public String collider = "BOX";    // BOX, SPHERE, CAPSULE, NONE
    public boolean isTrigger = false;
    public boolean fixedRotation = true;
    public boolean ccdBullet = false;  // Continuous Collision Detection (TOI / fast substepping)
    public float mass = 1.0f;
    public float density = 1.0f;
    public int collisionLayer = 1;
    public int collisionMask = 0xFFFF;
    public float vx = 0f;
    public float vy = 0f;
    public float vz = 0f;
    public float angularVel = 0f; // deg/sec for 2D rigid-body rotation
    public float gravityScale = 1.0f;
    public float bounce = 0.1f;
    public float friction = 0.65f;
    public float drag = 0.02f;
    public boolean isGrounded = false;
    public transient float coyoteTimer = 0f;
    public boolean alive = true;
    public transient boolean transformDirty = false;

    // v4.5.0 Godot-style Scene Tree parenting.
    // x/y/z/rot*/scale* always hold WORLD transforms (all renderers + physics read them),
    // while local* hold the transform relative to the parent. SceneHierarchy keeps both in sync.
    public String parentId = "";
    public float localX = 0f, localY = 0f, localZ = 0f;
    public float localRotX = 0f, localRotY = 0f, localRotZ = 0f;
    public float localScaleX = 1f, localScaleY = 1f, localScaleZ = 1f;
    // Snapshot of the world transform SceneHierarchy last wrote, so external edits (gizmo drags,
    // inspector scrubbers, scripts, physics) can be told apart from parent-driven motion.
    public transient float pubX = Float.NaN, pubY = Float.NaN, pubZ = Float.NaN;
    public transient float pubRotX = Float.NaN, pubRotY = Float.NaN, pubRotZ = Float.NaN;
    public transient float pubScaleX = Float.NaN, pubScaleY = Float.NaN, pubScaleZ = Float.NaN;

    // Continuous spin (deg/sec) for collectibles, props, asteroids
    public float spinX = 0f;
    public float spinY = 0f;
    public float spinZ = 0f;

    public Entity() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = "Entity_" + this.id.substring(0, 4);
        this.tag = "decor";
        this.is3D = true;
        this.primitive = "CUBE";
    }

    public static Entity create3D(String name, String tag, String primitive,
                                  float x, float y, float z,
                                  float sx, float sy, float sz,
                                  String colorHex, String bodyType) {
        Entity e = new Entity();
        e.name = name;
        e.tag = tag;
        e.is3D = true;
        e.primitive = primitive;
        e.x = x;
        e.y = y;
        e.z = z;
        e.scaleX = sx;
        e.scaleY = sy;
        e.scaleZ = sz;
        e.colorHex = colorHex;
        e.bodyType = bodyType;
        return e;
    }

    public static Entity create2D(String name, String tag, String primitive,
                                  float x, float y, float w, float h,
                                  String colorHex, String bodyType) {
        Entity e = new Entity();
        e.name = name;
        e.tag = tag;
        e.is3D = false;
        e.primitive = primitive;
        e.x = x;
        e.y = y;
        e.z = 0f;
        e.scaleX = w;
        e.scaleY = h;
        e.scaleZ = 1f;
        e.colorHex = colorHex;
        e.bodyType = bodyType;
        if ("CIRCLE".equalsIgnoreCase(primitive) || "SPRITE_COIN".equalsIgnoreCase(primitive)) {
            e.collider = "SPHERE";
        }
        return e;
    }

    public float[] parseColorRgba() {
        return hexToRgba(colorHex, alpha);
    }

    public float[] parseSecondaryRgba() {
        return hexToRgba(secondaryColorHex, alpha);
    }

    public String getActiveSpriteName() {
        if (customSpriteName != null && !customSpriteName.isEmpty()) return customSpriteName;
        if (spriteFrames != null && !spriteFrames.isEmpty()) {
            int comma = spriteFrames.indexOf(',');
            return (comma > 0 ? spriteFrames.substring(0, comma) : spriteFrames).trim();
        }
        return "";
    }

    /**
     * Pure-Java hex color parser (#RGB, #RRGGBB, #AARRGGBB) — works identically on Android & Desktop JVM.
     */
    public static float[] hexToRgba(String hex, float alphaOverride) {
        int argb = hexToArgbInt(hex, 0xFF00E5FF);
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        return new float[]{r, g, b, alphaOverride};
    }

    public static int hexToArgbInt(String hex, int fallback) {
        if (hex == null) return fallback;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16) * 17;
                int g = Integer.parseInt(s.substring(1, 2), 16) * 17;
                int b = Integer.parseInt(s.substring(2, 3), 16) * 17;
                return (0xFF << 24) | (r << 16) | (g << 8) | b;
            } else if (s.length() == 6) {
                return 0xFF000000 | Integer.parseInt(s, 16);
            } else if (s.length() == 8) {
                return (int) Long.parseLong(s, 16);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    public Entity deepCopy() {
        try {
            return fromJson(this.toJson());
        } catch (JSONException e) {
            return new Entity();
        }
    }

    public boolean canJump() {
        return isGrounded || coyoteTimer > 0.001f;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("tag", tag);
        o.put("is3D", is3D);
        o.put("primitive", primitive);
        o.put("customObjName", customObjName);
        o.put("customSpriteName", customSpriteName);
        o.put("spriteFrames", spriteFrames);
        o.put("animFps", animFps);
        o.put("x", x);
        o.put("y", y);
        o.put("z", z);
        o.put("rotX", rotX);
        o.put("rotY", rotY);
        o.put("rotZ", rotZ);
        o.put("scaleX", scaleX);
        o.put("scaleY", scaleY);
        o.put("scaleZ", scaleZ);
        o.put("colorHex", colorHex);
        o.put("secondaryColorHex", secondaryColorHex);
        o.put("alpha", alpha);
        o.put("checkerTexture", checkerTexture);
        o.put("emissive", emissive);
        o.put("visible", visible);
        o.put("material", material);
        o.put("texture", texture);
        o.put("bodyType", bodyType);
        o.put("collider", collider);
        o.put("isTrigger", isTrigger);
        o.put("fixedRotation", fixedRotation);
        o.put("ccdBullet", ccdBullet);
        o.put("mass", mass);
        o.put("density", density);
        o.put("collisionLayer", collisionLayer);
        o.put("collisionMask", collisionMask);
        o.put("vx", vx);
        o.put("vy", vy);
        o.put("vz", vz);
        o.put("angularVel", angularVel);
        o.put("gravityScale", gravityScale);
        o.put("bounce", bounce);
        o.put("friction", friction);
        o.put("drag", drag);
        o.put("spinX", spinX);
        o.put("spinY", spinY);
        o.put("spinZ", spinZ);
        o.put("parentId", parentId);
        o.put("localX", localX);
        o.put("localY", localY);
        o.put("localZ", localZ);
        o.put("localRotX", localRotX);
        o.put("localRotY", localRotY);
        o.put("localRotZ", localRotZ);
        o.put("localScaleX", localScaleX);
        o.put("localScaleY", localScaleY);
        o.put("localScaleZ", localScaleZ);
        return o;
    }

    public static Entity fromJson(JSONObject o) {
        Entity e = new Entity();
        e.id = o.optString("id", e.id);
        e.name = o.optString("name", "Entity");
        e.tag = o.optString("tag", "decor");
        e.is3D = o.optBoolean("is3D", true);
        e.primitive = o.optString("primitive", e.is3D ? "CUBE" : "RECT");
        e.customObjName = o.optString("customObjName", "");
        e.customSpriteName = o.optString("customSpriteName", "");
        e.spriteFrames = o.optString("spriteFrames", "");
        e.animFps = (float) o.optDouble("animFps", 6.0);
        e.x = (float) o.optDouble("x", 0.0);
        e.y = (float) o.optDouble("y", 0.0);
        e.z = (float) o.optDouble("z", 0.0);
        e.rotX = (float) o.optDouble("rotX", 0.0);
        e.rotY = (float) o.optDouble("rotY", 0.0);
        e.rotZ = (float) o.optDouble("rotZ", 0.0);
        e.scaleX = (float) o.optDouble("scaleX", 1.0);
        e.scaleY = (float) o.optDouble("scaleY", 1.0);
        e.scaleZ = (float) o.optDouble("scaleZ", 1.0);
        e.colorHex = o.optString("colorHex", "#FFFFFF");
        e.secondaryColorHex = o.optString("secondaryColorHex", "#27272A");
        e.alpha = (float) o.optDouble("alpha", 1.0);
        e.checkerTexture = o.optBoolean("checkerTexture", false);
        e.emissive = o.optBoolean("emissive", false);
        e.visible = o.optBoolean("visible", true);
        e.material = o.optString("material", "MATTE");
        e.texture = o.optString("texture", "NONE");
        e.bodyType = o.optString("bodyType", "STATIC");
        e.collider = o.optString("collider", "BOX");
        e.isTrigger = o.optBoolean("isTrigger", false);
        e.fixedRotation = o.optBoolean("fixedRotation", true);
        e.ccdBullet = o.optBoolean("ccdBullet", false);
        e.mass = (float) o.optDouble("mass", 1.0);
        e.density = (float) o.optDouble("density", 1.0);
        e.collisionLayer = o.optInt("collisionLayer", 1);
        e.collisionMask = o.optInt("collisionMask", 0xFFFF);
        e.vx = (float) o.optDouble("vx", 0.0);
        e.vy = (float) o.optDouble("vy", 0.0);
        e.vz = (float) o.optDouble("vz", 0.0);
        e.angularVel = (float) o.optDouble("angularVel", 0.0);
        e.gravityScale = (float) o.optDouble("gravityScale", 1.0);
        e.bounce = (float) o.optDouble("bounce", 0.1);
        e.friction = (float) o.optDouble("friction", 0.65);
        e.drag = (float) o.optDouble("drag", 0.02);
        e.spinX = (float) o.optDouble("spinX", 0.0);
        e.spinY = (float) o.optDouble("spinY", 0.0);
        e.spinZ = (float) o.optDouble("spinZ", 0.0);
        e.parentId = o.optString("parentId", "");
        e.localX = (float) o.optDouble("localX", 0.0);
        e.localY = (float) o.optDouble("localY", 0.0);
        e.localZ = (float) o.optDouble("localZ", 0.0);
        e.localRotX = (float) o.optDouble("localRotX", 0.0);
        e.localRotY = (float) o.optDouble("localRotY", 0.0);
        e.localRotZ = (float) o.optDouble("localRotZ", 0.0);
        e.localScaleX = (float) o.optDouble("localScaleX", 1.0);
        e.localScaleY = (float) o.optDouble("localScaleY", 1.0);
        e.localScaleZ = (float) o.optDouble("localScaleZ", 1.0);
        return e;
    }
}
