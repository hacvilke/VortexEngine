package com.vortex.engine.core.math;

/**
 * Pure-Java Column-Major 4x4 Matrix Math (OpenGL ES & Desktop JVM compatible, zero android.* imports).
 */
public class Mat4 {
    public final float[] m = new float[16];

    public Mat4() {
        identity();
    }

    public Mat4 identity() {
        setIdentity(m);
        return this;
    }

    public static void setIdentity(float[] out) {
        for (int i = 0; i < 16; i++) out[i] = 0f;
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f;
    }

    public static void buildModelMatrix(
            float[] out,
            float tx, float ty, float tz,
            float rx, float ry, float rz,
            float sx, float sy, float sz
    ) {
        setIdentity(out);
        translate(out, tx, ty, tz);
        if (ry != 0f) rotateAxis(out, ry, 0f, 1f, 0f);
        if (rx != 0f) rotateAxis(out, rx, 1f, 0f, 0f);
        if (rz != 0f) rotateAxis(out, rz, 0f, 0f, 1f);
        scale(out, sx, sy, sz);
    }

    public static void translate(float[] m, float x, float y, float z) {
        for (int i = 0; i < 4; i++) {
            m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z;
        }
    }

    public static void scale(float[] m, float sx, float sy, float sz) {
        for (int i = 0; i < 4; i++) {
            m[i] *= sx;
            m[4 + i] *= sy;
            m[8 + i] *= sz;
        }
    }

    public static void rotateAxis(float[] m, float deg, float ax, float ay, float az) {
        double rad = Math.toRadians(deg);
        float s = (float) Math.sin(rad);
        float c = (float) Math.cos(rad);
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6f) return;
        ax /= len; ay /= len; az /= len;
        float nc = 1.0f - c;

        float[] r = new float[16];
        r[0] = ax * ax * nc + c;      r[4] = ax * ay * nc - az * s; r[8]  = ax * az * nc + ay * s; r[12] = 0f;
        r[1] = ay * ax * nc + az * s; r[5] = ay * ay * nc + c;      r[9]  = ay * az * nc - ax * s; r[13] = 0f;
        r[2] = az * ax * nc - ay * s; r[6] = az * ay * nc + ax * s; r[10] = az * az * nc + c;      r[14] = 0f;
        r[3] = 0f;                    r[7] = 0f;                    r[11] = 0f;                    r[15] = 1f;

        float[] tmp = new float[16];
        multiplyMM(tmp, m, r);
        System.arraycopy(tmp, 0, m, 0, 16);
    }

    public static void multiplyMM(float[] out, float[] lhs, float[] rhs) {
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                out[c * 4 + r] =
                        lhs[0 * 4 + r] * rhs[c * 4 + 0] +
                        lhs[1 * 4 + r] * rhs[c * 4 + 1] +
                        lhs[2 * 4 + r] * rhs[c * 4 + 2] +
                        lhs[3 * 4 + r] * rhs[c * 4 + 3];
            }
        }
    }

    public static void multiplyMV(float[] out, float[] m, float[] v) {
        for (int r = 0; r < 4; r++) {
            out[r] = m[r] * v[0] + m[4 + r] * v[1] + m[8 + r] * v[2] + m[12 + r] * v[3];
        }
    }

    public static boolean invertM(float[] mInv, float[] m) {
        float[] inv = new float[16];
        inv[0] = m[5]  * m[10] * m[15] - m[5]  * m[11] * m[14] - m[9]  * m[6]  * m[15] + m[9]  * m[7]  * m[14] + m[13] * m[6]  * m[11] - m[13] * m[7]  * m[10];
        inv[4] = -m[4]  * m[10] * m[15] + m[4]  * m[11] * m[14] + m[8]  * m[6]  * m[15] - m[8]  * m[7]  * m[14] - m[12] * m[6]  * m[11] + m[12] * m[7]  * m[10];
        inv[8] = m[4]  * m[9] * m[15] - m[4]  * m[11] * m[13] - m[8]  * m[5] * m[15] + m[8]  * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4]  * m[9] * m[14] + m[4]  * m[10] * m[13] + m[8]  * m[5] * m[14] - m[8]  * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1]  * m[10] * m[15] + m[1]  * m[11] * m[14] + m[9]  * m[2] * m[15] - m[9]  * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0]  * m[10] * m[15] - m[0]  * m[11] * m[14] - m[8]  * m[2] * m[15] + m[8]  * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0]  * m[9] * m[15] + m[0]  * m[11] * m[13] + m[8]  * m[1] * m[15] - m[8]  * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0]  * m[9] * m[14] - m[0]  * m[10] * m[13] - m[8]  * m[1] * m[14] + m[8]  * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1]  * m[6] * m[15] - m[1]  * m[7] * m[14] - m[5]  * m[2] * m[15] + m[5]  * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0]  * m[6] * m[15] + m[0]  * m[7] * m[14] + m[4]  * m[2] * m[15] - m[4]  * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0]  * m[5] * m[15] - m[0]  * m[7] * m[13] - m[4]  * m[1] * m[15] + m[4]  * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0]  * m[5] * m[14] + m[0]  * m[6] * m[13] + m[4]  * m[1] * m[14] - m[4]  * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];

        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (Math.abs(det) < 1e-8f) return false;
        det = 1.0f / det;
        for (int i = 0; i < 16; i++) mInv[i] = inv[i] * det;
        return true;
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
        if (!invertM(invVP, viewProjMatrix)) {
            return new Ray3D(new Vec3(0f, 0f, 5f), new Vec3(0f, 0f, -1f));
        }

        float[] nearIn = new float[]{ndcX, ndcY, -1.0f, 1.0f};
        float[] farIn = new float[]{ndcX, ndcY, 1.0f, 1.0f};
        float[] nearOut = new float[4];
        float[] farOut = new float[4];

        multiplyMV(nearOut, invVP, nearIn);
        multiplyMV(farOut, invVP, farIn);

        if (Math.abs(nearOut[3]) > 1e-6f) {
            nearOut[0] /= nearOut[3]; nearOut[1] /= nearOut[3]; nearOut[2] /= nearOut[3];
        }
        if (Math.abs(farOut[3]) > 1e-6f) {
            farOut[0] /= farOut[3]; farOut[1] /= farOut[3]; farOut[2] /= farOut[3];
        }

        Vec3 origin = new Vec3(nearOut[0], nearOut[1], nearOut[2]);
        Vec3 end = new Vec3(farOut[0], farOut[1], farOut[2]);
        return new Ray3D(origin, end.sub(origin).normalize());
    }
}
