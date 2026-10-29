package com.vortex.engine.core.voxel;

/**
 * Block palette for the voxel world (v4.8.0).
 *
 * Ids are stable — they are written into save files, so only ever append.
 * Colours are hand-picked to stay inside the Vortex zero-blue-chrome rule for UI while still
 * giving the world a natural palette (sky water is the only cool tone, and that is content).
 */
public final class BlockType {

    public static final int AIR = 0;
    public static final int GRASS = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int SAND = 4;
    public static final int WOOD = 5;
    public static final int LEAVES = 6;
    public static final int WATER = 7;
    public static final int PLANK = 8;
    public static final int GLASS = 9;
    public static final int BRICK = 10;
    public static final int LAMP = 11;
    public static final int SNOW = 12;
    public static final int COUNT = 13;

    private static final String[] NAMES = {
            "Air", "Grass", "Dirt", "Stone", "Sand", "Wood", "Leaves",
            "Water", "Plank", "Glass", "Brick", "Lamp", "Snow"
    };
    /** ARGB colours used by both the software viewport and the GL renderer. */
    private static final int[] COLORS = {
            0x00000000,
            0xFF4E8B3C, // grass top
            0xFF7A5537, // dirt
            0xFF8A8D92, // stone
            0xFFD9C89A, // sand
            0xFF6E4B2A, // wood
            0xFF3E7A3A, // leaves
            0xFF2F6E79, // water
            0xFFC9A26B, // plank
            0xFFBFD8DC, // glass
            0xFF9C4F42, // brick
            0xFFF0D98C, // lamp (emissive)
            0xFFE8EDF2  // snow
    };
    /** Blocks the player cannot walk through. Water/glass/lamp-free walking stays simple. */
    private static final boolean[] SOLID = {
            false, true, true, true, true, true, true, false, true, true, true, true, true
    };
    /** Transparent blocks are not meshed against neighbouring transparent blocks of the same type. */
    private static final boolean[] TRANSPARENT = {
            true, false, false, false, false, false, false, true, false, true, false, false, false
    };
    private static final boolean[] EMISSIVE = {
            false, false, false, false, false, false, false, false, false, false, false, true, false
    };

    private BlockType() { }

    public static String name(int id) {
        return id >= 0 && id < NAMES.length ? NAMES[id] : "Air";
    }

    public static int color(int id) {
        return id >= 0 && id < COLORS.length ? COLORS[id] : 0;
    }

    public static boolean isSolid(int id) {
        return id > 0 && id < SOLID.length && SOLID[id];
    }

    public static boolean isTransparent(int id) {
        return id <= 0 || (id < TRANSPARENT.length && TRANSPARENT[id]);
    }

    public static boolean isEmissive(int id) {
        return id > 0 && id < EMISSIVE.length && EMISSIVE[id];
    }

    public static int idFromName(String n) {
        if (n == null) return AIR;
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equalsIgnoreCase(n)) return i;
        }
        return AIR;
    }

    /** The ids a player can put in the hotbar (no air, no water). */
    public static int[] hotbarIds() {
        return new int[]{GRASS, DIRT, STONE, SAND, WOOD, LEAVES, PLANK, GLASS, BRICK, LAMP, SNOW};
    }

    /** Face shading multipliers (top, bottom, +x/-x, +z/-z) for the software renderer's flat path. */
    public static float faceTint(int axis, boolean positive) {
        if (axis == 1) return positive ? 1.0f : 0.55f;
        return positive ? 0.86f : 0.72f;
    }
}
