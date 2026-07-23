package games.pixscape.runtime.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockLinkedTiles {
    private static final float EPSILON = 0.0001f;

    private SpatialBlockLinkedTiles() {
    }

    public static Refs compute(SpatialBlockData block, TiledMapLayerData map, Refs out) {
        if (out == null) out = new Refs();
        out.clear();
        out.sourceAuthored = block != null && block.hasAuthoredLinkedTileRefs();
        out.authoredRefCount = block != null && block.linkedTileRefs != null ? block.linkedTileRefs.size : 0;
        if (!SpatialBlockGeometry.isIndexableActorOccluder(block) || map == null) {
            return out;
        }

        if (block.hasAuthoredLinkedTileRefs()) {
            return resolveAuthored(block, map, out);
        }

        SpatialBlockGeometry.CellRange range = out.tmpRange;
        if (!SpatialBlockGeometry.writeCoveredCellRange(block, range)) {
            return out;
        }
        expand(range, 1);

        float[] footprint = out.tmpFootprint;
        if (!SpatialBlockGeometry.writeTileCellFootprint(block, map, footprint)) {
            return out;
        }

        for (int gy = range.minGy; gy < range.maxGyExclusive; gy++) {
            for (int gx = range.minGx; gx < range.maxGxExclusive; gx++) {
                boolean inside = map.isInside(gx, gy);
                int tileAssetId = inside ? map.getTile(gx, gy) : 0;
                int tiledRenderRef = inside ? map.tiledRenderRefForTile(gx, gy) : -1;
                if (inside) {
                    writeTileCellFootprint(map, gx, gy, out.tmpTileFootprint);
                    writeTileBaseSegment(map, gx, gy, out.tmpSegment);
                } else {
                    clear(out.tmpTileFootprint);
                    clear(out.tmpSegment);
                }
                if (!inside) {
                    continue;
                }
                if (tileAssetId <= 0) {
                    continue;
                }
                if (tiledRenderRef < 0) {
                    continue;
                }
                boolean intersects = tileCellFootprintIntersectsBlock(block, gx, gy);
                if (!intersects) {
                    continue;
                }
                out.add(gx, gy, tiledRenderRef, tileAssetId, out.tmpSegment);
            }
        }
        return out;
    }

    private static Refs resolveAuthored(SpatialBlockData block, TiledMapLayerData map, Refs out) {
        if (block.linkedTileRefs == null) {
            return out;
        }

        for (int i = 0, n = block.linkedTileRefs.size; i < n; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null) {
                continue;
            }

            int gx = ref.gx;
            int gy = ref.gy;
            boolean inside = map.isInside(gx, gy);
            int tileAssetId = inside ? map.getTile(gx, gy) : 0;
            int tiledRenderRef = inside ? map.tiledRenderRefForTile(gx, gy) : -1;

            if (!inside) {
                continue;
            }
            if (tileAssetId <= 0) {
                continue;
            }
            if (tiledRenderRef < 0) {
                continue;
            }

            writeTileBaseSegment(map, gx, gy, out.tmpSegment);
            out.add(gx, gy, tiledRenderRef, tileAssetId, out.tmpSegment);
        }

        return out;
    }

    private static void writeTileCellFootprint(TiledMapLayerData map, int gx, int gy, float[] out8) {
        if (map == null || out8 == null || out8.length < 8) return;
        map.tileToCellVertices(gx, gy, out8);
    }

    private static void clear(float[] values) {
        if (values == null) return;
        for (int i = 0; i < values.length; i++) {
            values[i] = 0f;
        }
    }

    public static void writeTileBaseSegment(TiledMapLayerData map, int gx, int gy, float[] out4) {
        if (map == null || out4 == null || out4.length < 4) return;
        if (map.projection == SceneMetaRuntime.TiledProjection.ISO) {
            out4[0] = map.tileToWorldX(gx + 1f, gy);
            out4[1] = map.tileToWorldY(gx + 1f, gy);
            out4[2] = map.tileToWorldX(gx, gy + 1f);
            out4[3] = map.tileToWorldY(gx, gy + 1f);
            return;
        }

        out4[0] = map.tileToWorldX(gx, gy + 1);
        out4[1] = map.tileToWorldY(gx, gy + 1);
        out4[2] = map.tileToWorldX(gx + 1, gy + 1);
        out4[3] = map.tileToWorldY(gx + 1, gy + 1);
    }

    private static void expand(SpatialBlockGeometry.CellRange range, int cells) {
        range.minGx -= cells;
        range.maxGxExclusive += cells;
        range.minGy -= cells;
        range.maxGyExclusive += cells;
    }

    private static boolean tileCellFootprintIntersectsBlock(SpatialBlockData block, int gx, int gy) {
        if (block == null) return false;
        return block.x < gx + 1f - EPSILON
                && block.x + block.width > gx + EPSILON
                && block.y < gy + 1f - EPSILON
                && block.y + block.depth > gy + EPSILON;
    }

    private static boolean segmentIntersectsQuad(float[] segment, float[] quad) {
        float ax = segment[0];
        float ay = segment[1];
        float bx = segment[2];
        float by = segment[3];
        if (pointInConvexQuad(lerp(ax, bx, 0.25f), lerp(ay, by, 0.25f), quad)) return true;
        if (pointInConvexQuad(lerp(ax, bx, 0.5f), lerp(ay, by, 0.5f), quad)) return true;
        if (pointInConvexQuad(lerp(ax, bx, 0.75f), lerp(ay, by, 0.75f), quad)) return true;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            if (properSegmentsIntersect(
                    ax, ay, bx, by,
                    quad[i * 2], quad[i * 2 + 1],
                    quad[j * 2], quad[j * 2 + 1])) {
                return true;
            }
        }
        return false;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static boolean pointInConvexQuad(float px, float py, float[] quad) {
        boolean hasPositive = false;
        boolean hasNegative = false;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            float cross = orientation(
                    quad[i * 2], quad[i * 2 + 1],
                    quad[j * 2], quad[j * 2 + 1],
                    px, py);
            if (cross > EPSILON) hasPositive = true;
            if (cross < -EPSILON) hasNegative = true;
            if (hasPositive && hasNegative) return false;
        }
        return true;
    }

    private static boolean properSegmentsIntersect(float ax0, float ay0,
                                                   float ax1, float ay1,
                                                   float bx0, float by0,
                                                   float bx1, float by1) {
        float o1 = orientation(ax0, ay0, ax1, ay1, bx0, by0);
        float o2 = orientation(ax0, ay0, ax1, ay1, bx1, by1);
        float o3 = orientation(bx0, by0, bx1, by1, ax0, ay0);
        float o4 = orientation(bx0, by0, bx1, by1, ax1, ay1);
        return oppositeSigns(o1, o2) && oppositeSigns(o3, o4);
    }

    private static float orientation(float ax, float ay, float bx, float by, float cx, float cy) {
        return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    }

    private static boolean oppositeSigns(float a, float b) {
        return (a < -EPSILON && b > EPSILON) || (a > EPSILON && b < -EPSILON);
    }

    public static final class Refs {
        public int count;
        public int[] gx = new int[0];
        public int[] gy = new int[0];
        public int[] tiledRenderRef = new int[0];
        public int[] tileAssetId = new int[0];
        public float[] base = new float[0];
        public boolean sourceAuthored;
        public int authoredRefCount;

        private final SpatialBlockGeometry.CellRange tmpRange = new SpatialBlockGeometry.CellRange();
        private final float[] tmpFootprint = new float[8];
        private final float[] tmpTileFootprint = new float[8];
        private final float[] tmpSegment = new float[4];

        public void clear() {
            count = 0;
            sourceAuthored = false;
            authoredRefCount = 0;
        }

        public int gx(int index) {
            return gx[index];
        }

        public int gy(int index) {
            return gy[index];
        }

        public int tiledRenderRef(int index) {
            return tiledRenderRef[index];
        }

        public int tileAssetId(int index) {
            return tileAssetId[index];
        }

        public void baseSegment(int index, float[] out4) {
            if (out4 == null || out4.length < 4) return;
            int offset = index * 4;
            out4[0] = base[offset];
            out4[1] = base[offset + 1];
            out4[2] = base[offset + 2];
            out4[3] = base[offset + 3];
        }

        private void add(int gx, int gy, int tiledRenderRef, int tileAssetId, float[] baseSegment) {
            ensureCapacity(count + 1);
            this.gx[count] = gx;
            this.gy[count] = gy;
            this.tiledRenderRef[count] = tiledRenderRef;
            this.tileAssetId[count] = tileAssetId;
            int offset = count * 4;
            base[offset] = baseSegment[0];
            base[offset + 1] = baseSegment[1];
            base[offset + 2] = baseSegment[2];
            base[offset + 3] = baseSegment[3];
            count++;
        }

        private void ensureCapacity(int required) {
            if (required <= gx.length) return;
            int next = Math.max(4, gx.length);
            while (required > next) next <<= 1;
            gx = grow(gx, next);
            gy = grow(gy, next);
            tiledRenderRef = grow(tiledRenderRef, next);
            tileAssetId = grow(tileAssetId, next);
            base = grow(base, next * 4);
        }

        private static int[] grow(int[] source, int next) {
            int[] out = new int[next];
            System.arraycopy(source, 0, out, 0, source.length);
            return out;
        }

        private static float[] grow(float[] source, int next) {
            float[] out = new float[next];
            System.arraycopy(source, 0, out, 0, source.length);
            return out;
        }
    }
}
