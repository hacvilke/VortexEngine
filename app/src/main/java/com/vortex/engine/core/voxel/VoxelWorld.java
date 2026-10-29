package com.vortex.engine.core.voxel;

import com.vortex.engine.core.noise.FastNoiseLite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vortex Voxel World (v4.8.0) — a Minecraft-style block grid that runs everywhere the engine runs
 * (desktop software renderer, Android GL renderer, exported builds).
 *
 * Design notes for beginners reading this file:
 *  - Blocks are stored in one flat byte array: index = y * SIZE * SIZE + z * SIZE + x.
 *    A byte holds the block id ({@link BlockType}); AIR is 0.
 *  - The world is 96x96 blocks wide with a height of 64 by default. That is 589,824 cells = 0.6 MB,
 *    which is why one world fits easily on a phone. Save files use run-length encoding instead.
 *  - Only faces that touch air are meshed ("face culling"), and neighbouring faces of the same block
 *    are merged into big rectangles ("greedy meshing"), so a whole landscape is a few thousand
 *    triangles instead of millions.
 */
public final class VoxelWorld {

    public static final int SIZE = 96;             // x/z extent in blocks
    public static final int HEIGHT = 64;           // y extent in blocks
    public static final int SEA_LEVEL = 18;        // water fills up to here
    public static final int VERSION = 1;

    public final int size;
    public final int height;
    public final byte[] blocks;

    private int dirtyFlag = 1;

    public VoxelWorld(int size, int height) {
        this.size = size;
        this.height = height;
        this.blocks = new byte[size * size * height];
    }

    public static VoxelWorld createDefault() {
        return new VoxelWorld(SIZE, HEIGHT);
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < size && y < height && z < size;
    }

    public int get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return BlockType.AIR;
        return blocks[(y * size + z) * size + x] & 0xFF;
    }

    public void set(int x, int y, int z, int id) {
        if (!inBounds(x, y, z)) return;
        int i = (y * size + z) * size + x;
        if ((blocks[i] & 0xFF) == id) return;
        blocks[i] = (byte) id;
        dirtyFlag++;
    }

    public boolean isSolid(int x, int y, int z) {
        // World edges are solid walls below sea level so the player cannot fall out of the map.
        if (y < 0) return true;
        if (x < 0 || z < 0 || x >= size || z >= size || y >= height) return false;
        return BlockType.isSolid(get(x, y, z));
    }

    public boolean isSolidAt(float wx, float wy, float wz) {
        return isSolid((int) Math.floor(wx), (int) Math.floor(wy), (int) Math.floor(wz));
    }

    /** Bumped on every change; renderers rebuild their mesh when it differs from their cached value. */
    public int dirty() { return dirtyFlag; }

    public void markClean(int version) { dirtyFlag = version; }

    /** Height of the highest solid block in a column (used for spawning and for map helpers). */
    public int surfaceHeight(int x, int z) {
        for (int y = height - 1; y >= 0; y--) {
            if (BlockType.isSolid(get(x, y, z))) return y;
        }
        return 0;
    }

    public int highestSolidY() {
        for (int y = height - 1; y > 0; y--) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    if (get(x, y, z) != BlockType.AIR) return y;
                }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ generation

    /**
     * Generates rolling hills, beaches, water, caves-free stone strata and oak trees.
     * Deterministic for a seed, so "Seed 4711" always rebuilds the exact same valley.
     */
    public void generate(int seed) {
        FastNoiseLite hills = new FastNoiseLite(seed);
        hills.SetFrequency(0.012f);
        FastNoiseLite detail = new FastNoiseLite(seed + 17);
        detail.SetFrequency(0.055f);
        FastNoiseLite biome = new FastNoiseLite(seed + 91);
        biome.SetFrequency(0.006f);

        java.util.Arrays.fill(blocks, (byte) BlockType.AIR);

        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                float h = hills.GetNoise(x, z) * 0.5f + 0.5f;
                float d = detail.GetNoise(x, z);
                float b = biome.GetNoise(x, z);
                // Base sits BELOW sea level so valleys flood into lakes and coastlines appear.
                int top = Math.round(SEA_LEVEL - 5f + h * 17f + d * 3.2f);
                if (b > 0.35f) top += Math.round((b - 0.35f) * 30f); // mountain biome
                top = Math.max(3, Math.min(height - 8, top));

                boolean beach = top <= SEA_LEVEL + 1;
                int stoneTop = Math.max(1, top - 4);
                for (int y = 0; y <= top; y++) {
                    int id;
                    if (y == top) {
                        id = beach ? BlockType.SAND : (top > 38 ? BlockType.SNOW : BlockType.GRASS);
                    } else if (y > stoneTop) {
                        id = beach ? BlockType.SAND : BlockType.DIRT;
                    } else {
                        id = BlockType.STONE;
                    }
                    set(x, y, z, id);
                }
                for (int y = top + 1; y <= SEA_LEVEL; y++) {
                    set(x, y, z, BlockType.WATER);
                }
            }
        }

        // Trees: one attempt per 12x12 patch, never in water or on beaches.
        java.util.Random rng = new java.util.Random(seed * 31L + 7L);
        for (int gz = 2; gz < size - 2; gz += 6) {
            for (int gx = 2; gx < size - 2; gx += 6) {
                if (rng.nextInt(100) > 34) continue;
                int x = gx + rng.nextInt(6) - 3;
                int z = gz + rng.nextInt(6) - 3;
                if (x < 3 || z < 3 || x > size - 4 || z > size - 4) continue;
                int y = surfaceHeight(x, z);
                int ground = get(x, y, z);
                if (ground != BlockType.GRASS) continue;
                int trunk = 3 + rng.nextInt(3);
                for (int i = 0; i < trunk; i++) set(x, y + 1 + i, z, BlockType.WOOD);
                int topY = y + trunk;
                for (int dy = -1; dy <= 1; dy++) {
                    int radius = dy == 1 ? 1 : 2;
                    for (int dz = -radius; dz <= radius; dz++) {
                        for (int dx = -radius; dx <= radius; dx++) {
                            // Rounded, full canopy: no random corner holes, so oaks read as trees
                            // rather than as crumbs of green floating over a trunk.
                            if (dy == 1 && Math.abs(dx) == 1 && Math.abs(dz) == 1) continue;
                            int tx = x + dx, ty = topY + dy, tz = z + dz;
                            if (get(tx, ty, tz) == BlockType.AIR) set(tx, ty, tz, BlockType.LEAVES);
                        }
                    }
                }
            }
        }
        dirtyFlag++;
    }

    /**
     * Picks a pleasant place to start near (nearX, nearZ) and flattens a small meadow there:
     * a hilltop with sky above and a few flat blocks around, so the first thing a player sees is a
     * view instead of the inside of a hill. Returns {x, z}.
     */
    public int[] prepareSpawn(int nearX, int nearZ) {
        nearX = Math.max(12, Math.min(size - 13, nearX));
        nearZ = Math.max(12, Math.min(size - 13, nearZ));
        int bestX = nearX, bestZ = nearZ;
        float best = -1e9f;
        for (int dz = -10; dz <= 10; dz += 2) {
            for (int dx = -10; dx <= 10; dx += 2) {
                int x = nearX + dx, z = nearZ + dz;
                int h = surfaceHeight(x, z);
                if (get(x, h, z) != BlockType.GRASS) continue;   // never spawn on sand/water/snow
                float openness = 0f;
                for (int y = h + 1; y <= Math.min(height - 1, h + 6); y++) {
                    if (get(x, y, z) == BlockType.AIR) openness += 1f;
                }
                if (openness < 5f) continue;                     // inside a cave or under a tree
                float flat = 0f;
                float lo = h, hi = h;
                for (int sz = -2; sz <= 2; sz += 2) {
                    for (int sx = -2; sx <= 2; sx += 2) {
                        int nh = surfaceHeight(x + sx, z + sz);
                        lo = Math.min(lo, nh);
                        hi = Math.max(hi, nh);
                    }
                }
                flat = 6f - (hi - lo);                           // reward level ground
                float score = h * 0.8f + flat * 2.0f + openness
                        - Math.max(0f, h - (SEA_LEVEL + 2)) * 0.35f;   // avoid spawning on peaks
                if (score > best) { best = score; bestX = x; bestZ = z; }
            }
        }
        clearSpawnPad(bestX, bestZ, 6);
        return new int[]{bestX, bestZ};
    }

    /**
     * Flattens a circular area to its centre column height: grass on top, three dirt layers, stone
     * below, and everything above (hills, trees, water) removed. Used for the spawn meadow and
     * available to scripts as a "build me a clearing" helper.
     */
    public void clearSpawnPad(int cx, int cz, int radius) {
        cx = Math.max(2, Math.min(size - 3, cx));
        cz = Math.max(2, Math.min(size - 3, cz));
        int floorY = Math.max(4, surfaceHeight(cx, cz));
        if (floorY <= SEA_LEVEL) floorY = SEA_LEVEL + 1;   // never drown on spawn
        float rMax = Math.max(2f, radius);
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx, z = cz + dz;
                if (x < 1 || z < 1 || x >= size - 1 || z >= size - 1) continue;
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d > rMax) continue;
                // Blend the flat centre into the untouched terrain with a squared falloff, so the
                // clearing reads as a meadow instead of a mesa with cliff walls.
                float w = d / rMax;
                w = w * w;
                int original = surfaceHeight(x, z);
                int target = Math.round(floorY * (1f - w) + original * w);
                if (d < 2.5f) target = floorY;              // dead-flat spawn disc
                for (int y = target + 1; y < height; y++) {
                    if (get(x, y, z) != BlockType.AIR) set(x, y, z, BlockType.AIR);
                }
                for (int y = Math.max(1, target - 3); y <= target; y++) {
                    set(x, y, z, (y == target) ? BlockType.GRASS : BlockType.DIRT);
                }
                for (int y = 1; y < target - 3; y++) {
                    if (get(x, y, z) == BlockType.AIR) set(x, y, z, BlockType.STONE);
                }
            }
        }
        dirtyFlag++;
    }

    // ------------------------------------------------------------------ raycast

    /** Result of a voxel raycast: hit block coordinates, the face normal and the adjacent air cell. */
    public static final class RayHit {
        public int x, y, z;          // the solid block that was hit
        public int nx, ny, nz;       // face normal pointing back at the ray origin
        public int px, py, pz;       // the empty cell in front of that face (where a new block goes)
        public float distance;
        public int blockId;
    }

    /**
     * Amanatides & Woo voxel traversal. Used for "look at a block and break it".
     * Returns null when nothing solid is within {@code maxDistance} blocks.
     */
    public RayHit raycast(float ox, float oy, float oz, float dx, float dy, float dz, float maxDistance) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) return null;
        dx /= len; dy /= len; dz /= len;

        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        float tDeltaX = dx == 0 ? Float.MAX_VALUE : Math.abs(1f / dx);
        float tDeltaY = dy == 0 ? Float.MAX_VALUE : Math.abs(1f / dy);
        float tDeltaZ = dz == 0 ? Float.MAX_VALUE : Math.abs(1f / dz);
        float tMaxX = dx == 0 ? Float.MAX_VALUE : ((dx > 0 ? (x + 1 - ox) : (ox - x)) * tDeltaX);
        float tMaxY = dy == 0 ? Float.MAX_VALUE : ((dy > 0 ? (y + 1 - oy) : (oy - y)) * tDeltaY);
        float tMaxZ = dz == 0 ? Float.MAX_VALUE : ((dz > 0 ? (z + 1 - oz) : (oz - z)) * tDeltaZ);

        int nx = 0, ny = 0, nz = 0;
        float t = 0f;
        while (t <= maxDistance) {
            int id = get(x, y, z);
            if (BlockType.isSolid(id) || id == BlockType.WATER) {
                RayHit hit = new RayHit();
                hit.x = x; hit.y = y; hit.z = z;
                hit.nx = nx; hit.ny = ny; hit.nz = nz;
                hit.px = x + nx; hit.py = y + ny; hit.pz = z + nz;
                hit.distance = t;
                hit.blockId = id;
                return hit;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX; t = tMaxX; tMaxX += tDeltaX; nx = -stepX; ny = 0; nz = 0;
            } else if (tMaxY < tMaxZ) {
                y += stepY; t = tMaxY; tMaxY += tDeltaY; nx = 0; ny = -stepY; nz = 0;
            } else {
                z += stepZ; t = tMaxZ; tMaxZ += tDeltaZ; nx = 0; ny = 0; nz = -stepZ;
            }
            if (y < -2 || y > height + 2) break;
        }
        return null;
    }

    // ------------------------------------------------------------------ editing

    public boolean breakBlock(int x, int y, int z) {
        if (!inBounds(x, y, z)) return false;
        int id = get(x, y, z);
        if (id == BlockType.AIR || id == BlockType.WATER) return false;
        set(x, y, z, BlockType.AIR);
        return true;
    }

    public boolean placeBlock(int x, int y, int z, int id) {
        if (!inBounds(x, y, z) || id == BlockType.AIR) return false;
        if (get(x, y, z) != BlockType.AIR && get(x, y, z) != BlockType.WATER) return false;
        set(x, y, z, id);
        return true;
    }

    // ------------------------------------------------------------------ save / load

    /** Run-length encoded "id:count," pairs, base-10. Compact enough for a whole world in JSON. */
    public String serializeRle() {
        StringBuilder sb = new StringBuilder(blocks.length / 3);
        int current = blocks.length > 0 ? (blocks[0] & 0xFF) : 0;
        int count = 0;
        for (int i = 0; i < blocks.length; i++) {
            int v = blocks[i] & 0xFF;
            if (v == current) {
                count++;
            } else {
                sb.append(current).append(':').append(count).append(',');
                current = v;
                count = 1;
            }
        }
        if (count > 0) sb.append(current).append(':').append(count);
        return sb.toString();
    }

    public static VoxelWorld fromRle(String data, int size, int height) {
        VoxelWorld w = new VoxelWorld(size, height);
        if (data == null || data.isEmpty()) return w;
        int index = 0;
        for (String pair : data.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) continue;
            int id, count;
            try {
                id = Integer.parseInt(pair.substring(0, colon).trim());
                count = Integer.parseInt(pair.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                continue;
            }
            for (int i = 0; i < count && index < w.blocks.length; i++) {
                w.blocks[index++] = (byte) id;
            }
            if (index >= w.blocks.length) break;
        }
        return w;
    }

    // ------------------------------------------------------------------ greedy meshing

    /**
     * A merged quad: two triangles, six floats per vertex for the desktop renderer (pos + normal),
     * and the block id it belongs to.
     */
    public static final class Quad {
        public final int blockId;
        public final float[] vertices; // 12 floats: v0(3) v1(3) v2(3) v3(3)
        public final float nx, ny, nz;

        Quad(int blockId, float[] v, float nx, float ny, float nz) {
            this.blockId = blockId;
            this.vertices = v;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }
    }

    /**
     * Builds the visible surface as merged quads.
     *
     * @param radius when > 0 only blocks within this distance of (centerX, centerZ) are meshed, which
     *               is how the phone keeps the frame rate high; 0 meshes the whole world.
     */
    public List<Quad> buildQuads(int centerX, int centerZ, int radius) {
        List<Quad> out = new ArrayList<>();
        int minX = 0, maxX = size - 1, minZ = 0, maxZ = size - 1;
        if (radius > 0) {
            minX = Math.max(0, centerX - radius);
            maxX = Math.min(size - 1, centerX + radius);
            minZ = Math.max(0, centerZ - radius);
            maxZ = Math.min(size - 1, centerZ + radius);
        }

        // Six directions: +x, -x, +y, -y, +z, -z
        for (int axis = 0; axis < 3; axis++) {
            for (int dir = 0; dir < 2; dir++) {
                boolean positive = dir == 0;
                int nx = axis == 0 ? (positive ? 1 : -1) : 0;
                int ny = axis == 1 ? (positive ? 1 : -1) : 0;
                int nz = axis == 2 ? (positive ? 1 : -1) : 0;

                int uAxis = (axis + 1) % 3;   // second axis
                int vAxis = (axis + 2) % 3;   // third axis
                int uSize = axisSize(uAxis);
                int vSize = axisSize(vAxis);
                int sliceCount = axisSize(axis);

                int[] mask = new int[uSize * vSize];
                int[] maskPos = new int[uSize * vSize];

                for (int slice = 0; slice < sliceCount; slice++) {
                    java.util.Arrays.fill(mask, 0);
                    for (int v = 0; v < vSize; v++) {
                        for (int u = 0; u < uSize; u++) {
                            int[] pos = new int[3];
                            pos[axis] = slice;
                            pos[uAxis] = u;
                            pos[vAxis] = v;
                            int x = pos[0], y = pos[1], z = pos[2];
                            if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                            int id = get(x, y, z);
                            if (id == BlockType.AIR) continue;
                            int nX = x + nx, nY = y + ny, nZ = z + nz;
                            int neighbor;
                            if (nY < 0) neighbor = BlockType.STONE;
                            else if (nY >= height || nX < 0 || nZ < 0 || nX >= size || nZ >= size) neighbor = BlockType.AIR;
                            else neighbor = get(nX, nY, nZ);
                            boolean visible;
                            if (neighbor == BlockType.AIR) visible = true;
                            else if (BlockType.isTransparent(neighbor) && neighbor != id) visible = true;
                            else visible = false;
                            if (visible) {
                                mask[v * uSize + u] = id;
                                maskPos[v * uSize + u] = (y * size + z) * size + x;
                            }
                        }
                    }
                    // Greedy rectangle merge on this slice
                    for (int v = 0; v < vSize; v++) {
                        for (int u = 0; u < uSize; u++) {
                            int id = mask[v * uSize + u];
                            if (id == 0) continue;
                            int w = 1, h = 1;
                            while (u + w < uSize && mask[v * uSize + u + w] == id) w++;
                            boolean grow = true;
                            while (grow && v + h < vSize) {
                                for (int k = 0; k < w; k++) {
                                    if (mask[(v + h) * uSize + u + k] != id) { grow = false; break; }
                                }
                                if (grow) h++;
                            }
                            for (int vv = 0; vv < h; vv++) {
                                for (int uu = 0; uu < w; uu++) {
                                    mask[(v + vv) * uSize + u + uu] = 0;
                                }
                            }
                            float[] corners = quadCorners(axis, positive, slice, u, v, w, h,
                                    uAxis, vAxis, nx, ny, nz);
                            out.add(new Quad(id, corners, nx, ny, nz));
                        }
                    }
                }
            }
        }
        return out;
    }

    private int axisSize(int axis) {
        return axis == 1 ? height : size;
    }

    /** Corner coordinates for a merged face; ordering gives counter-clockwise winding as seen from outside. */
    private float[] quadCorners(int axis, boolean positive, int slice, int u, int v, int w, int h,
                                int uAxis, int vAxis, int nx, int ny, int nz) {
        float plane = positive ? slice + 1f : slice;
        float[] pts = new float[12];
        float[][] uv = {
                {u, v}, {u + w, v}, {u + w, v + h}, {u, v + h}
        };
        // Flip the winding for negative faces so the normal always points outwards.
        int[] order = positive ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};
        for (int i = 0; i < 4; i++) {
            float[] q = uv[order[i]];
            int[] pos = new int[3];
            pos[axis] = 0;
            pos[uAxis] = 0;
            pos[vAxis] = 0;
            float[] p3 = new float[3];
            p3[axis] = plane;
            p3[uAxis] = q[0];
            p3[vAxis] = q[1];
            pts[i * 3] = p3[0];
            pts[i * 3 + 1] = p3[1];
            pts[i * 3 + 2] = p3[2];
        }
        return pts;
    }

    /**
     * Converts merged quads into flat triangles: 3 vertices per triangle, 6 floats per vertex
     * (x, y, z, nx, ny, nz). Renderers that want triangles for a "draw now" software rasteriser
     * use this; the GL path builds a mesh instead.
     */
    public static float[] quadsToTriangles(List<Quad> quads) {
        float[] out = new float[quads.size() * 2 * 18];
        int o = 0;
        for (Quad q : quads) {
            float[] v = q.vertices;
            int[] idx = {0, 1, 2, 0, 2, 3};
            for (int i : idx) {
                out[o++] = v[i * 3];
                out[o++] = v[i * 3 + 1];
                out[o++] = v[i * 3 + 2];
                out[o++] = q.nx;
                out[o++] = q.ny;
                out[o++] = q.nz;
            }
        }
        return out;
    }

    /** Grouped triangle soup per block id — one draw call per material on the GL renderer. */
    public static Map<Integer, float[]> trianglesByBlock(List<Quad> quads) {
        Map<Integer, List<Quad>> grouped = new LinkedHashMap<>();
        for (Quad q : quads) {
            List<Quad> list = grouped.get(q.blockId);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(q.blockId, list);
            }
            list.add(q);
        }
        Map<Integer, float[]> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Quad>> e : grouped.entrySet()) {
            out.put(e.getKey(), quadsToTriangles(e.getValue()));
        }
        return out;
    }
}
