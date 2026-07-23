package games.pixscape.runtime.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockGeometry {
    private SpatialBlockGeometry() {
    }

    public static boolean isIndexableActorOccluder(SpatialBlockData block) {
        return block != null
                && block.actorOccluder
                && block.height > 0f
                && block.width > 0f
                && block.depth > 0f;
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

        float x0 = block.x;
        float y0 = block.y;
        float x1 = block.x + block.width;
        float y1 = block.y + block.depth;
        float offsetX = cellOriginOffsetX(map);
        float offsetY = cellOriginOffsetY(map);

        out8[0] = map.tileToWorldX(x0, y0) + offsetX;
        out8[1] = map.tileToWorldY(x0, y0) + offsetY;
        out8[2] = map.tileToWorldX(x1, y0) + offsetX;
        out8[3] = map.tileToWorldY(x1, y0) + offsetY;
        out8[4] = map.tileToWorldX(x1, y1) + offsetX;
        out8[5] = map.tileToWorldY(x1, y1) + offsetY;
        out8[6] = map.tileToWorldX(x0, y1) + offsetX;
        out8[7] = map.tileToWorldY(x0, y1) + offsetY;
        return true;
    }

    private static float cellOriginOffsetX(TiledMapLayerData map) {
        if (map == null || map.projection != SceneMetaRuntime.TiledProjection.ISO) return 0f;
        return map.tileWidth * 0.5f;
    }

    private static float cellOriginOffsetY(TiledMapLayerData map) {
        return 0f;
    }

    public static boolean writeCoveredCellRange(SpatialBlockData block, CellRange out) {
        if (block == null || out == null) return false;
        if (block.width <= 0f || block.depth <= 0f) return false;

        int minGx = (int) Math.floor(block.x);
        int minGy = (int) Math.floor(block.y);
        int maxGxExclusive = (int) Math.ceil(block.x + block.width);
        int maxGyExclusive = (int) Math.ceil(block.y + block.depth);

        if (maxGxExclusive <= minGx || maxGyExclusive <= minGy) return false;
        out.set(minGx, maxGxExclusive, minGy, maxGyExclusive);
        return true;
    }

    public static boolean containsTilePoint(SpatialBlockData block, float gx, float gy) {
        if (block == null) return false;
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
