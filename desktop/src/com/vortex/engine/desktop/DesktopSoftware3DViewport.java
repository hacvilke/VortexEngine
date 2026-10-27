package com.vortex.engine.desktop;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import com.vortex.engine.core.noise.TerrainGenerator;
import com.vortex.engine.core.physics.PhysicsWorld;
import com.vortex.engine.core.script.VortexScriptEngine;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Cross-Platform 60 FPS 2D & 3D Game Viewport for Windows .exe & Linux Desktop.
 * Features perspective 3D sunlit triangle rasterization (including FastNoiseLite 3D Terrain
 * & Wavefront .OBJ meshes), JBox2D tumbling 2D rigid-body rendering, 16x16 pixel-art sprites,
 * particle bursts, mouse orbit/picking, and WASD/Arrow + Space/J/K keyboard controls!
 */
public class DesktopSoftware3DViewport extends JPanel {

    public interface ViewportListener {
        void onEntitySelected(Entity e);
        void onEntityMoved(Entity e);
        void onLog(String msg);
        void onTelemetry(int fps, int score, int health, String banner);
    }

    private static class Tri3D implements Comparable<Tri3D> {
        float sx0, sy0, sx1, sy1, sx2, sy2;
        float depth;
        Color fill;
        Color edge;

        @Override
        public int compareTo(Tri3D o) {
            return Float.compare(o.depth, this.depth); // Painter back-to-front
        }
    }

    private static class Particle {
        float x, y, z, vx, vy, vz, life, maxLife;
        Color color;
        boolean is3D;
    }

    public final PhysicsWorld physicsWorld = new PhysicsWorld();
    public final VortexScriptEngine scriptEngine = new VortexScriptEngine();
    public final DesktopAudioEngine audioEngine = new DesktopAudioEngine();

    private GameProject activeProject;
    private boolean playing = false;
    private String selectedEntityId = null;
    private ViewportListener listener;

    // 3D Orbit & Play Camera
    public float editorYawDeg = 35f;
    public float editorPitchDeg = 26f;
    public float editorDist = 19f;
    public float editor2DCamX = 0f, editor2DCamY = 1.5f, editor2DZoom = 18f;

    private float playCamX = 0f, playCamY = 6f, playCamZ = 12f;
    private float playLookX = 0f, playLookY = 1f, playLookZ = 0f;
    private float shakeIntensity = 0f, shakeTimer = 0f;
    private final Random rng = new Random();

    private final List<Particle> particles = new ArrayList<>();
    private final List<Tri3D> triBuffer = new ArrayList<>(4096);
    private final Map<String, BufferedImage> spriteCache = new HashMap<>();
    private float[] cachedTerrainVerts = null;
    private int cachedTerrainSeed = Integer.MIN_VALUE;

    private final Set<Integer> heldKeys = new HashSet<>();
    private Point lastMouse = null;
    private boolean draggingEntity = false;

    private long lastTickNs = System.nanoTime();
    private int frameCount = 0;
    private long fpsWindowNs = System.nanoTime();
    private int currentFps = 60;
    private final Timer frameTimer;

    public DesktopSoftware3DViewport() {
        setBackground(new Color(9, 13, 22));
        setFocusable(true);
        setupInputs();

        frameTimer = new Timer(16, e -> tickAndRepaint());
        frameTimer.start();
    }

    public void setViewportListener(ViewportListener l) {
        this.listener = l;
    }

    public void invalidateAssetCaches() {
        spriteCache.clear();
        cachedTerrainVerts = null;
    }

    public void loadProject(final GameProject project, boolean startPlay) {
        this.activeProject = project;
        this.playing = startPlay;
        this.particles.clear();
        this.heldKeys.clear();
        this.spriteCache.clear();
        this.cachedTerrainVerts = null;
        this.physicsWorld.reset();
        this.lastTickNs = System.nanoTime();

        scriptEngine.attach(project, physicsWorld, new VortexScriptEngine.HostBridge() {
            @Override
            public void onLog(String message) {
                if (listener != null) listener.onLog(message);
            }

            @Override
            public void onCameraShake(float intensity, float duration) {
                shakeIntensity = intensity;
                shakeTimer = duration;
            }

            @Override
            public void onEmitParticles(float x, float y, float z, int count, String colorHex) {
                emitParticles(x, y, z, count, colorHex, project != null && "3D".equalsIgnoreCase(project.dimensionMode));
            }

            @Override
            public void onPlaySound(String soundName) {
                audioEngine.play(soundName);
            }

            @Override
            public void onPlayMusic(String pattern, int bpm) {
                audioEngine.startMusicLoop(pattern, bpm);
            }

            @Override
            public void onStopMusic() {
                audioEngine.stopMusicLoop();
            }
        });

        if (!startPlay) {
            audioEngine.stopMusicLoop();
        }

        if (project != null) {
            editor2DZoom = project.zoom2D;
            Entity target = project.findByName(project.cameraTargetName);
            if (target != null) {
                playLookX = target.x; playLookY = target.y; playLookZ = target.z;
                playCamX = target.x; playCamY = target.y + project.cameraHeight; playCamZ = target.z + project.cameraDistance;
            }
            if (startPlay) {
                scriptEngine.compileAndInit(project.scriptCode);
                requestFocusInWindow();
            }
        }
        repaint();
    }

    public boolean isPlaying() { return playing; }
    public GameProject getActiveProject() { return activeProject; }
    public String getSelectedEntityId() { return selectedEntityId; }
    public void setSelectedEntityId(String id) { this.selectedEntityId = id; repaint(); }

    public void resetCamera() {
        editorYawDeg = 35f; editorPitchDeg = 26f; editorDist = 19f;
        editor2DCamX = 0f; editor2DCamY = 1.5f;
        if (activeProject != null) editor2DZoom = activeProject.zoom2D;
        repaint();
    }

    private void setupInputs() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                lastMouse = e.getPoint();
                if (playing) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        scriptEngine.inputBtnA = true;
                        scriptEngine.notifyButton("A");
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        scriptEngine.inputBtnB = true;
                        scriptEngine.notifyButton("B");
                    }
                    return;
                }
                if (SwingUtilities.isLeftMouseButton(e) && activeProject != null) {
                    Entity hit = pickEntity(e.getX(), e.getY());
                    if (hit != null) {
                        selectedEntityId = hit.id;
                        draggingEntity = true;
                        if (listener != null) listener.onEntitySelected(hit);
                        repaint();
                    } else {
                        draggingEntity = false;
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                draggingEntity = false;
                if (playing) {
                    if (SwingUtilities.isLeftMouseButton(e)) scriptEngine.inputBtnA = false;
                    if (SwingUtilities.isRightMouseButton(e)) scriptEngine.inputBtnB = false;
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMouse == null || activeProject == null || playing) return;
                int dx = e.getX() - lastMouse.x;
                int dy = e.getY() - lastMouse.y;
                lastMouse = e.getPoint();

                boolean is3D = "3D".equalsIgnoreCase(activeProject.dimensionMode);
                if (draggingEntity && selectedEntityId != null && SwingUtilities.isLeftMouseButton(e)) {
                    Entity sel = activeProject.findById(selectedEntityId);
                    if (sel != null) {
                        if (is3D) {
                            double yaw = Math.toRadians(editorYawDeg);
                            float scale = editorDist * 0.0022f;
                            sel.x += (float) (Math.cos(yaw) * dx) * scale;
                            sel.z += (float) (-Math.sin(yaw) * dx) * scale;
                            if (e.isShiftDown()) {
                                sel.y -= dy * scale;
                            } else {
                                sel.x += (float) (Math.sin(yaw) * dy) * scale;
                                sel.z += (float) (Math.cos(yaw) * dy) * scale;
                            }
                        } else {
                            float worldPerPx = editor2DZoom / Math.max(1f, getHeight());
                            sel.x += dx * worldPerPx;
                            sel.y -= dy * worldPerPx;
                        }
                        if (listener != null) listener.onEntityMoved(sel);
                    }
                } else {
                    if (is3D) {
                        editorYawDeg -= dx * 0.45f;
                        editorPitchDeg = Math.max(-78f, Math.min(78f, editorPitchDeg + dy * 0.35f));
                    } else {
                        float worldPerPx = editor2DZoom / Math.max(1f, getHeight());
                        editor2DCamX -= dx * worldPerPx;
                        editor2DCamY += dy * worldPerPx;
                    }
                }
                repaint();
            }
        });

        addMouseWheelListener((MouseWheelEvent e) -> {
            if (activeProject == null || playing) return;
            float rot = (float) e.getPreciseWheelRotation();
            if ("3D".equalsIgnoreCase(activeProject.dimensionMode)) {
                editorDist = Math.max(4f, Math.min(85f, editorDist * (1f + rot * 0.1f)));
            } else {
                editor2DZoom = Math.max(4f, Math.min(65f, editor2DZoom * (1f + rot * 0.1f)));
            }
            repaint();
        });

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                boolean fresh = heldKeys.add(code);
                syncKeyboardAxes();
                if (playing && fresh) {
                    if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_J || code == KeyEvent.VK_Z) {
                        scriptEngine.inputBtnA = true;
                        scriptEngine.notifyButton("A");
                    } else if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_K || code == KeyEvent.VK_X) {
                        scriptEngine.inputBtnB = true;
                        scriptEngine.notifyButton("B");
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                int code = e.getKeyCode();
                heldKeys.remove(code);
                syncKeyboardAxes();
                if (code == KeyEvent.VK_SPACE || code == KeyEvent.VK_J || code == KeyEvent.VK_Z) {
                    scriptEngine.inputBtnA = false;
                } else if (code == KeyEvent.VK_SHIFT || code == KeyEvent.VK_K || code == KeyEvent.VK_X) {
                    scriptEngine.inputBtnB = false;
                }
            }
        });
    }

    private void syncKeyboardAxes() {
        float jx = 0f, jy = 0f;
        if (heldKeys.contains(KeyEvent.VK_A) || heldKeys.contains(KeyEvent.VK_LEFT)) jx -= 1f;
        if (heldKeys.contains(KeyEvent.VK_D) || heldKeys.contains(KeyEvent.VK_RIGHT)) jx += 1f;
        if (heldKeys.contains(KeyEvent.VK_W) || heldKeys.contains(KeyEvent.VK_UP)) jy += 1f;
        if (heldKeys.contains(KeyEvent.VK_S) || heldKeys.contains(KeyEvent.VK_DOWN)) jy -= 1f;
        scriptEngine.inputJoyX = jx;
        scriptEngine.inputJoyY = jy;
    }

    private void tickAndRepaint() {
        long now = System.nanoTime();
        float dt = (now - lastTickNs) / 1_000_000_000f;
        lastTickNs = now;
        if (dt < 0f || dt > 0.08f) dt = 0.016f;

        frameCount++;
        if (now - fpsWindowNs >= 500_000_000L) {
            currentFps = Math.round(frameCount * 1_000_000_000f / Math.max(1L, now - fpsWindowNs));
            frameCount = 0;
            fpsWindowNs = now;
            if (listener != null && activeProject != null) {
                listener.onTelemetry(currentFps, scriptEngine.score, scriptEngine.health, scriptEngine.hudBanner);
            }
        }

        if (playing && activeProject != null) {
            syncKeyboardAxes();
            scriptEngine.update(dt);
            List<PhysicsWorld.CollisionEvent> cols = physicsWorld.step(activeProject, dt);
            for (PhysicsWorld.CollisionEvent ce : cols) {
                scriptEngine.notifyCollision(ce.a, ce.b);
            }
            updateParticles(dt, activeProject.gravityY);
            repaint();
        }
    }

    private void emitParticles(float x, float y, float z, int count, String hex, boolean is3D) {
        Color col = toAwtColor(hex, 255);
        for (int i = 0; i < Math.min(count, 45); i++) {
            Particle p = new Particle();
            p.x = x; p.y = y; p.z = z;
            p.vx = (rng.nextFloat() - 0.5f) * 11f;
            p.vy = rng.nextFloat() * 8f + 1.5f;
            p.vz = is3D ? (rng.nextFloat() - 0.5f) * 11f : 0f;
            p.maxLife = p.life = 0.35f + rng.nextFloat() * 0.45f;
            p.color = col;
            p.is3D = is3D;
            particles.add(p);
        }
    }

    private void updateParticles(float dt, float gravY) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0f) { particles.remove(i); continue; }
            p.vy += gravY * 0.35f * dt;
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        GameProject p = activeProject;
        if (p == null) {
            g2.dispose();
            return;
        }

        g2.setColor(toAwtColor(p.skyColorHex, 255));
        g2.fillRect(0, 0, w, h);

        if ("3D".equalsIgnoreCase(p.dimensionMode)) {
            paint3DScene(g2, p, w, h);
        } else {
            paint2DScene(g2, p, w, h);
        }

        paintHudOverlay(g2, p, w, h);
        g2.dispose();
    }

    // =========================================================================
    // 3D Perspective Camera + Sunlit Triangle Painter
    // =========================================================================
    private void paint3DScene(Graphics2D g2, GameProject p, int w, int h) {
        float camX, camY, camZ, lookX, lookY, lookZ;
        if (!playing) {
            double yaw = Math.toRadians(editorYawDeg);
            double pitch = Math.toRadians(editorPitchDeg);
            float horiz = (float) (Math.cos(pitch) * editorDist);
            camX = (float) (Math.sin(yaw) * horiz);
            camY = 0.5f + (float) (Math.sin(pitch) * editorDist);
            camZ = (float) (Math.cos(yaw) * horiz);
            lookX = 0f; lookY = 0.5f; lookZ = 0f;
        } else {
            Entity target = p.findByName(p.cameraTargetName);
            if (target != null && !"FIXED".equalsIgnoreCase(p.cameraMode)) {
                playLookX += (target.x - playLookX) * 0.14f;
                playLookY += ((target.y + 0.6f) - playLookY) * 0.14f;
                playLookZ += (target.z - playLookZ) * 0.14f;
                playCamX += (target.x - playCamX) * 0.14f;
                playCamY += ((target.y + p.cameraHeight) - playCamY) * 0.14f;
                playCamZ += ((target.z + p.cameraDistance) - playCamZ) * 0.14f;
                camX = playCamX; camY = playCamY; camZ = playCamZ;
                lookX = playLookX; lookY = playLookY; lookZ = playLookZ;
            } else {
                camX = 0f; camY = p.cameraHeight + 3f; camZ = p.cameraDistance + 4f;
                lookX = 0f; lookY = 0f; lookZ = 0f;
            }
            if (shakeTimer > 0f) {
                shakeTimer -= 0.016f;
                camX += (rng.nextFloat() - 0.5f) * 2f * shakeIntensity;
                camY += (rng.nextFloat() - 0.5f) * 2f * shakeIntensity;
            }
        }

        // Camera basis vectors
        float fx = lookX - camX, fy = lookY - camY, fz = lookZ - camZ;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-4f) fl = 1f;
        fx /= fl; fy /= fl; fz /= fl;

        // right = forward x (0,1,0)
        float rx = -fz, ry = 0f, rz = fx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) { rx = 1f; rz = 0f; rl = 1f; }
        rx /= rl; rz /= rl;

        // up = right x forward
        float ux = ry * fz - rz * fy;
        float uy = rz * fx - rx * fz;
        float uz = rx * fy - ry * fx;

        float fovScale = (float) (1.0 / Math.tan(Math.toRadians(Math.max(35f, p.cameraFov) * 0.5)));
        float halfH = h * 0.5f;
        float halfW = w * 0.5f;

        if (!playing || p.showGrid) {
            g2.setStroke(new BasicStroke(1f));
            for (int i = -20; i <= 20; i += 2) {
                g2.setColor(i == 0 ? new Color(0, 190, 230, 120) : new Color(55, 75, 105, 75));
                draw3DLine(g2, i, 0f, -20, i, 0f, 20, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
                draw3DLine(g2, -20, 0f, i, 20, 0f, i, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
            }
        }

        triBuffer.clear();
        Color skyCol = toAwtColor(p.skyColorHex, 255);

        for (Entity e : p.entities) {
            if (!e.alive || !e.visible) continue;
            appendEntity3DTriangles(p, e, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH, skyCol);
        }

        Collections.sort(triBuffer);
        Path2D poly = new Path2D.Float();
        g2.setStroke(new BasicStroke(1f));
        for (int i = 0; i < triBuffer.size(); i++) {
            Tri3D t = triBuffer.get(i);
            poly.reset();
            poly.moveTo(t.sx0, t.sy0);
            poly.lineTo(t.sx1, t.sy1);
            poly.lineTo(t.sx2, t.sy2);
            poly.closePath();
            g2.setColor(t.fill);
            g2.fill(poly);
            if (t.edge != null) {
                g2.setColor(t.edge);
                g2.draw(poly);
            }
        }

        // Selection Gizmo in Edit Mode
        if (!playing && selectedEntityId != null) {
            Entity sel = p.findById(selectedEntityId);
            if (sel != null) {
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(new Color(255, 80, 90));
                draw3DLine(g2, sel.x, sel.y, sel.z, sel.x + 2.2f, sel.y, sel.z, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
                g2.setColor(new Color(50, 240, 120));
                draw3DLine(g2, sel.x, sel.y, sel.z, sel.x, sel.y + 2.2f, sel.z, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
                g2.setColor(new Color(70, 160, 255));
                draw3DLine(g2, sel.x, sel.y, sel.z, sel.x, sel.y, sel.z + 2.2f, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
            }
        }

        // 3D Particles
        for (Particle pt : particles) {
            float[] pr = project3D(pt.x, pt.y, pt.z, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
            if (pr != null) {
                int sz = Math.max(3, Math.round(90f / pr[2]));
                g2.setColor(pt.color);
                g2.fillOval(Math.round(pr[0]) - sz / 2, Math.round(pr[1]) - sz / 2, sz, sz);
            }
        }
    }

    private void appendEntity3DTriangles(
            GameProject p, Entity e,
            float camX, float camY, float camZ,
            float rx, float ry, float rz, float ux, float uy, float uz, float fx, float fy, float fz,
            float fovScale, float halfW, float halfH, Color skyCol
    ) {
        String prim = e.primitive == null ? "CUBE" : e.primitive.toUpperCase();
        Color baseCol = toAwtColor(e.colorHex, Math.round(e.alpha * 255));
        Color secCol = toAwtColor(e.secondaryColorHex, Math.round(e.alpha * 255));

        if ("TERRAIN".equals(prim)) {
            if (cachedTerrainVerts == null || cachedTerrainSeed != p.terrainSeed) {
                cachedTerrainSeed = p.terrainSeed;
                cachedTerrainVerts = TerrainGenerator.buildInterleavedTerrainVertices(p.terrainSeed, p.terrainFreq, 24);
            }
            int triCount = cachedTerrainVerts.length / 24; // 3 verts * 8 floats
            for (int t = 0; t < triCount; t++) {
                int b = t * 24;
                float x0 = e.x + cachedTerrainVerts[b] * e.scaleX;
                float y0 = e.y + cachedTerrainVerts[b + 1] * e.scaleY;
                float z0 = e.z + cachedTerrainVerts[b + 2] * e.scaleZ;
                float nx = cachedTerrainVerts[b + 3], ny = cachedTerrainVerts[b + 4], nz = cachedTerrainVerts[b + 5];

                float x1 = e.x + cachedTerrainVerts[b + 8] * e.scaleX;
                float y1 = e.y + cachedTerrainVerts[b + 9] * e.scaleY;
                float z1 = e.z + cachedTerrainVerts[b + 10] * e.scaleZ;

                float x2 = e.x + cachedTerrainVerts[b + 16] * e.scaleX;
                float y2 = e.y + cachedTerrainVerts[b + 17] * e.scaleY;
                float z2 = e.z + cachedTerrainVerts[b + 18] * e.scaleZ;

                Color c = (e.checkerTexture && ((t / 2) % 2 == 0)) ? baseCol : (e.checkerTexture ? secCol : baseCol);
                pushProjectedTri(x0, y0, z0, x1, y1, z1, x2, y2, z2, nx, ny, nz, c, false, null,
                        camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH, skyCol, p.fogNear, p.fogFar);
            }
            return;
        }

        // Build local unit triangles for Cube / Pyramid / Sphere-Octahedron
        float[][] tris = getPrimitiveUnitTris(prim);
        double radY = Math.toRadians(e.rotY);
        double radZ = Math.toRadians(e.rotZ);
        float cy = (float) Math.cos(radY), sy = (float) Math.sin(radY);
        float cz = (float) Math.cos(radZ), sz = (float) Math.sin(radZ);
        boolean isSel = !playing && e.id.equals(selectedEntityId);
        Color selEdge = isSel ? new Color(0, 229, 255, 220) : new Color(0, 0, 0, 35);

        for (int i = 0; i < tris.length; i++) {
            float[] t = tris[i];
            float[] p0 = xform(t[0], t[1], t[2], e, cy, sy, cz, sz);
            float[] p1 = xform(t[3], t[4], t[5], e, cy, sy, cz, sz);
            float[] p2 = xform(t[6], t[7], t[8], e, cy, sy, cz, sz);

            float ux1 = p1[0] - p0[0], uy1 = p1[1] - p0[1], uz1 = p1[2] - p0[2];
            float vx1 = p2[0] - p0[0], vy1 = p2[1] - p0[1], vz1 = p2[2] - p0[2];
            float nx = uy1 * vz1 - uz1 * vy1;
            float ny = uz1 * vx1 - ux1 * vz1;
            float nz = ux1 * vy1 - uy1 * vx1;
            float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1e-5f) { nx /= nl; ny /= nl; nz /= nl; }

            Color c = (e.checkerTexture && (i % 2 == 1)) ? secCol : baseCol;
            pushProjectedTri(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                    nx, ny, nz, c, e.emissive, selEdge,
                    camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH, skyCol, p.fogNear, p.fogFar);
        }
    }

    private float[] xform(float lx, float ly, float lz, Entity e, float cy, float sy, float cz, float sz) {
        float x = lx * e.scaleX, y = ly * e.scaleY, z = lz * e.scaleZ;
        // RotZ
        float xz = x * cz - y * sz;
        float yz = x * sz + y * cz;
        // RotY
        float xy = xz * cy + z * sy;
        float zy = -xz * sy + z * cy;
        return new float[]{e.x + xy, e.y + yz, e.z + zy};
    }

    private void pushProjectedTri(
            float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
            float nx, float ny, float nz, Color col, boolean emissive, Color edge,
            float camX, float camY, float camZ,
            float rx, float ry, float rz, float ux, float uy, float uz, float fx, float fy, float fz,
            float fovScale, float halfW, float halfH, Color skyCol, float fogNear, float fogFar
    ) {
        float[] s0 = project3D(x0, y0, z0, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
        float[] s1 = project3D(x1, y1, z1, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
        float[] s2 = project3D(x2, y2, z2, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
        if (s0 == null || s1 == null || s2 == null) return;

        float depth = (s0[2] + s1[2] + s2[2]) / 3f;
        if (depth > 140f) return;

        float light = Math.max(0f, nx * 0.45f + ny * 0.80f + nz * 0.40f);
        float shade = emissive ? (0.82f + light * 0.30f) : (0.38f + light * 0.65f);

        int r = Math.min(255, Math.round(col.getRed() * shade));
        int g = Math.min(255, Math.round(col.getGreen() * shade));
        int b = Math.min(255, Math.round(col.getBlue() * shade));

        if (fogFar > fogNear && depth > fogNear) {
            float ft = Math.min(0.85f, (depth - fogNear) / (fogFar - fogNear));
            r = Math.round(r * (1f - ft) + skyCol.getRed() * ft);
            g = Math.round(g * (1f - ft) + skyCol.getGreen() * ft);
            b = Math.round(b * (1f - ft) + skyCol.getBlue() * ft);
        }

        Tri3D tri = new Tri3D();
        tri.sx0 = s0[0]; tri.sy0 = s0[1];
        tri.sx1 = s1[0]; tri.sy1 = s1[1];
        tri.sx2 = s2[0]; tri.sy2 = s2[1];
        tri.depth = depth;
        tri.fill = new Color(r, g, b, col.getAlpha());
        tri.edge = edge;
        triBuffer.add(tri);
    }

    private float[] project3D(
            float wx, float wy, float wz,
            float camX, float camY, float camZ,
            float rx, float ry, float rz, float ux, float uy, float uz, float fx, float fy, float fz,
            float fovScale, float halfW, float halfH
    ) {
        float dx = wx - camX, dy = wy - camY, dz = wz - camZ;
        float cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.25f) return null;
        float cx = dx * rx + dy * ry + dz * rz;
        float cy = dx * ux + dy * uy + dz * uz;
        float sx = halfW + (cx / cz) * fovScale * halfH;
        float sy = halfH - (cy / cz) * fovScale * halfH;
        return new float[]{sx, sy, cz};
    }

    private void draw3DLine(
            Graphics2D g2, float x0, float y0, float z0, float x1, float y1, float z1,
            float camX, float camY, float camZ,
            float rx, float ry, float rz, float ux, float uy, float uz, float fx, float fy, float fz,
            float fovScale, float halfW, float halfH
    ) {
        float[] a = project3D(x0, y0, z0, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
        float[] b = project3D(x1, y1, z1, camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
        if (a != null && b != null) {
            g2.drawLine(Math.round(a[0]), Math.round(a[1]), Math.round(b[0]), Math.round(b[1]));
        }
    }

    private static float[][] CUBE_TRIS;
    private static float[][] PYRAMID_TRIS;
    private static float[][] SPHERE_TRIS;

    private static float[][] getPrimitiveUnitTris(String prim) {
        if ("PYRAMID".equals(prim) || "CUSTOM_OBJ".equals(prim)) {
            if (PYRAMID_TRIS == null) {
                PYRAMID_TRIS = new float[][]{
                        {0, 0.5f, 0, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f},
                        {0, 0.5f, 0, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f},
                        {0, 0.5f, 0, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f},
                        {0, 0.5f, 0, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f}
                };
            }
            return PYRAMID_TRIS;
        }
        if ("SPHERE".equals(prim) || "CYLINDER".equals(prim)) {
            if (SPHERE_TRIS == null) {
                List<float[]> list = new ArrayList<>();
                int stacks = 6, slices = 10;
                for (int i = 0; i < stacks; i++) {
                    double p0 = Math.PI * (-0.5 + (double) i / stacks);
                    double p1 = Math.PI * (-0.5 + (double) (i + 1) / stacks);
                    float y0 = (float) Math.sin(p0) * 0.5f, r0 = (float) Math.cos(p0) * 0.5f;
                    float y1 = (float) Math.sin(p1) * 0.5f, r1 = (float) Math.cos(p1) * 0.5f;
                    for (int j = 0; j < slices; j++) {
                        double t0 = 2 * Math.PI * j / slices, t1 = 2 * Math.PI * (j + 1) / slices;
                        float x00 = (float) Math.cos(t0) * r0, z00 = (float) Math.sin(t0) * r0;
                        float x10 = (float) Math.cos(t1) * r0, z10 = (float) Math.sin(t1) * r0;
                        float x01 = (float) Math.cos(t0) * r1, z01 = (float) Math.sin(t0) * r1;
                        float x11 = (float) Math.cos(t1) * r1, z11 = (float) Math.sin(t1) * r1;
                        list.add(new float[]{x00, y0, z00, x10, y0, z10, x11, y1, z11});
                        list.add(new float[]{x00, y0, z00, x11, y1, z11, x01, y1, z01});
                    }
                }
                SPHERE_TRIS = list.toArray(new float[0][]);
            }
            return SPHERE_TRIS;
        }
        if (CUBE_TRIS == null) {
            CUBE_TRIS = new float[][]{
                    {-0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f}, {-0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f},
                    {0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f}, {0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f},
                    {-0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f}, {-0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f},
                    {-0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f}, {-0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f},
                    {0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f}, {0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f},
                    {-0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f}, {-0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f}
            };
        }
        return CUBE_TRIS;
    }

    // =========================================================================
    // 2D Orthographic Scene + JBox2D Rotation + 16x16 Pixel Art Sprites
    // =========================================================================
    private void paint2DScene(Graphics2D g2, GameProject p, int w, int h) {
        float zoom = playing ? p.zoom2D : editor2DZoom;
        float ppu = h / Math.max(4f, zoom);
        float cx = editor2DCamX, cy = editor2DCamY;

        if (playing) {
            Entity target = p.findByName(p.cameraTargetName);
            if (target != null && !"FIXED".equalsIgnoreCase(p.cameraMode)) {
                playCamX += (target.x - playCamX) * 0.14f;
                playCamY += (target.y - playCamY) * 0.14f;
                cx = playCamX; cy = playCamY;
            } else { cx = 0f; cy = 0f; }
            if (shakeTimer > 0f) {
                shakeTimer -= 0.016f;
                cx += (rng.nextFloat() - 0.5f) * 2f * shakeIntensity;
                cy += (rng.nextFloat() - 0.5f) * 2f * shakeIntensity;
            }
        }

        if (!playing || p.showGrid) {
            g2.setStroke(new BasicStroke(1f));
            float halfW = (w * 0.5f) / ppu, halfH = (h * 0.5f) / ppu;
            for (int gx = (int) (cx - halfW) - 1; gx <= (int) (cx + halfW) + 1; gx += 2) {
                int sx = Math.round(w * 0.5f + (gx - cx) * ppu);
                g2.setColor(gx == 0 ? new Color(0, 200, 240, 110) : new Color(50, 70, 95, 70));
                g2.drawLine(sx, 0, sx, h);
            }
            for (int gy = (int) (cy - halfH) - 1; gy <= (int) (cy + halfH) + 1; gy += 2) {
                int sy = Math.round(h * 0.5f - (gy - cy) * ppu);
                g2.setColor(gy == 0 ? new Color(200, 90, 240, 110) : new Color(50, 70, 95, 70));
                g2.drawLine(0, sy, w, sy);
            }
        }

        for (Entity e : p.entities) {
            if (!e.alive || !e.visible) continue;
            float sx = w * 0.5f + (e.x - cx) * ppu;
            float sy = h * 0.5f - (e.y - cy) * ppu;
            int pw = Math.max(4, Math.round(Math.abs(e.scaleX) * ppu));
            int ph = Math.max(4, Math.round(Math.abs(e.scaleY) * ppu));

            AffineTransform old = g2.getTransform();
            g2.translate(sx, sy);
            g2.rotate(-Math.toRadians(e.rotZ));

            String prim = e.primitive == null ? "RECT" : e.primitive.toUpperCase();
            Color col = toAwtColor(e.colorHex, Math.round(e.alpha * 255));

            String activeSp = e.getActiveSpriteName();
            if ("CUSTOM_SPRITE".equals(prim) || !activeSp.isEmpty()) {
                String key = !activeSp.isEmpty() ? activeSp : "default";
                BufferedImage img = spriteCache.get(key);
                if (img == null) {
                    String enc = p.customSprites.get(key);
                    if (enc == null && !p.customSprites.isEmpty()) enc = p.customSprites.values().iterator().next();
                    int[] px = PixelSpriteCodec.decodeToArgbPixels(enc);
                    img = new BufferedImage(PixelSpriteCodec.SIZE, PixelSpriteCodec.SIZE, BufferedImage.TYPE_INT_ARGB);
                    img.setRGB(0, 0, PixelSpriteCodec.SIZE, PixelSpriteCodec.SIZE, px, 0, PixelSpriteCodec.SIZE);
                    spriteCache.put(key, img);
                }
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(img, -pw / 2, -ph / 2, pw, ph, null);
            } else if ("CIRCLE".equals(prim) || "SPRITE_COIN".equals(prim)) {
                g2.setColor(col);
                g2.fillOval(-pw / 2, -ph / 2, pw, ph);
                if ("SPRITE_COIN".equals(prim)) {
                    g2.setColor(new Color(255, 250, 200));
                    g2.fillRect(-pw / 10, -ph / 4, Math.max(2, pw / 5), ph / 2);
                }
            } else if ("TRIANGLE".equals(prim) || "SPRITE_SHIP".equals(prim)) {
                int[] xs = {0, -pw / 2, pw / 2};
                int[] ys = {-ph / 2, ph / 2, ph / 2};
                g2.setColor(col);
                g2.fillPolygon(xs, ys, 3);
            } else {
                g2.setColor(col);
                g2.fillRoundRect(-pw / 2, -ph / 2, pw, ph, 6, 6);
                if ("SPRITE_HERO".equals(prim) || "SPRITE_SLIME".equals(prim)) {
                    g2.setColor("SPRITE_HERO".equals(prim) ? Color.WHITE : new Color(255, 50, 50));
                    g2.fillRect(-pw / 3, -ph / 4, Math.max(3, pw / 5), Math.max(3, ph / 5));
                    g2.fillRect(pw / 8, -ph / 4, Math.max(3, pw / 5), Math.max(3, ph / 5));
                }
            }

            if (!playing && e.id.equals(selectedEntityId)) {
                g2.setColor(new Color(0, 229, 255));
                g2.setStroke(new BasicStroke(2.2f));
                g2.drawRect(-pw / 2 - 3, -ph / 2 - 3, pw + 6, ph + 6);
            }
            g2.setTransform(old);
        }

        for (Particle pt : particles) {
            int px = Math.round(w * 0.5f + (pt.x - cx) * ppu);
            int py = Math.round(h * 0.5f - (pt.y - cy) * ppu);
            g2.setColor(pt.color);
            g2.fillOval(px - 4, py - 4, 8, 8);
        }
    }

    private void paintHudOverlay(Graphics2D g2, GameProject p, int w, int h) {
        g2.setColor(new Color(11, 15, 23, 185));
        g2.fill(new RoundRectangle2D.Float(14, 12, 410, 38, 12, 12));

        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        if (playing) {
            g2.setColor(new Color(0, 229, 255));
            g2.drawString("SCORE " + scriptEngine.score, 26, 36);
            g2.setColor(scriptEngine.health > 30 ? new Color(0, 230, 118) : new Color(255, 82, 82));
            g2.drawString("HP " + scriptEngine.health, 145, 36);
            g2.setColor(new Color(180, 200, 225));
            g2.drawString(currentFps + " FPS • WASD + Space(A) / Shift(B)", 225, 36);

            if (scriptEngine.hudBanner != null && !scriptEngine.hudBanner.isEmpty()) {
                g2.setFont(new Font("SansSerif", Font.BOLD, 18));
                FontMetrics fm = g2.getFontMetrics();
                int bw = fm.stringWidth(scriptEngine.hudBanner);
                g2.setColor(new Color(11, 15, 23, 180));
                g2.fillRoundRect((w - bw) / 2 - 14, 58, bw + 28, 32, 10, 10);
                g2.setColor(new Color(255, 213, 79));
                g2.drawString(scriptEngine.hudBanner, (w - bw) / 2, 80);
            }
        } else {
            g2.setColor(new Color(0, 229, 255));
            g2.drawString("EDITOR (" + p.dimensionMode + " • " + p.scriptLanguage + ")", 26, 36);
            g2.setColor(new Color(165, 185, 210));
            g2.drawString("Left-Drag: Move • Right-Drag: Orbit/Pan • Wheel: Zoom", 185, 36);
        }
    }

    private Entity pickEntity(int mx, int my) {
        GameProject p = activeProject;
        if (p == null) return null;
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        if ("2D".equalsIgnoreCase(p.dimensionMode)) {
            float ppu = h / Math.max(4f, editor2DZoom);
            float wx = editor2DCamX + (mx - w * 0.5f) / ppu;
            float wy = editor2DCamY - (my - h * 0.5f) / ppu;
            for (int i = p.entities.size() - 1; i >= 0; i--) {
                Entity e = p.entities.get(i);
                float hx = Math.max(0.35f, Math.abs(e.scaleX) * 0.5f);
                float hy = Math.max(0.35f, Math.abs(e.scaleY) * 0.5f);
                if (wx >= e.x - hx && wx <= e.x + hx && wy >= e.y - hy && wy <= e.y + hy) return e;
            }
            return null;
        } else {
            double yaw = Math.toRadians(editorYawDeg), pitch = Math.toRadians(editorPitchDeg);
            float horiz = (float) (Math.cos(pitch) * editorDist);
            float camX = (float) (Math.sin(yaw) * horiz);
            float camY = 0.5f + (float) (Math.sin(pitch) * editorDist);
            float camZ = (float) (Math.cos(yaw) * horiz);
            float fx = -camX, fy = 0.5f - camY, fz = -camZ;
            float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
            fx /= fl; fy /= fl; fz /= fl;
            float rx = -fz, rz = fx;
            float rl = (float) Math.sqrt(rx * rx + rz * rz);
            rx /= rl; rz /= rl;
            float ux = rz * fy, uy = rz * fx - rx * fz, uz = -rx * fy;
            float fovScale = (float) (1.0 / Math.tan(Math.toRadians(Math.max(35f, p.cameraFov) * 0.5)));

            Entity best = null;
            float bestD = 52f;
            for (Entity e : p.entities) {
                if ("TERRAIN".equalsIgnoreCase(e.primitive)) continue;
                float[] pr = project3D(e.x, e.y, e.z, camX, camY, camZ, rx, 0, rz, ux, uy, uz, fx, fy, fz, fovScale, w * 0.5f, h * 0.5f);
                if (pr != null) {
                    float d = (float) Math.hypot(mx - pr[0], my - pr[1]);
                    if (d < bestD) { bestD = d; best = e; }
                }
            }
            return best;
        }
    }

    public static Color toAwtColor(String hex, int alpha) {
        int argb = Entity.hexToArgbInt(hex, 0xFF00E5FF);
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, Math.max(0, Math.min(255, alpha)));
    }

    public void shutdown() {
        frameTimer.stop();
        audioEngine.shutdown();
    }
}
