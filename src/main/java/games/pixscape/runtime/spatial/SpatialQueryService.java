package games.pixscape.runtime.spatial;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialQueryService {
    private static final float FALLBACK_HALF_EXTENT = 0.5f;

    private final float[] tmpCellVertices = new float[8];

    public SpatialVolume buildEntityVolume(TransformComponent transform,
                                           SpatialHeightComponent height,
                                           SpatialShapesComponent shapes) {
        return buildEntityVolume(transform, height, shapes, new SpatialVolume());
    }

    public SpatialVolume buildEntityVolume(TransformComponent transform,
                                           SpatialHeightComponent height,
                                           SpatialShapesComponent shapes,
                                           SpatialVolume out) {
        if (out == null) out = new SpatialVolume();
        if (transform == null) {
            return out.set(0f, 0f, altitudeOf(height), heightOf(height),
                    -FALLBACK_HALF_EXTENT, -FALLBACK_HALF_EXTENT,
                    FALLBACK_HALF_EXTENT, FALLBACK_HALF_EXTENT);
        }

        float minX = transform.x - FALLBACK_HALF_EXTENT;
        float minY = transform.y - FALLBACK_HALF_EXTENT;
        float maxX = transform.x + FALLBACK_HALF_EXTENT;
        float maxY = transform.y + FALLBACK_HALF_EXTENT;
        boolean foundFootprint = false;

        if (shapes != null && shapes.hasShapes()) {
            Array<SpatialShapeData> data = shapes.shapes;
            for (int i = 0, n = data.size; i < n; i++) {
                SpatialShapeData shape = data.get(i);
                if (!isFootprintShape(shape)) continue;

                if (!foundFootprint) {
                    minX = Float.MAX_VALUE;
                    minY = Float.MAX_VALUE;
                    maxX = -Float.MAX_VALUE;
                    maxY = -Float.MAX_VALUE;
                    foundFootprint = true;
                }

                Bounds bounds = boundsForShape(transform, shape);
                minX = Math.min(minX, bounds.minX);
                minY = Math.min(minY, bounds.minY);
                maxX = Math.max(maxX, bounds.maxX);
                maxY = Math.max(maxY, bounds.maxY);
            }
        }

        return out.set(transform.x, transform.y, altitudeOf(height), heightOf(height), minX, minY, maxX, maxY);
    }

    public SpatialVolume buildTiledCellVolume(TiledMapLayerData map, int gx, int gy) {
        return buildTiledCellVolume(map, gx, gy, new SpatialVolume());
    }

    public SpatialVolume buildTiledCellVolume(TiledMapLayerData map, int gx, int gy, SpatialVolume out) {
        if (out == null) out = new SpatialVolume();
        if (map == null || !map.isInside(gx, gy)) {
            return out.set(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);
        }

        map.tileToCellVertices(gx, gy, tmpCellVertices);

        float minX = tmpCellVertices[0];
        float minY = tmpCellVertices[1];
        float maxX = tmpCellVertices[0];
        float maxY = tmpCellVertices[1];

        for (int i = 2; i < 8; i += 2) {
            float x = tmpCellVertices[i];
            float y = tmpCellVertices[i + 1];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        return out.set(
                map.tileToWorldX(gx, gy),
                map.tileToWorldY(gx, gy),
                map.getTileAltitude(gx, gy),
                map.getTileHeight(gx, gy),
                minX,
                minY,
                maxX,
                maxY
        );
    }

    public int getTileSpatialFlags(TiledMapLayerData map, int gx, int gy) {
        return map != null ? map.getTileSpatialFlags(gx, gy) : 0;
    }

    public boolean verticallyOverlaps(SpatialVolume a, SpatialVolume b) {
        return a != null && a.verticalOverlaps(b);
    }

    public boolean isAbove(SpatialVolume a, SpatialVolume b) {
        return a != null && b != null && a.hasHeight() && b.hasHeight() && a.bottom() >= b.top();
    }

    public boolean isBelow(SpatialVolume a, SpatialVolume b) {
        return a != null && b != null && a.hasHeight() && b.hasHeight() && a.top() <= b.bottom();
    }

    public boolean isBehind(SpatialVolume a, SpatialVolume b) {
        return a != null && b != null && a.worldY > b.worldY;
    }

    public boolean isInFrontOf(SpatialVolume a, SpatialVolume b) {
        return a != null && b != null && a.worldY < b.worldY;
    }

    public int relation(SpatialVolume a, SpatialVolume b) {
        if (a == null || b == null) return SpatialRelation.NONE;
        if (isAbove(a, b)) return SpatialRelation.ABOVE;
        if (isBelow(a, b)) return SpatialRelation.BELOW;
        if (a.intersects(b)) return SpatialRelation.OVERLAPPING;
        if (isBehind(a, b)) return SpatialRelation.BEHIND;
        if (isInFrontOf(a, b)) return SpatialRelation.IN_FRONT_OF;
        return SpatialRelation.NONE;
    }

    public boolean canOccludeActor(boolean actorOccluder, SpatialVolume occluder, SpatialVolume actor) {
        return actorOccludedBy(actor, occluder, actorOccluder);
    }

    public boolean actorOccludedBy(SpatialVolume actor, SpatialVolume occluder, boolean actorOccluder) {
        return actorOcclusion(actor, occluder, actorOccluder, null).occluded;
    }

    public boolean actorOrderedBehindOccluder(SpatialVolume actor, SpatialVolume occluder, boolean actorOccluder) {
        return actorOcclusionForOrdering(actor, occluder, actorOccluder, null).occluded;
    }

    public boolean actorOccludedByAny(SpatialVolume actor,
                                      SpatialVolume[] candidateVolumes,
                                      boolean[] candidateActorOccluders,
                                      IntArray candidateIds) {
        if (actor == null || candidateVolumes == null || candidateActorOccluders == null || candidateIds == null) {
            return false;
        }

        for (int i = 0, n = candidateIds.size; i < n; i++) {
            int id = candidateIds.get(i);
            if (id < 0 || id >= candidateVolumes.length || id >= candidateActorOccluders.length) continue;
            if (actorOccludedBy(actor, candidateVolumes[id], candidateActorOccluders[id])) {
                return true;
            }
        }
        return false;
    }

    public SpatialOcclusionResult actorOcclusionForOrdering(SpatialVolume actor,
                                                            SpatialVolume occluder,
                                                            boolean actorOccluder,
                                                            SpatialOcclusionResult out) {
        if (out == null) out = new SpatialOcclusionResult();
        out.reset();

        if (actor == null || occluder == null || !isBehind(actor, occluder)) {
            return out;
        }

        return actorOcclusion(actor, occluder, actorOccluder, out);
    }

    public SpatialOcclusionResult actorOcclusion(SpatialVolume actor,
                                                 SpatialVolume occluder,
                                                 boolean actorOccluder,
                                                 SpatialOcclusionResult out) {
        if (out == null) out = new SpatialOcclusionResult();
        out.reset();

        if (actor == null || occluder == null || !actorOccluder) {
            return out;
        }

        out.actorBottom = actor.bottom();
        out.actorTop = actor.top();
        out.occluderBottom = occluder.bottom();
        out.occluderTop = occluder.top();

        if (!actor.footprintIntersects(occluder)) {
            return out;
        }

        if (!actor.hasHeight() || !occluder.hasHeight()) {
            return out;
        }

        if (!actor.verticalOverlaps(occluder)) {
            return out;
        }

        out.occluded = true;
        out.partiallyOccluded = actor.top() > occluder.top();
        return out;
    }

    public boolean hasActorOccluder(SpatialShapesComponent shapes) {
        if (shapes == null || !shapes.hasShapes()) return false;
        for (int i = 0, n = shapes.shapes.size; i < n; i++) {
            SpatialShapeData shape = shapes.shapes.get(i);
            if (shape != null && shape.actorOccluder) return true;
        }
        return false;
    }

    private static boolean isFootprintShape(SpatialShapeData shape) {
        return shape != null
                && (shape.collisionEnabled || shape.actorOccluder || shape.lightOccluder || shape.particleOccluder);
    }

    private static float altitudeOf(SpatialHeightComponent height) {
        return height != null ? height.altitude : 0f;
    }

    private static float heightOf(SpatialHeightComponent height) {
        return height != null ? height.height : 0f;
    }

    private Bounds boundsForShape(TransformComponent transform, SpatialShapeData shape) {
        Bounds bounds = new Bounds();
        bounds.minX = Float.MAX_VALUE;
        bounds.minY = Float.MAX_VALUE;
        bounds.maxX = -Float.MAX_VALUE;
        bounds.maxY = -Float.MAX_VALUE;

        if (shape.shapeType == SpatialShapeData.SHAPE_CIRCLE) {
            addCircleBounds(transform, shape, bounds);
        } else if (shape.shapeType == SpatialShapeData.SHAPE_POLYGON) {
            addPolygonBounds(transform, shape, bounds);
        } else {
            addBoxBounds(transform, shape, bounds);
        }

        if (bounds.minX == Float.MAX_VALUE) {
            bounds.minX = transform.x - FALLBACK_HALF_EXTENT;
            bounds.minY = transform.y - FALLBACK_HALF_EXTENT;
            bounds.maxX = transform.x + FALLBACK_HALF_EXTENT;
            bounds.maxY = transform.y + FALLBACK_HALF_EXTENT;
        }
        return bounds;
    }

    private void addBoxBounds(TransformComponent transform, SpatialShapeData shape, Bounds bounds) {
        addShapePoint(transform, shape, -shape.halfW, -shape.halfH, bounds);
        addShapePoint(transform, shape, shape.halfW, -shape.halfH, bounds);
        addShapePoint(transform, shape, shape.halfW, shape.halfH, bounds);
        addShapePoint(transform, shape, -shape.halfW, shape.halfH, bounds);
    }

    private void addCircleBounds(TransformComponent transform, SpatialShapeData shape, Bounds bounds) {
        float scale = Math.max(Math.abs(transform.scaleX), Math.abs(transform.scaleY));
        float r = Math.max(0f, shape.radius * scale);
        float[] center = transformPoint(transform, shape, 0f, 0f);
        bounds.add(center[0] - r, center[1] - r);
        bounds.add(center[0] + r, center[1] + r);
    }

    private void addPolygonBounds(TransformComponent transform, SpatialShapeData shape, Bounds bounds) {
        if (shape.polyVerts == null || shape.polyCount < 3 || shape.polyVerts.length < shape.polyCount * 2) {
            addBoxBounds(transform, shape, bounds);
            return;
        }

        for (int i = 0; i < shape.polyCount; i++) {
            addShapePoint(transform, shape, shape.polyVerts[i * 2], shape.polyVerts[i * 2 + 1], bounds);
        }
    }

    private void addShapePoint(TransformComponent transform,
                               SpatialShapeData shape,
                               float localX,
                               float localY,
                               Bounds bounds) {
        float[] p = transformPoint(transform, shape, localX, localY);
        bounds.add(p[0], p[1]);
    }

    private float[] transformPoint(TransformComponent transform,
                                   SpatialShapeData shape,
                                   float localX,
                                   float localY) {
        float shapeAngle = shape.angleDeg * MathUtils.degreesToRadians;
        float shapeCos = MathUtils.cos(shapeAngle);
        float shapeSin = MathUtils.sin(shapeAngle);

        float sx = localX * shapeCos - localY * shapeSin + shape.offsetX;
        float sy = localX * shapeSin + localY * shapeCos + shape.offsetY;

        sx *= transform.scaleX;
        sy *= transform.scaleY;

        float cos = MathUtils.cos(transform.rotationRad);
        float sin = MathUtils.sin(transform.rotationRad);

        return new float[]{
                transform.x + sx * cos - sy * sin,
                transform.y + sx * sin + sy * cos
        };
    }

    private static final class Bounds {
        float minX;
        float minY;
        float maxX;
        float maxY;

        void add(float x, float y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
    }
}
