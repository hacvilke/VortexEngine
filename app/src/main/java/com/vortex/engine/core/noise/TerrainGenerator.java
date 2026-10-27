package com.vortex.engine.core.noise;

import com.vortex.engine.core.model.Entity;

/**
 * Procedural 3D Terrain Heightmap & Mesh Builder powered by Jordan Peck's FastNoiseLite (MIT).
 * Shared across 3D Physics Collision, Android OpenGL ES 2.0/3.0, and Desktop 3D Studio.
 */
public class TerrainGenerator {

    public static FastNoiseLite createConfiguredNoise(int seed, float frequency) {
        FastNoiseLite fn = new FastNoiseLite(seed);
        fn.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        fn.SetFractalType(FastNoiseLite.FractalType.FBm);
        fn.SetFractalOctaves(4);
        fn.SetFractalLacunarity(2.0f);
        fn.SetFractalGain(0.5f);
        fn.SetFrequency(Math.max(0.005f, frequency));
        return fn;
    }

    /**
     * Normalized height in [-0.5, +0.5] at local normalized coordinates (nx, nz) in [-0.5, +0.5].
     */
    public static float sampleLocalNormalizedHeight(FastNoiseLite noise, float nx, float nz) {
        // Gentle radial falloff near edges + flat spawn plateau near center (0,0)
        float worldSampleX = nx * 40f;
        float worldSampleZ = nz * 40f;
        float raw = noise.GetNoise(worldSampleX, worldSampleZ); // [-1, 1]
        float distCenter = (float) Math.hypot(nx, nz);
        float spawnFlatten = Math.min(1f, distCenter / 0.12f);
        return raw * 0.5f * spawnFlatten;
    }

    /**
     * Exact world-space Y surface height of a TERRAIN entity at world (wx, wz).
     */
    public static float sampleWorldHeight(Entity terrainEntity, FastNoiseLite noise, float wx, float wz) {
        float sx = Math.max(1f, Math.abs(terrainEntity.scaleX));
        float sy = Math.max(0.2f, Math.abs(terrainEntity.scaleY));
        float sz = Math.max(1f, Math.abs(terrainEntity.scaleZ));

        float nx = (wx - terrainEntity.x) / sx;
        float nz = (wz - terrainEntity.z) / sz;
        if (nx < -0.5f || nx > 0.5f || nz < -0.5f || nz > 0.5f) {
            return Float.NEGATIVE_INFINITY;
        }
        return terrainEntity.y + sampleLocalNormalizedHeight(noise, nx, nz) * sy;
    }

    /**
     * Builds interleaved 8-float-per-vertex (pos3 + normal3 + uv2) triangle mesh in unit [-0.5..0.5] space.
     */
    public static float[] buildInterleavedTerrainVertices(int seed, float frequency, int gridResolution) {
        int res = Math.max(8, Math.min(64, gridResolution));
        FastNoiseLite noise = createConfiguredNoise(seed, frequency);

        float[][] hMap = new float[res + 1][res + 1];
        for (int gz = 0; gz <= res; gz++) {
            float nz = (float) gz / res - 0.5f;
            for (int gx = 0; gx <= res; gx++) {
                float nx = (float) gx / res - 0.5f;
                hMap[gz][gx] = sampleLocalNormalizedHeight(noise, nx, nz);
            }
        }

        // 2 triangles (6 vertices) per grid cell, 8 floats per vertex
        float[] out = new float[res * res * 6 * 8];
        int ptr = 0;
        float step = 1.0f / res;

        for (int gz = 0; gz < res; gz++) {
            float z0 = (float) gz / res - 0.5f;
            float z1 = z0 + step;
            float v0 = (float) gz / res * 6f;
            float v1 = (float) (gz + 1) / res * 6f;

            for (int gx = 0; gx < res; gx++) {
                float x0 = (float) gx / res - 0.5f;
                float x1 = x0 + step;
                float u0 = (float) gx / res * 6f;
                float u1 = (float) (gx + 1) / res * 6f;

                float y00 = hMap[gz][gx];
                float y10 = hMap[gz][gx + 1];
                float y01 = hMap[gz + 1][gx];
                float y11 = hMap[gz + 1][gx + 1];

                ptr = emitTri(out, ptr,
                        x0, y00, z0, u0, v0,
                        x0, y01, z1, u0, v1,
                        x1, y11, z1, u1, v1);

                ptr = emitTri(out, ptr,
                        x0, y00, z0, u0, v0,
                        x1, y11, z1, u1, v1,
                        x1, y10, z0, u1, v0);
            }
        }
        return out;
    }

    private static int emitTri(float[] out, int ptr,
                               float x0, float y0, float z0, float u0, float v0,
                               float x1, float y1, float z1, float u1, float v1,
                               float x2, float y2, float z2, float u2, float v2) {
        float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
        float vx = x2 - x0, vy = y2 - y0, vz = z2 - z0;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len; ny /= len; nz /= len;
        } else {
            nx = 0f; ny = 1f; nz = 0f;
        }

        out[ptr++] = x0; out[ptr++] = y0; out[ptr++] = z0;
        out[ptr++] = nx; out[ptr++] = ny; out[ptr++] = nz;
        out[ptr++] = u0; out[ptr++] = v0;

        out[ptr++] = x1; out[ptr++] = y1; out[ptr++] = z1;
        out[ptr++] = nx; out[ptr++] = ny; out[ptr++] = nz;
        out[ptr++] = u1; out[ptr++] = v1;

        out[ptr++] = x2; out[ptr++] = y2; out[ptr++] = z2;
        out[ptr++] = nx; out[ptr++] = ny; out[ptr++] = nz;
        out[ptr++] = u2; out[ptr++] = v2;
        return ptr;
    }
}
