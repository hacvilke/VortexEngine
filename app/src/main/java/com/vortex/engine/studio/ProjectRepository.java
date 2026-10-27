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
            GameProject box2dLua = ProjectTemplates.create("ANGRY_BOX2D_LUA", "Angry Crates Siege", "com.vortex.game.angrycrates");
            GameProject terrain3D = ProjectTemplates.create("FRONTIER_TERRAIN_3D_LUA", "Procedural Frontier 3D", "com.vortex.game.frontier3d");
            GameProject neon3D = ProjectTemplates.create("NEON_3D", "Neon Horizon 3D", "com.vortex.game.neonhorizon");
            GameProject platformer2D = ProjectTemplates.create("PLATFORMER_2D", "Starbound Cavern 2D", "com.vortex.game.starbound");
            saveProject(ctx, box2dLua);
            saveProject(ctx, terrain3D);
            saveProject(ctx, neon3D);
            saveProject(ctx, platformer2D);
            list.add(box2dLua);
            list.add(terrain3D);
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
