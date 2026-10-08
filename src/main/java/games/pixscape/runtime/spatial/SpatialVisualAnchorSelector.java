package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Selects visible, spatially anchored tile quads touched by the actor's footprint/height envelope. */
public class SpatialVisualAnchorSelector {
    public int candidateCount;
    public int[] candidateAnchors = new int[0];
    private final float[] actorQuad = new float[8];
    private final float[] tileQuad = new float[8];

    public void select(int actor, SpatialActorCollector actors, DynamicEntityRenderState ecs,
                       TiledMapRenderState tiled, TiledMapLayerData map,
                       SpatialProjectedFaceCache faces, float mapOffsetX, float mapOffsetY) {
        candidateCount = 0;
        if (ecs == null || tiled == null || map == null || faces == null) return;
        int slot = actors.actorSlot[actor];
        float offsetX = ecs.offsetX[slot];
        float offsetY = ecs.offsetY[slot];
        actors.writeInfluenceQuad(actor, offsetX, offsetY, actorQuad);
        float minX = minX(actorQuad), maxX = maxX(actorQuad);
        float minY = minY(actorQuad), maxY = maxY(actorQuad);
        if (!(maxX > minX && maxY > minY)) return;

        // The tile origin may lie outside the actor bounds by a full cell or by a
        // tileset image overhang. This mirrors Tiled culling's reverse padding.
        float queryMinX = minX - mapOffsetX - map.tileWidth - map.visualPaddingRight;
        float queryMaxX = maxX - mapOffsetX + map.tileWidth + map.visualPaddingLeft;
        float queryMinY = minY - mapOffsetY - map.tileHeight - map.visualPaddingTop;
        float queryMaxY = maxY - mapOffsetY + map.tileHeight + map.visualPaddingBottom;
        float gxMin = Float.POSITIVE_INFINITY, gxMax = Float.NEGATIVE_INFINITY;
        float gyMin = Float.POSITIVE_INFINITY, gyMax = Float.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? queryMinX : queryMaxX;
            float y = (corner & 2) == 0 ? queryMinY : queryMaxY;
            float gx = map.projectWorldToTileX(x, y);
            float gy = map.projectWorldToTileY(x, y);
            gxMin = Math.min(gxMin, gx); gxMax = Math.max(gxMax, gx);
            gyMin = Math.min(gyMin, gy); gyMax = Math.max(gyMax, gy);
        }
        int fromGx = Math.max(0, (int) Math.floor(gxMin) - 1);
        int toGx = Math.min(map.mapWidth - 1, (int) Math.ceil(gxMax) + 1);
        int fromGy = Math.max(0, (int) Math.floor(gyMin) - 1);
        int toGy = Math.min(map.mapHeight - 1, (int) Math.ceil(gyMax) + 1);
        for (int gy = fromGy; gy <= toGy; gy++) {
            for (int gx = fromGx; gx <= toGx; gx++) {
                int anchor = faces.anchorForCell(gx, gy);
                if (anchor < 0 || !faces.anchorResolved[anchor]) continue;
                int ref = faces.anchorTiledRef[anchor];
                if (ref < 0 || ref >= tiled.getCapacity() || !tiled.enabled[ref] || !tiled.visible[ref]) continue;
                setQuad(tileQuad, tiled.x1[ref] + mapOffsetX, tiled.y1[ref] + mapOffsetY,
                        tiled.x2[ref] + mapOffsetX, tiled.y2[ref] + mapOffsetY,
                        tiled.x3[ref] + mapOffsetX, tiled.y3[ref] + mapOffsetY,
                        tiled.x4[ref] + mapOffsetX, tiled.y4[ref] + mapOffsetY);
                if (maxX < minX(tileQuad) || minX > maxX(tileQuad)
                        || maxY < minY(tileQuad) || minY > maxY(tileQuad)) continue;
                if (!overlaps(actorQuad, tileQuad)) continue;
                ensureCapacity(candidateCount + 1);
                candidateAnchors[candidateCount++] = anchor;
            }
        }
    }

    private void ensureCapacity(int required) {
        if (required <= candidateAnchors.length) return;
        int next = Math.max(8, candidateAnchors.length);
        while (next < required) next <<= 1;
        int[] grown = new int[next];
        System.arraycopy(candidateAnchors, 0, grown, 0, candidateCount);
        candidateAnchors = grown;
    }

    private static void setQuad(float[] q, float x1, float y1, float x2, float y2,
                                float x3, float y3, float x4, float y4) {
        q[0] = x1; q[1] = y1; q[2] = x2; q[3] = y2;
        q[4] = x3; q[5] = y3; q[6] = x4; q[7] = y4;
    }

    private static float minX(float[] q) { return Math.min(Math.min(q[0], q[2]), Math.min(q[4], q[6])); }
    private static float maxX(float[] q) { return Math.max(Math.max(q[0], q[2]), Math.max(q[4], q[6])); }
    private static float minY(float[] q) { return Math.min(Math.min(q[1], q[3]), Math.min(q[5], q[7])); }
    private static float maxY(float[] q) { return Math.max(Math.max(q[1], q[3]), Math.max(q[5], q[7])); }

    private static boolean overlaps(float[] a, float[] b) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            for (int k = 0; k < 4; k++) {
                int l = (k + 1) & 3;
                if (edgesIntersect(a[i * 2], a[i * 2 + 1], a[j * 2], a[j * 2 + 1],
                        b[k * 2], b[k * 2 + 1], b[l * 2], b[l * 2 + 1])) return true;
            }
        }
        return contains(a, b[0], b[1]) || contains(b, a[0], a[1]);
    }

    private static boolean edgesIntersect(float ax, float ay, float bx, float by,
                                          float cx, float cy, float dx, float dy) {
        float abC = cross(bx - ax, by - ay, cx - ax, cy - ay);
        float abD = cross(bx - ax, by - ay, dx - ax, dy - ay);
        float cdA = cross(dx - cx, dy - cy, ax - cx, ay - cy);
        float cdB = cross(dx - cx, dy - cy, bx - cx, by - cy);
        if (abC == 0f && onSegment(ax, ay, bx, by, cx, cy)) return true;
        if (abD == 0f && onSegment(ax, ay, bx, by, dx, dy)) return true;
        if (cdA == 0f && onSegment(cx, cy, dx, dy, ax, ay)) return true;
        if (cdB == 0f && onSegment(cx, cy, dx, dy, bx, by)) return true;
        return (abC < 0f) != (abD < 0f) && (cdA < 0f) != (cdB < 0f);
    }

    private static float cross(float ax, float ay, float bx, float by) { return ax * by - ay * bx; }
    private static boolean onSegment(float ax, float ay, float bx, float by, float x, float y) {
        return x >= Math.min(ax, bx) && x <= Math.max(ax, bx)
                && y >= Math.min(ay, by) && y <= Math.max(ay, by);
    }

    private static boolean contains(float[] polygon, float x, float y) {
        boolean inside = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            float xi = polygon[i * 2], yi = polygon[i * 2 + 1];
            float xj = polygon[j * 2], yj = polygon[j * 2 + 1];
            if ((yi > y) != (yj > y)
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside;
        }
        return inside;
    }
}
