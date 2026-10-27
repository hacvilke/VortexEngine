package com.vortex.engine.core.model;

/**
 * Pure-Java Cross-Platform Starter Game Templates (shared by Android Studio APK, Windows .exe & Linux Studio).
 * Showcases Lua 5.2 (LuaJ), JBox2D Rigid-Body Physics, FastNoiseLite 3D Procedural Terrain, Pixel Art & VortexScript!
 */
public class ProjectTemplates {

    public static final String[] TEMPLATE_KEYS = {
            "ANGRY_BOX2D_LUA",
            "FRONTIER_TERRAIN_3D_LUA",
            "NEON_3D",
            "SKY_ISLANDS_3D",
            "PLATFORMER_2D",
            "SHOOTER_2D",
            "BLANK_3D_LUA",
            "BLANK_2D_LUA"
    };

    public static final String[] TEMPLATE_NAMES = {
            "2D Physics: Angry Crates Siege (Lua 5.2 + JBox2D Rigid-Body)",
            "3D World: Procedural Frontier (Lua 5.2 + FastNoiseLite Terrain)",
            "3D Sci-Fi: Neon Horizon Runner (Custom .obj Starfighter)",
            "3D Parkour: Sky Islands Adventure (3D Physics & Relics)",
            "2D Action: Starbound Cavern (Platformer + Slime AI)",
            "2D Arcade: Nova Blaster (Space Shooter + Plasma Lasers)",
            "3D Sandbox: Blank 3D Project (Lua 5.2)",
            "2D Sandbox: Blank 2D Project (Lua 5.2 + JBox2D)"
    };

    public static GameProject create(String templateKey, String title, String pkgName) {
        switch (templateKey) {
            case "ANGRY_BOX2D_LUA":         return buildAngryCratesBox2D(title, pkgName);
            case "FRONTIER_TERRAIN_3D_LUA": return buildFrontierTerrain3D(title, pkgName);
            case "SKY_ISLANDS_3D":          return buildSkyIslands3D(title, pkgName);
            case "PLATFORMER_2D":           return buildPlatformer2D(title, pkgName);
            case "SHOOTER_2D":              return buildSpaceShooter2D(title, pkgName);
            case "BLANK_2D_LUA":            return buildBlankLua(title, pkgName, false);
            case "BLANK_3D_LUA":            return buildBlankLua(title, pkgName, true);
            case "NEON_3D":
            default:                        return buildNeonHorizon3D(title, pkgName);
        }
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
        p.skyColorHex = "#0D1322";
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
        p.skyColorHex = "#10253A";
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
        p.skyColorHex = "#080C1A";
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
        p.skyColorHex = "#12233E"; p.ambientColorHex = "#344E72"; p.cameraDistance = 11f; p.cameraHeight = 5.8f;
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
        p.useBox2D = true; p.skyColorHex = "#0E1324"; p.zoom2D = 16f; p.gravityY = -26f;

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
        p.skyColorHex = "#070913"; p.cameraMode = "FIXED"; p.zoom2D = 18f; p.gravityY = 0f;
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
}
