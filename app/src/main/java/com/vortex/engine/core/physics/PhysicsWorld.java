package com.vortex.engine.core.physics;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.noise.FastNoiseLite;
import com.vortex.engine.core.noise.TerrainGenerator;
import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.collision.WorldManifold;
import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.contacts.Contact;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid Physics World:
 *  - 2D Mode: Powered by Open-Source JBox2D (Erin Catto's Box2D Rigid-Body Solver) + Kinematic/AABB support.
 *  - 3D Mode: 3D AABB & Sphere solver + FastNoiseLite Procedural Terrain Heightfield Collision.
 */
public class PhysicsWorld {

    public static class CollisionEvent {
        public final Entity a;
        public final Entity b;

        public CollisionEvent(Entity a, Entity b) {
            this.a = a;
            this.b = b;
        }
    }

    private final List<CollisionEvent> events = new ArrayList<>();
    private final Set<String> pairDedup = new HashSet<>();

    // JBox2D 2D Rigid-Body Simulation State
    private World box2dWorld;
    private final Map<String, Body> bodyMap = new HashMap<>();
    private String boundProjectId = null;
    private FastNoiseLite cachedTerrainNoise;
    private int cachedNoiseSeed = Integer.MIN_VALUE;
    private float cachedNoiseFreq = -1f;

    public synchronized void reset() {
        box2dWorld = null;
        bodyMap.clear();
        boundProjectId = null;
        events.clear();
        pairDedup.clear();
    }

    public synchronized void applyImpulse2D(Entity e, float impulseX, float impulseY) {
        if (e == null) return;
        Body b = bodyMap.get(e.id);
        if (b != null) {
            b.setAwake(true);
            b.applyLinearImpulse(new Vec2(impulseX, impulseY), b.getWorldCenter());
            Vec2 v = b.getLinearVelocity();
            e.vx = v.x;
            e.vy = v.y;
        } else {
            e.vx += impulseX;
            e.vy += impulseY;
        }
    }

    public synchronized List<CollisionEvent> step(GameProject project, float dt) {
        events.clear();
        pairDedup.clear();
        if (project == null || dt <= 0f) return events;

        float clampedDt = Math.min(dt, 0.05f);
        boolean mode3D = "3D".equalsIgnoreCase(project.dimensionMode);

        if (!mode3D && project.useBox2D) {
            stepJBox2D(project, clampedDt);
        } else {
            stepBuiltInSolver(project, clampedDt, mode3D);
        }

        // Remove destroyed entities safely after step
        List<Entity> ents = project.entities;
        for (int i = ents.size() - 1; i >= 0; i--) {
            Entity e = ents.get(i);
            if (!e.alive) {
                Body deadBody = bodyMap.remove(e.id);
                if (deadBody != null && box2dWorld != null) {
                    box2dWorld.destroyBody(deadBody);
                }
                ents.remove(i);
            }
        }
        return events;
    }

    // ===================== OPEN-SOURCE JBOX2D 2D RIGID-BODY PIPELINE =====================

    private void ensureBox2DWorld(GameProject project) {
        if (box2dWorld == null || !project.id.equals(boundProjectId)) {
            boundProjectId = project.id;
            bodyMap.clear();
            box2dWorld = new World(new Vec2(project.gravityX, project.gravityY));
            box2dWorld.setContactListener(new ContactListener() {
                @Override
                public void beginContact(Contact contact) {
                    handleBox2DContact(contact, true);
                }

                @Override
                public void endContact(Contact contact) {
                }

                @Override
                public void preSolve(Contact contact, Manifold oldManifold) {
                    handleBox2DContact(contact, false);
                }

                @Override
                public void postSolve(Contact contact, ContactImpulse impulse) {
                }
            });
        } else {
            box2dWorld.setGravity(new Vec2(project.gravityX, project.gravityY));
        }
    }

    private void handleBox2DContact(Contact contact, boolean recordEvent) {
        if (!contact.isTouching()) return;
        Object ua = contact.getFixtureA().getBody().getUserData();
        Object ub = contact.getFixtureB().getBody().getUserData();
        if (!(ua instanceof Entity) || !(ub instanceof Entity)) return;

        Entity a = (Entity) ua;
        Entity b = (Entity) ub;
        if (!a.alive || !b.alive) return;

        if (recordEvent) {
            recordCollision(a, b);
        }

        if (!a.isTrigger && !b.isTrigger) {
            WorldManifold wm = new WorldManifold();
            contact.getWorldManifold(wm);
            // JBox2D normal points from A to B
            if (wm.normal.y < -0.45f) {
                a.isGrounded = true;
            } else if (wm.normal.y > 0.45f) {
                b.isGrounded = true;
            }
            // Fallback vertical position check for flat sensors/edges
            if (a.y > b.y + (Math.abs(b.scaleY) * 0.35f)) a.isGrounded = true;
            if (b.y > a.y + (Math.abs(a.scaleY) * 0.35f)) b.isGrounded = true;
        }
    }

    private void stepJBox2D(GameProject project, float dt) {
        ensureBox2DWorld(project);

        Set<String> activeIds = new HashSet<>();
        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            if (!e.alive || !e.visible || "NONE".equalsIgnoreCase(e.bodyType) || "NONE".equalsIgnoreCase(e.collider)) {
                continue;
            }
            activeIds.add(e.id);
            if ("DYNAMIC".equalsIgnoreCase(e.bodyType)) {
                e.isGrounded = false;
            }

            Body b = bodyMap.get(e.id);
            if (b == null) {
                b = createBox2DBody(e);
                bodyMap.put(e.id, b);
            } else {
                // Push script-modified position/velocity into Box2D body seamlessly
                Vec2 pos = b.getPosition();
                if (e.transformDirty || Math.hypot(pos.x - e.x, pos.y - e.y) > 0.35f) {
                    b.setTransform(new Vec2(e.x, e.y), (float) Math.toRadians(e.rotZ));
                    e.transformDirty = false;
                }
                b.setLinearVelocity(new Vec2(e.vx, e.vy));
                b.setFixedRotation(e.fixedRotation);
                b.setGravityScale(e.gravityScale);
                if (!e.fixedRotation && Math.abs(e.angularVel) > 0.01f) {
                    b.setAngularVelocity((float) Math.toRadians(e.angularVel));
                }
            }
        }

        // Remove orphaned bodies
        Iterator<Map.Entry<String, Body>> it = bodyMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Body> entry = it.next();
            if (!activeIds.contains(entry.getKey())) {
                box2dWorld.destroyBody(entry.getValue());
                it.remove();
            }
        }

        // Re-detect resting contacts before stepping so jump() works smoothly on standing surfaces
        for (Contact c = box2dWorld.getContactList(); c != null; c = c.getNext()) {
            handleBox2DContact(c, false);
        }

        box2dWorld.step(dt, 8, 3);

        // Read back simulated positions, velocities & rigid-body angles!
        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            if (!e.alive) continue;
            if (e.spinZ != 0f && ("STATIC".equalsIgnoreCase(e.bodyType) || e.isTrigger)) {
                e.rotZ = (e.rotZ + e.spinZ * dt) % 360f;
            }
            Body b = bodyMap.get(e.id);
            if (b != null && !"STATIC".equalsIgnoreCase(e.bodyType)) {
                Vec2 p = b.getPosition();
                Vec2 v = b.getLinearVelocity();
                e.x = p.x;
                e.y = p.y;
                e.vx = v.x;
                e.vy = v.y;
                if (!e.fixedRotation) {
                    e.rotZ = (float) Math.toDegrees(b.getAngle());
                    e.angularVel = (float) Math.toDegrees(b.getAngularVelocity());
                } else if (e.spinZ != 0f) {
                    e.rotZ = (e.rotZ + e.spinZ * dt) % 360f;
                }
            }
        }
    }

    private Body createBox2DBody(Entity e) {
        BodyDef bd = new BodyDef();
        if ("DYNAMIC".equalsIgnoreCase(e.bodyType)) bd.type = BodyType.DYNAMIC;
        else if ("KINEMATIC".equalsIgnoreCase(e.bodyType)) bd.type = BodyType.KINEMATIC;
        else bd.type = BodyType.STATIC;

        bd.position.set(e.x, e.y);
        bd.angle = (float) Math.toRadians(e.rotZ);
        bd.fixedRotation = e.fixedRotation;
        bd.gravityScale = e.gravityScale;
        bd.linearDamping = Math.max(0f, e.drag * 2f);
        bd.userData = e;

        Body body = box2dWorld.createBody(bd);
        body.setLinearVelocity(new Vec2(e.vx, e.vy));

        FixtureDef fd = new FixtureDef();
        boolean isCircle = "SPHERE".equalsIgnoreCase(e.collider)
                || "CIRCLE".equalsIgnoreCase(e.primitive)
                || "SPRITE_COIN".equalsIgnoreCase(e.primitive);

        if (isCircle) {
            CircleShape cs = new CircleShape();
            cs.setRadius(Math.max(0.08f, Math.max(Math.abs(e.scaleX), Math.abs(e.scaleY)) * 0.5f));
            fd.shape = cs;
        } else {
            PolygonShape ps = new PolygonShape();
            ps.setAsBox(
                    Math.max(0.05f, Math.abs(e.scaleX) * 0.5f),
                    Math.max(0.05f, Math.abs(e.scaleY) * 0.5f)
            );
            fd.shape = ps;
        }

        fd.density = Math.max(0.1f, e.density);
        fd.friction = Math.max(0f, e.friction);
        fd.restitution = Math.max(0f, Math.min(1f, e.bounce));
        fd.isSensor = e.isTrigger;

        body.createFixture(fd);
        return body;
    }

    // ===================== 3D + FALLBACK SOLVER & FASTNOISELITE TERRAIN =====================

    public float sampleTerrainHeightAt(GameProject p, float wx, float wz) {
        if (p == null) return 0f;
        for (int i = 0; i < p.entities.size(); i++) {
            Entity e = p.entities.get(i);
            if (e.alive && "TERRAIN".equalsIgnoreCase(e.primitive)) {
                return TerrainGenerator.sampleWorldHeight(e, getTerrainNoise(p), wx, wz);
            }
        }
        return 0f;
    }

    private FastNoiseLite getTerrainNoise(GameProject p) {
        if (cachedTerrainNoise == null || cachedNoiseSeed != p.terrainSeed || Math.abs(cachedNoiseFreq - p.terrainFreq) > 1e-5f) {
            cachedNoiseSeed = p.terrainSeed;
            cachedNoiseFreq = p.terrainFreq;
            cachedTerrainNoise = TerrainGenerator.createConfiguredNoise(p.terrainSeed, p.terrainFreq);
        }
        return cachedTerrainNoise;
    }

    private void stepBuiltInSolver(GameProject project, float clampedDt, boolean mode3D) {
        List<Entity> ents = project.entities;
        Entity terrainEntity = null;

        for (int i = 0; i < ents.size(); i++) {
            Entity e = ents.get(i);
            if (!e.alive || !e.visible) continue;
            if (mode3D && "TERRAIN".equalsIgnoreCase(e.primitive)) {
                terrainEntity = e;
            }

            if (e.spinX != 0f) e.rotX = (e.rotX + e.spinX * clampedDt) % 360f;
            if (e.spinY != 0f) e.rotY = (e.rotY + e.spinY * clampedDt) % 360f;
            if (e.spinZ != 0f) e.rotZ = (e.rotZ + e.spinZ * clampedDt) % 360f;

            if ("DYNAMIC".equalsIgnoreCase(e.bodyType)) {
                e.isGrounded = false;
                e.vx += project.gravityX * e.gravityScale * clampedDt;
                e.vy += project.gravityY * e.gravityScale * clampedDt;
                if (mode3D) e.vz += project.gravityZ * e.gravityScale * clampedDt;

                float damp = Math.max(0f, 1.0f - e.drag * clampedDt * 8f);
                e.vx *= damp;
                if (mode3D) e.vz *= damp;

                e.x += e.vx * clampedDt;
                e.y += e.vy * clampedDt;
                if (mode3D) e.z += e.vz * clampedDt;

                // Resolve against FastNoiseLite 3D Terrain Heightfield!
                if (mode3D && terrainEntity != null && e != terrainEntity) {
                    float groundY = TerrainGenerator.sampleWorldHeight(terrainEntity, getTerrainNoise(project), e.x, e.z);
                    float halfH = Math.max(0.1f, Math.abs(e.scaleY) * 0.5f);
                    if (groundY > -9999f && (e.y - halfH) <= groundY) {
                        e.y = groundY + halfH;
                        if (e.vy < 0f) e.vy = -e.vy * e.bounce;
                        e.isGrounded = true;
                        recordCollision(e, terrainEntity);
                    }
                }
            } else if ("KINEMATIC".equalsIgnoreCase(e.bodyType)) {
                e.x += e.vx * clampedDt;
                e.y += e.vy * clampedDt;
                if (mode3D) e.z += e.vz * clampedDt;
            }
        }

        int n = ents.size();
        for (int i = 0; i < n; i++) {
            Entity a = ents.get(i);
            if (!a.alive || !a.visible || "TERRAIN".equalsIgnoreCase(a.primitive)
                    || "NONE".equalsIgnoreCase(a.collider) || "NONE".equalsIgnoreCase(a.bodyType)) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                Entity b = ents.get(j);
                if (!b.alive || !b.visible || "TERRAIN".equalsIgnoreCase(b.primitive)
                        || "NONE".equalsIgnoreCase(b.collider) || "NONE".equalsIgnoreCase(b.bodyType)) {
                    continue;
                }
                boolean aDyn = "DYNAMIC".equalsIgnoreCase(a.bodyType);
                boolean bDyn = "DYNAMIC".equalsIgnoreCase(b.bodyType);
                boolean aKin = "KINEMATIC".equalsIgnoreCase(a.bodyType);
                boolean bKin = "KINEMATIC".equalsIgnoreCase(b.bodyType);
                if (!aDyn && !bDyn && !aKin && !bKin) continue;

                if (mode3D) checkAndResolve3D(a, b, aDyn, bDyn);
                else checkAndResolve2D(a, b, aDyn, bDyn);
            }
        }
    }

    private void recordCollision(Entity a, Entity b) {
        String key = a.id.compareTo(b.id) < 0 ? (a.id + ":" + b.id) : (b.id + ":" + a.id);
        if (pairDedup.add(key)) {
            events.add(new CollisionEvent(a, b));
        }
    }

    private void checkAndResolve3D(Entity a, Entity b, boolean aDyn, boolean bDyn) {
        float ahx = Math.max(0.05f, Math.abs(a.scaleX) * 0.5f);
        float ahy = "PLANE".equalsIgnoreCase(a.primitive) ? 0.12f : Math.max(0.05f, Math.abs(a.scaleY) * 0.5f);
        float ahz = Math.max(0.05f, Math.abs(a.scaleZ) * 0.5f);

        float bhx = Math.max(0.05f, Math.abs(b.scaleX) * 0.5f);
        float bhy = "PLANE".equalsIgnoreCase(b.primitive) ? 0.12f : Math.max(0.05f, Math.abs(b.scaleY) * 0.5f);
        float bhz = Math.max(0.05f, Math.abs(b.scaleZ) * 0.5f);

        float dx = b.x - a.x;
        float px = (ahx + bhx) - Math.abs(dx);
        if (px <= 0f) return;

        float dy = b.y - a.y;
        float py = (ahy + bhy) - Math.abs(dy);
        if (py <= 0f) return;

        float dz = b.z - a.z;
        float pz = (ahz + bhz) - Math.abs(dz);
        if (pz <= 0f) return;

        recordCollision(a, b);
        if (a.isTrigger || b.isTrigger || (!aDyn && !bDyn)) return;

        float restitution = Math.max(a.bounce, b.bounce);
        if (py <= px && py <= pz) {
            float sign = dy >= 0f ? -1f : 1f;
            if (aDyn && bDyn) {
                a.y += sign * py * 0.5f;
                b.y -= sign * py * 0.5f;
            } else if (aDyn) {
                a.y += sign * py;
                if (sign > 0f) { a.isGrounded = true; a.vx *= a.friction; a.vz *= a.friction; }
                if ((sign > 0f && a.vy < 0f) || (sign < 0f && a.vy > 0f)) a.vy = -a.vy * restitution;
            } else {
                b.y -= sign * py;
                if (-sign > 0f) { b.isGrounded = true; b.vx *= b.friction; b.vz *= b.friction; }
                if ((-sign > 0f && b.vy < 0f) || (-sign < 0f && b.vy > 0f)) b.vy = -b.vy * restitution;
            }
        } else if (px <= pz) {
            float sign = dx >= 0f ? -1f : 1f;
            if (aDyn && bDyn) { a.x += sign * px * 0.5f; b.x -= sign * px * 0.5f; }
            else if (aDyn) { a.x += sign * px; a.vx = -a.vx * restitution; }
            else { b.x -= sign * px; b.vx = -b.vx * restitution; }
        } else {
            float sign = dz >= 0f ? -1f : 1f;
            if (aDyn && bDyn) { a.z += sign * pz * 0.5f; b.z -= sign * pz * 0.5f; }
            else if (aDyn) { a.z += sign * pz; a.vz = -a.vz * restitution; }
            else { b.z -= sign * pz; b.vz = -b.vz * restitution; }
        }
    }

    private void checkAndResolve2D(Entity a, Entity b, boolean aDyn, boolean bDyn) {
        float ahx = Math.max(0.05f, Math.abs(a.scaleX) * 0.5f);
        float ahy = Math.max(0.05f, Math.abs(a.scaleY) * 0.5f);
        float bhx = Math.max(0.05f, Math.abs(b.scaleX) * 0.5f);
        float bhy = Math.max(0.05f, Math.abs(b.scaleY) * 0.5f);

        float dx = b.x - a.x;
        float px = (ahx + bhx) - Math.abs(dx);
        if (px <= 0f) return;

        float dy = b.y - a.y;
        float py = (ahy + bhy) - Math.abs(dy);
        if (py <= 0f) return;

        recordCollision(a, b);
        if (a.isTrigger || b.isTrigger || (!aDyn && !bDyn)) return;

        float restitution = Math.max(a.bounce, b.bounce);
        if (py <= px) {
            float sign = dy >= 0f ? -1f : 1f;
            if (aDyn && bDyn) { a.y += sign * py * 0.5f; b.y -= sign * py * 0.5f; }
            else if (aDyn) {
                a.y += sign * py;
                if (sign > 0f) { a.isGrounded = true; a.vx *= a.friction; }
                if ((sign > 0f && a.vy < 0f) || (sign < 0f && a.vy > 0f)) a.vy = -a.vy * restitution;
            } else {
                b.y -= sign * py;
                if (-sign > 0f) { b.isGrounded = true; b.vx *= b.friction; }
                if ((-sign > 0f && b.vy < 0f) || (-sign < 0f && b.vy > 0f)) b.vy = -b.vy * restitution;
            }
        } else {
            float sign = dx >= 0f ? -1f : 1f;
            if (aDyn && bDyn) { a.x += sign * px * 0.5f; b.x -= sign * px * 0.5f; }
            else if (aDyn) { a.x += sign * px; a.vx = -a.vx * restitution; }
            else { b.x -= sign * px; b.vx = -b.vx * restitution; }
        }
    }
}
