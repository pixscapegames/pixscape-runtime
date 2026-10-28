package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectSet;

/** Cold load/authoring validation; never used for frame-time relation lookup. */
public final class SpatialBlockLinks {
    private SpatialBlockLinks() { }

    public static Array<SpatialBlockLink> copy(Array<SpatialBlockLink> links) {
        Array<SpatialBlockLink> result = new Array<>(SpatialBlockLink.class);
        if (links != null) for (SpatialBlockLink link : links) result.add(link == null ? null : link.copy());
        return result;
    }

    /** Copies a selected set only when both local endpoints are mapped by its owner. */
    public static Array<SpatialBlockLink> remap(Array<SpatialBlockLink> links, com.badlogic.gdx.utils.IntIntMap ids) {
        Array<SpatialBlockLink> result = new Array<>(SpatialBlockLink.class);
        if (links != null) for (SpatialBlockLink link : links) {
            int first = ids.get(link.firstBlockId, 0), second = ids.get(link.secondBlockId, 0);
            if (first <= 0 || second <= 0) continue;
            result.add(new SpatialBlockLink(first, second, link.firstFace, link.secondFace));
        }
        return result;
    }

    public static void validate(Array<SpatialBlockData> walls, Array<SpatialBlockLink> links) {
        IntMap<SpatialBlockData> index = new IntMap<>();
        if (walls != null) for (SpatialBlockData wall : walls) {
            if (wall == null || wall.id <= 0)
                throw new IllegalArgumentException("Spatial wall id must be positive.");
            if (index.containsKey(wall.id))
                throw new IllegalArgumentException("duplicate wall id " + wall.id + ".");
            index.put(wall.id, wall);
        }
        ObjectSet<String> pairs = new ObjectSet<>();
        if (links == null) throw new IllegalArgumentException("Spatial links collection is null.");
        for (SpatialBlockLink link : links) {
            if (link == null || link.firstBlockId == link.secondBlockId)
                throw new IllegalArgumentException("Invalid Spatial self/null link.");
            SpatialBlockData a = index.get(link.firstBlockId), b = index.get(link.secondBlockId);
            String pair = Math.min(link.firstBlockId, link.secondBlockId) + ":" + Math.max(link.firstBlockId, link.secondBlockId);
            if (!pairs.add(pair)) throw new IllegalArgumentException("Duplicate Spatial link " + pair + ".");
            if (a == null || b == null) throw new IllegalArgumentException("Missing endpoint for Spatial link " + pair + ".");
            if (!contact(a, b, link.firstFace, link.secondFace))
                throw new IllegalArgumentException("Invalid contact faces for Spatial link " + pair + ".");
            if (a.structureId != b.structureId || Float.compare(a.altitude, b.altitude) != 0
                    || Float.compare(a.height, b.height) != 0)
                throw new IllegalArgumentException("Spatial link " + pair + " requires one structure, altitude and height.");
        }
    }

    public static boolean contact(SpatialBlockData a, SpatialBlockData b, int face, int other) {
        if (a == null || b == null) return false;
        if (face == 0 && other == 0) return overlap(a, b);
        if (opposite(face) != other || other == 0) return false;
        float span = face <= SpatialBlockLink.MAX_X
                ? Math.min(a.y + a.depth, b.y + b.depth) - Math.max(a.y, b.y)
                : Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
        return span > SpatialWallGeometry.GEOMETRY_EPSILON && near(coordinate(a, face), coordinate(b, other));
    }

    public static boolean overlap(SpatialBlockData a, SpatialBlockData b) {
        return SpatialWallGeometry.classifyJunction(a, b, new SpatialWallGeometry.Bounds(),
                new SpatialWallGeometry.Bounds(), new SpatialWallGeometry.Junction())
                == SpatialWallGeometry.JunctionClassification.VALID_RECTANGULAR_JUNCTION;
    }

    public static int contactFace(SpatialBlockData a, SpatialBlockData b) {
        int found = 0;
        for (int face = 1; face <= 8; face *= 2) if (contact(a, b, face, opposite(face))) {
            if (found != 0) return -1;
            found = face;
        }
        return found;
    }

    public static int opposite(int face) {
        switch (face) {
            case 1: return 2;
            case 2: return 1;
            case 4: return 8;
            case 8: return 4;
            default: return 0;
        }
    }

    public static float coordinate(SpatialBlockData wall, int face) {
        switch (face) {
            case 1: return wall.x;
            case 2: return wall.x + wall.width;
            case 4: return wall.y;
            case 8: return wall.y + wall.depth;
            default: throw new IllegalArgumentException("Unknown Spatial contact face " + face);
        }
    }

    public static boolean near(float a, float b) {
        return Math.abs(a - b) <= SpatialWallGeometry.GEOMETRY_EPSILON;
    }
}
