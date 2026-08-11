package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Allocation-free primitive geometry and validation rules for authored rectangular walls. */
public final class SpatialWallGeometry {
    public static final float GEOMETRY_EPSILON = 0.0001f;

    private SpatialWallGeometry() {
    }

    public enum JunctionClassification {
        NONE,
        VALID_RECTANGULAR_JUNCTION,
        DUPLICATE,
        CONTAINMENT,
        INVALID
    }

    public enum CoverageValidation {
        VALID,
        MISSING_WALL,
        INVALID_BLOCK_ID,
        INVALID_STRUCTURE_ID,
        INVALID_GEOMETRY,
        MISSING_MAP,
        MISSING_REFS,
        WRONG_REF_COUNT,
        NULL_REF,
        REF_OUTSIDE_RECTANGLE,
        DUPLICATE_REF,
        REF_OUTSIDE_MAP,
        EMPTY_MAP_CELL,
        FOOTPRINT_OUTSIDE_LINKED_REGION
    }

    /** Mutable caller-owned output for continuous exclusive footprint bounds. */
    public static final class Bounds {
        public float minX;
        public float maxX;
        public float minY;
        public float maxY;
    }

    /** Mutable caller-owned output for the strict integer linked-cell rectangle. */
    public static final class LinkedCellBounds {
        public int minGx;
        public int maxGxExclusive;
        public int minGy;
        public int maxGyExclusive;
    }

    /** Mutable caller-owned output for pairwise junction classification and bounds. */
    public static final class Junction {
        public JunctionClassification classification = JunctionClassification.INVALID;
        public float minX;
        public float maxX;
        public float minY;
        public float maxY;
    }

    public static boolean isFinite(float value) {
        return !Float.isNaN(value)
                && !Float.isInfinite(value);
    }

    public static boolean extractBounds(SpatialBlockData wall, Bounds out) {
        if (wall == null || out == null
                || !isFinite(wall.x) || !isFinite(wall.y)
                || !isFinite(wall.width) || !isFinite(wall.depth)
                || wall.width < GEOMETRY_EPSILON || wall.depth < GEOMETRY_EPSILON) {
            return false;
        }
        float maxX = wall.x + wall.width;
        float maxY = wall.y + wall.depth;
        if (!isFinite(maxX) || !isFinite(maxY)) return false;
        out.minX = wall.x;
        out.maxX = maxX;
        out.minY = wall.y;
        out.maxY = maxY;
        return true;
    }

    public static boolean rectanglesIntersect(Bounds a, Bounds b) {
        return a != null && b != null
                && Math.min(a.maxX, b.maxX) - Math.max(a.minX, b.minX) > GEOMETRY_EPSILON
                && Math.min(a.maxY, b.maxY) - Math.max(a.minY, b.minY) > GEOMETRY_EPSILON;
    }

    public static boolean identical(Bounds a, Bounds b) {
        return a != null && b != null
                && nearlyEqual(a.minX, b.minX) && nearlyEqual(a.maxX, b.maxX)
                && nearlyEqual(a.minY, b.minY) && nearlyEqual(a.maxY, b.maxY);
    }

    public static boolean contains(Bounds outer, Bounds inner) {
        return outer != null && inner != null
                && outer.minX <= inner.minX + GEOMETRY_EPSILON
                && outer.maxX + GEOMETRY_EPSILON >= inner.maxX
                && outer.minY <= inner.minY + GEOMETRY_EPSILON
                && outer.maxY + GEOMETRY_EPSILON >= inner.maxY;
    }

    public static JunctionClassification classifyJunction(SpatialBlockData a,
                                                           SpatialBlockData b,
                                                           Bounds aBounds,
                                                           Bounds bBounds,
                                                           Junction out) {
        if (out == null || !extractBounds(a, aBounds) || !extractBounds(b, bBounds)) {
            if (out != null) out.classification = JunctionClassification.INVALID;
            return JunctionClassification.INVALID;
        }
        if (!rectanglesIntersect(aBounds, bBounds)) {
            out.classification = JunctionClassification.NONE;
            clearJunctionBounds(out);
        } else if (identical(aBounds, bBounds)) {
            out.classification = JunctionClassification.DUPLICATE;
            setIntersection(aBounds, bBounds, out);
        } else if (contains(aBounds, bBounds) || contains(bBounds, aBounds)) {
            out.classification = JunctionClassification.CONTAINMENT;
            setIntersection(aBounds, bBounds, out);
        } else {
            out.classification = JunctionClassification.VALID_RECTANGULAR_JUNCTION;
            setIntersection(aBounds, bBounds, out);
        }
        return out.classification;
    }

    public static CoverageValidation validateAuthoredWall(SpatialBlockData wall,
                                                           TiledMapLayerData map,
                                                           Bounds bounds) {
        if (wall == null) return CoverageValidation.MISSING_WALL;
        if (wall.id <= 0) return CoverageValidation.INVALID_BLOCK_ID;
        if (wall.structureId <= 0) return CoverageValidation.INVALID_STRUCTURE_ID;
        if (!extractBounds(wall, bounds)) return CoverageValidation.INVALID_GEOMETRY;
        if (map == null) return CoverageValidation.MISSING_MAP;
        if (!wall.linkedTileRefsAuthored || wall.linkedTileRefs == null) return CoverageValidation.MISSING_REFS;
        if (wall.linkedTileRefs.size == 0) return CoverageValidation.MISSING_REFS;
        for (int i = 0; i < wall.linkedTileRefs.size; i++) {
            if (wall.linkedTileRefs.get(i) == null) return CoverageValidation.NULL_REF;
        }

        LinkedCellBounds linked = new LinkedCellBounds();
        if (!extractLinkedCellBounds(wall, linked)) return CoverageValidation.MISSING_REFS;
        long expected = (long) (linked.maxGxExclusive - linked.minGx)
                * (linked.maxGyExclusive - linked.minGy);
        if (expected > Integer.MAX_VALUE || wall.linkedTileRefs.size != (int) expected) {
            return CoverageValidation.WRONG_REF_COUNT;
        }
        for (int i = 0; i < wall.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(i);
            if (ref == null) return CoverageValidation.NULL_REF;
            if (ref.gx < linked.minGx || ref.gx >= linked.maxGxExclusive
                    || ref.gy < linked.minGy || ref.gy >= linked.maxGyExclusive) {
                return CoverageValidation.REF_OUTSIDE_RECTANGLE;
            }
            for (int j = 0; j < i; j++) {
                SpatialBlockData.LinkedTileRef previous = wall.linkedTileRefs.get(j);
                if (previous != null && previous.gx == ref.gx && previous.gy == ref.gy) {
                    return CoverageValidation.DUPLICATE_REF;
                }
            }
            if (!map.isInside(ref.gx, ref.gy)) return CoverageValidation.REF_OUTSIDE_MAP;
            if (map.getTile(ref.gx, ref.gy) <= 0) return CoverageValidation.EMPTY_MAP_CELL;
        }
        if (bounds.minX < linked.minGx - GEOMETRY_EPSILON
                || bounds.maxX > linked.maxGxExclusive + GEOMETRY_EPSILON
                || bounds.minY < linked.minGy - GEOMETRY_EPSILON
                || bounds.maxY > linked.maxGyExclusive + GEOMETRY_EPSILON) {
            return CoverageValidation.FOOTPRINT_OUTSIDE_LINKED_REGION;
        }
        return CoverageValidation.VALID;
    }

    public static boolean extractLinkedCellBounds(SpatialBlockData wall, LinkedCellBounds out) {
        if (wall == null || out == null || !wall.linkedTileRefsAuthored
                || wall.linkedTileRefs == null || wall.linkedTileRefs.size == 0) return false;
        int minGx = Integer.MAX_VALUE;
        int minGy = Integer.MAX_VALUE;
        int maxGx = Integer.MIN_VALUE;
        int maxGy = Integer.MIN_VALUE;
        for (int i = 0; i < wall.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(i);
            if (ref == null) return false;
            minGx = Math.min(minGx, ref.gx);
            minGy = Math.min(minGy, ref.gy);
            maxGx = Math.max(maxGx, ref.gx);
            maxGy = Math.max(maxGy, ref.gy);
        }
        out.minGx = minGx;
        out.minGy = minGy;
        out.maxGxExclusive = maxGx + 1;
        out.maxGyExclusive = maxGy + 1;
        return true;
    }

    /** Lightweight runtime guard for consumers that only resolve authored anchor refs. */
    public static boolean hasUniqueLinkedTileRefs(SpatialBlockData wall) {
        if (wall == null || wall.linkedTileRefs == null || wall.linkedTileRefs.size == 0) return false;
        for (int i = 0; i < wall.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(i);
            if (ref == null) return false;
            for (int j = 0; j < i; j++) {
                SpatialBlockData.LinkedTileRef previous = wall.linkedTileRefs.get(j);
                if (previous == null || previous.gx == ref.gx && previous.gy == ref.gy) return false;
            }
        }
        return true;
    }

    private static void setIntersection(Bounds a, Bounds b, Junction out) {
        out.minX = Math.max(a.minX, b.minX);
        out.maxX = Math.min(a.maxX, b.maxX);
        out.minY = Math.max(a.minY, b.minY);
        out.maxY = Math.min(a.maxY, b.maxY);
    }

    private static void clearJunctionBounds(Junction out) {
        out.minX = 0f;
        out.maxX = 0f;
        out.minY = 0f;
        out.maxY = 0f;
    }

    private static boolean nearlyEqual(float a, float b) {
        return Math.abs(a - b) <= GEOMETRY_EPSILON;
    }
}
