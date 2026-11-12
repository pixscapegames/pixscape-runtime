package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.Comparator;

/**
 * Cold projection of visible roof patches not already represented by an exposed wall edge.
 * ORTHO X faces are edge-on. Their omission must not omit the horizontal top surface too.
 * Image cells clip the visible surface, not its depth relation: every patch retains
 * the front ground edge of the continuous authored roof. A cell border is not a
 * physical edge and cannot put the actor in front of one slice and behind the next.
 * Height is applied once; finite surface bounds still qualify visual overlap.
 */
final class SpatialRoofSupports {
    final int structure, gx, gy;
    final float minX, maxX, frontY, minY, maxY, altitude, height;

    private SpatialRoofSupports(SpatialBlockData wall, int gx, int gy,
                                float minX, float maxX, float minY, float maxY, float frontY) {
        structure = wall.structureId;
        this.gx = gx;
        this.gy = gy;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        altitude = wall.altitude;
        height = wall.height;
        this.frontY = frontY;
    }

    /**
     * Rebuilt only with compiled geometry/projection. No runtime-frame traversal.
     */
    static Array<SpatialRoofSupports> collect(SpatialCompiledLayerCache compiled, TiledMapLayerData map) {
        Array<SpatialRoofSupports> result = new Array<SpatialRoofSupports>();
        if (map.projection != TiledProjection.ORTHO || compiled.source() == null
                || compiled.structureCount() == 0) return result;
        IntMap<LongMap<Boolean>> represented = new IntMap<LongMap<Boolean>>();
        float[] segment = new float[4];
        for (int s = 0; s < compiled.structureCount(); s++) {
            CompiledSpatialStructure structure = compiled.structure(s);
            LongMap<Boolean> cells = new LongMap<Boolean>();
            represented.put(structure.structureId(), cells);
            CompiledSpatialStructure.FaceSet faces = structure.actorOccluder();
            for (int f = 0; f < faces.faceCount(); f++) {
                int end = faces.anchorCellStart(f) + faces.anchorCellCount(f);
                for (int a = faces.anchorCellStart(f); a < end; a++) {
                    int gx = faces.anchorGx(a), gy = faces.anchorGy(a);
                    if (SpatialAnchoredSegmentProjection.project(faces, f, gx, gy, structure.altitude(), map, segment))
                        cells.put(key(gx, gy), Boolean.TRUE);
                }
            }
        }
        for (SpatialBlockData wall : compiled.source().blocks) {
            if (wall == null || !wall.actorOccluder) continue;
            LongMap<Boolean> cells = represented.get(wall.structureId);
            // Match the compiler: legacy/unassigned blocks do not form a structure.
            if (cells == null) continue;
            float roofMinY = map.originY + wall.y * map.tileHeight + map.spatialAltitudeOffset(wall.altitude + wall.height);
            float roofMaxY = roofMinY + wall.depth * map.tileHeight;
            float roofMinX = map.originX + wall.x * map.tileWidth;
            float roofMaxX = roofMinX + wall.width * map.tileWidth;
            for (SpatialBlockData.LinkedTileRef ref : wall.linkedTileRefs) {
                if (cells.containsKey(key(ref.gx, ref.gy))) continue;
                float minX = Math.max(roofMinX, map.originX + ref.gx * map.tileWidth);
                float maxX = Math.min(roofMaxX, map.originX + (ref.gx + 1f) * map.tileWidth);
                float minY = Math.max(roofMinY, map.originY + ref.gy * map.tileHeight);
                float maxY = Math.min(roofMaxY, map.originY + (ref.gy + 1f) * map.tileHeight);
                if (maxX - minX <= SpatialLineRelation.EPSILON || maxY - minY <= SpatialLineRelation.EPSILON) continue;
                result.add(new SpatialRoofSupports(wall, ref.gx, ref.gy,
                        minX, maxX, minY, maxY, roofMinY - wall.height));
            }
        }
        result.sort(ORDER);
        for (int i = result.size - 1; i > 0; i--)
            if (ORDER.compare(result.get(i - 1), result.get(i)) == 0) result.removeIndex(i);
        return result;
    }

    private static long key(int gx, int gy) {
        return (long) gx << 32 | (gy & 0xffffffffL);
    }

    private static final Comparator<SpatialRoofSupports> ORDER = new Comparator<SpatialRoofSupports>() {
        @Override
        public int compare(SpatialRoofSupports a, SpatialRoofSupports b) {
            int c = Integer.compare(a.structure, b.structure);
            if (c == 0) c = Integer.compare(a.gx, b.gx);
            if (c == 0) c = Integer.compare(a.gy, b.gy);
            if (c == 0) c = Float.compare(a.minX, b.minX);
            if (c == 0) c = Float.compare(a.maxX, b.maxX);
            if (c == 0) c = Float.compare(a.minY, b.minY);
            if (c == 0) c = Float.compare(a.maxY, b.maxY);
            if (c == 0) c = Float.compare(a.frontY, b.frontY);
            return c;
        }
    };
}
