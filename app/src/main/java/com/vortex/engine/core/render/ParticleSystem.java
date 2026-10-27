package com.vortex.engine.core.render;

import android.opengl.GLES20;
import com.vortex.engine.core.model.Entity;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

public class ParticleSystem {
    private static final int MAX_PARTICLES = 384;
    // 7 floats per point: x, y, z, r, g, b, a
    private static final int FLOATS_PER_PT = 7;

    private final float[] px = new float[MAX_PARTICLES];
    private final float[] py = new float[MAX_PARTICLES];
    private final float[] pz = new float[MAX_PARTICLES];
    private final float[] vx = new float[MAX_PARTICLES];
    private final float[] vy = new float[MAX_PARTICLES];
    private final float[] vz = new float[MAX_PARTICLES];
    private final float[] cr = new float[MAX_PARTICLES];
    private final float[] cg = new float[MAX_PARTICLES];
    private final float[] cb = new float[MAX_PARTICLES];
    private final float[] life = new float[MAX_PARTICLES];
    private final float[] maxLife = new float[MAX_PARTICLES];
    private int cursor = 0;

    private final float[] gpuScratch = new float[MAX_PARTICLES * FLOATS_PER_PT];
    private final FloatBuffer gpuBuffer;
    private final Random rng = new Random();

    public ParticleSystem() {
        ByteBuffer bb = ByteBuffer.allocateDirect(gpuScratch.length * 4);
        bb.order(ByteOrder.nativeOrder());
        gpuBuffer = bb.asFloatBuffer();
    }

    public synchronized void clear() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            life[i] = 0f;
        }
    }

    public synchronized void emitBurst(float x, float y, float z, int count, String colorHex, boolean is3D) {
        float[] rgba = Entity.hexToRgba(colorHex, 1f);
        int clamped = Math.max(1, Math.min(count, 80));
        for (int i = 0; i < clamped; i++) {
            int idx = cursor;
            cursor = (cursor + 1) % MAX_PARTICLES;

            px[idx] = x;
            py[idx] = y;
            pz[idx] = is3D ? z : 0.2f;

            float speed = 2.0f + rng.nextFloat() * 7.5f;
            double theta = rng.nextDouble() * Math.PI * 2.0;
            if (is3D) {
                double phi = (rng.nextDouble() - 0.35) * Math.PI;
                vx[idx] = (float) (Math.cos(theta) * Math.cos(phi) * speed);
                vy[idx] = (float) (Math.sin(phi) * speed + 2.2f);
                vz[idx] = (float) (Math.sin(theta) * Math.cos(phi) * speed);
            } else {
                vx[idx] = (float) (Math.cos(theta) * speed);
                vy[idx] = (float) (Math.sin(theta) * speed);
                vz[idx] = 0f;
            }

            cr[idx] = Math.min(1f, rgba[0] + (rng.nextFloat() - 0.5f) * 0.2f);
            cg[idx] = Math.min(1f, rgba[1] + (rng.nextFloat() - 0.5f) * 0.2f);
            cb[idx] = Math.min(1f, rgba[2] + (rng.nextFloat() - 0.5f) * 0.2f);
            maxLife[idx] = 0.35f + rng.nextFloat() * 0.55f;
            life[idx] = maxLife[idx];
        }
    }

    public synchronized void update(float dt, float gravityY) {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (life[i] <= 0f) continue;
            life[i] -= dt;
            vy[i] += gravityY * 0.35f * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
        }
    }

    public synchronized void draw(ShaderProgram flatShader, float[] vpMatrix, float pointSizePx) {
        int active = 0;
        for (int i = 0; i < MAX_PARTICLES; i++) {
            if (life[i] <= 0f) continue;
            float alpha = Math.max(0f, Math.min(1f, life[i] / Math.max(0.01f, maxLife[i])));
            int base = active * FLOATS_PER_PT;
            gpuScratch[base] = px[i];
            gpuScratch[base + 1] = py[i];
            gpuScratch[base + 2] = pz[i];
            gpuScratch[base + 3] = cr[i];
            gpuScratch[base + 4] = cg[i];
            gpuScratch[base + 5] = cb[i];
            gpuScratch[base + 6] = alpha;
            active++;
        }
        if (active == 0) return;

        gpuBuffer.position(0);
        gpuBuffer.put(gpuScratch, 0, active * FLOATS_PER_PT);

        flatShader.use();
        GLES20.glUniformMatrix4fv(flatShader.uniform("uMVP"), 1, false, vpMatrix, 0);
        GLES20.glUniform1f(flatShader.uniform("uPointSize"), pointSizePx);

        int aPos = flatShader.attr("aPosition");
        int aCol = flatShader.attr("aColor");
        int stride = FLOATS_PER_PT * 4;

        gpuBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, gpuBuffer);

        gpuBuffer.position(3);
        GLES20.glEnableVertexAttribArray(aCol);
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, stride, gpuBuffer);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, active);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aCol);
    }
}
