package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;

final class SpatialV2Rule {
    private SpatialV2Rule() {
    }

    static boolean hasValidAuthoredTileRefs(SpatialBlockData block) {
        if (block == null || block.linkedTileRefs == null || block.linkedTileRefs.size == 0) {
            return false;
        }

        int count = block.linkedTileRefs.size;
        for (int i = 0; i < count; i++) {
            SpatialBlockData.LinkedTileRef a = block.linkedTileRefs.get(i);
            if (a == null) return false;
            for (int j = i + 1; j < count; j++) {
                SpatialBlockData.LinkedTileRef b = block.linkedTileRefs.get(j);
                if (b == null) return false;
                if (a.gx == b.gx && a.gy == b.gy) return false;
            }
        }
        return true;
    }
}
