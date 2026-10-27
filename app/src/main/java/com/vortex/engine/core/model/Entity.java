package com.vortex.engine.core.model;

import android.graphics.Color;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

public class Entity {
    // 3D Primitives: CUBE, SPHERE, PYRAMID, CYLINDER, PLANE, CUSTOM_OBJ
    // 2D Primitives: RECT, CIRCLE, TRIANGLE, STAR, SPRITE_HERO, SPRITE_SHIP, SPRITE_SLIME, SPRITE_COIN
    public String id;
    public String name;
    public String tag;        // player, solid, coin, enemy, hazard, goal, projectile, decor
    public boolean is3D;
    public String primitive;
    public String customObjName = "";

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
    public String colorHex = "#00E5FF";
    public String secondaryColorHex = "#1A2639";
    public float alpha = 1.0f;
    public boolean checkerTexture = false;
    public boolean emissive = false;
    public boolean visible = true;

    // Physics & Motion
    public String bodyType = "STATIC"; // STATIC, DYNAMIC, KINEMATIC, NONE
    public String collider = "BOX";    // BOX, SPHERE, NONE
    public boolean isTrigger = false;
    public float vx = 0f;
    public float vy = 0f;
    public float vz = 0f;
    public float gravityScale = 1.0f;
    public float bounce = 0.1f;
    public float friction = 0.85f;
    public float drag = 0.02f;
    public boolean isGrounded = false;
    public boolean alive = true;

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
        return e;
    }

    public float[] parseColorRgba() {
        return hexToRgba(colorHex, alpha);
    }

    public float[] parseSecondaryRgba() {
        return hexToRgba(secondaryColorHex, alpha);
    }

    public static float[] hexToRgba(String hex, float alphaOverride) {
        try {
            String clean = (hex == null || hex.isEmpty()) ? "#00E5FF" : hex.trim();
            if (!clean.startsWith("#")) clean = "#" + clean;
            int c = Color.parseColor(clean);
            return new float[]{
                    Color.red(c) / 255f,
                    Color.green(c) / 255f,
                    Color.blue(c) / 255f,
                    alphaOverride
            };
        } catch (Exception ex) {
            return new float[]{0.0f, 0.9f, 1.0f, alphaOverride};
        }
    }

    public Entity deepCopy() {
        try {
            return fromJson(this.toJson());
        } catch (JSONException e) {
            return new Entity();
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("tag", tag);
        o.put("is3D", is3D);
        o.put("primitive", primitive);
        o.put("customObjName", customObjName);
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
        o.put("bodyType", bodyType);
        o.put("collider", collider);
        o.put("isTrigger", isTrigger);
        o.put("vx", vx);
        o.put("vy", vy);
        o.put("vz", vz);
        o.put("gravityScale", gravityScale);
        o.put("bounce", bounce);
        o.put("friction", friction);
        o.put("drag", drag);
        o.put("spinX", spinX);
        o.put("spinY", spinY);
        o.put("spinZ", spinZ);
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
        e.x = (float) o.optDouble("x", 0.0);
        e.y = (float) o.optDouble("y", 0.0);
        e.z = (float) o.optDouble("z", 0.0);
        e.rotX = (float) o.optDouble("rotX", 0.0);
        e.rotY = (float) o.optDouble("rotY", 0.0);
        e.rotZ = (float) o.optDouble("rotZ", 0.0);
        e.scaleX = (float) o.optDouble("scaleX", 1.0);
        e.scaleY = (float) o.optDouble("scaleY", 1.0);
        e.scaleZ = (float) o.optDouble("scaleZ", 1.0);
        e.colorHex = o.optString("colorHex", "#00E5FF");
        e.secondaryColorHex = o.optString("secondaryColorHex", "#1A2639");
        e.alpha = (float) o.optDouble("alpha", 1.0);
        e.checkerTexture = o.optBoolean("checkerTexture", false);
        e.emissive = o.optBoolean("emissive", false);
        e.visible = o.optBoolean("visible", true);
        e.bodyType = o.optString("bodyType", "STATIC");
        e.collider = o.optString("collider", "BOX");
        e.isTrigger = o.optBoolean("isTrigger", false);
        e.vx = (float) o.optDouble("vx", 0.0);
        e.vy = (float) o.optDouble("vy", 0.0);
        e.vz = (float) o.optDouble("vz", 0.0);
        e.gravityScale = (float) o.optDouble("gravityScale", 1.0);
        e.bounce = (float) o.optDouble("bounce", 0.1);
        e.friction = (float) o.optDouble("friction", 0.85);
        e.drag = (float) o.optDouble("drag", 0.02);
        e.spinX = (float) o.optDouble("spinX", 0.0);
        e.spinY = (float) o.optDouble("spinY", 0.0);
        e.spinZ = (float) o.optDouble("spinZ", 0.0);
        return e;
    }
}
