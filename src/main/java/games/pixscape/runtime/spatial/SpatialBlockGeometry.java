package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockGeometry {
    private SpatialBlockGeometry() {
    }

    public static boolean isSupportedOrientation(SpatialBlockData block) {
        return block != null && block.orientation == SpatialBlockOrientation.TILE_CELL;
    }

    public static boolean isIndexableActorOccluder(SpatialBlockData block) {
        return block != null
                && block.enabled
                && block.actorOccluder
                && block.height > 0f
                && block.width > 0f
                && block.depth > 0f
                && isSupportedOrientation(block);
    }

    public static float bottom(SpatialBlockData block) {
        return block != null ? block.altitude : 0f;
    }

    public static float top(SpatialBlockData block) {
        return block != null ? block.altitude + block.height : 0f;
    }

    public static boolean writeTileCellFootprint(SpatialBlockData block,
                                                 TiledMapLayerData map,
                                                 float[] out8) {
        if (block == null || map == null || out8 == null || out8.length < 8) return false;
        if (!isSupportedOrientation(block)) return false;

        float x0 = block.x;
        float y0 = block.y;
        float x1 = block.x + block.width;
        float y1 = block.y + block.depth;

        out8[0] = map.tileToWorldX(x0, y0);
        out8[1] = map.tileToWorldY(x0, y0);
        out8[2] = map.tileToWorldX(x1, y0);
        out8[3] = map.tileToWorldY(x1, y0);
        out8[4] = map.tileToWorldX(x1, y1);
        out8[5] = map.tileToWorldY(x1, y1);
        out8[6] = map.tileToWorldX(x0, y1);
        out8[7] = map.tileToWorldY(x0, y1);
        return true;
    }

    public static boolean writeCoveredCellRange(SpatialBlockData block, CellRange out) {
        if (block == null || out == null) return false;
        if (!isSupportedOrientation(block) || block.width <= 0f || block.depth <= 0f) return false;

        int minGx = (int) Math.floor(block.x);
        int minGy = (int) Math.floor(block.y);
        int maxGxExclusive = (int) Math.ceil(block.x + block.width);
        int maxGyExclusive = (int) Math.ceil(block.y + block.depth);

        if (maxGxExclusive <= minGx || maxGyExclusive <= minGy) return false;
        out.set(minGx, maxGxExclusive, minGy, maxGyExclusive);
        return true;
    }

    public static boolean containsTilePoint(SpatialBlockData block, float gx, float gy) {
        if (block == null || !isSupportedOrientation(block)) return false;
        return gx >= block.x
                && gx < block.x + block.width
                && gy >= block.y
                && gy < block.y + block.depth;
    }

    public static final class CellRange {
        public int minGx;
        public int maxGxExclusive;
        public int minGy;
        public int maxGyExclusive;

        public CellRange set(int minGx, int maxGxExclusive, int minGy, int maxGyExclusive) {
            this.minGx = minGx;
            this.maxGxExclusive = maxGxExclusive;
            this.minGy = minGy;
            this.maxGyExclusive = maxGyExclusive;
            return this;
        }
    }
}
