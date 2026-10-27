package com.vortex.engine.core.math;

public class Vec2 {
    public float x;
    public float y;

    public Vec2() {
        this(0f, 0f);
    }

    public Vec2(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vec2 set(float nx, float ny) {
        this.x = nx;
        this.y = ny;
        return this;
    }

    public Vec2 copy() {
        return new Vec2(x, y);
    }

    public Vec2 add(Vec2 o) {
        return new Vec2(x + o.x, y + o.y);
    }

    public Vec2 sub(Vec2 o) {
        return new Vec2(x - o.x, y - o.y);
    }

    public Vec2 mul(float s) {
        return new Vec2(x * s, y * s);
    }

    public float dot(Vec2 o) {
        return x * o.x + y * o.y;
    }

    public float len() {
        return (float) Math.hypot(x, y);
    }

    public Vec2 normalize() {
        float l = len();
        if (l < 1e-6f) return new Vec2(0f, 0f);
        return new Vec2(x / l, y / l);
    }

    public static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }
}
