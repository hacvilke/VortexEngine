package com.vortex.engine.studio;

import android.content.Context;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Local GameProject repository + Built-in 2D & 3D Starter Game Templates.
 */
public class ProjectRepository {

    private static File projectsDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "vortex_projects");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<GameProject> listProjects(Context ctx) {
        File dir = projectsDir(ctx);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        List<GameProject> list = new ArrayList<>();
        if (files != null) {
            for (File f : files) {
                try {
                    String json = readFile(f);
                    list.add(GameProject.fromJsonString(json));
                } catch (Exception ignored) {
                }
            }
        }
        if (list.isEmpty()) {
            // Seed starter 3D & 2D projects on first run so the user can play & build immediately!
            GameProject neon3D = createTemplate("NEON_3D", "Neon Horizon 3D", "com.vortex.game.neonhorizon");
            GameProject platformer2D = createTemplate("PLATFORMER_2D", "Starbound Cavern 2D", "com.vortex.game.starbound");
            saveProject(ctx, neon3D);
            saveProject(ctx, platformer2D);
            list.add(neon3D);
            list.add(platformer2D);
        }
        Collections.sort(list, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return list;
    }

    public static void saveProject(Context ctx, GameProject project) {
        if (project == null) return;
        project.updatedAt = System.currentTimeMillis();
        File f = new File(projectsDir(ctx), project.id + ".json");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            fos.write(project.toJsonString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    public static GameProject loadProject(Context ctx, String id) {
        if (id == null) return null;
        File f = new File(projectsDir(ctx), id + ".json");
        if (!f.exists()) return null;
        try {
            return GameProject.fromJsonString(readFile(f));
        } catch (Exception e) {
            return null;
        }
    }

    public static void deleteProject(Context ctx, String id) {
        if (id == null) return;
        File f = new File(projectsDir(ctx), id + ".json");
        if (f.exists()) f.delete();
    }

    private static String readFile(File f) throws Exception {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    // ===================== BUILT-IN 2D & 3D GAME TEMPLATES =====================

    public static GameProject createTemplate(String templateKey, String title, String pkgName) {
        switch (templateKey) {
            case "SKY_ISLANDS_3D": return buildSkyIslands3D(title, pkgName);
            case "PLATFORMER_2D":  return buildPlatformer2D(title, pkgName);
            case "SHOOTER_2D":     return buildSpaceShooter2D(title, pkgName);
            case "BLANK_2D":       return buildBlank(title, pkgName, false);
            case "BLANK_3D":       return buildBlank(title, pkgName, true);
            case "NEON_3D":
            default:               return buildNeonHorizon3D(title, pkgName);
        }
    }

    private static GameProject buildNeonHorizon3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.iconBgHex = "#090D18";
        p.iconAccentHex = "#00E5FF";
        p.iconGlyph = "ROCKET";
        p.skyColorHex = "#080C1A";
        p.ambientColorHex = "#1E3050";
        p.fogEnabled = true;
        p.fogNear = 18f;
        p.fogFar = 68f;
        p.cameraMode = "FOLLOW";
        p.cameraTargetName = "Player";
        p.cameraDistance = 9.5f;
        p.cameraHeight = 4.6f;

        // Custom Wavefront .obj futuristic star-craft mesh!
        p.customObjMeshes.put("starfighter",
                "# Vortex Custom 3D Starfighter (.obj)\n" +
                "v  0.0  0.15 -0.85\n" +
                "v -0.65 -0.10  0.65\n" +
                "v  0.65 -0.10  0.65\n" +
                "v  0.0  0.42  0.35\n" +
                "v  0.0 -0.22  0.45\n" +
                "f 1 2 4\n" +
                "f 1 4 3\n" +
                "f 4 2 3\n" +
                "f 1 5 2\n" +
                "f 1 3 5\n" +
                "f 5 3 2\n"
        );

        // Highway floor
        Entity floor = Entity.create3D("TrackFloor", "solid", "CUBE", 0f, -0.5f, -35f, 18f, 1f, 120f, "#101B2D", "STATIC");
        floor.checkerTexture = true;
        floor.secondaryColorHex = "#09101C";
        p.entities.add(floor);

        // Player 3D Custom OBJ Starfighter
        Entity player = Entity.create3D("Player", "player", "CUSTOM_OBJ", 0f, 0.8f, 4f, 1.4f, 1.1f, 1.6f, "#00E5FF", "DYNAMIC");
        player.customObjName = "starfighter";
        player.emissive = true;
        p.entities.add(player);

        // Neon Obstacles & Collectibles along the 3D corridor
        float[][] hazards = {
                {-3.5f, 1.0f, -8f}, {3.2f, 1.0f, -15f}, {0f, 1.2f, -23f},
                {-4.2f, 1.2f, -32f}, {3.8f, 1.2f, -40f}, {0f, 1.4f, -50f},
                {-2.8f, 1.2f, -62f}, {2.8f, 1.2f, -72f}
        };
        for (int i = 0; i < hazards.length; i++) {
            Entity ob = Entity.create3D("Barrier_" + (i + 1), "hazard", "PYRAMID",
                    hazards[i][0], hazards[i][1], hazards[i][2], 2.0f, 2.2f, 2.0f, "#FF4081", "STATIC");
            ob.emissive = true;
            p.entities.add(ob);
        }

        float[][] crystals = {
                {0f, 1.1f, -5f}, {-3.2f, 1.2f, -12f}, {3.0f, 1.2f, -19f},
                {0f, 2.4f, -23f}, {2.5f, 1.2f, -35f}, {-2.5f, 1.2f, -45f},
                {0f, 1.3f, -56f}, {0f, 1.5f, -68f}
        };
        for (int i = 0; i < crystals.length; i++) {
            Entity c = Entity.create3D("Crystal_" + (i + 1), "coin", "SPHERE",
                    crystals[i][0], crystals[i][1], crystals[i][2], 1.0f, 1.0f, 1.0f, "#FFD740", "STATIC");
            c.isTrigger = true;
            c.emissive = true;
            c.spinY = 140f;
            p.entities.add(c);
        }

        Entity portal = Entity.create3D("WarpGate", "goal", "CYLINDER", 0f, 2.2f, -82f, 4.5f, 4.4f, 1.2f, "#00E676", "STATIC");
        portal.isTrigger = true;
        portal.emissive = true;
        portal.spinY = 65f;
        p.entities.add(portal);

        p.scriptCode =
                "// Neon Horizon 3D — VortexScript (.vxs)\n" +
                "// Move: Left Joystick | Button A: Hover Jump | Button B: Hyper Boost\n" +
                "let speed = 13.0;\n" +
                "let finished = false;\n\n" +
                "fn onStart() {\n" +
                "    setBanner('COLLECT CRYSTALS & REACH THE WARP GATE!');\n" +
                "    log('Neon Horizon 3D initialized!');\n" +
                "}\n\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player') || finished) { return; }\n" +
                "    let boost = 1.0;\n" +
                "    if (btnB()) {\n" +
                "        boost = 1.85;\n" +
                "        particles(getX('Player'), getY('Player'), getZ('Player') + 1.1, 3, '#00E5FF');\n" +
                "    }\n" +
                "    let vx = joyX() * 11.5;\n" +
                "    let vy = getVY('Player');\n" +
                "    setVel('Player', vx, vy, -speed * boost);\n" +
                "    setRot('Player', 0, 0, -joyX() * 24);\n\n" +
                "    if (getY('Player') < -6) {\n" +
                "        setPos('Player', 0, 1.5, 4);\n" +
                "        setVel('Player', 0, 0, 0);\n" +
                "        addHealth(-20);\n" +
                "        playSound('hit');\n" +
                "    }\n" +
                "}\n\n" +
                "fn onButton(btn) {\n" +
                "    if (btn == 'A') {\n" +
                "        jump('Player', 11.5);\n" +
                "    }\n" +
                "}\n\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if (a == 'Player' && tagB == 'coin') {\n" +
                "        particles(getX(b), getY(b), getZ(b), 22, '#FFD740');\n" +
                "        destroy(b);\n" +
                "        addScore(100);\n" +
                "        playSound('coin');\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'hazard') {\n" +
                "        particles(getX('Player'), getY('Player'), getZ('Player'), 30, '#FF4081');\n" +
                "        shake(0.45, 0.3);\n" +
                "        addHealth(-25);\n" +
                "        playSound('explosion');\n" +
                "        destroy(b);\n" +
                "        if (getHealth() <= 0) {\n" +
                "            setBanner('SHIP WRECKED! TAP RESTART');\n" +
                "            finished = true;\n" +
                "        }\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'goal') {\n" +
                "        finished = true;\n" +
                "        setVel('Player', 0, 0, 0);\n" +
                "        addScore(500);\n" +
                "        playSound('powerup');\n" +
                "        particles(getX(b), getY(b), getZ(b), 55, '#00E676');\n" +
                "        setBanner('SECTOR CLEARED! SCORE: ' + getScore());\n" +
                "    }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildSkyIslands3D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "3D";
        p.iconBgHex = "#102038";
        p.iconAccentHex = "#00E676";
        p.iconGlyph = "CUBE";
        p.skyColorHex = "#12233E";
        p.ambientColorHex = "#344E72";
        p.cameraDistance = 11f;
        p.cameraHeight = 5.8f;

        float[][] islands = {
                {0f, -0.5f, 0f, 7f, 1f, 7f},
                {0f, 0.5f, -8f, 4.5f, 1f, 4.5f},
                {5f, 1.6f, -13f, 4.5f, 1f, 4.5f},
                {0f, 2.8f, -19f, 4.5f, 1f, 4.5f},
                {-5f, 4.0f, -25f, 5f, 1f, 5f},
                {0f, 5.2f, -32f, 6.5f, 1f, 6.5f}
        };
        for (int i = 0; i < islands.length; i++) {
            float[] d = islands[i];
            Entity isl = Entity.create3D("Island_" + (i + 1), "solid", "CUBE",
                    d[0], d[1], d[2], d[3], d[4], d[5], "#26A69A", "STATIC");
            isl.checkerTexture = true;
            isl.secondaryColorHex = "#1B7A70";
            p.entities.add(isl);

            if (i > 0 && i < islands.length - 1) {
                Entity relic = Entity.create3D("Relic_" + i, "coin", "PYRAMID",
                        d[0], d[1] + 1.4f, d[2], 0.9f, 1.1f, 0.9f, "#FFD54F", "STATIC");
                relic.isTrigger = true;
                relic.emissive = true;
                relic.spinY = 120f;
                p.entities.add(relic);
            }
        }

        Entity player = Entity.create3D("Player", "player", "SPHERE", 0f, 1.2f, 0f, 1.1f, 1.1f, 1.1f, "#00E5FF", "DYNAMIC");
        player.bounce = 0.15f;
        p.entities.add(player);

        Entity beacon = Entity.create3D("SkyTotem", "goal", "CYLINDER", 0f, 7.0f, -32f, 1.6f, 2.8f, 1.6f, "#7C4DFF", "STATIC");
        beacon.isTrigger = true;
        beacon.emissive = true;
        p.entities.add(beacon);

        p.scriptCode =
                "// Sky Islands 3D Parkour — VortexScript\n" +
                "fn onStart() {\n" +
                "    setBanner('LEAP ACROSS THE 3D SKY ISLANDS!');\n" +
                "}\n\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    setVel('Player', joyX() * 8.5, getVY('Player'), -joyY() * 8.5);\n" +
                "    if (getY('Player') < -8) {\n" +
                "        setPos('Player', 0, 2.0, 0);\n" +
                "        setVel('Player', 0, 0, 0);\n" +
                "        playSound('hit');\n" +
                "    }\n" +
                "}\n\n" +
                "fn onButton(btn) {\n" +
                "    if (btn == 'A') { jump('Player', 12.2); }\n" +
                "}\n\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if (a == 'Player' && tagB == 'coin') {\n" +
                "        particles(getX(b), getY(b), getZ(b), 20, '#FFD54F');\n" +
                "        destroy(b);\n" +
                "        addScore(150);\n" +
                "        playSound('coin');\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'goal') {\n" +
                "        playSound('powerup');\n" +
                "        particles(getX(b), getY(b), getZ(b), 50, '#7C4DFF');\n" +
                "        setBanner('SUMMIT REACHED! SCORE ' + getScore());\n" +
                "    }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildPlatformer2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "2D";
        p.iconBgHex = "#13172B";
        p.iconAccentHex = "#FFB300";
        p.iconGlyph = "STAR";
        p.skyColorHex = "#0E1324";
        p.zoom2D = 16f;
        p.gravityY = -26f;

        p.entities.add(Entity.create2D("Ground", "solid", "RECT", 8f, -3.5f, 36f, 2f, "#23354D", "STATIC"));
        p.entities.add(Entity.create2D("Plat_1", "solid", "RECT", 5f, -0.2f, 4.5f, 0.7f, "#2E4F6E", "STATIC"));
        p.entities.add(Entity.create2D("Plat_2", "solid", "RECT", 11.5f, 2.2f, 4.5f, 0.7f, "#2E4F6E", "STATIC"));
        p.entities.add(Entity.create2D("Plat_3", "solid", "RECT", 18.0f, 4.2f, 5.0f, 0.7f, "#2E4F6E", "STATIC"));

        Entity hero = Entity.create2D("Player", "player", "SPRITE_HERO", -2f, 0f, 1.15f, 1.35f, "#00E5FF", "DYNAMIC");
        p.entities.add(hero);

        Entity slime1 = Entity.create2D("Slime_1", "enemy", "SPRITE_SLIME", 6f, -1.8f, 1.2f, 1.0f, "#FF5252", "KINEMATIC");
        slime1.vx = 2.6f;
        p.entities.add(slime1);

        float[][] coins = {{5f, 1.1f}, {11.5f, 3.5f}, {14.5f, -1.5f}, {18.0f, 5.6f}};
        for (int i = 0; i < coins.length; i++) {
            Entity c = Entity.create2D("Coin_" + (i + 1), "coin", "SPRITE_COIN", coins[i][0], coins[i][1], 0.85f, 0.85f, "#FFD740", "STATIC");
            c.isTrigger = true;
            p.entities.add(c);
        }

        Entity flag = Entity.create2D("ExitCrystal", "goal", "CIRCLE", 20.0f, 5.8f, 1.3f, 1.3f, "#00E676", "STATIC");
        flag.isTrigger = true;
        p.entities.add(flag);

        p.scriptCode =
                "// Starbound Cavern 2D — VortexScript\n" +
                "// Move: Joystick | A: Jump | Stomp slimes from above!\n" +
                "fn onStart() {\n" +
                "    setBanner('COLLECT COINS & STOMP SLIMES!');\n" +
                "}\n\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    setVel('Player', joyX() * 9.0, getVY('Player'));\n\n" +
                "    if (exists('Slime_1')) {\n" +
                "        if (getX('Slime_1') > 11.0) { setVel('Slime_1', -2.6, 0); }\n" +
                "        if (getX('Slime_1') < 2.0)  { setVel('Slime_1', 2.6, 0); }\n" +
                "    }\n\n" +
                "    if (getY('Player') < -9.0) {\n" +
                "        setPos('Player', -2, 1);\n" +
                "        setVel('Player', 0, 0);\n" +
                "        addHealth(-20);\n" +
                "        playSound('hit');\n" +
                "    }\n" +
                "}\n\n" +
                "fn onButton(btn) {\n" +
                "    if (btn == 'A') { jump('Player', 13.5); }\n" +
                "}\n\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if (a == 'Player' && tagB == 'coin') {\n" +
                "        particles(getX(b), getY(b), 0, 18, '#FFD740');\n" +
                "        destroy(b);\n" +
                "        addScore(100);\n" +
                "        playSound('coin');\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'enemy') {\n" +
                "        if (getY('Player') > getY(b) + 0.45) {\n" +
                "            particles(getX(b), getY(b), 0, 26, '#FF5252');\n" +
                "            destroy(b);\n" +
                "            setVel('Player', getVX('Player'), 10.5);\n" +
                "            addScore(250);\n" +
                "            playSound('explosion');\n" +
                "        } else {\n" +
                "            shake(0.35, 0.25);\n" +
                "            addHealth(-25);\n" +
                "            setPos('Player', -2, 1);\n" +
                "            playSound('hit');\n" +
                "        }\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'goal') {\n" +
                "        playSound('powerup');\n" +
                "        particles(getX(b), getY(b), 0, 45, '#00E676');\n" +
                "        setBanner('STAGE CLEAR! SCORE: ' + getScore());\n" +
                "    }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildSpaceShooter2D(String title, String pkgName) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = "2D";
        p.iconBgHex = "#080B14";
        p.iconAccentHex = "#7C4DFF";
        p.iconGlyph = "ROCKET";
        p.skyColorHex = "#070913";
        p.cameraMode = "FIXED";
        p.zoom2D = 18f;
        p.gravityY = 0f;

        Entity ship = Entity.create2D("Player", "player", "SPRITE_SHIP", 0f, -5.5f, 1.3f, 1.4f, "#00E5FF", "KINEMATIC");
        p.entities.add(ship);

        for (int i = 0; i < 5; i++) {
            Entity ast = Entity.create2D("Meteor_" + i, "enemy", "CIRCLE", -6f + i * 3f, 5f + (i % 2) * 2.5f, 1.35f, 1.35f, "#FF7043", "KINEMATIC");
            ast.vy = -2.8f - (i * 0.25f);
            ast.spinZ = 95f;
            p.entities.add(ast);
        }

        p.scriptCode =
                "// Nova Blaster 2D — Arcade Space Shooter\n" +
                "// Move: Joystick | Button A or B: Fire Plasma Laser!\n" +
                "let shotId = 0;\n" +
                "let spawnTimer = 0.0;\n\n" +
                "fn onStart() {\n" +
                "    setBanner('BLAST METEORS WITH A / B!');\n" +
                "}\n\n" +
                "fn onUpdate(dt) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    let nx = clamp(getX('Player') + joyX() * 12.0 * dt, -11.0, 11.0);\n" +
                "    let ny = clamp(getY('Player') + joyY() * 10.0 * dt, -7.5, 6.5);\n" +
                "    setPos('Player', nx, ny);\n\n" +
                "    spawnTimer += dt;\n" +
                "    if (spawnTimer > 1.1) {\n" +
                "        spawnTimer = 0.0;\n" +
                "        shotId += 1;\n" +
                "        let mName = 'Meteor_W' + shotId;\n" +
                "        spawn2D(mName, 'enemy', 'CIRCLE', rand(-10, 10), 9.5, 1.3, 1.3, '#FF5252', 'KINEMATIC');\n" +
                "        setVel(mName, rand(-1.2, 1.2), rand(-4.8, -2.5));\n" +
                "    }\n" +
                "}\n\n" +
                "fn onButton(btn) {\n" +
                "    if (!exists('Player')) { return; }\n" +
                "    shotId += 1;\n" +
                "    let bName = 'Laser_' + shotId;\n" +
                "    spawn2D(bName, 'projectile', 'RECT', getX('Player'), getY('Player') + 1.0, 0.25, 0.85, '#00E676', 'KINEMATIC');\n" +
                "    setVel(bName, 0, 18.0);\n" +
                "    playSound('laser');\n" +
                "}\n\n" +
                "fn onCollide(a, b, tagA, tagB) {\n" +
                "    if ((tagA == 'projectile' && tagB == 'enemy') || (tagA == 'enemy' && tagB == 'projectile')) {\n" +
                "        particles(getX(a), getY(a), 0, 22, '#FF7043');\n" +
                "        destroy(a);\n" +
                "        destroy(b);\n" +
                "        addScore(100);\n" +
                "        playSound('explosion');\n" +
                "    }\n" +
                "    if (a == 'Player' && tagB == 'enemy') {\n" +
                "        particles(getX(b), getY(b), 0, 28, '#FF5252');\n" +
                "        destroy(b);\n" +
                "        shake(0.4, 0.25);\n" +
                "        addHealth(-25);\n" +
                "        playSound('hit');\n" +
                "    }\n" +
                "}\n";
        return p;
    }

    private static GameProject buildBlank(String title, String pkgName, boolean is3D) {
        GameProject p = new GameProject();
        p.title = title;
        p.packageName = pkgName;
        p.dimensionMode = is3D ? "3D" : "2D";
        if (is3D) {
            Entity floor = Entity.create3D("Floor", "solid", "CUBE", 0f, -0.5f, 0f, 16f, 1f, 16f, "#1B2838", "STATIC");
            floor.checkerTexture = true;
            p.entities.add(floor);
            p.entities.add(Entity.create3D("Player", "player", "CUBE", 0f, 1f, 0f, 1f, 1f, 1f, "#00E5FF", "DYNAMIC"));
            p.scriptCode =
                    "// Custom 3D VortexScript\n" +
                    "fn onStart() {\n" +
                    "    setBanner('MY CUSTOM 3D GAME');\n" +
                    "}\n\n" +
                    "fn onUpdate(dt) {\n" +
                    "    setVel('Player', joyX() * 8.0, getVY('Player'), -joyY() * 8.0);\n" +
                    "}\n\n" +
                    "fn onButton(btn) {\n" +
                    "    if (btn == 'A') { jump('Player', 11.0); }\n" +
                    "}\n";
        } else {
            p.entities.add(Entity.create2D("Floor", "solid", "RECT", 0f, -3.5f, 20f, 1.5f, "#1B2838", "STATIC"));
            p.entities.add(Entity.create2D("Player", "player", "SPRITE_HERO", 0f, 0f, 1.2f, 1.3f, "#00E5FF", "DYNAMIC"));
            p.scriptCode =
                    "// Custom 2D VortexScript\n" +
                    "fn onStart() {\n" +
                    "    setBanner('MY CUSTOM 2D GAME');\n" +
                    "}\n\n" +
                    "fn onUpdate(dt) {\n" +
                    "    setVel('Player', joyX() * 8.5, getVY('Player'));\n" +
                    "}\n\n" +
                    "fn onButton(btn) {\n" +
                    "    if (btn == 'A') { jump('Player', 12.5); }\n" +
                    "}\n";
        }
        return p;
    }
}
