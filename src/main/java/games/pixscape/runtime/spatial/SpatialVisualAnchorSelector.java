package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Selects visible tile anchors touched by the actor's spatial influence envelope. */
public class SpatialVisualAnchorSelector {
    public int candidateCount;
    public int[] candidateAnchors = new int[0];
    private final SpatialVisualAnchorQuery.Workspace query = new SpatialVisualAnchorQuery.Workspace();

    public void select(int actor, SpatialActorCollector actors, DynamicEntityRenderState ecs,
                       TiledMapRenderState tiled, TiledMapLayerData map,
                       SpatialProjectedFaceCache faces, float mapOffsetX, float mapOffsetY) {
        candidateCount = 0;
        if (ecs == null || tiled == null || map == null || faces == null) {
            return;
        }
        if (!SpatialVisualAnchorQuery.window(actor, actors, ecs, map, mapOffsetX, mapOffsetY, query)) {
            return;
        }
        for (int gy = query.fromGy; gy <= query.toGy; gy++) {
            for (int gx = query.fromGx; gx <= query.toGx; gx++) {
                int anchor = faces.anchorForCell(gx, gy);
                if (anchor >= 0 && SpatialVisualAnchorQuery.accept(anchor, faces, tiled, mapOffsetX, mapOffsetY, query)) {
                    ensureCapacity(candidateCount + 1);
                    candidateAnchors[candidateCount++] = anchor;
                }
            }
        }
    }

    private void ensureCapacity(int required) {
        if (required <= candidateAnchors.length) {
            return;
        }
        int next = Math.max(8, candidateAnchors.length);
        while (next < required) {
            next <<= 1;
        }
        int[] grown = new int[next];
        System.arraycopy(candidateAnchors, 0, grown, 0, candidateCount);
        candidateAnchors = grown;
    }
}
