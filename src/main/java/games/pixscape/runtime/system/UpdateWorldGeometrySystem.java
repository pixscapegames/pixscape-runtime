package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.helper.OrientedBoundsHelper;
import games.pixscape.runtime.render.GeometryDirty;

/**
 * Recompute world geometry (axes/oriented bounds/AABB) ONLY for entities
 * marked GEOMETRY dirty via DirtyTrackerSystem (outside components).
 *
 * - Reads GeometryDirty submask (pos/origin/rot/scale/size) to avoid unnecessary trig recomputation.
 * - Does not consume/remove: DirtyFlushSystem flushes at end of frame.
 */
public final class UpdateWorldGeometrySystem extends BaseSystem {

    private final DirtyTrackerSystem dirty;

    public UpdateWorldGeometrySystem(DirtyTrackerSystem dirty) {
        this.dirty = dirty;
    }

    private ComponentMapper<TransformComponent>       mT;
    private ComponentMapper<DimensionsComponent>      mD;
    private ComponentMapper<OrientedBoundsComponent>  mB;
    private ComponentMapper<AABBComponent>            mA;

    private final float[] tmpCorners = new float[8];

    @Override
    protected void initialize() {
        mT   = world.getMapper(TransformComponent.class);
        mD     = world.getMapper(DimensionsComponent.class);
        mB    = world.getMapper(OrientedBoundsComponent.class);
        mA    = world.getMapper(AABBComponent.class);
    }

    @Override
    protected void processSystem() {
        if (dirty == null) return;

        final IntArray list = dirty.geometryEntities();
        if (list == null || list.size == 0) return;

        for (int i = list.size - 1; i >= 0; i--) {
            final int e = list.get(i);

            if (!world.getEntityManager().isActive(e)) continue;

            final int sub = dirty.geomSub(e);
            if (sub == GeometryDirty.NONE) {
                // Coarse GEOMETRY without sub => logically nothing to do.
                // (rendering can still consume coarse via its own pass)
                continue;
            }

            TransformComponent t = mT.getSafe(e, null);
            DimensionsComponent d = mD.getSafe(e, null);
            OrientedBoundsComponent b = mB.getSafe(e, null);
            AABBComponent a = mA.getSafe(e, null);

            if (t == null || d == null || b == null || a == null) {
                // prevents keeping a "blocking" submask indefinitely
                dirty.clearAllGeomSub(e);
                continue;
            }

            // 1) ROTATION / SCALE / SIZE => axes + demi-extents (+ caches)
            if ((sub & GeometryDirty.AXES_MASK) != 0) {
                float rad = t.rotationRad;
                float cos = (float) Math.cos(rad);
                float sin = (float) Math.sin(rad);

                b.ux =  cos; b.uy =  sin;
                b.vx = -sin; b.vy =  cos;

                float sx = t.scaleX;
                float sy = t.scaleY;

                b.hx = 0.5f * d.width  * Math.abs(sx);
                b.hy = 0.5f * d.height * Math.abs(sy);

                t.cos = cos; t.sin = sin;
                t.absCos = Math.abs(cos);
                t.absSin = Math.abs(sin);
                t.invScaleX = (sx != 0f) ? 1f / sx : 0f;
                t.invScaleY = (sy != 0f) ? 1f / sy : 0f;
            }

            // 2) POSITION/ORIGIN/(ROTATION/SCALE)/SIZE => centre + AABB
            if ((sub & GeometryDirty.AABB_MASK) != 0) {

                float pivotWorldX = t.x;
                float pivotWorldY = t.y;

                float dx = (d.width  * 0.5f - t.originX) * t.scaleX;
                float dy = (d.height * 0.5f - t.originY) * t.scaleY;

                b.cx = pivotWorldX + b.ux * dx + b.vx * dy;
                b.cy = pivotWorldY + b.uy * dx + b.vy * dy;

                OrientedBoundsHelper.toCorners(b, tmpCorners);

                float x1 = tmpCorners[0], y1 = tmpCorners[1];
                float x2 = tmpCorners[2], y2 = tmpCorners[3];
                float x3 = tmpCorners[4], y3 = tmpCorners[5];
                float x4 = tmpCorners[6], y4 = tmpCorners[7];

                float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
                float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
                float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
                float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

                a.minX = minX; a.minY = minY; a.maxX = maxX; a.maxY = maxY;
            }

            // 3) Consume geometry logic (submask)
            dirty.clearAllGeomSub(e);
        }
    }
}
