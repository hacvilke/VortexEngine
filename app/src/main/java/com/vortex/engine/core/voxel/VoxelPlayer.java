package com.vortex.engine.core.voxel;

/**
 * First-person block-world controller (v4.8.0): walking, sprinting, jumping, flying, swimming,
 * block breaking/placing and AABB-vs-voxel collision, all in plain Java so the exact same movement
 * runs in the desktop studio, on Android and in exported builds.
 *
 * The player is a box 0.6 x 1.8 x 0.6 (width x height x depth); {@link #x}/{@link #y}/{@link #z}
 * is the CENTRE OF THE FEET, so the eye sits at {@code y + EYE_HEIGHT}.
 */
public final class VoxelPlayer {

    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float GRAVITY = 26f;
    public static final float JUMP_SPEED = 8.4f;
    public static final float WALK_SPEED = 4.6f;
    public static final float SPRINT_SPEED = 7.4f;
    public static final float SWIM_SPEED = 3.0f;
    public static final float FLY_SPEED = 9.0f;
    public static final float REACH = 6.0f;

    public float x, y, z;
    public float vx, vy, vz;
    public float yawDeg = 0f;      // 0 = looking toward -Z
    public float pitchDeg = 0f;    // -85..85
    public boolean onGround;
    public boolean inWater;
    public boolean flying;
    public int hotbarIndex = 0;
    public int selectedBlock = BlockType.GRASS;

    private float bobPhase = 0f;
    private final int[] hotbar = BlockType.hotbarIds();

    public void spawn(VoxelWorld world, float sx, float sz) {
        int bx = Math.max(1, Math.min(world.size - 2, Math.round(sx)));
        int bz = Math.max(1, Math.min(world.size - 2, Math.round(sz)));
        int top = world.surfaceHeight(bx, bz);
        this.x = bx + 0.5f;
        this.z = bz + 0.5f;
        this.y = top + 1.05f;
        this.vx = this.vy = this.vz = 0f;
        this.onGround = false;
    }

    public float eyeX() { return x; }
    public float eyeY() { return y + EYE_HEIGHT; }
    public float eyeZ() { return z; }

    /** Look direction unit vector from yaw/pitch. Matches the software viewport's camera convention. */
    public float dirX() { return (float) (Math.sin(Math.toRadians(yawDeg)) * Math.cos(Math.toRadians(pitchDeg))); }
    public float dirY() { return (float) Math.sin(Math.toRadians(pitchDeg)); }
    public float dirZ() { return (float) (-Math.cos(Math.toRadians(yawDeg)) * Math.cos(Math.toRadians(pitchDeg))); }

    public int[] hotbar() { return hotbar; }

    public int cycleHotbar(int delta) {
        hotbarIndex = Math.floorMod(hotbarIndex + delta, hotbar.length);
        selectedBlock = hotbar[hotbarIndex];
        return selectedBlock;
    }

    public void selectHotbar(int index) {
        hotbarIndex = Math.floorMod(index, hotbar.length);
        selectedBlock = hotbar[hotbarIndex];
    }

    /**
     * One physics + input step.
     *
     * @param forward -1..1 (W/S)   @param strafe -1..1 (D/A)
     * @param jump    true while the jump/space key is held
     * @param sneak   true while crouch is held (slower walk, no falling off edges ignored for simplicity)
     */
    public void update(VoxelWorld world, float dt, float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
        if (dt <= 0f) return;
        if (dt > 0.05f) dt = 0.05f; // clamp so a stalled frame cannot tunnel the player through blocks

        inWater = world.get((int) Math.floor(x), (int) Math.floor(y + 0.6f), (int) Math.floor(z)) == BlockType.WATER;

        float speed;
        if (flying) speed = FLY_SPEED;
        else if (inWater) speed = SWIM_SPEED;
        else speed = sprint ? SPRINT_SPEED : (sneak ? WALK_SPEED * 0.45f : WALK_SPEED);

        // Movement is relative to where the player looks (only yaw, so looking up does not fly you up).
        double yawRad = Math.toRadians(yawDeg);
        float fx = (float) Math.sin(yawRad), fz = (float) -Math.cos(yawRad);
        float rx = -fz, rz = fx;

        float wishX = fx * forward + rx * strafe;
        float wishZ = fz * forward + rz * strafe;
        float wishLen = (float) Math.sqrt(wishX * wishX + wishZ * wishZ);
        if (wishLen > 1f) { wishX /= wishLen; wishZ /= wishLen; }

        float targetVx = wishX * speed;
        float targetVz = wishZ * speed;
        float accel = onGround ? 14f : 5.5f;
        if (flying || inWater) accel = 9f;
        vx += (targetVx - vx) * Math.min(1f, accel * dt);
        vz += (targetVz - vz) * Math.min(1f, accel * dt);

        if (flying) {
            float vertical = (jump ? 1f : 0f) - (sneak ? 1f : 0f);
            vy += (vertical * FLY_SPEED - vy) * Math.min(1f, 10f * dt);
        } else if (inWater) {
            vy -= GRAVITY * 0.35f * dt;
            if (jump) vy = Math.min(3.2f, vy + 12f * dt);
            vy = Math.max(-4f, vy);
        } else {
            vy -= GRAVITY * dt;
            if (jump && onGround) {
                vy = JUMP_SPEED;
                onGround = false;
            }
        }

        move(world, vx * dt, vy * dt, vz * dt);

        // Head bob for the camera (purely cosmetic, used by the viewport).
        float horizontalSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        bobPhase += horizontalSpeed * dt * 2.4f;

        // Fall-out protection: any y below the bedrock row is respawned at the surface.
        if (y < -8f) spawn(world, x, z);
    }

    public float bobOffset() {
        return onGround ? (float) Math.sin(bobPhase * 6.0) * 0.035f : 0f;
    }

    /** Swept move with per-axis resolution against the block grid. */
    public void move(VoxelWorld world, float dx, float dy, float dz) {
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) / 0.2f));
        float sx = dx / steps, sy = dy / steps, sz = dz / steps;
        for (int i = 0; i < steps; i++) {
            if (sx != 0f) {
                x += sx;
                if (collides(world)) {
                    x -= sx;
                    vx = 0f;
                    sx = 0f;
                }
            }
            if (sz != 0f) {
                z += sz;
                if (collides(world)) {
                    z -= sz;
                    vz = 0f;
                    sz = 0f;
                }
            }
            if (sy != 0f) {
                y += sy;
                if (collides(world)) {
                    y -= sy;
                    if (sy < 0) onGround = true;
                    vy = 0f;
                    sy = 0f;
                } else if (sy < 0) {
                    onGround = false;
                }
            }
        }
        // Ground check even when standing still (so jumping works right after landing).
        if (!collides(world) && world.isSolidAt(x, y - 0.02f, z)) onGround = true;
    }

    /** True when the player's AABB overlaps any solid block. */
    public boolean collides(VoxelWorld world) {
        float hw = WIDTH * 0.5f;
        int minX = (int) Math.floor(x - hw), maxX = (int) Math.floor(x + hw);
        int minY = (int) Math.floor(y), maxY = (int) Math.floor(y + HEIGHT);
        int minZ = (int) Math.floor(z - hw), maxZ = (int) Math.floor(z + hw);
        for (int by = minY; by <= maxY; by++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                for (int bx = minX; bx <= maxX; bx++) {
                    if (world.isSolid(bx, by, bz)) return true;
                }
            }
        }
        return false;
    }

    /** The block the player is looking at (null when out of reach). */
    public VoxelWorld.RayHit target(VoxelWorld world) {
        return world.raycast(eyeX(), eyeY(), eyeZ(), dirX(), dirY(), dirZ(), REACH);
    }

    /** Breaks the targeted block. Returns the removed block id, or AIR when nothing happened. */
    public int breakTarget(VoxelWorld world) {
        VoxelWorld.RayHit hit = target(world);
        if (hit == null) return BlockType.AIR;
        int id = world.get(hit.x, hit.y, hit.z);
        return world.breakBlock(hit.x, hit.y, hit.z) ? id : BlockType.AIR;
    }

    /** Places the selected block against the targeted face. Returns true when a block was placed. */
    public boolean placeTarget(VoxelWorld world, int blockId) {
        VoxelWorld.RayHit hit = target(world);
        if (hit == null) return false;
        // Never place a block inside your own body.
        float hw = WIDTH * 0.5f;
        boolean overlapsSelf = hit.px + 1 > x - hw && hit.px < x + hw
                && hit.py + 1 > y && hit.py < y + HEIGHT
                && hit.pz + 1 > z - hw && hit.pz < z + hw;
        if (overlapsSelf) return false;
        return world.placeBlock(hit.px, hit.py, hit.pz, blockId);
    }
}
