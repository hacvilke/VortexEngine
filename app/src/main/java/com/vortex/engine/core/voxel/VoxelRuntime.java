package com.vortex.engine.core.voxel;

import com.vortex.engine.core.model.GameProject;

import java.util.List;
import java.util.Map;

/**
 * Live voxel state for one project: the world, the player, and the cached surface mesh.
 * Desktop, Android and scripts all talk to this single object, so a block broken on the phone is
 * truly gone everywhere (and vice-versa).
 */
public final class VoxelRuntime {

    public final VoxelWorld world;
    public final VoxelPlayer player = new VoxelPlayer();

    private List<VoxelWorld.Quad> cachedQuads;
    private Map<Integer, float[]> cachedTrianglesByBlock;
    private float[] cachedTriangles;
    private int cachedDirty = -1;
    private int cachedRadius = -1;
    private int cachedCenterX = Integer.MIN_VALUE, cachedCenterZ = Integer.MIN_VALUE;

    private long lastEditNanos = 0L;

    public VoxelRuntime(VoxelWorld world) {
        this.world = world;
    }

    /** Loads the saved world (or generates a fresh one for the seed) and spawns the player. */
    public static VoxelRuntime forProject(GameProject project) {
        VoxelWorld world;
        String data = project.voxelWorldData;
        if (data != null && !data.isEmpty()) {
            world = VoxelWorld.fromRle(data, VoxelWorld.SIZE, VoxelWorld.HEIGHT);
            if (world.highestSolidY() == 0) world.generate(project.voxelSeed);   // corrupt/empty -> regenerate
        } else {
            world = VoxelWorld.createDefault();
            world.generate(project.voxelSeed);
            // Fresh world: choose an open, level spawn and carve a small meadow around it.
            int[] spot = world.prepareSpawn(Math.round(project.voxelSpawnX), Math.round(project.voxelSpawnZ));
            project.voxelSpawnX = spot[0] + 0.5f;
            project.voxelSpawnZ = spot[1] + 0.5f;
            project.voxelWorldData = world.serializeRle();
        }
        VoxelRuntime rt = new VoxelRuntime(world);
        rt.player.spawn(world, project.voxelSpawnX, project.voxelSpawnZ);
        rt.player.yawDeg = project.voxelSpawnYaw;
        return rt;
    }

    public VoxelRuntime ensureInProject(GameProject project) {
        if (project.voxelRuntime == null) project.voxelRuntime = this;
        return this;
    }

    // ------------------------------------------------------------------ mesh access

    /** Triangles for the whole visible surface (used by the software viewport). */
    public float[] triangles() {
        ensureMesh(0, 0, 0);
        return cachedTriangles;
    }

    public Map<Integer, float[]> trianglesByBlock() {
        ensureMesh(0, 0, 0);
        return cachedTrianglesByBlock;
    }

    /** Radius-limited mesh (used by the Android renderer to keep the frame rate up). */
    public Map<Integer, float[]> trianglesByBlock(int centerX, int centerZ, int radius) {
        ensureMesh(centerX, centerZ, radius);
        return cachedTrianglesByBlock;
    }

    private void ensureMesh(int centerX, int centerZ, int radius) {
        boolean stale = cachedTrianglesByBlock == null
                || cachedDirty != world.dirty()
                || cachedRadius != radius
                || (radius > 0 && (Math.abs(centerX - cachedCenterX) > 3 || Math.abs(centerZ - cachedCenterZ) > 3));
        if (!stale) return;
        cachedDirty = world.dirty();
        cachedRadius = radius;
        cachedCenterX = centerX;
        cachedCenterZ = centerZ;
        cachedQuads = world.buildQuads(centerX, centerZ, radius);
        cachedTrianglesByBlock = VoxelWorld.trianglesByBlock(cachedQuads);
        cachedTriangles = VoxelWorld.quadsToTriangles(cachedQuads);
    }

    /** The visible surface as merged quads (what the software viewport paints). */
    public List<VoxelWorld.Quad> quads() {
        ensureMesh(0, 0, 0);
        return cachedQuads;
    }

    public int quadCount() {
        return cachedQuads == null ? 0 : cachedQuads.size();
    }

    // ------------------------------------------------------------------ editing

    public int breakAt(int x, int y, int z) {
        int id = world.get(x, y, z);
        lastEditNanos = System.nanoTime();
        return world.breakBlock(x, y, z) ? id : BlockType.AIR;
    }

    public boolean placeAt(int x, int y, int z, int blockId) {
        lastEditNanos = System.nanoTime();
        return world.placeBlock(x, y, z, blockId);
    }

    public int breakTargeted() {
        lastEditNanos = System.nanoTime();
        return player.breakTarget(world);
    }

    public boolean placeTargeted(int blockId) {
        lastEditNanos = System.nanoTime();
        return player.placeTarget(world, blockId);
    }

    public long lastEditNanos() { return lastEditNanos; }

    /** Called by the editor after a build/regenerate so the world is written back into the project. */
    public void persist(GameProject project) {
        project.voxelWorldData = world.serializeRle();
        project.voxelSeed = project.voxelSeed;
        project.voxelRuntime = this;
    }

    public void regenerate(GameProject project, int seed) {
        world.generate(seed);
        project.voxelSeed = seed;
        player.spawn(world, VoxelWorld.SIZE * 0.5f, VoxelWorld.SIZE * 0.5f);
        project.voxelWorldData = world.serializeRle();
        invalidate();
    }

    public void invalidate() {
        cachedDirty = -1;
        cachedTrianglesByBlock = null;
        cachedTriangles = null;
        cachedQuads = null;
    }
}
