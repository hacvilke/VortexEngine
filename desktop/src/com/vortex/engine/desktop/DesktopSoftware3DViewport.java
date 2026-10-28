package com.vortex.engine.desktop;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import com.vortex.engine.core.noise.FastNoiseLite;
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
    // Material preset of the entity currently being submitted (set in appendEntity3DTriangles).
    private float matSpec = 1f, matShineMul = 1f, matAmbMul = 1f;
    private String currentMatName = "MATTE";
    // v4.7.0 tile painter state
    private boolean tilePaintMode = false;
    private String tilePaintColor = "#6E7B5B";
    private String tilePaintLayer = "Ground";
    private boolean tilePaintSolid = false;
    public static final String[] TILE_PALETTE = {
            "#6E7B5B", "#4F5A44", "#8C7A5B", "#3F4650", "#A88D6A", "#5B6B7A",
            "#2E3138", "#C9A227", "#B04A4A", "#DDD8CE", "#7A6B8C", "#46695C"
    };
    private int hoverCellX = Integer.MIN_VALUE, hoverCellY = Integer.MIN_VALUE;
    /** Set by the studio so tile painting can refresh the hierarchy + inspector. */
    private Runnable onSceneChanged;
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
    private String gizmoMode = "MOVE"; // SELECT, MOVE, SCALE
    private boolean gridSnap = false;
    private boolean showColliders = false;

    private long lastTickNs = System.nanoTime();
    private int frameCount = 0;
    private long fpsWindowNs = System.nanoTime();
    private int currentFps = 0;
    private String snapPreviewZone = null; // "LEFT", "RIGHT", "BOTTOM", or null
    private final Timer frameTimer;

    public DesktopSoftware3DViewport() {
        setBackground(new Color(9, 9, 11));
        setFocusable(true);
        setupInputs();

        // Only run the 60 FPS frame timer during an active playtest (0% idle CPU & no idle FPS counter!)
        frameTimer = new Timer(16, e -> tickAndRepaint());
    }

    public void setSnapPreviewZone(String zone) {
        if ((snapPreviewZone == null && zone == null)
                || (snapPreviewZone != null && snapPreviewZone.equals(zone))) return;
        this.snapPreviewZone = zone;
        repaint();
    }

    public void setViewportListener(ViewportListener l) {
        this.listener = l;
    }

    public void invalidateAssetCaches() {
        spriteCache.clear();
        cachedTerrainVerts = null;
    }

    public void resetEditorCamera() {
        editorYawDeg = 35f;
        editorPitchDeg = 26f;
        editorDist = 19f;
        editor2DCamX = 0f;
        editor2DCamY = 1.5f;
        editor2DZoom = 18f;
        repaint();
    }

    public void loadProject(final GameProject project, boolean startPlay) {
        // Immediately kill all active audio lines & dispose previous VM before switching state
        audioEngine.stopAll();
        scriptEngine.dispose();

        this.activeProject = project;
        this.playing = startPlay;
        this.particles.clear();
        this.heldKeys.clear();
        this.spriteCache.clear();
        this.cachedTerrainVerts = null;
        this.physicsWorld.reset();
        this.lastTickNs = System.nanoTime();
        this.fpsWindowNs = System.nanoTime();
        this.frameCount = 0;
        this.currentFps = startPlay ? 60 : 0;

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
                if (playing) audioEngine.play(soundName);
            }

            @Override
            public void onPlayMusic(String pattern, int bpm) {
                if (playing) audioEngine.startMusicLoop(pattern, bpm);
            }

            @Override
            public void onStopMusic() {
                audioEngine.stopMusicLoop();
            }
        });

        if (project != null) {
            editor2DZoom = project.zoom2D;
            Entity target = project.findByName(project.cameraTargetName);
            if (target != null) {
                playLookX = target.x; playLookY = target.y; playLookZ = target.z;
                playCamX = target.x; playCamY = target.y + project.cameraHeight; playCamZ = target.z + project.cameraDistance;
            }
            if (startPlay) {
                scriptEngine.compileProject(project);
                if (!frameTimer.isRunning()) frameTimer.start();
                requestFocusInWindow();
            } else {
                if (frameTimer.isRunning()) frameTimer.stop();
                audioEngine.stopAll();
            }
        } else {
            if (frameTimer.isRunning()) frameTimer.stop();
        }
        repaint();
    }

    public boolean isPlaying() { return playing; }
    public GameProject getActiveProject() { return activeProject; }
    public String getSelectedEntityId() { return selectedEntityId; }
    public void setSelectedEntityId(String id) { this.selectedEntityId = id; repaint(); }
    public String getGizmoMode() { return gizmoMode; }
    public void setGizmoMode(String mode) { this.gizmoMode = mode == null ? "MOVE" : mode.toUpperCase(); repaint(); }
    public boolean isGridSnap() { return gridSnap; }
    public void setGridSnap(boolean snap) { this.gridSnap = snap; repaint(); }
    public boolean isTilePaintMode() { return tilePaintMode; }
    public void setTilePaintMode(boolean on) { this.tilePaintMode = on; repaint(); }
    public String getTilePaintColor() { return tilePaintColor; }
    public void setTilePaintColor(String hex) { if (hex != null && hex.startsWith("#")) this.tilePaintColor = hex; repaint(); }
    public void setOnSceneChanged(Runnable r) { this.onSceneChanged = r; }
    private void notifySceneChanged() { if (onSceneChanged != null) onSceneChanged.run(); }
    public String getTilePaintLayer() { return tilePaintLayer; }
    public void setTilePaintLayer(String layer) { if (layer != null && !layer.trim().isEmpty()) this.tilePaintLayer = layer.trim(); }
    public boolean isTilePaintSolid() { return tilePaintSolid; }
    public void setTilePaintSolid(boolean solid) { this.tilePaintSolid = solid; repaint(); }
    public boolean isShowColliders() { return showColliders; }
    public void setShowColliders(boolean show) { this.showColliders = show; repaint(); }

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
                if (tilePaintMode && activeProject != null && "2D".equalsIgnoreCase(activeProject.dimensionMode)) {
                    if (handleTilePaletteClick(e.getX(), e.getY())) return;
                    if (SwingUtilities.isLeftMouseButton(e)) { paintTileAt(e.getX(), e.getY(), false); return; }
                    if (SwingUtilities.isRightMouseButton(e)) { paintTileAt(e.getX(), e.getY(), true); return; }
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
            public void mouseMoved(MouseEvent e) {
                if (tilePaintMode && activeProject != null && !playing
                        && "2D".equalsIgnoreCase(activeProject.dimensionMode)) {
                    updateTileHover(e.getX(), e.getY());
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (lastMouse == null || activeProject == null || playing) return;
                if (tilePaintMode && "2D".equalsIgnoreCase(activeProject.dimensionMode)) {
                    hoverCellX = cellX(e.getX());
                    hoverCellY = cellY(e.getY());
                    if (SwingUtilities.isLeftMouseButton(e)) paintTileAt(e.getX(), e.getY(), false);
                    else if (SwingUtilities.isRightMouseButton(e)) paintTileAt(e.getX(), e.getY(), true);
                    return;
                }
                int dx = e.getX() - lastMouse.x;
                int dy = e.getY() - lastMouse.y;
                lastMouse = e.getPoint();

                boolean is3D = "3D".equalsIgnoreCase(activeProject.dimensionMode);
                if (draggingEntity && selectedEntityId != null && SwingUtilities.isLeftMouseButton(e) && !"SELECT".equals(gizmoMode)) {
                    Entity sel = activeProject.findById(selectedEntityId);
                    if (sel != null) {
                        if ("SCALE".equals(gizmoMode)) {
                            float sDeltaX = dx * 0.025f;
                            float sDeltaY = -dy * 0.025f;
                            sel.scaleX = Math.max(0.15f, sel.scaleX + sDeltaX);
                            sel.scaleY = Math.max(0.15f, sel.scaleY + sDeltaY);
                            if (is3D && e.isShiftDown()) sel.scaleZ = Math.max(0.15f, sel.scaleZ + sDeltaY);
                        } else if (is3D) {
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
                            if (gridSnap) {
                                sel.x = Math.round(sel.x * 2f) * 0.5f;
                                sel.y = Math.round(sel.y * 2f) * 0.5f;
                                sel.z = Math.round(sel.z * 2f) * 0.5f;
                            }
                        } else {
                            float worldPerPx = editor2DZoom / Math.max(1f, getHeight());
                            sel.x += dx * worldPerPx;
                            sel.y -= dy * worldPerPx;
                            if (gridSnap) {
                                sel.x = Math.round(sel.x * 4f) * 0.25f;
                                sel.y = Math.round(sel.y * 4f) * 0.25f;
                            }
                        }
                        sel.transformDirty = true;
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
        if (!playing || activeProject == null) {
            if (frameTimer.isRunning()) frameTimer.stop();
            return;
        }
        long now = System.nanoTime();
        float dt = (now - lastTickNs) / 1_000_000_000f;
        lastTickNs = now;
        if (dt < 0f || dt > 0.08f) dt = 0.016f;

        frameCount++;
        if (now - fpsWindowNs >= 500_000_000L) {
            currentFps = Math.round(frameCount * 1_000_000_000f / Math.max(1L, now - fpsWindowNs));
            frameCount = 0;
            fpsWindowNs = now;
            if (listener != null) {
                listener.onTelemetry(currentFps, scriptEngine.score, scriptEngine.health, scriptEngine.hudBanner);
            }
        }

        syncKeyboardAxes();
        scriptEngine.update(dt);
        List<PhysicsWorld.CollisionEvent> cols = physicsWorld.step(activeProject, dt);
        for (PhysicsWorld.CollisionEvent ce : cols) {
            scriptEngine.notifyCollision(ce.a, ce.b);
        }
        updateParticles(dt, activeProject.gravityY);
        repaint();
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

        // Scene tree: moving a parent in the editor immediately drags its children.
        if (!playing) com.vortex.engine.core.model.SceneHierarchy.propagate(p.entities);

        boolean mode3D = "3D".equalsIgnoreCase(p.dimensionMode);
        if (mode3D) {
            paintSky(g2, p, w, h);
        } else {
            g2.setColor(toAwtColor(p.skyColorHex, 255));
            g2.fillRect(0, 0, w, h);
        }

        if (mode3D) {
            paint3DScene(g2, p, w, h);
        } else {
            paint2DScene(g2, p, w, h);
            if (tilePaintMode && !playing) paintTileOverlay(g2, p, w, h);
        }

        paintHudOverlay(g2, p, w, h);
        g2.dispose();
    }

    // =========================================================================
    // 3D Perspective Camera + Sunlit Triangle Painter
    // =========================================================================
    /**
     * Sky + atmosphere (v4.7.0): vertical sky gradient from the light rig, warm horizon band, sun
     * disc with bloom, and stars when the sun is low. Cheap, resolution independent, no textures.
     */
    private void paintSky(Graphics2D g2, GameProject p, int w, int h) {
        Color base = toAwtColor(p.skyColorHex, 255);
        float lum = (base.getRed() * 0.2126f + base.getGreen() * 0.7152f + base.getBlue() * 0.0722f) / 255f;
        float sunElev = Math.max(0f, Math.min(1f, p.sunPitchDeg / 90f));
        Color sunCol = toAwtColor(p.sunColorHex, 255);

        // horizon colour = sky tinted toward the sun colour, brightness follows sun elevation
        float horizonMix = 0.20f + 0.42f * (1f - sunElev);
        Color horizon = new Color(
                clampRGB(base.getRed() * (1f - horizonMix) + sunCol.getRed() * horizonMix * 0.75f),
                clampRGB(base.getGreen() * (1f - horizonMix) + sunCol.getGreen() * horizonMix * 0.75f),
                clampRGB(base.getBlue() * (1f - horizonMix) + sunCol.getBlue() * horizonMix * 0.75f));
        Color zenith = new Color(clampRGB(base.getRed() * 0.55f), clampRGB(base.getGreen() * 0.58f), clampRGB(base.getBlue() * 0.68f));

        g2.setPaint(new java.awt.GradientPaint(0, 0, zenith, 0, h, horizon));
        g2.fillRect(0, 0, w, h);

        // stars: deterministic, only when the sky is genuinely dark
        if (lum < 0.16f) {
            java.util.Random rng = new java.util.Random(1337);
            int starCount = 90;
            g2.setColor(new Color(255, 255, 255, 90));
            for (int i = 0; i < starCount; i++) {
                int sx = rng.nextInt(Math.max(1, w));
                int sy = rng.nextInt(Math.max(1, (int) (h * 0.72f)));
                int size = rng.nextInt(10) == 0 ? 2 : 1;
                g2.fillRect(sx, sy, size, size);
            }
        }

        // sun disc: project a point far along the light direction from the camera target
        float[] camBasis = editorCameraBasis();
        if (camBasis != null) {
            float yaw = (float) Math.toRadians(p.sunYawDeg);
            float pitch = (float) Math.toRadians(Math.max(4f, p.sunPitchDeg));
            float lx = (float) (Math.sin(yaw) * Math.cos(pitch));
            float ly = (float) Math.sin(pitch);
            float lz = (float) (Math.cos(yaw) * Math.cos(pitch));
            float cx = camBasis[0] + lx * 600f, cyy = camBasis[1] + ly * 600f, cz = camBasis[2] + lz * 600f;
            float[] s = project3D(cx, cyy, cz, camBasis[0], camBasis[1], camBasis[2],
                    camBasis[3], camBasis[4], camBasis[5], camBasis[6], camBasis[7], camBasis[8],
                    camBasis[9], camBasis[10], camBasis[11], camBasis[12], w * 0.5f, h * 0.5f);
            if (s != null && s[0] > -200 && s[0] < w + 200 && s[1] > -200 && s[1] < h + 200) {
                float radius = 26f + 22f * sunElev;
                for (int i = 4; i >= 1; i--) {
                    float r = radius * i * 0.85f;
                    int alpha = Math.max(6, 26 / i);
                    g2.setColor(new Color(sunCol.getRed(), sunCol.getGreen(), sunCol.getBlue(), alpha));
                    g2.fillOval(Math.round(s[0] - r), Math.round(s[1] - r), Math.round(r * 2), Math.round(r * 2));
                }
                g2.setColor(new Color(clampRGB(sunCol.getRed() * 1.05f), clampRGB(sunCol.getGreen() * 1.02f), clampRGB(sunCol.getBlue() * 0.95f)));
                g2.fillOval(Math.round(s[0] - radius * 0.55f), Math.round(s[1] - radius * 0.55f),
                        Math.round(radius * 1.1f), Math.round(radius * 1.1f));
            }
        }
    }

    private static int clampRGB(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    /** {camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale} for the editor camera. */
    private float[] editorCameraBasis() {
        double yaw = Math.toRadians(editorYawDeg);
        double pitch = Math.toRadians(editorPitchDeg);
        float horiz = (float) (Math.cos(pitch) * editorDist);
        float camX = (float) (Math.sin(yaw) * horiz);
        float camY = 0.5f + (float) (Math.sin(pitch) * editorDist);
        float camZ = (float) (Math.cos(yaw) * horiz);
        float lookX = 0f, lookY = 0.5f, lookZ = 0f;
        float fx = lookX - camX, fy = lookY - camY, fz = lookZ - camZ;
        float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fl < 1e-4f) fl = 1f;
        fx /= fl; fy /= fl; fz /= fl;
        float rx = -fz, rz = fx;
        float rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) { rx = 1f; rz = 0f; rl = 1f; }
        rx /= rl; rz /= rl;
        float ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        float fovScale = (float) (1.0 / Math.tan(Math.toRadians(60) * 0.5));
        GameProject p = activeProject;
        if (p != null) fovScale = (float) (1.0 / Math.tan(Math.toRadians(Math.max(35f, p.cameraFov) * 0.5)));
        return new float[]{camX, camY, camZ, rx, 0f, rz, ux, uy, uz, fx, fy, fz, fovScale};
    }

    /** Tile-mode HUD: cell hover ring, palette strip, layer + solid readout. */
    private void paintTileOverlay(Graphics2D g2, GameProject p, int w, int h) {
        if (hoverCellX != Integer.MIN_VALUE) {
            int[] r = cellRect(hoverCellX, hoverCellY);
            g2.setColor(new Color(255, 255, 255, 170));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(r[0], r[1], r[2], r[3]);
            g2.setColor(toAwtColor(tilePaintColor, 70));
            g2.fillRect(r[0], r[1], r[2], r[3]);
        }
        int size = 26, gap = 6, top = h - size - 10;
        g2.setColor(new Color(18, 18, 20, 210));
        g2.fillRoundRect(6, top - 8, TILE_PALETTE.length * (size + gap) + 12, size + 16, 8, 8);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(120, 120, 128, 160));
        g2.drawRoundRect(6, top - 8, TILE_PALETTE.length * (size + gap) + 12, size + 16, 8, 8);
        for (int i = 0; i < TILE_PALETTE.length; i++) {
            int x = 12 + i * (size + gap);
            g2.setColor(toAwtColor(TILE_PALETTE[i], 255));
            g2.fillRoundRect(x, top, size, size, 5, 5);
            g2.setColor(TILE_PALETTE[i].equalsIgnoreCase(tilePaintColor)
                    ? Color.WHITE : new Color(0, 0, 0, 90));
            g2.setStroke(new BasicStroke(TILE_PALETTE[i].equalsIgnoreCase(tilePaintColor) ? 2f : 1f));
            g2.drawRoundRect(x, top, size, size, 5, 5);
        }
        String hud = "TILE  •  layer " + tilePaintLayer + (tilePaintSolid ? "  •  SOLID" : "  •  decor")
                + "  •  LMB paint  RMB erase";
        g2.setFont(getFont() == null ? new Font("SansSerif", Font.PLAIN, 11) : getFont().deriveFont(Font.PLAIN, 11f));
        int tw = g2.getFontMetrics().stringWidth(hud);
        g2.setColor(new Color(18, 18, 20, 200));
        g2.fillRoundRect(6, top - 34, tw + 16, 22, 7, 7);
        g2.setColor(new Color(230, 230, 235));
        g2.drawString(hud, 14, top - 19);
    }

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
                g2.setColor(i == 0 ? new Color(235, 235, 240, 125) : new Color(62, 62, 70, 75));
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

        // Soft contact shadows: a cheap projected blob under every solid, offset away from the sun.
        if (p.shadows && p.shadowStrength > 0.01f) {
            float sYaw = (float) Math.toRadians(p.sunYawDeg);
            float sPitch = (float) Math.toRadians(Math.max(12f, p.sunPitchDeg));
            float sxDir = (float) (Math.sin(sYaw) * Math.cos(sPitch));
            float szDir = (float) (Math.cos(sYaw) * Math.cos(sPitch));
            for (Entity se : p.entities) {
                if (!se.alive || !se.visible) continue;
                if ("TERRAIN".equalsIgnoreCase(se.primitive) || "PLANE".equalsIgnoreCase(se.primitive)) continue;
                // Giant set-dressing meshes would smear into a lake of black - skip them.
                if (Math.max(Math.abs(se.scaleX), Math.abs(se.scaleZ)) > 8f) continue;
                float groundY = 0f;
                Entity terrain = null;
                for (Entity te : p.entities) {
                    if (te.alive && "TERRAIN".equalsIgnoreCase(te.primitive)) { terrain = te; break; }
                }
                if (terrain != null) {
                    groundY = TerrainGenerator.sampleWorldHeight(terrain, getNoise(p), se.x, se.z);
                }
                float height = Math.max(0f, se.y - Math.abs(se.scaleY) * 0.5f - groundY);
                float falloff = (float) Math.max(0.15, 1.0 - height / 12.0);
                float offX = sxDir * height * 0.55f;
                float offZ = szDir * height * 0.55f;
                float[] c = project3D(se.x + offX, groundY + 0.02f, se.z + offZ,
                        camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH);
                if (c == null) continue;
                float radius = Math.max(Math.abs(se.scaleX), Math.abs(se.scaleZ)) * (fovScale * halfH) / Math.max(1f, c[2]) * 0.62f;
                if (radius < 1.5f) continue;
                int alpha = (int) (255 * p.shadowStrength * falloff * Math.min(1f, 9f / Math.max(2f, c[2])));
                alpha = Math.min(110, alpha);
                radius = Math.min(radius, 90f);
                if (alpha <= 3) continue;
                java.awt.geom.Ellipse2D.Float sh = new java.awt.geom.Ellipse2D.Float(
                        c[0] - radius, c[1] - radius * 0.42f, radius * 2f, radius * 0.84f);
                g2.setColor(new Color(0, 0, 0, Math.min(150, alpha)));
                g2.fill(sh);
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
                g2.setColor(new Color(235, 235, 242));
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
        applyMaterial(e);
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
                c = proceduralTint(e, c, (x0 + x1 + x2) / 3f, (y0 + y1 + y2) / 3f, (z0 + z1 + z2) / 3f);
                // Edge stroke matches the fill so adjacent terrain triangles stitch without cracks.
                Color seam = new Color(Math.round(c.getRed() * 0.93f), Math.round(c.getGreen() * 0.93f),
                        Math.round(c.getBlue() * 0.93f), 255);
                pushProjectedTri(x0, y0, z0, x1, y1, z1, x2, y2, z2, nx, ny, nz, c, false, seam,
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
        Color selEdge = isSel ? new Color(255, 255, 255, 235) : new Color(0, 0, 0, 40);

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
            c = proceduralTint(e, c, (p0[0] + p1[0] + p2[0]) / 3f, (p0[1] + p1[1] + p2[1]) / 3f, (p0[2] + p1[2] + p2[2]) / 3f);
            pushProjectedTri(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2],
                    nx, ny, nz, c, e.emissive, selEdge,
                    camX, camY, camZ, rx, ry, rz, ux, uy, uz, fx, fy, fz, fovScale, halfW, halfH, skyCol, p.fogNear, p.fogFar);
        }
    }

    /** Maps the material preset to specular strength / shininess / ambient response. */
    private void applyMaterial(Entity e) {
        String m = e.material == null ? "MATTE" : e.material.toUpperCase(java.util.Locale.US);
        currentMatName = m;
        switch (m) {
            case "METAL":    matSpec = 2.6f; matShineMul = 1.35f; matAmbMul = 0.72f; break;
            case "GLASS":    matSpec = 3.2f; matShineMul = 1.7f;  matAmbMul = 1.18f; break;
            case "EMISSIVE": matSpec = 0.0f; matShineMul = 1f;    matAmbMul = 1.0f;  break;
            case "UNLIT":    matSpec = 0.0f; matShineMul = 1f;    matAmbMul = 0f;    break;
            default:         matSpec = 1.0f; matShineMul = 1f;    matAmbMul = 1f;    break;
        }
    }

    /**
     * Procedural surface pattern (v4.7.0) — modulates a triangle's colour from its world position so
     * surfaces read as checker plate, brushed stripes, stone noise, grid panels or brick without any
     * texture files. Deterministic: same world point always yields the same tint.
     */
    private static Color proceduralTint(Entity e, Color base, float wx, float wy, float wz) {
        String tex = e.texture == null ? "NONE" : e.texture.toUpperCase(java.util.Locale.US);
        if ("NONE".equals(tex)) return base;
        float f;
        switch (tex) {
            case "CHECKER": {
                int cx = (int) Math.floor(wx * 2f), cy = (int) Math.floor(wy * 2f), cz = (int) Math.floor(wz * 2f);
                f = ((cx + cy + cz) & 1) == 0 ? 1.0f : 0.84f;
                break;
            }
            case "STRIPES":
                f = 0.88f + 0.12f * (float) Math.abs(Math.sin(wy * 9.0));
                break;
            case "GRID": {
                float gx = Math.abs(wx * 4f - Math.round(wx * 4f));
                float gy = Math.abs(wy * 4f - Math.round(wy * 4f));
                f = (gx < 0.06f || gy < 0.06f) ? 0.80f : 1.0f;
                break;
            }
            case "BRICK": {
                int row = (int) Math.floor(wy * 4f);
                float offset = (row & 1) == 0 ? 0f : 0.5f;
                float bx = Math.abs((wx * 2f + offset) - Math.round(wx * 2f + offset));
                float by = Math.abs(wy * 4f - Math.round(wy * 4f));
                f = (bx < 0.05f || by < 0.07f) ? 0.78f : 1.0f;
                break;
            }
            case "NOISE": {
                int h = Float.floatToIntBits(wx * 31.7f) ^ Float.floatToIntBits(wy * 57.3f) ^ Float.floatToIntBits(wz * 13.1f);
                h ^= (h >>> 16);
                f = 0.90f + ((h & 0xFF) / 255f) * 0.18f;
                break;
            }
            default:
                return base;
        }
        return new Color(
                Math.max(0, Math.min(255, Math.round(base.getRed() * f))),
                Math.max(0, Math.min(255, Math.round(base.getGreen() * f))),
                Math.max(0, Math.min(255, Math.round(base.getBlue() * f))),
                base.getAlpha());
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

        // ------------------------------------------------------------------
        // v4.6.0 lighting: directional key light + hemisphere ambient + wrap
        // diffuse + Blinn-Phong specular + rim. All driven by the project's
        // light rig, so the Scene dock can retune the whole mood live.
        // ------------------------------------------------------------------
        GameProject lp = activeProject;
        float lYaw = (float) Math.toRadians(lp != null ? lp.sunYawDeg : 42f);
        float lPitch = (float) Math.toRadians(lp != null ? lp.sunPitchDeg : 54f);
        float lx = (float) (Math.sin(lYaw) * Math.cos(lPitch));
        float ly = (float) Math.sin(lPitch);
        float lz = (float) (Math.cos(lYaw) * Math.cos(lPitch));
        float ll = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (ll > 1e-5f) { lx /= ll; ly /= ll; lz /= ll; }

        float sunI = lp == null ? 1f : Math.max(0f, Math.min(2f, lp.sunIntensity));
        float amb = lp == null ? 0.34f : Math.max(0f, Math.min(1f, lp.ambientStrength));
        Color sunCol = lp == null ? Color.WHITE : toAwtColor(lp.sunColorHex, 255);

        // Two-sided lighting: flip the normal toward the camera so triangles wound either way
        // receive the same light (this is what kept spheres rendering nearly black).
        float camDx = camX - (x0 + x1 + x2) / 3f;
        float camDy = camY - (y0 + y1 + y2) / 3f;
        float camDz = camZ - (z0 + z1 + z2) / 3f;
        if (nx * camDx + ny * camDy + nz * camDz < 0f) { nx = -nx; ny = -ny; nz = -nz; }

        float ndl = nx * lx + ny * ly + nz * lz;
        float wrap = Math.max(0f, (ndl + 0.32f) / 1.32f);          // half-Lambert: soft terminator
        float skyFill = 0.5f + 0.5f * ny;                          // up-facing gets sky, down gets bounce

        // view vector (surface -> camera) for the specular lobe
        float faceCx = (x0 + x1 + x2) / 3f, faceCy = (y0 + y1 + y2) / 3f, faceCz = (z0 + z1 + z2) / 3f;
        float vx1 = camX - faceCx, vy1 = camY - faceCy, vz1 = camZ - faceCz;
        float vl = (float) Math.sqrt(vx1 * vx1 + vy1 * vy1 + vz1 * vz1);
        float spec = 0f;
        if (vl > 1e-4f) {
            vx1 /= vl; vy1 /= vl; vz1 /= vl;
            float hx1 = lx + vx1, hy1 = ly + vy1, hz1 = lz + vz1;
            float hl = (float) Math.sqrt(hx1 * hx1 + hy1 * hy1 + hz1 * hz1);
            if (hl > 1e-4f) {
                float nh = Math.max(0f, (nx * hx1 + ny * hy1 + nz * hz1) / hl);
                spec = (float) Math.pow(nh, 26.0 * matShineMul) * 0.45f * sunI * matSpec;
            }
        }
        float rim = emissive ? 0f : (float) Math.pow(1f - Math.max(0f, Math.abs(ndl)), 3.0) * 0.10f;

        float shade;
        if (emissive || "EMISSIVE".equalsIgnoreCase(String.valueOf(currentMatName))) {
            shade = 1.02f + wrap * 0.18f;
        } else if ("UNLIT".equalsIgnoreCase(String.valueOf(currentMatName))) {
            shade = 1f;
        } else {
            shade = amb * matAmbMul * (0.72f + 0.28f * skyFill) + wrap * sunI * 0.86f + spec + rim;
        }
        shade = Math.max(0.06f, Math.min(2.4f, shade));

        int r = Math.min(255, Math.round(col.getRed() * shade));
        int g = Math.min(255, Math.round(col.getGreen() * shade));
        int b = Math.min(255, Math.round(col.getBlue() * shade));
        if (!emissive) {
            // warm the lit side toward the light colour, cool the shadow side
            float litMix = (float) Math.min(0.30, wrap * sunI * 0.26);
            r = Math.round(r * (1f - litMix) + sunCol.getRed() * litMix);
            g = Math.round(g * (1f - litMix) + sunCol.getGreen() * litMix);
            b = Math.round(b * (1f - litMix) + sunCol.getBlue() * litMix);
            float coolMix = (float) Math.min(0.16, (1f - wrap) * 0.16f);
            r = Math.round(r * (1f - coolMix) + 24 * coolMix);
            g = Math.round(g * (1f - coolMix) + 28 * coolMix);
            b = Math.round(b * (1f - coolMix) + 40 * coolMix);
        }

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

    private FastNoiseLite cachedNoise = null;
    private int cachedNoiseSeed = Integer.MIN_VALUE;

    /** Shared terrain noise so shadows + terrain mesh agree on the ground height. */
    private FastNoiseLite getNoise(GameProject p) {
        if (cachedNoise == null || cachedNoiseSeed != p.terrainSeed) {
            cachedNoise = new FastNoiseLite(p.terrainSeed);
            cachedNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
            cachedNoise.SetFrequency(p.terrainFreq);
            cachedNoiseSeed = p.terrainSeed;
        }
        return cachedNoise;
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
    // ------------------------------------------------------------------ tile painter (v4.7.0)

    /** Grid cell X for a screen X in 2D editor view. */
    private int cellX(int sx) {
        float zoom = editor2DZoom;
        float ppu = getHeight() / Math.max(4f, zoom);
        float world = (sx - getWidth() * 0.5f) / ppu + editor2DCamX;
        return (int) Math.floor(world / com.vortex.engine.core.asset.TileBrush.CELL);
    }

    /** Grid cell Y for a screen Y in 2D editor view (world Y is up). */
    private int cellY(int sy) {
        float zoom = editor2DZoom;
        float ppu = getHeight() / Math.max(4f, zoom);
        float world = (getHeight() * 0.5f - sy) / ppu + editor2DCamY;
        return (int) Math.floor(world / com.vortex.engine.core.asset.TileBrush.CELL);
    }

    /** Screen rect of a grid cell: {x, y, w, h}. */
    private int[] cellRect(int gx, int gy) {
        float ppu = getHeight() / Math.max(4f, editor2DZoom);
        float x = getWidth() * 0.5f + (gx * com.vortex.engine.core.asset.TileBrush.CELL - editor2DCamX) * ppu;
        float y = getHeight() * 0.5f - ((gy + 1) * com.vortex.engine.core.asset.TileBrush.CELL - editor2DCamY) * ppu;
        return new int[]{Math.round(x), Math.round(y),
                Math.max(1, Math.round(ppu * com.vortex.engine.core.asset.TileBrush.CELL)),
                Math.max(1, Math.round(ppu * com.vortex.engine.core.asset.TileBrush.CELL))};
    }

    private void paintTileAt(int sx, int sy, boolean erase) {
        if (activeProject == null || playing) return;
        int gx = cellX(sx), gy = cellY(sy);
        hoverCellX = gx; hoverCellY = gy;
        boolean changed = erase
                ? com.vortex.engine.core.asset.TileBrush.erase(activeProject, tilePaintLayer, gx, gy)
                : com.vortex.engine.core.asset.TileBrush.paint(activeProject, tilePaintLayer, gx, gy,
                        tilePaintColor, tilePaintSolid) != null;
        if (changed) notifySceneChanged();
        repaint();
    }

    /** Palette hit test at the bottom of the viewport; returns true when a swatch was clicked. */
    private boolean handleTilePaletteClick(int sx, int sy) {
        int size = 26, gap = 6, top = getHeight() - size - 10;
        if (sy < top - 4 || sy > top + size + 4) return false;
        for (int i = 0; i < TILE_PALETTE.length; i++) {
            int x = 12 + i * (size + gap);
            if (sx >= x && sx <= x + size) {
                tilePaintColor = TILE_PALETTE[i];
                repaint();
                return true;
            }
        }
        return false;
    }

    /** Hover ring in tile mode (called from the motion listener). */
    void updateTileHover(int sx, int sy) {
        hoverCellX = cellX(sx);
        hoverCellY = cellY(sy);
    }

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
                g2.setColor(gx == 0 ? new Color(235, 235, 240, 115) : new Color(56, 56, 64, 72));
                g2.drawLine(sx, 0, sx, h);
            }
            for (int gy = (int) (cy - halfH) - 1; gy <= (int) (cy + halfH) + 1; gy += 2) {
                int sy = Math.round(h * 0.5f - (gy - cy) * ppu);
                g2.setColor(gy == 0 ? new Color(235, 235, 240, 115) : new Color(56, 56, 64, 72));
                g2.drawLine(0, sy, w, sy);
            }
        }

        for (Entity e : p.entities) {
            if (!e.alive || !e.visible) continue;
            float sx = w * 0.5f + (e.x - cx) * ppu;
            float sy = h * 0.5f - (e.y - cy) * ppu;
            int pw = Math.max(4, Math.round(Math.abs(e.scaleX) * ppu));
            int ph = Math.max(4, Math.round(Math.abs(e.scaleY) * ppu));

            // v4.7.0 — emissive material blooms in 2D so lights read at a glance.
            if (e.material != null && e.material.equalsIgnoreCase("EMISSIVE")) {
                Color glowBase = toAwtColor(e.colorHex, 255);
                for (int ring = 3; ring >= 1; ring--) {
                    int pad = Math.round(Math.max(pw, ph) * 0.22f * ring);
                    g2.setColor(new Color(glowBase.getRed(), glowBase.getGreen(), glowBase.getBlue(), 16 + 8 * (3 - ring)));
                    g2.fillOval(Math.round(sx) - pw / 2 - pad, Math.round(sy) - ph / 2 - pad, pw + pad * 2, ph + pad * 2);
                }
            }

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

            if (showColliders && !"NONE".equalsIgnoreCase(e.collider) && !"NONE".equalsIgnoreCase(e.bodyType)) {
                g2.setColor(e.isTrigger ? new Color(250, 204, 21, 215) : new Color(0, 230, 118, 215));
                g2.setStroke(e.isTrigger
                        ? new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 4f}, 0f)
                        : new BasicStroke(1.4f));
                if ("SPHERE".equalsIgnoreCase(e.collider) || "CIRCLE".equals(prim) || "SPRITE_COIN".equals(prim)) {
                    int d = Math.max(pw, ph);
                    g2.drawOval(-d / 2, -d / 2, d, d);
                } else {
                    g2.drawRect(-pw / 2, -ph / 2, pw, ph);
                }
            }

            if (!playing && e.id.equals(selectedEntityId)) {
                g2.setColor(new Color(255, 255, 255, 225));
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawRect(-pw / 2 - 3, -ph / 2 - 3, pw + 6, ph + 6);
                g2.setTransform(old);
                // Godot / Unreal X & Y Transform Gizmo Arrows
                int gx = Math.round(sx), gy = Math.round(sy);
                int gizLen = 36;
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 107, 107)); // X axis
                g2.drawLine(gx, gy, gx + gizLen, gy);
                if ("SCALE".equals(gizmoMode)) {
                    g2.fillRect(gx + gizLen - 3, gy - 3, 7, 7);
                } else {
                    g2.fillPolygon(new int[]{gx + gizLen + 6, gx + gizLen - 2, gx + gizLen - 2}, new int[]{gy, gy - 4, gy + 4}, 3);
                }
                g2.setColor(new Color(74, 222, 128)); // Y axis
                g2.drawLine(gx, gy, gx, gy - gizLen);
                if ("SCALE".equals(gizmoMode)) {
                    g2.fillRect(gx - 3, gy - gizLen - 4, 7, 7);
                } else {
                    g2.fillPolygon(new int[]{gx, gx - 4, gx + 4}, new int[]{gy - gizLen - 6, gy - gizLen + 2, gy - gizLen + 2}, 3);
                }
                continue;
            }
            g2.setTransform(old);
            if (showColliders && (Math.abs(e.vx) > 0.1f || Math.abs(e.vy) > 0.1f)) {
                g2.setColor(new Color(255, 255, 255, 180));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawLine(Math.round(sx), Math.round(sy),
                        Math.round(sx + e.vx * 4f), Math.round(sy - e.vy * 4f));
            }
        }

        for (Particle pt : particles) {
            int px = Math.round(w * 0.5f + (pt.x - cx) * ppu);
            int py = Math.round(h * 0.5f - (pt.y - cy) * ppu);
            g2.setColor(pt.color);
            g2.fillOval(px - 4, py - 4, 8, 8);
        }
    }

    private void paintHudOverlay(Graphics2D g2, GameProject p, int w, int h) {
        DesktopUiKit.enableAA(g2);
        if (playing) {
            int barW = Math.min(w - 24, 340);
            g2.setColor(new Color(14, 14, 17, 210));
            g2.fill(new RoundRectangle2D.Float(12, 10, barW, 32, 8, 8));
            g2.setColor(new Color(58, 58, 66, 200));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(12, 10, barW, 32, 8, 8));

            g2.setFont(DesktopUiKit.FONT_UI_BOLD);
            g2.setColor(new Color(255, 255, 255));
            g2.drawString("SCORE " + scriptEngine.score, 22, 30);
            g2.setColor(scriptEngine.health > 30 ? new Color(134, 239, 172) : new Color(252, 165, 165));
            g2.drawString("HP " + scriptEngine.health, 128, 30);
            g2.setColor(new Color(185, 185, 194));
            g2.drawString(currentFps + " FPS", 218, 30);

            if (scriptEngine.hudBanner != null && !scriptEngine.hudBanner.isEmpty()) {
                g2.setFont(DesktopUiKit.FONT_TITLE);
                FontMetrics fm = g2.getFontMetrics();
                int bw = fm.stringWidth(scriptEngine.hudBanner);
                g2.setColor(new Color(14, 14, 17, 220));
                g2.fillRoundRect((w - bw) / 2 - 14, 50, bw + 28, 30, 8, 8);
                g2.setColor(new Color(255, 255, 255));
                g2.drawString(scriptEngine.hudBanner, (w - bw) / 2, 70);
            }

            // In-Game Custom GUI Widgets (Labels & Progress/Chart Bars)
            for (VortexScriptEngine.UiWidget wg : scriptEngine.uiWidgets.values()) {
                int wx = Math.round((wg.xPct / 100f) * w);
                int wy = Math.round((wg.yPct / 100f) * h);
                Color wc = toAwtColor(wg.colorHex, 255);
                if ("BAR".equalsIgnoreCase(wg.kind)) {
                    int bw = Math.min(240, Math.max(120, w / 4));
                    int bh = 14;
                    int bx = wx - bw / 2;
                    float ratio = Math.max(0f, Math.min(1f, wg.value / Math.max(0.0001f, wg.maxValue)));
                    g2.setFont(DesktopUiKit.FONT_SMALL_BOLD);
                    g2.setColor(new Color(245, 245, 248));
                    g2.drawString(wg.text + " (" + Math.round(wg.value) + "/" + Math.round(wg.maxValue) + ")", bx, wy - 4);
                    g2.setColor(new Color(14, 14, 18, 220));
                    g2.fillRoundRect(bx, wy, bw, bh, 6, 6);
                    g2.setColor(wc);
                    g2.fillRoundRect(bx + 2, wy + 2, Math.max(0, Math.round((bw - 4) * ratio)), bh - 4, 4, 4);
                    g2.setColor(new Color(255, 255, 255, 95));
                    g2.drawRoundRect(bx, wy, bw, bh, 6, 6);
                } else {
                    g2.setFont(new Font(DesktopUiKit.UI_FAMILY, Font.BOLD, Math.max(12, Math.round(wg.fontSize))));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(wg.text);
                    g2.setColor(new Color(10, 10, 13, 185));
                    g2.fillRoundRect(wx - tw / 2 - 8, wy - fm.getAscent() - 2, tw + 16, fm.getHeight() + 4, 6, 6);
                    g2.setColor(wc);
                    g2.drawString(wg.text, wx - tw / 2, wy);
                }
            }

            // In-Game RPG / Story Dialog Box (`vortex.showDialog(...)` / Blueprint GUI Dialog Block)
            VortexScriptEngine.DialogState dlg = scriptEngine.activeDialog;
            if (dlg != null && dlg.active && dlg.message != null && !dlg.message.isEmpty()) {
                int dw = Math.min(640, w - 40);
                int dh = 86;
                int dx = (w - dw) / 2;
                int dy = h - dh - 18;
                g2.setColor(new Color(11, 11, 14, 238));
                g2.fillRoundRect(dx, dy, dw, dh, 12, 12);
                g2.setColor(new Color(255, 255, 255, 220));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(dx, dy, dw, dh, 12, 12);

                if (dlg.speaker != null && !dlg.speaker.isEmpty()) {
                    g2.setFont(DesktopUiKit.FONT_SMALL_BOLD);
                    int sw = g2.getFontMetrics().stringWidth(dlg.speaker) + 20;
                    g2.setColor(Color.WHITE);
                    g2.fillRoundRect(dx + 14, dy - 11, sw, 20, 6, 6);
                    g2.setColor(new Color(10, 10, 12));
                    g2.drawString(dlg.speaker.toUpperCase(), dx + 24, dy + 3);
                }
                g2.setFont(DesktopUiKit.FONT_UI);
                g2.setColor(new Color(248, 248, 250));
                drawWrappedText(g2, dlg.message, dx + 18, dy + 32, dw - 36, 19);
            }
        }

        // Visual Code-Tab Drag/Drop Snap Zone Highlight
        if (snapPreviewZone != null) {
            int zx = 8, zy = 8, zw = w - 16, zh = h - 16;
            String label = "Split Code";
            if ("RIGHT".equals(snapPreviewZone)) {
                zx = w / 2 + 4; zw = w / 2 - 12;
                label = "Split Right";
            } else if ("LEFT".equals(snapPreviewZone)) {
                zx = 8; zw = w / 2 - 12;
                label = "Split Left";
            } else if ("BOTTOM".equals(snapPreviewZone)) {
                zy = h / 2 + 4; zh = h / 2 - 12;
                label = "Split Bottom";
            }
            g2.setColor(new Color(255, 255, 255, 34));
            g2.fillRoundRect(zx, zy, zw, zh, 12, 12);
            g2.setColor(new Color(255, 255, 255, 225));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 6f}, 0f));
            g2.drawRoundRect(zx, zy, zw, zh, 12, 12);
            g2.setFont(DesktopUiKit.FONT_UI_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            int lw = fm.stringWidth(label);
            g2.setColor(new Color(12, 12, 15, 230));
            g2.fillRoundRect(zx + (zw - lw) / 2 - 12, zy + zh / 2 - 16, lw + 24, 30, 8, 8);
            g2.setColor(Color.WHITE);
            g2.drawString(label, zx + (zw - lw) / 2, zy + zh / 2 + 4);
        }
    }

    private void drawWrappedText(Graphics2D g2, String text, int x, int y, int maxW, int lineH) {
        FontMetrics fm = g2.getFontMetrics();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        int curY = y;
        for (String wd : words) {
            String test = line.length() == 0 ? wd : line + " " + wd;
            if (fm.stringWidth(test) > maxW && line.length() > 0) {
                g2.drawString(line.toString(), x, curY);
                line.setLength(0);
                line.append(wd);
                curY += lineH;
            } else {
                line.setLength(0);
                line.append(test);
            }
        }
        if (line.length() > 0) g2.drawString(line.toString(), x, curY);
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
        int argb = Entity.hexToArgbInt(hex, 0xFFFFFFFF);
        return new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, Math.max(0, Math.min(255, alpha)));
    }

    public void shutdown() {
        frameTimer.stop();
        audioEngine.shutdown();
        scriptEngine.dispose();
    }
}
