package com.vortex.engine.core.model;

import com.vortex.engine.core.audio.ChiptuneGenerator;
import com.vortex.engine.core.flow.VortexFlowCompiler;

/**
 * Pure-Java Cross-Platform Starter Game Templates (shared by Android Studio APK, Windows .exe, Linux Studio & HTML5 Web).
 * Showcases v3.0 Theta* AI Pathfinding, Universal Tween Engine, 16-Step Chiptune Synth, Vortex Flow, LuaJ 5.2, JBox2D & FastNoiseLite!
 */
public class ProjectTemplates {

    public static final String[] TEMPLATE_KEYS = {
            "NEON_SURVIVORS_JS_2D",
            "IRON_SENTINEL_JAVA_2D",
            "STARHULL_MECHA_JS_3D",
            "CYBER_MAZE_AI_2D",
            "CITADEL_TWEEN_3D",
            "ANGRY_BOX2D_LUA",
            "FRONTIER_TERRAIN_3D_LUA",
            "NEON_3D",
            "SKY_ISLANDS_3D",
            "PLATFORMER_2D",
            "SHOOTER_2D",
            "BLANK_3D_LUA",
            "BLANK_2D_LUA",
            "EXPLORER_3D",
            "MINECRAFT_3D"
    };

    public static final String[] TEMPLATE_NAMES = {
            "2D Rogue-Lite: Neon Survivors Swarm (ES6 JS • Rhino + Animated Sprites)",
            "2D Java Arena: Iron Sentinel (Native Java VM + JBox2D + Auto-Turret)",
            "3D Mecha Combat: Starhull Raiders (ES6 JS • Rhino + 3D Terrain + Railgun)",
            "2D AI Maze: Cyber Hunters (Lua 5.2 + Theta* Pathfinding + Chiptune)",
            "3D Siege: Neon Citadel (Lua 5.2 + Universal Tween Engine + Raycast)",
            "2D Physics: Angry Crates Siege (Lua 5.2 + JBox2D Rigid-Body)",
            "3D World: Procedural Frontier (Lua 5.2 + FastNoiseLite Terrain)",
            "3D Sci-Fi: Neon Horizon Runner (Custom .obj Starfighter)",
            "3D Parkour: Sky Islands Adventure (3D Physics & Relics)",
            "2D Action: Starbound Cavern (Platformer + Slime AI)",
            "2D Arcade: Nova Blaster (Space Shooter + Plasma Lasers)",
            "3D Sandbox: Blank 3D Project (Java / ES6 JS / Lua 5.2)",
            "2D Sandbox: Blank 2D Project (Java / ES6 JS / Lua 5.2 + JBox2D)",
            "3D Explorer: Green Hollow Valley (ES6 JS + sunlight rig + collecting + NPC patrol)",
            "3D Voxel: Blockfall — mine, build, survive (Minecraft-style block world, infinite terrain seed)"
    };

    public static GameProject create(String templateKey, String title, String pkgName) {
        GameProject p;
        switch (templateKey) {
            case "NEON_SURVIVORS_JS_2D":    p = buildNeonSurvivorsJs2D(title, pkgName); break;
            case "IRON_SENTINEL_JAVA_2D":   p = buildIronSentinelJava2D(title, pkgName); break;
            case "STARHULL_MECHA_JS_3D":    p = buildStarhullMechaJs3D(title, pkgName); break;
            case "CYBER_MAZE_AI_2D":        p = buildCyberMazeAI2D(title, pkgName); break;
            case "CITADEL_TWEEN_3D":        p = buildCitadelTween3D(title, pkgName); break;
            case "ANGRY_BOX2D_LUA":         p = buildAngryCratesBox2D(title, pkgName); break;
            case "FRONTIER_TERRAIN_3D_LUA": p = buildFrontierTerrain3D(title, pkgName); break;
            case "SKY_ISLANDS_3D":          p = buildSkyIslands3D(title, pkgName); break;
            case "PLATFORMER_2D":           p = buildPlatformer2D(title, pkgName); break;
            case "SHOOTER_2D":              p = buildSpaceShooter2D(title, pkgName); break;
            case "BLANK_2D_LUA":            p = buildBlankLua(title, pkgName, false); break;
            case "BLANK_3D_LUA":            p = buildBlankLua(title, pkgName, true); break;
            case "EXPLORER_3D":             p = buildExplorer3D(title, pkgName); break;
            case "MINECRAFT_3D":            p = buildVoxelBlockfall(title, pkgName); break;
            case "NEON_3D":
            default:                        p = buildNeonHorizon3D(title, pkgName); break;
        }
        p.ensureScriptFilesInitialized();
        com.vortex.engine.core.flow.VortexBlueprintGraph.populateStarterDialogGraph(p);
        return p;
    }

    private static GameProject buildIronSentinelJava2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "2D";
        p.scriptLanguage = "JAVA";
        p.useBox2D = true;
        p.gravityY = 0f;
        p.skyColorHex = "#0B0B0E";
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.musicEnabled = true;
        p.musicBpm = 136;
        p.musicPattern = ChiptuneGenerator.PRESET_BOSS;

        p.customSprites.put("knight_1", PixelSpriteCodec.createDefaultKnightSprite());
        p.customSprites.put("knight_2", PixelSpriteCodec.createKnightRunFrame2Sprite());
        p.customSprites.put("drone_1", PixelSpriteCodec.createDroneFrame1Sprite());
        p.customSprites.put("drone_2", PixelSpriteCodec.createDroneFrame2Sprite());
        p.customSprites.put("gem", PixelSpriteCodec.createGemSprite());

        Entity hero = Entity.create2D("Player", "player", "CUSTOM_SPRITE", 0f, 0f, 1.4f, 1.4f, "#FFFFFF", "DYNAMIC");
        hero.customSpriteName = "knight_1";
        hero.spriteFrames = "knight_1,knight_2";
        hero.animFps = 7f;
        hero.fixedRotation = true;
        p.entities.add(hero);

        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI * 2.0 / 6.0;
            Entity d = Entity.create2D("Drone_" + i, "enemy", "CUSTOM_SPRITE",
                    (float) (Math.cos(ang) * 9.0), (float) (Math.sin(ang) * 7.0), 1.25f, 1.25f, "#EF4444", "DYNAMIC");
            d.customSpriteName = "drone_1";
            d.spriteFrames = "drone_1,drone_2";
            d.animFps = 9f;
            p.entities.add(d);

            Entity g = Entity.create2D("Core_" + i, "coin", "CUSTOM_SPRITE",
                    (float) (Math.cos(ang + 0.5) * 5.2), (float) (Math.sin(ang + 0.5) * 4.2), 0.9f, 0.9f, "#FACC15", "STATIC");
            g.customSpriteName = "gem";
            g.isTrigger = true;
            p.entities.add(g);
        }

        p.scriptCode =
                "// Iron Sentinel Arena — Written in Native Java (VM: Java)\n" +
                "// Runs natively on Desktop (.exe / Linux), Android (.apk), and exports to HTML5!\n" +
                "public class GameScript {\n" +
                "    float fireTimer = 0.0f;\n" +
                "    int boltSeq = 0;\n" +
                "    int wave = 1;\n\n" +
                "    public void onStart() {\n" +
                "        vortex.setFixedRotation(\"Player\", true);\n" +
                "        vortex.playMusic(\"BOSS\", 136);\n" +
                "        int best = (int) vortex.loadNum(\"javaBest\", 0);\n" +
                "        vortex.setBanner(\"IRON SENTINEL [JAVA VM] • WAVE 1 • BEST \" + best + \" (A=DASH, B=NOVA)\");\n" +
                "    }\n\n" +
                "    public void onUpdate(float dt) {\n" +
                "        if (!vortex.exists(\"Player\")) return;\n" +
                "        vortex.setVel(\"Player\", vortex.joyX() * 8.8f, vortex.joyY() * 8.8f);\n" +
                "        vortex.chaseTagAI(\"enemy\", \"Player\", 3.8f + wave * 0.35f);\n\n" +
                "        fireTimer += dt;\n" +
                "        if (fireTimer >= 0.42f) {\n" +
                "            fireTimer = 0.0f;\n" +
                "            String target = vortex.findNearestTag(\"Player\", \"enemy\", 24.0);\n" +
                "            if (target != null && target.length() > 0) {\n" +
                "                float px = vortex.getX(\"Player\");\n" +
                "                float py = vortex.getY(\"Player\");\n" +
                "                float dx = vortex.getX(target) - px;\n" +
                "                float dy = vortex.getY(target) - py;\n" +
                "                float len = (float) Math.hypot(dx, dy);\n" +
                "                if (len < 0.01f) len = 1.0f;\n" +
                "                boltSeq++;\n" +
                "                String bolt = \"Bolt_\" + boltSeq;\n" +
                "                vortex.spawn2D(bolt, \"projectile\", \"CIRCLE\", px, py, 0.38, 0.38, \"#FFFFFF\", \"KINEMATIC\");\n" +
                "                vortex.setVel(bolt, (dx / len) * 18.0f, (dy / len) * 18.0f);\n" +
                "                vortex.playSound(\"laser\");\n" +
                "            }\n" +
                "        }\n\n" +
                "        if (vortex.countByTag(\"enemy\") == 0) {\n" +
                "            wave++;\n" +
                "            for (int i = 0; i < 4 + wave; i++) {\n" +
                "                double a = (Math.PI * 2.0 * i) / (4 + wave);\n" +
                "                String en = \"WaveDrone_\" + wave + \"_\" + i;\n" +
                "                vortex.spawn2D(en, \"enemy\", \"CIRCLE\", Math.cos(a) * 10.0, Math.sin(a) * 7.5, 1.15, 1.15, \"#EF4444\", \"DYNAMIC\");\n" +
                "            }\n" +
                "            vortex.setBanner(\"IRON SENTINEL [JAVA VM] • WAVE \" + wave + \" INCOMING!\");\n" +
                "        }\n" +
                "    }\n\n" +
                "    public void onButton(String btn) {\n" +
                "        if (!vortex.exists(\"Player\")) return;\n" +
                "        if (\"A\".equals(btn)) {\n" +
                "            vortex.applyImpulse(\"Player\", vortex.joyX() * 14.0, vortex.joyY() * 14.0);\n" +
                "            vortex.particles(vortex.getX(\"Player\"), vortex.getY(\"Player\"), 0, 18, \"#FFFFFF\");\n" +
                "            vortex.playSound(\"jump\");\n" +
                "        } else if (\"B\".equals(btn)) {\n" +
                "            String near = vortex.findNearestTag(\"Player\", \"enemy\", 12.0);\n" +
                "            if (near != null && near.length() > 0) {\n" +
                "                vortex.particles(vortex.getX(near), vortex.getY(near), 0, 26, \"#FACC15\");\n" +
                "                vortex.destroy(near);\n" +
                "                vortex.addScore(150);\n" +
                "            }\n" +
                "            vortex.shake(0.35, 0.2);\n" +
                "            vortex.playSound(\"powerup\");\n" +
                "        }\n" +
                "    }\n\n" +
                "    public void onCollide(String a, String b, String tagA, String tagB) {\n" +
                "        if ((\"projectile\".equals(tagA) && \"enemy\".equals(tagB)) || (\"projectile\".equals(tagB) && \"enemy\".equals(tagA))) {\n" +
                "            String en = \"enemy\".equals(tagA) ? a : b;\n" +
                "            vortex.particles(vortex.getX(en), vortex.getY(en), 0, 22, \"#EF4444\");\n" +
                "            vortex.destroy(a);\n" +
                "            vortex.destroy(b);\n" +
                "            int sc = vortex.addScore(100);\n" +
                "            if (sc > vortex.loadNum(\"javaBest\", 0)) vortex.saveData(\"javaBest\", sc);\n" +
                "            vortex.playSound(\"explosion\");\n" +
                "        } else if ((\"player\".equals(tagA) && \"coin\".equals(tagB)) || (\"player\".equals(tagB) && \"coin\".equals(tagA))) {\n" +
                "            String c = \"coin\".equals(tagA) ? a : b;\n" +
                "            vortex.destroy(c);\n" +
                "            vortex.addScore(50);\n" +
                "            vortex.playSound(\"coin\");\n" +
                "        } else if ((\"player\".equals(tagA) && \"enemy\".equals(tagB)) || (\"player\".equals(tagB) && \"enemy\".equals(tagA))) {\n" +
                "            vortex.addHealth(-10);\n" +
                "            vortex.shake(0.3, 0.18);\n" +
                "            vortex.playSound(\"hit\");\n" +
                "        }\n" +
                "    }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildAngryCratesBox2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "2D";
        p.scriptLanguage = "LUA";
        p.useBox2D = true;
        p.iconBgHex = "#1A1025";
        p.iconAccentHex = "#FFB300";
        p.iconGlyph = "STAR";
        p.skyColorHex = "#0B0B0E";
        p.cameraMode = "FIXED";
        p.zoom2D = 18f;
        p.gravityY = -20f;

        p.customSprites.put("crate", PixelSpriteCodec.createCrateSprite());
        p.customSprites.put("knight", PixelSpriteCodec.createDefaultKnightSprite());

        Entity ground = Entity.create2D("Ground", "solid", "RECT", 0f, -6.5f, 34f, 2f, "#223348", "STATIC");
        p.entities.add(ground);

        Entity cannon = Entity.create2D("Cannon", "player", "SPRITE_SHIP", -9.5f, -4.5f, 1.4f, 1.5f, "#00E5FF", "KINEMATIC");
        cannon.rotZ = -55f;
        p.entities.add(cannon);

        // Build a 3-story destructible JBox2D tower of tumbling crates & slimes!
        float baseX = 5.5f;
        for (int row = 0; row < 3; row++) {
            float y = -4.7f + row * 2.2f;
            Entity leftPillar = Entity.create2D("Crate_L" + row, "solid", "CUSTOM_SPRITE", baseX - 1.6f, y, 1.1f, 1.5f, "#FFB300", "DYNAMIC");
            leftPillar.customSpriteName = "crate";
            leftPillar.fixedRotation = false;
            leftPillar.bounce = 0.2f;
            p.entities.add(leftPillar);

            Entity rightPillar = Entity.create2D("Crate_R" + row, "solid", "CUSTOM_SPRITE", baseX + 1.6f, y, 1.1f, 1.5f, "#FFB300", "DYNAMIC");
            rightPillar.customSpriteName = "crate";
            rightPillar.fixedRotation = false;
            rightPillar.bounce = 0.2f;
            p.entities.add(rightPillar);

            Entity beam = Entity.create2D("Beam_" + row, "solid", "RECT", baseX, y + 1.05f, 4.4f, 0.45f, "#8D6E63", "DYNAMIC");
            beam.fixedRotation = false;
            p.entities.add(beam);

            Entity targetSlime = Entity.create2D("Slime_" + row, "enemy", "SPRITE_SLIME", baseX, y - 0.1f, 1.0f, 0.9f, "#FF5252", "DYNAMIC");
            targetSlime.fixedRotation = false;
            targetSlime.bounce = 0.35f;
            p.entities.add(targetSlime);
        }

        p.scriptCode =
                "-- Angry Crates Siege — Powered by Lua 5.2 (LuaJ) + JBox2D Rigid-Body Physics!\n" +
                "-- Aim Cannon: Up/Down Joystick | Fire Heavy Orb: Button A or B\n" +
                "local aimAngle = 38\n" +
                "local orbCount = 0\n\n" +
                "function onStart()\n" +
                "    vortex.setBanner('AIM WITH JOYSTICK & PRESS A/B TO SMASH TOWER!')\n" +
                "    print('Lua 5.2 + JBox2D Rigid-Body Physics ready!')\n" +
                "end\n\n" +
                "function onUpdate(dt)\n" +
                "    aimAngle = vortex.clamp(aimAngle + vortex.joyY() * 55 * dt, 10, 75)\n" +
                "    vortex.setRot('Cannon', 0, 0, aimAngle - 90)\n" +
                "    if vortex.countByTag('enemy') == 0 then\n" +
                "        vortex.setBanner('TOWER DEMOLISHED! SCORE: ' .. vortex.getScore())\n" +
                "    end\n" +
                "end\n\n" +
                "function onButton(btn)\n" +
                "    orbCount = orbCount + 1\n" +
                "    local name = 'Orb_' .. orbCount\n" +
                "    local rad = math.rad(aimAngle)\n" +
                "    local power = (btn == 'B') and 25.0 or 19.5\n" +
                "    vortex.spawn2D(name, 'projectile', 'CIRCLE', -8.6, -4.0, 1.05, 1.05, '#00E5FF', 'DYNAMIC')\n" +
                "    vortex.setFixedRotation(name, false)\n" +
                "    vortex.setVel(name, math.cos(rad) * power, math.sin(rad) * power)\n" +
                "    vortex.playSound('laser')\n" +
                "end\n\n" +
                "function onCollide(a, b, tagA, tagB)\n" +
                "    if (tagA == 'projectile' and tagB == 'enemy') or (tagA == 'enemy' and tagB == 'projectile') then\n" +
                "        local target = (tagA == 'enemy') and a or b\n" +
                "        vortex.particles(vortex.getX(target), vortex.getY(target), 0, 28, '#FF5252')\n" +
                "        vortex.destroy(target)\n" +
                "        vortex.addScore(250)\n" +
                "        vortex.shake(0.35, 0.22)\n" +
                "        vortex.playSound('explosion')\n" +
                "    end\n" +
                "end\n";
        return p;
    }

    /**
     * v4.6.0 starter: a walkable 3D valley with the sunlight rig, soft shadows, collectible gems,
     * a patrolling critter and a live HUD — the shortest path to "explorer" style play.
     */
    private static GameProject buildExplorer3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.scriptLanguage = "JS";
        p.terrainSeed = 4711;
        p.terrainFreq = 0.055f;
        p.skyColorHex = "#101A22";
        p.fogNear = 30f;
        p.fogFar = 110f;
        p.cameraDistance = 10f;
        p.cameraHeight = 5.2f;
        // Daylight rig: high sun, warm colour, gentle ambient fill, soft contact shadows.
        p.sunYawDeg = 38f;
        p.sunPitchDeg = 58f;
        p.sunIntensity = 1.05f;
        p.ambientStrength = 0.40f;
        p.sunColorHex = "#FFF2D6";
        p.shadows = true;
        p.shadowStrength = 0.42f;

        Entity terrain = Entity.create3D("Valley", "solid", "TERRAIN", 0f, 0f, 0f, 80f, 8.5f, 80f, "#3E7C4A", "STATIC");
        terrain.checkerTexture = true;
        terrain.secondaryColorHex = "#2E5E39";
        p.entities.add(terrain);

        Entity player = Entity.create3D("Hero", "player", "SPHERE", 0f, 4f, 0f, 1.2f, 1.2f, 1.2f, "#F5F3EF", "DYNAMIC");
        player.fixedRotation = true;
        p.entities.add(player);

        // little hamlet
        Entity house = Entity.create3D("Cabin", "solid", "CUBE", 9f, 2.2f, -6f, 4.2f, 3.4f, 4.2f, "#B98A5A", "STATIC");
        p.entities.add(house);
        Entity roof = Entity.create3D("CabinRoof", "decor", "PYRAMID", 9f, 4.6f, -6f, 4.8f, 2.4f, 4.8f, "#8C4A3C", "STATIC");
        p.entities.add(roof);
        com.vortex.engine.core.model.SceneHierarchy.reparent(p.entities, roof, house);

        Entity fence = Entity.create3D("FencePost", "solid", "CUBE", -8f, 1.4f, 6f, 0.5f, 1.8f, 0.5f, "#8A6A45", "STATIC");
        p.entities.add(fence);

        // collectible gems scattered on the hills
        float[][] gems = {{-12f, 4f, -9f}, {14f, 4f, 8f}, {-18f, 5f, 12f}, {5f, 6f, 17f}, {20f, 4f, -14f}};
        String[] gemNames = new String[gems.length];
        for (int i = 0; i < gems.length; i++) {
            Entity gem = Entity.create3D("Gem_" + (i + 1), "coin", "PYRAMID",
                    gems[i][0], gems[i][1], gems[i][2], 0.9f, 1.3f, 0.9f, "#7BE3C1", "STATIC");
            gem.isTrigger = true;
            gem.emissive = true;
            gem.spinY = 110f;
            p.entities.add(gem);
            gemNames[i] = gem.name;
        }

        // one patrolling critter that chases the hero
        Entity critter = Entity.create3D("Mossling", "enemy", "SPHERE", -14f, 3f, 4f, 1.4f, 1.4f, 1.4f, "#8BC34A", "DYNAMIC");
        p.entities.add(critter);

        p.scriptCode =
                "// Green Hollow Valley - walk, jump, collect gems, dodge the mossling.\n" +
                "function onStart() {\n" +
                "  vortex.setBanner('GREEN HOLLOW - COLLECT THE GEMS');\n" +
                "  vortex.setLabel('hud_gems', 'GEMS 0', 6, 6, 15, '#FFFFFF');\n" +
                "}\n" +
                "function onUpdate(dt) {\n" +
                "  if (!vortex.exists('Hero')) return;\n" +
                "  var run = vortex.btnB() ? 15.5 : 9.5;\n" +
                "  vortex.setVel('Hero', vortex.joyX() * run, vortex.getVY('Hero'), -vortex.joyY() * run);\n" +
                "  if (vortex.exists('Mossling')) vortex.chaseTagAI('enemy', 'Hero', 3.4);\n" +
                "  if (vortex.getY('Hero') < -25) { vortex.setPos('Hero', 0, 6, 0); vortex.setVel('Hero', 0, 0, 0); }\n" +
                "  vortex.setProgressBar('hud_hp', 'VIGOR', vortex.getHealth(), 100, 50, 94, '#7BE3C1');\n" +
                "}\n" +
                "function onButton(btn) {\n" +
                "  if (btn === 'A') vortex.jump('Hero', 12.5);\n" +
                "}\n" +
                "function onCollide(a, b, tagA, tagB) {\n" +
                "  if (a === 'Hero' && tagB === 'coin') {\n" +
                "    vortex.particles(vortex.getX(b), vortex.getY(b), vortex.getZ(b), 24, '#7BE3C1');\n" +
                "    vortex.destroy(b);\n" +
                "    vortex.addScore(100);\n" +
                "    vortex.playSound('coin');\n" +
                "    vortex.setLabel('hud_gems', 'GEMS ' + vortex.countByTag('coin') + '  SCORE ' + vortex.getScore(), 6, 6, 15, '#FFFFFF');\n" +
                "  }\n" +
                "  if (a === 'Hero' && tagB === 'enemy') {\n" +
                "    vortex.addHealth(-6);\n" +
                "    vortex.playSound('hit');\n" +
                "    vortex.setBanner('OUCH!');\n" +
                "  }\n" +
                "}\n";
        p.putScriptFile("scripts/main.js", p.scriptCode);
        return p;
    }


    /**
     * v4.8.0 — "Blockfall": a Minecraft-style voxel sandbox.
     *
     * The world itself is owned by the voxel core (VoxelWorld/VoxelPlayer), so walking, jumping,
     * swimming, flying, breaking and placing all work on desktop and phone without a single line of
     * script. The starter script below is deliberately small and commented line by line: it shows a
     * beginner how to read the world from code (vortex.voxel*) and how to react to gameplay events.
     */
    private static GameProject buildVoxelBlockfall(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.scriptLanguage = "JS";
        p.voxelEnabled = true;
        p.voxelSeed = 4711;
        p.voxelSpawnX = 48f;
        p.voxelSpawnZ = 48f;
        p.voxelSpawnYaw = 200f;
        p.voxelRenderRadius = 46;

        // Daylight rig tuned for blocky surfaces: high sun, cool shadows, gentle ambient.
        p.skyColorHex = "#8FC7E8";
        p.sunYawDeg = 42f;
        p.sunPitchDeg = 62f;
        p.sunIntensity = 1.12f;
        p.ambientStrength = 0.46f;
        p.sunColorHex = "#FFF4DC";
        p.shadows = true;
        p.shadowStrength = 0.30f;
        p.fogNear = 48f;
        p.fogFar = 120f;
        p.gravityY = 26f;
        p.cameraMode = "FIRST_PERSON";
        p.musicEnabled = true;
        p.musicBpm = 96;
        p.musicPattern = ChiptuneGenerator.PRESET_SKYCHILL;
        p.iconAccentHex = "#7BE3C1";
        p.iconGlyph = "CUBE";

        // Scene props: the world is procedural, but a few glowing shards sit on it so the
        // scene tree is not empty and there is a first objective to chase.
        com.vortex.engine.core.voxel.VoxelRuntime vrt = p.ensureVoxelRuntime();
        float[][] shardSpots = {
                {43f, 43f}, {54f, 44f}, {40f, 55f}, {56f, 52f}, {48f, 38f}
        };
        for (int si = 0; si < shardSpots.length; si++) {
            float sx = shardSpots[si][0];
            float sz = shardSpots[si][1];
            int top = vrt.world.surfaceHeight((int) sx, (int) sz);
            Entity shard = Entity.create3D("Shard" + (si + 1), "shard", "OCTAHEDRON",
                    sx, top + 2.4f, sz, 0.9f, 1.5f, 0.9f, "#7BE3C1", "STATIC");
            shard.emissive = true;
            p.entities.add(shard);
        }
        int homeTop = vrt.world.surfaceHeight(48, 44);
        Entity beacon = Entity.create3D("HomeBeacon", "beacon", "PYRAMID",
                48f, homeTop + 1.0f, 44f, 1.4f, 2.4f, 1.4f, "#FACC15", "STATIC");
        beacon.emissive = true;
        p.entities.add(beacon);

        p.scriptCode =
                "// BLOCKFALL — a Minecraft-style voxel world.\n" +
                "//\n" +
                "// You do not need to write anything to play: the engine runs the block world\n" +
                "// (walking, jumping, swimming, flying, mining, building) by itself.\n" +
                "// This file is here so you can see HOW to talk to that world from code.\n" +
                "//\n" +
                "// Controls:  WASD move · SPACE jump · F fly · SHIFT sprint · CTRL sneak\n" +
                "//            LMB mine · RMB build · 1-9 pick block · B cycle block · middle click pipette\n" +
                "\n" +
                "var mined = 0;\n" +
                "var built = 0;\n" +
                "\n" +
                "function onStart() {\n" +
                "  vortex.setBanner('BLOCKFALL — mine the hills, build a home');\n" +
                "  // Pretend to welcome the player with a short line in the console.\n" +
                "  vortex.log('World seed: ' + vortex.voxelSeed());\n" +
                "}\n" +
                "\n" +
                "function onUpdate(dt) {\n" +
                "  // Runs every frame. The engine already handles walking, mining and building;\n" +
                "  // this is where YOUR game logic goes.\n" +
                "  onUpdateShards();\n" +
                "}\n" +
                "\n" +
                "// Called when the player mines a block or places one.\n" +
                "function onBlockMined(name) {\n" +
                "  mined = mined + 1;\n" +
                "  vortex.addScore(10);\n" +
                "  vortex.playSound('coin');\n" +
                "  vortex.setBanner('MINED ' + mined + '  ·  BUILT ' + built);\n" +
                "}\n" +
                "\n" +
                "function onBlockPlaced(name) {\n" +
                "  built = built + 1;\n" +
                "  vortex.setBanner('MINED ' + mined + '  ·  BUILT ' + built);\n" +
                "}\n" +
                "\n" +
                "// Example of editing the world from code: build a 5x5 stone floor under the player.\n" +
                "// Type 'buildFloor()' in this file and call it from onStart() to try it.\n" +
                "function buildFloor() {\n" +
                "  var px = floor(vortex.playerX());\n" +
                "  var py = floor(vortex.playerY()) - 1;\n" +
                "  var pz = floor(vortex.playerZ());\n" +
                "  for (var dx = -2; dx <= 2; dx++) {\n" +
                "    for (var dz = -2; dz <= 2; dz++) {\n" +
                "      vortex.voxelSet(px + dx, py, pz + dz, 'STONE');\n" +
                "    }\n" +
                "  }\n" +
                "  vortex.log('Floor built at ' + px + ',' + py + ',' + pz);\n" +
                "}\n" +
                "\n" +
                "// A shard is a floating collectible. The world is voxels, not physics bodies, so we\n" +
                "// simply check the distance every frame - the same pattern works for any pickup.\n" +
                "var shards = 0;\n" +
                "function onUpdateShards() {\n" +
                "  for (var i = 1; i <= 5; i++) {\n" +
                "    var name = 'Shard' + i;\n" +
                "    if (!vortex.exists(name)) continue;\n" +
                "    var dx = vortex.getX(name) - vortex.playerX();\n" +
                "    var dy = vortex.getY(name) - vortex.playerY();\n" +
                "    var dz = vortex.getZ(name) - vortex.playerZ();\n" +
                "    if (dx * dx + dy * dy + dz * dz < 6.0) {\n" +
                "      vortex.particles(vortex.getX(name), vortex.getY(name), vortex.getZ(name), 22, '#7BE3C1');\n" +
                "      vortex.destroy(name);\n" +
                "      vortex.playSound('coin');\n" +
                "      vortex.addScore(250);\n" +
                "      shards = shards + 1;\n" +
                "      vortex.setBanner('SHARD ' + shards + '/5');\n" +
                "      if (shards >= 5) vortex.setBanner('ALL SHARDS - now build something');\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
        p.putScriptFile("scripts/main.js", p.scriptCode);
        return p;
    }

    private static GameProject buildFrontierTerrain3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.scriptLanguage = "LUA";
        p.terrainSeed = 2026;
        p.terrainFreq = 0.06f;
        p.iconBgHex = "#0B1E26";
        p.iconAccentHex = "#00E676";
        p.iconGlyph = "CUBE";
        p.skyColorHex = "#0B0B0E";
        p.ambientColorHex = "#34546E";
        p.fogNear = 24f;
        p.fogFar = 85f;
        p.cameraDistance = 11.5f;
        p.cameraHeight = 6.0f;

        Entity terrain = Entity.create3D("WorldTerrain", "solid", "TERRAIN", 0f, 0f, 0f, 70f, 9.5f, 70f, "#2E7D32", "STATIC");
        terrain.checkerTexture = true;
        terrain.secondaryColorHex = "#1B5E20";
        p.entities.add(terrain);

        Entity player = Entity.create3D("Player", "player", "SPHERE", 0f, 3.0f, 0f, 1.2f, 1.2f, 1.2f, "#00E5FF", "DYNAMIC");
        player.emissive = true;
        p.entities.add(player);

        float[][] orbSpots = {
                {8f, 4.5f, -8f}, {-9f, 4.5f, -12f}, {12f, 5.2f, 10f},
                {-14f, 5.0f, 9f}, {0f, 5.5f, -20f}, {16f, 5.5f, -16f}
        };
        for (int i = 0; i < orbSpots.length; i++) {
            Entity orb = Entity.create3D("SkyOrb_" + (i + 1), "coin", "PYRAMID",
                    orbSpots[i][0], orbSpots[i][1], orbSpots[i][2], 1.1f, 1.5f, 1.1f, "#FFD740", "STATIC");
            orb.isTrigger = true;
            orb.emissive = true;
            orb.spinY = 125f;
            p.entities.add(orb);
        }

        p.scriptCode =
                "-- Procedural Frontier 3D — Powered by Lua 5.2 (LuaJ) + FastNoiseLite Terrain!\n" +
                "-- Explore 3D hills & valleys, jump with A, turbo dash with B!\n" +
                "function onStart()\n" +
                "    vortex.setBanner('EXPLORE THE PROCEDURAL 3D MOUNTAINS & COLLECT ORBS!')\n" +
                "end\n\n" +
                "function onUpdate(dt)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    local speed = vortex.btnB() and 16.5 or 10.0\n" +
                "    vortex.setVel('Player', vortex.joyX() * speed, vortex.getVY('Player'), -vortex.joyY() * speed)\n" +
                "    if vortex.btnB() then\n" +
                "        vortex.particles(vortex.getX('Player'), vortex.getY('Player'), vortex.getZ('Player'), 2, '#00E5FF')\n" +
                "    end\n" +
                "    if vortex.getY('Player') < -12 then\n" +
                "        vortex.setPos('Player', 0, 4, 0)\n" +
                "        vortex.setVel('Player', 0, 0, 0)\n" +
                "    end\n" +
                "end\n\n" +
                "function onButton(btn)\n" +
                "    if btn == 'A' then\n" +
                "        vortex.jump('Player', 13.0)\n" +
                "    end\n" +
                "end\n\n" +
                "function onCollide(a, b, tagA, tagB)\n" +
                "    if a == 'Player' and tagB == 'coin' then\n" +
                "        vortex.particles(vortex.getX(b), vortex.getY(b), vortex.getZ(b), 26, '#FFD740')\n" +
                "        vortex.destroy(b)\n" +
                "        vortex.addScore(150)\n" +
                "        vortex.playSound('coin')\n" +
                "        if vortex.countByTag('coin') == 0 then\n" +
                "            vortex.playSound('powerup')\n" +
                "            vortex.setBanner('ALL FRONTIER ORBS FOUND! SCORE: ' .. vortex.getScore())\n" +
                "        end\n" +
                "    end\n" +
                "end\n";
        return p;
    }

    private static GameProject buildNeonHorizon3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.scriptLanguage = "VXS";
        p.iconBgHex = "#090D18";
        p.iconAccentHex = "#00E5FF";
        p.iconGlyph = "ROCKET";
        p.skyColorHex = "#0B0B0E";
        p.ambientColorHex = "#1E3050";
        p.cameraDistance = 9.5f;
        p.cameraHeight = 4.6f;

        p.customObjMeshes.put("starfighter",
                "# Vortex Custom 3D Starfighter (.obj)\n" +
                "v  0.0  0.15 -0.85\nv -0.65 -0.10  0.65\nv  0.65 -0.10  0.65\n" +
                "v  0.0  0.42  0.35\nv  0.0 -0.22  0.45\n" +
                "f 1 2 4\nf 1 4 3\nf 4 2 3\nf 1 5 2\nf 1 3 5\nf 5 3 2\n");

        Entity floor = Entity.create3D("TrackFloor", "solid", "CUBE", 0f, -0.5f, -35f, 18f, 1f, 120f, "#101B2D", "STATIC");
        floor.checkerTexture = true; floor.secondaryColorHex = "#09101C";
        p.entities.add(floor);

        Entity player = Entity.create3D("Player", "player", "CUSTOM_OBJ", 0f, 0.8f, 4f, 1.4f, 1.1f, 1.6f, "#00E5FF", "DYNAMIC");
        player.customObjName = "starfighter"; player.emissive = true;
        p.entities.add(player);

        float[][] hazards = {{-3.5f, 1f, -8f}, {3.2f, 1f, -15f}, {0f, 1.2f, -23f}, {-4.2f, 1.2f, -32f}, {3.8f, 1.2f, -40f}, {0f, 1.4f, -50f}};
        for (int i = 0; i < hazards.length; i++) {
            Entity ob = Entity.create3D("Barrier_" + (i + 1), "hazard", "PYRAMID", hazards[i][0], hazards[i][1], hazards[i][2], 2f, 2.2f, 2f, "#FF4081", "STATIC");
            ob.emissive = true; p.entities.add(ob);
        }
        float[][] crystals = {{0f, 1.1f, -5f}, {-3.2f, 1.2f, -12f}, {3f, 1.2f, -19f}, {0f, 2.4f, -23f}, {2.5f, 1.2f, -35f}, {-2.5f, 1.2f, -45f}};
        for (int i = 0; i < crystals.length; i++) {
            Entity c = Entity.create3D("Crystal_" + (i + 1), "coin", "SPHERE", crystals[i][0], crystals[i][1], crystals[i][2], 1f, 1f, 1f, "#FFD740", "STATIC");
            c.isTrigger = true; c.emissive = true; c.spinY = 140f; p.entities.add(c);
        }
        Entity portal = Entity.create3D("WarpGate", "goal", "CYLINDER", 0f, 2.2f, -64f, 4.5f, 4.4f, 1.2f, "#00E676", "STATIC");
        portal.isTrigger = true; portal.emissive = true; portal.spinY = 65f; p.entities.add(portal);

        p.scriptCode =
                "// Neon Horizon 3D — VortexScript (.vxs)\n" +
                "let speed = 13.0;\nlet finished = false;\n" +
                "fn onStart() { setBanner('COLLECT CRYSTALS & REACH THE WARP GATE!'); }\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player') || finished) { return; }\n" +
                "    let boost = 1.0;\n" +
                "    if (btnB()) { boost = 1.85; particles(getX('Player'), getY('Player'), getZ('Player') + 1.1, 3, '#00E5FF'); }\n" +
                "    setVel('Player', joyX() * 11.5, getVY('Player'), -speed * boost);\n" +
                "    setRot('Player', 0, 0, -joyX() * 24);\n" +
                "    if (getY('Player') < -6) { setPos('Player', 0, 1.5, 4); setVel('Player', 0, 0, 0); addHealth(-20); playSound('hit'); }\n" +
                "}\n" +
                "fn onButton(btn) { if (btn == 'A') { jump('Player', 11.5); } }\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if (a == 'Player' && tagB == 'coin') { particles(getX(b), getY(b), getZ(b), 22, '#FFD740'); destroy(b); addScore(100); playSound('coin'); }\n" +
                "    if (a == 'Player' && tagB == 'hazard') { particles(getX('Player'), getY('Player'), getZ('Player'), 30, '#FF4081'); shake(0.45, 0.3); addHealth(-25); playSound('explosion'); destroy(b); }\n" +
                "    if (a == 'Player' && tagB == 'goal') { finished = true; setVel('Player', 0, 0, 0); addScore(500); playSound('powerup'); setBanner('SECTOR CLEARED! SCORE: ' + getScore()); }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildSkyIslands3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title; p.packageName = pkgName; p.dimensionMode = "3D"; p.scriptLanguage = "VXS";
        p.skyColorHex = "#0B0B0E"; p.ambientColorHex = "#2A2A30"; p.cameraDistance = 11f; p.cameraHeight = 5.8f;
        float[][] islands = {{0, -0.5f, 0, 7, 1, 7}, {0, 0.5f, -8, 4.5f, 1, 4.5f}, {5, 1.6f, -13, 4.5f, 1, 4.5f}, {0, 2.8f, -19, 4.5f, 1, 4.5f}, {0, 4.0f, -26, 6.5f, 1, 6.5f}};
        for (int i = 0; i < islands.length; i++) {
            float[] d = islands[i];
            Entity isl = Entity.create3D("Island_" + (i + 1), "solid", "CUBE", d[0], d[1], d[2], d[3], d[4], d[5], "#26A69A", "STATIC");
            isl.checkerTexture = true; isl.secondaryColorHex = "#1B7A70"; p.entities.add(isl);
            if (i > 0 && i < islands.length - 1) {
                Entity relic = Entity.create3D("Relic_" + i, "coin", "PYRAMID", d[0], d[1] + 1.4f, d[2], 0.9f, 1.1f, 0.9f, "#FFD54F", "STATIC");
                relic.isTrigger = true; relic.emissive = true; relic.spinY = 120f; p.entities.add(relic);
            }
        }
        p.entities.add(Entity.create3D("Player", "player", "SPHERE", 0f, 1.2f, 0f, 1.1f, 1.1f, 1.1f, "#00E5FF", "DYNAMIC"));
        Entity beacon = Entity.create3D("SkyTotem", "goal", "CYLINDER", 0f, 5.8f, -26f, 1.6f, 2.8f, 1.6f, "#7C4DFF", "STATIC");
        beacon.isTrigger = true; beacon.emissive = true; p.entities.add(beacon);

        p.scriptCode =
                "fn onStart() { setBanner('LEAP ACROSS THE 3D SKY ISLANDS!'); }\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    setVel('Player', joyX() * 8.5, getVY('Player'), -joyY() * 8.5);\n" +
                "    if (getY('Player') < -8) { setPos('Player', 0, 2.0, 0); setVel('Player', 0, 0, 0); playSound('hit'); }\n" +
                "}\n" +
                "fn onButton(btn) { if (btn == 'A') { jump('Player', 12.2); } }\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if (a == 'Player' && tagB == 'coin') { particles(getX(b), getY(b), getZ(b), 20, '#FFD54F'); destroy(b); addScore(150); playSound('coin'); }\n" +
                "    if (a == 'Player' && tagB == 'goal') { playSound('powerup'); setBanner('SUMMIT REACHED! SCORE ' + getScore()); }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildPlatformer2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title; p.packageName = pkgName; p.dimensionMode = "2D"; p.scriptLanguage = "VXS";
        p.useBox2D = true; p.skyColorHex = "#0B0B0E"; p.zoom2D = 16f; p.gravityY = -26f;

        p.customSprites.put("knight", PixelSpriteCodec.createDefaultKnightSprite());

        p.entities.add(Entity.create2D("Ground", "solid", "RECT", 8f, -3.5f, 36f, 2f, "#23354D", "STATIC"));
        p.entities.add(Entity.create2D("Plat_1", "solid", "RECT", 5f, -0.2f, 4.5f, 0.7f, "#2E4F6E", "STATIC"));
        p.entities.add(Entity.create2D("Plat_2", "solid", "RECT", 11.5f, 2.2f, 4.5f, 0.7f, "#2E4F6E", "STATIC"));
        p.entities.add(Entity.create2D("Plat_3", "solid", "RECT", 18.0f, 4.2f, 5.0f, 0.7f, "#2E4F6E", "STATIC"));

        Entity hero = Entity.create2D("Player", "player", "SPRITE_HERO", -2f, 0f, 1.15f, 1.35f, "#00E5FF", "DYNAMIC");
        hero.fixedRotation = true;
        p.entities.add(hero);

        Entity slime1 = Entity.create2D("Slime_1", "enemy", "SPRITE_SLIME", 6f, -1.8f, 1.2f, 1.0f, "#FF5252", "KINEMATIC");
        slime1.vx = 2.6f;
        p.entities.add(slime1);

        float[][] coins = {{5f, 1.1f}, {11.5f, 3.5f}, {14.5f, -1.5f}, {18.0f, 5.6f}};
        for (int i = 0; i < coins.length; i++) {
            Entity c = Entity.create2D("Coin_" + (i + 1), "coin", "SPRITE_COIN", coins[i][0], coins[i][1], 0.85f, 0.85f, "#FFD740", "STATIC");
            c.isTrigger = true; p.entities.add(c);
        }
        Entity flag = Entity.create2D("ExitCrystal", "goal", "CIRCLE", 20.0f, 5.8f, 1.3f, 1.3f, "#00E676", "STATIC");
        flag.isTrigger = true; p.entities.add(flag);

        p.scriptCode =
                "// Starbound Cavern 2D — VortexScript + JBox2D\n" +
                "fn onStart() { setBanner('COLLECT COINS & STOMP SLIMES!'); }\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    setVel('Player', joyX() * 9.0, getVY('Player'));\n" +
                "    if (exists('Slime_1')) {\n" +
                "        if (getX('Slime_1') > 11.0) { setVel('Slime_1', -2.6, 0); }\n" +
                "        if (getX('Slime_1') < 2.0)  { setVel('Slime_1', 2.6, 0); }\n" +
                "    }\n" +
                "    if (getY('Player') < -9.0) { setPos('Player', -2, 1); setVel('Player', 0, 0); addHealth(-20); playSound('hit'); }\n" +
                "}\n" +
                "fn onButton(btn) { if (btn == 'A') { jump('Player', 13.8); } }\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    let other = (a == 'Player') ? '' : '';\n" +
                "    if ((a == 'Player' && tagB == 'coin') || (b == 'Player' && tagA == 'coin')) {\n" +
                "        let c = (tagB == 'coin') ? b : a;\n" +
                "        particles(getX(c), getY(c), 0, 18, '#FFD740'); destroy(c); addScore(100); playSound('coin');\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'enemy') {\n" +
                "        if (getY('Player') > getY(b) + 0.45) { particles(getX(b), getY(b), 0, 26, '#FF5252'); destroy(b); setVel('Player', getVX('Player'), 10.5); addScore(250); playSound('explosion'); }\n" +
                "        else { shake(0.35, 0.25); addHealth(-25); setPos('Player', -2, 1); playSound('hit'); }\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'goal') { playSound('powerup'); setBanner('STAGE CLEAR! SCORE: ' + getScore()); }\n" +
                "}\n";
        // Note: VortexScript doesn't use ternary '? :' — keep pure if/else syntax!
        p.scriptCode =
                "// Starbound Cavern 2D — VortexScript + JBox2D\n" +
                "fn onStart() { setBanner('COLLECT COINS & STOMP SLIMES!'); }\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    setVel('Player', joyX() * 9.0, getVY('Player'));\n" +
                "    if (exists('Slime_1')) {\n" +
                "        if (getX('Slime_1') > 11.0) { setVel('Slime_1', -2.6, 0); }\n" +
                "        if (getX('Slime_1') < 2.0)  { setVel('Slime_1', 2.6, 0); }\n" +
                "    }\n" +
                "    if (getY('Player') < -9.0) { setPos('Player', -2, 1); setVel('Player', 0, 0); addHealth(-20); playSound('hit'); }\n" +
                "}\n" +
                "fn onButton(btn) { if (btn == 'A') { jump('Player', 13.8); } }\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if (a == 'Player' && tagB == 'coin') { particles(getX(b), getY(b), 0, 18, '#FFD740'); destroy(b); addScore(100); playSound('coin'); }\n" +
                "    if (b == 'Player' && tagA == 'coin') { particles(getX(a), getY(a), 0, 18, '#FFD740'); destroy(a); addScore(100); playSound('coin'); }\n" +
                "    if (a == 'Player' && tagB == 'enemy') {\n" +
                "        if (getY('Player') > getY(b) + 0.45) { particles(getX(b), getY(b), 0, 26, '#FF5252'); destroy(b); setVel('Player', getVX('Player'), 10.5); addScore(250); playSound('explosion'); }\n" +
                "        else { shake(0.35, 0.25); addHealth(-25); setPos('Player', -2, 1); playSound('hit'); }\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'goal') { playSound('powerup'); setBanner('STAGE CLEAR! SCORE: ' + getScore()); }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildSpaceShooter2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title; p.packageName = pkgName; p.dimensionMode = "2D"; p.scriptLanguage = "LUA";
        p.useBox2D = false; // Crisp arcade kinematic collision
        p.skyColorHex = "#0B0B0E"; p.cameraMode = "FIXED"; p.zoom2D = 18f; p.gravityY = 0f;
        p.entities.add(Entity.create2D("Player", "player", "SPRITE_SHIP", 0f, -5.5f, 1.3f, 1.4f, "#00E5FF", "KINEMATIC"));
        for (int i = 0; i < 5; i++) {
            Entity ast = Entity.create2D("Meteor_" + i, "enemy", "CIRCLE", -6f + i * 3f, 5f + (i % 2) * 2.5f, 1.35f, 1.35f, "#FF7043", "KINEMATIC");
            ast.vy = -2.8f - (i * 0.25f); ast.spinZ = 95f; p.entities.add(ast);
        }
        p.scriptCode =
                "-- Nova Blaster 2D — Lua 5.2 Space Shooter\n" +
                "local shotId = 0\n" +
                "local spawnTimer = 0\n\n" +
                "function onStart()\n" +
                "    vortex.setBanner('BLAST METEORS WITH A / B!')\n" +
                "end\n\n" +
                "function onUpdate(dt)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    local nx = vortex.clamp(vortex.getX('Player') + vortex.joyX() * 12 * dt, -11, 11)\n" +
                "    local ny = vortex.clamp(vortex.getY('Player') + vortex.joyY() * 10 * dt, -7.5, 6.5)\n" +
                "    vortex.setPos('Player', nx, ny)\n" +
                "    spawnTimer = spawnTimer + dt\n" +
                "    if spawnTimer > 1.1 then\n" +
                "        spawnTimer = 0\n" +
                "        shotId = shotId + 1\n" +
                "        local m = 'Meteor_W' .. shotId\n" +
                "        vortex.spawn2D(m, 'enemy', 'CIRCLE', vortex.rand(-10, 10), 9.5, 1.3, 1.3, '#FF5252', 'KINEMATIC')\n" +
                "        vortex.setVel(m, vortex.rand(-1.2, 1.2), vortex.rand(-4.8, -2.5))\n" +
                "    end\n" +
                "end\n\n" +
                "function onButton(btn)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    shotId = shotId + 1\n" +
                "    local b = 'Laser_' .. shotId\n" +
                "    vortex.spawn2D(b, 'projectile', 'RECT', vortex.getX('Player'), vortex.getY('Player') + 1.0, 0.25, 0.85, '#00E676', 'KINEMATIC')\n" +
                "    vortex.setVel(b, 0, 18.0)\n" +
                "    vortex.playSound('laser')\n" +
                "end\n\n" +
                "function onCollide(a, b, tagA, tagB)\n" +
                "    if (tagA == 'projectile' and tagB == 'enemy') or (tagA == 'enemy' and tagB == 'projectile') then\n" +
                "        vortex.particles(vortex.getX(a), vortex.getY(a), 0, 22, '#FF7043')\n" +
                "        vortex.destroy(a); vortex.destroy(b)\n" +
                "        vortex.addScore(100); vortex.playSound('explosion')\n" +
                "    end\n" +
                "end\n";
        return p;
    }

    private static GameProject buildBlankLua(String title, String pkgName, boolean is3D) {
        GameProject p = new GameProject();
        p.title = title; p.packageName = pkgName; p.dimensionMode = is3D ? "3D" : "2D"; p.scriptLanguage = "LUA";
        p.customSprites.put("knight", PixelSpriteCodec.createDefaultKnightSprite());
        if (is3D) {
            Entity floor = Entity.create3D("Floor", "solid", "CUBE", 0f, -0.5f, 0f, 18f, 1f, 18f, "#1B2838", "STATIC");
            floor.checkerTexture = true; p.entities.add(floor);
            p.entities.add(Entity.create3D("Player", "player", "CUBE", 0f, 1f, 0f, 1f, 1f, 1f, "#00E5FF", "DYNAMIC"));
            p.scriptCode =
                    "-- Lua 5.2 (LuaJ) 3D Script\n" +
                    "function onStart()\n    vortex.setBanner('MY LUA 3D GAME')\nend\n\n" +
                    "function onUpdate(dt)\n    vortex.setVel('Player', vortex.joyX() * 8, vortex.getVY('Player'), -vortex.joyY() * 8)\nend\n\n" +
                    "function onButton(btn)\n    if btn == 'A' then vortex.jump('Player', 11.5) end\nend\n";
        } else {
            p.entities.add(Entity.create2D("Floor", "solid", "RECT", 0f, -3.5f, 22f, 1.5f, "#1B2838", "STATIC"));
            p.entities.add(Entity.create2D("Player", "player", "CUSTOM_SPRITE", 0f, 0f, 1.4f, 1.4f, "#00E5FF", "DYNAMIC"));
            p.entities.get(1).customSpriteName = "knight";
            p.scriptCode =
                    "-- Lua 5.2 (LuaJ) + JBox2D 2D Script\n" +
                    "function onStart()\n    vortex.setBanner('MY LUA + JBOX2D GAME')\nend\n\n" +
                    "function onUpdate(dt)\n    vortex.setVel('Player', vortex.joyX() * 8.5, vortex.getVY('Player'))\nend\n\n" +
                    "function onButton(btn)\n    if btn == 'A' then vortex.jump('Player', 12.5) end\nend\n";
        }
        return p;
    }

    private static GameProject buildCyberMazeAI2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "2D";
        p.scriptLanguage = "LUA";
        p.useBox2D = true;
        p.gravityX = 0f;
        p.gravityY = 0f; // Top-down labyrinth!
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.zoom2D = 16.5f;
        p.skyColorHex = "#0B0B0E";
        p.iconBgHex = "#0A1024";
        p.iconAccentHex = "#00E5FF";
        p.iconGlyph = "ROCKET";
        p.musicEnabled = true;
        p.musicBpm = 134;
        p.musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK;

        p.customSprites.put("knight", PixelSpriteCodec.createDefaultKnightSprite());
        p.customSprites.put("slime", PixelSpriteCodec.createSlimeSprite());
        p.customSprites.put("crate", PixelSpriteCodec.createCrateSprite());

        // Outer neon arena walls
        p.entities.add(Entity.create2D("WallTop", "solid", "RECT", 0f, 9.5f, 26f, 1.0f, "#183052", "STATIC"));
        p.entities.add(Entity.create2D("WallBot", "solid", "RECT", 0f, -9.5f, 26f, 1.0f, "#183052", "STATIC"));
        p.entities.add(Entity.create2D("WallLeft", "solid", "RECT", -12.5f, 0f, 1.0f, 20f, "#183052", "STATIC"));
        p.entities.add(Entity.create2D("WallRight", "solid", "RECT", 12.5f, 0f, 1.0f, 20f, "#183052", "STATIC"));

        // Interior maze obstacles that Theta* / A* AI navigates around!
        float[][] mazeBlocks = {
                {-6f, 4.2f, 5.5f, 1.1f}, {6f, 4.2f, 5.5f, 1.1f},
                {0f, 1.2f, 1.2f, 5.2f},
                {-6f, -3.8f, 5.5f, 1.1f}, {6f, -3.8f, 5.5f, 1.1f},
                {-3.2f, 0f, 1.1f, 3.6f}, {3.2f, 0f, 1.1f, 3.6f}
        };
        for (int i = 0; i < mazeBlocks.length; i++) {
            float[] m = mazeBlocks[i];
            p.entities.add(Entity.create2D("MazeWall_" + i, "solid", "RECT", m[0], m[1], m[2], m[3], "#005B7F", "STATIC"));
        }

        // Player cyber-runner
        Entity player = Entity.create2D("Player", "player", "CUSTOM_SPRITE", 0f, -6.8f, 1.25f, 1.25f, "#00E5FF", "DYNAMIC");
        player.customSpriteName = "knight";
        player.drag = 0.25f;
        p.entities.add(player);

        // Autonomous Theta* Pathfinding Hunters!
        float[][] hunterSpawns = {{-9.5f, 7.0f}, {9.5f, 7.0f}, {0f, 7.2f}};
        for (int i = 0; i < hunterSpawns.length; i++) {
            Entity h = Entity.create2D("Hunter_" + (i + 1), "enemy", "CUSTOM_SPRITE",
                    hunterSpawns[i][0], hunterSpawns[i][1], 1.25f, 1.25f, "#FF1744", "DYNAMIC");
            h.customSpriteName = "slime";
            h.drag = 0.15f;
            p.entities.add(h);
        }

        // Tweened Cyber Data Cores (coins) around the labyrinth
        float[][] corePts = {
                {-9.5f, -7f}, {9.5f, -7f}, {-9.5f, 0f}, {9.5f, 0f},
                {-6f, 7f}, {6f, 7f}, {-1.8f, 4.2f}, {1.8f, 4.2f}, {0f, -2.5f}
        };
        for (int i = 0; i < corePts.length; i++) {
            Entity c = Entity.create2D("DataCore_" + i, "coin", "CIRCLE", corePts[i][0], corePts[i][1], 0.72f, 0.72f, "#FFD54F", "STATIC");
            c.isTrigger = true;
            p.entities.add(c);
        }

        // Pre-populate Vortex Flow Visual Rules so users can inspect or edit no-code logic immediately!
        p.flowRules.add(new VortexFlowCompiler.FlowRule("ON_START", "Player", "", "PLAY_MUSIC", 134f, "CYBERPUNK"));
        p.flowRules.add(new VortexFlowCompiler.FlowRule("ON_START", "coin", "", "FLOAT_TWEEN_TAG", 0.35f, "SINE"));
        p.flowRules.add(new VortexFlowCompiler.FlowRule("JOYSTICK_MOVE", "Player", "", "MOVE_2D_TOPDOWN", 8.2f, ""));
        p.flowRules.add(new VortexFlowCompiler.FlowRule("AI_HUNTER_TICK", "enemy", "Player", "AI_CHASE_PATHFIND", 4.35f, ""));
        p.flowRules.add(new VortexFlowCompiler.FlowRule("ON_BTN_A", "Player", "", "SHOOT_LASER_2D", 18.0f, "#00E5FF"));
        p.flowRules.add(new VortexFlowCompiler.FlowRule("ON_COLLIDE", "player", "coin", "COLLECT_SCORE_DESTROY", 150f, "#FFD54F"));
        p.flowRules.add(new VortexFlowCompiler.FlowRule("ON_COLLIDE", "player", "enemy", "DAMAGE_SHAKE", 20f, "#FF1744"));

        p.scriptCode =
                "-- Cyber Hunters 2D (v3.0 Showcase: Theta* Pathfinding + Universal Tween Engine + Chiptune Synth)\n" +
                "local pulseCd = 0\n\n" +
                "function onStart()\n" +
                "    vortex.setFixedRotation('Player', true)\n" +
                "    vortex.playMusic('CYBERPUNK', 134)\n" +
                "    vortex.tweenTagYoyo('coin', 0.32, 0.85, 'SINE')\n" +
                "    vortex.setBanner('THETA* AI LABYRINTH — COLLECT ALL 9 CORES! (A=EMP PULSE)')\n" +
                "end\n\n" +
                "function onUpdate(dt)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    pulseCd = math.max(0, pulseCd - dt)\n" +
                "    vortex.setVel('Player', vortex.joyX() * 8.2, vortex.joyY() * 8.2)\n" +
                "    -- Theta* Any-Angle Obstacle-Avoiding Pathfinding on all 'enemy' hunters!\n" +
                "    vortex.chaseTagAI('enemy', 'Player', 4.35)\n" +
                "    local left = vortex.countByTag('coin')\n" +
                "    if left == 0 then\n" +
                "        vortex.setBanner('SYSTEM HACKED! ALL CORES SECURED! SCORE: ' .. vortex.getScore())\n" +
                "    end\n" +
                "end\n\n" +
                "function onButton(btn)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    if btn == 'A' and pulseCd <= 0 then\n" +
                "        pulseCd = 1.6\n" +
                "        local px, py = vortex.getX('Player'), vortex.getY('Player')\n" +
                "        vortex.particles(px, py, 0, 34, '#00E5FF')\n" +
                "        vortex.playSound('powerup')\n" +
                "        vortex.shake(0.30, 0.20)\n" +
                "        vortex.tweenScale('Player', 1.65, 1.65, 1.0, 0.22, 'ELASTIC', 1)\n" +
                "        for i = 1, 3 do\n" +
                "            local h = 'Hunter_' .. i\n" +
                "            if vortex.exists(h) and vortex.distance('Player', h) < 5.5 then\n" +
                "                local dx = vortex.getX(h) - px\n" +
                "                local dy = vortex.getY(h) - py\n" +
                "                vortex.setPos(h, px + dx * 2.2, py + dy * 2.2, 0)\n" +
                "                vortex.particles(vortex.getX(h), vortex.getY(h), 0, 18, '#FF1744')\n" +
                "            end\n" +
                "        end\n" +
                "    end\n" +
                "end\n\n" +
                "function onCollide(a, b, tagA, tagB)\n" +
                "    local other, otherTag = nil, nil\n" +
                "    if tagA == 'player' then other = b; otherTag = tagB\n" +
                "    elseif tagB == 'player' then other = a; otherTag = tagA end\n" +
                "    if not other then return end\n\n" +
                "    if otherTag == 'coin' then\n" +
                "        vortex.particles(vortex.getX(other), vortex.getY(other), 0, 22, '#FFD54F')\n" +
                "        vortex.destroy(other)\n" +
                "        vortex.addScore(150)\n" +
                "        vortex.playSound('coin')\n" +
                "    elseif otherTag == 'enemy' then\n" +
                "        vortex.addHealth(-20)\n" +
                "        vortex.shake(0.45, 0.25)\n" +
                "        vortex.playSound('hit')\n" +
                "        vortex.particles(vortex.getX('Player'), vortex.getY('Player'), 0, 25, '#FF1744')\n" +
                "        vortex.setPos(other, 0, 7.2, 0)\n" +
                "        if vortex.getHealth() <= 0 then\n" +
                "            vortex.setBanner('CAUGHT BY THETA* HUNTERS! PRESS PLAY TO RETRY')\n" +
                "            vortex.destroy('Player')\n" +
                "        end\n" +
                "    end\n" +
                "end\n";
        return p;
    }

    private static GameProject buildCitadelTween3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.scriptLanguage = "LUA";
        p.skyColorHex = "#0B0B0E";
        p.ambientColorHex = "#2A3256";
        p.fogEnabled = true;
        p.fogNear = 20f;
        p.fogFar = 78f;
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.cameraDistance = 12.5f;
        p.cameraHeight = 6.5f;
        p.iconBgHex = "#120B24";
        p.iconAccentHex = "#FF4081";
        p.iconGlyph = "SWORD";
        p.musicEnabled = true;
        p.musicBpm = 144;
        p.musicPattern = ChiptuneGenerator.PRESET_BOSS;

        Entity floor = Entity.create3D("CitadelBridge", "solid", "CUBE", 0f, -0.6f, -12f, 14f, 1.2f, 44f, "#1A243B", "STATIC");
        floor.checkerTexture = true;
        p.entities.add(floor);

        Entity player = Entity.create3D("Player", "player", "SPHERE", 0f, 1.2f, 6f, 1.1f, 1.1f, 1.1f, "#00E5FF", "DYNAMIC");
        p.entities.add(player);

        // Tweened Crusher Gates (Universal Tween Engine Bounce & Sine Yoyo!)
        for (int i = 0; i < 3; i++) {
            float gz = -2f - i * 8.5f;
            Entity gate = Entity.create3D("Gate_" + (i + 1), "hazard", "CUBE", -4.2f + (i % 2) * 8.4f, 1.4f, gz, 4.2f, 2.6f, 1.2f, "#FF1744", "KINEMATIC");
            gate.isTrigger = true;
            p.entities.add(gate);
        }

        // Floating Citadel Boss Core + Relic Shards
        for (int i = 0; i < 5; i++) {
            Entity shard = Entity.create3D("Shard_" + i, "coin", "PYRAMID", (i % 2 == 0 ? -2.8f : 2.8f), 1.4f, 1f - i * 5.5f, 0.95f, 1.2f, 0.95f, "#FFD54F", "STATIC");
            shard.spinY = 120f;
            shard.isTrigger = true;
            p.entities.add(shard);
        }

        Entity boss = Entity.create3D("CitadelCore", "enemy", "CYLINDER", 0f, 2.2f, -28f, 2.8f, 3.2f, 2.8f, "#E040FB", "KINEMATIC");
        boss.spinY = 90f;
        p.entities.add(boss);

        p.scriptCode =
                "-- Neon Citadel 3D (v3.0 Showcase: Universal Tween Engine + 3D Raycast Laser + Boss Chiptune)\n" +
                "local bossHp = 5\n\n" +
                "function onStart()\n" +
                "    vortex.playMusic('BOSS', 144)\n" +
                "    -- Universal Tween Engine infinite Yoyo animations!\n" +
                "    vortex.tweenPos('Gate_1', 4.2, 1.4, -2.0, 1.35, 'SINE', -1)\n" +
                "    vortex.tweenPos('Gate_2', -4.2, 1.4, -10.5, 1.15, 'QUAD', -1)\n" +
                "    vortex.tweenPos('Gate_3', 4.2, 1.4, -19.0, 0.95, 'BOUNCE', -1)\n" +
                "    vortex.tweenPos('CitadelCore', 0, 4.2, -28.0, 1.4, 'SINE', -1)\n" +
                "    vortex.tweenTagYoyo('coin', 0.65, 0.9, 'SINE')\n" +
                "    vortex.setBanner('CITADEL SIEGE 3D — A=JUMP, B=3D RAYCAST BEAM!')\n" +
                "end\n\n" +
                "function onUpdate(dt)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    vortex.setVel('Player', vortex.joyX() * 9.0, vortex.getVY('Player'), -vortex.joyY() * 9.0)\n" +
                "    if vortex.getY('Player') < -8 then\n" +
                "        vortex.setPos('Player', 0, 2.0, 6.0)\n" +
                "        vortex.addHealth(-20)\n" +
                "    end\n" +
                "end\n\n" +
                "function onButton(btn)\n" +
                "    if not vortex.exists('Player') then return end\n" +
                "    if btn == 'A' then\n" +
                "        vortex.jump('Player', 12.0)\n" +
                "        vortex.playSound('jump')\n" +
                "    elseif btn == 'B' then\n" +
                "        -- Instant 3D Raycast Plasma Beam forward (-Z)!\n" +
                "        local px, py, pz = vortex.getX('Player'), vortex.getY('Player'), vortex.getZ('Player')\n" +
                "        vortex.playSound('laser')\n" +
                "        vortex.particles(px, py, pz - 2.0, 18, '#00E5FF')\n" +
                "        local hit = vortex.raycast(px, py, pz - 0.8, 0, 0, -1, 38.0)\n" +
                "        if hit == 'CitadelCore' then\n" +
                "            bossHp = bossHp - 1\n" +
                "            vortex.tweenScale('CitadelCore', 3.6, 4.0, 3.6, 0.25, 'ELASTIC', 1)\n" +
                "            vortex.particles(vortex.getX('CitadelCore'), vortex.getY('CitadelCore'), -28, 35, '#E040FB')\n" +
                "            vortex.shake(0.45, 0.22)\n" +
                "            vortex.addScore(250)\n" +
                "            if bossHp <= 0 then\n" +
                "                vortex.destroy('CitadelCore')\n" +
                "                vortex.playSound('explosion')\n" +
                "                vortex.setBanner('CITADEL CORE DESTROYED! VICTORY! SCORE: ' .. vortex.getScore())\n" +
                "            else\n" +
                "                vortex.setBanner('CORE HIT BY 3D RAYCAST! BOSS HP: ' .. bossHp)\n" +
                "            end\n" +
                "        end\n" +
                "    end\n" +
                "end\n\n" +
                "function onCollide(a, b, tagA, tagB)\n" +
                "    local other, otherTag = nil, nil\n" +
                "    if tagA == 'player' then other = b; otherTag = tagB\n" +
                "    elseif tagB == 'player' then other = a; otherTag = tagA end\n" +
                "    if not other then return end\n" +
                "    if otherTag == 'coin' then\n" +
                "        vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 20, '#FFD54F')\n" +
                "        vortex.destroy(other)\n" +
                "        vortex.addScore(100)\n" +
                "        vortex.playSound('coin')\n" +
                "    elseif otherTag == 'hazard' then\n" +
                "        vortex.addHealth(-25)\n" +
                "        vortex.shake(0.5, 0.25)\n" +
                "        vortex.playSound('hit')\n" +
                "        vortex.setPos('Player', 0, 2.0, 6.0)\n" +
                "    end\n" +
                "end\n";
        return p;
    }

    private static GameProject buildNeonSurvivorsJs2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "2D";
        p.scriptLanguage = "JS";
        p.useBox2D = true;
        p.gravityX = 0f;
        p.gravityY = 0f;
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.zoom2D = 16.0f;
        p.skyColorHex = "#0B0B0E";
        p.iconBgHex = "#091022";
        p.iconAccentHex = "#00E676";
        p.iconGlyph = "STAR";
        p.musicEnabled = true;
        p.musicBpm = 138;
        p.musicPattern = ChiptuneGenerator.PRESET_CYBERPUNK;

        p.customSprites.put("knight_1", PixelSpriteCodec.createDefaultKnightSprite());
        p.customSprites.put("knight_2", PixelSpriteCodec.createKnightRunFrame2Sprite());
        p.customSprites.put("drone_1", PixelSpriteCodec.createDroneFrame1Sprite());
        p.customSprites.put("drone_2", PixelSpriteCodec.createDroneFrame2Sprite());
        p.customSprites.put("gem", PixelSpriteCodec.createGemSprite());

        // Arena Bounds + Tactical Pillars for Theta* Pathfinding (Pure Monochrome Carbon/Titanium)
        p.entities.add(Entity.create2D("ArenaN", "solid", "RECT", 0f, 11f, 30f, 1f, "#27272A", "STATIC"));
        p.entities.add(Entity.create2D("ArenaS", "solid", "RECT", 0f, -11f, 30f, 1f, "#27272A", "STATIC"));
        p.entities.add(Entity.create2D("ArenaW", "solid", "RECT", -14.5f, 0f, 1f, 23f, "#27272A", "STATIC"));
        p.entities.add(Entity.create2D("ArenaE", "solid", "RECT", 14.5f, 0f, 1f, 23f, "#27272A", "STATIC"));

        float[][] pillars = {{-5.5f, 4.5f}, {5.5f, 4.5f}, {-5.5f, -4.5f}, {5.5f, -4.5f}};
        for (int i = 0; i < pillars.length; i++) {
            p.entities.add(Entity.create2D("Pillar_" + i, "solid", "RECT", pillars[i][0], pillars[i][1], 2.1f, 2.1f, "#3F3F46", "STATIC"));
        }

        Entity player = Entity.create2D("Player", "player", "CUSTOM_SPRITE", 0f, 0f, 1.38f, 1.38f, "#FFFFFF", "DYNAMIC");
        player.customSpriteName = "knight_1";
        player.spriteFrames = "knight_1,knight_2";
        player.animFps = 7.5f;
        p.entities.add(player);

        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI * 2.0 / 6.0;
            Entity drone = Entity.create2D("Drone_" + i, "enemy", "CUSTOM_SPRITE",
                    (float) Math.cos(ang) * 9.5f, (float) Math.sin(ang) * 7.5f, 1.25f, 1.25f, "#FF5252", "DYNAMIC");
            drone.customSpriteName = "drone_1";
            drone.spriteFrames = "drone_1,drone_2";
            drone.animFps = 9.5f;
            p.entities.add(drone);
        }

        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI * 2.0 / 6.0 + 0.5;
            Entity gem = Entity.create2D("Gem_" + i, "coin", "CUSTOM_SPRITE",
                    (float) Math.cos(ang) * 5.0f, (float) Math.sin(ang) * 4.0f, 0.9f, 0.9f, "#00E676", "STATIC");
            gem.customSpriteName = "gem";
            gem.isTrigger = true;
            p.entities.add(gem);
        }

        p.scriptFiles.put("objects/swarm_director.js",
                "// objects/swarm_director.js — Modular Swarm Director & XP HUD Controller\n" +
                "class SwarmDirector {\n" +
                "  constructor() {\n" +
                "    this.wave = 1;\n" +
                "    this.level = 1;\n" +
                "    this.xp = 0;\n" +
                "    this.fireTimer = 0;\n" +
                "    this.spawnTimer = 0;\n" +
                "    this.novaCd = 0;\n" +
                "    this.seq = 100;\n" +
                "    this.best = vortex.loadNum('survivorsBest', 0);\n" +
                "  }\n" +
                "  fireInterval() { return Math.max(0.16, 0.52 - (this.level - 1) * 0.06); }\n" +
                "  hud() {\n" +
                "    vortex.setBanner(`NEON SURVIVORS [ES6 JS] • LVL ${this.level} • WAVE ${this.wave} • BEST ${this.best}`);\n" +
                "    vortex.setProgressBar('xp_bar', `LVL ${this.level} XP`, this.xp % (this.level * 3), this.level * 3, 84, 13, '#FFFFFF');\n" +
                "  }\n" +
                "}\n" +
                "const dir = new SwarmDirector();\n");

        p.scriptCode =
                "// scripts/main.js — Player Combat, Auto-Aiming Weapon & Collision Hooks\n" +
                "function onStart() {\n" +
                "  vortex.setFixedRotation('Player', true);\n" +
                "  vortex.playMusic('CYBERPUNK', 138);\n" +
                "  vortex.tweenTagYoyo('coin', 0.28, 0.85, 'SINE');\n" +
                "  dir.hud();\n" +
                "}\n\n" +
                "function onUpdate(dt) {\n" +
                "  if (!vortex.exists('Player')) return;\n" +
                "  dir.novaCd = Math.max(0, dir.novaCd - dt);\n" +
                "  vortex.setVel('Player', vortex.joyX() * 8.6, vortex.joyY() * 8.6);\n" +
                "  vortex.chaseTagAI('enemy', 'Player', 3.9 + dir.wave * 0.25);\n\n" +
                "  // Auto-Aiming Plasma Weapon targeting closest Theta* Cyber-Drone!\n" +
                "  dir.fireTimer += dt;\n" +
                "  if (dir.fireTimer >= dir.fireInterval()) {\n" +
                "    dir.fireTimer = 0;\n" +
                "    const nearest = vortex.findNearestTag('Player', 'enemy');\n" +
                "    if (nearest) {\n" +
                "      const px = vortex.getX('Player'), py = vortex.getY('Player');\n" +
                "      const dx = vortex.getX(nearest) - px, dy = vortex.getY(nearest) - py;\n" +
                "      const len = Math.sqrt(dx * dx + dy * dy) || 1;\n" +
                "      const b = `Bolt_${++dir.seq}`;\n" +
                "      vortex.spawn2D(b, 'projectile', 'CIRCLE', px, py, 0.42, 0.42, '#00E5FF', 'KINEMATIC');\n" +
                "      vortex.setVel(b, (dx / len) * 18.5, (dy / len) * 18.5);\n" +
                "      vortex.playSound('laser');\n" +
                "    }\n" +
                "  }\n\n" +
                "  // Endless Wave Spawner\n" +
                "  dir.spawnTimer += dt;\n" +
                "  if (dir.spawnTimer >= 2.8 && vortex.countByTag('enemy') < 12) {\n" +
                "    dir.spawnTimer = 0;\n" +
                "    dir.wave = 1 + Math.floor(vortex.time() / 15);\n" +
                "    const ang = vortex.rand(0, 6.283);\n" +
                "    const nm = `Drone_${++dir.seq}`;\n" +
                "    vortex.spawn2D(nm, 'enemy', 'CUSTOM_SPRITE', Math.cos(ang) * 10.5, Math.sin(ang) * 8.0, 1.25, 1.25, '#FF5252', 'DYNAMIC');\n" +
                "    vortex.setSpriteAnim(nm, 'drone_1,drone_2', 9.5);\n" +
                "    dir.hud();\n" +
                "  }\n" +
                "}\n\n" +
                "function onButton(btn) {\n" +
                "  if (!vortex.exists('Player')) return;\n" +
                "  if (btn === 'A' && dir.novaCd <= 0) {\n" +
                "    dir.novaCd = 2.0;\n" +
                "    const px = vortex.getX('Player'), py = vortex.getY('Player');\n" +
                "    vortex.tweenScale('Player', 1.85, 1.85, 1.0, 0.24, 'ELASTIC', 1);\n" +
                "    vortex.particles(px, py, 0, 36, '#00E676');\n" +
                "    vortex.shake(0.35, 0.22);\n" +
                "    vortex.playSound('powerup');\n" +
                "    for (let k = 0; k < 8; k++) {\n" +
                "      const a = (k / 8) * Math.PI * 2;\n" +
                "      const b = `Nova_${++dir.seq}`;\n" +
                "      vortex.spawn2D(b, 'projectile', 'CIRCLE', px, py, 0.45, 0.45, '#00E676', 'KINEMATIC');\n" +
                "      vortex.setVel(b, Math.cos(a) * 16, Math.sin(a) * 16);\n" +
                "    }\n" +
                "  }\n" +
                "}\n\n" +
                "function onCollide(a, b, tagA, tagB) {\n" +
                "  if ((tagA === 'projectile' && tagB === 'enemy') || (tagB === 'projectile' && tagA === 'enemy')) {\n" +
                "    const en = tagA === 'enemy' ? a : b;\n" +
                "    const ex = vortex.getX(en), ey = vortex.getY(en);\n" +
                "    vortex.particles(ex, ey, 0, 22, '#FF5252');\n" +
                "    vortex.destroy(a); vortex.destroy(b);\n" +
                "    const gem = `Gem_${++dir.seq}`;\n" +
                "    vortex.spawn2D(gem, 'coin', 'CIRCLE', ex, ey, 0.62, 0.62, '#00E676', 'STATIC');\n" +
                "    const sc = vortex.addScore(100);\n" +
                "    if (sc > dir.best) { dir.best = sc; vortex.saveData('survivorsBest', sc); }\n" +
                "    vortex.playSound('explosion');\n" +
                "    dir.hud();\n" +
                "    return;\n" +
                "  }\n" +
                "  const other = tagA === 'player' ? b : (tagB === 'player' ? a : null);\n" +
                "  const oTag  = tagA === 'player' ? tagB : tagA;\n" +
                "  if (!other) return;\n" +
                "  if (oTag === 'coin') {\n" +
                "    vortex.particles(vortex.getX(other), vortex.getY(other), 0, 18, '#00E676');\n" +
                "    vortex.destroy(other);\n" +
                "    vortex.addScore(50);\n" +
                "    dir.xp += 1;\n" +
                "    if (dir.xp >= dir.level * 3) { dir.level += 1; vortex.playSound('powerup'); }\n" +
                "    else vortex.playSound('coin');\n" +
                "    dir.hud();\n" +
                "  } else if (oTag === 'enemy') {\n" +
                "    vortex.addHealth(-15);\n" +
                "    vortex.shake(0.45, 0.22);\n" +
                "    vortex.playSound('hit');\n" +
                "    vortex.destroy(other);\n" +
                "    if (vortex.getHealth() <= 0) {\n" +
                "      vortex.setBanner(`SWARM OVERRUN! FINAL SCORE: ${vortex.getScore()} (BEST: ${dir.best})`);\n" +
                "      vortex.destroy('Player');\n" +
                "    }\n" +
                "  }\n" +
                "}\n";
        p.scriptFiles.put("scripts/main.js", p.scriptCode);
        p.activeScriptFile = "scripts/main.js";
        return p;
    }

    private static GameProject buildStarhullMechaJs3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.scriptLanguage = "JS";
        p.terrainSeed = 4096;
        p.terrainFreq = 0.055f;
        p.skyColorHex = "#0B0B0E";
        p.ambientColorHex = "#24345A";
        p.fogEnabled = true;
        p.fogNear = 22f;
        p.fogFar = 84f;
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.cameraDistance = 13.5f;
        p.cameraHeight = 6.8f;
        p.iconBgHex = "#0B132B";
        p.iconAccentHex = "#38BDF8";
        p.iconGlyph = "ROCKET";
        p.musicEnabled = true;
        p.musicBpm = 142;
        p.musicPattern = ChiptuneGenerator.PRESET_BOSS;

        Entity terrain = Entity.create3D("StarhullCanyon", "solid", "TERRAIN", 0f, 0f, 0f, 75f, 9.0f, 75f, "#1E3A5F", "STATIC");
        terrain.checkerTexture = true;
        terrain.secondaryColorHex = "#0F1E36";
        p.entities.add(terrain);

        Entity mecha = Entity.create3D("Player", "player", "CUBE", 0f, 5.5f, 8f, 1.35f, 1.5f, 1.35f, "#00E5FF", "DYNAMIC");
        p.entities.add(mecha);

        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI * 2.0 / 6.0;
            float rx = (float) Math.cos(ang) * 14f;
            float rz = (float) Math.sin(ang) * 14f;
            Entity walker = Entity.create3D("HullWalker_" + i, "enemy", "PYRAMID", rx, 7f, rz, 1.5f, 2.0f, 1.5f, "#FF1744", "DYNAMIC");
            walker.spinY = 110f;
            p.entities.add(walker);

            Entity cell = Entity.create3D("FusionCell_" + i, "coin", "SPHERE", rx * 0.55f, 6.2f, rz * 0.55f, 0.9f, 0.9f, 0.9f, "#FFD54F", "STATIC");
            cell.isTrigger = true;
            p.entities.add(cell);
        }

        p.scriptCode =
                "// Starhull Mecha Raiders 3D — ES6+ JavaScript (Mozilla Rhino + 3D Terrain + Auto-Lock Railgun)\n" +
                "class MechaSystem {\n" +
                "  constructor() {\n" +
                "    this.railCd = 0;\n" +
                "    this.best = vortex.loadNum('starhullBest', 0);\n" +
                "  }\n" +
                "}\n" +
                "const sys = new MechaSystem();\n\n" +
                "function onStart() {\n" +
                "  vortex.playMusic('BOSS', 142);\n" +
                "  vortex.tweenTagYoyo('coin', 0.7, 0.95, 'SINE');\n" +
                "  vortex.setBanner(`STARHULL MECHA 3D [ES6 JS] • A=JUMP JETS • B=LOCK RAILGUN (BEST ${sys.best})`);\n" +
                "}\n\n" +
                "function onUpdate(dt) {\n" +
                "  if (!vortex.exists('Player')) return;\n" +
                "  sys.railCd = Math.max(0, sys.railCd - dt);\n" +
                "  vortex.setVel('Player', vortex.joyX() * 10.2, vortex.getVY('Player'), -vortex.joyY() * 10.2);\n" +
                "  vortex.chaseTagAI('enemy', 'Player', 4.6);\n" +
                "}\n\n" +
                "function onButton(btn) {\n" +
                "  if (!vortex.exists('Player')) return;\n" +
                "  if (btn === 'A') {\n" +
                "    vortex.jump('Player', 13.5);\n" +
                "    vortex.particles(vortex.getX('Player'), vortex.getY('Player') - 0.6, vortex.getZ('Player'), 16, '#38BDF8');\n" +
                "  } else if (btn === 'B' && sys.railCd <= 0) {\n" +
                "    sys.railCd = 0.35;\n" +
                "    vortex.playSound('laser');\n" +
                "    const locked = vortex.findNearestTag('Player', 'enemy');\n" +
                "    if (locked && vortex.distance('Player', locked) < 28) {\n" +
                "      const tx = vortex.getX(locked), ty = vortex.getY(locked), tz = vortex.getZ(locked);\n" +
                "      vortex.particles(tx, ty, tz, 32, '#00E5FF');\n" +
                "      vortex.destroy(locked);\n" +
                "      vortex.shake(0.38, 0.20);\n" +
                "      vortex.playSound('explosion');\n" +
                "      const sc = vortex.addScore(250);\n" +
                "      if (sc > sys.best) { sys.best = sc; vortex.saveData('starhullBest', sc); }\n" +
                "      if (vortex.countByTag('enemy') === 0) {\n" +
                "        vortex.setBanner(`SECTOR CLEARED! ALL HULL WALKERS DESTROYED! SCORE: ${sc}`);\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n\n" +
                "function onCollide(a, b, tagA, tagB) {\n" +
                "  const other = tagA === 'player' ? b : (tagB === 'player' ? a : null);\n" +
                "  const oTag  = tagA === 'player' ? tagB : tagA;\n" +
                "  if (!other) return;\n" +
                "  if (oTag === 'coin') {\n" +
                "    vortex.particles(vortex.getX(other), vortex.getY(other), vortex.getZ(other), 22, '#FFD54F');\n" +
                "    vortex.destroy(other);\n" +
                "    vortex.addScore(100);\n" +
                "    vortex.playSound('coin');\n" +
                "  } else if (oTag === 'enemy') {\n" +
                "    vortex.addHealth(-20);\n" +
                "    vortex.shake(0.48, 0.24);\n" +
                "    vortex.playSound('hit');\n" +
                "  }\n" +
                "}\n";
        return p;
    }
}
