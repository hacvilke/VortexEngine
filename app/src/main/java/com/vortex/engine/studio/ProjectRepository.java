package com.vortex.engine.studio;

import android.content.Context;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.ProjectTemplates;
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
 * Local GameProject repository wrapping cross-platform ProjectTemplates.
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
                    list.add(GameProject.fromJsonString(readFile(f)));
                } catch (Exception ignored) {
                }
            }
        }
        if (list.isEmpty()) {
            GameProject cyberMaze = ProjectTemplates.create("CYBER_MAZE_AI_2D", "Cyber Hunters AI Maze", "com.vortex.game.cyberhunters");
            GameProject citadel3D = ProjectTemplates.create("CITADEL_TWEEN_3D", "Neon Citadel Siege 3D", "com.vortex.game.citadel3d");
            GameProject box2dLua = ProjectTemplates.create("ANGRY_BOX2D_LUA", "Angry Crates Siege", "com.vortex.game.angrycrates");
            GameProject terrain3D = ProjectTemplates.create("FRONTIER_TERRAIN_3D_LUA", "Procedural Frontier 3D", "com.vortex.game.frontier3d");
            saveProject(ctx, terrain3D);
            saveProject(ctx, box2dLua);
            saveProject(ctx, citadel3D);
            saveProject(ctx, cyberMaze);
            list.add(cyberMaze);
            list.add(citadel3D);
            list.add(box2dLua);
            list.add(terrain3D);
            try { new File(dir, "v3_seeded.flag").createNewFile(); } catch (Exception ignored) {}
        } else {
            File v3Flag = new File(dir, "v3_seeded.flag");
            if (!v3Flag.exists()) {
                try {
                    v3Flag.createNewFile();
                    GameProject citadel3D = ProjectTemplates.create("CITADEL_TWEEN_3D", "Neon Citadel Siege 3D", "com.vortex.game.citadel3d");
                    GameProject cyberMaze = ProjectTemplates.create("CYBER_MAZE_AI_2D", "Cyber Hunters AI Maze", "com.vortex.game.cyberhunters");
                    saveProject(ctx, citadel3D);
                    Thread.sleep(5);
                    saveProject(ctx, cyberMaze);
                    list.add(citadel3D);
                    list.add(cyberMaze);
                } catch (Exception ignored) {
                }
            }
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

    public static GameProject createTemplate(String templateKey, String title, String pkgName) {
        return ProjectTemplates.create(templateKey, title, pkgName);
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
}
