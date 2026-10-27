package com.vortex.engine.core.render;

import android.opengl.GLES20;
import android.util.Log;

public class ShaderProgram {
    private static final String TAG = "VortexShader";
    public final int programId;

    public static final String LIT_3D_VERT =
            "uniform mat4 uMVP;\n" +
            "uniform mat4 uModel;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec3 aNormal;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "    vec4 worldPos = uModel * vec4(aPosition, 1.0);\n" +
            "    vWorldPos = worldPos.xyz;\n" +
            "    vNormal = normalize(mat3(uModel[0].xyz, uModel[1].xyz, uModel[2].xyz) * aNormal);\n" +
            "    vUV = aUV;\n" +
            "    gl_Position = uMVP * vec4(aPosition, 1.0);\n" +
            "}\n";

    public static final String LIT_3D_FRAG =
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "uniform vec4 uSecondaryColor;\n" +
            "uniform vec3 uLightDir;\n" +
            "uniform vec3 uAmbient;\n" +
            "uniform vec3 uSkyColor;\n" +
            "uniform vec3 uCamPos;\n" +
            "uniform float uChecker;\n" +
            "uniform float uEmissive;\n" +
            "uniform vec3 uFogParams;\n" +
            "varying vec3 vWorldPos;\n" +
            "varying vec3 vNormal;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "    vec3 baseCol = uColor.rgb;\n" +
            "    if (uChecker > 0.5) {\n" +
            "        float cx = floor(vUV.x * 8.0);\n" +
            "        float cy = floor(vUV.y * 8.0);\n" +
            "        float m = mod(cx + cy, 2.0);\n" +
            "        baseCol = mix(uColor.rgb, uSecondaryColor.rgb, m * 0.55);\n" +
            "    }\n" +
            "    vec3 N = normalize(vNormal);\n" +
            "    vec3 L = normalize(uLightDir);\n" +
            "    float diff = max(dot(N, L), 0.0);\n" +
            "    float hemi = N.y * 0.5 + 0.5;\n" +
            "    vec3 hemiLight = mix(uAmbient * 0.65, uAmbient * 1.35, hemi);\n" +
            "    vec3 V = normalize(uCamPos - vWorldPos);\n" +
            "    vec3 H = normalize(L + V);\n" +
            "    float spec = pow(max(dot(N, H), 0.0), 28.0) * 0.35;\n" +
            "    vec3 lit = baseCol * (hemiLight + vec3(diff * 0.88)) + vec3(spec);\n" +
            "    if (uEmissive > 0.5) {\n" +
            "        lit = mix(lit, baseCol * 1.25, 0.75);\n" +
            "    }\n" +
            "    if (uFogParams.x > 0.5) {\n" +
            "        float dist = length(vWorldPos - uCamPos);\n" +
            "        float fogFactor = clamp((dist - uFogParams.y) / max(1.0, uFogParams.z - uFogParams.y), 0.0, 1.0);\n" +
            "        lit = mix(lit, uSkyColor, fogFactor);\n" +
            "    }\n" +
            "    gl_FragColor = vec4(lit, uColor.a);\n" +
            "}\n";

    public static final String FLAT_VERT =
            "uniform mat4 uMVP;\n" +
            "uniform float uPointSize;\n" +
            "attribute vec3 aPosition;\n" +
            "attribute vec4 aColor;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    vColor = aColor;\n" +
            "    gl_PointSize = uPointSize;\n" +
            "    gl_Position = uMVP * vec4(aPosition, 1.0);\n" +
            "}\n";

    public static final String FLAT_FRAG =
            "precision mediump float;\n" +
            "varying vec4 vColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = vColor;\n" +
            "}\n";

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        programId = GLES20.glCreateProgram();
        GLES20.glAttachShader(programId, vs);
        GLES20.glAttachShader(programId, fs);
        GLES20.glLinkProgram(programId);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            String err = GLES20.glGetProgramInfoLog(programId);
            Log.e(TAG, "Program link error: " + err);
        }
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] status = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            Log.e(TAG, "Shader compile error: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }

    public void use() {
        GLES20.glUseProgram(programId);
    }

    public int attr(String name) {
        return GLES20.glGetAttribLocation(programId, name);
    }

    public int uniform(String name) {
        return GLES20.glGetUniformLocation(programId, name);
    }
}
