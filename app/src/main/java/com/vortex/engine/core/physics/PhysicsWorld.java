package com.vortex.engine.core.physics;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public List<CollisionEvent> step(GameProject project, float dt) {
        events.clear();
        pairDedup.clear();
        if (project == null || dt <= 0f) return events;

        float clampedDt = Math.min(dt, 0.05f);
        boolean mode3D = "3D".equalsIgnoreCase(project.dimensionMode);
        List<Entity> ents = project.entities;

        // 1. Integrate motion & continuous spin
        for (int i = 0; i < ents.size(); i++) {
            Entity e = ents.get(i);
            if (!e.alive || !e.visible) continue;

            if (e.spinX != 0f) e.rotX = (e.rotX + e.spinX * clampedDt) % 360f;
            if (e.spinY != 0f) e.rotY = (e.rotY + e.spinY * clampedDt) % 360f;
            if (e.spinZ != 0f) e.rotZ = (e.rotZ + e.spinZ * clampedDt) % 360f;

            if ("DYNAMIC".equalsIgnoreCase(e.bodyType)) {
                e.isGrounded = false;
                e.vx += project.gravityX * e.gravityScale * clampedDt;
                e.vy += project.gravityY * e.gravityScale * clampedDt;
                if (mode3D) {
                    e.vz += project.gravityZ * e.gravityScale * clampedDt;
                }

                float damp = Math.max(0f, 1.0f - e.drag * clampedDt * 8f);
                e.vx *= damp;
                if (mode3D) e.vz *= damp;

                e.x += e.vx * clampedDt;
                e.y += e.vy * clampedDt;
                if (mode3D) e.z += e.vz * clampedDt;
            } else if ("KINEMATIC".equalsIgnoreCase(e.bodyType)) {
                e.x += e.vx * clampedDt;
                e.y += e.vy * clampedDt;
                if (mode3D) e.z += e.vz * clampedDt;
            }
        }

        // 2. Pairwise AABB collision detection & resolution
        int n = ents.size();
        for (int i = 0; i < n; i++) {
            Entity a = ents.get(i);
            if (!a.alive || !a.visible || "NONE".equalsIgnoreCase(a.collider) || "NONE".equalsIgnoreCase(a.bodyType)) {
                continue;
            }
            for (int j = i + 1; j < n; j++) {
                Entity b = ents.get(j);
                if (!b.alive || !b.visible || "NONE".equalsIgnoreCase(b.collider) || "NONE".equalsIgnoreCase(b.bodyType)) {
                    continue;
                }
                boolean aDyn = "DYNAMIC".equalsIgnoreCase(a.bodyType);
                boolean bDyn = "DYNAMIC".equalsIgnoreCase(b.bodyType);
                boolean aKin = "KINEMATIC".equalsIgnoreCase(a.bodyType);
                boolean bKin = "KINEMATIC".equalsIgnoreCase(b.bodyType);
                if (!aDyn && !bDyn && !aKin && !bKin) continue;

                if (mode3D) {
                    checkAndResolve3D(a, b, aDyn, bDyn);
                } else {
                    checkAndResolve2D(a, b, aDyn, bDyn);
                }
            }
        }

        // 3. Remove destroyed entities safely after step
        for (int i = ents.size() - 1; i >= 0; i--) {
            if (!ents.get(i).alive) {
                ents.remove(i);
            }
        }

        return events;
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
        if (a.isTrigger || b.isTrigger) return;
        if (!aDyn && !bDyn) return;

        float restitution = Math.max(a.bounce, b.bounce);

        if (py <= px && py <= pz) {
            float sign = dy >= 0f ? -1f : 1f; // direction to push 'a' away from 'b'
            if (aDyn && bDyn) {
                a.y += sign * py * 0.5f;
                b.y -= sign * py * 0.5f;
            } else if (aDyn) {
                a.y += sign * py;
                if (sign > 0f) {
                    a.isGrounded = true;
                    a.vx *= a.friction;
                    a.vz *= a.friction;
                }
                if ((sign > 0f && a.vy < 0f) || (sign < 0f && a.vy > 0f)) {
                    a.vy = -a.vy * restitution;
                }
            } else {
                b.y -= sign * py;
                if (-sign > 0f) {
                    b.isGrounded = true;
                    b.vx *= b.friction;
                    b.vz *= b.friction;
                }
                if ((-sign > 0f && b.vy < 0f) || (-sign < 0f && b.vy > 0f)) {
                    b.vy = -b.vy * restitution;
                }
            }
        } else if (px <= pz) {
            float sign = dx >= 0f ? -1f : 1f;
            if (aDyn && bDyn) {
                a.x += sign * px * 0.5f;
                b.x -= sign * px * 0.5f;
                float tmp = a.vx;
                a.vx = b.vx * restitution;
                b.vx = tmp * restitution;
            } else if (aDyn) {
                a.x += sign * px;
                a.vx = -a.vx * restitution;
            } else {
                b.x -= sign * px;
                b.vx = -b.vx * restitution;
            }
        } else {
            float sign = dz >= 0f ? -1f : 1f;
            if (aDyn && bDyn) {
                a.z += sign * pz * 0.5f;
                b.z -= sign * pz * 0.5f;
                float tmp = a.vz;
                a.vz = b.vz * restitution;
                b.vz = tmp * restitution;
            } else if (aDyn) {
                a.z += sign * pz;
                a.vz = -a.vz * restitution;
            } else {
                b.z -= sign * pz;
                b.vz = -b.vz * restitution;
            }
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
        if (a.isTrigger || b.isTrigger) return;
        if (!aDyn && !bDyn) return;

        float restitution = Math.max(a.bounce, b.bounce);

        if (py <= px) {
            float sign = dy >= 0f ? -1f : 1f;
            if (aDyn && bDyn) {
                a.y += sign * py * 0.5f;
                b.y -= sign * py * 0.5f;
            } else if (aDyn) {
                a.y += sign * py;
                if (sign > 0f) {
                    a.isGrounded = true;
                    a.vx *= a.friction;
                }
                if ((sign > 0f && a.vy < 0f) || (sign < 0f && a.vy > 0f)) {
                    a.vy = -a.vy * restitution;
                }
            } else {
                b.y -= sign * py;
                if (-sign > 0f) {
                    b.isGrounded = true;
                    b.vx *= b.friction;
                }
                if ((-sign > 0f && b.vy < 0f) || (-sign < 0f && b.vy > 0f)) {
                    b.vy = -b.vy * restitution;
                }
            }
        } else {
            float sign = dx >= 0f ? -1f : 1f;
            if (aDyn && bDyn) {
                a.x += sign * px * 0.5f;
                b.x -= sign * px * 0.5f;
            } else if (aDyn) {
                a.x += sign * px;
                a.vx = -a.vx * restitution;
            } else {
                b.x -= sign * px;
                b.vx = -b.vx * restitution;
            }
        }
    }
}
