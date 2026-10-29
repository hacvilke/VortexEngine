package com.vortex.engine.core.model;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.flow.VortexBlueprintGraph;
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
    public String scriptLanguage = "JS";     // "JS", "JAVA", "LUA", "VXS"
    public boolean useBox2D = true;          // JBox2D Rigid-Body solver for 2D

    // Custom Procedural Terrain Parameters (FastNoiseLite)
    public int terrainSeed = 1337;
    public float terrainFreq = 0.065f;

    // Built-in 16-Step Polyphonic Chiptune Sequencer
    public boolean musicEnabled = false;
    public int musicBpm = 128;
    public String musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK;

    // Visual Event-Sheet Rules ("Vortex Flow") — preserved intact
    public final List<VortexFlowCompiler.FlowRule> flowRules = new ArrayList<>();

    // Visual Node-Based Blueprint Graph ("Blueprint Diagram" v4.3)
    public final List<VortexBlueprintGraph.BpNode> blueprintNodes = new ArrayList<>();
    public final List<VortexBlueprintGraph.BpLink> blueprintLinks = new ArrayList<>();

    // Custom Icon Theme (Monochrome Carbon + Crisp White defaults)
    public String iconBgHex = "#101013";
    public String iconAccentHex = "#FFFFFF";
    public String iconGlyph = "CUBE"; // CUBE, ROCKET, SWORD, STAR

    // Environment & Camera
    public String skyColorHex = "#0B0B0E";
    public String ambientColorHex = "#2A2A30";
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

    // ---- v4.6.0 Godot/Unreal-style lighting rig (DirectionalLight3D + ambient hemisphere) ----
    public float sunYawDeg = 42f;          // compass direction of the key light
    public float sunPitchDeg = 54f;        // elevation (90 = straight overhead)
    public float sunIntensity = 1.0f;      // 0 = flat, 1 = default, 2 = harsh
    public float ambientStrength = 0.34f;  // hemisphere fill so shadowed faces never go black
    public String sunColorHex = "#FFF6E5";
    public boolean shadows = true;         // soft contact shadows under objects
    public float shadowStrength = 0.38f;
    public float zoom2D = 18f;

    // Controls & Editor Settings
    public boolean showGrid = true;
    public boolean showJoystick = true;
    public boolean showActionButtons = true;
    public long updatedAt = System.currentTimeMillis();

    public final List<Entity> entities = new ArrayList<>();

    // Primary/Legacy script code + Multi-File Script Directory (v4.3)
    public String scriptCode = "";
    public final Map<String, String> scriptFiles = new LinkedHashMap<>();
    public String activeScriptFile = "";

    public final Map<String, String> customObjMeshes = new LinkedHashMap<>();
    // 16x16 pixel-art sprites encoded as 256 comma-separated #RRGGBB or '.' transparent tokens
    public final Map<String, String> customSprites = new LinkedHashMap<>();
    // ---- v4.8.0 voxel (Minecraft-style) world ----
    /** When true the project renders and simulates a block world instead of plain entities. */
    public boolean voxelEnabled = false;
    public int voxelSeed = 4711;
    public float voxelSpawnX = 48f;
    public float voxelSpawnZ = 48f;
    public float voxelSpawnYaw = 200f;
    /** Render distance in blocks for the phone renderer (0 = whole world). */
    public int voxelRenderRadius = 46;
    /** Run-length encoded block data; empty means "generate from voxelSeed". */
    public String voxelWorldData = "";
    /** Live world + player; never serialized. */
    public transient com.vortex.engine.core.voxel.VoxelRuntime voxelRuntime = null;

    /** v4.7.0 — reusable scene-tree prefabs (custom + seeded built-ins). */
    public final List<Prefab> prefabs = new ArrayList<>();

    public GameProject() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        ensurePrefabsInitialized();
    }

    public String getScriptExtension() {
        if ("JAVA".equalsIgnoreCase(scriptLanguage)) return ".java";
        if ("JS".equalsIgnoreCase(scriptLanguage)) return ".js";
        if ("LUA".equalsIgnoreCase(scriptLanguage)) return ".lua";
        return ".vxs";
    }

    public String defaultMainFileName() {
        return "scripts/main" + getScriptExtension();
    }

    public static String normalizeScriptPath(String path) {
        if (path == null) return "";
        String p = path.trim().replace('\\', '/').replaceAll("/+", "/");
        while (p.startsWith("/")) p = p.substring(1);
        while (p.startsWith("./")) p = p.substring(2);
        return p;
    }

    public void ensureScriptFilesInitialized() {
        if (scriptFiles.isEmpty()) {
            String mainPath = defaultMainFileName();
            scriptFiles.put(mainPath, scriptCode == null ? "" : scriptCode);
            activeScriptFile = mainPath;
        } else if (activeScriptFile == null || activeScriptFile.isEmpty() || !scriptFiles.containsKey(activeScriptFile)) {
            activeScriptFile = scriptFiles.keySet().iterator().next();
        }
        if (scriptCode == null || scriptCode.isEmpty()) {
            String first = scriptFiles.get(activeScriptFile);
            if (first != null) scriptCode = first;
        }
    }

    public String getActiveScriptContent() {
        ensureScriptFilesInitialized();
        String c = scriptFiles.get(activeScriptFile);
        return c != null ? c : (scriptCode == null ? "" : scriptCode);
    }

    public void putScriptFile(String path, String content) {
        String norm = normalizeScriptPath(path);
        if (norm.isEmpty()) norm = defaultMainFileName();
        if (!norm.contains(".")) norm = norm + getScriptExtension();
        String safeContent = content == null ? "" : content;
        scriptFiles.put(norm, safeContent);
        if (activeScriptFile == null || activeScriptFile.isEmpty() || norm.equals(activeScriptFile)) {
            activeScriptFile = norm;
            scriptCode = safeContent;
        }
    }

    public boolean renameScriptFile(String oldPath, String newPath) {
        String o = normalizeScriptPath(oldPath);
        String n = normalizeScriptPath(newPath);
        if (o.isEmpty() || n.isEmpty() || !scriptFiles.containsKey(o) || o.equals(n)) return false;
        if (!n.contains(".")) n = n + getScriptExtension();
        Map<String, String> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : scriptFiles.entrySet()) {
            if (entry.getKey().equals(o)) {
                rebuilt.put(n, entry.getValue());
            } else {
                rebuilt.put(entry.getKey(), entry.getValue());
            }
        }
        scriptFiles.clear();
        scriptFiles.putAll(rebuilt);
        if (o.equals(activeScriptFile)) activeScriptFile = n;
        return true;
    }

    public boolean removeScriptFile(String path) {
        String o = normalizeScriptPath(path);
        if (scriptFiles.size() <= 1 || !scriptFiles.containsKey(o)) return false;
        scriptFiles.remove(o);
        if (o.equals(activeScriptFile)) {
            activeScriptFile = scriptFiles.keySet().iterator().next();
            scriptCode = scriptFiles.get(activeScriptFile);
        }
        return true;
    }

    /**
     * Ordered list of script files: helper/object modules first so classes & utilities
     * are registered in scope, followed by `scripts/main.*` (entry point), plus optional
     * live Blueprint Graph module if `blueprintNodes` has active nodes!
     */
    public Map<String, String> getOrderedRuntimeScriptModules() {
        ensureScriptFilesInitialized();
        Map<String, String> ordered = new LinkedHashMap<>();
        String mainKey = null;
        for (String k : scriptFiles.keySet()) {
            String lower = k.toLowerCase();
            if (lower.endsWith("/main.js") || lower.endsWith("/main.java")
                    || lower.endsWith("/main.lua") || lower.endsWith("/main.vxs")
                    || lower.equals("main.js") || lower.equals("main.java")
                    || lower.equals("main.lua") || lower.equals("main.vxs")) {
                mainKey = k;
                break;
            }
        }
        for (Map.Entry<String, String> e : scriptFiles.entrySet()) {
            if (mainKey != null && mainKey.equals(e.getKey())) continue;
            ordered.put(e.getKey(), e.getValue());
        }
        if (mainKey != null) {
            ordered.put(mainKey, scriptFiles.get(mainKey));
        }
        if (!blueprintNodes.isEmpty() && !"VXS".equalsIgnoreCase(scriptLanguage)) {
            String bpCode = VortexBlueprintGraph.compileGraph(this, scriptLanguage);
            if (bpCode != null && !bpCode.trim().isEmpty()) {
                ordered.put("__blueprint/graph" + getScriptExtension(), bpCode);
            }
        }
        return ordered;
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
            ensureScriptFilesInitialized();
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
            o.put("blueprintNodes", VortexBlueprintGraph.nodesToJson(blueprintNodes));
            o.put("blueprintLinks", VortexBlueprintGraph.linksToJson(blueprintLinks));
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
            o.put("sunYawDeg", sunYawDeg);
            o.put("sunPitchDeg", sunPitchDeg);
            o.put("sunIntensity", sunIntensity);
            o.put("ambientStrength", ambientStrength);
            o.put("sunColorHex", sunColorHex);
            o.put("shadows", shadows);
            o.put("shadowStrength", shadowStrength);
            o.put("zoom2D", zoom2D);
            o.put("showGrid", showGrid);
            o.put("showJoystick", showJoystick);
            o.put("showActionButtons", showActionButtons);
            o.put("updatedAt", updatedAt);
            o.put("scriptCode", scriptCode == null ? "" : scriptCode);
            o.put("activeScriptFile", activeScriptFile == null ? "" : activeScriptFile);

            JSONObject filesJson = new JSONObject();
            for (Map.Entry<String, String> entry : scriptFiles.entrySet()) {
                filesJson.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
            }
            o.put("scriptFiles", filesJson);

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

            JSONArray prefabArr = new JSONArray();
            for (Prefab pf : prefabs) {
                try {
                    prefabArr.put(pf.toJson());
                } catch (Exception ignored) { }
            }
            o.put("prefabs", prefabArr);

            o.put("voxelEnabled", voxelEnabled);
            o.put("voxelSeed", voxelSeed);
            o.put("voxelSpawnX", voxelSpawnX);
            o.put("voxelSpawnZ", voxelSpawnZ);
            o.put("voxelSpawnYaw", voxelSpawnYaw);
            o.put("voxelRenderRadius", voxelRenderRadius);
            // Always persist the LIVE world, not the snapshot taken when it was first loaded —
            // otherwise every block mined or placed during this session would be lost on save.
            String liveVoxel = currentVoxelData();
            if (liveVoxel != null && !liveVoxel.isEmpty()) o.put("voxelWorldData", liveVoxel);
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
        String defaultLang = o.has("scriptLanguage") ? "LUA" : "VXS";
        p.scriptLanguage = o.optString("scriptLanguage", defaultLang);
        p.useBox2D = o.optBoolean("useBox2D", true);
        p.terrainSeed = o.optInt("terrainSeed", 1337);
        p.terrainFreq = (float) o.optDouble("terrainFreq", 0.065);
        p.musicEnabled = o.optBoolean("musicEnabled", false);
        p.musicBpm = o.optInt("musicBpm", 128);
        p.musicPattern = o.optString("musicPattern", ChiptuneGenerator.PRESET_CYBERPUNK);
        p.flowRules.addAll(VortexFlowCompiler.fromJsonArray(o.optJSONArray("flowRules")));
        p.blueprintNodes.addAll(VortexBlueprintGraph.nodesFromJson(o.optJSONArray("blueprintNodes")));
        p.blueprintLinks.addAll(VortexBlueprintGraph.linksFromJson(o.optJSONArray("blueprintLinks")));
        p.iconBgHex = o.optString("iconBgHex", "#101013");
        p.iconAccentHex = o.optString("iconAccentHex", "#FFFFFF");
        p.iconGlyph = o.optString("iconGlyph", "CUBE");
        p.skyColorHex = o.optString("skyColorHex", "#0B0B0E");
        p.ambientColorHex = o.optString("ambientColorHex", "#2A2A30");
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
        p.sunYawDeg = (float) o.optDouble("sunYawDeg", 42.0);
        p.sunPitchDeg = (float) o.optDouble("sunPitchDeg", 54.0);
        p.sunIntensity = (float) o.optDouble("sunIntensity", 1.0);
        p.ambientStrength = (float) o.optDouble("ambientStrength", 0.34);
        p.sunColorHex = o.optString("sunColorHex", "#FFF6E5");
        p.shadows = o.optBoolean("shadows", true);
        p.shadowStrength = (float) o.optDouble("shadowStrength", 0.38);
        p.zoom2D = (float) o.optDouble("zoom2D", 18.0);
        p.showGrid = o.optBoolean("showGrid", true);
        p.showJoystick = o.optBoolean("showJoystick", true);
        p.showActionButtons = o.optBoolean("showActionButtons", true);
        p.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        p.scriptCode = o.optString("scriptCode", "");
        p.activeScriptFile = o.optString("activeScriptFile", "");

        JSONObject filesObj = o.optJSONObject("scriptFiles");
        if (filesObj != null) {
            Iterator<String> fKeys = filesObj.keys();
            while (fKeys.hasNext()) {
                String k = fKeys.next();
                String norm = normalizeScriptPath(k);
                if (!norm.isEmpty()) {
                    p.scriptFiles.put(norm, filesObj.optString(k, ""));
                }
            }
        }
        p.ensureScriptFilesInitialized();

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

        JSONArray prefabArr = o.optJSONArray("prefabs");
        if (prefabArr != null) {
            p.prefabs.clear();   // the constructor seeds built-ins; the file is authoritative
            for (int i = 0; i < prefabArr.length(); i++) {
                JSONObject po = prefabArr.optJSONObject(i);
                if (po != null) p.prefabs.add(Prefab.fromJson(po));
            }
        }
        p.ensurePrefabsInitialized();

        p.voxelEnabled = o.optBoolean("voxelEnabled", false);
        p.voxelSeed = o.optInt("voxelSeed", 4711);
        p.voxelSpawnX = (float) o.optDouble("voxelSpawnX", 48.0);
        p.voxelSpawnZ = (float) o.optDouble("voxelSpawnZ", 48.0);
        p.voxelSpawnYaw = (float) o.optDouble("voxelSpawnYaw", 200.0);
        p.voxelRenderRadius = o.optInt("voxelRenderRadius", 46);
        p.voxelWorldData = o.optString("voxelWorldData", "");
        return p;
    }

    /** Seeds the built-in prefab palette once; custom prefabs are preserved. */
    public void ensurePrefabsInitialized() {
        if (!prefabs.isEmpty()) return;
        prefabs.addAll(Prefab.defaults());
    }

    public Prefab findPrefab(String name) {
        for (Prefab p : prefabs) {
            if (p.name != null && p.name.equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /** True when a prefab with this name already exists (case-insensitive). */
    public boolean hasPrefab(String name) {
        return findPrefab(name) != null;
    }

    /** Returns the live voxel runtime, creating (or loading) the world on first use. */
    public com.vortex.engine.core.voxel.VoxelRuntime ensureVoxelRuntime() {
        if (voxelRuntime == null) voxelRuntime = com.vortex.engine.core.voxel.VoxelRuntime.forProject(this);
        return voxelRuntime;
    }

    public void disposeVoxelRuntime() {
        syncVoxelData();
        voxelRuntime = null;
    }

    /** Re-encodes the live block world into {@link #voxelWorldData} (call before saving). */
    public void syncVoxelData() {
        if (voxelEnabled && voxelRuntime != null) {
            try {
                voxelWorldData = voxelRuntime.world != null ? voxelRuntime.world.serializeRle() : "";
            } catch (Exception ignored) { }
        }
    }

    /** RLE block data as it should be written to disk right now. */
    private String currentVoxelData() {
        if (voxelEnabled && voxelRuntime != null && voxelRuntime.world != null) {
            try {
                return voxelRuntime.world.serializeRle();
            } catch (Exception ignored) { }
        }
        return voxelWorldData;
    }
}
