package com.vortex.engine.core.render;

import android.opengl.GLES20;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class Mesh3D {
    // Interleaved layout: pos(3) + normal(3) + uv(2) = 8 floats (32 bytes)
    public static final int FLOATS_PER_VERTEX = 8;
    public static final int STRIDE_BYTES = FLOATS_PER_VERTEX * 4;

    public final FloatBuffer vertexBuffer;
    public final int vertexCount;

    public Mesh3D(float[] interleavedVertices) {
        this.vertexCount = interleavedVertices.length / FLOATS_PER_VERTEX;
        ByteBuffer bb = ByteBuffer.allocateDirect(interleavedVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        this.vertexBuffer = bb.asFloatBuffer();
        this.vertexBuffer.put(interleavedVertices);
        this.vertexBuffer.position(0);
    }

    public void draw(int aPos, int aNormal, int aUV) {
        vertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer);

        if (aNormal >= 0) {
            vertexBuffer.position(3);
            GLES20.glEnableVertexAttribArray(aNormal);
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer);
        }

        if (aUV >= 0) {
            vertexBuffer.position(6);
            GLES20.glEnableVertexAttribArray(aUV);
            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        GLES20.glDisableVertexAttribArray(aPos);
        if (aNormal >= 0) GLES20.glDisableVertexAttribArray(aNormal);
        if (aUV >= 0) GLES20.glDisableVertexAttribArray(aUV);
    }

    private static void addVert(List<Float> out,
                                float x, float y, float z,
                                float nx, float ny, float nz,
                                float u, float v) {
        out.add(x); out.add(y); out.add(z);
        out.add(nx); out.add(ny); out.add(nz);
        out.add(u); out.add(v);
    }

    private static void addQuad(List<Float> out,
                                float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float nx, float ny, float nz,
                                float uvScale) {
        addVert(out, x0, y0, z0, nx, ny, nz, 0f, 0f);
        addVert(out, x1, y1, z1, nx, ny, nz, uvScale, 0f);
        addVert(out, x2, y2, z2, nx, ny, nz, uvScale, uvScale);

        addVert(out, x0, y0, z0, nx, ny, nz, 0f, 0f);
        addVert(out, x2, y2, z2, nx, ny, nz, uvScale, uvScale);
        addVert(out, x3, y3, z3, nx, ny, nz, 0f, uvScale);
    }

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }

    public static Mesh3D createCube() {
        List<Float> v = new ArrayList<>(36 * 8);
        float h = 0.5f;
        // Front (+Z)
        addQuad(v, -h, -h,  h,  h, -h,  h,  h,  h,  h, -h,  h,  h,  0,  0,  1, 1f);
        // Back (-Z)
        addQuad(v,  h, -h, -h, -h, -h, -h, -h,  h, -h,  h,  h, -h,  0,  0, -1, 1f);
        // Right (+X)
        addQuad(v,  h, -h,  h,  h, -h, -h,  h,  h, -h,  h,  h,  h,  1,  0,  0, 1f);
        // Left (-X)
        addQuad(v, -h, -h, -h, -h, -h,  h, -h,  h,  h, -h,  h, -h, -1,  0,  0, 1f);
        // Top (+Y)
        addQuad(v, -h,  h,  h,  h,  h,  h,  h,  h, -h, -h,  h, -h,  0,  1,  0, 1f);
        // Bottom (-Y)
        addQuad(v, -h, -h, -h,  h, -h, -h,  h, -h,  h, -h, -h,  h,  0, -1,  0, 1f);
        return new Mesh3D(toArray(v));
    }

    public static Mesh3D createPlane() {
        List<Float> v = new ArrayList<>(12 * 8);
        float h = 0.5f;
        addQuad(v, -h, 0f,  h,  h, 0f,  h,  h, 0f, -h, -h, 0f, -h, 0f, 1f, 0f, 4f);
        addQuad(v, -h, 0f, -h,  h, 0f, -h,  h, 0f,  h, -h, 0f,  h, 0f, -1f, 0f, 4f);
        return new Mesh3D(toArray(v));
    }

    public static Mesh3D createPyramid() {
        List<Float> v = new ArrayList<>(18 * 8);
        float h = 0.5f;
        // 4 sloped faces
        addTriAutoNormal(v,  0f, h, 0f, -h, -h,  h,  h, -h,  h);
        addTriAutoNormal(v,  0f, h, 0f,  h, -h,  h,  h, -h, -h);
        addTriAutoNormal(v,  0f, h, 0f,  h, -h, -h, -h, -h, -h);
        addTriAutoNormal(v,  0f, h, 0f, -h, -h, -h, -h, -h,  h);
        // Bottom quad
        addQuad(v, -h, -h, -h,  h, -h, -h,  h, -h,  h, -h, -h,  h, 0f, -1f, 0f, 1f);
        return new Mesh3D(toArray(v));
    }

    private static void addTriAutoNormal(List<Float> out,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float x2, float y2, float z2) {
        float ux = x1 - x0, uy = y1 - y0, uz = z1 - z0;
        float vx = x2 - x0, vy = y2 - y0, vz = z2 - z0;
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) {
            nx /= len; ny /= len; nz /= len;
        }
        addVert(out, x0, y0, z0, nx, ny, nz, 0.5f, 1f);
        addVert(out, x1, y1, z1, nx, ny, nz, 0f, 0f);
        addVert(out, x2, y2, z2, nx, ny, nz, 1f, 0f);
    }

    public static Mesh3D createSphere(int stacks, int slices) {
        List<Float> v = new ArrayList<>(stacks * slices * 6 * 8);
        float r = 0.5f;
        for (int i = 0; i < stacks; i++) {
            double phi0 = Math.PI * (-0.5 + (double) i / stacks);
            double phi1 = Math.PI * (-0.5 + (double) (i + 1) / stacks);
            float y0 = (float) Math.sin(phi0), cy0 = (float) Math.cos(phi0);
            float y1 = (float) Math.sin(phi1), cy1 = (float) Math.cos(phi1);

            for (int j = 0; j < slices; j++) {
                double th0 = 2.0 * Math.PI * (double) j / slices;
                double th1 = 2.0 * Math.PI * (double) (j + 1) / slices;
                float x00 = (float) Math.cos(th0) * cy0, z00 = (float) Math.sin(th0) * cy0;
                float x10 = (float) Math.cos(th1) * cy0, z10 = (float) Math.sin(th1) * cy0;
                float x01 = (float) Math.cos(th0) * cy1, z01 = (float) Math.sin(th0) * cy1;
                float x11 = (float) Math.cos(th1) * cy1, z11 = (float) Math.sin(th1) * cy1;

                float u0 = (float) j / slices, u1 = (float) (j + 1) / slices;
                float v0 = (float) i / stacks, v1 = (float) (i + 1) / stacks;

                addVert(v, x00 * r, y0 * r, z00 * r, x00, y0, z00, u0, v0);
                addVert(v, x01 * r, y1 * r, z01 * r, x01, y1, z01, u0, v1);
                addVert(v, x11 * r, y1 * r, z11 * r, x11, y1, z11, u1, v1);

                addVert(v, x00 * r, y0 * r, z00 * r, x00, y0, z00, u0, v0);
                addVert(v, x11 * r, y1 * r, z11 * r, x11, y1, z11, u1, v1);
                addVert(v, x10 * r, y0 * r, z10 * r, x10, y0, z10, u1, v0);
            }
        }
        return new Mesh3D(toArray(v));
    }

    public static Mesh3D createCylinder(int slices) {
        List<Float> v = new ArrayList<>(slices * 12 * 8);
        float r = 0.5f;
        float h = 0.5f;
        for (int i = 0; i < slices; i++) {
            double a0 = 2.0 * Math.PI * i / slices;
            double a1 = 2.0 * Math.PI * (i + 1) / slices;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float u0 = (float) i / slices, u1 = (float) (i + 1) / slices;

            // Side quad
            addVert(v, c0 * r, -h, s0 * r, c0, 0f, s0, u0, 0f);
            addVert(v, c1 * r, -h, s1 * r, c1, 0f, s1, u1, 0f);
            addVert(v, c1 * r,  h, s1 * r, c1, 0f, s1, u1, 1f);

            addVert(v, c0 * r, -h, s0 * r, c0, 0f, s0, u0, 0f);
            addVert(v, c1 * r,  h, s1 * r, c1, 0f, s1, u1, 1f);
            addVert(v, c0 * r,  h, s0 * r, c0, 0f, s0, u0, 1f);

            // Top cap
            addVert(v, 0f, h, 0f, 0f, 1f, 0f, 0.5f, 0.5f);
            addVert(v, c1 * r, h, s1 * r, 0f, 1f, 0f, 0.5f + c1 * 0.5f, 0.5f + s1 * 0.5f);
            addVert(v, c0 * r, h, s0 * r, 0f, 1f, 0f, 0.5f + c0 * 0.5f, 0.5f + s0 * 0.5f);

            // Bottom cap
            addVert(v, 0f, -h, 0f, 0f, -1f, 0f, 0.5f, 0.5f);
            addVert(v, c0 * r, -h, s0 * r, 0f, -1f, 0f, 0.5f + c0 * 0.5f, 0.5f + s0 * 0.5f);
            addVert(v, c1 * r, -h, s1 * r, 0f, -1f, 0f, 0.5f + c1 * 0.5f, 0.5f + s1 * 0.5f);
        }
        return new Mesh3D(toArray(v));
    }
}
