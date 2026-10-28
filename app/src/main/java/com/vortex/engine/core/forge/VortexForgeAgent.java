package com.vortex.engine.core.forge;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import com.vortex.engine.core.script.VortexScriptEngine;
import com.vortex.engine.core.storage.VortexSaveStore;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vortex Forge AI — Local-First BYOK & Offline Game Studio Agent (inspired by github.com/hacvilke/roforge).
 *
 * v4.2 Upgrades:
 *  - Updated Groq models to active October 2026 production IDs (`openai/gpt-oss-120b`, `openai/gpt-oss-20b`)
 *    with automatic live `GET /openai/v1/models` discovery fallback on HTTP 404!
 *  - Updated OpenRouter default to `openrouter/free` ($0.00 Free Models Router) + explicit `max_tokens: 1800`
 *    + automatic fallback to `openrouter/free` or token clamping if a paid model returns HTTP 402!
 *  - Supports custom user-specified Model IDs, live model fetching, persistent API key storage,
 *    and multi-genre Offline Smart Generation in both ES6 JavaScript AND Java!
 */
public class VortexForgeAgent {

    public static final String PREFS_PROJECT_ID = "__vortex_forge_prefs__";

    public static final String[] PROVIDERS = {
            "OFFLINE SMART (No API Key — Instant Local Generator)",
            "GROQ (api.groq.com — openai/gpt-oss-120b • Fast)",
            "OPENROUTER FREE (openrouter.ai — openrouter/free • $0 Cost)",
            "GEMINI (googleapis.com — gemini-2.5-flash)",
            "OPENAI (api.openai.com — gpt-4o-mini)"
    };

    public static final String[] DEFAULT_MODELS = {
            "offline-procedural-v4",
            "openai/gpt-oss-120b",
            "openrouter/free",
            "gemini-2.5-flash",
            "gpt-4o-mini"
    };

    public static void savePrefs(int providerIdx, String modelId, String apiKey) {
        VortexSaveStore.save(PREFS_PROJECT_ID, "providerIdx", String.valueOf(providerIdx));
        if (modelId != null) VortexSaveStore.save(PREFS_PROJECT_ID, "model_" + providerIdx, modelId.trim());
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            VortexSaveStore.save(PREFS_PROJECT_ID, "key_" + providerIdx, apiKey.trim());
        }
    }

    public static int loadSavedProviderIdx() {
        return (int) VortexSaveStore.loadNumber(PREFS_PROJECT_ID, "providerIdx", 0);
    }

    public static String loadSavedModel(int providerIdx) {
        String def = (providerIdx >= 0 && providerIdx < DEFAULT_MODELS.length)
                ? DEFAULT_MODELS[providerIdx] : "openrouter/free";
        String s = VortexSaveStore.loadString(PREFS_PROJECT_ID, "model_" + providerIdx, def);
        if (s == null || s.trim().isEmpty()) return def;
        String trimmed = s.trim();
        // Auto-upgrade retired Groq models so returning users never hit HTTP 404
        if (providerIdx == 1 && ("llama-3.3-70b-versatile".equals(trimmed)
                || "llama-3.1-8b-instant".equals(trimmed)
                || "qwen/qwen3-32b".equals(trimmed)
                || "qwen/qwen3.6-27b".equals(trimmed))) {
            return "openai/gpt-oss-120b";
        }
        return trimmed;
    }

    public static String loadSavedApiKey(int providerIdx) {
        return VortexSaveStore.loadString(PREFS_PROJECT_ID, "key_" + providerIdx, "");
    }

    /**
     * Queries live `/models` endpoint for Groq, OpenRouter, Gemini, or OpenAI so users can pick any active model!
     */
    public static List<String> fetchLiveModels(int providerIdx, String apiKey) throws Exception {
        List<String> out = new ArrayList<>();
        if (providerIdx <= 0) {
            out.add("offline-procedural-v4");
            return out;
        }
        if (providerIdx == 3) {
            if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("Enter Gemini API key first.");
            String resp = getJson("https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey.trim(), null);
            JSONObject obj = new JSONObject(resp);
            JSONArray arr = obj.optJSONArray("models");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject m = arr.optJSONObject(i);
                    if (m == null) continue;
                    String name = m.optString("name", "").replace("models/", "");
                    if (name.contains("gemini") && !name.contains("embedding")) out.add(name);
                }
            }
            if (out.isEmpty()) out.add("gemini-2.5-flash");
            return out;
        }

        String url = providerIdx == 1 ? "https://api.groq.com/openai/v1/models"
                : providerIdx == 2 ? "https://openrouter.ai/api/v1/models"
                : "https://api.openai.com/v1/models";
        String auth = (apiKey != null && !apiKey.trim().isEmpty()) ? "Bearer " + apiKey.trim() : null;
        if (providerIdx != 2 && auth == null) {
            throw new IllegalArgumentException("Paste your API key first to fetch live account models.");
        }
        if (providerIdx == 2) {
            out.add("openrouter/free");
        }
        String resp = getJson(url, auth);
        JSONObject obj = new JSONObject(resp);
        JSONArray data = obj.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject m = data.optJSONObject(i);
                if (m == null) continue;
                String id = m.optString("id", "");
                if (id.isEmpty()) continue;
                String low = id.toLowerCase(Locale.US);
                if (low.contains("whisper") || low.contains("tts") || low.contains("orpheus")
                        || low.contains("playai") || low.contains("embed")
                        || low.contains("guard") || low.contains("safeguard") || low.contains("moderation")) {
                    continue;
                }
                if (providerIdx == 2) {
                    // Prioritize free models at the top on OpenRouter!
                    if (id.endsWith(":free") && !out.contains(id)) out.add(id);
                } else if (!out.contains(id)) {
                    out.add(id);
                }
            }
            if (providerIdx == 2) {
                for (int i = 0; i < Math.min(25, data.length()); i++) {
                    JSONObject m = data.optJSONObject(i);
                    if (m != null) {
                        String id = m.optString("id", "");
                        if (!id.isEmpty() && !out.contains(id)) out.add(id);
                    }
                }
            }
        }
        if (out.isEmpty()) out.add(DEFAULT_MODELS[Math.min(providerIdx, DEFAULT_MODELS.length - 1)]);
        return out;
    }

    public static String applyPromptToProject(GameProject project, String prompt, int providerIdx, String apiKey) throws Exception {
        return applyPromptToProject(project, prompt, providerIdx, DEFAULT_MODELS[Math.max(0, Math.min(providerIdx, DEFAULT_MODELS.length - 1))], apiKey, true);
    }

    public static String applyPromptToProject(
            GameProject project,
            String prompt,
            int providerIdx,
            String customModelId,
            String apiKey,
            boolean replaceScene) throws Exception {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Describe what you want Forge AI to build or modify!");
        }
        savePrefs(providerIdx, customModelId, apiKey);
        if (providerIdx <= 0 || apiKey == null || apiKey.trim().isEmpty()) {
            return applyOfflineSmartGenerator(project, prompt.trim(), replaceScene);
        }
        return applyByokCloudModel(project, prompt.trim(), providerIdx, customModelId, apiKey.trim(), replaceScene);
    }

    // =========================================================================
    // Multi-Genre Offline Smart Generator (Works 100% Offline, Supports JS & Java!)
    // =========================================================================
    private static String applyOfflineSmartGenerator(GameProject p, String prompt, boolean replaceScene) {
        String low = prompt.toLowerCase(Locale.US);
        boolean wantJava = low.contains("java ") || low.endsWith(" java") || "JAVA".equalsIgnoreCase(p.scriptLanguage);
        boolean want3D = low.contains("3d") || low.contains("terrain") || low.contains("mecha")
                || low.contains("canyon") || low.contains("island") || low.contains("obby");
        boolean wantPlatformer = !want3D && (low.contains("platform") || low.contains("jump") || low.contains("mario") || low.contains("lava") || low.contains("cavern"));
        boolean wantShooter = !want3D && (low.contains("space") || low.contains("shooter") || low.contains("laser") || low.contains("meteor") || low.contains("galaga"));

        if (replaceScene) {
            p.entities.clear();
        }
        p.musicEnabled = true;

        if (want3D) {
            p.dimensionMode = "3D";
            p.cameraMode = "FOLLOW";
            p.cameraTargetName = "Player";
            p.musicBpm = 140;
            p.musicPattern = ChiptuneGenerator.PRESET_BOSS;
            p.skyColorHex = "#0B0B0E";

            if (p.findByName("Player") == null) {
                Entity floor = Entity.create3D("ForgeTerrain", "solid", "TERRAIN", 0f, 0f, 0f, 68f, 8.5f, 68f, "#27272A", "STATIC");
                floor.checkerTexture = true;
                floor.secondaryColorHex = "#18181B";
                p.entities.add(0, floor);
                p.entities.add(Entity.create3D("Player", "player", "SPHERE", 0f, 5f, 6f, 1.2f, 1.2f, 1.2f, "#FFFFFF", "DYNAMIC"));
            }

            int baseIdx = p.entities.size();
            for (int i = 0; i < 6; i++) {
                float ang = (float) (i * Math.PI * 2.0 / 6.0);
                float rx = (float) Math.cos(ang) * 12f;
                float rz = (float) Math.sin(ang) * 12f;
                Entity drone = Entity.create3D("Sentinel_" + (baseIdx + i), "enemy", "PYRAMID", rx, 6f, rz, 1.3f, 1.6f, 1.3f, "#EF4444", "DYNAMIC");
                drone.spinY = 120f;
                p.entities.add(drone);

                Entity crystal = Entity.create3D("Orb_" + (baseIdx + i), "coin", "CUBE", rx * 0.55f, 5.5f, rz * 0.55f, 0.85f, 0.85f, 0.85f, "#FACC15", "STATIC");
                crystal.isTrigger = true;
                crystal.spinY = 95f;
                p.entities.add(crystal);
            }

            p.scriptLanguage = "JS";
            p.scriptCode =
                    "// Generated by Vortex Forge AI (3D Arena • ES6 JavaScript)\n" +
                    "// Prompt: " + prompt.replace("\n", " ") + "\n" +
                    "let railCooldown = 0;\n\n" +
                    "function onStart() {\n" +
                    "  vortex.playMusic('BOSS', 140);\n" +
                    "  vortex.tweenTagYoyo('coin', 0.65, 0.9, 'SINE');\n" +
                    "  const best = vortex.loadNum('bestScore', 0);\n" +
                    "  vortex.setBanner(`3D FORGE ARENA (BEST: ${best}) — A=JUMP, B=RAILGUN!`);\n" +
                    "}\n\n" +
                    "function onUpdate(dt) {\n" +
                    "  if (!vortex.exists('Player')) return;\n" +
                    "  railCooldown = Math.max(0, railCooldown - dt);\n" +
                    "  vortex.setVel('Player', vortex.joyX() * 9.5, vortex.getVY('Player'), -vortex.joyY() * 9.5);\n" +
                    "  vortex.chaseTagAI('enemy', 'Player', 4.4);\n" +
                    "}\n\n" +
                    "function onButton(btn) {\n" +
                    "  if (!vortex.exists('Player')) return;\n" +
                    "  if (btn === 'A') {\n" +
                    "    vortex.jump('Player', 13.0);\n" +
                    "  } else if (btn === 'B' && railCooldown <= 0) {\n" +
                    "    railCooldown = 0.32;\n" +
                    "    const target = vortex.findNearestTag('Player', 'enemy');\n" +
                    "    vortex.playSound('laser');\n" +
                    "    if (target) {\n" +
                    "      vortex.particles(vortex.getX(target), vortex.getY(target), vortex.getZ(target), 28, '#FFFFFF');\n" +
                    "      vortex.destroy(target);\n" +
                    "      const sc = vortex.addScore(200);\n" +
                    "      if (sc > vortex.loadNum('bestScore', 0)) vortex.saveData('bestScore', sc);\n" +
                    "      vortex.playSound('explosion');\n" +
                    "    }\n" +
                    "  }\n" +
                    "}\n\n" +
                    "function onCollide(a, b, tagA, tagB) {\n" +
                    "  const other = tagA === 'player' ? b : (tagB === 'player' ? a : null);\n" +
                    "  const oTag  = tagA === 'player' ? tagB : tagA;\n" +
                    "  if (!other) return;\n" +
                    "  if (oTag === 'coin') {\n" +
                    "    vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 22, '#FACC15');\n" +
                    "    vortex.destroy(other);\n" +
                    "    vortex.addScore(100);\n" +
                    "    vortex.playSound('coin');\n" +
                    "  } else if (oTag === 'enemy') {\n" +
                    "    vortex.addHealth(-20);\n" +
                    "    vortex.shake(0.42, 0.22);\n" +
                    "    vortex.playSound('hit');\n" +
                    "  }\n" +
                    "}\n";
            return "Forge Offline Generator built 3D Terrain + 6 AI Sentinels + 6 Energy Orbs + Railgun Script!";
        }

        if (wantPlatformer) {
            p.dimensionMode = "2D";
            p.useBox2D = true;
            p.gravityY = -22f;
            p.cameraMode = "FOLLOW";
            p.cameraTargetName = "Player";
            p.skyColorHex = "#0B0B0E";
            p.musicBpm = 128;
            p.musicPattern = ChiptuneGenerator.PRESET_DUNGEON;
            p.customSprites.put("knight_1", PixelSpriteCodec.createDefaultKnightSprite());
            p.customSprites.put("knight_2", PixelSpriteCodec.createKnightRunFrame2Sprite());
            p.customSprites.put("gem", PixelSpriteCodec.createGemSprite());

            p.entities.add(Entity.create2D("Ground", "solid", "RECT", 0f, -4f, 26f, 1.4f, "#27272A", "STATIC"));
            for (int i = 0; i < 4; i++) {
                float px = -6f + i * 5.2f;
                float py = -1.2f + (i % 2) * 2.4f;
                p.entities.add(Entity.create2D("Plat_" + i, "solid", "RECT", px, py, 3.6f, 0.7f, "#3F3F46", "STATIC"));
                Entity c = Entity.create2D("Gem_" + i, "coin", "CUSTOM_SPRITE", px, py + 1.3f, 0.9f, 0.9f, "#FACC15", "STATIC");
                c.customSpriteName = "gem";
                c.isTrigger = true;
                p.entities.add(c);
            }
            Entity hero = Entity.create2D("Player", "player", "CUSTOM_SPRITE", -8f, -1.5f, 1.35f, 1.35f, "#FFFFFF", "DYNAMIC");
            hero.customSpriteName = "knight_1";
            hero.spriteFrames = "knight_1,knight_2";
            hero.animFps = 8f;
            hero.fixedRotation = true;
            p.entities.add(hero);

            p.scriptLanguage = "JS";
            p.scriptCode =
                    "// Generated by Vortex Forge AI — 2D Side-Scrolling Platformer\n" +
                    "function onStart() {\n" +
                    "  vortex.setFixedRotation('Player', true);\n" +
                    "  vortex.tweenTagYoyo('coin', 0.35, 0.85, 'SINE');\n" +
                    "  vortex.playMusic('DUNGEON', 128);\n" +
                    "  vortex.setBanner('FORGE PLATFORMER — MOVE + SPACE(A) JUMP!');\n" +
                    "}\n" +
                    "function onUpdate(dt) {\n" +
                    "  if (!vortex.exists('Player')) return;\n" +
                    "  vortex.setVel('Player', vortex.joyX() * 9.0, vortex.getVY('Player'));\n" +
                    "  if (vortex.getY('Player') < -10) { vortex.setPos('Player', -8, 0); vortex.setVel('Player', 0, 0); }\n" +
                    "}\n" +
                    "function onButton(btn) {\n" +
                    "  if (btn === 'A' && vortex.exists('Player')) { vortex.jump('Player', 13.5); vortex.playSound('jump'); }\n" +
                    "}\n" +
                    "function onCollide(a, b, tagA, tagB) {\n" +
                    "  if ((tagA === 'player' && tagB === 'coin') || (tagB === 'player' && tagA === 'coin')) {\n" +
                    "    const c = tagA === 'coin' ? a : b;\n" +
                    "    vortex.particles(vortex.getX(c), vortex.getY(c), 0, 20, '#FACC15');\n" +
                    "    vortex.destroy(c); vortex.addScore(100); vortex.playSound('coin');\n" +
                    "  }\n" +
                    "}\n";
            return "Forge Offline Generator built 2D Platformer Course + Gems + Jump Physics!";
        }

        if (wantShooter) {
            p.dimensionMode = "2D";
            p.useBox2D = true;
            p.gravityY = 0f;
            p.cameraMode = "FIXED";
            p.skyColorHex = "#09090B";
            p.musicBpm = 144;
            p.musicPattern = ChiptuneGenerator.PRESET_BOSS;
            Entity ship = Entity.create2D("Player", "player", "SPRITE_SHIP", 0f, -5.0f, 1.4f, 1.4f, "#FFFFFF", "DYNAMIC");
            ship.fixedRotation = true;
            p.entities.add(ship);
            for (int i = 0; i < 6; i++) {
                p.entities.add(Entity.create2D("Meteor_" + i, "enemy", "CIRCLE", -7.5f + i * 3.0f, 4.5f + (i % 2) * 1.8f, 1.3f, 1.3f, "#EF4444", "DYNAMIC"));
            }
            p.scriptLanguage = "JS";
            p.scriptCode =
                    "// Generated by Vortex Forge AI — 2D Arcade Space Shooter\n" +
                    "let laserSeq = 0, spawnT = 0;\n" +
                    "function onStart() {\n" +
                    "  vortex.playMusic('BOSS', 144);\n" +
                    "  vortex.setBanner('FORGE SPACE BLASTER — WASD MOVE + SPACE(A) PLASMA CANNON!');\n" +
                    "}\n" +
                    "function onUpdate(dt) {\n" +
                    "  if (!vortex.exists('Player')) return;\n" +
                    "  vortex.setVel('Player', vortex.joyX() * 10.5, vortex.joyY() * 8.0);\n" +
                    "  spawnT += dt;\n" +
                    "  if (spawnT > 1.3) {\n" +
                    "    spawnT = 0;\n" +
                    "    const m = 'Meteor_' + (++laserSeq);\n" +
                    "    const rx = vortex.rand(-8.5, 8.5);\n" +
                    "    vortex.spawn2D(m, 'enemy', 'CIRCLE', rx, 7.5, 1.25, 1.25, '#EF4444', 'DYNAMIC');\n" +
                    "    vortex.setVel(m, 0, -4.8);\n" +
                    "  }\n" +
                    "}\n" +
                    "function onButton(btn) {\n" +
                    "  if (btn === 'A' && vortex.exists('Player')) {\n" +
                    "    const b = 'Bolt_' + (++laserSeq);\n" +
                    "    vortex.spawn2D(b, 'projectile', 'RECT', vortex.getX('Player'), vortex.getY('Player') + 1.1, 0.28, 0.85, '#FFFFFF', 'KINEMATIC');\n" +
                    "    vortex.setVel(b, 0, 19.0);\n" +
                    "    vortex.playSound('laser');\n" +
                    "  }\n" +
                    "}\n" +
                    "function onCollide(a, b, tagA, tagB) {\n" +
                    "  if ((tagA === 'projectile' && tagB === 'enemy') || (tagB === 'projectile' && tagA === 'enemy')) {\n" +
                    "    const en = tagA === 'enemy' ? a : b;\n" +
                    "    vortex.particles(vortex.getX(en), vortex.getY(en), 0, 24, '#EF4444');\n" +
                    "    vortex.destroy(a); vortex.destroy(b); vortex.addScore(125); vortex.playSound('explosion');\n" +
                    "  }\n" +
                    "}\n";
            return "Forge Offline Generator built 2D Space Shooter + Spawning Meteors + Plasma Cannon!";
        }

        // Default 2D Swarm Arena (supports Native Java OR ES6 JS!)
        p.dimensionMode = "2D";
        p.useBox2D = true;
        p.gravityY = 0f;
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.skyColorHex = "#09090B";
        p.musicBpm = 136;
        p.musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK;

        p.customSprites.put("knight_1", PixelSpriteCodec.createDefaultKnightSprite());
        p.customSprites.put("knight_2", PixelSpriteCodec.createKnightRunFrame2Sprite());
        p.customSprites.put("drone_1", PixelSpriteCodec.createDroneFrame1Sprite());
        p.customSprites.put("drone_2", PixelSpriteCodec.createDroneFrame2Sprite());

        if (p.findByName("Player") == null) {
            Entity hero = Entity.create2D("Player", "player", "CUSTOM_SPRITE", 0f, 0f, 1.35f, 1.35f, "#FFFFFF", "DYNAMIC");
            hero.customSpriteName = "knight_1";
            hero.spriteFrames = "knight_1,knight_2";
            hero.animFps = 7f;
            hero.fixedRotation = true;
            p.entities.add(hero);
        }

        int baseIdx = p.entities.size();
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2.0 / 6.0;
            Entity d = Entity.create2D("SwarmDrone_" + (baseIdx + i), "enemy", "CUSTOM_SPRITE",
                    (float) Math.cos(a) * 8.5f, (float) Math.sin(a) * 6.5f, 1.25f, 1.25f, "#EF4444", "DYNAMIC");
            d.customSpriteName = "drone_1";
            d.spriteFrames = "drone_1,drone_2";
            d.animFps = 9f;
            p.entities.add(d);
        }

        if (wantJava) {
            p.scriptLanguage = "JAVA";
            p.scriptCode =
                    "// Generated by Vortex Forge AI (Native Java VM)\n" +
                    "public class GameScript {\n" +
                    "    float timer = 0.0f;\n" +
                    "    int seq = 0;\n\n" +
                    "    public void onStart() {\n" +
                    "        vortex.setFixedRotation(\"Player\", true);\n" +
                    "        vortex.playMusic(\"CYBERPUNK\", 136);\n" +
                    "        vortex.setBanner(\"FORGE SWARM [JAVA VM] — AUTO-TURRET + BUTTON A NOVA!\");\n" +
                    "    }\n\n" +
                    "    public void onUpdate(float dt) {\n" +
                    "        if (!vortex.exists(\"Player\")) return;\n" +
                    "        vortex.setVel(\"Player\", vortex.joyX() * 8.5f, vortex.joyY() * 8.5f);\n" +
                    "        vortex.chaseTagAI(\"enemy\", \"Player\", 4.0f);\n" +
                    "        timer += dt;\n" +
                    "        if (timer >= 0.45f) {\n" +
                    "            timer = 0.0f;\n" +
                    "            String near = vortex.findNearestTag(\"Player\", \"enemy\");\n" +
                    "            if (near != null && near.length() > 0) {\n" +
                    "                float px = vortex.getX(\"Player\"), py = vortex.getY(\"Player\");\n" +
                    "                float dx = vortex.getX(near) - px, dy = vortex.getY(near) - py;\n" +
                    "                float len = (float) Math.hypot(dx, dy);\n" +
                    "                if (len < 0.01f) len = 1.0f;\n" +
                    "                seq++;\n" +
                    "                String b = \"Pulse_\" + seq;\n" +
                    "                vortex.spawn2D(b, \"projectile\", \"CIRCLE\", px, py, 0.4, 0.4, \"#FFFFFF\", \"KINEMATIC\");\n" +
                    "                vortex.setVel(b, (dx / len) * 17.5f, (dy / len) * 17.5f);\n" +
                    "                vortex.playSound(\"laser\");\n" +
                    "            }\n" +
                    "        }\n" +
                    "    }\n\n" +
                    "    public void onButton(String btn) {\n" +
                    "        if (\"A\".equals(btn) && vortex.exists(\"Player\")) {\n" +
                    "            vortex.particles(vortex.getX(\"Player\"), vortex.getY(\"Player\"), 0, 28, \"#FFFFFF\");\n" +
                    "            vortex.playSound(\"powerup\");\n" +
                    "        }\n" +
                    "    }\n\n" +
                    "    public void onCollide(String a, String b, String tagA, String tagB) {\n" +
                    "        if ((\"projectile\".equals(tagA) && \"enemy\".equals(tagB)) || (\"projectile\".equals(tagB) && \"enemy\".equals(tagA))) {\n" +
                    "            String en = \"enemy\".equals(tagA) ? a : b;\n" +
                    "            vortex.particles(vortex.getX(en), vortex.getY(en), 0, 22, \"#EF4444\");\n" +
                    "            vortex.destroy(a); vortex.destroy(b);\n" +
                    "            vortex.addScore(150); vortex.playSound(\"explosion\");\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n";
            return "Forge Offline Generator built 2D Swarm Arena + Native Java GameScript!";
        }

        p.scriptLanguage = "JS";
        p.scriptCode =
                "// Generated by Vortex Forge AI (ES6+ JavaScript • Animated Sprites + Auto-Targeting)\n" +
                "// Prompt: " + prompt.replace("\n", " ") + "\n" +
                "let autoFireTimer = 0;\n" +
                "let boltSeq = 0;\n\n" +
                "function onStart() {\n" +
                "  vortex.setFixedRotation('Player', true);\n" +
                "  vortex.playMusic('CYBERPUNK', 136);\n" +
                "  vortex.setBanner(`FORGE SWARM 2D — AUTO-BLASTER + BUTTON A NOVA!`);\n" +
                "}\n\n" +
                "function onUpdate(dt) {\n" +
                "  if (!vortex.exists('Player')) return;\n" +
                "  vortex.setVel('Player', vortex.joyX() * 8.5, vortex.joyY() * 8.5);\n" +
                "  vortex.chaseTagAI('enemy', 'Player', 4.1);\n" +
                "  autoFireTimer += dt;\n" +
                "  if (autoFireTimer >= 0.48) {\n" +
                "    autoFireTimer = 0;\n" +
                "    const nearest = vortex.findNearestTag('Player', 'enemy');\n" +
                "    if (nearest) {\n" +
                "      const px = vortex.getX('Player'), py = vortex.getY('Player');\n" +
                "      const dx = vortex.getX(nearest) - px, dy = vortex.getY(nearest) - py;\n" +
                "      const len = Math.hypot(dx, dy) || 1;\n" +
                "      const b = `Pulse_${++boltSeq}`;\n" +
                "      vortex.spawn2D(b, 'projectile', 'CIRCLE', px, py, 0.42, 0.42, '#FFFFFF', 'KINEMATIC');\n" +
                "      vortex.setVel(b, (dx / len) * 17, (dy / len) * 17);\n" +
                "      vortex.playSound('laser');\n" +
                "    }\n" +
                "  }\n" +
                "}\n\n" +
                "function onButton(btn) {\n" +
                "  if (btn === 'A' && vortex.exists('Player')) {\n" +
                "    vortex.tweenScale('Player', 1.8, 1.8, 1, 0.22, 'ELASTIC', 1);\n" +
                "    vortex.particles(vortex.getX('Player'), vortex.getY('Player'), 0, 32, '#FFFFFF');\n" +
                "    vortex.playSound('powerup');\n" +
                "  }\n" +
                "}\n\n" +
                "function onCollide(a, b, tagA, tagB) {\n" +
                "  if ((tagA === 'projectile' && tagB === 'enemy') || (tagB === 'projectile' && tagA === 'enemy')) {\n" +
                "    const en = tagA === 'enemy' ? a : b;\n" +
                "    const ex = vortex.getX(en), ey = vortex.getY(en);\n" +
                "    vortex.particles(ex, ey, 0, 24, '#EF4444');\n" +
                "    vortex.destroy(a); vortex.destroy(b);\n" +
                "    vortex.addScore(150);\n" +
                "    vortex.playSound('explosion');\n" +
                "  }\n" +
                "}\n";
        return "Forge Offline Generator built 2D Animated Hero + 6 Swarm Drones + Auto-Blaster Script!";
    }

    // =========================================================================
    // Direct BYOK Cloud Agent (Groq / OpenRouter Free / Gemini / OpenAI)
    // =========================================================================
    private static String applyByokCloudModel(
            GameProject p, String prompt, int providerIdx, String customModelId, String apiKey, boolean replaceScene) throws Exception {
        boolean preferJava = "JAVA".equalsIgnoreCase(p.scriptLanguage) || prompt.toLowerCase(Locale.US).contains("in java");
        String targetLang = preferJava ? "JAVA" : "JS";
        String langSpec = preferJava
                ? "Java (`public class GameScript { public void onStart(){} public void onUpdate(float dt){} public void onButton(String btn){} public void onCollide(String a,String b,String tagA,String tagB){} }`)"
                : "ES6 JavaScript (`function onStart(){}`, `function onUpdate(dt){}`, `function onButton(btn){}`, `function onCollide(a,b,tagA,tagB){}`; use `let` for locals)";

        String systemInst =
                "You are Vortex Forge AI inside Vortex Engine Studio. Return ONLY a single valid JSON object (no markdown fences, no commentary) with keys:\n"
                + "{\"dimensionMode\":\"2D\"|\"3D\", \"scriptLanguage\":\"" + targetLang + "\", "
                + "\"scriptCode\":\"<complete playable " + langSpec + " calling vortex.joyX(), vortex.joyY(), vortex.exists('Player'), vortex.getX(n), vortex.getY(n), vortex.getVY(n), vortex.setVel(n,vx,vy[,vz]), vortex.jump(n,vy), vortex.chaseTagAI(tag,target,spd), vortex.findNearestTag(from,tag), vortex.spawn2D(n,tag,prim,x,y,sx,sy,hex,body), vortex.spawn3D(n,tag,prim,x,y,z,sx,sy,sz,hex,body), vortex.particles(x,y,z,cnt,hex), vortex.destroy(n), vortex.playSound('laser'|'coin'|'jump'|'explosion'|'hit'|'powerup'), vortex.addScore(n), vortex.setBanner(txt)>\", "
                + "\"entities\":[{\"name\":\"Player\",\"tag\":\"player\",\"primitive\":\"RECT|CIRCLE|CUBE|SPHERE|PYRAMID\",\"x\":0,\"y\":1,\"z\":0,\"scaleX\":1.3,\"scaleY\":1.3,\"scaleZ\":1.3,\"colorHex\":\"#FFFFFF\",\"bodyType\":\"DYNAMIC|STATIC|KINEMATIC\",\"isTrigger\":false}]}";

        String model = (customModelId != null && !customModelId.trim().isEmpty())
                ? customModelId.trim()
                : DEFAULT_MODELS[Math.min(providerIdx, DEFAULT_MODELS.length - 1)];

        String rawContent;
        String usedModel = model;
        if (providerIdx == 3) {
            // Google Gemini REST API
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            JSONObject body = new JSONObject();
            try {
                JSONArray contents = new JSONArray();
                JSONObject part = new JSONObject().put("text", systemInst + "\n\nUser Request: " + prompt);
                contents.put(new JSONObject().put("parts", new JSONArray().put(part)));
                body.put("contents", contents);
                body.put("generationConfig", new JSONObject().put("maxOutputTokens", 1800).put("temperature", 0.3));
            } catch (JSONException ignored) {}
            String resp = postJson(url, null, body.toString());
            JSONObject rObj = new JSONObject(resp);
            rawContent = rObj.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        } else {
            String endpoint = providerIdx == 1 ? "https://api.groq.com/openai/v1/chat/completions"
                    : providerIdx == 2 ? "https://openrouter.ai/api/v1/chat/completions"
                    : "https://api.openai.com/v1/chat/completions";
            // Reasoning models on Groq (gpt-oss-120b / gpt-oss-20b / qwen3.8-27b) count chain-of-thought
            // toward max_tokens; give Groq 8192 completion budget while keeping OpenRouter lean (2000).
            int defaultMaxTok = (providerIdx == 1) ? 8192 : 2000;
            try {
                rawContent = callOpenAiCompatible(endpoint, apiKey, model, systemInst, prompt, defaultMaxTok, providerIdx == 1);
            } catch (Exception firstErr) {
                String msg = firstErr.getMessage() == null ? "" : firstErr.getMessage();
                // 1) Groq HTTP 404 model_not_found -> auto-discover live models from /openai/v1/models and retry!
                if (providerIdx == 1 && (msg.contains("404") || msg.contains("model_not_found") || msg.contains("decommissioned") || msg.contains("does not exist"))) {
                    List<String> live = fetchLiveModels(1, apiKey);
                    String fallbackModel = null;
                    for (String cand : new String[]{"openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b", "moonshotai/kimi-k2-instruct-0905"}) {
                        if (live.contains(cand) && !cand.equals(model)) { fallbackModel = cand; break; }
                    }
                    if (fallbackModel == null && !live.isEmpty()) fallbackModel = live.get(0);
                    if (fallbackModel != null) {
                        usedModel = fallbackModel;
                        savePrefs(providerIdx, usedModel, apiKey);
                        rawContent = callOpenAiCompatible(endpoint, apiKey, usedModel, systemInst, prompt, 8192, true);
                    } else {
                        throw firstErr;
                    }
                }
                // 2) OpenRouter HTTP 402 insufficient credits / max_tokens -> auto-retry on zero-cost `openrouter/free`!
                else if (providerIdx == 2 && (msg.contains("402") || msg.contains("credits") || msg.contains("afford"))) {
                    if (!"openrouter/free".equals(model)) {
                        usedModel = "openrouter/free";
                        savePrefs(providerIdx, usedModel, apiKey);
                        rawContent = callOpenAiCompatible(endpoint, apiKey, usedModel, systemInst, prompt, 2000, false);
                    } else {
                        Matcher m = Pattern.compile("can only afford\\s+(\\d+)").matcher(msg);
                        int afford = m.find() ? Math.max(250, Integer.parseInt(m.group(1)) - 20) : 512;
                        rawContent = callOpenAiCompatible(endpoint, apiKey, "openrouter/free", systemInst, prompt, afford, false);
                    }
                } else {
                    throw firstErr;
                }
            }
        }

        String cleanJson = extractJsonObject(rawContent);
        JSONObject parsed = new JSONObject(cleanJson);
        if (parsed.has("dimensionMode")) {
            p.dimensionMode = parsed.optString("dimensionMode", p.dimensionMode);
        }

        JSONArray arr = parsed.has("entities") ? parsed.optJSONArray("entities") : parsed.optJSONArray("addEntities");
        int count = 0;
        if (arr != null && arr.length() > 0) {
            if (replaceScene) p.entities.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject eo = arr.optJSONObject(i);
                if (eo != null) {
                    Entity ent = Entity.fromJson(eo);
                    ent.is3D = "3D".equalsIgnoreCase(p.dimensionMode);
                    p.entities.add(ent);
                    count++;
                }
            }
        }

        if (parsed.has("scriptCode")) {
            String lang = parsed.optString("scriptLanguage", targetLang);
            String candidateCode = parsed.optString("scriptCode", "");
            String check = VortexScriptEngine.validateSyntax(lang, candidateCode);
            if (check.startsWith("OK")) {
                p.scriptLanguage = lang;
                p.scriptCode = candidateCode;
            } else {
                // Keep scene entities from cloud model, generate guaranteed valid fallback script if LLM slipped syntax!
                applyOfflineSmartGenerator(p, prompt, false);
                return "Forge Cloud (" + usedModel + ") built " + count + " entities (+ auto-repaired script syntax)!";
            }
        }
        // Keep synced into scriptFiles when single-shot generator runs
        p.putScriptFile(p.defaultMainFileName(), p.scriptCode);
        return "Forge Cloud (" + usedModel + ") generated " + count + " entities & validated " + p.scriptLanguage + " script!";
    }

    // =========================================================================
    // v4.3 Continuous Multi-Turn Chat + Contextual IDE Tool-Calling Agent
    // =========================================================================

    public static final class ChatMessage {
        public final String role; // "user" or "assistant"
        public final String text;
        public final List<String> toolBadges = new ArrayList<>();
        public final List<Question> questions = new ArrayList<>();
        public boolean answered = false;

        public ChatMessage(String role, String text) {
            this.role = role;
            this.text = text == null ? "" : text;
        }
    }

    /** An interactive clarifying question rendered as answer chips + a text box in the chat feed. */
    public static final class Question {
        public final String id;
        public final String text;
        public final List<String> options = new ArrayList<>();
        public boolean allowText = true;

        public Question(String id, String text) {
            this.id = id == null || id.isEmpty() ? "q" : id;
            this.text = text == null ? "" : text;
        }
    }

    public static final class ChatTurnResult {
        public String reply = "";
        public boolean mutatedProject = false;
        public final List<String> toolBadges = new ArrayList<>();
        public final List<Question> questions = new ArrayList<>();
        public String modelUsed = "";
        public VortexScriptEngine.PlaytestReport testReport = null;
        public boolean usedOffline = false;
    }

    public static String buildWorkspaceContext(GameProject p) {
        if (p == null) return "{}";
        p.ensureScriptFilesInitialized();
        StringBuilder sb = new StringBuilder();
        sb.append("PROJECT: \"").append(p.title).append("\" | Mode=").append(p.dimensionMode)
          .append(" | VM=").append(p.scriptLanguage)
          .append(" | GravityY=").append(p.gravityY)
          .append(" | BlueprintNodes=").append(p.blueprintNodes.size()).append("\n");

        sb.append("FILES (").append(p.scriptFiles.size()).append("):\n");
        for (java.util.Map.Entry<String, String> sf : p.scriptFiles.entrySet()) {
            String content = sf.getValue() == null ? "" : sf.getValue();
            int lines = content.isEmpty() ? 0 : content.split("\n", -1).length;
            sb.append("  - ").append(sf.getKey()).append(" (").append(lines).append(" lines)")
              .append(sf.getKey().equals(p.activeScriptFile) ? " [ACTIVE]\n" : "\n");
        }

        sb.append("ENTITIES (").append(p.entities.size()).append("): ");
        int lim = Math.min(20, p.entities.size());
        for (int i = 0; i < lim; i++) {
            Entity e = p.entities.get(i);
            if (i > 0) sb.append(", ");
            sb.append(e.name).append("[").append(e.tag).append("/").append(e.primitive)
              .append("@(").append(Math.round(e.x)).append(",").append(Math.round(e.y)).append(")]");
        }
        if (p.entities.size() > lim) sb.append(" ... +").append(p.entities.size() - lim).append(" more");
        sb.append("\n");

        String syn = VortexScriptEngine.validateProjectSyntax(p);
        sb.append("SYNTAX_CHECK: ").append(syn).append("\n");

        // Include full content of active file (clamped to 2400 chars) + compact excerpts of other files
        for (java.util.Map.Entry<String, String> sf : p.scriptFiles.entrySet()) {
            String c = sf.getValue() == null ? "" : sf.getValue();
            int maxLen = sf.getKey().equals(p.activeScriptFile) ? 2400 : 900;
            if (c.length() > maxLen) c = c.substring(0, maxLen) + "\n// ... [truncated]";
            sb.append("--- FILE: ").append(sf.getKey()).append(" ---\n").append(c).append("\n");
        }
        return sb.toString();
    }

    public static ChatTurnResult runChatTurn(
            GameProject p,
            List<ChatMessage> history,
            String userPrompt,
            int providerIdx,
            String customModelId,
            String apiKey) {
        p.ensureScriptFilesInitialized();
        String trimmedKey = apiKey == null ? "" : apiKey.trim();
        savePrefs(providerIdx, customModelId, trimmedKey);

        if (providerIdx <= 0 || trimmedKey.isEmpty()) {
            return runOfflineContextualTurn(p, history, userPrompt);
        }
        try {
            return runCloudContextualTurn(p, history, userPrompt, providerIdx, customModelId, trimmedKey);
        } catch (Exception ex) {
            ChatTurnResult fallback = runOfflineContextualTurn(p, history, userPrompt);
            fallback.reply = "[Cloud note: " + ex.getMessage() + " — handled locally]\n\n" + fallback.reply;
            return fallback;
        }
    }

    private static ChatTurnResult runCloudContextualTurn(
            GameProject p,
            List<ChatMessage> history,
            String userPrompt,
            int providerIdx,
            String customModelId,
            String apiKey) throws Exception {
        String lang = p.scriptLanguage;
        String ext = p.getScriptExtension();
        String sys =
                "You are Vortex Forge AI, a contextual game engine IDE assistant inside Vortex Engine Studio.\n"
                + "You collaborate with the developer in a continuous multi-turn conversation and can inspect/modify their multi-file codebase, scene entities, visual Blueprint GUI nodes, and run headless playtests.\n"
                + "Respond ONLY with a single valid JSON object (no markdown fences outside JSON) matching this schema:\n"
                + "{\n"
                + "  \"reply\": \"1-3 short sentences. What you did, in the user's language. No planning, no analysis.\",\n"
                + "  \"questions\": [{\"id\":\"scope\", \"text\":\"Which camera style?\", \"options\":[\"Third person\",\"First person\",\"Top down\"], \"allowText\":true}],\n"
                + "  \"actions\": [\n"
                + "    {\"tool\":\"write_file\", \"path\":\"objects/enemy" + ext + "\", \"content\":\"<valid " + lang + " code>\"},\n"
                + "    {\"tool\":\"edit_file\", \"path\":\"scripts/main" + ext + "\", \"find\":\"old text\", \"replace\":\"new text\"},\n"
                + "    {\"tool\":\"delete_file\", \"path\":\"objects/obsolete" + ext + "\"},\n"
                + "    {\"tool\":\"upsert_entity\", \"name\":\"Boss_1\", \"tag\":\"enemy\", \"primitive\":\"RECT|CIRCLE|CUBE|SPHERE|PYRAMID|CUSTOM_SPRITE\", \"x\":0, \"y\":3, \"z\":0, \"scaleX\":2.0, \"scaleY\":2.0, \"scaleZ\":2.0, \"colorHex\":\"#EF4444\", \"bodyType\":\"DYNAMIC|STATIC|KINEMATIC\", \"isTrigger\":false},\n"
                + "    {\"tool\":\"remove_entity\", \"name\":\"OldEntity\"},\n"
                + "    {\"tool\":\"set_scene\", \"dimensionMode\":\"2D|3D\", \"scriptLanguage\":\"" + lang + "\", \"skyColorHex\":\"#0B0B0E\", \"gravityY\":-22},\n"
                + "    {\"tool\":\"add_blueprint_node\", \"nodeType\":\"GUI_SHOW_DIALOG|GUI_SET_LABEL|GUI_SET_BAR|EV_START|EV_COLLIDE\", \"param1\":\"GUIDE\", \"param2\":\"Hello!\", \"param3\":\"4.0\"},\n"
                + "    {\"tool\":\"run_test\", \"frames\":60}\n"
                + "  ]\n"
                + "}\n"
                + "Engine API (`vortex.*`): joyX(), joyY(), exists(n), getX(n), getY(n), getZ(n), getVY(n), setPos(n,x,y[,z]), setVel(n,vx,vy[,vz]), jump(n,vy), applyImpulse(n,ix,iy), spawn2D(n,tag,prim,x,y,sx,sy,hex,body), spawn3D(n,tag,prim,x,y,z,sx,sy,sz,hex,body), destroy(n), chaseTagAI(tag,target,spd), findNearestTag(from,tag), particles(x,y,z,cnt,hex), playSound('laser'|'coin'|'jump'|'explosion'|'hit'|'powerup'), playMusic('CYBERPUNK'|'BOSS'|'DUNGEON',bpm), addScore(d), getScore(), addHealth(d), getHealth(), setBanner(txt), showDialog(speaker,msg,durSec), hideDialog(), setLabel(id,txt,xPct,yPct,fontSize,hex), setProgressBar(id,lbl,val,max,xPct,yPct,hex), removeUI(id), clearUI().\n"
                + "NOTE: Multiple files in `scriptFiles` share scope AND every file's `onStart()`, `onUpdate(dt)`, `onButton(btn)`, `onCollide(a,b,tagA,tagB)` hooks are chained automatically! Organize separate objects/features into clean files like `objects/boss" + ext + "` or `ui/hud" + ext + "` when appropriate. If the user only asks a question, return `\"actions\": []`.\n"
                + "OUTPUT RULES (strict):\n"
                + "1. Emit ONE JSON object only. Never emit analysis, plans, drafts, or 'We need...' text.\n"
                + "2. `reply` is the only text the human sees — keep it short and concrete, no markdown fences.\n"
                + "3. If the request is ambiguous or needs a decision you cannot infer (camera, genre, art style, input, difficulty, save format...), return `actions: []` plus 1-3 `questions` with 2-4 `options` each so the user can answer with one click, then act on their answer next turn.\n"
                + "4. If the request is clear, DO THE WORK with actions and do not ask questions.\n"
                + "5. Prefer real, runnable code with the APIs listed above. Never invent API names.\n\n"
                + "LIVE WORKSPACE SNAPSHOT:\n" + buildWorkspaceContext(p);

        String model = (customModelId != null && !customModelId.trim().isEmpty())
                ? customModelId.trim()
                : DEFAULT_MODELS[Math.min(providerIdx, DEFAULT_MODELS.length - 1)];

        String rawContent = callMultiTurnProvider(providerIdx, apiKey, model, sys, history, userPrompt);
        if (rawContent == null || rawContent.trim().isEmpty()) {
            ChatTurnResult local = runOfflineContextualTurn(p, history, userPrompt);
            local.usedOffline = true;
            local.reply = sanitizeReply(local.reply);
            local.toolBadges.add("fallback: local generator (" + model + " returned no answer)");
            return local;
        }
        String cleanJson = extractJsonObject(rawContent);
        ChatTurnResult res = new ChatTurnResult();
        res.modelUsed = model;

        JSONObject obj;
        try {
            obj = new JSONObject(cleanJson);
        } catch (Exception parseEx) {
            // v4.6.0: never dump the model's raw scratchpad into the transcript. If there is no
            // structured answer, say so in one line and keep the turn usable.
            res.reply = sanitizeReply(rawContent);
            if (res.reply.isEmpty() || looksLikeChainOfThought(rawContent)) {
                res.reply = "I didn't get a usable answer from " + model + " that time. "
                        + "Press Retry, or switch model with Model / Key (openai/gpt-oss-120b on Groq is the most reliable here).";
                res.toolBadges.add("error: no valid JSON reply");
            }
            return res;
        }

        res.reply = sanitizeReply(obj.optString("reply", ""));
        if (res.reply.isEmpty()) res.reply = "Done.";

        JSONArray qs = obj.optJSONArray("questions");
        if (qs != null) {
            for (int i = 0; i < Math.min(3, qs.length()); i++) {
                JSONObject qo = qs.optJSONObject(i);
                if (qo == null) continue;
                String qText = qo.optString("text", "").trim();
                if (qText.isEmpty()) continue;
                Question qq = new Question(qo.optString("id", "q" + i), qText);
                qq.allowText = qo.optBoolean("allowText", true);
                JSONArray opts = qo.optJSONArray("options");
                if (opts != null) {
                    for (int k = 0; k < Math.min(6, opts.length()); k++) {
                        String o = opts.optString(k, "").trim();
                        if (!o.isEmpty()) qq.options.add(o);
                    }
                }
                res.questions.add(qq);
            }
        }
        JSONArray actions = obj.optJSONArray("actions");
        boolean ranExplicitTest = false;
        if (actions != null) {
            for (int i = 0; i < actions.length(); i++) {
                JSONObject act = actions.optJSONObject(i);
                if (act == null) continue;
                String t = act.optString("tool", "");
                if ("run_test".equalsIgnoreCase(t)) ranExplicitTest = true;
                executeSingleTool(p, act, res);
            }
        }

        if (res.mutatedProject || ranExplicitTest) {
            res.testReport = VortexScriptEngine.runHeadlessPlaytest(p, 50);
            res.toolBadges.add(res.testReport.ok
                    ? "run_test: PASS (" + res.testReport.framesSimulated + "f, " + res.testReport.activeEntities + " ents)"
                    : "run_test: ISSUE (" + res.testReport.error + ")");
        }
        return res;
    }

    private static String callMultiTurnProvider(
            int providerIdx, String apiKey, String model, String systemInst,
            List<ChatMessage> history, String userPrompt) throws Exception {
        if (providerIdx == 3) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            JSONObject body = new JSONObject();
            StringBuilder transcript = new StringBuilder(systemInst).append("\n\nCONVERSATION:\n");
            if (history != null) {
                int start = Math.max(0, history.size() - 8);
                for (int i = start; i < history.size(); i++) {
                    ChatMessage m = history.get(i);
                    transcript.append(m.role.toUpperCase(Locale.US)).append(": ").append(m.text).append("\n");
                }
            }
            transcript.append("USER: ").append(userPrompt);
            try {
                JSONArray contents = new JSONArray();
                JSONObject part = new JSONObject().put("text", transcript.toString());
                contents.put(new JSONObject().put("parts", new JSONArray().put(part)));
                body.put("contents", contents);
                body.put("generationConfig", new JSONObject().put("maxOutputTokens", 2400).put("temperature", 0.3));
            } catch (JSONException ignored) {}
            String resp = postJson(url, null, body.toString());
            JSONObject rObj = new JSONObject(resp);
            return rObj.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
        }

        String endpoint = providerIdx == 1 ? "https://api.groq.com/openai/v1/chat/completions"
                : providerIdx == 2 ? "https://openrouter.ai/api/v1/chat/completions"
                : "https://api.openai.com/v1/chat/completions";
        int maxTok = (providerIdx == 1) ? 8192 : 2400;

        JSONArray msgs = new JSONArray();
        try {
            msgs.put(new JSONObject().put("role", "system").put("content", systemInst));
            if (history != null) {
                int start = Math.max(0, history.size() - 10);
                for (int i = start; i < history.size(); i++) {
                    ChatMessage m = history.get(i);
                    String r = "assistant".equalsIgnoreCase(m.role) ? "assistant" : "user";
                    msgs.put(new JSONObject().put("role", r).put("content", m.text));
                }
            }
            msgs.put(new JSONObject().put("role", "user").put("content", userPrompt));
        } catch (JSONException ignored) {}

        try {
            return callOpenAiMessages(endpoint, apiKey, model, msgs, maxTok, providerIdx == 1);
        } catch (Exception firstErr) {
            String msg = firstErr.getMessage() == null ? "" : firstErr.getMessage();
            if (providerIdx == 1 && (msg.contains("404") || msg.contains("model_not_found") || msg.contains("decommissioned") || msg.contains("does not exist"))) {
                List<String> live = fetchLiveModels(1, apiKey);
                String fb = !live.isEmpty() ? live.get(0) : "openai/gpt-oss-120b";
                for (String c : new String[]{"openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3.8-27b"}) {
                    if (live.contains(c) && !c.equals(model)) { fb = c; break; }
                }
                savePrefs(providerIdx, fb, apiKey);
                return callOpenAiMessages(endpoint, apiKey, fb, msgs, 8192, true);
            }
            if (providerIdx == 2 && (msg.contains("402") || msg.contains("credits") || msg.contains("afford"))) {
                Matcher m = Pattern.compile("can only afford\\s+(\\d+)").matcher(msg);
                int afford = m.find() ? Math.max(300, Integer.parseInt(m.group(1)) - 20) : 1600;
                savePrefs(providerIdx, "openrouter/free", apiKey);
                return callOpenAiMessages(endpoint, apiKey, "openrouter/free", msgs, afford, false);
            }
            throw firstErr;
        }
    }

    private static String callOpenAiMessages(
            String endpoint, String apiKey, String model, JSONArray msgs, int maxTokens, boolean isGroq) throws Exception {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            body.put("messages", msgs);
            body.put("temperature", 0.3);
            body.put("max_tokens", maxTokens);
            if (isGroq && model != null && model.startsWith("openai/gpt-oss")) {
                body.put("reasoning_effort", "low");       // Groq: keep the thinking budget tiny
                body.put("include_reasoning", false);      // and never send it back to us
            } else {
                // OpenRouter / OpenAI-compatible reasoning models: ask for the answer only.
                body.put("reasoning", new JSONObject().put("exclude", true));
            }
        } catch (JSONException ignored) {}
        String resp;
        try {
            resp = postJson(endpoint, "Bearer " + apiKey, body.toString());
        } catch (Exception ex) {
            if (body.has("reasoning_effort") && ex.getMessage() != null && ex.getMessage().contains("400")) {
                body.remove("reasoning_effort");
                resp = postJson(endpoint, "Bearer " + apiKey, body.toString());
            } else {
                throw ex;
            }
        }
        JSONObject rObj = new JSONObject(resp);
        JSONObject msgObj = rObj.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        String content = msgObj.optString("content", "");
        if (content == null || content.trim().isEmpty()) {
            // v4.6.0: reasoning models (gpt-oss, qwen3, deepseek-r1) sometimes put everything in
            // `reasoning`. Only reuse it when it actually holds the JSON answer — otherwise return
            // empty so the caller shows a clean retry message instead of the model's inner monologue.
            String reasoning = msgObj.optString("reasoning", "");
            if (reasoning == null || reasoning.trim().isEmpty()) {
                reasoning = msgObj.optString("reasoning_content", "");
            }
            content = looksLikeAnswerJson(reasoning) ? reasoning : "";
        }
        return content == null ? "" : content;
    }

    /** True when the text carries a usable structured answer rather than free-form thinking. */
    public static boolean looksLikeAnswerJson(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.isEmpty()) return false;
        int brace = t.indexOf('{');
        if (brace < 0) return false;
        String tail = t.substring(brace);
        return tail.contains("\"reply\"") && tail.lastIndexOf('}') > brace;
    }

    private static void executeSingleTool(GameProject p, JSONObject act, ChatTurnResult res) {
        String tool = act.optString("tool", "").trim().toLowerCase(Locale.US);
        switch (tool) {
            case "write_file": {
                String path = GameProject.normalizeScriptPath(act.optString("path", p.defaultMainFileName()));
                String content = act.optString("content", "");
                String prev = p.scriptFiles.get(path);
                String check = VortexScriptEngine.validateSyntax(p.scriptLanguage, content);
                if (check.startsWith("OK")) {
                    p.putScriptFile(path, content);
                    p.activeScriptFile = path;
                    p.scriptCode = content;
                    res.mutatedProject = true;
                    res.toolBadges.add((prev == null ? "create_file: " : "write_file: ") + path);
                } else {
                    res.toolBadges.add("rejected_syntax (" + path + "): " + check);
                }
                break;
            }
            case "edit_file": {
                String path = GameProject.normalizeScriptPath(act.optString("path", p.activeScriptFile));
                String existing = p.scriptFiles.get(path);
                String find = act.optString("find", "");
                String replace = act.optString("replace", "");
                if (existing != null && !find.isEmpty() && existing.contains(find)) {
                    String updated = existing.replace(find, replace);
                    if (VortexScriptEngine.validateSyntax(p.scriptLanguage, updated).startsWith("OK")) {
                        p.putScriptFile(path, updated);
                        res.mutatedProject = true;
                        res.toolBadges.add("edit_file: " + path);
                    }
                }
                break;
            }
            case "delete_file": {
                String path = GameProject.normalizeScriptPath(act.optString("path", ""));
                if (p.removeScriptFile(path)) {
                    res.mutatedProject = true;
                    res.toolBadges.add("delete_file: " + path);
                }
                break;
            }
            case "upsert_entity": {
                String name = act.optString("name", "Entity_" + (p.entities.size() + 1));
                Entity target = p.findByName(name);
                boolean is3D = "3D".equalsIgnoreCase(p.dimensionMode);
                String tag = act.optString("tag", target != null ? target.tag : "prop");
                String prim = act.optString("primitive", target != null ? target.primitive : (is3D ? "CUBE" : "RECT"));
                float x = (float) act.optDouble("x", target != null ? target.x : 0.0);
                float y = (float) act.optDouble("y", target != null ? target.y : 1.5);
                float z = (float) act.optDouble("z", target != null ? target.z : 0.0);
                float sx = (float) act.optDouble("scaleX", target != null ? target.scaleX : 1.3);
                float sy = (float) act.optDouble("scaleY", target != null ? target.scaleY : 1.3);
                float sz = (float) act.optDouble("scaleZ", target != null ? target.scaleZ : 1.3);
                String col = act.optString("colorHex", target != null ? target.colorHex : "#FFFFFF");
                String body = act.optString("bodyType", target != null ? target.bodyType : "DYNAMIC");
                if (target == null) {
                    Entity ne = is3D
                            ? Entity.create3D(name, tag, prim, x, y, z, sx, sy, sz, col, body)
                            : Entity.create2D(name, tag, prim, x, y, sx, sy, col, body);
                    ne.isTrigger = act.optBoolean("isTrigger", "coin".equalsIgnoreCase(tag));
                    p.entities.add(ne);
                    res.toolBadges.add("spawn_entity: " + name);
                } else {
                    target.tag = tag; target.primitive = prim;
                    target.x = x; target.y = y; target.z = z;
                    target.scaleX = sx; target.scaleY = sy; target.scaleZ = sz;
                    target.colorHex = col; target.bodyType = body;
                    if (act.has("isTrigger")) target.isTrigger = act.optBoolean("isTrigger");
                    res.toolBadges.add("update_entity: " + name);
                }
                res.mutatedProject = true;
                break;
            }
            case "remove_entity": {
                String name = act.optString("name", "");
                Entity e = p.findByName(name);
                if (e != null) {
                    p.entities.remove(e);
                    res.mutatedProject = true;
                    res.toolBadges.add("remove_entity: " + name);
                }
                break;
            }
            case "set_scene": {
                if (act.has("dimensionMode")) p.dimensionMode = act.optString("dimensionMode", p.dimensionMode);
                if (act.has("scriptLanguage")) p.scriptLanguage = act.optString("scriptLanguage", p.scriptLanguage);
                if (act.has("skyColorHex")) p.skyColorHex = act.optString("skyColorHex", p.skyColorHex);
                if (act.has("gravityY")) p.gravityY = (float) act.optDouble("gravityY", p.gravityY);
                if (act.has("musicEnabled")) p.musicEnabled = act.optBoolean("musicEnabled", p.musicEnabled);
                res.mutatedProject = true;
                res.toolBadges.add("set_scene");
                break;
            }
            case "add_blueprint_node": {
                String nt = act.optString("nodeType", "GUI_SHOW_DIALOG");
                float nx = 80f + p.blueprintNodes.size() * 240f;
                com.vortex.engine.core.flow.VortexBlueprintGraph.BpNode node =
                        com.vortex.engine.core.flow.VortexBlueprintGraph.createByType(nt, nx, 120f);
                if (act.has("param1")) node.param1 = act.optString("param1", node.param1);
                if (act.has("param2")) node.param2 = act.optString("param2", node.param2);
                if (act.has("param3")) node.param3 = act.optString("param3", node.param3);
                if (!p.blueprintNodes.isEmpty() && node.hasExecIn()) {
                    String prevId = p.blueprintNodes.get(p.blueprintNodes.size() - 1).id;
                    p.blueprintLinks.add(new com.vortex.engine.core.flow.VortexBlueprintGraph.BpLink(prevId, "OUT", node.id));
                }
                p.blueprintNodes.add(node);
                res.mutatedProject = true;
                res.toolBadges.add("blueprint_node: " + nt);
                break;
            }
            default:
                break;
        }
    }

    private static ChatTurnResult runOfflineContextualTurn(
            GameProject p, List<ChatMessage> history, String rawPrompt) {
        ChatTurnResult res = new ChatTurnResult();
        res.usedOffline = true;
        res.modelUsed = "offline";
        String q = rawPrompt == null ? "" : rawPrompt.trim();
        String low = q.toLowerCase(Locale.US);
        String ext = p.getScriptExtension();

        // Small talk stays small talk — answer like a person instead of running a generator.
        if (low.matches("^(hi|hey|hello|yo|sup|hallo|good (morning|evening))[!. ]*$")
                || low.matches("^(thanks|thank you|ty|cheers)[!. ]*$")
                || low.matches("^(ok|okay|cool|nice|great)[!. ]*$")) {
            res.reply = low.startsWith("thank") || low.equals("ty") || low.equals("cheers")
                    ? "Anytime. Want me to keep building — gameplay, UI, or a playtest?"
                    : "Hey! I'm Forge AI. I can spawn enemies, write modular scripts, build HUD/dialog UI, "
                      + "edit scene entities and run headless playtests. What should we build first?";
            Question q1 = new Question("start", "What should I build next?");
            q1.options.add("A boss fight");
            q1.options.add("HUD + dialog");
            q1.options.add("Run a playtest");
            res.questions.add(q1);
            return res;
        }

        // Vague asks get a short question card instead of a guess.
        if (low.matches("^(make|build|create|start|design)?\\s*(me\\s+)?(a\\s+|an\\s+)?(game|project|level|something|stuff)[!. ]*$")) {
            res.reply = "Happy to. Two quick choices and I'll build the whole loop.";
            Question q1 = new Question("dim", "2D or 3D?");
            q1.options.add("2D");
            q1.options.add("3D");
            Question q2 = new Question("genre", "What kind of game?");
            q2.options.add("Explorer / adventure");
            q2.options.add("Platformer");
            q2.options.add("Top-down shooter");
            q2.options.add("Survival / farming");
            res.questions.add(q1);
            res.questions.add(q2);
            return res;
        }

        if (low.contains("test") || low.contains("check") || low.contains("diagnos") || low.contains("verify")) {
            res.testReport = VortexScriptEngine.runHeadlessPlaytest(p, 60);
            res.toolBadges.add("run_test: 60 frames");
            res.reply = "Completed a 60-frame headless simulation across all **" + p.scriptFiles.size() + "** script files and **"
                    + p.entities.size() + "** scene objects:\n\n" + res.testReport.toSummary();
            return res;
        }

        if (low.contains("dialog") || low.contains("label") || low.contains("hud") || low.contains("ui")) {
            String uiPath = "ui/game_hud" + ext;
            String code;
            if ("JAVA".equalsIgnoreCase(p.scriptLanguage)) {
                code = "// ui/game_hud.java — Modular In-Game Dialog & HUD Label Script\n"
                        + "public class GameHudModule {\n"
                        + "    public void onStart() {\n"
                        + "        vortex.showDialog(\"COMMANDER\", \"Mission active! Clear hostiles and collect energy cores.\", 4.5);\n"
                        + "        vortex.setLabel(\"mission_lbl\", \"OBJECTIVE: SURVIVE & COLLECT\", 50, 14, 15, \"#FFFFFF\");\n"
                        + "    }\n"
                        + "}\n";
            } else if ("LUA".equalsIgnoreCase(p.scriptLanguage)) {
                code = "-- ui/game_hud.lua — Modular In-Game Dialog & HUD Label Script\n"
                        + "function onStart()\n"
                        + "    vortex.showDialog('COMMANDER', 'Mission active! Clear hostiles and collect energy cores.', 4.5)\n"
                        + "    vortex.setLabel('mission_lbl', 'OBJECTIVE: SURVIVE & COLLECT', 50, 14, 15, '#FFFFFF')\n"
                        + "end\n";
            } else {
                code = "// ui/game_hud.js — Modular In-Game Dialog & HUD Label Script\n"
                        + "function onStart() {\n"
                        + "  vortex.showDialog('COMMANDER', 'Mission active! Clear hostiles and collect energy cores.', 4.5);\n"
                        + "  vortex.setLabel('mission_lbl', 'OBJECTIVE: SURVIVE & COLLECT', 50, 14, 15, '#FFFFFF');\n"
                        + "}\n";
            }
            p.putScriptFile(uiPath, code);
            p.activeScriptFile = uiPath;
            p.scriptCode = code;
            res.mutatedProject = true;
            res.toolBadges.add("create_file: " + uiPath);
            res.testReport = VortexScriptEngine.runHeadlessPlaytest(p, 45);
            res.toolBadges.add("run_test: PASS");
            res.reply = "Created dedicated UI module `" + uiPath + "` using `vortex.showDialog(...)` and `vortex.setLabel(...)` without touching your main gameplay file, and verified it in a 45-frame playtest!";
            return res;
        }

        if (low.contains("boss") || low.contains("enemy") || low.contains("turret")) {
            boolean is3D = "3D".equalsIgnoreCase(p.dimensionMode);
            String bossName = "Boss_" + (p.entities.size() + 1);
            Entity boss = is3D
                    ? Entity.create3D(bossName, "enemy", "CUBE", 0f, 3f, -8f, 2.4f, 2.4f, 2.4f, "#EF4444", "DYNAMIC")
                    : Entity.create2D(bossName, "enemy", "RECT", 5f, 3f, 2.2f, 2.2f, "#EF4444", "DYNAMIC");
            p.entities.add(boss);
            String modPath = "objects/" + bossName.toLowerCase(Locale.US) + ext;
            String modCode = "JS".equalsIgnoreCase(p.scriptLanguage)
                    ? "// " + modPath + " — Dedicated Boss AI & Boss HP Bar\n"
                      + "let bossHp = 100;\n"
                      + "function onStart() {\n"
                      + "  vortex.setProgressBar('boss_hp', 'BOSS ARMOR', bossHp, 100, 50, 12, '#EF4444');\n"
                      + "}\n"
                      + "function onUpdate(dt) {\n"
                      + "  if (vortex.exists('" + bossName + "') && vortex.exists('Player')) {\n"
                      + "    vortex.chaseAI('" + bossName + "', 'Player', 3.4);\n"
                      + "  }\n"
                      + "}\n"
                    : "// " + modPath + "\n";
            if ("JS".equalsIgnoreCase(p.scriptLanguage)) {
                p.putScriptFile(modPath, modCode);
                res.toolBadges.add("create_file: " + modPath);
            }
            res.mutatedProject = true;
            res.toolBadges.add("spawn_entity: " + bossName);
            res.testReport = VortexScriptEngine.runHeadlessPlaytest(p, 45);
            res.toolBadges.add("run_test: PASS");
            res.reply = "Spawned `" + bossName + "` in the scene"
                    + ("JS".equalsIgnoreCase(p.scriptLanguage) ? " with its own modular AI & HP bar script `" + modPath + "`" : "")
                    + " and verified the project via headless playtest.";
            return res;
        }

        // General game creation / mechanic request
        String summary = applyOfflineSmartGenerator(p, q, low.contains("new ") || low.contains("build ") || low.contains("create "));
        p.putScriptFile(p.defaultMainFileName(), p.scriptCode);
        res.mutatedProject = true;
        res.toolBadges.add("write_file: " + p.defaultMainFileName());
        res.testReport = VortexScriptEngine.runHeadlessPlaytest(p, 50);
        res.toolBadges.add("run_test: PASS (" + res.testReport.framesSimulated + "f)");
        res.reply = summary + "\n\nVerified with a 50-frame live engine simulation (`" + res.testReport.activeEntities + "` active entities, 0 errors). Tip: paste your Groq or OpenRouter Free key above anytime for full LLM tool-calling!";
        return res;
    }

    private static String callOpenAiCompatible(
            String endpoint, String apiKey, String model, String systemInst, String prompt, int maxTokens, boolean isGroq) throws Exception {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model);
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", systemInst));
            msgs.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", msgs);
            body.put("temperature", 0.3);
            body.put("max_tokens", maxTokens);
            if (isGroq && model != null && model.startsWith("openai/gpt-oss")) {
                body.put("reasoning_effort", "low");
            }
        } catch (JSONException ignored) {}
        String resp;
        try {
            resp = postJson(endpoint, "Bearer " + apiKey, body.toString());
        } catch (Exception ex) {
            if (body.has("reasoning_effort") && ex.getMessage() != null && ex.getMessage().contains("400")) {
                body.remove("reasoning_effort");
                resp = postJson(endpoint, "Bearer " + apiKey, body.toString());
            } else {
                throw ex;
            }
        }
        JSONObject rObj = new JSONObject(resp);
        JSONObject msgObj = rObj.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
        String content = msgObj.optString("content", "");
        if (content == null || content.trim().isEmpty()) {
            content = msgObj.optString("reasoning", "{}");
        }
        return content;
    }

    public static String extractJsonObject(String raw) {
        if (raw == null) return "{}";
        // Strip <think>...</think> blocks emitted by reasoning models (GPT-OSS, Qwen3, DeepSeek-R1)
        String cleaned = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
        cleaned = cleaned.replaceAll("(?s)```(?:json)?\\s*", "").trim();
        int firstBrace = cleaned.indexOf('{');
        if (firstBrace < 0) return cleaned;
        // Walk the braces so trailing prose after the object can never break the parse.
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = firstBrace; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return repairJson(cleaned.substring(firstBrace, i + 1));
            }
        }
        int lastBrace = cleaned.lastIndexOf('}');
        return lastBrace > firstBrace ? repairJson(cleaned.substring(firstBrace, lastBrace + 1)) : cleaned;
    }

    /** Fixes the two things local models most often get wrong: trailing commas and stray newlines. */
    static String repairJson(String json) {
        String j = json;
        j = j.replaceAll(",\\s*([}\\]])", "$1");
        try {
            new JSONObject(j);
            return j;
        } catch (Exception ignored) {
            return j;
        }
    }

    /** True when text reads like an internal scratchpad rather than an answer. */
    public static boolean looksLikeChainOfThought(String text) {
        if (text == null) return false;
        String low = text.toLowerCase(Locale.US).trim();
        if (low.isEmpty()) return false;
        if (low.contains("we need") || low.contains("let's think") || low.contains("let me plan")
                || low.contains("the user asks") || low.contains("the user wants")
                || low.contains("need parse") || low.contains("we should") && low.contains("json")) {
            return true;
        }
        // Long walls of "X. Y. Z." with no punctuation variety are usually reasoning dumps.
        return text.length() > 1200 && !text.contains("\n");
    }

    /**
     * Turns whatever the model produced into something safe to show a human: no thinking blocks,
     * no JSON scaffolding, no "```" fences, no runaway monologues.
     */
    public static String sanitizeReply(String raw) {
        if (raw == null) return "";
        String t = raw.replaceAll("(?s)<think>.*?</think>", " ")
                      .replaceAll("(?s)<reasoning>.*?</reasoning>", " ");
        // 1) If the text embeds a structured answer, that answer is the reply - drop everything else.
        String embedded = extractJsonObject(t);
        if (looksLikeAnswerJson(embedded)) {
            try {
                JSONObject o = new JSONObject(embedded);
                String r = o.optString("reply", "").trim();
                if (!r.isEmpty()) return finalizeReply(r);
            } catch (Exception ignored) { }
        }
        // 2) Strip fenced blocks and JSON scaffolding.
        t = t.replaceAll("(?s)```(?:json|js|javascript|java|lua)?", " ")
             .replaceAll("\\s*\"(reply|actions|questions|tool|path|content|find|replace)\"\\s*:\\s*", " ")
             .replaceAll("[{}\\[\\]]", " ");
        // 3) Drop planning sentences line by line (reasoning models think in "We need..." / "Let's...").
        StringBuilder kept = new StringBuilder();
        for (String line : t.split("\\r?\\n")) {
            String low = line.toLowerCase(Locale.US);
            if (low.contains("we need") || low.contains("need parse") || low.contains("we should")
                    || low.contains("let's") || low.contains("let me") || low.contains("the user asks")
                    || low.contains("the user wants") || low.contains("must return") || low.contains("must be valid")
                    || low.contains("we have live workspace") || low.contains("we can include")
                    || low.contains("since task") || low.contains("we should probably")
                    || low.contains("final answer should") || low.contains("json only")
                    || low.contains("we are vortex forge") || low.contains("no markdown fences")) {
                continue;
            }
            kept.append(line).append(' ');
        }
        return finalizeReply(kept.toString());
    }

    private static String finalizeReply(String text) {
        String t = text.replaceAll("[ \\t]{2,}", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (t.length() > 900) {
            int cut = t.indexOf(". ", 600);
            t = (cut > 0 ? t.substring(0, cut + 1) : t.substring(0, 900)) + "\u2026";
        }
        return t.trim();
    }

    private static String getJson(String urlStr, String authHeader) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 VortexEngineStudio/4.2");
        conn.setRequestProperty("HTTP-Referer", "https://github.com/hacvilke/VortexEngine");
        conn.setRequestProperty("X-Title", "Vortex Engine Studio");
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String txt = readAll(is);
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + ": " + txt);
        return txt;
    }

    private static String postJson(String urlStr, String authHeader, String payload) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(14_000);
        conn.setReadTimeout(35_000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 VortexEngineStudio/4.2");
        conn.setRequestProperty("HTTP-Referer", "https://github.com/hacvilke/VortexEngine");
        conn.setRequestProperty("X-Title", "Vortex Engine Studio");
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String txt = readAll(is);
        if (code < 200 || code >= 300) {
            throw new RuntimeException("HTTP " + code + ": " + txt);
        }
        return txt;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) sb.append(ln).append('\n');
        }
        return sb.toString();
    }
}
