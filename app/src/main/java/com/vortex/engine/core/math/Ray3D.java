package com.vortex.engine.core.math;

public class Ray3D {
    public final Vec3 origin;
    public final Vec3 dir;

    public Ray3D(Vec3 origin, Vec3 dir) {
        this.origin = origin;
        this.dir = dir;
    }

    /**
     * Returns distance t >= 0 along ray if intersecting AABB [min, max], or -1f on miss.
     */
    public float intersectAABB(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ
    ) {
        float tMin = 0.0f;
        float tMax = Float.MAX_VALUE;

        float[] o = new float[]{origin.x, origin.y, origin.z};
        float[] d = new float[]{dir.x, dir.y, dir.z};
        float[] bMin = new float[]{minX, minY, minZ};
        float[] bMax = new float[]{maxX, maxY, maxZ};

        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-6f) {
                if (o[i] < bMin[i] || o[i] > bMax[i]) {
                    return -1f;
                }
            } else {
                float invD = 1.0f / d[i];
                float t1 = (bMin[i] - o[i]) * invD;
                float t2 = (bMax[i] - o[i]) * invD;
                if (t1 > t2) {
                    float tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                if (t1 > tMin) tMin = t1;
                if (t2 < tMax) tMax = t2;
                if (tMin > tMax) return -1f;
            }
        }
        return tMin >= 0f ? tMin : tMax;
    }

    /**
     * Intersect horizontal plane y = planeY. Returns hit point or null.
     */
    public Vec3 intersectPlaneY(float planeY) {
        if (Math.abs(dir.y) < 1e-5f) return null;
        float t = (planeY - origin.y) / dir.y;
        if (t < 0f || t > 500f) return null;
        return new Vec3(origin.x + dir.x * t, planeY, origin.z + dir.z * t);
    }
}
