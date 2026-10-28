package com.vortex.engine.core.ui;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.FrameLayout;
import com.vortex.engine.core.math.Vec2;
import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.render.VortexGLRenderer;

/**
 * Composite OpenGL ES 2.0/3.0 Game Viewport + Editor Camera/Gizmo Touch Controller + Play Gamepad Overlay.
 */
public class VortexGameSurfaceView extends FrameLayout {

    public interface SelectionListener {
        void onEntitySelected(Entity entity);
        void onEntityMoved(Entity entity);
    }

    public final GLSurfaceView glView;
    public final VortexGLRenderer renderer;
    public final TouchControlsOverlay controlsOverlay;

    private SelectionListener selectionListener;
    private boolean gridSnap = true;
    private boolean draggingEntity = false;
    private float prevTouchX, prevTouchY;
    private final ScaleGestureDetector scaleDetector;

    public VortexGameSurfaceView(Context context) {
        super(context);
        glView = new GLSurfaceView(context);
        glView.setEGLContextClientVersion(2);
        glView.setPreserveEGLContextOnPause(true);

        renderer = new VortexGLRenderer();
        glView.setRenderer(renderer);
        // Default to on-demand rendering in Editor mode; switch to CONTINUOUSLY only during live Play!
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

        controlsOverlay = new TouchControlsOverlay(context, renderer.scriptEngine);
        controlsOverlay.setVisibility(GONE);

        addView(glView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(controlsOverlay, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (renderer.isPlaying()) return false;
                float factor = detector.getScaleFactor();
                if (factor <= 0.01f) return false;
                GameProject p = renderer.getActiveProject();
                if (p != null && "3D".equalsIgnoreCase(p.dimensionMode)) {
                    renderer.editorDist = Math.max(3f, Math.min(85f, renderer.editorDist / factor));
                } else {
                    renderer.editor2DZoom = Math.max(4f, Math.min(65f, renderer.editor2DZoom / factor));
                }
                glView.requestRender();
                return true;
            }
        });

        glView.setOnTouchListener((v, event) -> handleViewportTouch(event));
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setGridSnap(boolean snap) {
        this.gridSnap = snap;
    }

    public boolean isGridSnap() {
        return gridSnap;
    }

    public void loadProjectForEdit(GameProject project) {
        renderer.audioEngine.stopAll();
        renderer.loadProject(project, false);
        controlsOverlay.setVisibility(GONE);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glView.requestRender();
    }

    public void startPlayMode(GameProject playCopy) {
        renderer.loadProject(playCopy, true);
        controlsOverlay.configure(playCopy.showJoystick, playCopy.showActionButtons, true);
        controlsOverlay.setVisibility(VISIBLE);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    public void stopPlayMode(GameProject editorProject) {
        renderer.audioEngine.stopAll();
        controlsOverlay.setVisibility(GONE);
        renderer.loadProject(editorProject, false);
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glView.requestRender();
    }

    private boolean handleViewportTouch(MotionEvent event) {
        if (renderer.isPlaying()) {
            // Pass world touch coordinates to VortexScript
            int act = event.getActionMasked();
            if (act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE) {
                Vec2 w = renderer.screenToWorld2D(event.getX(), event.getY());
                renderer.scriptEngine.touchActive = true;
                renderer.scriptEngine.touchWorldX = w.x;
                renderer.scriptEngine.touchWorldY = w.y;
                if (act == MotionEvent.ACTION_DOWN) {
                    renderer.scriptEngine.callHook("onTouch", (double) w.x, (double) w.y);
                }
            } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
                renderer.scriptEngine.touchActive = false;
            }
            return true;
        }

        // Editor Mode: Pinch Zoom + Orbit/Pan + Entity Tap-Select & Drag
        scaleDetector.onTouchEvent(event);
        int ptrCount = event.getPointerCount();
        int action = event.getActionMasked();

        if (ptrCount >= 2) {
            draggingEntity = false;
            float cx = (event.getX(0) + event.getX(1)) * 0.5f;
            float cy = (event.getY(0) + event.getY(1)) * 0.5f;
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = cx - prevTouchX;
                float dy = cy - prevTouchY;
                GameProject p = renderer.getActiveProject();
                if (p != null && "3D".equalsIgnoreCase(p.dimensionMode)) {
                    renderer.editorTargetX -= dx * 0.02f;
                    renderer.editorTargetZ -= dy * 0.02f;
                } else {
                    renderer.editor2DCamX -= dx * 0.025f;
                    renderer.editor2DCamY += dy * 0.025f;
                }
            }
            prevTouchX = cx;
            prevTouchY = cy;
            glView.requestRender();
            return true;
        }

        float tx = event.getX();
        float ty = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                prevTouchX = tx;
                prevTouchY = ty;
                Entity hit = renderer.pickEntityAtScreen(tx, ty);
                if (hit != null) {
                    boolean alreadySelected = hit.id.equals(renderer.getSelectedEntityId());
                    renderer.setSelectedEntityId(hit.id);
                    draggingEntity = alreadySelected;
                    if (selectionListener != null) selectionListener.onEntitySelected(hit);
                } else {
                    draggingEntity = false;
                }
                glView.requestRender();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = tx - prevTouchX;
                float dy = ty - prevTouchY;
                if (draggingEntity && renderer.getSelectedEntityId() != null) {
                    renderer.dragSelectedEntityToScreen(tx, ty, gridSnap);
                    GameProject p = renderer.getActiveProject();
                    if (p != null && selectionListener != null) {
                        Entity sel = p.findById(renderer.getSelectedEntityId());
                        if (sel != null) selectionListener.onEntityMoved(sel);
                    }
                } else {
                    GameProject p = renderer.getActiveProject();
                    if (p != null && "3D".equalsIgnoreCase(p.dimensionMode)) {
                        renderer.editorYawDeg -= dx * 0.32f;
                        renderer.editorPitchDeg = Math.max(-75f, Math.min(82f, renderer.editorPitchDeg + dy * 0.28f));
                    } else {
                        float scale = renderer.editor2DZoom / Math.max(200f, getHeight());
                        renderer.editor2DCamX -= dx * scale;
                        renderer.editor2DCamY += dy * scale;
                    }
                }
                prevTouchX = tx;
                prevTouchY = ty;
                glView.requestRender();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                draggingEntity = false;
                return true;
            }
        }
        glView.requestRender();
        return true;
    }

    public void requestEditorRender() {
        glView.requestRender();
    }

    public void onResume() {
        glView.onResume();
        if (!renderer.isPlaying()) glView.requestRender();
    }

    public void onPause() {
        renderer.audioEngine.stopAll();
        glView.onPause();
    }

    public void onDestroy() {
        renderer.shutdown();
    }
}
