package com.vortex.engine.core.render;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import com.vortex.engine.core.audio.SynthAudioEngine;
import com.vortex.engine.core.math.Mat4;
import com.vortex.engine.core.math.Ray3D;
import com.vortex.engine.core.math.Vec2;
import com.vortex.engine.core.math.Vec3;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.physics.PhysicsWorld;
import com.vortex.engine.core.script.VortexScriptEngine;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class VortexGLRenderer implements GLSurfaceView.Renderer {

    public interface TelemetryListener {
        void onHudUpdate(int fps, int entityCount, int score, int health, String banner);
        void onScriptLog(String line);
    }

    private ShaderProgram lit3DShader;
    private ShaderProgram flatShader;

    private Mesh3D cubeMesh;
    private Mesh3D sphereMesh;
    private Mesh3D pyramidMesh;
    private Mesh3D cylinderMesh;
    private Mesh3D planeMesh;
    private final Map<String, Mesh3D> customMeshCache = new HashMap<>();

    // Dynamic line/shape scratch buffer (pos3 + col4 = 7 floats per vertex)
    private static final int SCRATCH_MAX_VERTS = 1024;
    private final float[] scratchVerts = new float[SCRATCH_MAX_VERTS * 7];
    private FloatBuffer scratchBuffer;

    private volatile GameProject activeProject;
    private volatile boolean playing = false;
    private volatile String selectedEntityId = null;

    // Editor camera controls
    public volatile float editorYawDeg = 35f;
    public volatile float editorPitchDeg = 28f;
    public volatile float editorDist = 18f;
    public volatile float editorTargetX = 0f;
    public volatile float editorTargetY = 0.5f;
    public volatile float editorTargetZ = 0f;

    public volatile float editor2DCamX = 0f;
    public volatile float editor2DCamY = 2f;
    public volatile float editor2DZoom = 18f;

    // Runtime play camera state
    private float playCamX = 0f, playCamY = 6f, playCamZ = 12f;
    private float playLookX = 0f, playLookY = 1f, playLookZ = 0f;
    private float shakeIntensity = 0f;
    private float shakeTimer = 0f;
    private final Random shakeRng = new Random();

    private int viewportW = 1;
    private int viewportH = 1;

    private final float[] projMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] vpMatrix = new float[16];
    private final float[] lastFrameVP = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] mvpMatrix = new float[16];

    public final PhysicsWorld physicsWorld = new PhysicsWorld();
    public final ParticleSystem particleSystem = new ParticleSystem();
    public final VortexScriptEngine scriptEngine = new VortexScriptEngine();
    public final SynthAudioEngine audioEngine = new SynthAudioEngine();

    private TelemetryListener telemetryListener;
    private long lastFrameNs = 0L;
    private int frameCounter = 0;
    private long fpsTimerNs = 0L;
    private volatile int currentFps = 60;

    public VortexGLRenderer() {
        ByteBuffer bb = ByteBuffer.allocateDirect(scratchVerts.length * 4);
        bb.order(ByteOrder.nativeOrder());
        scratchBuffer = bb.asFloatBuffer();
    }

    public void setTelemetryListener(TelemetryListener listener) {
        this.telemetryListener = listener;
    }

    public synchronized void loadProject(GameProject project, boolean startPlaying) {
        this.activeProject = project;
        this.customMeshCache.clear();
        this.particleSystem.clear();
        this.playing = startPlaying;
        this.lastFrameNs = System.nanoTime();

        scriptEngine.attach(project, particleSystem, audioEngine, new VortexScriptEngine.HostBridge() {
            @Override
            public void onLog(String message) {
                if (telemetryListener != null) telemetryListener.onScriptLog(message);
            }

            @Override
            public void onCameraShake(float intensity, float duration) {
                shakeIntensity = intensity;
                shakeTimer = duration;
            }
        });

        if (project != null) {
            editor2DZoom = project.zoom2D;
            Entity target = project.findByName(project.cameraTargetName);
            if (target != null) {
                playLookX = target.x;
                playLookY = target.y;
                playLookZ = target.z;
                playCamX = target.x;
                playCamY = target.y + project.cameraHeight;
                playCamZ = target.z + project.cameraDistance;
            }
            if (startPlaying) {
                scriptEngine.compileAndInit(project.scriptCode);
            }
        }
    }

    public GameProject getActiveProject() {
        return activeProject;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setSelectedEntityId(String id) {
        this.selectedEntityId = id;
    }

    public String getSelectedEntityId() {
        return selectedEntityId;
    }

    public void resetEditorCamera() {
        editorYawDeg = 35f;
        editorPitchDeg = 28f;
        editorDist = 18f;
        editorTargetX = 0f;
        editorTargetY = 0.5f;
        editorTargetZ = 0f;
        editor2DCamX = 0f;
        editor2DCamY = 2f;
        if (activeProject != null) editor2DZoom = activeProject.zoom2D;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        lit3DShader = new ShaderProgram(ShaderProgram.LIT_3D_VERT, ShaderProgram.LIT_3D_FRAG);
        flatShader = new ShaderProgram(ShaderProgram.FLAT_VERT, ShaderProgram.FLAT_FRAG);

        cubeMesh = Mesh3D.createCube();
        sphereMesh = Mesh3D.createSphere(16, 20);
        pyramidMesh = Mesh3D.createPyramid();
        cylinderMesh = Mesh3D.createCylinder(20);
        planeMesh = Mesh3D.createPlane();
        customMeshCache.clear();
        lastFrameNs = System.nanoTime();
        fpsTimerNs = lastFrameNs;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewportW = Math.max(1, width);
        viewportH = Math.max(1, height);
        GLES20.glViewport(0, 0, viewportW, viewportH);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNs) / 1_000_000_000.0f;
        lastFrameNs = now;
        if (dt < 0f || dt > 0.1f) dt = 0.016f;

        frameCounter++;
        if (now - fpsTimerNs >= 400_000_000L) {
            currentFps = Math.round(frameCounter * 1_000_000_000f / Math.max(1L, now - fpsTimerNs));
            frameCounter = 0;
            fpsTimerNs = now;
            GameProject p = activeProject;
            if (telemetryListener != null && p != null) {
                telemetryListener.onHudUpdate(
                        currentFps,
                        p.entities.size(),
                        scriptEngine.score,
                        scriptEngine.health,
                        scriptEngine.hudBanner
                );
            }
        }

        GameProject project;
        synchronized (this) {
            project = this.activeProject;
            if (project == null) {
                GLES20.glClearColor(0.05f, 0.07f, 0.11f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
                return;
            }

            if (playing) {
                scriptEngine.update(dt);
                List<PhysicsWorld.CollisionEvent> cols = physicsWorld.step(project, dt);
                for (int i = 0; i < cols.size(); i++) {
                    PhysicsWorld.CollisionEvent ce = cols.get(i);
                    scriptEngine.notifyCollision(ce.a, ce.b);
                }
                particleSystem.update(dt, project.gravityY);
            }
        }

        float[] sky = Entity.hexToRgba(project.skyColorHex, 1f);
        GLES20.glClearColor(sky[0], sky[1], sky[2], 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        boolean is3D = "3D".equalsIgnoreCase(project.dimensionMode);
        if (is3D) {
            render3DScene(project, dt, sky);
        } else {
            render2DScene(project, dt);
        }
    }

    private void render3DScene(GameProject project, float dt, float[] sky) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        float aspect = (float) viewportW / (float) viewportH;
        Matrix.perspectiveM(projMatrix, 0, Math.max(30f, Math.min(100f, project.cameraFov)), aspect, 0.2f, 220f);

        float camX, camY, camZ, lookX, lookY, lookZ;
        if (!playing) {
            double yawRad = Math.toRadians(editorYawDeg);
            double pitchRad = Math.toRadians(Math.max(-80f, Math.min(80f, editorPitchDeg)));
            float horiz = (float) (Math.cos(pitchRad) * editorDist);
            camX = editorTargetX + (float) (Math.sin(yawRad) * horiz);
            camY = editorTargetY + (float) (Math.sin(pitchRad) * editorDist);
            camZ = editorTargetZ + (float) (Math.cos(yawRad) * horiz);
            lookX = editorTargetX;
            lookY = editorTargetY;
            lookZ = editorTargetZ;
        } else {
            Entity target = project.findByName(project.cameraTargetName);
            if (target != null && "FIRST_PERSON".equalsIgnoreCase(project.cameraMode)) {
                double r = Math.toRadians(target.rotY);
                camX = target.x;
                camY = target.y + target.scaleY * 0.4f;
                camZ = target.z;
                lookX = camX - (float) Math.sin(r) * 10f;
                lookY = camY;
                lookZ = camZ - (float) Math.cos(r) * 10f;
            } else if (target != null && !"FIXED".equalsIgnoreCase(project.cameraMode)) {
                float desiredLookX = target.x;
                float desiredLookY = target.y + 0.6f;
                float desiredLookZ = target.z;
                float desiredCamX = target.x;
                float desiredCamY = target.y + project.cameraHeight;
                float desiredCamZ = target.z + project.cameraDistance;

                float smooth = Math.min(1f, dt * 7.5f);
                playLookX += (desiredLookX - playLookX) * smooth;
                playLookY += (desiredLookY - playLookY) * smooth;
                playLookZ += (desiredLookZ - playLookZ) * smooth;
                playCamX += (desiredCamX - playCamX) * smooth;
                playCamY += (desiredCamY - playCamY) * smooth;
                playCamZ += (desiredCamZ - playCamZ) * smooth;

                camX = playCamX; camY = playCamY; camZ = playCamZ;
                lookX = playLookX; lookY = playLookY; lookZ = playLookZ;
            } else {
                camX = 0f; camY = project.cameraHeight + 3f; camZ = project.cameraDistance + 4f;
                lookX = 0f; lookY = 0f; lookZ = 0f;
            }

            if (shakeTimer > 0f) {
                shakeTimer -= dt;
                camX += (shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity;
                camY += (shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity;
            }
        }

        Matrix.setLookAtM(viewMatrix, 0, camX, camY, camZ, lookX, lookY, lookZ, 0f, 1f, 0f);
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0);
        System.arraycopy(vpMatrix, 0, lastFrameVP, 0, 16);

        if (!playing || project.showGrid) {
            draw3DGrid();
        }

        lit3DShader.use();
        float[] amb = Entity.hexToRgba(project.ambientColorHex, 1f);
        GLES20.glUniform3f(lit3DShader.uniform("uLightDir"), 0.45f, 0.85f, 0.52f);
        GLES20.glUniform3f(lit3DShader.uniform("uAmbient"), amb[0] + 0.18f, amb[1] + 0.18f, amb[2] + 0.18f);
        GLES20.glUniform3f(lit3DShader.uniform("uSkyColor"), sky[0], sky[1], sky[2]);
        GLES20.glUniform3f(lit3DShader.uniform("uCamPos"), camX, camY, camZ);
        GLES20.glUniform3f(lit3DShader.uniform("uFogParams"),
                project.fogEnabled ? 1f : 0f, project.fogNear, project.fogFar);

        int aPos = lit3DShader.attr("aPosition");
        int aNorm = lit3DShader.attr("aNormal");
        int aUV = lit3DShader.attr("aUV");
        int uMVP = lit3DShader.uniform("uMVP");
        int uModel = lit3DShader.uniform("uModel");
        int uColor = lit3DShader.uniform("uColor");
        int uSecColor = lit3DShader.uniform("uSecondaryColor");
        int uChecker = lit3DShader.uniform("uChecker");
        int uEmissive = lit3DShader.uniform("uEmissive");

        Entity selected = null;
        synchronized (this) {
            for (int i = 0; i < project.entities.size(); i++) {
                Entity e = project.entities.get(i);
                if (!e.alive || !e.visible) continue;
                if (e.id.equals(selectedEntityId)) selected = e;

                Mat4.buildModelMatrix(modelMatrix, e.x, e.y, e.z, e.rotX, e.rotY, e.rotZ, e.scaleX, e.scaleY, e.scaleZ);
                Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);

                float[] col = e.parseColorRgba();
                float[] sec = e.parseSecondaryRgba();

                GLES20.glUniformMatrix4fv(uModel, 1, false, modelMatrix, 0);
                GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0);
                GLES20.glUniform4fv(uColor, 1, col, 0);
                GLES20.glUniform4fv(uSecColor, 1, sec, 0);
                GLES20.glUniform1f(uChecker, e.checkerTexture ? 1f : 0f);
                GLES20.glUniform1f(uEmissive, e.emissive ? 1f : 0f);

                Mesh3D mesh = resolveMesh(project, e);
                mesh.draw(aPos, aNorm, aUV);
            }
        }

        if (!playing && selected != null) {
            draw3DSelectionBoxAndGizmo(selected);
        }

        particleSystem.draw(flatShader, vpMatrix, 9.5f);
    }

    private Mesh3D resolveMesh(GameProject project, Entity e) {
        if (e.primitive == null) return cubeMesh;
        switch (e.primitive.toUpperCase()) {
            case "SPHERE": return sphereMesh;
            case "PYRAMID": return pyramidMesh;
            case "CYLINDER": return cylinderMesh;
            case "PLANE": return planeMesh;
            case "CUSTOM_OBJ": {
                String key = (e.customObjName == null || e.customObjName.isEmpty()) ? "default" : e.customObjName;
                Mesh3D cached = customMeshCache.get(key);
                if (cached == null) {
                    String objText = project.customObjMeshes.get(key);
                    if (objText == null && !project.customObjMeshes.isEmpty()) {
                        objText = project.customObjMeshes.values().iterator().next();
                    }
                    cached = ObjLoader.parse(objText);
                    customMeshCache.put(key, cached);
                }
                return cached;
            }
            default: return cubeMesh;
        }
    }

    private void render2DScene(GameProject project, float dt) {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        float aspect = (float) viewportW / (float) viewportH;
        float halfH = Math.max(3f, (playing ? project.zoom2D : editor2DZoom) * 0.5f);
        float halfW = halfH * aspect;

        float cx = editor2DCamX;
        float cy = editor2DCamY;
        if (playing) {
            Entity target = project.findByName(project.cameraTargetName);
            if (target != null && !"FIXED".equalsIgnoreCase(project.cameraMode)) {
                float smooth = Math.min(1f, dt * 8f);
                playCamX += (target.x - playCamX) * smooth;
                playCamY += (target.y - playCamY) * smooth;
                cx = playCamX;
                cy = playCamY;
            } else {
                cx = 0f;
                cy = 0f;
            }
            if (shakeTimer > 0f) {
                shakeTimer -= dt;
                cx += (shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity;
                cy += (shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity;
            }
        }

        Matrix.orthoM(vpMatrix, 0, cx - halfW, cx + halfW, cy - halfH, cy + halfH, -10f, 10f);
        System.arraycopy(vpMatrix, 0, lastFrameVP, 0, 16);

        if (!playing || project.showGrid) {
            draw2DGrid(cx, cy, halfW, halfH);
        }

        Entity selected = null;
        synchronized (this) {
            for (int i = 0; i < project.entities.size(); i++) {
                Entity e = project.entities.get(i);
                if (!e.alive || !e.visible) continue;
                if (e.id.equals(selectedEntityId)) selected = e;
                draw2DEntity(e);
            }
        }

        if (!playing && selected != null) {
            draw2DSelectionOutline(selected);
        }

        particleSystem.draw(flatShader, vpMatrix, 11.0f);
    }

    private void draw2DEntity(Entity e) {
        Mat4.buildModelMatrix(modelMatrix, e.x, e.y, 0f, 0f, 0f, e.rotZ, e.scaleX, e.scaleY, 1f);
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0);

        float[] c = e.parseColorRgba();
        String prim = e.primitive == null ? "RECT" : e.primitive.toUpperCase();

        int vCount = 0;
        switch (prim) {
            case "CIRCLE":
            case "SPRITE_COIN": {
                int segs = 20;
                for (int i = 0; i < segs; i++) {
                    double a0 = 2.0 * Math.PI * i / segs;
                    double a1 = 2.0 * Math.PI * (i + 1) / segs;
                    vCount = putScratchVert(vCount, 0f, 0f, 0f, c[0], c[1], c[2], c[3]);
                    vCount = putScratchVert(vCount, (float) Math.cos(a0) * 0.5f, (float) Math.sin(a0) * 0.5f, 0f, c[0] * 0.85f, c[1] * 0.85f, c[2] * 0.85f, c[3]);
                    vCount = putScratchVert(vCount, (float) Math.cos(a1) * 0.5f, (float) Math.sin(a1) * 0.5f, 0f, c[0] * 0.85f, c[1] * 0.85f, c[2] * 0.85f, c[3]);
                }
                if ("SPRITE_COIN".equals(prim)) {
                    vCount = putQuad2D(vCount, -0.09f, -0.22f, 0.09f, 0.22f, 1f, 0.98f, 0.75f, c[3]);
                }
                break;
            }
            case "TRIANGLE":
            case "SPRITE_SHIP": {
                vCount = putScratchVert(vCount, 0f, 0.5f, 0f, c[0], c[1], c[2], c[3]);
                vCount = putScratchVert(vCount, -0.48f, -0.48f, 0f, c[0] * 0.7f, c[1] * 0.7f, c[2] * 0.7f, c[3]);
                vCount = putScratchVert(vCount, 0.48f, -0.48f, 0f, c[0] * 0.7f, c[1] * 0.7f, c[2] * 0.7f, c[3]);
                if ("SPRITE_SHIP".equals(prim)) {
                    // Glowing cockpit visor
                    vCount = putQuad2D(vCount, -0.12f, -0.08f, 0.12f, 0.22f, 0.85f, 0.98f, 1.0f, c[3]);
                }
                break;
            }
            case "SPRITE_HERO": {
                // Body + visor eyes
                vCount = putQuad2D(vCount, -0.45f, -0.5f, 0.45f, 0.45f, c[0], c[1], c[2], c[3]);
                vCount = putQuad2D(vCount, -0.30f, 0.08f, -0.08f, 0.28f, 1f, 1f, 1f, c[3]);
                vCount = putQuad2D(vCount, 0.08f, 0.08f, 0.30f, 0.28f, 1f, 1f, 1f, c[3]);
                break;
            }
            case "SPRITE_SLIME": {
                vCount = putQuad2D(vCount, -0.48f, -0.5f, 0.48f, 0.28f, c[0], c[1], c[2], c[3]);
                vCount = putQuad2D(vCount, -0.35f, 0.28f, 0.35f, 0.46f, c[0], c[1], c[2], c[3]);
                vCount = putQuad2D(vCount, -0.26f, -0.05f, -0.08f, 0.14f, 1f, 0.2f, 0.2f, c[3]);
                vCount = putQuad2D(vCount, 0.08f, -0.05f, 0.26f, 0.14f, 1f, 0.2f, 0.2f, c[3]);
                break;
            }
            default: {
                vCount = putQuad2D(vCount, -0.5f, -0.5f, 0.5f, 0.5f, c[0], c[1], c[2], c[3]);
                if (e.checkerTexture) {
                    float[] s = e.parseSecondaryRgba();
                    vCount = putQuad2D(vCount, -0.46f, 0.32f, 0.46f, 0.48f, s[0], s[1], s[2], c[3]);
                }
                break;
            }
        }
        flushScratch(mvpMatrix, vCount, GLES20.GL_TRIANGLES, 1f);
    }

    private int putQuad2D(int vc, float x0, float y0, float x1, float y1, float r, float g, float b, float a) {
        vc = putScratchVert(vc, x0, y0, 0f, r, g, b, a);
        vc = putScratchVert(vc, x1, y0, 0f, r, g, b, a);
        vc = putScratchVert(vc, x1, y1, 0f, r, g, b, a);
        vc = putScratchVert(vc, x0, y0, 0f, r, g, b, a);
        vc = putScratchVert(vc, x1, y1, 0f, r, g, b, a);
        vc = putScratchVert(vc, x0, y1, 0f, r, g, b, a);
        return vc;
    }

    private void draw3DGrid() {
        int vc = 0;
        int half = 20;
        for (int i = -half; i <= half; i += 2) {
            boolean center = (i == 0);
            float r = center ? 0.0f : 0.18f;
            float g = center ? 0.72f : 0.25f;
            float b = center ? 0.90f : 0.36f;
            float a = center ? 0.65f : 0.35f;

            vc = putScratchVert(vc, i, 0.01f, -half, r, g, b, a);
            vc = putScratchVert(vc, i, 0.01f, half, r, g, b, a);

            vc = putScratchVert(vc, -half, 0.01f, i, r, g, b, a);
            vc = putScratchVert(vc, half, 0.01f, i, r, g, b, a);
        }
        flushScratch(vpMatrix, vc, GLES20.GL_LINES, 1f);
    }

    private void draw2DGrid(float cx, float cy, float halfW, float halfH) {
        int vc = 0;
        int startX = (int) Math.floor(cx - halfW) - 1;
        int endX = (int) Math.ceil(cx + halfW) + 1;
        int startY = (int) Math.floor(cy - halfH) - 1;
        int endY = (int) Math.ceil(cy + halfH) + 1;

        for (int x = startX; x <= endX; x += 2) {
            boolean zero = (x == 0);
            vc = putScratchVert(vc, x, startY, 0f, 0.16f, zero ? 0.65f : 0.23f, zero ? 0.8f : 0.34f, zero ? 0.55f : 0.25f);
            vc = putScratchVert(vc, x, endY, 0f, 0.16f, zero ? 0.65f : 0.23f, zero ? 0.8f : 0.34f, zero ? 0.55f : 0.25f);
        }
        for (int y = startY; y <= endY; y += 2) {
            boolean zero = (y == 0);
            vc = putScratchVert(vc, startX, y, 0f, zero ? 0.65f : 0.16f, 0.23f, zero ? 0.8f : 0.34f, zero ? 0.55f : 0.25f);
            vc = putScratchVert(vc, endX, y, 0f, zero ? 0.65f : 0.16f, 0.23f, zero ? 0.8f : 0.34f, zero ? 0.55f : 0.25f);
        }
        flushScratch(vpMatrix, vc, GLES20.GL_LINES, 1f);
    }

    private void draw3DSelectionBoxAndGizmo(Entity e) {
        float hx = Math.abs(e.scaleX) * 0.54f;
        float hy = Math.max(0.15f, Math.abs(e.scaleY) * 0.54f);
        float hz = Math.abs(e.scaleZ) * 0.54f;
        float x0 = e.x - hx, x1 = e.x + hx;
        float y0 = e.y - hy, y1 = e.y + hy;
        float z0 = e.z - hz, z1 = e.z + hz;

        int vc = 0;
        float r = 0f, g = 0.95f, b = 1f, a = 1f;
        // Bottom loop
        vc = putLine(vc, x0, y0, z0, x1, y0, z0, r, g, b, a);
        vc = putLine(vc, x1, y0, z0, x1, y0, z1, r, g, b, a);
        vc = putLine(vc, x1, y0, z1, x0, y0, z1, r, g, b, a);
        vc = putLine(vc, x0, y0, z1, x0, y0, z0, r, g, b, a);
        // Top loop
        vc = putLine(vc, x0, y1, z0, x1, y1, z0, r, g, b, a);
        vc = putLine(vc, x1, y1, z0, x1, y1, z1, r, g, b, a);
        vc = putLine(vc, x1, y1, z1, x0, y1, z1, r, g, b, a);
        vc = putLine(vc, x0, y1, z1, x0, y1, z0, r, g, b, a);
        // Verticals
        vc = putLine(vc, x0, y0, z0, x0, y1, z0, r, g, b, a);
        vc = putLine(vc, x1, y0, z0, x1, y1, z0, r, g, b, a);
        vc = putLine(vc, x1, y0, z1, x1, y1, z1, r, g, b, a);
        vc = putLine(vc, x0, y0, z1, x0, y1, z1, r, g, b, a);

        // X/Y/Z Axis Gizmo arrows
        float gl = Math.max(1.5f, Math.max(hx, hz) + 1.1f);
        vc = putLine(vc, e.x, e.y, e.z, e.x + gl, e.y, e.z, 1f, 0.25f, 0.35f, 1f); // +X Red
        vc = putLine(vc, e.x, e.y, e.z, e.x, e.y + gl, e.z, 0.2f, 1f, 0.45f, 1f);  // +Y Green
        vc = putLine(vc, e.x, e.y, e.z, e.x, e.y, e.z + gl, 0.25f, 0.6f, 1f, 1f);  // +Z Blue

        GLES20.glLineWidth(3.0f);
        flushScratch(vpMatrix, vc, GLES20.GL_LINES, 1f);
        GLES20.glLineWidth(1.0f);
    }

    private void draw2DSelectionOutline(Entity e) {
        float hx = Math.abs(e.scaleX) * 0.55f;
        float hy = Math.abs(e.scaleY) * 0.55f;
        int vc = 0;
        vc = putLine(vc, e.x - hx, e.y - hy, 0f, e.x + hx, e.y - hy, 0f, 0f, 0.95f, 1f, 1f);
        vc = putLine(vc, e.x + hx, e.y - hy, 0f, e.x + hx, e.y + hy, 0f, 0f, 0.95f, 1f, 1f);
        vc = putLine(vc, e.x + hx, e.y + hy, 0f, e.x - hx, e.y + hy, 0f, 0f, 0.95f, 1f, 1f);
        vc = putLine(vc, e.x - hx, e.y + hy, 0f, e.x - hx, e.y - hy, 0f, 0f, 0.95f, 1f, 1f);
        GLES20.glLineWidth(3.0f);
        flushScratch(vpMatrix, vc, GLES20.GL_LINES, 1f);
        GLES20.glLineWidth(1.0f);
    }

    private int putLine(int vc, float x0, float y0, float z0, float x1, float y1, float z1,
                        float r, float g, float b, float a) {
        vc = putScratchVert(vc, x0, y0, z0, r, g, b, a);
        vc = putScratchVert(vc, x1, y1, z1, r, g, b, a);
        return vc;
    }

    private int putScratchVert(int vc, float x, float y, float z, float r, float g, float b, float a) {
        if (vc >= SCRATCH_MAX_VERTS) return vc;
        int base = vc * 7;
        scratchVerts[base] = x;
        scratchVerts[base + 1] = y;
        scratchVerts[base + 2] = z;
        scratchVerts[base + 3] = r;
        scratchVerts[base + 4] = g;
        scratchVerts[base + 5] = b;
        scratchVerts[base + 6] = a;
        return vc + 1;
    }

    private void flushScratch(float[] mvp, int vertCount, int mode, float ptSize) {
        if (vertCount <= 0) return;
        scratchBuffer.position(0);
        scratchBuffer.put(scratchVerts, 0, vertCount * 7);

        flatShader.use();
        GLES20.glUniformMatrix4fv(flatShader.uniform("uMVP"), 1, false, mvp, 0);
        GLES20.glUniform1f(flatShader.uniform("uPointSize"), ptSize);

        int aPos = flatShader.attr("aPosition");
        int aCol = flatShader.attr("aColor");
        int stride = 7 * 4;

        scratchBuffer.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, scratchBuffer);

        scratchBuffer.position(3);
        GLES20.glEnableVertexAttribArray(aCol);
        GLES20.glVertexAttribPointer(aCol, 4, GLES20.GL_FLOAT, false, stride, scratchBuffer);

        GLES20.glDrawArrays(mode, 0, vertCount);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aCol);
    }

    // ===================== EDITOR RAYCAST PICKING & DRAGGING =====================

    public synchronized Entity pickEntityAtScreen(float touchX, float touchY) {
        GameProject p = activeProject;
        if (p == null) return null;

        if ("3D".equalsIgnoreCase(p.dimensionMode)) {
            Ray3D ray = Mat4.unprojectTouchToRay(touchX, touchY, viewportW, viewportH, lastFrameVP);
            Entity best = null;
            float bestDist = Float.MAX_VALUE;
            for (Entity e : p.entities) {
                if (!e.alive || !e.visible) continue;
                float hx = Math.max(0.25f, Math.abs(e.scaleX) * 0.5f);
                float hy = Math.max(0.25f, Math.abs(e.scaleY) * 0.5f);
                float hz = Math.max(0.25f, Math.abs(e.scaleZ) * 0.5f);
                float t = ray.intersectAABB(e.x - hx, e.y - hy, e.z - hz, e.x + hx, e.y + hy, e.z + hz);
                if (t >= 0f && t < bestDist) {
                    bestDist = t;
                    best = e;
                }
            }
            return best;
        } else {
            Vec2 w = screenToWorld2D(touchX, touchY);
            for (int i = p.entities.size() - 1; i >= 0; i--) {
                Entity e = p.entities.get(i);
                if (!e.alive || !e.visible) continue;
                float hx = Math.max(0.3f, Math.abs(e.scaleX) * 0.5f);
                float hy = Math.max(0.3f, Math.abs(e.scaleY) * 0.5f);
                if (w.x >= e.x - hx && w.x <= e.x + hx && w.y >= e.y - hy && w.y <= e.y + hy) {
                    return e;
                }
            }
            return null;
        }
    }

    public synchronized void dragSelectedEntityToScreen(float touchX, float touchY, boolean snapGrid) {
        GameProject p = activeProject;
        if (p == null || selectedEntityId == null) return;
        Entity e = p.findById(selectedEntityId);
        if (e == null) return;

        if ("3D".equalsIgnoreCase(p.dimensionMode)) {
            Ray3D ray = Mat4.unprojectTouchToRay(touchX, touchY, viewportW, viewportH, lastFrameVP);
            Vec3 hit = ray.intersectPlaneY(e.y);
            if (hit != null) {
                e.x = snapGrid ? Math.round(hit.x * 2f) / 2f : hit.x;
                e.z = snapGrid ? Math.round(hit.z * 2f) / 2f : hit.z;
            }
        } else {
            Vec2 w = screenToWorld2D(touchX, touchY);
            e.x = snapGrid ? Math.round(w.x * 2f) / 2f : w.x;
            e.y = snapGrid ? Math.round(w.y * 2f) / 2f : w.y;
        }
    }

    public Vec2 screenToWorld2D(float touchX, float touchY) {
        float aspect = (float) viewportW / Math.max(1f, (float) viewportH);
        float halfH = Math.max(3f, (playing && activeProject != null ? activeProject.zoom2D : editor2DZoom) * 0.5f);
        float halfW = halfH * aspect;
        float nx = (touchX / Math.max(1f, viewportW)) * 2f - 1f;
        float ny = 1f - (touchY / Math.max(1f, viewportH)) * 2f;
        float cx = playing ? playCamX : editor2DCamX;
        float cy = playing ? playCamY : editor2DCamY;
        return new Vec2(cx + nx * halfW, cy + ny * halfH);
    }

    public void shutdown() {
        audioEngine.shutdown();
    }
}
