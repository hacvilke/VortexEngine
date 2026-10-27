package com.vortex.engine.core.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight Wavefront .obj parser supporting v, vt, vn, and triangulated/quad/polygon faces.
 */
public class ObjLoader {

    public static Mesh3D parse(String objSource) {
        if (objSource == null || objSource.trim().isEmpty()) {
            return Mesh3D.createCube();
        }
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<Float> out = new ArrayList<>();

        String[] lines = objSource.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            try {
                if ("v".equals(parts[0]) && parts.length >= 4) {
                    positions.add(new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    });
                } else if ("vt".equals(parts[0]) && parts.length >= 3) {
                    uvs.add(new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2])
                    });
                } else if ("vn".equals(parts[0]) && parts.length >= 4) {
                    normals.add(new float[]{
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])
                    });
                } else if ("f".equals(parts[0]) && parts.length >= 4) {
                    int count = parts.length - 1;
                    int[][] refs = new int[count][3];
                    for (int i = 0; i < count; i++) {
                        refs[i] = parseFaceToken(parts[i + 1], positions.size(), uvs.size(), normals.size());
                    }
                    // Fan triangulation for triangles, quads, and convex polygons
                    for (int i = 1; i < count - 1; i++) {
                        emitTriangle(out, positions, uvs, normals, refs[0], refs[i], refs[i + 1]);
                    }
                }
            } catch (Exception ignored) {
                // Skip malformed line gracefully
            }
        }

        if (out.size() < 24) {
            return Mesh3D.createCube();
        }
        float[] data = new float[out.size()];
        for (int i = 0; i < data.length; i++) data[i] = out.get(i);
        return new Mesh3D(data);
    }

    private static int[] parseFaceToken(String token, int vLen, int vtLen, int vnLen) {
        String[] segs = token.split("/");
        int vi = parseIdx(segs[0], vLen);
        int ti = segs.length > 1 && !segs[1].isEmpty() ? parseIdx(segs[1], vtLen) : -1;
        int ni = segs.length > 2 && !segs[2].isEmpty() ? parseIdx(segs[2], vnLen) : -1;
        return new int[]{vi, ti, ni};
    }

    private static int parseIdx(String s, int total) {
        int idx = Integer.parseInt(s);
        return idx < 0 ? total + idx : idx - 1;
    }

    private static void emitTriangle(
            List<Float> out,
            List<float[]> pos, List<float[]> uvs, List<float[]> norms,
            int[] a, int[] b, int[] c
    ) {
        if (a[0] < 0 || a[0] >= pos.size() || b[0] < 0 || b[0] >= pos.size() || c[0] < 0 || c[0] >= pos.size()) {
            return;
        }
        float[] p0 = pos.get(a[0]);
        float[] p1 = pos.get(b[0]);
        float[] p2 = pos.get(c[0]);

        float ux = p1[0] - p0[0], uy = p1[1] - p0[1], uz = p1[2] - p0[2];
        float vx = p2[0] - p0[0], vy = p2[1] - p0[1], vz = p2[2] - p0[2];
        float fnx = uy * vz - uz * vy;
        float fny = uz * vx - ux * vz;
        float fnz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(fnx * fnx + fny * fny + fnz * fnz);
        if (len > 1e-6f) {
            fnx /= len; fny /= len; fnz /= len;
        } else {
            fnx = 0f; fny = 1f; fnz = 0f;
        }

        int[][] tri = new int[][]{a, b, c};
        float[][] defaultUV = new float[][]{{0f, 0f}, {1f, 0f}, {0.5f, 1f}};
        for (int i = 0; i < 3; i++) {
            int[] ref = tri[i];
            float[] p = pos.get(ref[0]);
            float[] uv = (ref[1] >= 0 && ref[1] < uvs.size()) ? uvs.get(ref[1]) : defaultUV[i];
            float[] n = (ref[2] >= 0 && ref[2] < norms.size()) ? norms.get(ref[2]) : new float[]{fnx, fny, fnz};
            out.add(p[0]); out.add(p[1]); out.add(p[2]);
            out.add(n[0]); out.add(n[1]); out.add(n[2]);
            out.add(uv[0]); out.add(uv[1]);
        }
    }
}
