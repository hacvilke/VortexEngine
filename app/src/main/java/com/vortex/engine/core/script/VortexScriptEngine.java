package com.vortex.engine.core.script;

import com.vortex.engine.core.ai.VortexAIEngine;
import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.math.Vec3;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.noise.FastNoiseLite;
import com.vortex.engine.core.noise.TerrainGenerator;
import com.vortex.engine.core.physics.PhysicsWorld;
import com.vortex.engine.core.storage.VortexSaveStore;
import com.vortex.engine.core.tween.VortexTweenSystem;
import bsh.EvalError;
import bsh.Interpreter;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.Undefined;
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

    // v4.3 Built-In In-Game GUI Overlay State (Labels, Progress/HP Bars, RPG/Story Dialog Box)
    public static final class UiWidget {
        public String id;
        public String kind; // "LABEL" or "BAR"
        public String text = "";
        public float xPct = 50f;
        public float yPct = 14f;
        public float value = 100f;
        public float maxValue = 100f;
        public int fontSize = 15;
        public String colorHex = "#FFFFFF";
    }

    public static final class DialogState {
        public volatile boolean active = false;
        public volatile String speaker = "";
        public volatile String message = "";
        public volatile boolean timed = false;
        public volatile float remainingSec = 0f;
        public volatile float ageSec = 0f;

        public void reset() {
            active = false;
            speaker = "";
            message = "";
            timed = false;
            remainingSec = 0f;
            ageSec = 0f;
        }
    }

    public static final class PlaytestReport {
        public boolean ok;
        public int framesSimulated;
        public int activeEntities;
        public int finalScore;
        public int finalHealth;
        public int uiWidgetCount;
        public boolean dialogShown;
        public String error;
        public final List<String> logs = new ArrayList<>();

        public String toSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append(ok ? "[TEST PASS]" : "[TEST FAIL]")
              .append(" frames=").append(framesSimulated)
              .append(" entities=").append(activeEntities)
              .append(" score=").append(finalScore)
              .append(" hp=").append(finalHealth)
              .append(" ui=").append(uiWidgetCount + (dialogShown ? 1 : 0));
            if (error != null && !error.isEmpty()) {
                sb.append("\nError: ").append(error);
            }
            if (!logs.isEmpty()) {
                sb.append("\nLogs: ").append(String.join(" | ", logs.subList(0, Math.min(6, logs.size()))));
            }
            return sb.toString();
        }
    }

    public final Map<String, UiWidget> uiWidgets = new java.util.concurrent.ConcurrentHashMap<>();
    public final DialogState activeDialog = new DialogState();

    private GameProject project;
    private PhysicsWorld physicsWorld;
    private HostBridge hostBridge;
    public final VortexTweenSystem tweenSystem = new VortexTweenSystem();
    public final VortexAIEngine aiEngine = new VortexAIEngine();
    private FastNoiseLite fastNoise = TerrainGenerator.createConfiguredNoise(1337, 0.065f);
    private final Random rng = new Random();

    // Active VM mode: "JS" (Mozilla Rhino ES6+), "JAVA" (Java VM via BeanShell), "LUA" (LuaJ 5.2), or "VXS" (VortexScript)
    private String activeVmMode = "JS";
    private boolean usingLua = false;
    private Globals luaGlobals;
    private Scriptable rhinoScope;
    private Interpreter javaInterpreter;
    private Object javaScriptInstance;
    private final List<String> javaModuleVars = new ArrayList<>();
    private final Map<String, List<Function>> jsHookChains = new HashMap<>();
    private final Map<String, List<LuaValue>> luaHookChains = new HashMap<>();

    private static final int MAX_RHINO_OPS_PER_CALL = 250_000;
    private static final ThreadLocal<Integer> rhinoOpsCounter = new ThreadLocal<>();

    private static final ContextFactory SANDBOXED_RHINO_FACTORY = new ContextFactory() {
        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setLanguageVersion(Context.VERSION_ES6);
            // Optimization level -1 runs Rhino's fast bytecode interpreter identically on Android ART/DEX & Desktop JVM!
            cx.setOptimizationLevel(-1);
            cx.setInstructionObserverThreshold(25_000);
            cx.setClassShutter(fullClassName -> false);
            return cx;
        }

        @Override
        protected void observeInstructionCount(Context cx, int instructionCount) {
            Integer prev = rhinoOpsCounter.get();
            int total = (prev == null ? 0 : prev) + instructionCount;
            if (total > MAX_RHINO_OPS_PER_CALL) {
                throw new Error("JavaScript Execution Quota Exceeded (Infinite Loop Guard > " + MAX_RHINO_OPS_PER_CALL + " ops)");
            }
            rhinoOpsCounter.set(total);
        }
    };

    public static final String[] ENGINE_API_NAMES = new String[]{
            "joyX", "inputX", "joyY", "inputY", "btnA", "btnB", "touchDown", "touchX", "touchY",
            "exists", "getX", "getY", "getZ", "getVX", "getVY", "getVZ", "getRotY", "getRotZ", "getTag",
            "isGrounded", "dist", "distance", "countByTag", "findNearestTag",
            "setPos", "move", "setVel", "addVel", "applyImpulse", "setAngularVel", "setFixedRotation",
            "jump", "setRot", "rotate", "setScale", "setSpin", "setColor", "setSpriteAnim", "destroy",
            "spawn3D", "spawn2D", "particles", "emitParticles", "playSound", "playMusic", "stopMusic",
            "shake", "shakeCamera", "setCamTarget", "setCamDist", "setSky",
            "addScore", "setScore", "getScore", "setHealth", "addHealth", "getHealth",
            "saveData", "loadData", "loadNum",
            "setBanner", "setHudText", "showDialog", "hideDialog", "setLabel", "setProgressBar", "removeUI", "clearUI", "log",
            "chaseAI", "chaseTagAI", "raycast",
            "tweenPos", "tweenScale", "tweenRot", "tweenTagYoyo",
            "noise2D", "noise3D", "terrainHeight", "rand", "clamp", "time"
    };

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

    public static String detectRuntimeMode(String langSetting, String code) {
        if ("JAVA".equalsIgnoreCase(langSetting)) {
            return "JAVA";
        }
        if ("JS".equalsIgnoreCase(langSetting) || "JAVASCRIPT".equalsIgnoreCase(langSetting)) {
            return "JS";
        }
        if ("LUA".equalsIgnoreCase(langSetting)) {
            if (code != null && code.contains("fn on") && !code.contains("function ")) return "VXS";
            return "LUA";
        }
        if (code != null) {
            String trimmed = code.trim();
            if (trimmed.contains("public void on") || trimmed.contains("void onUpdate(") || trimmed.contains("public class ")) {
                return "JAVA";
            }
            if (trimmed.contains("const ") || trimmed.contains("class ") || trimmed.contains("=>") || trimmed.contains("console.log")) {
                return "JS";
            }
            if (trimmed.startsWith("--") || code.contains("then\n") || code.contains("elseif ")) {
                return "LUA";
            }
        }
        return "VXS";
    }

    /**
     * Normalizes modern Java 8/11/17 syntax (generic type arguments `<...>` & `@Override` annotations)
     * so standard Java game scripts execute identically on Desktop JRE/JDK and Android ART/DEX!
     */
    public static String normalizeJavaForInterpreter(String src) {
        if (src == null || src.isEmpty()) return "";
        String out = src.replaceAll("@Override\\b", "");
        // Strip diamond & simple generic angle brackets in type declarations (e.g., List<String>, ArrayList<>())
        out = out.replaceAll("new\\s+([A-Za-z0-9_$.]+)\\s*<[^>]*>\\s*\\(", "new $1(");
        out = out.replaceAll("\\b(List|ArrayList|Map|HashMap|Set|HashSet)\\s*<[^>;=(){]+>", "$1");
        return out;
    }

    public static String detectJavaEntryClassName(String src) {
        if (src == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?m)\\b(?:public\\s+)?class\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b").matcher(src);
        return m.find() ? m.group(1) : null;
    }

    public static boolean detectLuaMode(String langSetting, String code) {
        return "LUA".equals(detectRuntimeMode(langSetting, code));
    }

    /**
     * Bridges ES6 class declarations & `for (const x of ...)` for Mozilla Rhino 1.7.15
     * while preserving `let`, `const`, arrow functions, template literals, Map/Set, etc.
     */
    public static String transpileModernEs6ForRhino(String src) {
        if (src == null || src.isEmpty()) return "";
        String out = src.replaceAll("for\\s*\\(\\s*const\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s+of\\b", "for (let $1 of");
        java.util.regex.Pattern classPat = java.util.regex.Pattern.compile(
                "(?m)(^|[;\\s])class\\s+([A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+extends\\s+([A-Za-z_$][A-Za-z0-9_$.]*))?\\s*\\{");
        int guard = 0;
        while (guard++ < 32) {
            java.util.regex.Matcher m = classPat.matcher(out);
            if (!m.find()) break;
            String lead = m.group(1);
            String clsName = m.group(2);
            String baseName = m.group(3);
            int bodyStart = m.end(); // right after '{'
            int bodyEnd = findMatchingBrace(out, bodyStart - 1);
            if (bodyEnd < 0) break;

            String body = out.substring(bodyStart, bodyEnd);
            String desugared = desugarClassBody(clsName, baseName, body);
            out = out.substring(0, m.start()) + lead + desugared + out.substring(bodyEnd + 1);
        }
        return out;
    }

    private static int findMatchingBrace(String s, int openBraceIdx) {
        int depth = 0;
        int n = s.length();
        for (int i = openBraceIdx; i < n; i++) {
            char c = s.charAt(i);
            if (c == '\'' || c == '"' || c == '`') {
                char q = c;
                i++;
                while (i < n && s.charAt(i) != q) {
                    if (s.charAt(i) == '\\') i++;
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n) {
                if (s.charAt(i + 1) == '/') {
                    while (i < n && s.charAt(i) != '\n') i++;
                    continue;
                } else if (s.charAt(i + 1) == '*') {
                    i += 2;
                    while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                    i++;
                    continue;
                }
            }
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static String desugarClassBody(String clsName, String baseName, String body) {
        StringBuilder ctor = new StringBuilder();
        StringBuilder methods = new StringBuilder();
        boolean hasCtor = false;
        int idx = 0;
        int n = body.length();
        java.util.regex.Pattern mPat = java.util.regex.Pattern.compile(
                "\\G[\\s;]*(?:(static)\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*\\{");
        while (idx < n) {
            // Skip comments and whitespace between methods
            while (idx < n && (Character.isWhitespace(body.charAt(idx)) || body.charAt(idx) == ';')) idx++;
            if (idx >= n) break;
            if (body.startsWith("//", idx)) {
                while (idx < n && body.charAt(idx) != '\n') idx++;
                continue;
            }
            if (body.startsWith("/*", idx)) {
                int endC = body.indexOf("*/", idx + 2);
                idx = endC < 0 ? n : endC + 2;
                continue;
            }
            java.util.regex.Matcher mm = mPat.matcher(body);
            if (!mm.find(idx) || mm.start() != idx) break;
            boolean isStatic = mm.group(1) != null;
            String mName = mm.group(2);
            String mArgs = mm.group(3);
            int mOpen = mm.end() - 1;
            int mClose = findMatchingBrace(body, mOpen);
            if (mClose < 0) break;
            String mBody = body.substring(mOpen + 1, mClose);
            if ("constructor".equals(mName) && !isStatic) {
                hasCtor = true;
                String patchedBody = baseName != null
                        ? mBody.replaceAll("\\bsuper\\s*\\(", baseName + ".call(this, ")
                        : mBody;
                ctor.append("function ").append(clsName).append("(").append(mArgs).append(") {").append(patchedBody).append("}\n");
            } else if (isStatic) {
                methods.append(clsName).append(".").append(mName)
                        .append(" = function(").append(mArgs).append(") {").append(mBody).append("};\n");
            } else {
                methods.append(clsName).append(".prototype.").append(mName)
                        .append(" = function(").append(mArgs).append(") {").append(mBody).append("};\n");
            }
            idx = mClose + 1;
        }
        StringBuilder res = new StringBuilder();
        if (!hasCtor) {
            res.append("function ").append(clsName).append("() {")
               .append(baseName != null ? " " + baseName + ".apply(this, arguments); " : "")
               .append("}\n");
        } else {
            res.append(ctor);
        }
        if (baseName != null) {
            res.append(clsName).append(".prototype = Object.create(").append(baseName).append(".prototype);\n");
            res.append(clsName).append(".prototype.constructor = ").append(clsName).append(";\n");
        }
        res.append(methods);
        return res.toString();
    }

    public static String validateSyntax(String langSetting, String code) {
        String mode = detectRuntimeMode(langSetting, code);
        if ("JAVA".equals(mode)) {
            try {
                Interpreter checkInterp = new Interpreter();
                String javaSrc = normalizeJavaForInterpreter(code == null ? "" : code);
                // Wrap inside an inert method parser check so top-level code doesn't execute side-effects during check
                String entryCls = detectJavaEntryClassName(javaSrc);
                if (entryCls != null) {
                    checkInterp.eval(javaSrc);
                } else {
                    checkInterp.eval("class __SyntaxCheckWrap {\n" + javaSrc + "\n}");
                }
                return "OK (Java • Native Vortex Java VM)";
            } catch (EvalError ee) {
                return "Java Syntax Error (Line " + ee.getErrorLineNumber() + "): " + ee.getMessage();
            } catch (Exception ex) {
                return "Java Syntax Error: " + ex.getMessage();
            }
        }
        if ("JS".equals(mode)) {
            Context cx = SANDBOXED_RHINO_FACTORY.enterContext();
            try {
                String jsSrc = transpileModernEs6ForRhino(code == null ? "" : code);
                cx.compileString(jsSrc, "main.js", 1, null);
                return "OK (ES6+ JavaScript • Mozilla Rhino)";
            } catch (RhinoException re) {
                return "JS Syntax Error (Line " + re.lineNumber() + "): " + re.details();
            } catch (Exception ex) {
                return "JS Syntax Error: " + ex.getMessage();
            } finally {
                Context.exit();
            }
        }
        if ("LUA".equals(mode)) {
            try {
                Globals g = JsePlatform.standardGlobals();
                g.load(code == null ? "" : code, "main.lua");
                return "OK (Lua 5.2 • LuaJ)";
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

    public static String validateProjectSyntax(GameProject p) {
        if (p == null) return "OK";
        p.ensureScriptFilesInitialized();
        for (Map.Entry<String, String> entry : p.scriptFiles.entrySet()) {
            String res = validateSyntax(p.scriptLanguage, entry.getValue());
            if (!res.startsWith("OK")) {
                return "[" + entry.getKey() + "] " + res;
            }
        }
        return validateSyntax(p.scriptLanguage, p.getActiveScriptContent());
    }

    public static PlaytestReport runHeadlessPlaytest(GameProject srcProject, int frames) {
        PlaytestReport rep = new PlaytestReport();
        if (srcProject == null) {
            rep.ok = false;
            rep.error = "Null project";
            return rep;
        }
        String syn = validateProjectSyntax(srcProject);
        if (!syn.startsWith("OK")) {
            rep.ok = false;
            rep.error = syn;
            return rep;
        }
        GameProject sandbox = srcProject.deepCopy();
        PhysicsWorld pw = new PhysicsWorld();
        VortexScriptEngine se = new VortexScriptEngine();
        se.attach(sandbox, pw, new HostBridge() {
            @Override public void onLog(String message) { if (rep.logs.size() < 24) rep.logs.add(message); }
            @Override public void onCameraShake(float intensity, float duration) {}
            @Override public void onEmitParticles(float x, float y, float z, int count, String colorHex) {}
            @Override public void onPlaySound(String soundName) {}
            @Override public void onPlayMusic(String pattern, int bpm) {}
            @Override public void onStopMusic() {}
        });
        boolean ok = se.compileProject(sandbox);
        if (!ok) {
            rep.ok = false;
            rep.error = se.getLastError();
            se.dispose();
            return rep;
        }
        int stepCount = Math.max(1, Math.min(240, frames));
        for (int f = 0; f < stepCount; f++) {
            se.update(0.016f);
            List<PhysicsWorld.CollisionEvent> cols = pw.step(sandbox, 0.016f);
            for (PhysicsWorld.CollisionEvent ce : cols) {
                se.notifyCollision(ce.a, ce.b);
            }
            if (!se.compiledOk && se.getLastError() != null) {
                rep.ok = false;
                rep.error = se.getLastError();
                rep.framesSimulated = f + 1;
                se.dispose();
                return rep;
            }
        }
        int aliveCnt = 0;
        for (Entity e : sandbox.entities) {
            if (e.alive) aliveCnt++;
        }
        rep.ok = true;
        rep.framesSimulated = stepCount;
        rep.activeEntities = aliveCnt;
        rep.finalScore = se.score;
        rep.finalHealth = se.health;
        rep.uiWidgetCount = se.uiWidgets.size();
        rep.dialogShown = se.activeDialog.active;
        se.dispose();
        return rep;
    }

    public synchronized void dispose() {
        globals.clear();
        functions.clear();
        jsHookChains.clear();
        luaHookChains.clear();
        javaModuleVars.clear();
        luaGlobals = null;
        rhinoScope = null;
        javaInterpreter = null;
        javaScriptInstance = null;
        uiWidgets.clear();
        activeDialog.reset();
        tweenSystem.reset();
        aiEngine.reset();
        rhinoOpsCounter.remove();
        compiledOk = false;
    }

    public synchronized boolean compileProject(GameProject proj) {
        if (proj == null) return false;
        this.project = proj;
        Map<String, String> modules = proj.getOrderedRuntimeScriptModules();
        if (modules.size() <= 1) {
            String single = modules.isEmpty() ? proj.scriptCode : modules.values().iterator().next();
            return compileAndInit(single);
        }
        return compileMultiFileModules(modules);
    }

    private synchronized boolean compileMultiFileModules(Map<String, String> modules) {
        dispose();
        score = 0;
        health = 100;
        hudBanner = "";
        elapsedTime = 0f;
        lastError = null;

        if (project != null && project.musicEnabled && hostBridge != null) {
            hostBridge.onPlayMusic(project.musicPattern, project.musicBpm);
        }

        String sample = modules.values().iterator().next();
        String lang = project != null ? project.scriptLanguage : "JS";
        activeVmMode = detectRuntimeMode(lang, sample);
        usingLua = "LUA".equals(activeVmMode);
        String[] hookNames = {"onStart", "onUpdate", "onButton", "onCollide"};

        if ("JS".equals(activeVmMode)) {
            Context cx = SANDBOXED_RHINO_FACTORY.enterContext();
            try {
                rhinoOpsCounter.set(0);
                rhinoScope = cx.initSafeStandardObjects();
                registerRhinoEngineApi(cx, rhinoScope);
                for (String hn : hookNames) jsHookChains.put(hn, new ArrayList<>());

                for (Map.Entry<String, String> mod : modules.entrySet()) {
                    for (String hn : hookNames) {
                        ScriptableObject.deleteProperty(rhinoScope, hn);
                    }
                    String jsSrc = transpileModernEs6ForRhino(mod.getValue() == null ? "" : mod.getValue());
                    cx.evaluateString(rhinoScope, jsSrc, mod.getKey(), 1, null);
                    for (String hn : hookNames) {
                        Object prop = ScriptableObject.getProperty(rhinoScope, hn);
                        if (prop instanceof Function) {
                            jsHookChains.get(hn).add((Function) prop);
                        }
                    }
                }
                compiledOk = true;
            } catch (RhinoException re) {
                lastError = re.sourceName() + " Line " + re.lineNumber() + ": " + re.details();
                log("[JS Error] " + lastError);
                return false;
            } catch (Throwable t) {
                lastError = t.getMessage();
                log("[JS Error] " + lastError);
                return false;
            } finally {
                rhinoOpsCounter.remove();
                Context.exit();
            }
            callHook("onStart");
            return compiledOk;
        } else if ("JAVA".equals(activeVmMode)) {
            try {
                javaInterpreter = new Interpreter();
                VortexJavaBridge api = new VortexJavaBridge();
                javaInterpreter.set("vortex", api);
                javaInterpreter.eval(
                        "void print(Object o) { vortex.log(String.valueOf(o)); }\n" +
                        "void println(Object o) { vortex.log(String.valueOf(o)); }\n");

                int modIdx = 0;
                for (Map.Entry<String, String> mod : modules.entrySet()) {
                    String javaSrc = normalizeJavaForInterpreter(mod.getValue() == null ? "" : mod.getValue());
                    if (javaSrc.trim().isEmpty()) continue;
                    String entryCls = detectJavaEntryClassName(javaSrc);
                    String varName = "__modInst_" + (modIdx++);
                    if (entryCls != null) {
                        javaInterpreter.eval(javaSrc);
                        Object inst;
                        try {
                            inst = javaInterpreter.eval("new " + entryCls + "(vortex)");
                        } catch (Throwable ignored) {
                            inst = javaInterpreter.eval("new " + entryCls + "()");
                        }
                        javaInterpreter.set(varName, inst);
                        try { javaInterpreter.eval(varName + ".vortex = vortex;"); } catch (Throwable ignored) {}
                        javaModuleVars.add(varName);
                    } else {
                        String synthCls = "__VortexModClass_" + modIdx;
                        javaInterpreter.eval("public class " + synthCls + " {\n" + javaSrc + "\n}");
                        Object inst = javaInterpreter.eval("new " + synthCls + "()");
                        javaInterpreter.set(varName, inst);
                        javaModuleVars.add(varName);
                    }
                }
                compiledOk = true;
                callHook("onStart");
                return true;
            } catch (EvalError ee) {
                lastError = "Line " + ee.getErrorLineNumber() + ": " + ee.getMessage();
                log("[Java Error] " + lastError);
                return false;
            } catch (Throwable t) {
                lastError = t.getMessage();
                log("[Java Error] " + lastError);
                return false;
            }
        } else if (usingLua) {
            try {
                luaGlobals = JsePlatform.standardGlobals();
                registerLuaEngineApi(luaGlobals);
                registerLuaInMemoryRequire(luaGlobals, modules);
                for (String hn : hookNames) luaHookChains.put(hn, new ArrayList<>());

                for (Map.Entry<String, String> mod : modules.entrySet()) {
                    for (String hn : hookNames) luaGlobals.set(hn, LuaValue.NIL);
                    LuaValue chunk = luaGlobals.load(mod.getValue() == null ? "" : mod.getValue(), mod.getKey());
                    chunk.call();
                    for (String hn : hookNames) {
                        LuaValue fn = luaGlobals.get(hn);
                        if (fn != null && fn.isfunction()) {
                            luaHookChains.get(hn).add(fn);
                        }
                    }
                }
                compiledOk = true;
                callHook("onStart");
                return true;
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log("[Lua Error] " + lastError);
                return false;
            }
        } else {
            StringBuilder joined = new StringBuilder();
            for (String code : modules.values()) {
                if (code != null) joined.append(code).append("\n");
            }
            return compileAndInit(joined.toString());
        }
    }

    private void registerLuaInMemoryRequire(final Globals g, final Map<String, String> modules) {
        try {
            LuaValue pkg = g.get("package");
            if (pkg != null && pkg.istable()) {
                LuaValue preload = pkg.get("preload");
                if (preload != null && preload.istable()) {
                    for (final Map.Entry<String, String> e : modules.entrySet()) {
                        String rawKey = e.getKey().replace('\\', '/');
                        String noExt = rawKey.endsWith(".lua") ? rawKey.substring(0, rawKey.length() - 4) : rawKey;
                        String dotKey = noExt.replace('/', '.');
                        String baseName = noExt.contains("/") ? noExt.substring(noExt.lastIndexOf('/') + 1) : noExt;
                        VarArgFunction loader = new VarArgFunction() {
                            @Override
                            public Varargs invoke(Varargs args) {
                                return g.load(e.getValue() == null ? "" : e.getValue(), e.getKey()).call();
                            }
                        };
                        preload.set(noExt, loader);
                        preload.set(dotKey, loader);
                        preload.set(baseName, loader);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized boolean compileAndInit(String sourceCode) {
        dispose();
        score = 0;
        health = 100;
        hudBanner = "";
        elapsedTime = 0f;
        lastError = null;
        compiledOk = false;

        if (project != null && project.musicEnabled && hostBridge != null) {
            hostBridge.onPlayMusic(project.musicPattern, project.musicBpm);
        }

        String lang = project != null ? project.scriptLanguage : "VXS";
        activeVmMode = detectRuntimeMode(lang, sourceCode);
        usingLua = "LUA".equals(activeVmMode);

        if ("JAVA".equals(activeVmMode)) {
            try {
                javaInterpreter = new Interpreter();
                VortexJavaBridge api = new VortexJavaBridge();
                javaInterpreter.set("vortex", api);
                String javaSrc = normalizeJavaForInterpreter(sourceCode == null ? "" : sourceCode);
                String entryCls = detectJavaEntryClassName(javaSrc);
                javaInterpreter.eval(
                        "void print(Object o) { vortex.log(String.valueOf(o)); }\n" +
                        "void println(Object o) { vortex.log(String.valueOf(o)); }\n");
                javaInterpreter.eval(javaSrc);
                if (entryCls != null) {
                    // Instantiate user's Java game class and bind vortex if field exists
                    try {
                        javaScriptInstance = javaInterpreter.eval("new " + entryCls + "(vortex)");
                    } catch (Throwable ignored) {
                        javaScriptInstance = javaInterpreter.eval("new " + entryCls + "()");
                    }
                    javaInterpreter.set("__gameInstance", javaScriptInstance);
                    try { javaInterpreter.eval("__gameInstance.vortex = vortex;"); } catch (Throwable ignored) {}
                }
                compiledOk = true;
                callHook("onStart");
                return true;
            } catch (EvalError ee) {
                lastError = "Line " + ee.getErrorLineNumber() + ": " + ee.getMessage();
                log("[Java Error] " + lastError);
                return false;
            } catch (Throwable t) {
                lastError = t.getMessage();
                log("[Java Error] " + lastError);
                return false;
            }
        } else if ("JS".equals(activeVmMode)) {
            Context cx = SANDBOXED_RHINO_FACTORY.enterContext();
            try {
                rhinoOpsCounter.set(0);
                rhinoScope = cx.initSafeStandardObjects();
                registerRhinoEngineApi(cx, rhinoScope);
                String jsSrc = transpileModernEs6ForRhino(sourceCode == null ? "" : sourceCode);
                cx.evaluateString(rhinoScope, jsSrc, "main.js", 1, null);
                compiledOk = true;
            } catch (RhinoException re) {
                lastError = "Line " + re.lineNumber() + ": " + re.details();
                log("[JS Error] " + lastError);
                return false;
            } catch (Throwable t) {
                lastError = t.getMessage();
                log("[JS Error] " + lastError);
                return false;
            } finally {
                rhinoOpsCounter.remove();
                Context.exit();
            }
            callHook("onStart");
            return compiledOk;
        } else if (usingLua) {
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

    private void registerRhinoEngineApi(Context cx, Scriptable scope) {
        Scriptable vortexObj = cx.newObject(scope);
        for (final String fnName : ENGINE_API_NAMES) {
            BaseFunction jsFn = new BaseFunction() {
                @Override
                public Object call(Context c, Scriptable sc, Scriptable thisObj, Object[] args) {
                    Object[] jArgs = new Object[args == null ? 0 : args.length];
                    for (int i = 0; i < jArgs.length; i++) {
                        Object raw = args[i];
                        if (raw == null || raw == Undefined.instance) jArgs[i] = null;
                        else if (raw instanceof Boolean) jArgs[i] = raw;
                        else if (raw instanceof Number) jArgs[i] = ((Number) raw).doubleValue();
                        else jArgs[i] = Context.toString(raw);
                    }
                    Object res = callBuiltIn(fnName, jArgs, 0);
                    if (res == null) return Undefined.instance;
                    return res;
                }
            };
            ScriptableObject.putProperty(vortexObj, fnName, jsFn);
            ScriptableObject.putProperty(scope, fnName, jsFn);
        }
        ScriptableObject.putProperty(scope, "vortex", vortexObj);

        BaseFunction printFn = new BaseFunction() {
            @Override
            public Object call(Context c, Scriptable sc, Scriptable thisObj, Object[] args) {
                StringBuilder sb = new StringBuilder();
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        if (i > 0) sb.append(' ');
                        sb.append(Context.toString(args[i]));
                    }
                }
                log(sb.toString());
                return Undefined.instance;
            }
        };
        ScriptableObject.putProperty(scope, "print", printFn);
        Scriptable consoleObj = cx.newObject(scope);
        ScriptableObject.putProperty(consoleObj, "log", printFn);
        ScriptableObject.putProperty(consoleObj, "warn", printFn);
        ScriptableObject.putProperty(consoleObj, "error", printFn);
        ScriptableObject.putProperty(scope, "console", consoleObj);
    }

    private void registerLuaEngineApi(Globals g) {
        LuaTable vortexTbl = new LuaTable();
        String[] apiNames = ENGINE_API_NAMES;

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

    /**
     * Strongly-typed Java Game Scripting API exposed as `vortex` inside `VM: Java`.
     */
    public final class VortexJavaBridge {
        public float joyX() { return inputJoyX; }
        public float joyY() { return inputJoyY; }
        public boolean btnA() { return inputBtnA; }
        public boolean btnB() { return inputBtnB; }
        public boolean isDown(String k) { return "A".equalsIgnoreCase(k) ? inputBtnA : inputBtnB; }
        public boolean exists(String name) { return Boolean.TRUE.equals(callBuiltIn("exists", new Object[]{name}, 0)); }
        public float getX(String n) { return ((Number) callBuiltIn("getX", new Object[]{n}, 0)).floatValue(); }
        public float getY(String n) { return ((Number) callBuiltIn("getY", new Object[]{n}, 0)).floatValue(); }
        public float getZ(String n) { return ((Number) callBuiltIn("getZ", new Object[]{n}, 0)).floatValue(); }
        public float getVX(String n) { return ((Number) callBuiltIn("getVX", new Object[]{n}, 0)).floatValue(); }
        public float getVY(String n) { return ((Number) callBuiltIn("getVY", new Object[]{n}, 0)).floatValue(); }
        public float getVZ(String n) { return ((Number) callBuiltIn("getVZ", new Object[]{n}, 0)).floatValue(); }
        public String getTag(String n) { return String.valueOf(callBuiltIn("getTag", new Object[]{n}, 0)); }
        public boolean isGrounded(String n) { return Boolean.TRUE.equals(callBuiltIn("isGrounded", new Object[]{n}, 0)); }
        public float dist(String a, String b) { return ((Number) callBuiltIn("dist", new Object[]{a, b}, 0)).floatValue(); }
        public int countByTag(String tag) { return ((Number) callBuiltIn("countByTag", new Object[]{tag}, 0)).intValue(); }
        public String findNearestTag(String from, String tag) { return String.valueOf(callBuiltIn("findNearestTag", new Object[]{from, tag}, 0)); }
        public String findNearestTag(String from, String tag, double maxRange) { return String.valueOf(callBuiltIn("findNearestTag", new Object[]{from, tag, maxRange}, 0)); }

        public void setPos(String n, double x, double y) { callBuiltIn("setPos", new Object[]{n, x, y}, 0); }
        public void setPos(String n, double x, double y, double z) { callBuiltIn("setPos", new Object[]{n, x, y, z}, 0); }
        public void setVel(String n, double vx, double vy) { callBuiltIn("setVel", new Object[]{n, vx, vy}, 0); }
        public void setVel(String n, double vx, double vy, double vz) { callBuiltIn("setVel", new Object[]{n, vx, vy, vz}, 0); }
        public void applyImpulse(String n, double ix, double iy) { callBuiltIn("applyImpulse", new Object[]{n, ix, iy}, 0); }
        public void setFixedRotation(String n, boolean fixed) { callBuiltIn("setFixedRotation", new Object[]{n, fixed}, 0); }
        public void jump(String n, double vy) { callBuiltIn("jump", new Object[]{n, vy}, 0); }
        public void setRot(String n, double rx, double ry, double rz) { callBuiltIn("setRot", new Object[]{n, rx, ry, rz}, 0); }
        public void setScale(String n, double sx, double sy, double sz) { callBuiltIn("setScale", new Object[]{n, sx, sy, sz}, 0); }
        public void setColor(String n, String hex) { callBuiltIn("setColor", new Object[]{n, hex}, 0); }
        public void setSpriteAnim(String n, String frames, double fps) { callBuiltIn("setSpriteAnim", new Object[]{n, frames, fps}, 0); }
        public void destroy(String n) { callBuiltIn("destroy", new Object[]{n}, 0); }

        public void spawn2D(String n, String tag, String prim, double x, double y, double sx, double sy, String hex, String body) {
            callBuiltIn("spawn2D", new Object[]{n, tag, prim, x, y, sx, sy, hex, body}, 0);
        }
        public void spawn3D(String n, String tag, String prim, double x, double y, double z, double sx, double sy, double sz, String hex, String body) {
            callBuiltIn("spawn3D", new Object[]{n, tag, prim, x, y, z, sx, sy, sz, hex, body}, 0);
        }
        public void particles(double x, double y, double z, int count, String hex) {
            callBuiltIn("particles", new Object[]{x, y, z, (double) count, hex}, 0);
        }
        public void playSound(String sfx) { callBuiltIn("playSound", new Object[]{sfx}, 0); }
        public void playMusic(String preset, int bpm) { callBuiltIn("playMusic", new Object[]{preset, (double) bpm}, 0); }
        public void stopMusic() { callBuiltIn("stopMusic", new Object[0], 0); }
        public void shake(double intensity, double dur) { callBuiltIn("shake", new Object[]{intensity, dur}, 0); }

        public void chaseAI(String src, String target, double spd) { callBuiltIn("chaseAI", new Object[]{src, target, spd}, 0); }
        public void chaseTagAI(String tag, String target, double spd) { callBuiltIn("chaseTagAI", new Object[]{tag, target, spd}, 0); }
        public String raycast(double ox, double oy, double oz, double dx, double dy, double dz, double maxD) {
            return String.valueOf(callBuiltIn("raycast", new Object[]{ox, oy, oz, dx, dy, dz, maxD}, 0));
        }
        public void tweenTagYoyo(String tag, double dy, double dur, String ease) {
            callBuiltIn("tweenTagYoyo", new Object[]{tag, dy, dur, ease}, 0);
        }
        public void tweenScale(String n, double sx, double sy, double sz, double dur, String ease, int yoyo) {
            callBuiltIn("tweenScale", new Object[]{n, sx, sy, sz, dur, ease, (double) yoyo}, 0);
        }

        public int addScore(int delta) { return ((Number) callBuiltIn("addScore", new Object[]{(double) delta}, 0)).intValue(); }
        public int getScore() { return score; }
        public int addHealth(int delta) { return ((Number) callBuiltIn("addHealth", new Object[]{(double) delta}, 0)).intValue(); }
        public int getHealth() { return health; }
        public void saveData(String k, Object v) { callBuiltIn("saveData", new Object[]{k, String.valueOf(v)}, 0); }
        public String loadData(String k, String def) { return String.valueOf(callBuiltIn("loadData", new Object[]{k, def}, 0)); }
        public float loadNum(String k, double def) { return ((Number) callBuiltIn("loadNum", new Object[]{k, def}, 0)).floatValue(); }
        public void setBanner(String text) { callBuiltIn("setBanner", new Object[]{text}, 0); }
        public void showDialog(String speaker, String msg) { callBuiltIn("showDialog", new Object[]{speaker, msg, 0.0}, 0); }
        public void showDialog(String speaker, String msg, double durSec) { callBuiltIn("showDialog", new Object[]{speaker, msg, durSec}, 0); }
        public void hideDialog() { callBuiltIn("hideDialog", new Object[0], 0); }
        public void setLabel(String id, String text, double xPct, double yPct) { callBuiltIn("setLabel", new Object[]{id, text, xPct, yPct, 15.0, "#FFFFFF"}, 0); }
        public void setLabel(String id, String text, double xPct, double yPct, double fontSize, String hex) { callBuiltIn("setLabel", new Object[]{id, text, xPct, yPct, fontSize, hex}, 0); }
        public void setProgressBar(String id, String label, double val, double max, double xPct, double yPct) { callBuiltIn("setProgressBar", new Object[]{id, label, val, max, xPct, yPct, "#FFFFFF"}, 0); }
        public void setProgressBar(String id, String label, double val, double max, double xPct, double yPct, String hex) { callBuiltIn("setProgressBar", new Object[]{id, label, val, max, xPct, yPct, hex}, 0); }
        public void removeUI(String id) { callBuiltIn("removeUI", new Object[]{id}, 0); }
        public void clearUI() { callBuiltIn("clearUI", new Object[0], 0); }
        public void log(Object msg) { VortexScriptEngine.this.log(String.valueOf(msg)); }
        public float rand(double lo, double hi) { return ((Number) callBuiltIn("rand", new Object[]{lo, hi}, 0)).floatValue(); }
        public float time() { return elapsedTime; }
    }

    public synchronized void update(float dt) {
        elapsedTime += dt;
        if (activeDialog.active) {
            activeDialog.ageSec += dt;
            if (activeDialog.timed) {
                activeDialog.remainingSec -= dt;
                if (activeDialog.remainingSec <= 0f) {
                    activeDialog.active = false;
                }
            }
        }
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
        if (activeDialog.active && !activeDialog.timed && activeDialog.ageSec > 0.25f && "A".equalsIgnoreCase(buttonName)) {
            activeDialog.active = false;
        }
        if (!compiledOk) return;
        callHook("onButton", buttonName);
    }

    public synchronized Object callHook(String fnName, Object... args) {
        if ("JAVA".equals(activeVmMode)) {
            if (javaInterpreter == null) return null;
            try {
                List<String> prefixes = new ArrayList<>();
                if (!javaModuleVars.isEmpty()) {
                    for (String mv : javaModuleVars) prefixes.add(mv + ".");
                } else {
                    prefixes.add(javaScriptInstance != null ? "__gameInstance." : "");
                }

                if ("onUpdate".equals(fnName)) {
                    float dtVal = (args != null && args.length > 0 && args[0] instanceof Number)
                            ? ((Number) args[0]).floatValue() : 0.016f;
                    javaInterpreter.set("__dt", dtVal);
                } else if ("onButton".equals(fnName)) {
                    String btn = (args != null && args.length > 0) ? String.valueOf(args[0]) : "";
                    javaInterpreter.set("__btn", btn);
                } else if ("onCollide".equals(fnName) && args != null && args.length >= 4) {
                    javaInterpreter.set("__ca", String.valueOf(args[0]));
                    javaInterpreter.set("__cb", String.valueOf(args[1]));
                    javaInterpreter.set("__ta", String.valueOf(args[2]));
                    javaInterpreter.set("__tb", String.valueOf(args[3]));
                }

                for (String targetPrefix : prefixes) {
                    try {
                        if ("onStart".equals(fnName)) {
                            javaInterpreter.eval(targetPrefix + "onStart();");
                        } else if ("onUpdate".equals(fnName)) {
                            javaInterpreter.eval(targetPrefix + "onUpdate(__dt);");
                        } else if ("onButton".equals(fnName)) {
                            javaInterpreter.eval(targetPrefix + "onButton(__btn);");
                        } else if ("onCollide".equals(fnName) && args != null && args.length >= 4) {
                            javaInterpreter.eval(targetPrefix + "onCollide(__ca, __cb, __ta, __tb);");
                        }
                    } catch (EvalError ee) {
                        String msg = ee.getMessage();
                        if (msg != null && (msg.contains("Method " + fnName) || msg.contains("not found"))) {
                            continue;
                        }
                        throw ee;
                    }
                }
            } catch (EvalError ee) {
                lastError = "Line " + ee.getErrorLineNumber() + ": " + ee.getMessage();
                log("[Java Runtime Error in " + fnName + "] " + lastError);
                compiledOk = false;
            } catch (Throwable t) {
                lastError = t.getMessage();
                log("[Java Error in " + fnName + "] " + lastError);
                compiledOk = false;
            }
            return null;
        }
        if ("JS".equals(activeVmMode)) {
            if (rhinoScope == null) return null;
            List<Function> chain = jsHookChains.get(fnName);
            List<Function> toCall = new ArrayList<>();
            if (chain != null && !chain.isEmpty()) {
                toCall.addAll(chain);
            } else {
                Object prop = ScriptableObject.getProperty(rhinoScope, fnName);
                if (prop instanceof Function) toCall.add((Function) prop);
            }
            if (toCall.isEmpty()) return null;
            Context cx = SANDBOXED_RHINO_FACTORY.enterContext();
            try {
                Object last = null;
                Object[] safeArgs = args == null ? new Object[0] : args;
                for (Function fn : toCall) {
                    rhinoOpsCounter.set(0);
                    last = fn.call(cx, rhinoScope, rhinoScope, safeArgs);
                }
                return last;
            } catch (RhinoException re) {
                lastError = re.sourceName() + " Line " + re.lineNumber() + ": " + re.details();
                log("[JS Runtime Error in " + fnName + "] " + lastError);
                compiledOk = false;
            } catch (Throwable t) {
                lastError = t.getMessage();
                log("[JS Error in " + fnName + "] " + lastError);
                compiledOk = false;
            } finally {
                rhinoOpsCounter.remove();
                Context.exit();
            }
            return null;
        }
        if (usingLua) {
            if (luaGlobals == null) return null;
            List<LuaValue> chain = luaHookChains.get(fnName);
            List<LuaValue> toCall = new ArrayList<>();
            if (chain != null && !chain.isEmpty()) {
                toCall.addAll(chain);
            } else {
                LuaValue fn = luaGlobals.get(fnName);
                if (fn != null && !fn.isnil() && fn.isfunction()) toCall.add(fn);
            }
            if (toCall.isEmpty()) return null;
            try {
                LuaValue[] lArgs = new LuaValue[args.length];
                for (int i = 0; i < args.length; i++) {
                    Object a = args[i];
                    if (a instanceof Number) lArgs[i] = LuaValue.valueOf(((Number) a).doubleValue());
                    else if (a instanceof Boolean) lArgs[i] = LuaValue.valueOf((Boolean) a);
                    else lArgs[i] = LuaValue.valueOf(String.valueOf(a));
                }
                for (LuaValue fn : toCall) {
                    fn.invoke(lArgs);
                }
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
            // v4.0 Multi-Frame Flipbook Sprite Animation + Nearest Target Lock + Persistent Save Store!
            case "setSpriteAnim": {
                Entity e = project.findByName(str(a, 0));
                if (e != null) {
                    e.spriteFrames = str(a, 1);
                    e.animFps = flt(a, 2, 6.0f);
                }
                return 0.0;
            }
            case "findNearestTag": {
                Entity from = project.findByName(str(a, 0));
                String tag = str(a, 1);
                if (from == null) return "";
                Entity best = null;
                float bestD2 = Float.MAX_VALUE;
                for (int i = 0; i < project.entities.size(); i++) {
                    Entity cand = project.entities.get(i);
                    if (cand.alive && cand != from && tag.equalsIgnoreCase(cand.tag)) {
                        float d2 = (cand.x - from.x) * (cand.x - from.x)
                                 + (cand.y - from.y) * (cand.y - from.y)
                                 + (cand.z - from.z) * (cand.z - from.z);
                        if (d2 < bestD2) {
                            bestD2 = d2;
                            best = cand;
                        }
                    }
                }
                return best != null ? best.name : "";
            }
            case "saveData": {
                VortexSaveStore.save(project != null ? project.getSlug() : "game", str(a, 0), str(a, 1));
                return 0.0;
            }
            case "loadData": {
                String def = a.length > 1 ? str(a, 1) : "";
                return VortexSaveStore.load(project != null ? project.getSlug() : "game", str(a, 0), def);
            }
            case "loadNum": {
                float def = flt(a, 1, 0f);
                String raw = VortexSaveStore.load(project != null ? project.getSlug() : "game", str(a, 0), String.valueOf(def));
                try { return Double.parseDouble(raw); } catch (Exception ex) { return (double) def; }
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
            case "showDialog": {
                activeDialog.speaker = a.length > 0 ? str(a, 0) : "";
                activeDialog.message = a.length > 1 ? str(a, 1) : str(a, 0);
                float dur = a.length > 2 ? flt(a, 2, 0f) : 0f;
                activeDialog.timed = dur > 0.05f;
                activeDialog.remainingSec = dur;
                activeDialog.ageSec = 0f;
                activeDialog.active = true;
                return 0.0;
            }
            case "hideDialog": {
                activeDialog.reset();
                return 0.0;
            }
            case "setLabel": {
                String id = str(a, 0);
                if (id.isEmpty()) id = "label_default";
                UiWidget w = uiWidgets.get(id);
                if (w == null) w = new UiWidget();
                w.id = id;
                w.kind = "LABEL";
                w.text = a.length > 1 ? str(a, 1) : "";
                w.xPct = Math.max(2f, Math.min(98f, flt(a, 2, 50f)));
                w.yPct = Math.max(3f, Math.min(96f, flt(a, 3, 14f)));
                w.fontSize = Math.max(10, Math.min(36, Math.round(flt(a, 4, 15f))));
                w.colorHex = a.length > 5 && !str(a, 5).isEmpty() ? str(a, 5) : "#FFFFFF";
                uiWidgets.put(id, w);
                return 0.0;
            }
            case "setProgressBar": {
                String id = str(a, 0);
                if (id.isEmpty()) id = "bar_default";
                UiWidget w = uiWidgets.get(id);
                if (w == null) w = new UiWidget();
                w.id = id;
                w.kind = "BAR";
                w.text = a.length > 1 ? str(a, 1) : "";
                w.value = flt(a, 2, 100f);
                w.maxValue = Math.max(1f, flt(a, 3, 100f));
                w.xPct = Math.max(5f, Math.min(95f, flt(a, 4, 50f)));
                w.yPct = Math.max(4f, Math.min(95f, flt(a, 5, 10f)));
                w.colorHex = a.length > 6 && !str(a, 6).isEmpty() ? str(a, 6) : "#FFFFFF";
                uiWidgets.put(id, w);
                return 0.0;
            }
            case "removeUI": {
                uiWidgets.remove(str(a, 0));
                return 0.0;
            }
            case "clearUI": {
                uiWidgets.clear();
                activeDialog.reset();
                return 0.0;
            }
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
