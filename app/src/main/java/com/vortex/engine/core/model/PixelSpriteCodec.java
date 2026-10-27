package com.vortex.engine.core.model;

/**
 * Pure-Java 16x16 Pixel Art Sprite Encoder/Decoder shared by Android OpenGL ES & Desktop Studio.
 * Format: 256 comma-separated tokens (row-major top-to-bottom), each '.' (transparent) or '#RRGGBB'.
 */
public class PixelSpriteCodec {
    public static final int SIZE = 16;

    public static int[] decodeToArgbPixels(String encoded) {
        int[] out = new int[SIZE * SIZE];
        if (encoded == null || encoded.trim().isEmpty()) {
            return decodeToArgbPixels(createDefaultKnightSprite());
        }
        String[] tokens = encoded.split(",");
        int limit = Math.min(out.length, tokens.length);
        for (int i = 0; i < limit; i++) {
            String t = tokens[i].trim();
            if (t.isEmpty() || ".".equals(t)) {
                out[i] = 0x00000000;
            } else {
                out[i] = Entity.hexToArgbInt(t, 0xFF00E5FF);
            }
        }
        return out;
    }

    public static String encodeFromArgbPixels(int[] argb) {
        StringBuilder sb = new StringBuilder(SIZE * SIZE * 8);
        for (int i = 0; i < SIZE * SIZE; i++) {
            if (i > 0) sb.append(',');
            int c = (argb != null && i < argb.length) ? argb[i] : 0;
            int alpha = (c >>> 24) & 0xFF;
            if (alpha < 32) {
                sb.append('.');
            } else {
                sb.append(String.format("#%06X", (c & 0xFFFFFF)));
            }
        }
        return sb.toString();
    }

    public static String createDefaultKnightSprite() {
        String[] rows = new String[]{
                "................",
                ".....CCCCCC.....",
                "....CCCCCCCC....",
                "...CCWWWWWWCC...",
                "...CCWKKKKWCC...",
                "...CCWWWWWWCC...",
                "....CCCCCCCC....",
                "...BBBBBBBBBB...",
                "..BBCCCCCCCCBB..",
                "..BBCYYYYYYCBB..",
                "..BBCCCCCCCCBB..",
                "....BBBBBBBB....",
                "....BBB..BBB....",
                "...BBBB..BBBB...",
                "...KKKK..KKKK...",
                "................"
        };
        return fromAsciiArt(rows);
    }

    public static String createCrateSprite() {
        String[] rows = new String[]{
                "OOOOOOOOOOOOOOOO",
                "OYYYYYYYYYYYYYYO",
                "OYOOOOOOOOOOOOYO",
                "OYOYYOOOOOOYYOYO",
                "OYOOYYOOOOYYOOYO",
                "OYOOOYYOOYYOOOYO",
                "OYOOOOYYYYOOOOYO",
                "OYOOOOYYYYOOOOYO",
                "OYOOOYYOOYYOOOYO",
                "OYOOYYOOOOYYOOYO",
                "OYOYYOOOOOOYYOYO",
                "OYOOOOOOOOOOOOYO",
                "OYYYYYYYYYYYYYYO",
                "OOOOOOOOOOOOOOOO",
                "OOOOOOOOOOOOOOOO",
                "................"
        };
        return fromAsciiArt(rows);
    }

    public static String createSlimeSprite() {
        String[] rows = new String[]{
                "................",
                ".....RRRRRR.....",
                "...RRRRRRRRRR...",
                "..RRRRRRRRRRRR..",
                "..RRWWKRRWWKRR..",
                "..RRWWKRRWWKRR..",
                ".RRRRRRRRRRRRRR.",
                ".RRRRYYYYYYRRRR.",
                ".RRRRYKKKKYRRRR.",
                ".RRRRRRRRRRRRRR.",
                "..RRRRRRRRRRRR..",
                "..RR.RR..RR.RR..",
                ".RR..RR..RR..RR.",
                "................",
                "................",
                "................"
        };
        return fromAsciiArt(rows);
    }

    public static String createKnightRunFrame2Sprite() {
        String[] rows = new String[]{
                ".....CCCCCC.....",
                "....CCCCCCCC....",
                "...CCWWWWWWCC...",
                "...CCWKKKKWCC...",
                "...CCWWWWWWCC...",
                "....CCCCCCCC....",
                "...BBBBBBBBBB...",
                "..BBCCCCCCCCBB..",
                "..BBCYYYYYYCBB..",
                "..BBCCCCCCCCBB..",
                "....BBBBBBBB....",
                "...BBB....BBB...",
                "..BBBB....BBBB..",
                "..KKKK....KKKK..",
                "................",
                "................"
        };
        return fromAsciiArt(rows);
    }

    public static String createDroneFrame1Sprite() {
        String[] rows = new String[]{
                "..CCCC....CCCC..",
                ".WWWWWW..WWWWWW.",
                "................",
                "....RRRRRRRR....",
                "...RRRRRRRRRR...",
                "..RRRYYYYYYRRR..",
                "..RRRYKKKKYRRR..",
                "..RRRYYYYYYRRR..",
                "...RRRRRRRRRR...",
                "....RRRRRRRR....",
                "...CC......CC...",
                "..CC........CC..",
                "................",
                "................",
                "................",
                "................"
        };
        return fromAsciiArt(rows);
    }

    public static String createDroneFrame2Sprite() {
        String[] rows = new String[]{
                "................",
                "..WWWW....WWWW..",
                ".CCCCCC..CCCCCC.",
                "....RRRRRRRR....",
                "...RRRRRRRRRR...",
                "..RRRYYYYYYRRR..",
                "..RRRYRRRRRYRR..",
                "..RRRYYYYYYRRR..",
                "...RRRRRRRRRR...",
                "....RRRRRRRR....",
                "....CC....CC....",
                "...CC......CC...",
                "................",
                "................",
                "................",
                "................"
        };
        return fromAsciiArt(rows);
    }

    public static String createGemSprite() {
        String[] rows = new String[]{
                "................",
                ".......GG.......",
                "......GGGG......",
                ".....GGWWGG.....",
                "....GGWWWWGG....",
                "...GGGGWWGGGG...",
                "..GGGGGGGGGGGG..",
                "..GGGGGGGGGGGG..",
                "...GGGGGGGGGG...",
                "....GGGGGGGG....",
                ".....GGGGGG.....",
                "......GGGG......",
                ".......GG.......",
                "................",
                "................",
                "................"
        };
        return fromAsciiArt(rows);
    }

    private static String fromAsciiArt(String[] rows16) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < SIZE; y++) {
            String row = y < rows16.length ? rows16[y] : "................";
            for (int x = 0; x < SIZE; x++) {
                if (sb.length() > 0) sb.append(',');
                char ch = x < row.length() ? row.charAt(x) : '.';
                switch (ch) {
                    case 'C': sb.append("#00E5FF"); break;
                    case 'B': sb.append("#1E88E5"); break;
                    case 'W': sb.append("#FFFFFF"); break;
                    case 'K': sb.append("#0B0F17"); break;
                    case 'Y': sb.append("#FFD740"); break;
                    case 'O': sb.append("#8D5524"); break;
                    case 'R': sb.append("#FF5252"); break;
                    case 'G': sb.append("#00E676"); break;
                    default:  sb.append('.'); break;
                }
            }
        }
        return sb.toString();
    }
}
