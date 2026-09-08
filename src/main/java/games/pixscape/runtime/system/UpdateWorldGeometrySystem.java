package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.SkipWire;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.helper.QuadGeometryHelper;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.GeometryDirty;

/**
 * Recompute world geometry (axes/oriented bounds/AABB) ONLY for entities
 * marked GEOMETRY dirty via DirtyTrackerSystem (outside components).
 * <p>
 * - Reads GeometryDirty submask (pos/origin/rot/scale/size/quad) to avoid unnecessary trig recomputation.
 * - Does not consume/remove: DirtyFlushSystem flushes at end of frame.
 */
public final class UpdateWorldGeometrySystem extends BaseSystem implements ProfiledSystem {

    private DirtyTrackerSystem dirty;
    @SkipWire
    private GameObjectHierarchySystem hierarchy;

    private ComponentMapper<TransformComponent> mT;
    private ComponentMapper<DimensionsComponent> mD;
    private ComponentMapper<OrientedBoundsComponent> mB;
    private ComponentMapper<AABBComponent> mA;
    private ComponentMapper<QuadDeformComponent> mQuad;

    private final float[] tmpCorners = new float[8];
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    @Override
    protected void initialize() {
        hierarchy = world.getSystem(GameObjectHierarchySystem.class);
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.UPDATE_WORLD_GEOMETRY);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.UPDATE_WORLD_GEOMETRY, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
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

            WorldTransformState worldState = hierarchy != null ? hierarchy.worldTransforms() : null;
            boolean resolved = worldState != null && worldState.isResolved(e);
            float worldX = resolved ? worldState.x[e] : t.x;
            float worldY = resolved ? worldState.y[e] : t.y;
            float worldRotation = resolved ? worldState.rotationRad[e] : t.rotationRad;
            float worldScaleX = resolved ? worldState.scaleX[e] : t.scaleX;
            float worldScaleY = resolved ? worldState.scaleY[e] : t.scaleY;

            // 1) ROTATION / SCALE / SIZE => axes + half-extents (+ caches)
            if ((sub & GeometryDirty.AXES_MASK) != 0) {
                float cos = MathUtils.cos(worldRotation);
                float sin = MathUtils.sin(worldRotation);

                b.ux = cos;
                b.uy = sin;
                b.vx = -sin;
                b.vy = cos;

                float sx = worldScaleX;
                float sy = worldScaleY;

                b.hx = 0.5f * d.width * Math.abs(sx);
                b.hy = 0.5f * d.height * Math.abs(sy);

            }

            // 2) POSITION/ORIGIN/(ROTATION/SCALE)/SIZE => center + AABB
            if ((sub & GeometryDirty.AABB_MASK) != 0) {

                float pivotWorldX = worldX;
                float pivotWorldY = worldY;

                float dx = (d.width * 0.5f - t.originX) * worldScaleX;
                float dy = (d.height * 0.5f - t.originY) * worldScaleY;

                b.cx = pivotWorldX + b.ux * dx + b.vx * dy;
                b.cy = pivotWorldY + b.uy * dx + b.vy * dy;

                QuadGeometryHelper.toWorldCorners(
                        b, t, mQuad.getSafe(e, null), worldScaleX, worldScaleY, tmpCorners);

                float x1 = tmpCorners[0], y1 = tmpCorners[1];
                float x2 = tmpCorners[2], y2 = tmpCorners[3];
                float x3 = tmpCorners[4], y3 = tmpCorners[5];
                float x4 = tmpCorners[6], y4 = tmpCorners[7];

                float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
                float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
                float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
                float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

                a.minX = minX;
                a.minY = minY;
                a.maxX = maxX;
                a.maxY = maxY;
            }

            // 3) Consume geometry logic (submask)
            dirty.clearAllGeomSub(e);
        }
    }

    /** Runs persistent geometry preparation without advancing the normal World pipeline. */
    public void prepareRuntimeAvailability() {
        processSystemInternal();
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
