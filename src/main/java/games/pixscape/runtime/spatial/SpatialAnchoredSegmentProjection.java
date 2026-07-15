package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Shared allocation-free projection of one face clipped to one supporting tile cell. */
final class SpatialAnchoredSegmentProjection {
    private SpatialAnchoredSegmentProjection() {
    }

    static boolean project(CompiledSpatialStructure.FaceSet faces,
                           int face,
                           int gx,
                           int gy,
                           float altitude,
                           TiledMapLayerData map,
                           float[] out) {
        boolean vertical = isVertical(faces.orientation(face));
        float localStart = vertical ? gy : gx;
        float start = Math.max(faces.startCoordinate(face), localStart);
        float end = Math.min(faces.endCoordinate(face), localStart + 1f);
        if (end - start <= SpatialLineRelation.EPSILON) return false;

        if (vertical) {
            float x = faces.constantCoordinate(face);
            map.projectSpatialPoint(x, start, altitude, out, 0);
            map.projectSpatialPoint(x, end, altitude, out, 2);
        } else {
            float y = faces.constantCoordinate(face);
            map.projectSpatialPoint(start, y, altitude, out, 0);
            map.projectSpatialPoint(end, y, altitude, out, 2);
        }
        if (Math.abs(out[2] - out[0]) <= SpatialLineRelation.EPSILON) return false;
        if (out[2] < out[0]) {
            float swap = out[0]; out[0] = out[2]; out[2] = swap;
            swap = out[1]; out[1] = out[3]; out[3] = swap;
        }
        return true;
    }

    private static boolean isVertical(byte orientation) {
        return orientation == CompiledSpatialStructure.MIN_X
                || orientation == CompiledSpatialStructure.MAX_X;
    }
}
