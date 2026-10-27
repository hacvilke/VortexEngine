package com.vortex.engine.core.math;

public class Vec3 {
    public float x;
    public float y;
    public float z;

    public Vec3() {
        this(0f, 0f, 0f);
    }

    public Vec3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3 set(float nx, float ny, float nz) {
        this.x = nx;
        this.y = ny;
        this.z = nz;
        return this;
    }

    public Vec3 copy() {
        return new Vec3(x, y, z);
    }

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 mul(float s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public float dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(
                y * o.z - z * o.y,
                z * o.x - x * o.z,
                x * o.y - y * o.x
        );
    }

    public float len() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public Vec3 normalize() {
        float l = len();
        if (l < 1e-6f) return new Vec3(0f, 0f, 0f);
        return new Vec3(x / l, y / l, z / l);
    }

    public static float dist(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
