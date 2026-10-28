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
import org.jbox2d.dynamics.Filter;
import org.jbox2d.dynamics.Fixture;
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
 * Vortex Physics World v4.4 — Unreal Chaos / Box2D Inspired Hybrid 2D & 3D Rigid-Body Solver:
 *  - 2D Mode: JBox2D Rigid-Body Solver with CCD (Bullet TOI), Collision Layer/Mask Filters & Coyote-Time Grounding.
 *  - 3D Mode: Substepped (4x–8x CCD) Impulse-Conserving Sphere/Capsule/AABB Manifold Solver,
 *             Coulomb Tangential Friction, Baumgarte Positional Slop Correction,
 *             FastNoiseLite 3D Terrain Slope-Normal Deflection, Line-Trace Raycasts & Radial Impulses.
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

    public static final class RaycastHit {
        public final Entity entity;
        public final float distance;
        public final float hitX, hitY, hitZ;
        public final float normalX, normalY, normalZ;

        public RaycastHit(Entity entity, float distance,
                          float hitX, float hitY, float hitZ,
                          float normalX, float normalY, float normalZ) {
            this.entity = entity;
            this.distance = distance;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
            this.normalX = normalX;
            this.normalY = normalY;
            this.normalZ = normalZ;
        }
    }

    private static final float COYOTE_GRACE_SEC = 0.11f;
    private static final float BAUMGARTE_PERCENT = 0.82f;
    private static final float PENETRATION_SLOP = 0.005f;

    private final List<CollisionEvent> events = new ArrayList<>();
    private final Set<String> pairDedup = new HashSet<>();

    // JBox2D 2D Rigid-Body Simulation State
    private World box2dWorld;
    private final Map<String, Body> bodyMap = new HashMap<>();
    private final Map<String, Float> bodyScaleSig = new HashMap<>();
    private String boundProjectId = null;
    private FastNoiseLite cachedTerrainNoise;
    private int cachedNoiseSeed = Integer.MIN_VALUE;
    private float cachedNoiseFreq = -1f;
    private float animClock = 0f;

    public synchronized void reset() {
        box2dWorld = null;
        bodyMap.clear();
        bodyScaleSig.clear();
        boundProjectId = null;
        events.clear();
        pairDedup.clear();
        animClock = 0f;
    }

    public synchronized void applyImpulse2D(Entity e, float impulseX, float impulseY) {
        applyImpulse3D(e, impulseX, impulseY, 0f);
    }

    public synchronized void applyImpulse3D(Entity e, float impulseX, float impulseY, float impulseZ) {
        if (e == null) return;
        Body b = bodyMap.get(e.id);
        if (b != null) {
            b.setAwake(true);
            b.applyLinearImpulse(new Vec2(impulseX, impulseY), b.getWorldCenter());
            Vec2 v = b.getLinearVelocity();
            e.vx = v.x;
            e.vy = v.y;
        } else {
            float invMass = getInverseMass(e);
            if (invMass <= 0f) invMass = 1f;
            e.vx += impulseX * invMass;
            e.vy += impulseY * invMass;
            e.vz += impulseZ * invMass;
        }
    }

    public synchronized void applyForce(Entity e, float fx, float fy, float fz, float dt) {
        if (e == null || dt <= 0f) return;
        applyImpulse3D(e, fx * dt, fy * dt, fz * dt);
    }

    /**
     * Unreal URadialForceComponent-inspired shockwave impulse affecting all DYNAMIC bodies in radius.
     */
    public synchronized int applyRadialImpulse(GameProject project,
                                               float cx, float cy, float cz,
                                               float radius, float strength, boolean linearFalloff) {
        if (project == null || radius <= 0.01f) return 0;
        boolean mode3D = "3D".equalsIgnoreCase(project.dimensionMode);
        float r2 = radius * radius;
        int affected = 0;
        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            if (!e.alive || !"DYNAMIC".equalsIgnoreCase(e.bodyType)) continue;
            float dx = e.x - cx;
            float dy = e.y - cy;
            float dz = mode3D ? (e.z - cz) : 0f;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > r2) continue;
            float d = (float) Math.sqrt(Math.max(1e-6f, d2));
            float factor = linearFalloff ? Math.max(0.05f, 1.0f - (d / radius)) : 1.0f;
            float mag = strength * factor;
            float nx = d > 1e-4f ? dx / d : 0f;
            float ny = d > 1e-4f ? dy / d : 1f;
            float nz = (mode3D && d > 1e-4f) ? dz / d : 0f;
            applyImpulse3D(e, nx * mag, ny * mag, nz * mag);
            affected++;
        }
        return affected;
    }

    /**
     * Unreal LineTraceSingle-inspired 2D/3D Raycast against Spheres, Capsules, AABBs & 3D Terrain.
     */
    public synchronized RaycastHit raycast(GameProject project,
                                           float ox, float oy, float oz,
                                           float dx, float dy, float dz,
                                           float maxDist, String ignoreNameOrTag) {
        if (project == null || maxDist <= 0f) return null;
        boolean mode3D = "3D".equalsIgnoreCase(project.dimensionMode);
        if (!mode3D) { oz = 0f; dz = 0f; }
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return null;
        dx /= len; dy /= len; dz /= len;

        Entity bestEnt = null;
        float bestT = maxDist;
        float bestNx = 0f, bestNy = 1f, bestNz = 0f;

        for (int i = 0; i < project.entities.size(); i++) {
            Entity e = project.entities.get(i);
            if (!e.alive || !e.visible || "NONE".equalsIgnoreCase(e.collider)) continue;
            if (ignoreNameOrTag != null && !ignoreNameOrTag.isEmpty()) {
                if (ignoreNameOrTag.equalsIgnoreCase(e.name) || ignoreNameOrTag.equalsIgnoreCase(e.tag)) {
                    continue;
                }
            }
            if (mode3D && "TERRAIN".equalsIgnoreCase(e.primitive)) {
                float step = Math.max(0.35f, maxDist / 64f);
                for (float t = step; t <= bestT; t += step) {
                    float px = ox + dx * t;
                    float py = oy + dy * t;
                    float pz = oz + dz * t;
                    float gh = TerrainGenerator.sampleWorldHeight(e, getTerrainNoise(project), px, pz);
                    if (gh > -9000f && py <= gh) {
                        bestT = t;
                        bestEnt = e;
                        float[] nrm = sampleTerrainNormal(e, project, px, pz);
                        bestNx = nrm[0]; bestNy = nrm[1]; bestNz = nrm[2];
                        break;
                    }
                }
                continue;
            }

            if (isSpherical(e)) {
                float r = getSphereRadius(e, mode3D);
                float ocx = ox - e.x;
                float ocy = oy - e.y;
                float ocz = mode3D ? (oz - e.z) : 0f;
                float b = ocx * dx + ocy * dy + ocz * dz;
                float c = ocx * ocx + ocy * ocy + ocz * ocz - r * r;
                float disc = b * b - c;
                if (disc >= 0f) {
                    float t = -b - (float) Math.sqrt(disc);
                    if (t >= 0f && t < bestT) {
                        bestT = t;
                        bestEnt = e;
                        float hx = ox + dx * t, hy = oy + dy * t, hz = oz + dz * t;
                        float invR = 1f / Math.max(0.01f, r);
                        bestNx = (hx - e.x) * invR;
                        bestNy = (hy - e.y) * invR;
                        bestNz = mode3D ? (hz - e.z) * invR : 0f;
                    }
                }
            } else {
                float hx = Math.max(0.05f, Math.abs(e.scaleX) * 0.5f);
                float hy = (mode3D && "PLANE".equalsIgnoreCase(e.primitive)) ? 0.12f : Math.max(0.05f, Math.abs(e.scaleY) * 0.5f);
                float hz = mode3D ? Math.max(0.05f, Math.abs(e.scaleZ) * 0.5f) : 1.0f;

                float tMin = 0f;
                float tMax = bestT;
                float hitNx = 0f, hitNy = 0f, hitNz = 0f;

                // X slab
                if (Math.abs(dx) < 1e-6f) {
                    if (ox < e.x - hx || ox > e.x + hx) continue;
                } else {
                    float t1 = (e.x - hx - ox) / dx;
                    float t2 = (e.x + hx - ox) / dx;
                    float nSign = -1f;
                    if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; nSign = 1f; }
                    if (t1 > tMin) { tMin = t1; hitNx = nSign; hitNy = 0f; hitNz = 0f; }
                    if (t2 < tMax) tMax = t2;
                    if (tMin > tMax) continue;
                }
                // Y slab
                if (Math.abs(dy) < 1e-6f) {
                    if (oy < e.y - hy || oy > e.y + hy) continue;
                } else {
                    float t1 = (e.y - hy - oy) / dy;
                    float t2 = (e.y + hy - oy) / dy;
                    float nSign = -1f;
                    if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; nSign = 1f; }
                    if (t1 > tMin) { tMin = t1; hitNx = 0f; hitNy = nSign; hitNz = 0f; }
                    if (t2 < tMax) tMax = t2;
                    if (tMin > tMax) continue;
                }
                // Z slab (3D)
                if (mode3D) {
                    if (Math.abs(dz) < 1e-6f) {
                        if (oz < e.z - hz || oz > e.z + hz) continue;
                    } else {
                        float t1 = (e.z - hz - oz) / dz;
                        float t2 = (e.z + hz - oz) / dz;
                        float nSign = -1f;
                        if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; nSign = 1f; }
                        if (t1 > tMin) { tMin = t1; hitNx = 0f; hitNy = 0f; hitNz = nSign; }
                        if (t2 < tMax) tMax = t2;
                        if (tMin > tMax) continue;
                    }
                }
                if (tMin >= 0f && tMin < bestT) {
                    bestT = tMin;
                    bestEnt = e;
                    bestNx = hitNx; bestNy = hitNy; bestNz = hitNz;
                }
            }
        }

        if (bestEnt == null) return null;
        return new RaycastHit(
                bestEnt, bestT,
                ox + dx * bestT, oy + dy * bestT, oz + dz * bestT,
                bestNx, bestNy, bestNz);
    }

    public synchronized List<CollisionEvent> step(GameProject project, float dt) {
        events.clear();
        pairDedup.clear();
        if (project == null || dt <= 0f) return events;

        float clampedDt = Math.min(dt, 0.05f);
        animClock += clampedDt;

        // Godot-style scene tree: published parent world transforms to children first, so a moving
        // parent drags its children before any collider is integrated this frame.
        com.vortex.engine.core.model.SceneHierarchy.propagate(project.entities);

        for (int i = 0; i < project.entities.size(); i++) {
            Entity ent = project.entities.get(i);
            if (!ent.alive) continue;
            if (ent.coyoteTimer > 0f) {
                ent.coyoteTimer = Math.max(0f, ent.coyoteTimer - clampedDt);
            }
            if (ent.spriteFrames != null && !ent.spriteFrames.isEmpty()) {
                String[] frames = ent.spriteFrames.split(",");
                if (frames.length > 0) {
                    int fIdx = ((int) (animClock * Math.max(0.5f, ent.animFps))) % frames.length;
                    String nextSprite = frames[fIdx].trim();
                    if (!nextSprite.isEmpty()) ent.customSpriteName = nextSprite;
                }
            }
        }

        boolean mode3D = "3D".equalsIgnoreCase(project.dimensionMode);
        if (!mode3D && project.useBox2D) {
            stepJBox2D(project, clampedDt);
        } else {
            stepSubsteppedRigidSolver(project, clampedDt, mode3D);
        }

        // Remove destroyed entities safely after step
        List<Entity> ents = project.entities;
        for (int i = ents.size() - 1; i >= 0; i--) {
            Entity e = ents.get(i);
            if (!e.alive) {
                Body deadBody = bodyMap.remove(e.id);
                bodyScaleSig.remove(e.id);
                if (deadBody != null && box2dWorld != null) {
                    box2dWorld.destroyBody(deadBody);
                }
                ents.remove(i);
            }
        }
        // ...and fold the solved world transforms back into each child's local transform.
        com.vortex.engine.core.model.SceneHierarchy.heal(ents);
        com.vortex.engine.core.model.SceneHierarchy.syncLocalsFromWorld(ents);
        return events;
    }

    // ===================== OPEN-SOURCE JBOX2D 2D RIGID-BODY PIPELINE =====================

    private void ensureBox2DWorld(GameProject project) {
        if (box2dWorld == null || !project.id.equals(boundProjectId)) {
            boundProjectId = project.id;
            bodyMap.clear();
            bodyScaleSig.clear();
            box2dWorld = new World(new Vec2(project.gravityX, project.gravityY));
            box2dWorld.setContinuousPhysics(true);
            box2dWorld.setWarmStarting(true);
            box2dWorld.setContactListener(new ContactListener() {
                @Override
                public void beginContact(Contact contact) {
                    handleBox2DContact(contact, true);
                }
                @Override public void endContact(Contact contact) {}
                @Override
                public void preSolve(Contact contact, Manifold oldManifold) {
                    handleBox2DContact(contact, false);
                }
                @Override public void postSolve(Contact contact, ContactImpulse impulse) {}
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
            if (wm.normal.y < -0.42f || a.y > b.y + (Math.abs(b.scaleY) * 0.35f)) {
                a.isGrounded = true;
                a.coyoteTimer = COYOTE_GRACE_SEC;
            }
            if (wm.normal.y > 0.42f || b.y > a.y + (Math.abs(a.scaleY) * 0.35f)) {
                b.isGrounded = true;
                b.coyoteTimer = COYOTE_GRACE_SEC;
            }
        }
    }

    private float computeBodySignature(Entity e) {
        return Math.abs(e.scaleX) * 1000f + Math.abs(e.scaleY) * 10f
                + (e.isTrigger ? 0.5f : 0f) + ("SPHERE".equalsIgnoreCase(e.collider) ? 0.25f : 0f);
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

            float sig = computeBodySignature(e);
            Body b = bodyMap.get(e.id);
            Float prevSig = bodyScaleSig.get(e.id);
            if (b != null && (prevSig == null || Math.abs(prevSig - sig) > 0.01f)) {
                box2dWorld.destroyBody(b);
                b = null;
            }

            if (b == null) {
                b = createBox2DBody(e);
                bodyMap.put(e.id, b);
                bodyScaleSig.put(e.id, sig);
            } else {
                Vec2 pos = b.getPosition();
                if (e.transformDirty || Math.hypot(pos.x - e.x, pos.y - e.y) > 0.35f) {
                    b.setTransform(new Vec2(e.x, e.y), (float) Math.toRadians(e.rotZ));
                    e.transformDirty = false;
                }
                b.setLinearVelocity(new Vec2(e.vx, e.vy));
                b.setFixedRotation(e.fixedRotation);
                b.setGravityScale(e.gravityScale);
                b.setLinearDamping(Math.max(0f, e.drag * 2f));
                boolean wantBullet = e.ccdBullet
                        || "projectile".equalsIgnoreCase(e.tag)
                        || (e.vx * e.vx + e.vy * e.vy) > 144f;
                b.setBullet(wantBullet);
                Fixture fx = b.getFixtureList();
                if (fx != null) {
                    fx.setFriction(Math.max(0f, e.friction));
                    fx.setRestitution(Math.max(0f, Math.min(1.5f, e.bounce)));
                }
                if (!e.fixedRotation && Math.abs(e.angularVel) > 0.01f) {
                    b.setAngularVelocity((float) Math.toRadians(e.angularVel));
                }
            }
        }

        Iterator<Map.Entry<String, Body>> it = bodyMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Body> entry = it.next();
            if (!activeIds.contains(entry.getKey())) {
                box2dWorld.destroyBody(entry.getValue());
                bodyScaleSig.remove(entry.getKey());
                it.remove();
            }
        }

        for (Contact c = box2dWorld.getContactList(); c != null; c = c.getNext()) {
            handleBox2DContact(c, false);
        }

        box2dWorld.step(dt, 10, 4);

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
        bd.bullet = e.ccdBullet || "projectile".equalsIgnoreCase(e.tag) || (e.vx * e.vx + e.vy * e.vy) > 144f;
        bd.userData = e;

        Body body = box2dWorld.createBody(bd);
        body.setLinearVelocity(new Vec2(e.vx, e.vy));

        FixtureDef fd = new FixtureDef();
        if (isSpherical(e)) {
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

        fd.density = Math.max(0.08f, e.density * Math.max(0.1f, e.mass));
        fd.friction = Math.max(0f, e.friction);
        fd.restitution = Math.max(0f, Math.min(1.5f, e.bounce));
        fd.isSensor = e.isTrigger;
        Filter filter = new Filter();
        filter.categoryBits = e.collisionLayer & 0xFFFF;
        filter.maskBits = e.collisionMask & 0xFFFF;
        fd.filter = filter;

        body.createFixture(fd);
        return body;
    }

    // ===================== SUBSTEPPED 3D / CHAOS-INSPIRED RIGID-BODY SOLVER =====================

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

    private float[] sampleTerrainNormal(Entity terrainEntity, GameProject p, float wx, float wz) {
        FastNoiseLite noise = getTerrainNoise(p);
        float eps = 0.35f;
        float hL = TerrainGenerator.sampleWorldHeight(terrainEntity, noise, wx - eps, wz);
        float hR = TerrainGenerator.sampleWorldHeight(terrainEntity, noise, wx + eps, wz);
        float hD = TerrainGenerator.sampleWorldHeight(terrainEntity, noise, wx, wz - eps);
        float hU = TerrainGenerator.sampleWorldHeight(terrainEntity, noise, wx, wz + eps);
        if (hL < -9000f || hR < -9000f || hD < -9000f || hU < -9000f) {
            return new float[]{0f, 1f, 0f};
        }
        float nx = hL - hR;
        float ny = 2.0f * eps;
        float nz = hD - hU;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-5f) return new float[]{0f, 1f, 0f};
        return new float[]{nx / len, ny / len, nz / len};
    }

    private FastNoiseLite getTerrainNoise(GameProject p) {
        if (cachedTerrainNoise == null || cachedNoiseSeed != p.terrainSeed || Math.abs(cachedNoiseFreq - p.terrainFreq) > 1e-5f) {
            cachedNoiseSeed = p.terrainSeed;
            cachedNoiseFreq = p.terrainFreq;
            cachedTerrainNoise = TerrainGenerator.createConfiguredNoise(p.terrainSeed, p.terrainFreq);
        }
        return cachedTerrainNoise;
    }

    private static boolean isSpherical(Entity e) {
        return "SPHERE".equalsIgnoreCase(e.collider)
                || "SPHERE".equalsIgnoreCase(e.primitive)
                || "CIRCLE".equalsIgnoreCase(e.primitive)
                || "SPRITE_COIN".equalsIgnoreCase(e.primitive);
    }

    private static float getSphereRadius(Entity e, boolean mode3D) {
        float m = Math.max(Math.abs(e.scaleX), Math.abs(e.scaleY));
        if (mode3D) m = Math.max(m, Math.abs(e.scaleZ));
        return Math.max(0.06f, m * 0.5f);
    }

    private static float getInverseMass(Entity e) {
        if (!"DYNAMIC".equalsIgnoreCase(e.bodyType)) return 0f;
        float m = Math.max(0.05f, e.mass * Math.max(0.1f, e.density));
        return 1.0f / m;
    }

    private static boolean canLayersCollide(Entity a, Entity b) {
        return ((a.collisionLayer & b.collisionMask) != 0) && ((b.collisionLayer & a.collisionMask) != 0);
    }

    private void stepSubsteppedRigidSolver(GameProject project, float clampedDt, boolean mode3D) {
        List<Entity> ents = project.entities;
        Entity terrainEntity = null;
        boolean hasHighSpeedCcd = false;

        for (int i = 0; i < ents.size(); i++) {
            Entity e = ents.get(i);
            if (!e.alive || !e.visible) continue;
            if (mode3D && "TERRAIN".equalsIgnoreCase(e.primitive)) {
                terrainEntity = e;
            }
            if ("DYNAMIC".equalsIgnoreCase(e.bodyType)) {
                e.isGrounded = false;
            }
            if (e.ccdBullet || "projectile".equalsIgnoreCase(e.tag)
                    || (e.vx * e.vx + e.vy * e.vy + e.vz * e.vz) > 225f) {
                hasHighSpeedCcd = true;
            }
            if (e.spinX != 0f) e.rotX = (e.rotX + e.spinX * clampedDt) % 360f;
            if (e.spinY != 0f) e.rotY = (e.rotY + e.spinY * clampedDt) % 360f;
            if (e.spinZ != 0f) e.rotZ = (e.rotZ + e.spinZ * clampedDt) % 360f;
        }

        int substeps = hasHighSpeedCcd ? 6 : 4;
        float subDt = clampedDt / substeps;

        for (int step = 0; step < substeps; step++) {
            // 1. Symplectic Euler Velocity + Position Integration & Terrain Contact
            for (int i = 0; i < ents.size(); i++) {
                Entity e = ents.get(i);
                if (!e.alive || !e.visible) continue;

                if ("DYNAMIC".equalsIgnoreCase(e.bodyType)) {
                    e.vx += project.gravityX * e.gravityScale * subDt;
                    e.vy += project.gravityY * e.gravityScale * subDt;
                    if (mode3D) e.vz += project.gravityZ * e.gravityScale * subDt;

                    float damp = Math.max(0f, 1.0f - Math.max(0f, e.drag) * subDt * 6f);
                    e.vx *= damp;
                    if (mode3D) e.vz *= damp;

                    e.x += e.vx * subDt;
                    e.y += e.vy * subDt;
                    if (mode3D) e.z += e.vz * subDt;

                    if (mode3D && terrainEntity != null && e != terrainEntity && !"NONE".equalsIgnoreCase(e.collider)) {
                        float groundY = TerrainGenerator.sampleWorldHeight(terrainEntity, getTerrainNoise(project), e.x, e.z);
                        float halfH = isSpherical(e) ? getSphereRadius(e, true) : Math.max(0.1f, Math.abs(e.scaleY) * 0.5f);
                        if (groundY > -9999f && (e.y - halfH) <= groundY) {
                            e.y = groundY + halfH;
                            float[] nrm = sampleTerrainNormal(terrainEntity, project, e.x, e.z);
                            float vn = e.vx * nrm[0] + e.vy * nrm[1] + e.vz * nrm[2];
                            if (vn < 0f) {
                                float rest = Math.max(0f, Math.min(1.2f, e.bounce));
                                float jn = -(1.0f + rest) * vn;
                                e.vx += nrm[0] * jn;
                                e.vy += nrm[1] * jn;
                                e.vz += nrm[2] * jn;

                                // Tangential Coulomb friction on terrain slope
                                float tx = e.vx - (e.vx * nrm[0] + e.vy * nrm[1] + e.vz * nrm[2]) * nrm[0];
                                float ty = e.vy - (e.vx * nrm[0] + e.vy * nrm[1] + e.vz * nrm[2]) * nrm[1];
                                float tz = e.vz - (e.vx * nrm[0] + e.vy * nrm[1] + e.vz * nrm[2]) * nrm[2];
                                float tLen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
                                if (tLen > 1e-4f) {
                                    float mu = Math.max(0f, Math.min(1.5f, e.friction));
                                    float decel = Math.min(tLen, mu * Math.max(jn, Math.abs(project.gravityY) * subDt * 1.5f));
                                    e.vx -= (tx / tLen) * decel;
                                    e.vy -= (ty / tLen) * decel;
                                    e.vz -= (tz / tLen) * decel;
                                }
                            }
                            if (nrm[1] > 0.52f) {
                                e.isGrounded = true;
                                e.coyoteTimer = COYOTE_GRACE_SEC;
                            }
                            recordCollision(e, terrainEntity);
                        }
                    }
                } else if ("KINEMATIC".equalsIgnoreCase(e.bodyType)) {
                    e.x += e.vx * subDt;
                    e.y += e.vy * subDt;
                    if (mode3D) e.z += e.vz * subDt;
                }
            }

            // 2. Narrowphase Manifold + Impulse & Coulomb Friction Resolution
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
                    if (!canLayersCollide(a, b)) continue;
                    boolean aDyn = "DYNAMIC".equalsIgnoreCase(a.bodyType);
                    boolean bDyn = "DYNAMIC".equalsIgnoreCase(b.bodyType);
                    boolean aKin = "KINEMATIC".equalsIgnoreCase(a.bodyType);
                    boolean bKin = "KINEMATIC".equalsIgnoreCase(b.bodyType);
                    if (!aDyn && !bDyn && !aKin && !bKin) continue;

                    resolveRigidContact(a, b, aDyn, bDyn, mode3D);
                }
            }
        }
    }

    private void recordCollision(Entity a, Entity b) {
        String key = a.id.compareTo(b.id) < 0 ? (a.id + ":" + b.id) : (b.id + ":" + a.id);
        if (pairDedup.add(key)) {
            events.add(new CollisionEvent(a, b));
        }
    }

    /**
     * Unified 2D/3D Contact Manifold + Impulse + Restitution + Coulomb Friction Solver.
     * Normal (nx, ny, nz) always points from A -> B.
     */
    private void resolveRigidContact(Entity a, Entity b, boolean aDyn, boolean bDyn, boolean mode3D) {
        boolean aSphere = isSpherical(a);
        boolean bSphere = isSpherical(b);

        float nx = 0f, ny = 0f, nz = 0f;
        float penetration = 0f;

        if (aSphere && bSphere) {
            float ra = getSphereRadius(a, mode3D);
            float rb = getSphereRadius(b, mode3D);
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float dz = mode3D ? (b.z - a.z) : 0f;
            float d2 = dx * dx + dy * dy + dz * dz;
            float rSum = ra + rb;
            if (d2 >= rSum * rSum) return;
            float d = (float) Math.sqrt(Math.max(1e-8f, d2));
            if (d > 1e-5f) {
                nx = dx / d; ny = dy / d; nz = dz / d;
            } else {
                nx = 0f; ny = 1f; nz = 0f;
            }
            penetration = rSum - d;
        } else if (aSphere != bSphere) {
            // Sphere vs Box/AABB closest-point manifold
            Entity sph = aSphere ? a : b;
            Entity box = aSphere ? b : a;
            float r = getSphereRadius(sph, mode3D);
            float hx = Math.max(0.05f, Math.abs(box.scaleX) * 0.5f);
            float hy = (mode3D && "PLANE".equalsIgnoreCase(box.primitive)) ? 0.12f : Math.max(0.05f, Math.abs(box.scaleY) * 0.5f);
            float hz = mode3D ? Math.max(0.05f, Math.abs(box.scaleZ) * 0.5f) : 0f;

            float relX = sph.x - box.x;
            float relY = sph.y - box.y;
            float relZ = mode3D ? (sph.z - box.z) : 0f;

            float cx = Math.max(-hx, Math.min(hx, relX));
            float cy = Math.max(-hy, Math.min(hy, relY));
            float cz = mode3D ? Math.max(-hz, Math.min(hz, relZ)) : 0f;

            float dx = relX - cx;
            float dy = relY - cy;
            float dz = relZ - cz;
            float d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > r * r) return;

            if (d2 > 1e-6f) {
                float d = (float) Math.sqrt(d2);
                // Vector from box -> sphere is (dx/d, dy/d, dz/d)
                float b2sx = dx / d, b2sy = dy / d, b2sz = dz / d;
                penetration = r - d;
                if (aSphere) {
                    nx = -b2sx; ny = -b2sy; nz = -b2sz;
                } else {
                    nx = b2sx; ny = b2sy; nz = b2sz;
                }
            } else {
                // Sphere center deeply inside box: pick minimum face penetration axis
                float px = hx - Math.abs(relX) + r;
                float py = hy - Math.abs(relY) + r;
                float pz = mode3D ? (hz - Math.abs(relZ) + r) : Float.MAX_VALUE;
                float dirFlip = aSphere ? -1f : 1f;
                if (py <= px && py <= pz) {
                    penetration = py;
                    ny = (relY >= 0f ? 1f : -1f) * dirFlip;
                } else if (px <= pz) {
                    penetration = px;
                    nx = (relX >= 0f ? 1f : -1f) * dirFlip;
                } else {
                    penetration = pz;
                    nz = (relZ >= 0f ? 1f : -1f) * dirFlip;
                }
            }
        } else {
            // AABB vs AABB SAT manifold
            float ahx = Math.max(0.05f, Math.abs(a.scaleX) * 0.5f);
            float ahy = (mode3D && "PLANE".equalsIgnoreCase(a.primitive)) ? 0.12f : Math.max(0.05f, Math.abs(a.scaleY) * 0.5f);
            float ahz = mode3D ? Math.max(0.05f, Math.abs(a.scaleZ) * 0.5f) : 1f;

            float bhx = Math.max(0.05f, Math.abs(b.scaleX) * 0.5f);
            float bhy = (mode3D && "PLANE".equalsIgnoreCase(b.primitive)) ? 0.12f : Math.max(0.05f, Math.abs(b.scaleY) * 0.5f);
            float bhz = mode3D ? Math.max(0.05f, Math.abs(b.scaleZ) * 0.5f) : 1f;

            float dx = b.x - a.x;
            float px = (ahx + bhx) - Math.abs(dx);
            if (px <= 0f) return;

            float dy = b.y - a.y;
            float py = (ahy + bhy) - Math.abs(dy);
            if (py <= 0f) return;

            float dz = mode3D ? (b.z - a.z) : 0f;
            float pz = mode3D ? ((ahz + bhz) - Math.abs(dz)) : Float.MAX_VALUE;
            if (mode3D && pz <= 0f) return;

            if (py <= px && py <= pz) {
                penetration = py;
                ny = dy >= 0f ? 1f : -1f;
            } else if (px <= pz) {
                penetration = px;
                nx = dx >= 0f ? 1f : -1f;
            } else {
                penetration = pz;
                nz = dz >= 0f ? 1f : -1f;
            }
        }

        recordCollision(a, b);
        if (a.isTrigger || b.isTrigger || (!aDyn && !bDyn)) return;

        float invMassA = getInverseMass(a);
        float invMassB = getInverseMass(b);
        float invMassSum = invMassA + invMassB;
        if (invMassSum <= 1e-6f) return;

        // Grounding detection (normal points from A -> B: ny < -0.5 means A is on top of B; ny > 0.5 means B is on top of A)
        if (aDyn && ny < -0.5f) {
            a.isGrounded = true;
            a.coyoteTimer = COYOTE_GRACE_SEC;
        }
        if (bDyn && ny > 0.5f) {
            b.isGrounded = true;
            b.coyoteTimer = COYOTE_GRACE_SEC;
        }

        // Baumgarte Positional Slop Correction weighted by inverse mass
        float corrMag = (Math.max(penetration - PENETRATION_SLOP, 0f) / invMassSum) * BAUMGARTE_PERCENT;
        if (aDyn) {
            a.x -= nx * corrMag * invMassA;
            a.y -= ny * corrMag * invMassA;
            if (mode3D) a.z -= nz * corrMag * invMassA;
        }
        if (bDyn) {
            b.x += nx * corrMag * invMassB;
            b.y += ny * corrMag * invMassB;
            if (mode3D) b.z += nz * corrMag * invMassB;
        }

        // Relative velocity along contact normal
        float rvx = b.vx - a.vx;
        float rvy = b.vy - a.vy;
        float rvz = mode3D ? (b.vz - a.vz) : 0f;
        float velAlongNormal = rvx * nx + rvy * ny + rvz * nz;
        if (velAlongNormal > 0f) return; // Already separating

        float restitution = Math.max(0f, Math.min(1.5f, Math.max(a.bounce, b.bounce)));
        // Suppress micro-jitter bounce on slow resting contacts
        if (Math.abs(velAlongNormal) < 1.15f) restitution = 0f;

        float j = -(1.0f + restitution) * velAlongNormal / invMassSum;

        if (aDyn) {
            a.vx -= nx * j * invMassA;
            a.vy -= ny * j * invMassA;
            if (mode3D) a.vz -= nz * j * invMassA;
        }
        if (bDyn) {
            b.vx += nx * j * invMassB;
            b.vy += ny * j * invMassB;
            if (mode3D) b.vz += nz * j * invMassB;
        }

        // Coulomb Tangential Friction Impulse (mu = sqrt(fa^2 + fb^2) * 0.7)
        rvx = b.vx - a.vx;
        rvy = b.vy - a.vy;
        rvz = mode3D ? (b.vz - a.vz) : 0f;
        float vn2 = rvx * nx + rvy * ny + rvz * nz;
        float tx = rvx - nx * vn2;
        float ty = rvy - ny * vn2;
        float tz = rvz - nz * vn2;
        float tLen = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (tLen > 1e-4f) {
            tx /= tLen; ty /= tLen; tz /= tLen;
            float jt = -(rvx * tx + rvy * ty + rvz * tz) / invMassSum;
            float mu = (float) Math.sqrt(Math.max(0f, a.friction) * Math.max(0f, b.friction));
            float maxFriction = j * mu;
            if (Math.abs(jt) > maxFriction) {
                jt = jt > 0f ? maxFriction : -maxFriction;
            }
            if (aDyn) {
                a.vx -= tx * jt * invMassA;
                a.vy -= ty * jt * invMassA;
                if (mode3D) a.vz -= tz * jt * invMassA;
            }
            if (bDyn) {
                b.vx += tx * jt * invMassB;
                b.vy += ty * jt * invMassB;
                if (mode3D) b.vz += tz * jt * invMassB;
            }
        }
    }
}
