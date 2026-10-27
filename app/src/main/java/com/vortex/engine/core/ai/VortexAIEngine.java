package com.vortex.engine.core.ai;

import com.vortex.engine.core.math.Ray3D;
import com.vortex.engine.core.math.Vec3;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.physics.PhysicsWorld;
import java.util.List;
import org.xguzm.pathfinding.grid.GridCell;
import org.xguzm.pathfinding.grid.NavigationGrid;
import org.xguzm.pathfinding.grid.finders.AStarGridFinder;
import org.xguzm.pathfinding.grid.finders.GridFinderOptions;
import org.xguzm.pathfinding.grid.finders.ThetaStarGridFinder;

/**
 * Pure-Java Game AI & Raycasting Engine powered by xaguzman/pathfinding (Apache-2.0).
 * Provides Any-Angle Theta* & A* Obstacle-Avoiding Pathfinding + 2D/3D Line-of-Sight Raycasting!
 */
public class VortexAIEngine {

    private static final int GRID_SIZE = 48;     // Covers [-24 .. +24] world units at 1.0u resolution
    private static final float CELL_SIZE = 1.0f;
    private static final float HALF_SPAN = (GRID_SIZE * CELL_SIZE) * 0.5f;

    private final GridCell[][] cells = new GridCell[GRID_SIZE][GRID_SIZE];
    private final NavigationGrid<GridCell> navGrid;
    private final ThetaStarGridFinder<GridCell> thetaFinder;
    private final AStarGridFinder<GridCell> astarFinder;

    private long lastGridBuildTick = -999;
    private long tickCounter = 0;

    public VortexAIEngine() {
        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                cells[x][y] = new GridCell(x, y, true);
            }
        }
        navGrid = new NavigationGrid<>(cells, true);
        GridFinderOptions opts = new GridFinderOptions();
        opts.allowDiagonal = true;
        opts.dontCrossCorners = true;
        thetaFinder = new ThetaStarGridFinder<>(GridCell.class, opts);
        astarFinder = new AStarGridFinder<>(GridCell.class, opts);
    }

    public void onFrameTick() {
        tickCounter++;
    }

    public void reset() {
        lastGridBuildTick = -999;
        tickCounter = 0;
    }

    /**
     * Rebuilds walkability grid from STATIC non-trigger wall/obstacle entities at most every 12 frames (~0.2s).
     */
    private void ensureNavGrid(GameProject project) {
        if (project == null || (tickCounter - lastGridBuildTick) < 12) return;
        lastGridBuildTick = tickCounter;

        for (int x = 0; x < GRID_SIZE; x++) {
            for (int y = 0; y < GRID_SIZE; y++) {
                cells[x][y].setWalkable(true);
            }
        }

        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            if (!e.alive || e.isTrigger || !"STATIC".equalsIgnoreCase(e.bodyType)) continue;
            if ("TERRAIN".equalsIgnoreCase(e.primitive) || "PLANE".equalsIgnoreCase(e.primitive)) continue;

            // In 3D, ignore flat ground floors (y + scaleY*0.5 <= 0.25) so floors stay walkable!
            if (is3D && (e.y + Math.abs(e.scaleY) * 0.5f) <= 0.35f) continue;

            float cx = e.x;
            float cy = is3D ? e.z : e.y;
            float hx = Math.abs(e.scaleX) * 0.5f + 0.35f;
            float hy = Math.abs(is3D ? e.scaleZ : e.scaleY) * 0.5f + 0.35f;

            // In 2D platformer vs top-down: skip ultra-wide floor slabs at the bottom (width > 24)
            if (!is3D && Math.abs(e.scaleX) > 26f && e.y < -4.5f) continue;

            int gx0 = worldToGrid(cx - hx);
            int gx1 = worldToGrid(cx + hx);
            int gy0 = worldToGrid(cy - hy);
            int gy1 = worldToGrid(cy + hy);
            for (int gx = gx0; gx <= gx1; gx++) {
                for (int gy = gy0; gy <= gy1; gy++) {
                    cells[gx][gy].setWalkable(false);
                }
            }
        }
    }

    /**
     * Smartly steers `seeker` towards `target` around obstacles using Theta* / A* pathfinding!
     */
    public void chaseEntityAI(GameProject project, PhysicsWorld physics, Entity seeker, Entity target, float speed) {
        if (project == null || seeker == null || target == null || !seeker.alive || !target.alive) return;
        ensureNavGrid(project);

        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        float sx = seeker.x;
        float sy = is3D ? seeker.z : seeker.y;
        float tx = target.x;
        float ty = is3D ? target.z : target.y;

        int gsx = worldToGrid(sx), gsy = worldToGrid(sy);
        int gtx = worldToGrid(tx), gty = worldToGrid(ty);

        // Temporarily ensure start & goal cells are walkable so pathfinder can enter/exit
        boolean oldStart = cells[gsx][gsy].isWalkable();
        boolean oldGoal = cells[gtx][gty].isWalkable();
        cells[gsx][gsy].setWalkable(true);
        cells[gtx][gty].setWalkable(true);

        float aimX = tx, aimY = ty;
        try {
            List<GridCell> path = thetaFinder.findPath(gsx, gsy, gtx, gty, navGrid);
            if (path == null || path.isEmpty()) {
                path = astarFinder.findPath(gsx, gsy, gtx, gty, navGrid);
            }
            if (path != null && !path.isEmpty()) {
                GridCell wp = path.get(0);
                if (path.size() > 1 && Math.hypot(gridToWorld(wp.x) - sx, gridToWorld(wp.y) - sy) < 0.65f) {
                    wp = path.get(1);
                }
                aimX = gridToWorld(wp.x);
                aimY = gridToWorld(wp.y);
            }
        } catch (Throwable ignored) {
        } finally {
            cells[gsx][gsy].setWalkable(oldStart);
            cells[gtx][gty].setWalkable(oldGoal);
        }

        float dx = aimX - sx;
        float dy = aimY - sy;
        float dist = (float) Math.hypot(dx, dy);
        if (dist < 0.08f) return;

        float vx = (dx / dist) * speed;
        float vy = (dy / dist) * speed;

        if (is3D) {
            seeker.vx = vx;
            seeker.vz = vy;
        } else {
            seeker.vx = vx;
            seeker.vy = vy;
        }
    }

    /**
     * Casts a ray from (ox,oy,oz) along direction (dx,dy,dz) up to maxDist, returning the closest hit Entity name (or "").
     */
    public String raycast(GameProject project, Entity ignoreEntity,
                          float ox, float oy, float oz,
                          float dx, float dy, float dz, float maxDist) {
        if (project == null) return "";
        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        if (!is3D) { oz = 0f; dz = 0f; }

        Ray3D ray = new Ray3D(new Vec3(ox, oy, oz), new Vec3(dx, dy, dz).normalize());
        String bestName = "";
        float bestT = maxDist > 0f ? maxDist : 100f;

        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            if (!e.alive || !e.visible || e == ignoreEntity || "TERRAIN".equalsIgnoreCase(e.primitive)) continue;
            float hx = Math.abs(e.scaleX) * 0.5f;
            float hy = Math.abs(e.scaleY) * 0.5f;
            float hz = is3D ? Math.abs(e.scaleZ) * 0.5f : 1.0f;

            float t = ray.intersectAABB(e.x - hx, e.y - hy, e.z - hz, e.x + hx, e.y + hy, e.z + hz);
            if (t >= 0f && t <= bestT) {
                bestT = t;
                bestName = e.name;
            }
        }
        return bestName;
    }

    private static int worldToGrid(float w) {
        int idx = Math.round((w + HALF_SPAN) / CELL_SIZE);
        return Math.max(0, Math.min(GRID_SIZE - 1, idx));
    }

    private static float gridToWorld(int g) {
        return (g * CELL_SIZE) - HALF_SPAN;
    }
}
