package com.vortex.engine.core.tween;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenAccessor;
import aurelienribon.tweenengine.TweenEquation;
import aurelienribon.tweenengine.TweenEquations;
import aurelienribon.tweenengine.TweenManager;
import com.vortex.engine.core.model.Entity;

/**
 * Pure-Java Universal Tween Engine (Apache-2.0) wrapper for Vortex Entities.
 * Enables 1-line Bounce, Elastic, Back, Sine, Quad & Expo animations with Yoyo loops!
 */
public class VortexTweenSystem implements TweenAccessor<Entity> {

    public static final int POS_XYZ   = 1;
    public static final int SCALE_XYZ = 2;
    public static final int ROT_XYZ   = 3;
    public static final int ALPHA     = 4;

    static {
        Tween.registerAccessor(Entity.class, new VortexTweenSystem());
        Tween.setCombinedAttributesLimit(3);
    }

    private final TweenManager manager = new TweenManager();

    public void reset() {
        manager.killAll();
    }

    public void update(float dt) {
        if (dt > 0f) {
            manager.update(dt);
        }
    }

    public void tweenPos(Entity e, float tx, float ty, float tz, float duration, String easeName, int yoyoCount) {
        if (e == null) return;
        manager.killTarget(e, POS_XYZ);
        Tween t = Tween.to(e, POS_XYZ, Math.max(0.05f, duration))
                .target(tx, ty, tz)
                .ease(resolveEquation(easeName));
        if (yoyoCount != 0) {
            t.repeatYoyo(yoyoCount < 0 ? Tween.INFINITY : yoyoCount, 0f);
        }
        t.start(manager);
    }

    public void tweenScale(Entity e, float sx, float sy, float sz, float duration, String easeName, int yoyoCount) {
        if (e == null) return;
        manager.killTarget(e, SCALE_XYZ);
        Tween t = Tween.to(e, SCALE_XYZ, Math.max(0.05f, duration))
                .target(sx, sy, sz)
                .ease(resolveEquation(easeName));
        if (yoyoCount != 0) {
            t.repeatYoyo(yoyoCount < 0 ? Tween.INFINITY : yoyoCount, 0f);
        }
        t.start(manager);
    }

    public void tweenRot(Entity e, float rx, float ry, float rz, float duration, String easeName, int yoyoCount) {
        if (e == null) return;
        manager.killTarget(e, ROT_XYZ);
        Tween t = Tween.to(e, ROT_XYZ, Math.max(0.05f, duration))
                .target(rx, ry, rz)
                .ease(resolveEquation(easeName));
        if (yoyoCount != 0) {
            t.repeatYoyo(yoyoCount < 0 ? Tween.INFINITY : yoyoCount, 0f);
        }
        t.start(manager);
    }

    public static TweenEquation resolveEquation(String name) {
        if (name == null) return TweenEquations.easeInOutSine;
        switch (name.trim().toUpperCase()) {
            case "BOUNCE":
            case "BOUNCE_OUT":  return TweenEquations.easeOutBounce;
            case "ELASTIC":
            case "ELASTIC_OUT": return TweenEquations.easeOutElastic;
            case "BACK":
            case "BACK_OUT":    return TweenEquations.easeOutBack;
            case "EXPO":
            case "EXPO_OUT":    return TweenEquations.easeOutExpo;
            case "QUAD":
            case "QUAD_INOUT":  return TweenEquations.easeInOutQuad;
            case "LINEAR":      return TweenEquations.easeNone;
            case "SINE":
            case "SINE_INOUT":
            default:            return TweenEquations.easeInOutSine;
        }
    }

    @Override
    public int getValues(Entity target, int tweenType, float[] returnValues) {
        switch (tweenType) {
            case POS_XYZ:
                returnValues[0] = target.x;
                returnValues[1] = target.y;
                returnValues[2] = target.z;
                return 3;
            case SCALE_XYZ:
                returnValues[0] = target.scaleX;
                returnValues[1] = target.scaleY;
                returnValues[2] = target.scaleZ;
                return 3;
            case ROT_XYZ:
                returnValues[0] = target.rotX;
                returnValues[1] = target.rotY;
                returnValues[2] = target.rotZ;
                return 3;
            case ALPHA:
                returnValues[0] = target.alpha;
                return 1;
            default:
                return 0;
        }
    }

    @Override
    public void setValues(Entity target, int tweenType, float[] newValues) {
        switch (tweenType) {
            case POS_XYZ:
                target.x = newValues[0];
                target.y = newValues[1];
                target.z = newValues[2];
                break;
            case SCALE_XYZ:
                target.scaleX = newValues[0];
                target.scaleY = newValues[1];
                target.scaleZ = newValues[2];
                break;
            case ROT_XYZ:
                target.rotX = newValues[0];
                target.rotY = newValues[1];
                target.rotZ = newValues[2];
                break;
            case ALPHA:
                target.alpha = Math.max(0f, Math.min(1f, newValues[0]));
                break;
        }
    }
}
