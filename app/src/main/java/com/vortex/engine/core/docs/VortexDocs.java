package com.vortex.engine.core.docs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-IDE documentation (v4.8.0).
 *
 * Everything here ships inside the app, so a beginner always has the answer offline — no browser,
 * no internet, no guessing. The same text is what Forge AI quotes when you ask it a "how do I…"
 * question, which is why the answers match the real API instead of being invented.
 *
 * Structure: one entry per topic with a title, a one-line summary, a list of searchable keywords and
 * a body written in plain sentences with small code blocks.
 */
public final class VortexDocs {

    public static final class Topic {
        public final String id;
        public final String title;
        public final String summary;
        public final String[] keywords;
        public final String body;

        Topic(String id, String title, String summary, String[] keywords, String body) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.keywords = keywords;
            this.body = body;
        }
    }

    private static final Map<String, Topic> TOPICS = new LinkedHashMap<>();

    private VortexDocs() { }

    private static void add(String id, String title, String summary, String[] keywords, String body) {
        TOPICS.put(id, new Topic(id, title, summary, keywords, body));
    }

    static {
        add("start", "Getting started (5 min)", "From zero to a running game.", new String[]{
                "start", "begin", "beginner", "first", "tutorial", "new", "how to start", "intro"},
                "1. File ▸ New Project… and pick a starter.\n"
                + "   • 3D Voxel: Blockfall — a Minecraft-style block world you can mine and build in.\n"
                + "   • 3D Explorer: Green Hollow Valley — walk a hill, collect gems.\n"
                + "   • 2D Action: Starbound Cavern — platforms + slimes.\n"
                + "2. Press ▶ PLAY GAME (top right). Test the starter before changing anything.\n"
                + "3. Left dock = the scene tree. Click a node to select it; the right dock shows its "
                + "Transform / Rendering / Physics.\n"
                + "4. Right dock ▸ Script tab holds your code. The file you edit is listed in Files.\n"
                + "5. Tools ▸ Docs (this window) answers questions while you work; Forge AI (AI tab) can "
                + "build things for you in plain language.\n"
                + "6. Build ▸ Export makes an .exe / .apk / HTML page when you are ready to share.\n\n"
                + "Golden rule: change one thing, press play, look. That loop teaches faster than reading.");

        add("voxel", "Voxel worlds: mine & build", "How the block world works and how to reshape it.",
                new String[]{"voxel", "minecraft", "block", "mine", "build", "dig", "place", "terrain", "cave"},
                "A voxel project stores a 96×96×64 grid of blocks. One byte per block, drawn with greedy "
                + "meshing so a whole landscape is a few thousand triangles.\n\n"
                + "Playing:  WASD move · SPACE jump · F fly · SHIFT sprint · CTRL sneak\n"
                + "          LMB mine · RMB build · 1-9 pick block · B cycle · middle-click pipette\n"
                + "On a phone: left thumb = joystick, drag on the world = look, tap = mine, "
                + "long-press (or second finger) = build.\n\n"
                + "Editing without playing: press play, or in the editor use Tools ▸ Voxel Block Palette to "
                + "choose a block, then left-click to mine and right-click to build in the viewport.\n\n"
                + "Scripting (works in JS / Lua / Java):\n"
                + "  vortex.voxelGet(x, y, z)            block id at a cell (0 = air)\n"
                + "  vortex.voxelSet(x, y, z, 'STONE')   place a block by name or id\n"
                + "  vortex.voxelName(id)                'Stone'\n"
                + "  vortex.voxelHeight(x, z)            highest solid block in that column\n"
                + "  vortex.playerX/Y/Z()                player position\n"
                + "  vortex.setPlayerPos(x, y, z)        teleport\n"
                + "  vortex.setPlayerFlying(true)        creative flight\n"
                + "  vortex.voxelRegenerate(seed)         new world from a seed\n\n"
                + "Block names: Grass, Dirt, Stone, Sand, Wood, Leaves, Water, Plank, Glass, Brick, Lamp, Snow.\n\n"
                + "Events: define onBlockMined(name) and onBlockPlaced(name) in your script to react "
                + "(score, sounds, unlocks).");

        add("script", "Writing your first script", "Hooks, the vortex API and how to read it.",
                new String[]{"script", "code", "js", "javascript", "lua", "java", "api", "hook", "function"},
                "Every script file shares one scope and can define these functions:\n"
                + "  onStart()                     runs once when play starts\n"
                + "  onUpdate(dt)                  every frame; dt is seconds since last frame\n"
                + "  onButton(name)                'A' = jump/action key, 'B' = second key\n"
                + "  onCollide(a, b, tagA, tagB)   two bodies touched\n"
                + "  onBlockMined(name) / onBlockPlaced(name)   voxel edits\n\n"
                + "Reading the world:  vortex.exists('Player'), getX/getY/getZ, getVY, isGrounded, countByTag('enemy')\n"
                + "Changing things:    setPos, setVel, addVel, applyImpulse, jump, setScale, setColor, destroy, spawn3D\n"
                + "Game state:         addScore, getScore, setHealth, addHealth, setBanner, setLabel, showDialog\n"
                + "Audio/feel:         playSound('coin'|'laser'|'jump'|'hit'|'explosion'), shake(0.4, 0.2), particles(x,y,z,20,'#FFF')\n"
                + "Voxel:              voxelGet, voxelSet, voxelName, voxelHeight, playerX/Y/Z (see Voxel worlds)\n\n"
                + "Example (JavaScript):\n"
                + "  function onStart() { vortex.setBanner('Collect 3 gems'); }\n"
                + "  function onUpdate(dt) {\n"
                + "    if (!vortex.exists('Player')) return;\n"
                + "    vortex.setVel('Player', vortex.joyX() * 8, vortex.getVY('Player'), -vortex.joyY() * 8);\n"
                + "  }\n\n"
                + "If something is red in the console, the message names the line — press ▶ again after fixing it.");

        add("camera", "Camera & controls", "Follow cams, first person, and why you cannot move.",
                new String[]{"camera", "movement", "move", "wasd", "controls", "jump", "first person", "stuck"},
                "Camera modes live in the Scene dock: FOLLOW (chases a target), FIRST_PERSON, FIXED.\n"
                + "Set cameraTargetName to the node the camera should follow.\n\n"
                + "Movement is usually driven by a script: vortex.setVel(name, vx, vy, vz).\n"
                + "jump(name, 12) only works while the body is grounded.\n\n"
                + "'My player does not move' — check in this order:\n"
                + "1. Is the body type DYNAMIC? STATIC bodies ignore velocity.\n"
                + "2. Is gravity pulling it into the floor (body stuck inside a collider)? Move it up 1 unit.\n"
                + "3. Is the script actually saved (the file tab shows a dot when unsaved)?\n"
                + "4. Is onUpdate spelled exactly, with (dt)?\n"
                + "5. Console shows errors in red — read the top one first.\n\n"
                + "Voxel projects do not need movement code: the engine moves the player.");

        add("3d", "Building a 3D scene", "Primitives, lighting, materials, sky.",
                new String[]{"3d", "scene", "model", "cube", "sphere", "light", "lighting", "material", "sky", "fog"},
                "Create ▸ Cube / Sphere / Pyramid / Cylinder adds a primitive at the origin; move it with the "
                + "Transform fields or drag it in the viewport (W = move, E-style scale via the SCALE gizmo).\n\n"
                + "Lighting rig (Scene dock): sun yaw, elevation, power, colour, ambient fill and shadow "
                + "strength. Day/Dusk/Night presets are one click.\n\n"
                + "Materials (Rendering ▸ Material Preset): MATTE, METAL, GLASS, EMISSIVE, UNLIT.\n"
                + "Surface patterns (Rendering ▸ Surface Pattern): CHECKER, STRIPES, GRID, BRICK, NOISE — "
                + "procedural, so no texture files are needed.\n\n"
                + "Sky comes from the light rig: gradient, warm horizon, sun disc and stars on dark skies.\n"
                + "Fog (Scene dock) hides the far edge of the world — set fogNear/fogFar around your play area.");

        add("physics", "Physics and collisions", "Why things fall through, and how to fix it.",
                new String[]{"physics", "collision", "collide", "gravity", "fall", "jump", "box2d", "solver"},
                "Bodies: STATIC (never moves), DYNAMIC (gravity + impulses), KINEMATIC (moves by code, pushes others).\n"
                + "Collider shapes: BOX, CIRCLE, NONE. Triggers overlap without pushing.\n\n"
                + "Checklist when an object falls through the floor:\n"
                + "1. Floor body must be STATIC with a collider (collider NONE = no collision!).\n"
                + "2. Very thin, very fast objects tunnel: make them thicker or slower.\n"
                + "3. Gravity sign differs by mode: 2D platformers usually want gravityY negative.\n"
                + "4. Tags identify collisions: onCollide(a, b, tagA, tagB) compares tags, not names.\n\n"
                + "The 3D solver substeps four times per frame with continuous collision detection, so "
                + "spheres and boxes rest cleanly on terrain and voxel floors.");

        add("export", "Exporting your game", "Turning your project into something you can share.",
                new String[]{"export", "build", "exe", "apk", "android", "html", "web", "share", "release"},
                "Build tab ▸ choose a target:\n"
                + "• Windows .exe / Linux — a self-contained launcher with your game inside.\n"
                + "• Android .apk — installs on a phone; on-device builds run through Termux.\n"
                + "• HTML5 — one self-contained .html file (no server, no assets folder).\n\n"
                + "Before exporting: press ▶ and play for a minute. Exporting a broken game just moves "
                + "the bug to someone else's device.\n\n"
                + "Keep an eye on the console during the build; the last line says where the file went.");

        add("fix", "When something breaks", "Fast diagnosis order.",
                new String[]{"error", "bug", "broken", "not working", "crash", "red", "help", "fix"},
                "Read the console bottom-up: the first red line is usually the real error, the rest are echoes.\n\n"
                + "Common ones:\n"
                + "• 'undefined is not a function' — the API name is misspelled; check the Script API doc.\n"
                + "• 'could not find entity' — you renamed a node; scripts look names up by string.\n"
                + "• Nothing renders — the camera has no target, or the object is behind the camera.\n"
                + "• Everything is black — sun intensity or ambient is 0, or the sky colour is black.\n"
                + "• Slow — lower the render radius / fog distance, delete off-screen objects.\n\n"
                + "Ask Forge AI: 'why is my player stuck?' — it reads your actual files and scene, not a guess.");

        add("forge", "Using Forge AI well", "How to get useful results.",
                new String[]{"forge", "ai", "assistant", "prompt", "ask", "generate"},
                "Forge AI reads your project (files, entities, syntax check) before answering, and can write "
                + "files, add entities and run playtests.\n\n"
                + "Good prompts are concrete: 'Add a double jump and show it in the HUD' beats 'improve my game'.\n"
                + "If you are unsure what you want, ask a question instead: 'how do I make the camera follow "
                + "the player?' — you get the documented answer plus a button to apply it.\n\n"
                + "Every change Forge makes shows up as a badge in the chat (for example ✓ write_file: "
                + "objects/boss.js) with an Undo button, so you can experiment safely.\n\n"
                + "It works offline with a built-in generator; connecting a provider key (AI tab ▸ settings) "
                + "switches to the full model.");

        add("prefab", "Prefabs and tile maps", "Reuse scene chunks and paint 2D tiles.",
                new String[]{"prefab", "tile", "tilemap", "tiled", "tmx", "reuse", "copy", "level"},
                "Prefabs: select a node → Tools ▸ Create Prefab from Selection. Later, Tools ▸ Prefab "
                + "Palette drops a fresh copy (children included) under the selected node.\n\n"
                + "Tile maps: File ▸ Import Tiled Map (.tmx) brings in a Tiled level, or paint directly — "
                + "Tools ▸ Tile Paint Mode, pick a swatch at the bottom, drag to paint, right-drag to erase. "
                + "Solid cells become colliders. Merge Tile Runs compacts the layer afterwards.\n\n"
                + "On the phone the same painter is the 🧱 TILES button on the 2D toolbar.");
    }

    public static List<Topic> all() {
        return new ArrayList<>(TOPICS.values());
    }

    public static Topic get(String id) {
        return TOPICS.get(id);
    }

    public static String[] titles() {
        List<String> out = new ArrayList<>();
        for (Topic t : TOPICS.values()) out.add(t.title);
        return out.toArray(new String[0]);
    }

    /** Topics matching a free-text query, best first. Empty when nothing matches. */
    public static List<Topic> search(String query) {
        List<Topic> out = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) return out;
        String q = query.toLowerCase(Locale.US);
        for (Topic t : TOPICS.values()) {
            int score = 0;
            for (String k : t.keywords) {
                if (q.contains(k)) score += 3;
                else if (k.contains(q)) score += 2;
            }
            if (q.contains(t.title.toLowerCase(Locale.US))) score += 4;
            if (t.body.toLowerCase(Locale.US).contains(q)) score += 1;
            if (score > 0) out.add(t);
        }
        out.sort((a, b) -> Integer.compare(scoreOf(b, q), scoreOf(a, q)));
        return out;
    }

    /** True when a topic should stay visible for this (possibly partial) query. */
    public static boolean matches(Topic t, String query) {
        if (t == null) return false;
        if (query == null) return true;
        String q = query.trim().toLowerCase(Locale.US);
        if (q.isEmpty()) return true;
        if (t.title.toLowerCase(Locale.US).contains(q)) return true;
        if (t.summary.toLowerCase(Locale.US).contains(q)) return true;
        if (t.body.toLowerCase(Locale.US).contains(q)) return true;
        for (String k : t.keywords) {
            if (k.contains(q) || q.contains(k)) return true;
        }
        return false;
    }

    private static int scoreOf(Topic t, String q) {
        int score = 0;
        for (String k : t.keywords) {
            if (q.contains(k)) score += 3;
            else if (k.contains(q)) score += 2;
        }
        if (q.contains(t.title.toLowerCase(Locale.US))) score += 4;
        if (t.body.toLowerCase(Locale.US).contains(q)) score += 1;
        return score;
    }

    /** The best answer for a question, or null when it is not a documentation question. */
    public static Topic answerFor(String question) {
        if (question == null) return null;
        String q = question.toLowerCase(Locale.US);
        boolean looksLikeQuestion = q.contains("how ") || q.contains("what ") || q.contains("why ")
                || q.contains("where ") || q.contains("can i") || q.contains("does ") || q.endsWith("?")
                || q.contains("documentation") || q.contains("docs") || q.contains("explain");
        List<Topic> hits = search(q);
        if (hits.isEmpty()) return null;
        if (!looksLikeQuestion && hits.size() > 1) {
            // A bare noun ("voxel", "camera") still gets its doc — beginners ask that way a lot.
            return hits.get(0);
        }
        return hits.get(0);
    }

    /** Short answer text for chat: the summary, the body and where to read more. */
    public static String shortAnswer(String question) {
        Topic t = answerFor(question);
        if (t == null) return null;
        return t.body + "\n\n(full topic: Tools ▸ Docs ▸ " + t.title + ")";
    }

    public static List<String> keywordList() {
        return Arrays.asList("start", "voxel", "script", "camera", "3d", "physics", "export", "fix", "forge", "prefab");
    }
}
