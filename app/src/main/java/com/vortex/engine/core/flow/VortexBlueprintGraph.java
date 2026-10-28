package com.vortex.engine.core.flow;

import com.vortex.engine.core.model.GameProject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vortex Blueprint Graph — Visual Node-Based Event & GUI Scripting Diagram (Unreal Blueprints style).
 *
 * Complements the existing Vortex Flow Event-Sheet tab with a visual block-and-wire canvas:
 *  - Event Nodes (On Start, On Tick, On Button A/B, On Collision)
 *  - GUI / Dialog / Label / Progress Bar Blocks (Show Dialog Box, Hide Dialog, Set Text Label, Set HP/Progress Bar, Clear UI)
 *  - Gameplay / Physics / AI / Audio Blocks (Move Joystick, Jump, Shoot, Chase AI, Modify Score/HP, Destroy, Play SFX)
 *  - Branch / Condition Blocks (If Score >= N, If Tag Count == 0 -> True / False wires)
 *
 * Compiles cleanly to ES6 JavaScript, Native Java, or Lua 5.2, AND can execute side-by-side
 * with multi-file code scripts without overwriting user code!
 */
public class VortexBlueprintGraph {

    public static final String CAT_EVENT  = "EVENT";
    public static final String CAT_GUI    = "GUI";
    public static final String CAT_ACTION = "ACTION";
    public static final String CAT_BRANCH = "BRANCH";

    public static final String[][] NODE_PALETTE = {
            // { typeKey, category, title, defaultParam1, defaultParam2, defaultParam3 }
            {"EV_START",        CAT_EVENT,  "Event: On Game Start",     "",           "",                          ""},
            {"EV_TICK",         CAT_EVENT,  "Event: Every Frame (Tick)","",           "",                          ""},
            {"EV_BTN_A",        CAT_EVENT,  "Event: Button A Pressed",  "A",          "",                          ""},
            {"EV_BTN_B",        CAT_EVENT,  "Event: Button B Pressed",  "B",          "",                          ""},
            {"EV_COLLIDE",      CAT_EVENT,  "Event: On Collision",      "player",     "coin",                      ""},

            // GUI / In-Game Text / Dialog / Visual Chart & Progress Bar Blocks
            {"GUI_SHOW_DIALOG", CAT_GUI,    "GUI: Show Dialog Box",     "GUIDE",      "Welcome! Collect all cores.", "4.0"},
            {"GUI_HIDE_DIALOG", CAT_GUI,    "GUI: Hide Dialog Box",     "",           "",                          ""},
            {"GUI_SET_LABEL",   CAT_GUI,    "GUI: Set Text Label",      "quest_lbl",  "Objective: Clear Wave",     "50,14"},
            {"GUI_SET_BAR",     CAT_GUI,    "GUI: Set Progress/HP Bar", "boss_bar",   "SHIELD",                    "100,100"},
            {"GUI_SET_BANNER",  CAT_GUI,    "GUI: Set Top Banner",      "",           "STAGE 1 READY",             ""},
            {"GUI_CLEAR_UI",    CAT_GUI,    "GUI: Clear UI Element",    "*",          "",                          ""},

            // Gameplay, Physics & Audio Blocks
            {"ACT_MOVE_JOY",    CAT_ACTION, "Move: Joystick Drive",     "Player",     "8.5",                       "AUTO"},
            {"ACT_JUMP",        CAT_ACTION, "Physics: Jump Impulse",    "Player",     "12.5",                      ""},
            {"ACT_SHOOT",       CAT_ACTION, "Combat: Fire Projectile",  "Player",     "18.0",                      "#FFFFFF"},
            {"ACT_CHASE_AI",    CAT_ACTION, "AI: Chase Target Tag",     "enemy",      "Player",                    "4.2"},
            {"ACT_ADD_SCORE",   CAT_ACTION, "Stat: Add Score",          "100",        "",                          ""},
            {"ACT_ADD_HP",      CAT_ACTION, "Stat: Modify Health",      "-20",        "",                          ""},
            {"ACT_DESTROY_OTH", CAT_ACTION, "Scene: Destroy Other",     "coin",       "#FFFFFF",                   ""},
            {"ACT_PLAY_SFX",    CAT_ACTION, "Audio: Play Sound FX",     "coin",       "",                          ""},

            // Branch / Visual Logic Blocks
            {"BR_SCORE_GTE",    CAT_BRANCH, "Branch: Score >= Value",   "300",        "",                          ""},
            {"BR_TAG_ZERO",     CAT_BRANCH, "Branch: Tag Count == 0",   "coin",       "",                          ""}
    };

    public static class BpNode {
        public String id;
        public String type = "EV_START";
        public String title = "Event: On Game Start";
        public float x = 60f;
        public float y = 80f;
        public String param1 = "";
        public String param2 = "";
        public String param3 = "";

        public BpNode() {
            this.id = "bp_" + UUID.randomUUID().toString().substring(0, 6);
        }

        public BpNode(String type, String title, float x, float y, String p1, String p2, String p3) {
            this();
            this.type = type;
            this.title = title;
            this.x = x;
            this.y = y;
            this.param1 = p1 == null ? "" : p1;
            this.param2 = p2 == null ? "" : p2;
            this.param3 = p3 == null ? "" : p3;
        }

        public String getCategory() {
            if (type.startsWith("EV_")) return CAT_EVENT;
            if (type.startsWith("GUI_")) return CAT_GUI;
            if (type.startsWith("BR_")) return CAT_BRANCH;
            return CAT_ACTION;
        }

        public boolean hasExecIn() {
            return !type.startsWith("EV_");
        }

        public boolean isBranch() {
            return type.startsWith("BR_");
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("type", type);
                o.put("title", title);
                o.put("x", x);
                o.put("y", y);
                o.put("param1", param1);
                o.put("param2", param2);
                o.put("param3", param3);
            } catch (JSONException ignored) {
            }
            return o;
        }

        public static BpNode fromJson(JSONObject o) {
            BpNode n = new BpNode();
            n.id = o.optString("id", n.id);
            n.type = o.optString("type", "EV_START");
            n.title = o.optString("title", n.type);
            n.x = (float) o.optDouble("x", 60.0);
            n.y = (float) o.optDouble("y", 80.0);
            n.param1 = o.optString("param1", "");
            n.param2 = o.optString("param2", "");
            n.param3 = o.optString("param3", "");
            return n;
        }
    }

    public static class BpLink {
        public String fromNodeId;
        public String fromPin = "OUT"; // "OUT", "TRUE", "FALSE"
        public String toNodeId;

        public BpLink() {}

        public BpLink(String fromNodeId, String fromPin, String toNodeId) {
            this.fromNodeId = fromNodeId;
            this.fromPin = (fromPin == null || fromPin.isEmpty()) ? "OUT" : fromPin;
            this.toNodeId = toNodeId;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("fromNodeId", fromNodeId);
                o.put("fromPin", fromPin);
                o.put("toNodeId", toNodeId);
            } catch (JSONException ignored) {
            }
            return o;
        }

        public static BpLink fromJson(JSONObject o) {
            return new BpLink(
                    o.optString("fromNodeId", ""),
                    o.optString("fromPin", "OUT"),
                    o.optString("toNodeId", ""));
        }
    }

    public static BpNode createFromPalette(int idx, float x, float y) {
        int safe = Math.max(0, Math.min(NODE_PALETTE.length - 1, idx));
        String[] row = NODE_PALETTE[safe];
        return new BpNode(row[0], row[2], x, y, row[3], row[4], row[5]);
    }

    public static BpNode createByType(String typeKey, float x, float y) {
        for (int i = 0; i < NODE_PALETTE.length; i++) {
            if (NODE_PALETTE[i][0].equalsIgnoreCase(typeKey)) {
                return createFromPalette(i, x, y);
            }
        }
        return createFromPalette(0, x, y);
    }

    public static void populateStarterDialogGraph(GameProject p) {
        if (p == null || !p.blueprintNodes.isEmpty()) return;
        BpNode evStart = new BpNode("EV_START", "Event: On Game Start", 36f, 68f, "", "", "");
        BpNode dlg = new BpNode("GUI_SHOW_DIALOG", "GUI: Show Dialog Box", 274f, 56f,
                "OPERATOR", "Systems online. Use WASD/Joystick to move and Space/A for action.", "4.5");
        BpNode lbl = new BpNode("GUI_SET_LABEL", "GUI: Set Text Label", 512f, 56f,
                "obj_hint", "OBJECTIVE: SURVIVE", "84,6");
        p.blueprintNodes.add(evStart);
        p.blueprintNodes.add(dlg);
        p.blueprintNodes.add(lbl);
        p.blueprintLinks.add(new BpLink(evStart.id, "OUT", dlg.id));
        p.blueprintLinks.add(new BpLink(dlg.id, "OUT", lbl.id));
    }

    public static JSONArray nodesToJson(List<BpNode> nodes) {
        JSONArray a = new JSONArray();
        if (nodes != null) {
            for (BpNode n : nodes) a.put(n.toJson());
        }
        return a;
    }

    public static List<BpNode> nodesFromJson(JSONArray a) {
        List<BpNode> out = new ArrayList<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(BpNode.fromJson(o));
            }
        }
        return out;
    }

    public static JSONArray linksToJson(List<BpLink> links) {
        JSONArray a = new JSONArray();
        if (links != null) {
            for (BpLink l : links) a.put(l.toJson());
        }
        return a;
    }

    public static List<BpLink> linksFromJson(JSONArray a) {
        List<BpLink> out = new ArrayList<>();
        if (a != null) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(BpLink.fromJson(o));
            }
        }
        return out;
    }

    // =========================================================================
    // Polyglot Blueprint Graph Code Generators (ES6 JS, Native Java, Lua 5.2)
    // =========================================================================

    public static String compileGraph(GameProject p, String targetLang) {
        if (p == null || p.blueprintNodes.isEmpty()) return "";
        boolean is3D = "3D".equalsIgnoreCase(p.dimensionMode);
        if ("JAVA".equalsIgnoreCase(targetLang)) {
            return compileToJava(p.blueprintNodes, p.blueprintLinks, is3D);
        }
        if ("LUA".equalsIgnoreCase(targetLang)) {
            return compileToLua(p.blueprintNodes, p.blueprintLinks, is3D);
        }
        return compileToJs(p.blueprintNodes, p.blueprintLinks, is3D);
    }

    public static String compileToJs(List<BpNode> nodes, List<BpLink> links, boolean is3D) {
        StringBuilder onStart = new StringBuilder();
        StringBuilder onUpdate = new StringBuilder();
        StringBuilder onBtnA = new StringBuilder();
        StringBuilder onBtnB = new StringBuilder();
        StringBuilder onCollide = new StringBuilder();

        for (BpNode n : nodes) {
            switch (n.type) {
                case "EV_START":
                    emitChainJs(n.id, "OUT", nodes, links, is3D, onStart, "  ", new HashSet<>());
                    break;
                case "EV_TICK":
                    emitChainJs(n.id, "OUT", nodes, links, is3D, onUpdate, "  ", new HashSet<>());
                    break;
                case "EV_BTN_A":
                    emitChainJs(n.id, "OUT", nodes, links, is3D, onBtnA, "    ", new HashSet<>());
                    break;
                case "EV_BTN_B":
                    emitChainJs(n.id, "OUT", nodes, links, is3D, onBtnB, "    ", new HashSet<>());
                    break;
                case "EV_COLLIDE": {
                    String tA = esc(n.param1.isEmpty() ? "player" : n.param1);
                    String tB = esc(n.param2.isEmpty() ? "coin" : n.param2);
                    StringBuilder body = new StringBuilder();
                    emitChainJs(n.id, "OUT", nodes, links, is3D, body, "    ", new HashSet<>());
                    if (body.length() > 0) {
                        onCollide.append("  if ((tagA === '").append(tA).append("' && tagB === '").append(tB)
                                .append("') || (tagB === '").append(tA).append("' && tagA === '").append(tB).append("')) {\n")
                                .append("    const bpSubj = (tagA === '").append(tA).append("') ? a : b;\n")
                                .append("    const bpOther = (bpSubj === a) ? b : a;\n")
                                .append(body)
                                .append("  }\n");
                    }
                    break;
                }
                default:
                    break;
            }
        }

        StringBuilder out = new StringBuilder("// Generated from Vortex Visual Blueprint Graph (ES6 JS)\n");
        out.append("let __bpShotSeq = 0;\n");
        if (onStart.length() > 0) out.append("function onStart() {\n").append(onStart).append("}\n");
        if (onUpdate.length() > 0) out.append("function onUpdate(dt) {\n").append(onUpdate).append("}\n");
        if (onBtnA.length() > 0 || onBtnB.length() > 0) {
            out.append("function onButton(btn) {\n");
            if (onBtnA.length() > 0) out.append("  if (btn === 'A') {\n").append(onBtnA).append("  }\n");
            if (onBtnB.length() > 0) out.append("  if (btn === 'B') {\n").append(onBtnB).append("  }\n");
            out.append("}\n");
        }
        if (onCollide.length() > 0) out.append("function onCollide(a, b, tagA, tagB) {\n").append(onCollide).append("}\n");
        return out.toString();
    }

    private static void emitChainJs(String fromId, String pin, List<BpNode> nodes, List<BpLink> links,
                                    boolean is3D, StringBuilder sb, String pad, Set<String> visited) {
        if (visited.size() > 64) return;
        for (BpLink l : links) {
            if (fromId.equals(l.fromNodeId) && pin.equalsIgnoreCase(l.fromPin)) {
                BpNode next = findNode(nodes, l.toNodeId);
                if (next == null) continue;
                String visitKey = fromId + ":" + pin + "->" + next.id;
                if (!visited.add(visitKey)) continue;

                if (next.isBranch()) {
                    String cond = "BR_SCORE_GTE".equals(next.type)
                            ? "vortex.getScore() >= " + parseNum(next.param1, 100f)
                            : "vortex.countByTag('" + esc(next.param1.isEmpty() ? "coin" : next.param1) + "') === 0";
                    sb.append(pad).append("if (").append(cond).append(") {\n");
                    emitChainJs(next.id, "TRUE", nodes, links, is3D, sb, pad + "  ", new HashSet<>(visited));
                    sb.append(pad).append("} else {\n");
                    emitChainJs(next.id, "FALSE", nodes, links, is3D, sb, pad + "  ", new HashSet<>(visited));
                    sb.append(pad).append("}\n");
                } else {
                    sb.append(emitSingleNodeJs(next, is3D, pad));
                    emitChainJs(next.id, "OUT", nodes, links, is3D, sb, pad, visited);
                }
            }
        }
    }

    private static String emitSingleNodeJs(BpNode n, boolean is3D, String pad) {
        String p1 = esc(n.param1);
        String p2 = esc(n.param2);
        String p3 = esc(n.param3);
        switch (n.type) {
            case "GUI_SHOW_DIALOG":
                return pad + "vortex.showDialog('" + p1 + "', '" + p2 + "', " + parseNum(p3, 4.0f) + ");\n";
            case "GUI_HIDE_DIALOG":
                return pad + "vortex.hideDialog();\n";
            case "GUI_SET_LABEL": {
                float[] xy = parsePair(p3, 50f, 14f);
                return pad + "vortex.setLabel('" + (p1.isEmpty() ? "lbl" : p1) + "', '" + p2 + "', " + xy[0] + ", " + xy[1] + ", 15, '#FFFFFF');\n";
            }
            case "GUI_SET_BAR": {
                float[] vm = parsePair(p3, 100f, 100f);
                return pad + "vortex.setProgressBar('" + (p1.isEmpty() ? "bar" : p1) + "', '" + p2 + "', " + vm[0] + ", " + vm[1] + ", 50, 10, '#FFFFFF');\n";
            }
            case "GUI_SET_BANNER":
                return pad + "vortex.setBanner('" + (p2.isEmpty() ? p1 : p2) + "');\n";
            case "GUI_CLEAR_UI":
                return (p1.isEmpty() || "*".equals(p1))
                        ? pad + "vortex.clearUI();\n"
                        : pad + "vortex.removeUI('" + p1 + "');\n";
            case "ACT_MOVE_JOY": {
                String ent = p1.isEmpty() ? "Player" : p1;
                float spd = parseNum(p2, 8.5f);
                if (is3D) {
                    return pad + "if (vortex.exists('" + ent + "')) vortex.setVel('" + ent + "', vortex.joyX() * " + spd + ", vortex.getVY('" + ent + "'), -vortex.joyY() * " + spd + ");\n";
                }
                return pad + "if (vortex.exists('" + ent + "')) vortex.setVel('" + ent + "', vortex.joyX() * " + spd + ", vortex.joyY() * " + spd + ");\n";
            }
            case "ACT_JUMP":
                return pad + "vortex.jump('" + (p1.isEmpty() ? "Player" : p1) + "', " + parseNum(p2, 12.5f) + ");\n";
            case "ACT_SHOOT": {
                String ent = p1.isEmpty() ? "Player" : p1;
                float spd = parseNum(p2, 18f);
                String col = p3.isEmpty() ? "#FFFFFF" : p3;
                return pad + "if (vortex.exists('" + ent + "')) { const bn = 'BpBolt_' + (++__bpShotSeq); vortex.spawn2D(bn, 'projectile', 'CIRCLE', vortex.getX('" + ent + "') + 0.8, vortex.getY('" + ent + "'), 0.45, 0.45, '" + col + "', 'KINEMATIC'); vortex.setVel(bn, " + spd + ", 0); vortex.playSound('laser'); }\n";
            }
            case "ACT_CHASE_AI":
                return pad + "vortex.chaseTagAI('" + (p1.isEmpty() ? "enemy" : p1) + "', '" + (p2.isEmpty() ? "Player" : p2) + "', " + parseNum(p3, 4.2f) + ");\n";
            case "ACT_ADD_SCORE":
                return pad + "vortex.addScore(" + Math.round(parseNum(p1, 100f)) + ");\n";
            case "ACT_ADD_HP":
                return pad + "vortex.addHealth(" + Math.round(parseNum(p1, -20f)) + ");\n";
            case "ACT_DESTROY_OTH":
                return pad + "if (typeof bpOther !== 'undefined') { vortex.particles(vortex.getX(bpOther), vortex.getY(bpOther), vortex.getZ(bpOther), 18, '" + (p2.isEmpty() ? "#FFFFFF" : p2) + "'); vortex.destroy(bpOther); }\n";
            case "ACT_PLAY_SFX":
                return pad + "vortex.playSound('" + (p1.isEmpty() ? "coin" : p1) + "');\n";
            default:
                return "";
        }
    }

    public static String compileToJava(List<BpNode> nodes, List<BpLink> links, boolean is3D) {
        StringBuilder onStart = new StringBuilder();
        StringBuilder onUpdate = new StringBuilder();
        StringBuilder onBtnA = new StringBuilder();
        StringBuilder onBtnB = new StringBuilder();
        StringBuilder onCollide = new StringBuilder();

        for (BpNode n : nodes) {
            switch (n.type) {
                case "EV_START":
                    emitChainJava(n.id, "OUT", nodes, links, is3D, onStart, "    ", new HashSet<>());
                    break;
                case "EV_TICK":
                    emitChainJava(n.id, "OUT", nodes, links, is3D, onUpdate, "    ", new HashSet<>());
                    break;
                case "EV_BTN_A":
                    emitChainJava(n.id, "OUT", nodes, links, is3D, onBtnA, "        ", new HashSet<>());
                    break;
                case "EV_BTN_B":
                    emitChainJava(n.id, "OUT", nodes, links, is3D, onBtnB, "        ", new HashSet<>());
                    break;
                case "EV_COLLIDE": {
                    String tA = esc(n.param1.isEmpty() ? "player" : n.param1);
                    String tB = esc(n.param2.isEmpty() ? "coin" : n.param2);
                    StringBuilder body = new StringBuilder();
                    emitChainJava(n.id, "OUT", nodes, links, is3D, body, "        ", new HashSet<>());
                    if (body.length() > 0) {
                        onCollide.append("    if ((tagA.equals(\"").append(tA).append("\") && tagB.equals(\"").append(tB)
                                .append("\")) || (tagB.equals(\"").append(tA).append("\") && tagA.equals(\"").append(tB).append("\"))) {\n")
                                .append("        String bpSubj = tagA.equals(\"").append(tA).append("\") ? a : b;\n")
                                .append("        String bpOther = bpSubj.equals(a) ? b : a;\n")
                                .append(body)
                                .append("    }\n");
                    }
                    break;
                }
                default:
                    break;
            }
        }

        StringBuilder out = new StringBuilder("// Generated from Vortex Visual Blueprint Graph (Native Java)\n");
        out.append("int __bpShotSeq = 0;\n");
        if (onStart.length() > 0) out.append("public void onStart() {\n").append(onStart).append("}\n");
        if (onUpdate.length() > 0) out.append("public void onUpdate(double dt) {\n").append(onUpdate).append("}\n");
        if (onBtnA.length() > 0 || onBtnB.length() > 0) {
            out.append("public void onButton(String btn) {\n");
            if (onBtnA.length() > 0) out.append("    if (\"A\".equals(btn)) {\n").append(onBtnA).append("    }\n");
            if (onBtnB.length() > 0) out.append("    if (\"B\".equals(btn)) {\n").append(onBtnB).append("    }\n");
            out.append("}\n");
        }
        if (onCollide.length() > 0) out.append("public void onCollide(String a, String b, String tagA, String tagB) {\n").append(onCollide).append("}\n");
        return out.toString();
    }

    private static void emitChainJava(String fromId, String pin, List<BpNode> nodes, List<BpLink> links,
                                      boolean is3D, StringBuilder sb, String pad, Set<String> visited) {
        if (visited.size() > 64) return;
        for (BpLink l : links) {
            if (fromId.equals(l.fromNodeId) && pin.equalsIgnoreCase(l.fromPin)) {
                BpNode next = findNode(nodes, l.toNodeId);
                if (next == null) continue;
                String visitKey = fromId + ":" + pin + "->" + next.id;
                if (!visited.add(visitKey)) continue;

                if (next.isBranch()) {
                    String cond = "BR_SCORE_GTE".equals(next.type)
                            ? "vortex.getScore() >= " + Math.round(parseNum(next.param1, 100f))
                            : "vortex.countByTag(\"" + esc(next.param1.isEmpty() ? "coin" : next.param1) + "\") == 0";
                    sb.append(pad).append("if (").append(cond).append(") {\n");
                    emitChainJava(next.id, "TRUE", nodes, links, is3D, sb, pad + "    ", new HashSet<>(visited));
                    sb.append(pad).append("} else {\n");
                    emitChainJava(next.id, "FALSE", nodes, links, is3D, sb, pad + "    ", new HashSet<>(visited));
                    sb.append(pad).append("}\n");
                } else {
                    String jsStmt = emitSingleNodeJs(next, is3D, pad)
                            .replace("'", "\"")
                            .replace("const bn =", "String bn =")
                            .replace("typeof bpOther !== \"undefined\"", "bpOther != null");
                    sb.append(jsStmt);
                    emitChainJava(next.id, "OUT", nodes, links, is3D, sb, pad, visited);
                }
            }
        }
    }

    public static String compileToLua(List<BpNode> nodes, List<BpLink> links, boolean is3D) {
        StringBuilder onStart = new StringBuilder();
        StringBuilder onUpdate = new StringBuilder();
        StringBuilder onBtnA = new StringBuilder();
        StringBuilder onBtnB = new StringBuilder();
        StringBuilder onCollide = new StringBuilder();

        for (BpNode n : nodes) {
            switch (n.type) {
                case "EV_START":
                    emitChainLua(n.id, "OUT", nodes, links, is3D, onStart, "    ", new HashSet<>());
                    break;
                case "EV_TICK":
                    emitChainLua(n.id, "OUT", nodes, links, is3D, onUpdate, "    ", new HashSet<>());
                    break;
                case "EV_BTN_A":
                    emitChainLua(n.id, "OUT", nodes, links, is3D, onBtnA, "        ", new HashSet<>());
                    break;
                case "EV_BTN_B":
                    emitChainLua(n.id, "OUT", nodes, links, is3D, onBtnB, "        ", new HashSet<>());
                    break;
                case "EV_COLLIDE": {
                    String tA = esc(n.param1.isEmpty() ? "player" : n.param1);
                    String tB = esc(n.param2.isEmpty() ? "coin" : n.param2);
                    StringBuilder body = new StringBuilder();
                    emitChainLua(n.id, "OUT", nodes, links, is3D, body, "        ", new HashSet<>());
                    if (body.length() > 0) {
                        onCollide.append("    if (tagA == '").append(tA).append("' and tagB == '").append(tB)
                                .append("') or (tagB == '").append(tA).append("' and tagA == '").append(tB).append("') then\n")
                                .append("        local bpSubj = (tagA == '").append(tA).append("') and a or b\n")
                                .append("        local bpOther = (bpSubj == a) and b or a\n")
                                .append(body)
                                .append("    end\n");
                    }
                    break;
                }
                default:
                    break;
            }
        }

        StringBuilder out = new StringBuilder("-- Generated from Vortex Visual Blueprint Graph (Lua 5.2)\n");
        out.append("local __bpShotSeq = 0\n");
        if (onStart.length() > 0) out.append("function onStart()\n").append(onStart).append("end\n");
        if (onUpdate.length() > 0) out.append("function onUpdate(dt)\n").append(onUpdate).append("end\n");
        if (onBtnA.length() > 0 || onBtnB.length() > 0) {
            out.append("function onButton(btn)\n");
            if (onBtnA.length() > 0) out.append("    if btn == 'A' then\n").append(onBtnA).append("    end\n");
            if (onBtnB.length() > 0) out.append("    if btn == 'B' then\n").append(onBtnB).append("    end\n");
            out.append("end\n");
        }
        if (onCollide.length() > 0) out.append("function onCollide(a, b, tagA, tagB)\n").append(onCollide).append("end\n");
        return out.toString();
    }

    private static void emitChainLua(String fromId, String pin, List<BpNode> nodes, List<BpLink> links,
                                     boolean is3D, StringBuilder sb, String pad, Set<String> visited) {
        if (visited.size() > 64) return;
        for (BpLink l : links) {
            if (fromId.equals(l.fromNodeId) && pin.equalsIgnoreCase(l.fromPin)) {
                BpNode next = findNode(nodes, l.toNodeId);
                if (next == null) continue;
                String visitKey = fromId + ":" + pin + "->" + next.id;
                if (!visited.add(visitKey)) continue;

                if (next.isBranch()) {
                    String cond = "BR_SCORE_GTE".equals(next.type)
                            ? "vortex.getScore() >= " + Math.round(parseNum(next.param1, 100f))
                            : "vortex.countByTag('" + esc(next.param1.isEmpty() ? "coin" : next.param1) + "') == 0";
                    sb.append(pad).append("if ").append(cond).append(" then\n");
                    emitChainLua(next.id, "TRUE", nodes, links, is3D, sb, pad + "    ", new HashSet<>(visited));
                    sb.append(pad).append("else\n");
                    emitChainLua(next.id, "FALSE", nodes, links, is3D, sb, pad + "    ", new HashSet<>(visited));
                    sb.append(pad).append("end\n");
                } else {
                    String p1 = esc(next.param1);
                    String p2 = esc(next.param2);
                    String p3 = esc(next.param3);
                    switch (next.type) {
                        case "GUI_SHOW_DIALOG":
                            sb.append(pad).append("vortex.showDialog('").append(p1).append("', '").append(p2).append("', ").append(parseNum(p3, 4f)).append(")\n");
                            break;
                        case "GUI_HIDE_DIALOG":
                            sb.append(pad).append("vortex.hideDialog()\n");
                            break;
                        case "GUI_SET_LABEL": {
                            float[] xy = parsePair(p3, 50f, 14f);
                            sb.append(pad).append("vortex.setLabel('").append(p1.isEmpty() ? "lbl" : p1).append("', '").append(p2).append("', ").append(xy[0]).append(", ").append(xy[1]).append(", 15, '#FFFFFF')\n");
                            break;
                        }
                        case "GUI_SET_BAR": {
                            float[] vm = parsePair(p3, 100f, 100f);
                            sb.append(pad).append("vortex.setProgressBar('").append(p1.isEmpty() ? "bar" : p1).append("', '").append(p2).append("', ").append(vm[0]).append(", ").append(vm[1]).append(", 50, 10, '#FFFFFF')\n");
                            break;
                        }
                        case "GUI_SET_BANNER":
                            sb.append(pad).append("vortex.setBanner('").append(p2.isEmpty() ? p1 : p2).append("')\n");
                            break;
                        case "GUI_CLEAR_UI":
                            sb.append(pad).append((p1.isEmpty() || "*".equals(p1)) ? "vortex.clearUI()\n" : "vortex.removeUI('" + p1 + "')\n");
                            break;
                        case "ACT_ADD_SCORE":
                            sb.append(pad).append("vortex.addScore(").append(Math.round(parseNum(p1, 100f))).append(")\n");
                            break;
                        case "ACT_ADD_HP":
                            sb.append(pad).append("vortex.addHealth(").append(Math.round(parseNum(p1, -20f))).append(")\n");
                            break;
                        case "ACT_PLAY_SFX":
                            sb.append(pad).append("vortex.playSound('").append(p1.isEmpty() ? "coin" : p1).append("')\n");
                            break;
                        case "ACT_DESTROY_OTH":
                            sb.append(pad).append("if bpOther ~= nil then vortex.destroy(bpOther) end\n");
                            break;
                        case "ACT_JUMP":
                            sb.append(pad).append("vortex.jump('").append(p1.isEmpty() ? "Player" : p1).append("', ").append(parseNum(p2, 12.5f)).append(")\n");
                            break;
                        case "ACT_CHASE_AI":
                            sb.append(pad).append("vortex.chaseTagAI('").append(p1.isEmpty() ? "enemy" : p1).append("', '").append(p2.isEmpty() ? "Player" : p2).append("', ").append(parseNum(p3, 4.2f)).append(")\n");
                            break;
                        case "ACT_MOVE_JOY": {
                            String ent = p1.isEmpty() ? "Player" : p1;
                            float spd = parseNum(p2, 8.5f);
                            if (is3D) {
                                sb.append(pad).append("if vortex.exists('").append(ent).append("') then vortex.setVel('").append(ent).append("', vortex.joyX()*").append(spd).append(", vortex.getVY('").append(ent).append("'), -vortex.joyY()*").append(spd).append(") end\n");
                            } else {
                                sb.append(pad).append("if vortex.exists('").append(ent).append("') then vortex.setVel('").append(ent).append("', vortex.joyX()*").append(spd).append(", vortex.joyY()*").append(spd).append(") end\n");
                            }
                            break;
                        }
                        default:
                            break;
                    }
                    emitChainLua(next.id, "OUT", nodes, links, is3D, sb, pad, visited);
                }
            }
        }
    }

    private static BpNode findNode(List<BpNode> nodes, String id) {
        if (id == null) return null;
        for (BpNode n : nodes) {
            if (id.equals(n.id)) return n;
        }
        return null;
    }

    private static float parseNum(String s, float def) {
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return Float.parseFloat(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static float[] parsePair(String s, float d1, float d2) {
        if (s == null || s.trim().isEmpty()) return new float[]{d1, d2};
        String[] parts = s.split(",");
        float a = parts.length > 0 ? parseNum(parts[0], d1) : d1;
        float b = parts.length > 1 ? parseNum(parts[1], d2) : d2;
        return new float[]{a, b};
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "").replace("'", "").replace("\"", "").replace("\n", " ");
    }
}
