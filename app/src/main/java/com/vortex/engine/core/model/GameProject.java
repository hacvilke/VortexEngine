package com.vortex.engine.core.model;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.flow.VortexFlowCompiler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GameProject {
    public String id;
    public String title = "My Vortex Game";
    public String packageName = "com.vortex.game.mygame";
    public String versionName = "1.0.0";
    public int versionCode = 1;
    public String orientation = "landscape"; // landscape, portrait, sensor
    public String dimensionMode = "3D";      // "3D" or "2D"
    public String scriptLanguage = "LUA";    // "LUA" (LuaJ 5.2) or "VXS" (VortexScript)
    public boolean useBox2D = true;          // JBox2D Rigid-Body solver for 2D

    // Custom Procedural Terrain Parameters (FastNoiseLite)
    public int terrainSeed = 1337;
    public float terrainFreq = 0.065f;

    // Built-in 16-Step Polyphonic Chiptune Sequencer (v3.0)
    public boolean musicEnabled = false;
    public int musicBpm = 128;
    public String musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK;

    // Visual Event-Sheet Rules ("Vortex Flow" v3.0)
    public final List<VortexFlowCompiler.FlowRule> flowRules = new ArrayList<>();

    // Custom Icon Theme
    public String iconBgHex = "#0D1321";
    public String iconAccentHex = "#00E5FF";
    public String iconGlyph = "CUBE"; // CUBE, ROCKET, SWORD, STAR

    // Environment & Camera
    public String skyColorHex = "#0B1020";
    public String ambientColorHex = "#24344D";
    public boolean fogEnabled = true;
    public float fogNear = 18f;
    public float fogFar = 68f;

    public float gravityX = 0f;
    public float gravityY = -22f;
    public float gravityZ = 0f;

    public String cameraMode = "FOLLOW"; // FOLLOW, ORBIT, FIRST_PERSON, FIXED
    public String cameraTargetName = "Player";
    public float cameraDistance = 11f;
    public float cameraHeight = 5.5f;
    public float cameraFov = 60f;
    public float zoom2D = 18f;

    // Controls & Editor Settings
    public boolean showGrid = true;
    public boolean showJoystick = true;
    public boolean showActionButtons = true;
    public long updatedAt = System.currentTimeMillis();

    public final List<Entity> entities = new ArrayList<>();
    public String scriptCode = "";
    public final Map<String, String> customObjMeshes = new LinkedHashMap<>();
    // 16x16 pixel-art sprites encoded as 256 comma-separated #RRGGBB or '.' transparent tokens
    public final Map<String, String> customSprites = new LinkedHashMap<>();

    public GameProject() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
    }

    public String getSlug() {
        String s = title.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty()) s = "vortex_game";
        if (Character.isDigit(s.charAt(0))) s = "g_" + s;
        return s;
    }

    public Entity findByName(String name) {
        if (name == null) return null;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e.alive && name.equalsIgnoreCase(e.name)) {
                return e;
            }
        }
        return null;
    }

    public Entity findById(String entityId) {
        if (entityId == null) return null;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (entityId.equals(e.id)) {
                return e;
            }
        }
        return null;
    }

    public GameProject deepCopy() {
        try {
            return fromJsonString(toJsonString());
        } catch (Exception e) {
            return new GameProject();
        }
    }

    public String toJsonString() {
        try {
            JSONObject o = new JSONObject();
            o.put("id", id);
            o.put("title", title);
            o.put("packageName", packageName);
            o.put("versionName", versionName);
            o.put("versionCode", versionCode);
            o.put("orientation", orientation);
            o.put("dimensionMode", dimensionMode);
            o.put("scriptLanguage", scriptLanguage);
            o.put("useBox2D", useBox2D);
            o.put("terrainSeed", terrainSeed);
            o.put("terrainFreq", terrainFreq);
            o.put("musicEnabled", musicEnabled);
            o.put("musicBpm", musicBpm);
            o.put("musicPattern", musicPattern);
            o.put("flowRules", VortexFlowCompiler.toJsonArray(flowRules));
            o.put("iconBgHex", iconBgHex);
            o.put("iconAccentHex", iconAccentHex);
            o.put("iconGlyph", iconGlyph);
            o.put("skyColorHex", skyColorHex);
            o.put("ambientColorHex", ambientColorHex);
            o.put("fogEnabled", fogEnabled);
            o.put("fogNear", fogNear);
            o.put("fogFar", fogFar);
            o.put("gravityX", gravityX);
            o.put("gravityY", gravityY);
            o.put("gravityZ", gravityZ);
            o.put("cameraMode", cameraMode);
            o.put("cameraTargetName", cameraTargetName);
            o.put("cameraDistance", cameraDistance);
            o.put("cameraHeight", cameraHeight);
            o.put("cameraFov", cameraFov);
            o.put("zoom2D", zoom2D);
            o.put("showGrid", showGrid);
            o.put("showJoystick", showJoystick);
            o.put("showActionButtons", showActionButtons);
            o.put("updatedAt", updatedAt);
            o.put("scriptCode", scriptCode);

            JSONArray arr = new JSONArray();
            for (Entity e : entities) {
                arr.put(e.toJson());
            }
            o.put("entities", arr);

            JSONObject meshes = new JSONObject();
            for (Map.Entry<String, String> entry : customObjMeshes.entrySet()) {
                meshes.put(entry.getKey(), entry.getValue());
            }
            o.put("customObjMeshes", meshes);

            JSONObject sprites = new JSONObject();
            for (Map.Entry<String, String> entry : customSprites.entrySet()) {
                sprites.put(entry.getKey(), entry.getValue());
            }
            o.put("customSprites", sprites);
            return o.toString(2);
        } catch (JSONException ex) {
            return "{}";
        }
    }

    public static GameProject fromJsonString(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        GameProject p = new GameProject();
        p.id = o.optString("id", p.id);
        p.title = o.optString("title", "My Vortex Game");
        p.packageName = o.optString("packageName", "com.vortex.game.mygame");
        p.versionName = o.optString("versionName", "1.0.0");
        p.versionCode = o.optInt("versionCode", 1);
        p.orientation = o.optString("orientation", "landscape");
        p.dimensionMode = o.optString("dimensionMode", "3D");
        // Backwards compatible: existing v1.0 projects without scriptLanguage default to VXS
        String defaultLang = o.has("scriptLanguage") ? "LUA" : "VXS";
        p.scriptLanguage = o.optString("scriptLanguage", defaultLang);
        p.useBox2D = o.optBoolean("useBox2D", true);
        p.terrainSeed = o.optInt("terrainSeed", 1337);
        p.terrainFreq = (float) o.optDouble("terrainFreq", 0.065);
        p.musicEnabled = o.optBoolean("musicEnabled", false);
        p.musicBpm = o.optInt("musicBpm", 128);
        p.musicPattern = o.optString("musicPattern", ChiptuneGenerator.PRESET_CYBERPUNK);
        p.flowRules.addAll(VortexFlowCompiler.fromJsonArray(o.optJSONArray("flowRules")));
        p.iconBgHex = o.optString("iconBgHex", "#0D1321");
        p.iconAccentHex = o.optString("iconAccentHex", "#00E5FF");
        p.iconGlyph = o.optString("iconGlyph", "CUBE");
        p.skyColorHex = o.optString("skyColorHex", "#0B1020");
        p.ambientColorHex = o.optString("ambientColorHex", "#24344D");
        p.fogEnabled = o.optBoolean("fogEnabled", true);
        p.fogNear = (float) o.optDouble("fogNear", 18.0);
        p.fogFar = (float) o.optDouble("fogFar", 68.0);
        p.gravityX = (float) o.optDouble("gravityX", 0.0);
        p.gravityY = (float) o.optDouble("gravityY", -22.0);
        p.gravityZ = (float) o.optDouble("gravityZ", 0.0);
        p.cameraMode = o.optString("cameraMode", "FOLLOW");
        p.cameraTargetName = o.optString("cameraTargetName", "Player");
        p.cameraDistance = (float) o.optDouble("cameraDistance", 11.0);
        p.cameraHeight = (float) o.optDouble("cameraHeight", 5.5);
        p.cameraFov = (float) o.optDouble("cameraFov", 60.0);
        p.zoom2D = (float) o.optDouble("zoom2D", 18.0);
        p.showGrid = o.optBoolean("showGrid", true);
        p.showJoystick = o.optBoolean("showJoystick", true);
        p.showActionButtons = o.optBoolean("showActionButtons", true);
        p.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        p.scriptCode = o.optString("scriptCode", "");

        JSONArray arr = o.optJSONArray("entities");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject eo = arr.optJSONObject(i);
                if (eo != null) {
                    p.entities.add(Entity.fromJson(eo));
                }
            }
        }

        JSONObject meshes = o.optJSONObject("customObjMeshes");
        if (meshes != null) {
            Iterator<String> keys = meshes.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                p.customObjMeshes.put(k, meshes.optString(k, ""));
            }
        }

        JSONObject sprites = o.optJSONObject("customSprites");
        if (sprites != null) {
            Iterator<String> keys = sprites.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                p.customSprites.put(k, sprites.optString(k, ""));
            }
        }
        return p;
    }
}
