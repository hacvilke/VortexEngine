package com.vortex.engine.desktop;

import com.vortex.engine.core.asset.TileWorldBuilder;
import com.vortex.engine.core.asset.TmxMap;
import com.vortex.engine.core.model.GameProject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Desktop side of the Tiled import: parses the .tmx/.tsx, samples the tileset PNG through ImageIO
 * so imported tiles keep their real colours, and hands the result to {@link TileWorldBuilder}.
 */
public final class DesktopTmxImporter {

    public static final class ImportOutcome {
        public TileWorldBuilder.Result result;
        public String warning = "";
    }

    private DesktopTmxImporter() { }

    public static ImportOutcome importInto(GameProject project, File tmxFile) throws Exception {
        ImportOutcome out = new ImportOutcome();
        TmxMap map = TmxMap.parse(tmxFile);

        final Map<String, BufferedImage> imageCache = new HashMap<>();
        TileWorldBuilder.TileColorSource colors = (tileset, localTileId) -> {
            if (tileset.imagePath == null || tileset.imagePath.isEmpty()) return 0;
            BufferedImage img = imageCache.get(tileset.imagePath);
            if (img == null) {
                try {
                    File f = new File(tileset.imagePath);
                    if (!f.isFile()) return 0;
                    img = ImageIO.read(f);
                } catch (Exception e) {
                    return 0;
                }
                if (img == null) return 0;
                imageCache.put(tileset.imagePath, img);
            }
            int cols = Math.max(1, tileset.columns);
            int tw = Math.max(1, tileset.tileWidth);
            int th = Math.max(1, tileset.tileHeight);
            int id = (int) localTileId;
            int col = id % cols;
            int row = id / cols;
            int px = tileset.margin + col * (tw + tileset.spacing) + tw / 2;
            int py = tileset.margin + row * (th + tileset.spacing) + th / 2;
            if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) return 0;
            int argb = img.getRGB(px, py);
            // Fully transparent centre pixel (e.g. a hole in the sprite): sample the tile corners.
            if ((argb >>> 24) < 8) {
                int[][] probes = {{px - tw / 3, py - th / 3}, {px + tw / 3, py - th / 3}, {px - tw / 3, py + th / 3}, {px + tw / 3, py + th / 3}};
                for (int[] p : probes) {
                    if (p[0] < 0 || p[1] < 0 || p[0] >= img.getWidth() || p[1] >= img.getHeight()) continue;
                    int a = img.getRGB(p[0], p[1]);
                    if ((a >>> 24) >= 8) return a | 0xFF000000;
                }
            }
            return argb | 0xFF000000;
        };

        TileWorldBuilder.Result r = TileWorldBuilder.build(project, map, colors, baseName(tmxFile));
        if (r.tileRects == 0 && r.solidObjects == 0) {
            out.warning = "Map parsed but produced no geometry — check layer encoding (CSV / base64).";
        }
        if (imageCache.isEmpty()) {
            out.warning = (out.warning.isEmpty() ? "" : out.warning + " ")
                    + "Tileset image not found next to the .tmx — palette colours were used instead.";
        }
        out.result = r;
        return out;
    }

    private static String baseName(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }
}
