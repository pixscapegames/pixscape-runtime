package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;

final class SpatialBlockV1Rules {
    private SpatialBlockV1Rules() {
    }

    static boolean isStraightSegment(int startX, int startY, int endX, int endY) {
        return startX == endX || startY == endY;
    }

    static boolean hasStraightContinuousAuthoredTileRefs(SpatialBlockData block) {
        if (block == null || block.linkedTileRefs == null || block.linkedTileRefs.size == 0) {
            return false;
        }

        int count = block.linkedTileRefs.size;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean sameX = true;
        boolean sameY = true;

        SpatialBlockData.LinkedTileRef first = block.linkedTileRefs.get(0);
        if (first == null) return false;
        int firstX = first.gx;
        int firstY = first.gy;

        for (int i = 0; i < count; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            if (ref == null) return false;
            if (ref.gx != firstX) sameX = false;
            if (ref.gy != firstY) sameY = false;
            if (ref.gx < minX) minX = ref.gx;
            if (ref.gx > maxX) maxX = ref.gx;
            if (ref.gy < minY) minY = ref.gy;
            if (ref.gy > maxY) maxY = ref.gy;
        }

        if (!sameX && !sameY) return false;

        int expectedCount = sameX ? maxY - minY + 1 : maxX - minX + 1;
        if (expectedCount != count) return false;

        for (int i = 0; i < count; i++) {
            SpatialBlockData.LinkedTileRef a = block.linkedTileRefs.get(i);
            for (int j = i + 1; j < count; j++) {
                SpatialBlockData.LinkedTileRef b = block.linkedTileRefs.get(j);
                if (a.gx == b.gx && a.gy == b.gy) return false;
            }
        }
        return true;
    }
}

