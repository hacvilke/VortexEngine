package com.vortex.engine.core.math;

import android.opengl.Matrix;

public class Mat4 {
    public final float[] m = new float[16];

    public Mat4() {
        identity();
    }

    public Mat4 identity() {
        Matrix.setIdentityM(m, 0);
        return this;
    }

    public static void buildModelMatrix(
            float[] out,
            float tx, float ty, float tz,
            float rx, float ry, float rz,
            float sx, float sy, float sz
    ) {
        Matrix.setIdentityM(out, 0);
        Matrix.translateM(out, 0, tx, ty, tz);
        if (ry != 0f) Matrix.rotateM(out, 0, ry, 0f, 1f, 0f);
        if (rx != 0f) Matrix.rotateM(out, 0, rx, 1f, 0f, 0f);
        if (rz != 0f) Matrix.rotateM(out, 0, rz, 0f, 0f, 1f);
        Matrix.scaleM(out, 0, sx, sy, sz);
    }

    public static Ray3D unprojectTouchToRay(
            float touchX, float touchY,
            float viewportW, float viewportH,
            float[] viewProjMatrix
    ) {
        if (viewportW <= 1f || viewportH <= 1f) {
            return new Ray3D(new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, -1f));
        }
        float ndcX = (2.0f * touchX) / viewportW - 1.0f;
        float ndcY = 1.0f - (2.0f * touchY) / viewportH;

        float[] invVP = new float[16];
        if (!Matrix.invertM(invVP, 0, viewProjMatrix, 0)) {
            return new Ray3D(new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, -1f));
        }

        float[] nearIn = new float[]{ndcX, ndcY, -1.0f, 1.0f};
        float[] farIn = new float[]{ndcX, ndcY, 1.0f, 1.0f};
        float[] nearOut = new float[4];
        float[] farOut = new float[4];

        Matrix.multiplyMV(nearOut, 0, invVP, 0, nearIn, 0);
        Matrix.multiplyMV(farOut, 0, invVP, 0, farIn, 0);

        if (Math.abs(nearOut[3]) > 1e-6f) {
            nearOut[0] /= nearOut[3];
            nearOut[1] /= nearOut[3];
            nearOut[2] /= nearOut[3];
        }
        if (Math.abs(farOut[3]) > 1e-6f) {
            farOut[0] /= farOut[3];
            farOut[1] /= farOut[3];
            farOut[2] /= farOut[3];
        }

        Vec3 origin = new Vec3(nearOut[0], nearOut[1], nearOut[2]);
        Vec3 end = new Vec3(farOut[0], farOut[1], farOut[2]);
        Vec3 dir = end.sub(origin).normalize();
        return new Ray3D(origin, dir);
    }
}
