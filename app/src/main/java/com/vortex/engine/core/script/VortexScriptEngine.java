package com.vortex.engine.core.script;

import com.vortex.engine.core.ai.VortexAIEngine;
import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.math.Vec3;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.noise.FastNoiseLite;
import com.vortex.engine.core.noise.TerrainGenerator;
import com.vortex.engine.core.physics.PhysicsWorld;
import com.vortex.engine.core.tween.VortexTweenSystem;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Unified Dual-Language Game Scripting Engine (100% Pure Java — runs on Android, Termux APKs & Desktop PC):
 *  1) Lua 5.2 VM powered by Open-Source LuaJ (org.luaj.vm2)
 *  2) VortexScript (.vxs) built-in AST interpreter
 *  + Direct bindings to JBox2D Rigid-Body Physics & FastNoiseLite Procedural Noise!
 */
public class VortexScriptEngine {

    public interface HostBridge {
        void onLog(String message);
        void onCameraShake(float intensity, float duration);
        void onEmitParticles(float x, float y, float z, int count, String colorHex);
        void onPlaySound(String soundName);
        default void onPlayMusic(String pattern, int bpm) {}
        default void onStopMusic() {}
    }

    // Shared Input & HUD State
    public volatile float inputJoyX = 0f;
    public volatile float inputJoyY = 0f;
    public volatile boolean inputBtnA = false;
    public volatile boolean inputBtnB = false;
    public volatile boolean touchActive = false;
    public volatile float touchWorldX = 0f;
    public volatile float touchWorldY = 0f;

    public volatile int score = 0;
    public volatile int health = 100;
    public volatile String hudBanner = "";
    public volatile float elapsedTime = 0f;

    private GameProject project;
    private PhysicsWorld physicsWorld;
    private HostBridge hostBridge;
    public final VortexTweenSystem tweenSystem = new VortexTweenSystem();
    public final VortexAIEngine aiEngine = new VortexAIEngine();
    private FastNoiseLite fastNoise = TerrainGenerator.createConfiguredNoise(1337, 0.065f);
    private final Random rng = new Random();

    // Active VM mode
    private boolean usingLua = false;
    private Globals luaGlobals;

    private final Map<String, Object> globals = new HashMap<>();
    private final Map<String, FnDecl> functions = new HashMap<>();
    private boolean compiledOk = false;
    private String lastError = null;
    private int instructionBudget = 0;
    private static final int MAX_INSTRUCTIONS_PER_CALL = 30000;

    public void attach(GameProject project, PhysicsWorld physicsWorld, HostBridge hostBridge) {
        this.project = project;
        this.physicsWorld = physicsWorld;
        this.hostBridge = hostBridge;
        this.tweenSystem.reset();
        this.aiEngine.reset();
        if (project != null) {
            this.fastNoise = TerrainGenerator.createConfiguredNoise(project.terrainSeed, project.terrainFreq);
        }
    }

    public static boolean detectLuaMode(String langSetting, String code) {
        if ("LUA".equalsIgnoreCase(langSetting)) {
            if (code != null && code.contains("fn on") && !code.contains("function ")) {
                return false;
            }
            return true;
        }
        if (code != null) {
            String trimmed = code.trim();
            if (trimmed.startsWith("--") || code.contains("function onStart") || code.contains("function onUpdate")) {
                return true;
            }
        }
        return false;
    }

    public static String validateSyntax(String langSetting, String code) {
        boolean isLua = detectLuaMode(langSetting, code);
        if (isLua) {
            try {
                Globals g = JsePlatform.standardGlobals();
                g.load(code == null ? "" : code, "main.lua");
                return "OK (Lua 5.2)";
            } catch (Exception ex) {
                return "Lua Syntax Error: " + ex.getMessage();
            }
        }
        String vxs = validateVxsSyntax(code);
        return "OK".equals(vxs) ? "OK (VortexScript)" : vxs;
    }

    public static String validateSyntax(String code) {
        return validateSyntax("VXS", code);
    }

    public synchronized boolean compileAndInit(String sourceCode) {
        globals.clear();
        functions.clear();
        luaGlobals = null;
        score = 0;
        health = 100;
        hudBanner = "";
        elapsedTime = 0f;
        lastError = null;
        compiledOk = false;
        tweenSystem.reset();
        aiEngine.reset();

        if (project != null && project.musicEnabled && hostBridge != null) {
            hostBridge.onPlayMusic(project.musicPattern, project.musicBpm);
        }

        String lang = project != null ? project.scriptLanguage : "VXS";
        usingLua = detectLuaMode(lang, sourceCode);

        if (usingLua) {
            try {
                luaGlobals = JsePlatform.standardGlobals();
                registerLuaEngineApi(luaGlobals);
                LuaValue chunk = luaGlobals.load(sourceCode == null ? "" : sourceCode, "main.lua");
                chunk.call();
                compiledOk = true;
                callHook("onStart");
                return true;
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log("[Lua Error] " + lastError);
                return false;
            }
        } else {
            try {
                List<Token> toks = tokenize(sourceCode == null ? "" : sourceCode);
                Parser parser = new Parser(toks);
                parser.parseProgram();
                functions.putAll(parser.fns);

                instructionBudget = MAX_INSTRUCTIONS_PER_CALL;
                for (Stmt s : parser.topLevelStmts) {
                    execStmt(s, globals, true);
                }
                compiledOk = true;
                callHook("onStart");
                return true;
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log("[Script Error] " + lastError);
                return false;
            }
        }
    }

    private void registerLuaEngineApi(Globals g) {
        LuaTable vortexTbl = new LuaTable();
        String[] apiNames = new String[]{
                "joyX", "inputX", "joyY", "inputY", "btnA", "btnB", "touchDown", "touchX", "touchY",
                "exists", "getX", "getY", "getZ", "getVX", "getVY", "getVZ", "getRotY", "getRotZ", "getTag",
                "isGrounded", "dist", "distance", "countByTag",
                "setPos", "move", "setVel", "addVel", "applyImpulse", "setAngularVel", "setFixedRotation",
                "jump", "setRot", "rotate", "setScale", "setSpin", "setColor", "destroy",
                "spawn3D", "spawn2D", "particles", "emitParticles", "playSound", "playMusic", "stopMusic",
                "shake", "shakeCamera", "setCamTarget", "setCamDist", "setSky",
                "addScore", "setScore", "getScore", "setHealth", "addHealth", "getHealth",
                "setBanner", "setHudText", "log",
                "chaseAI", "chaseTagAI", "raycast",
                "tweenPos", "tweenScale", "tweenRot", "tweenTagYoyo",
                "noise2D", "noise3D", "terrainHeight", "rand", "clamp", "time"
        };

        for (final String fnName : apiNames) {
            VarArgFunction bridgeFn = new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs vargs) {
                    int n = vargs.narg();
                    Object[] jArgs = new Object[n];
                    for (int i = 0; i < n; i++) {
                        LuaValue lv = vargs.arg(i + 1);
                        if (lv.isboolean()) jArgs[i] = lv.toboolean();
                        else if (lv.isnumber()) jArgs[i] = lv.todouble();
                        else if (lv.isnil()) jArgs[i] = null;
                        else jArgs[i] = lv.tojstring();
                    }
                    Object res = callBuiltIn(fnName, jArgs, 0);
                    if (res instanceof Boolean) return LuaValue.valueOf((Boolean) res);
                    if (res instanceof Number) return LuaValue.valueOf(((Number) res).doubleValue());
                    if (res instanceof String) return LuaValue.valueOf((String) res);
                    return LuaValue.NIL;
                }
            };
            vortexTbl.set(fnName, bridgeFn);
            // Also expose directly as global shorthand (like LÖVE2D / Pico-8)
            g.set(fnName, bridgeFn);
        }
        g.set("vortex", vortexTbl);
        g.set("print", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) sb.append('\t');
                    sb.append(args.arg(i).tojstring());
                }
                log(sb.toString());
                return LuaValue.NIL;
            }
        });
    }

    public String getLastError() { return lastError; }

    public synchronized void update(float dt) {
        elapsedTime += dt;
        aiEngine.onFrameTick();
        tweenSystem.update(dt);
        if (!compiledOk) return;
        callHook("onUpdate", (double) dt);
    }

    public synchronized void notifyCollision(Entity a, Entity b) {
        if (!compiledOk) return;
        callHook("onCollide", a.name, b.name, a.tag, b.tag);
    }

    public synchronized void notifyButton(String buttonName) {
        if (!compiledOk) return;
        callHook("onButton", buttonName);
    }

    public synchronized Object callHook(String fnName, Object... args) {
        if (usingLua) {
            if (luaGlobals == null) return null;
            LuaValue fn = luaGlobals.get(fnName);
            if (fn == null || fn.isnil() || !fn.isfunction()) return null;
            try {
                LuaValue[] lArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a instanceof Number) lArgs[i] = LuaValue.valueOf(((Number) a).doubleValue());
                    else if (a instanceof Boolean) lArgs[i] = LuaValue.valueOf((Boolean) a);
                    else lArgs[i] = LuaValue.valueOf(String.valueOf(a));
                }
                fn.invoke(lArgs);
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log("[Lua Runtime Error in " + fnName + "] " + lastError);
                compiledOk = false;
            }
            return null;
        }

        FnDecl fn = functions.get(fnName);
        if (fn == null) return null;
        instructionBudget = MAX_INSTRUCTIONS_PER_CALL;
        try {
            return invokeUserFn(fn, args);
        } catch (Exception ex) {
            lastError = ex.getMessage();
            log("[Runtime Error in " + fnName + "] " + lastError);
            compiledOk = false;
            return null;
        }
    }

    // ===================== ENGINE BUILT-IN API (LUA 5.2 + VORTEXSCRIPT) =====================

    private Object callBuiltIn(String name, Object[] a, int line) {
        if (project == null) return 0.0;
        switch (name) {
            case "joyX":
            case "inputX": return (double) inputJoyX;
            case "joyY":
            case "inputY": return (double) inputJoyY;
            case "btnA": return inputBtnA;
            case "btnB": return inputBtnB;
            case "touchDown": return touchActive;
            case "touchX": return (double) touchWorldX;
            case "touchY": return (double) touchWorldY;

            case "exists": return project.findByName(str(a, 0)) != null;
            case "getX": { Entity e = ent(a, 0); return e != null ? (double) e.x : 0.0; }
            case "getY": { Entity e = ent(a, 0); return e != null ? (double) e.y : 0.0; }
            case "getZ": { Entity e = ent(a, 0); return e != null ? (double) e.z : 0.0; }
            case "getVX": { Entity e = ent(a, 0); return e != null ? (double) e.vx : 0.0; }
            case "getVY": { Entity e = ent(a, 0); return e != null ? (double) e.vy : 0.0; }
            case "getVZ": { Entity e = ent(a, 0); return e != null ? (double) e.vz : 0.0; }
            case "getRotY": { Entity e = ent(a, 0); return e != null ? (double) e.rotY : 0.0; }
            case "getRotZ": { Entity e = ent(a, 0); return e != null ? (double) e.rotZ : 0.0; }
            case "getTag": { Entity e = ent(a, 0); return e != null ? e.tag : ""; }
            case "isGrounded": { Entity e = ent(a, 0); return e != null && e.isGrounded; }
            case "dist": {
                Entity e1 = ent(a, 0), e2 = ent(a, 1);
                if (e1 == null || e2 == null) return 999999.0;
                return (double) Vec3.dist(e1.x, e1.y, e1.z, e2.x, e2.y, e2.z);
            }
            case "countByTag": {
                String t = str(a, 0);
                int cnt = 0;
                for (Entity e : project.entities) {
                    if (e.alive && t.equalsIgnoreCase(e.tag)) cnt++;
                }
                return (double) cnt;
            }

            case "setPos": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.x = flt(a, 1, e.x);
                    e.y = flt(a, 2, e.y);
                    if (a.length > 3) e.z = flt(a, 3, e.z);
                    e.transformDirty = true;
                }
                return 0.0;
            }
            case "move": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.x += flt(a, 1, 0f);
                    e.y += flt(a, 2, 0f);
                    if (a.length > 3) e.z += flt(a, 3, 0f);
                    e.transformDirty = true;
                }
                return 0.0;
            }
            case "setVel": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.vx = flt(a, 1, e.vx);
                    e.vy = flt(a, 2, e.vy);
                    if (a.length > 3) e.vz = flt(a, 3, e.vz);
                }
                return 0.0;
            }
            case "addVel": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.vx += flt(a, 1, 0f);
                    e.vy += flt(a, 2, 0f);
                    if (a.length > 3) e.vz += flt(a, 3, 0f);
                }
                return 0.0;
            }
            case "applyImpulse": {
                Entity e = ent(a, 0);
                if (e != null) {
                    float ix = flt(a, 1, 0f), iy = flt(a, 2, 0f);
                    if (physicsWorld != null) physicsWorld.applyImpulse2D(e, ix, iy);
                    else { e.vx += ix; e.vy += iy; }
                }
                return 0.0;
            }
            case "setAngularVel": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.fixedRotation = false;
                    e.angularVel = flt(a, 1, 0f);
                }
                return 0.0;
            }
            case "setFixedRotation": {
                Entity e = ent(a, 0);
                if (e != null && a.length > 1) {
                    e.fixedRotation = truthy(a[1]);
                }
                return 0.0;
            }
            case "jump": {
                Entity e = ent(a, 0);
                float imp = flt(a, 1, 11f);
                if (e != null && e.isGrounded) {
                    e.vy = imp;
                    e.isGrounded = false;
                    if (hostBridge != null) hostBridge.onPlaySound("jump");
                    return true;
                }
                return false;
            }
            case "setRot": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.rotX = flt(a, 1, e.rotX);
                    e.rotY = flt(a, 2, e.rotY);
                    e.rotZ = flt(a, 3, e.rotZ);
                    e.transformDirty = true;
                }
                return 0.0;
            }
            case "rotate": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.rotX += flt(a, 1, 0f);
                    e.rotY += flt(a, 2, 0f);
                    e.rotZ += flt(a, 3, 0f);
                    e.transformDirty = true;
                }
                return 0.0;
            }
            case "setScale": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.scaleX = flt(a, 1, e.scaleX);
                    e.scaleY = flt(a, 2, e.scaleY);
                    if (a.length > 3) e.scaleZ = flt(a, 3, e.scaleZ);
                }
                return 0.0;
            }
            case "setSpin": {
                Entity e = ent(a, 0);
                if (e != null) {
                    e.spinX = flt(a, 1, 0f);
                    e.spinY = flt(a, 2, 0f);
                    e.spinZ = flt(a, 3, 0f);
                }
                return 0.0;
            }
            case "setColor": {
                Entity e = ent(a, 0);
                if (e != null) e.colorHex = str(a, 1);
                return 0.0;
            }
            case "destroy": {
                Entity e = ent(a, 0);
                if (e != null) e.alive = false;
                return 0.0;
            }

            case "spawn3D": {
                if (project.entities.size() >= 300) return "";
                String nm = str(a, 0);
                String tag = a.length > 1 ? str(a, 1) : "decor";
                String prim = a.length > 2 ? str(a, 2) : "CUBE";
                float x = flt(a, 3, 0f), y = flt(a, 4, 1f), z = flt(a, 5, 0f);
                float sx = flt(a, 6, 1f), sy = flt(a, 7, 1f), sz = flt(a, 8, 1f);
                String col = a.length > 9 ? str(a, 9) : "#00E5FF";
                String body = a.length > 10 ? str(a, 10) : "DYNAMIC";
                Entity ne = Entity.create3D(nm, tag, prim, x, y, z, sx, sy, sz, col, body);
                if ("coin".equalsIgnoreCase(tag) || "projectile".equalsIgnoreCase(tag)) ne.isTrigger = true;
                project.entities.add(ne);
                return ne.name;
            }
            case "spawn2D": {
                if (project.entities.size() >= 300) return "";
                String nm = str(a, 0);
                String tag = a.length > 1 ? str(a, 1) : "decor";
                String prim = a.length > 2 ? str(a, 2) : "RECT";
                float x = flt(a, 3, 0f), y = flt(a, 4, 0f);
                float w = flt(a, 5, 1f), h = flt(a, 6, 1f);
                String col = a.length > 7 ? str(a, 7) : "#00E5FF";
                String body = a.length > 8 ? str(a, 8) : "DYNAMIC";
                Entity ne = Entity.create2D(nm, tag, prim, x, y, w, h, col, body);
                if ("coin".equalsIgnoreCase(tag) || "projectile".equalsIgnoreCase(tag)) ne.isTrigger = true;
                if ("DYNAMIC".equalsIgnoreCase(body) && !"player".equalsIgnoreCase(tag)) {
                    ne.fixedRotation = false; // JBox2D tumbling crates/balls!
                }
                project.entities.add(ne);
                return ne.name;
            }

            case "particles":
            case "emitParticles": {
                if (hostBridge != null) {
                    float x = flt(a, 0, 0f), y = flt(a, 1, 0f), z = flt(a, 2, 0f);
                    int cnt = (int) flt(a, 3, 20f);
                    String col = a.length > 4 ? str(a, 4) : "#00E5FF";
                    hostBridge.onEmitParticles(x, y, z, cnt, col);
                }
                return 0.0;
            }
            case "playSound": {
                if (hostBridge != null) hostBridge.onPlaySound(str(a, 0));
                return 0.0;
            }
            case "playMusic": {
                String pat = a.length > 0 ? ChiptuneGenerator.resolvePresetOrCustom(str(a, 0)) : project.musicPattern;
                int bpm = a.length > 1 ? (int) flt(a, 1, project.musicBpm) : project.musicBpm;
                project.musicEnabled = true;
                project.musicPattern = pat;
                project.musicBpm = bpm;
                if (hostBridge != null) hostBridge.onPlayMusic(pat, bpm);
                return 0.0;
            }
            case "stopMusic": {
                if (hostBridge != null) hostBridge.onStopMusic();
                return 0.0;
            }
            // v3.0 AI Pathfinding & Raycasting (xaguzman/pathfinding Theta* & A*)
            case "distance": {
                Entity ea = project.findByName(str(a, 0));
                Entity eb = project.findByName(str(a, 1));
                if (ea == null || eb == null) return 9999.0;
                return Math.sqrt((ea.x - eb.x) * (ea.x - eb.x) + (ea.y - eb.y) * (ea.y - eb.y) + (ea.z - eb.z) * (ea.z - eb.z));
            }
            case "chaseAI": {
                Entity seeker = project.findByName(str(a, 0));
                Entity target = project.findByName(str(a, 1));
                float spd = flt(a, 2, 5.0f);
                aiEngine.chaseEntityAI(project, physicsWorld, seeker, target, spd);
                return 0.0;
            }
            case "chaseTagAI": {
                String tag = str(a, 0);
                Entity target = project.findByName(str(a, 1));
                float spd = flt(a, 2, 4.5f);
                if (target != null) {
                    for (int i = 0; i < project.entities.size(); i++) {
                        Entity se = project.entities.get(i);
                        if (se.alive && tag.equalsIgnoreCase(se.tag)) {
                            aiEngine.chaseEntityAI(project, physicsWorld, se, target, spd);
                        }
                    }
                }
                return 0.0;
            }
            case "raycast": {
                float ox = flt(a, 0, 0f), oy = flt(a, 1, 0f), oz = flt(a, 2, 0f);
                float dx = flt(a, 3, 1f), dy = flt(a, 4, 0f), dz = flt(a, 5, 0f);
                float maxD = flt(a, 6, 40f);
                return aiEngine.raycast(project, null, ox, oy, oz, dx, dy, dz, maxD);
            }
            // v3.0 Universal Tween Engine (Bounce, Elastic, Back, Sine, Quad, Expo + Yoyo)
            case "tweenPos": {
                Entity e = project.findByName(str(a, 0));
                if (e != null) {
                    tweenSystem.tweenPos(e, flt(a, 1, e.x), flt(a, 2, e.y), flt(a, 3, e.z),
                            flt(a, 4, 1.0f), a.length > 5 ? str(a, 5) : "SINE", (int) flt(a, 6, 0f));
                }
                return 0.0;
            }
            case "tweenScale": {
                Entity e = project.findByName(str(a, 0));
                if (e != null) {
                    tweenSystem.tweenScale(e, flt(a, 1, e.scaleX), flt(a, 2, e.scaleY), flt(a, 3, e.scaleZ),
                            flt(a, 4, 0.6f), a.length > 5 ? str(a, 5) : "ELASTIC", (int) flt(a, 6, 0f));
                }
                return 0.0;
            }
            case "tweenRot": {
                Entity e = project.findByName(str(a, 0));
                if (e != null) {
                    tweenSystem.tweenRot(e, flt(a, 1, e.rotX), flt(a, 2, e.rotY), flt(a, 3, e.rotZ),
                            flt(a, 4, 1.0f), a.length > 5 ? str(a, 5) : "QUAD", (int) flt(a, 6, 0f));
                }
                return 0.0;
            }
            case "tweenTagYoyo": {
                String tag = str(a, 0);
                float dy = flt(a, 1, 0.55f);
                float dur = flt(a, 2, 1.0f);
                String ease = a.length > 3 ? str(a, 3) : "SINE";
                for (int i = 0; i < project.entities.size(); i++) {
                    Entity te = project.entities.get(i);
                    if (te.alive && tag.equalsIgnoreCase(te.tag)) {
                        tweenSystem.tweenPos(te, te.x, te.y + dy, te.z, dur, ease, -1);
                    }
                }
                return 0.0;
            }
            case "terrainHeight": {
                if (physicsWorld != null) {
                    return (double) physicsWorld.sampleTerrainHeightAt(project, flt(a, 0, 0f), flt(a, 1, 0f));
                }
                return 0.0;
            }
            case "shake":
            case "shakeCamera": {
                if (hostBridge != null) hostBridge.onCameraShake(flt(a, 0, 0.35f), flt(a, 1, 0.25f));
                return 0.0;
            }
            case "setCamTarget": project.cameraTargetName = str(a, 0); return 0.0;
            case "setCamDist":
                project.cameraDistance = flt(a, 0, project.cameraDistance);
                project.cameraHeight = flt(a, 1, project.cameraHeight);
                return 0.0;
            case "setSky": project.skyColorHex = str(a, 0); return 0.0;

            case "addScore": score += (int) flt(a, 0, 1f); return (double) score;
            case "setScore": score = (int) flt(a, 0, 0f); return (double) score;
            case "getScore": return (double) score;
            case "setHealth": health = Math.max(0, Math.min(999, (int) flt(a, 0, 100f))); return (double) health;
            case "addHealth": health = Math.max(0, Math.min(999, health + (int) flt(a, 0, 0f))); return (double) health;
            case "getHealth": return (double) health;
            case "setBanner":
            case "setHudText": hudBanner = str(a, 0); return 0.0;
            case "log": log(str(a, 0)); return 0.0;

            // Open-Source FastNoiseLite procedural noise functions!
            case "noise2D": return (double) fastNoise.GetNoise(flt(a, 0, 0f), flt(a, 1, 0f));
            case "noise3D": return (double) fastNoise.GetNoise(flt(a, 0, 0f), flt(a, 1, 0f), flt(a, 2, 0f));

            case "sin": return Math.sin(flt(a, 0, 0f));
            case "cos": return Math.cos(flt(a, 0, 0f));
            case "atan2": return Math.atan2(flt(a, 0, 0f), flt(a, 1, 1f));
            case "sqrt": return Math.sqrt(Math.max(0.0, flt(a, 0, 0f)));
            case "abs": return Math.abs((double) flt(a, 0, 0f));
            case "min": return Math.min((double) flt(a, 0, 0f), (double) flt(a, 1, 0f));
            case "max": return Math.max((double) flt(a, 0, 0f), (double) flt(a, 1, 0f));
            case "floor": return Math.floor(flt(a, 0, 0f));
            case "clamp": {
                double v = flt(a, 0, 0f), lo = flt(a, 1, 0f), hi = flt(a, 2, 1f);
                return Math.max(lo, Math.min(hi, v));
            }
            case "rand": {
                float lo = flt(a, 0, 0f), hi = flt(a, 1, 1f);
                return (double) (lo + rng.nextFloat() * (hi - lo));
            }
            case "time": return (double) elapsedTime;

            default:
                throw new ScriptException(line, "Unknown function '" + name + "()'");
        }
    }

    // ===================== VORTEXSCRIPT (.VXS) LEXER, PARSER & VM =====================

    private enum TokType {
        NUM, STR, IDENT, LET, FN, IF, ELSE, WHILE, FOR, RETURN, TRUE, FALSE,
        LPAREN, RPAREN, LBRACE, RBRACE, COMMA, SEMI,
        PLUS, MINUS, STAR, SLASH, PERCENT,
        ASSIGN, PLUS_EQ, MINUS_EQ, STAR_EQ, SLASH_EQ,
        EQ, NEQ, LT, LTE, GT, GTE, AND, OR, NOT, EOF
    }

    private static class Token {
        final TokType type; final String text; final double numVal; final int line;
        Token(TokType type, String text, double numVal, int line) {
            this.type = type; this.text = text; this.numVal = numVal; this.line = line;
        }
    }

    public static class ScriptException extends RuntimeException {
        public final int line;
        public ScriptException(int line, String msg) {
            super("Line " + line + ": " + msg);
            this.line = line;
        }
    }

    private static String validateVxsSyntax(String code) {
        try {
            List<Token> toks = tokenize(code);
            new Parser(toks).parseProgram();
            return "OK";
        } catch (ScriptException se) {
            return se.getMessage();
        } catch (Exception ex) {
            return "Error: " + ex.getMessage();
        }
    }

    private static List<Token> tokenize(String src) {
        List<Token> out = new ArrayList<>();
        if (src == null) { out.add(new Token(TokType.EOF, "", 0, 1)); return out; }
        int n = src.length(), i = 0, line = 1;
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                i += 2; while (i < n && src.charAt(i) != '\n') i++; continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    if (src.charAt(i) == '\n') line++;
                    i++;
                }
                i += 2; continue;
            }
            if (c == '"' || c == '\'') {
                char quote = c; int startLine = line; i++;
                StringBuilder sb = new StringBuilder();
                while (i < n && src.charAt(i) != quote) {
                    char ch = src.charAt(i);
                    if (ch == '\n') line++;
                    if (ch == '\\' && i + 1 < n) {
                        char nx = src.charAt(i + 1);
                        sb.append(nx == 'n' ? '\n' : (nx == 't' ? '\t' : nx));
                        i += 2;
                    } else { sb.append(ch); i++; }
                }
                if (i >= n) throw new ScriptException(startLine, "Unterminated string literal");
                i++; out.add(new Token(TokType.STR, sb.toString(), 0, startLine)); continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(src.charAt(i + 1)))) {
                int start = i;
                while (i < n && (Character.isDigit(src.charAt(i)) || src.charAt(i) == '.')) i++;
                String numStr = src.substring(start, i);
                try { out.add(new Token(TokType.NUM, numStr, Double.parseDouble(numStr), line)); }
                catch (NumberFormatException ex) { throw new ScriptException(line, "Invalid number '" + numStr + "'"); }
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isLetterOrDigit(src.charAt(i)) || src.charAt(i) == '_')) i++;
                String id = src.substring(start, i);
                TokType kw;
                switch (id) {
                    case "let": case "var": kw = TokType.LET; break;
                    case "fn": case "function": kw = TokType.FN; break;
                    case "if": kw = TokType.IF; break;
                    case "else": kw = TokType.ELSE; break;
                    case "while": kw = TokType.WHILE; break;
                    case "for": kw = TokType.FOR; break;
                    case "return": kw = TokType.RETURN; break;
                    case "true": kw = TokType.TRUE; break;
                    case "false": kw = TokType.FALSE; break;
                    default: kw = TokType.IDENT; break;
                }
                out.add(new Token(kw, id, 0, line)); continue;
            }
            if (i + 1 < n) {
                String two = src.substring(i, i + 2);
                TokType tt = null;
                switch (two) {
                    case "==": tt = TokType.EQ; break;
                    case "!=": tt = TokType.NEQ; break;
                    case "<=": tt = TokType.LTE; break;
                    case ">=": tt = TokType.GTE; break;
                    case "&&": tt = TokType.AND; break;
                    case "||": tt = TokType.OR; break;
                    case "+=": tt = TokType.PLUS_EQ; break;
                    case "-=": tt = TokType.MINUS_EQ; break;
                    case "*=": tt = TokType.STAR_EQ; break;
                    case "/=": tt = TokType.SLASH_EQ; break;
                }
                if (tt != null) { out.add(new Token(tt, two, 0, line)); i += 2; continue; }
            }
            TokType st;
            switch (c) {
                case '(': st = TokType.LPAREN; break;
                case ')': st = TokType.RPAREN; break;
                case '{': st = TokType.LBRACE; break;
                case '}': st = TokType.RBRACE; break;
                case ',': st = TokType.COMMA; break;
                case ';': st = TokType.SEMI; break;
                case '+': st = TokType.PLUS; break;
                case '-': st = TokType.MINUS; break;
                case '*': st = TokType.STAR; break;
                case '/': st = TokType.SLASH; break;
                case '%': st = TokType.PERCENT; break;
                case '=': st = TokType.ASSIGN; break;
                case '<': st = TokType.LT; break;
                case '>': st = TokType.GT; break;
                case '!': st = TokType.NOT; break;
                default: throw new ScriptException(line, "Unexpected character '" + c + "'");
            }
            out.add(new Token(st, String.valueOf(c), 0, line)); i++;
        }
        out.add(new Token(TokType.EOF, "", 0, line));
        return out;
    }

    private interface Stmt {}
    private interface Expr {}
    private static class BlockStmt implements Stmt { final List<Stmt> stmts; BlockStmt(List<Stmt> s) { stmts = s; } }
    private static class LetStmt implements Stmt { final String name; final Expr init; LetStmt(String n, Expr i) { name = n; init = i; } }
    private static class AssignStmt implements Stmt { final String name; final TokType op; final Expr value; final int line; AssignStmt(String n, TokType o, Expr v, int l) { name = n; op = o; value = v; line = l; } }
    private static class IfStmt implements Stmt { final Expr cond; final Stmt thenBranch, elseBranch; IfStmt(Expr c, Stmt t, Stmt e) { cond = c; thenBranch = t; elseBranch = e; } }
    private static class WhileStmt implements Stmt { final Expr cond; final Stmt body; WhileStmt(Expr c, Stmt b) { cond = c; body = b; } }
    private static class ForStmt implements Stmt { final Stmt init, step, body; final Expr cond; ForStmt(Stmt i, Expr c, Stmt s, Stmt b) { init = i; cond = c; step = s; body = b; } }
    private static class ReturnStmt implements Stmt { final Expr value; ReturnStmt(Expr v) { value = v; } }
    private static class ExprStmt implements Stmt { final Expr expr; ExprStmt(Expr e) { expr = e; } }
    private static class FnDecl { final String name; final List<String> params; final BlockStmt body; FnDecl(String n, List<String> p, BlockStmt b) { name = n; params = p; body = b; } }
    private static class LiteralExpr implements Expr { final Object value; LiteralExpr(Object v) { value = v; } }
    private static class VarExpr implements Expr { final String name; final int line; VarExpr(String n, int l) { name = n; line = l; } }
    private static class UnaryExpr implements Expr { final TokType op; final Expr right; UnaryExpr(TokType o, Expr r) { op = o; right = r; } }
    private static class BinaryExpr implements Expr { final Expr left, right; final TokType op; BinaryExpr(Expr l, TokType o, Expr r) { left = l; op = o; right = r; } }
    private static class CallExpr implements Expr { final String callee; final List<Expr> args; final int line; CallExpr(String c, List<Expr> a, int l) { callee = c; args = a; line = l; } }

    private static class Parser {
        final List<Token> tokens; int pos = 0;
        final List<Stmt> topLevelStmts = new ArrayList<>();
        final Map<String, FnDecl> fns = new HashMap<>();
        Parser(List<Token> tokens) { this.tokens = tokens; }
        Token peek() { return tokens.get(pos); }
        Token prev() { return tokens.get(pos - 1); }
        boolean isAtEnd() { return peek().type == TokType.EOF; }
        boolean check(TokType t) { return !isAtEnd() && peek().type == t; }
        boolean match(TokType... types) { for (TokType t : types) if (check(t)) { pos++; return true; } return false; }
        Token consume(TokType t, String err) { if (check(t)) return tokens.get(pos++); throw new ScriptException(peek().line, err + " (found '" + peek().text + "')"); }
        void optionalSemi() { while (match(TokType.SEMI)) {} }
        void parseProgram() {
            while (!isAtEnd()) {
                optionalSemi(); if (isAtEnd()) break;
                if (match(TokType.FN)) { FnDecl fn = parseFnDecl(); fns.put(fn.name, fn); }
                else topLevelStmts.add(parseStmt());
                optionalSemi();
            }
        }
        FnDecl parseFnDecl() {
            Token name = consume(TokType.IDENT, "Expected function name");
            consume(TokType.LPAREN, "Expected '('");
            List<String> params = new ArrayList<>();
            if (!check(TokType.RPAREN)) do { params.add(consume(TokType.IDENT, "Expected param").text); } while (match(TokType.COMMA));
            consume(TokType.RPAREN, "Expected ')'");
            return new FnDecl(name.text, params, parseBlock());
        }
        BlockStmt parseBlock() {
            consume(TokType.LBRACE, "Expected '{'");
            List<Stmt> list = new ArrayList<>();
            while (!check(TokType.RBRACE) && !isAtEnd()) { optionalSemi(); if (check(TokType.RBRACE) || isAtEnd()) break; list.add(parseStmt()); optionalSemi(); }
            consume(TokType.RBRACE, "Expected '}'");
            return new BlockStmt(list);
        }
        Stmt parseStmt() {
            if (check(TokType.LBRACE)) return parseBlock();
            if (match(TokType.LET)) {
                Token name = consume(TokType.IDENT, "Expected variable name");
                Expr init = match(TokType.ASSIGN) ? parseExpr() : new LiteralExpr(0.0);
                optionalSemi(); return new LetStmt(name.text, init);
            }
            if (match(TokType.IF)) {
                consume(TokType.LPAREN, "Expected '('"); Expr c = parseExpr(); consume(TokType.RPAREN, "Expected ')'");
                Stmt tb = parseStmt(); Stmt eb = match(TokType.ELSE) ? parseStmt() : null;
                return new IfStmt(c, tb, eb);
            }
            if (match(TokType.WHILE)) {
                consume(TokType.LPAREN, "Expected '('"); Expr c = parseExpr(); consume(TokType.RPAREN, "Expected ')'");
                return new WhileStmt(c, parseStmt());
            }
            if (match(TokType.FOR)) {
                consume(TokType.LPAREN, "Expected '('");
                Stmt init = match(TokType.SEMI) ? null : parseStmt();
                Expr cond = check(TokType.SEMI) ? new LiteralExpr(Boolean.TRUE) : parseExpr();
                consume(TokType.SEMI, "Expected ';'");
                Stmt step = check(TokType.RPAREN) ? null : parseSimpleAssignOrExpr();
                consume(TokType.RPAREN, "Expected ')'");
                return new ForStmt(init, cond, step, parseStmt());
            }
            if (match(TokType.RETURN)) {
                Expr val = (!check(TokType.SEMI) && !check(TokType.RBRACE) && !isAtEnd()) ? parseExpr() : null;
                optionalSemi(); return new ReturnStmt(val);
            }
            Stmt s = parseSimpleAssignOrExpr(); optionalSemi(); return s;
        }
        Stmt parseSimpleAssignOrExpr() {
            if (check(TokType.IDENT)) {
                Token id = tokens.get(pos); TokType next = tokens.get(pos + 1).type;
                if (next == TokType.ASSIGN || next == TokType.PLUS_EQ || next == TokType.MINUS_EQ || next == TokType.STAR_EQ || next == TokType.SLASH_EQ) {
                    pos += 2; return new AssignStmt(id.text, next, parseExpr(), id.line);
                }
            }
            return new ExprStmt(parseExpr());
        }
        Expr parseExpr() { return parseOr(); }
        Expr parseOr() { Expr l = parseAnd(); while (match(TokType.OR)) l = new BinaryExpr(l, prev().type, parseAnd()); return l; }
        Expr parseAnd() { Expr l = parseEquality(); while (match(TokType.AND)) l = new BinaryExpr(l, prev().type, parseEquality()); return l; }
        Expr parseEquality() { Expr l = parseComparison(); while (match(TokType.EQ, TokType.NEQ)) l = new BinaryExpr(l, prev().type, parseComparison()); return l; }
        Expr parseComparison() { Expr l = parseTerm(); while (match(TokType.LT, TokType.LTE, TokType.GT, TokType.GTE)) l = new BinaryExpr(l, prev().type, parseTerm()); return l; }
        Expr parseTerm() { Expr l = parseFactor(); while (match(TokType.PLUS, TokType.MINUS)) l = new BinaryExpr(l, prev().type, parseFactor()); return l; }
        Expr parseFactor() { Expr l = parseUnary(); while (match(TokType.STAR, TokType.SLASH, TokType.PERCENT)) l = new BinaryExpr(l, prev().type, parseUnary()); return l; }
        Expr parseUnary() { if (match(TokType.MINUS, TokType.NOT)) return new UnaryExpr(prev().type, parseUnary()); return parsePrimary(); }
        Expr parsePrimary() {
            if (match(TokType.NUM)) return new LiteralExpr(prev().numVal);
            if (match(TokType.STR)) return new LiteralExpr(prev().text);
            if (match(TokType.TRUE)) return new LiteralExpr(Boolean.TRUE);
            if (match(TokType.FALSE)) return new LiteralExpr(Boolean.FALSE);
            if (match(TokType.IDENT)) {
                Token id = prev();
                if (match(TokType.LPAREN)) {
                    List<Expr> args = new ArrayList<>();
                    if (!check(TokType.RPAREN)) do { args.add(parseExpr()); } while (match(TokType.COMMA));
                    consume(TokType.RPAREN, "Expected ')'");
                    return new CallExpr(id.text, args, id.line);
                }
                return new VarExpr(id.text, id.line);
            }
            if (match(TokType.LPAREN)) { Expr in = parseExpr(); consume(TokType.RPAREN, "Expected ')'"); return in; }
            throw new ScriptException(peek().line, "Unexpected token '" + peek().text + "'");
        }
    }

    private static class ReturnSignal extends RuntimeException { final Object value; ReturnSignal(Object v) { value = v; } }

    private Object invokeUserFn(FnDecl fn, Object[] args) {
        Map<String, Object> local = new HashMap<>();
        for (int i = 0; i < fn.params.size(); i++) local.put(fn.params.get(i), i < args.length ? args[i] : 0.0);
        try { for (Stmt s : fn.body.stmts) execStmt(s, local, false); }
        catch (ReturnSignal ret) { return ret.value; }
        return 0.0;
    }

    private void tickBudget(int line) {
        if (--instructionBudget <= 0) throw new ScriptException(line, "Instruction budget exceeded");
    }

    private void execStmt(Stmt stmt, Map<String, Object> scope, boolean isGlobalScope) {
        tickBudget(0);
        if (stmt instanceof BlockStmt) { for (Stmt s : ((BlockStmt) stmt).stmts) execStmt(s, scope, isGlobalScope); }
        else if (stmt instanceof LetStmt) {
            LetStmt ls = (LetStmt) stmt; Object val = eval(ls.init, scope);
            if (isGlobalScope) globals.put(ls.name, val); else scope.put(ls.name, val);
        } else if (stmt instanceof AssignStmt) {
            AssignStmt as = (AssignStmt) stmt; Object rhs = eval(as.value, scope);
            boolean inLocal = !isGlobalScope && scope.containsKey(as.name);
            Object cur = inLocal ? scope.get(as.name) : globals.getOrDefault(as.name, 0.0);
            Object next;
            switch (as.op) {
                case PLUS_EQ: next = (cur instanceof String || rhs instanceof String) ? fmt(cur) + fmt(rhs) : num(cur) + num(rhs); break;
                case MINUS_EQ: next = num(cur) - num(rhs); break;
                case STAR_EQ: next = num(cur) * num(rhs); break;
                case SLASH_EQ: next = num(rhs) == 0.0 ? 0.0 : num(cur) / num(rhs); break;
                default: next = rhs;
            }
            if (inLocal) scope.put(as.name, next); else globals.put(as.name, next);
        } else if (stmt instanceof IfStmt) {
            IfStmt is = (IfStmt) stmt;
            if (truthy(eval(is.cond, scope))) execStmt(is.thenBranch, scope, isGlobalScope);
            else if (is.elseBranch != null) execStmt(is.elseBranch, scope, isGlobalScope);
        } else if (stmt instanceof WhileStmt) {
            WhileStmt ws = (WhileStmt) stmt;
            while (truthy(eval(ws.cond, scope))) { tickBudget(0); execStmt(ws.body, scope, isGlobalScope); }
        } else if (stmt instanceof ForStmt) {
            ForStmt fs = (ForStmt) stmt;
            if (fs.init != null) execStmt(fs.init, scope, isGlobalScope);
            while (truthy(eval(fs.cond, scope))) { tickBudget(0); execStmt(fs.body, scope, isGlobalScope); if (fs.step != null) execStmt(fs.step, scope, isGlobalScope); }
        } else if (stmt instanceof ReturnStmt) {
            ReturnStmt rs = (ReturnStmt) stmt; throw new ReturnSignal(rs.value != null ? eval(rs.value, scope) : 0.0);
        } else if (stmt instanceof ExprStmt) { eval(((ExprStmt) stmt).expr, scope); }
    }

    private Object eval(Expr expr, Map<String, Object> scope) {
        tickBudget(0);
        if (expr instanceof LiteralExpr) return ((LiteralExpr) expr).value;
        if (expr instanceof VarExpr) {
            String nm = ((VarExpr) expr).name;
            if (scope.containsKey(nm)) return scope.get(nm);
            return globals.getOrDefault(nm, 0.0);
        }
        if (expr instanceof UnaryExpr) {
            UnaryExpr ue = (UnaryExpr) expr; Object r = eval(ue.right, scope);
            return ue.op == TokType.MINUS ? -num(r) : !truthy(r);
        }
        if (expr instanceof BinaryExpr) {
            BinaryExpr be = (BinaryExpr) expr;
            if (be.op == TokType.AND) return truthy(eval(be.left, scope)) && truthy(eval(be.right, scope));
            if (be.op == TokType.OR) return truthy(eval(be.left, scope)) || truthy(eval(be.right, scope));
            Object l = eval(be.left, scope), r = eval(be.right, scope);
            switch (be.op) {
                case PLUS: return (l instanceof String || r instanceof String) ? fmt(l) + fmt(r) : num(l) + num(r);
                case MINUS: return num(l) - num(r);
                case STAR: return num(l) * num(r);
                case SLASH: return num(r) == 0.0 ? 0.0 : num(l) / num(r);
                case PERCENT: return num(r) == 0.0 ? 0.0 : num(l) % num(r);
                case LT: return num(l) < num(r);
                case LTE: return num(l) <= num(r);
                case GT: return num(l) > num(r);
                case GTE: return num(l) >= num(r);
                case EQ: return eqVal(l, r);
                case NEQ: return !eqVal(l, r);
                default: return 0.0;
            }
        }
        if (expr instanceof CallExpr) {
            CallExpr ce = (CallExpr) expr;
            Object[] args = new Object[ce.args.size()];
            for (int i = 0; i < args.length; i++) args[i] = eval(ce.args.get(i), scope);
            if (functions.containsKey(ce.callee)) return invokeUserFn(functions.get(ce.callee), args);
            return callBuiltIn(ce.callee, args, ce.line);
        }
        return 0.0;
    }

    private Entity ent(Object[] a, int idx) { return project != null && idx < a.length ? project.findByName(fmt(a[idx])) : null; }
    private static String str(Object[] a, int idx) { return idx < a.length ? fmt(a[idx]) : ""; }
    private static float flt(Object[] a, int idx, float def) { return idx < a.length ? (float) num(a[idx]) : def; }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Boolean) return ((Boolean) o) ? 1.0 : 0.0;
        if (o instanceof String) { try { return Double.parseDouble((String) o); } catch (Exception e) { return 0.0; } }
        return 0.0;
    }

    private static boolean truthy(Object o) {
        if (o == null) return false;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).doubleValue() != 0.0;
        if (o instanceof String) return !((String) o).isEmpty();
        return true;
    }

    private static boolean eqVal(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) return Math.abs(((Number) a).doubleValue() - ((Number) b).doubleValue()) < 1e-6;
        return fmt(a).equals(fmt(b));
    }

    private static String fmt(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            double d = (Double) o;
            if (Math.abs(d - Math.rint(d)) < 1e-7 && Math.abs(d) < 1e12) return String.valueOf((long) Math.rint(d));
            return String.format(java.util.Locale.US, "%.2f", d);
        }
        return String.valueOf(o);
    }

    private void log(String msg) { if (hostBridge != null) hostBridge.onLog(msg); }
}
