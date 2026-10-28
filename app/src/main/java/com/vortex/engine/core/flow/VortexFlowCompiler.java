package com.vortex.engine.core.flow;

import com.vortex.engine.core.model.GameProject;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vortex Flow — Visual Event-Sheet / No-Code Rule Model & Clean Lua 5.2 Code Generator.
 * Inspired by Construct 3 / GDevelop Event Sheets: lets creators build full 2D & 3D games
 * visually with zero typing, AND compiles 1-to-1 into readable Lua 5.2 (`vortex.*`) scripts!
 */
public class VortexFlowCompiler {

    public static final String[] EVENT_TYPES = {
            "ON_START",
            "JOYSTICK_MOVE",
            "ON_BTN_A",
            "ON_BTN_B",
            "ON_COLLIDE",
            "AI_HUNTER_TICK"
    };
    public static final String[] EVENT_KEYS = EVENT_TYPES;

    public static final String[] EVENT_LABELS = {
            "🏁 When Game Starts",
            "🕹 Every Frame: Joystick Controls Player",
            "🅰 When Button A Pressed",
            "🅱 When Button B Pressed",
            "💥 When [Subject Tag] Collides With [Target Tag]",
            "🤖 Every Frame: [Subject Tag] A* Hunts [Target Tag]"
    };

    public static final String[] ACTION_TYPES = {
            "SET_BANNER",
            "SHOW_DIALOG",
            "HIDE_DIALOG",
            "SET_LABEL",
            "SET_PROGRESS_BAR",
            "PLAY_MUSIC",
            "MOVE_2D_PLATFORMER",
            "MOVE_2D_TOPDOWN",
            "MOVE_3D_EXPLORER",
            "JUMP_IMPULSE",
            "SHOOT_LASER_2D",
            "COLLECT_SCORE_DESTROY",
            "DAMAGE_SHAKE",
            "DESTROY_BOTH_EXPLODE",
            "AI_CHASE_PATHFIND",
            "FLOAT_TWEEN_TAG",
            "VICTORY_BANNER"
    };
    public static final String[] ACTION_KEYS = ACTION_TYPES;

    public static final String[] ACTION_LABELS = {
            "📢 Show HUD Banner Text",
            "💬 GUI: Show Dialog Box (Speaker | Message)",
            "✖ GUI: Hide Dialog Box",
            "🏷 GUI: Set Screen Text Label",
            "📊 GUI: Set Progress / HP Bar",
            "🎵 Start Chiptune Music Loop",
            "🏃 Move Player Horizontally (2D Platformer)",
            "🧭 Move Player 4-Way (2D Top-Down / Maze)",
            "🌐 Move Player in 3D World (X / Z)",
            "🚀 Jump Upwards + Jump SFX",
            "⚡ Fire Plasma Laser Projectile + Laser SFX",
            "🪙 Collect Target: +Score + Particles + Coin SFX",
            "🛡 Take Damage: -25 HP + Camera Shake + Hit SFX",
            "💥 Explode Both Objects + +200 Score + Explosion SFX",
            "🧠 Smart A* / Theta* Pathfind Chase Target",
            "✨ Animate Floating Sine Tween on Tag",
            "🏆 Trigger Victory Banner + Fanfare SFX"
    };

    public static class FlowRule {
        public String eventType = "ON_COLLIDE";
        public String subjectTag = "player";
        public String targetTag = "coin";
        public String actionType = "COLLECT_SCORE_DESTROY";
        public float value = 100f;
        public String extra = "#FFD740";

        public FlowRule() {}

        public FlowRule(String eventType, String subjectTag, String targetTag,
                        String actionType, float value, String extra) {
            this.eventType = eventType;
            this.subjectTag = subjectTag;
            this.targetTag = targetTag;
            this.actionType = actionType;
            this.value = value;
            this.extra = extra;
        }

        public FlowRule(String eventType, String subjectTag, String targetTag,
                        String actionType, String param1, String param2) {
            this.eventType = eventType;
            this.subjectTag = (subjectTag == null || subjectTag.isEmpty()) ? param1 : subjectTag;
            this.targetTag = targetTag == null ? "" : targetTag;
            this.actionType = actionType;
            float parsed = 0f;
            try { parsed = Float.parseFloat(param2); }
            catch (Exception e1) {
                try { parsed = Float.parseFloat(param1); } catch (Exception ignored) {}
            }
            this.value = parsed;
            this.extra = (param1 != null && !param1.isEmpty() && !Character.isDigit(param1.charAt(0))) ? param1 : (param2 == null ? "" : param2);
        }

        public String describe() {
            return summaryText();
        }

        public String summaryText() {
            String ev;
            switch (eventType) {
                case "ON_START":       ev = "WHEN Game Starts"; break;
                case "JOYSTICK_MOVE":  ev = "EVERY FRAME (Joystick)"; break;
                case "ON_BTN_A":       ev = "WHEN Button A Pressed"; break;
                case "ON_BTN_B":       ev = "WHEN Button B Pressed"; break;
                case "AI_HUNTER_TICK": ev = "EVERY FRAME AI ['" + subjectTag + "' ➔ '" + targetTag + "']"; break;
                case "ON_COLLIDE":
                default:               ev = "WHEN '" + subjectTag + "' Hits '" + targetTag + "'"; break;
            }
            return ev + "  ➔  " + actionType + " (" + value + ")";
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("eventType", eventType);
                o.put("subjectTag", subjectTag);
                o.put("targetTag", targetTag);
                o.put("actionType", actionType);
                o.put("value", value);
                o.put("extra", extra == null ? "" : extra);
            } catch (JSONException ignored) {
            }
            return o;
        }

        public static FlowRule fromJson(JSONObject o) {
            FlowRule r = new FlowRule();
            r.eventType = o.optString("eventType", "ON_COLLIDE");
            r.subjectTag = o.optString("subjectTag", "player");
            r.targetTag = o.optString("targetTag", "coin");
            r.actionType = o.optString("actionType", "COLLECT_SCORE_DESTROY");
            r.value = (float) o.optDouble("value", 100.0);
            r.extra = o.optString("extra", "");
            return r;
        }
    }

    public static List<FlowRule> createDefaultPlatformerRules(boolean is3D) {
        List<FlowRule> list = new ArrayList<>();
        list.add(new FlowRule("ON_START", "player", "", "SET_BANNER", 0f, "BUILT WITH VORTEX VISUAL FLOW!"));
        list.add(new FlowRule("ON_START", "coin", "", "FLOAT_TWEEN_TAG", 0.65f, "SINE"));
        list.add(new FlowRule("JOYSTICK_MOVE", "Player", "", is3D ? "MOVE_3D_EXPLORER" : "MOVE_2D_PLATFORMER", 9.5f, ""));
        list.add(new FlowRule("ON_BTN_A", "Player", "", "JUMP_IMPULSE", 13.0f, ""));
        list.add(new FlowRule("ON_BTN_B", "Player", "", "SHOOT_LASER_2D", 18.0f, "#00E5FF"));
        list.add(new FlowRule("ON_COLLIDE", "player", "coin", "COLLECT_SCORE_DESTROY", 100f, "#FFD740"));
        list.add(new FlowRule("ON_COLLIDE", "projectile", "enemy", "DESTROY_BOTH_EXPLODE", 250f, "#FF5252"));
        list.add(new FlowRule("ON_COLLIDE", "player", "goal", "VICTORY_BANNER", 500f, "STAGE COMPLETE!"));
        return list;
    }

    public static String compileToJavaScript(GameProject p) {
        if (p == null) return "";
        return compileToJavaScript(p.flowRules, "3D".equalsIgnoreCase(p.dimensionMode));
    }

    public static String compileToJavaScript(List<FlowRule> rules, boolean is3D) {
        StringBuilder startBody = new StringBuilder();
        StringBuilder updateBody = new StringBuilder();
        StringBuilder btnABody = new StringBuilder();
        StringBuilder btnBBody = new StringBuilder();
        StringBuilder collideBody = new StringBuilder();

        for (FlowRule r : rules) {
            String code = emitActionJs(r, is3D);
            String scoped = "  {\n" + indent(code, "  ") + "  }\n";
            switch (r.eventType) {
                case "ON_START":
                    startBody.append(scoped);
                    break;
                case "JOYSTICK_MOVE":
                case "AI_HUNTER_TICK":
                    updateBody.append(scoped);
                    break;
                case "ON_BTN_A":
                    btnABody.append(scoped);
                    break;
                case "ON_BTN_B":
                    btnBBody.append(scoped);
                    break;
                case "ON_COLLIDE":
                    collideBody.append("  if ((tagA === '").append(esc(r.subjectTag))
                            .append("' && tagB === '").append(esc(r.targetTag))
                            .append("') || (tagB === '").append(esc(r.subjectTag))
                            .append("' && tagA === '").append(esc(r.targetTag)).append("')) {\n")
                            .append("    let subj = (tagA === '").append(esc(r.subjectTag)).append("') ? a : b;\n")
                            .append("    let other = (subj === a) ? b : a;\n")
                            .append(indent(code, "  "))
                            .append("  }\n");
                    break;
            }
        }

        return "// Auto-Generated by Vortex Flow Visual Event-Sheet -> ES6+ JavaScript (Mozilla Rhino / Web V8)\n" +
                "let flowShotId = 0;\n\n" +
                "function onStart() {\n" +
                (startBody.length() > 0 ? startBody.toString() : "  // Ready\n") +
                "}\n\n" +
                "function onUpdate(dt) {\n" +
                (updateBody.length() > 0 ? updateBody.toString() : "  // Tick\n") +
                "}\n\n" +
                "function onButton(btn) {\n" +
                (btnABody.length() > 0 ? "  if (btn === 'A') {\n" + indent(btnABody.toString(), "  ") + "  }\n" : "") +
                (btnBBody.length() > 0 ? "  if (btn === 'B') {\n" + indent(btnBBody.toString(), "  ") + "  }\n" : "") +
                "}\n\n" +
                "function onCollide(a, b, tagA, tagB) {\n" +
                collideBody.toString() +
                "}\n";
    }

    private static String emitActionJs(FlowRule r, boolean is3D) {
        String ext = (r.extra == null || r.extra.isEmpty()) ? "#00E5FF" : esc(r.extra);
        float v = r.value;
        switch (r.actionType) {
            case "SET_BANNER":
                return "  vortex.setBanner('" + ext + "');\n";
            case "SHOW_DIALOG":
                return "  vortex.showDialog('GUIDE', '" + ext + "', " + (v <= 0 ? 4.0f : v) + ");\n";
            case "HIDE_DIALOG":
                return "  vortex.hideDialog();\n";
            case "SET_LABEL":
                return "  vortex.setLabel('flow_lbl', '" + ext + "', 50, " + (v <= 0 ? 15f : v) + ", 15, '#FFFFFF');\n";
            case "SET_PROGRESS_BAR":
                return "  vortex.setProgressBar('flow_bar', '" + ext + "', " + (v <= 0 ? 100f : v) + ", 100, 50, 10, '#FFFFFF');\n";
            case "PLAY_MUSIC":
                return "  vortex.playMusic('" + (r.extra.isEmpty() ? "CYBERPUNK" : esc(r.extra)) + "');\n";
            case "MOVE_2D_PLATFORMER":
                return "  if (vortex.exists('Player')) vortex.setVel('Player', vortex.joyX() * " + v + ", vortex.getVY('Player'));\n";
            case "MOVE_2D_TOPDOWN":
                return "  if (vortex.exists('Player')) vortex.setVel('Player', vortex.joyX() * " + v + ", vortex.joyY() * " + v + ");\n";
            case "MOVE_3D_EXPLORER":
                return "  if (vortex.exists('Player')) vortex.setVel('Player', vortex.joyX() * " + v + ", vortex.getVY('Player'), -vortex.joyY() * " + v + ");\n";
            case "JUMP_IMPULSE":
                return "  vortex.jump('Player', " + (v <= 0 ? 12.5f : v) + ");\n";
            case "SHOOT_LASER_2D":
                return "  if (vortex.exists('Player')) {\n" +
                       "    let bName = `Bolt_${++flowShotId}`;\n" +
                       "    vortex.spawn2D(bName, 'projectile', 'RECT', vortex.getX('Player') + 0.8, vortex.getY('Player'), 0.65, 0.25, '" + ext + "', 'KINEMATIC');\n" +
                       "    vortex.setVel(bName, " + (v <= 0 ? 18f : v) + ", 0);\n" +
                       "    vortex.playSound('laser');\n" +
                       "  }\n";
            case "COLLECT_SCORE_DESTROY":
                return "  vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 22, '" + ext + "');\n" +
                       "  vortex.destroy(other);\n" +
                       "  vortex.addScore(" + Math.round(v <= 0 ? 100 : v) + ");\n" +
                       "  vortex.playSound('coin');\n";
            case "DAMAGE_SHAKE":
                return "  vortex.shake(0.38, 0.25);\n" +
                       "  vortex.addHealth(-" + Math.round(v <= 0 ? 25 : v) + ");\n" +
                       "  vortex.playSound('hit');\n";
            case "DESTROY_BOTH_EXPLODE":
                return "  vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 28, '" + ext + "');\n" +
                       "  vortex.destroy(subj); vortex.destroy(other);\n" +
                       "  vortex.addScore(" + Math.round(v <= 0 ? 200 : v) + ");\n" +
                       "  vortex.playSound('explosion');\n";
            case "AI_CHASE_PATHFIND":
                return "  vortex.chaseTagAI('" + esc(r.subjectTag) + "', 'Player', " + (v <= 0 ? 4.5f : v) + ");\n";
            case "FLOAT_TWEEN_TAG":
                return "  vortex.tweenTagYoyo('" + esc(r.subjectTag) + "', " + (v <= 0 ? 0.5f : v) + ", 1.1, 'SINE');\n";
            case "VICTORY_BANNER":
            default:
                return "  vortex.addScore(" + Math.round(v) + ");\n" +
                       "  vortex.playSound('powerup');\n" +
                       "  vortex.setBanner(`" + (r.extra.isEmpty() ? "VICTORY!" : esc(r.extra)) + " — SCORE: ${vortex.getScore()}`);\n";
        }
    }

    public static String compileToJava(GameProject p) {
        if (p == null) return "";
        return compileToJava(p.flowRules, "3D".equalsIgnoreCase(p.dimensionMode));
    }

    public static String compileToJava(List<FlowRule> rules, boolean is3D) {
        StringBuilder startBody = new StringBuilder();
        StringBuilder updateBody = new StringBuilder();
        StringBuilder btnABody = new StringBuilder();
        StringBuilder btnBBody = new StringBuilder();
        StringBuilder collideBody = new StringBuilder();

        for (FlowRule r : rules) {
            String code = emitActionJava(r, is3D);
            String scoped = "    {\n" + indent(code, "    ") + "    }\n";
            switch (r.eventType) {
                case "ON_START":
                    startBody.append(scoped);
                    break;
                case "JOYSTICK_MOVE":
                case "AI_HUNTER_TICK":
                    updateBody.append(scoped);
                    break;
                case "ON_BTN_A":
                    btnABody.append(scoped);
                    break;
                case "ON_BTN_B":
                    btnBBody.append(scoped);
                    break;
                case "ON_COLLIDE":
                    collideBody.append("    if ((tagA.equals(\"").append(esc(r.subjectTag))
                            .append("\") && tagB.equals(\"").append(esc(r.targetTag))
                            .append("\")) || (tagB.equals(\"").append(esc(r.subjectTag))
                            .append("\") && tagA.equals(\"").append(esc(r.targetTag)).append("\"))) {\n")
                            .append("        String subj = tagA.equals(\"").append(esc(r.subjectTag)).append("\") ? a : b;\n")
                            .append("        String other = subj.equals(a) ? b : a;\n")
                            .append(indent(code, "    "))
                            .append("    }\n");
                    break;
            }
        }

        return "// Auto-Generated by Vortex Flow Visual Event-Sheet -> Native Java\n" +
                "int flowShotId = 0;\n\n" +
                "public void onStart() {\n" +
                (startBody.length() > 0 ? startBody.toString() : "    // Ready\n") +
                "}\n\n" +
                "public void onUpdate(double dt) {\n" +
                (updateBody.length() > 0 ? updateBody.toString() : "    // Tick\n") +
                "}\n\n" +
                "public void onButton(String btn) {\n" +
                (btnABody.length() > 0 ? "    if (\"A\".equals(btn)) {\n" + indent(btnABody.toString(), "    ") + "    }\n" : "") +
                (btnBBody.length() > 0 ? "    if (\"B\".equals(btn)) {\n" + indent(btnBBody.toString(), "    ") + "    }\n" : "") +
                "}\n\n" +
                "public void onCollide(String a, String b, String tagA, String tagB) {\n" +
                collideBody.toString() +
                "}\n";
    }

    private static String emitActionJava(FlowRule r, boolean is3D) {
        String ext = (r.extra == null || r.extra.isEmpty()) ? "#FFFFFF" : esc(r.extra);
        float v = r.value;
        switch (r.actionType) {
            case "SET_BANNER":
                return "    vortex.setBanner(\"" + ext + "\");\n";
            case "SHOW_DIALOG":
                return "    vortex.showDialog(\"GUIDE\", \"" + ext + "\", " + (v <= 0 ? 4.0f : v) + ");\n";
            case "HIDE_DIALOG":
                return "    vortex.hideDialog();\n";
            case "SET_LABEL":
                return "    vortex.setLabel(\"flow_lbl\", \"" + ext + "\", 50, " + (v <= 0 ? 15f : v) + ", 15, \"#FFFFFF\");\n";
            case "SET_PROGRESS_BAR":
                return "    vortex.setProgressBar(\"flow_bar\", \"" + ext + "\", " + (v <= 0 ? 100f : v) + ", 100, 50, 10, \"#FFFFFF\");\n";
            case "PLAY_MUSIC":
                return "    vortex.playMusic(\"" + (r.extra.isEmpty() ? "CYBERPUNK" : esc(r.extra)) + "\");\n";
            case "MOVE_2D_PLATFORMER":
                return "    if (vortex.exists(\"Player\")) vortex.setVel(\"Player\", vortex.joyX() * " + v + ", vortex.getVY(\"Player\"));\n";
            case "MOVE_2D_TOPDOWN":
                return "    if (vortex.exists(\"Player\")) vortex.setVel(\"Player\", vortex.joyX() * " + v + ", vortex.joyY() * " + v + ");\n";
            case "MOVE_3D_EXPLORER":
                return "    if (vortex.exists(\"Player\")) vortex.setVel(\"Player\", vortex.joyX() * " + v + ", vortex.getVY(\"Player\"), -vortex.joyY() * " + v + ");\n";
            case "JUMP_IMPULSE":
                return "    vortex.jump(\"Player\", " + (v <= 0 ? 12.5f : v) + ");\n";
            case "SHOOT_LASER_2D":
                return "    if (vortex.exists(\"Player\")) {\n" +
                       "        String bName = \"Bolt_\" + (++flowShotId);\n" +
                       "        vortex.spawn2D(bName, \"projectile\", \"RECT\", vortex.getX(\"Player\") + 0.8, vortex.getY(\"Player\"), 0.65, 0.25, \"" + ext + "\", \"KINEMATIC\");\n" +
                       "        vortex.setVel(bName, " + (v <= 0 ? 18f : v) + ", 0);\n" +
                       "        vortex.playSound(\"laser\");\n" +
                       "    }\n";
            case "COLLECT_SCORE_DESTROY":
                return "    vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 22, \"" + ext + "\");\n" +
                       "    vortex.destroy(other);\n" +
                       "    vortex.addScore(" + Math.round(v <= 0 ? 100 : v) + ");\n" +
                       "    vortex.playSound(\"coin\");\n";
            case "DAMAGE_SHAKE":
                return "    vortex.shake(0.38, 0.25);\n" +
                       "    vortex.addHealth(-" + Math.round(v <= 0 ? 25 : v) + ");\n" +
                       "    vortex.playSound(\"hit\");\n";
            case "DESTROY_BOTH_EXPLODE":
                return "    vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 28, \"" + ext + "\");\n" +
                       "    vortex.destroy(subj); vortex.destroy(other);\n" +
                       "    vortex.addScore(" + Math.round(v <= 0 ? 200 : v) + ");\n" +
                       "    vortex.playSound(\"explosion\");\n";
            case "AI_CHASE_PATHFIND":
                return "    vortex.chaseTagAI(\"" + esc(r.subjectTag) + "\", \"Player\", " + (v <= 0 ? 4.5f : v) + ");\n";
            case "FLOAT_TWEEN_TAG":
                return "    vortex.tweenTagYoyo(\"" + esc(r.subjectTag) + "\", " + (v <= 0 ? 0.5f : v) + ", 1.1, \"SINE\");\n";
            case "VICTORY_BANNER":
            default:
                return "    vortex.addScore(" + Math.round(v) + ");\n" +
                       "    vortex.playSound(\"powerup\");\n" +
                       "    vortex.setBanner(\"" + (r.extra.isEmpty() ? "VICTORY!" : esc(r.extra)) + " — SCORE: \" + vortex.getScore());\n";
        }
    }

    public static String compileToLua(GameProject p) {
        if (p == null) return "";
        return compileToLua(p.flowRules, "3D".equalsIgnoreCase(p.dimensionMode));
    }

    public static String compileToLua(List<FlowRule> rules, boolean is3D) {
        StringBuilder startBody = new StringBuilder();
        StringBuilder updateBody = new StringBuilder();
        StringBuilder btnABody = new StringBuilder();
        StringBuilder btnBBody = new StringBuilder();
        StringBuilder collideBody = new StringBuilder();

        for (FlowRule r : rules) {
            String code = emitActionLua(r, is3D);
            switch (r.eventType) {
                case "ON_START":
                    startBody.append(code);
                    break;
                case "JOYSTICK_MOVE":
                case "AI_HUNTER_TICK":
                    updateBody.append(code);
                    break;
                case "ON_BTN_A":
                    btnABody.append(code);
                    break;
                case "ON_BTN_B":
                    btnBBody.append(code);
                    break;
                case "ON_COLLIDE":
                    collideBody.append("    if (tagA == '").append(esc(r.subjectTag))
                            .append("' and tagB == '").append(esc(r.targetTag))
                            .append("') or (tagB == '").append(esc(r.subjectTag))
                            .append("' and tagA == '").append(esc(r.targetTag)).append("') then\n")
                            .append("        local subj = (tagA == '").append(esc(r.subjectTag)).append("') and a or b\n")
                            .append("        local other = (subj == a) and b or a\n")
                            .append(indent(code, "    "))
                            .append("    end\n");
                    break;
            }
        }

        return "-- Auto-Generated by Vortex Flow Visual Event-Sheet Builder (Lua 5.2)\n" +
                "-- You can freely customize this Lua script or add more Flow blocks!\n" +
                "local flowShotId = 0\n\n" +
                "function onStart()\n" +
                (startBody.length() > 0 ? startBody.toString() : "    -- Ready\n") +
                "end\n\n" +
                "function onUpdate(dt)\n" +
                (updateBody.length() > 0 ? updateBody.toString() : "    -- Tick\n") +
                "end\n\n" +
                "function onButton(btn)\n" +
                (btnABody.length() > 0 ? "    if btn == 'A' then\n" + indent(btnABody.toString(), "    ") + "    end\n" : "") +
                (btnBBody.length() > 0 ? "    if btn == 'B' then\n" + indent(btnBBody.toString(), "    ") + "    end\n" : "") +
                "end\n\n" +
                "function onCollide(a, b, tagA, tagB)\n" +
                collideBody.toString() +
                "end\n";
    }

    private static String emitActionLua(FlowRule r, boolean is3D) {
        String ext = (r.extra == null || r.extra.isEmpty()) ? "#00E5FF" : esc(r.extra);
        float v = r.value;
        switch (r.actionType) {
            case "SET_BANNER":
                return "    vortex.setBanner('" + ext + "')\n";
            case "SHOW_DIALOG":
                return "    vortex.showDialog('GUIDE', '" + ext + "', " + (v <= 0 ? 4.0f : v) + ")\n";
            case "HIDE_DIALOG":
                return "    vortex.hideDialog()\n";
            case "SET_LABEL":
                return "    vortex.setLabel('flow_lbl', '" + ext + "', 50, " + (v <= 0 ? 15f : v) + ", 15, '#FFFFFF')\n";
            case "SET_PROGRESS_BAR":
                return "    vortex.setProgressBar('flow_bar', '" + ext + "', " + (v <= 0 ? 100f : v) + ", 100, 50, 10, '#FFFFFF')\n";
            case "PLAY_MUSIC":
                return "    vortex.playMusic('" + (r.extra.isEmpty() ? "CYBERPUNK" : esc(r.extra)) + "')\n";
            case "MOVE_2D_PLATFORMER":
                return "    if vortex.exists('Player') then\n" +
                       "        vortex.setVel('Player', vortex.joyX() * " + v + ", vortex.getVY('Player'))\n" +
                       "    end\n";
            case "MOVE_2D_TOPDOWN":
                return "    if vortex.exists('Player') then\n" +
                       "        vortex.setVel('Player', vortex.joyX() * " + v + ", vortex.joyY() * " + v + ")\n" +
                       "    end\n";
            case "MOVE_3D_EXPLORER":
                return "    if vortex.exists('Player') then\n" +
                       "        vortex.setVel('Player', vortex.joyX() * " + v + ", vortex.getVY('Player'), -vortex.joyY() * " + v + ")\n" +
                       "    end\n";
            case "JUMP_IMPULSE":
                return "    vortex.jump('Player', " + (v <= 0 ? 12.5f : v) + ")\n";
            case "SHOOT_LASER_2D":
                return "    if vortex.exists('Player') then\n" +
                       "        flowShotId = flowShotId + 1\n" +
                       "        local bName = 'Bolt_' .. flowShotId\n" +
                       "        vortex.spawn2D(bName, 'projectile', 'RECT', vortex.getX('Player') + 0.8, vortex.getY('Player'), 0.65, 0.25, '" + ext + "', 'KINEMATIC')\n" +
                       "        vortex.setVel(bName, " + (v <= 0 ? 18f : v) + ", 0)\n" +
                       "        vortex.playSound('laser')\n" +
                       "    end\n";
            case "COLLECT_SCORE_DESTROY":
                return "    vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 22, '" + ext + "')\n" +
                       "    vortex.destroy(other)\n" +
                       "    vortex.addScore(" + Math.round(v <= 0 ? 100 : v) + ")\n" +
                       "    vortex.playSound('coin')\n";
            case "DAMAGE_SHAKE":
                return "    vortex.shake(0.38, 0.25)\n" +
                       "    vortex.addHealth(-" + Math.round(v <= 0 ? 25 : v) + ")\n" +
                       "    vortex.playSound('hit')\n";
            case "DESTROY_BOTH_EXPLODE":
                return "    vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 28, '" + ext + "')\n" +
                       "    vortex.destroy(subj)\n" +
                       "    vortex.destroy(other)\n" +
                       "    vortex.addScore(" + Math.round(v <= 0 ? 200 : v) + ")\n" +
                       "    vortex.playSound('explosion')\n";
            case "AI_CHASE_PATHFIND":
                return "    vortex.chaseTagAI('" + esc(r.subjectTag) + "', 'Player', " + (v <= 0 ? 4.5f : v) + ")\n";
            case "FLOAT_TWEEN_TAG":
                return "    vortex.tweenTagYoyo('" + esc(r.subjectTag) + "', " + (v <= 0 ? 0.5f : v) + ", 1.1, 'SINE')\n";
            case "VICTORY_BANNER":
            default:
                return "    vortex.addScore(" + Math.round(v) + ")\n" +
                       "    vortex.playSound('powerup')\n" +
                       "    vortex.setBanner('" + (r.extra.isEmpty() ? "VICTORY!" : esc(r.extra)) + " — SCORE: ' .. vortex.getScore())\n";
        }
    }

    private static String indent(String block, String pad) {
        String[] lines = block.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String ln : lines) {
            if (!ln.isEmpty()) sb.append(pad).append(ln).append("\n");
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "").replace("'", "");
    }

    public static JSONArray toJsonArray(List<FlowRule> list) {
        JSONArray arr = new JSONArray();
        if (list != null) {
            for (FlowRule r : list) arr.put(r.toJson());
        }
        return arr;
    }

    public static List<FlowRule> fromJsonArray(JSONArray arr) {
        List<FlowRule> out = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) out.add(FlowRule.fromJson(o));
            }
        }
        return out;
    }
}
