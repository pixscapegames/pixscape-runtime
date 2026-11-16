package games.pixscape.runtime.service;

import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.hierarchy.WorldTransformState;
import games.pixscape.runtime.spatial.SpatialActorGeometry;

/** Shared allocation-free owner-pose reading for ordering and shadow anchors. */
public final class SpatialActorPose {
    private SpatialActorPose() {
    }

    public static boolean resolve(int owner, TransformComponent transform,
                                  SpatialFootprintComponent footprint, WorldTransformState poses,
                                  SpatialActorGeometry.Footprint out) {
        if (transform == null || footprint == null || !Float.isFinite(footprint.radiusPx)
                || footprint.radiusPx <= 0f || !Float.isFinite(footprint.altitude)) {
            return false;
        }
        boolean resolved = poses != null && poses.isResolved(owner);
        float rotation = resolved ? poses.rotationRad[owner] : transform.rotationRad;
        float cos = MathUtils.cos(rotation), sin = MathUtils.sin(rotation);
        float x = (resolved ? poses.x[owner] : transform.x)
                + footprint.localOffsetXPx * cos - footprint.localOffsetYPx * sin;
        float y = (resolved ? poses.y[owner] : transform.y)
                + footprint.localOffsetXPx * sin + footprint.localOffsetYPx * cos;
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            return false;
        }
        if (out != null) {
            out.footX = x;
            out.footY = y;
            out.minX = x - footprint.radiusPx;
            out.maxX = x + footprint.radiusPx;
            out.minY = y - footprint.radiusPx;
            out.maxY = y + footprint.radiusPx;
            out.bottom = out.top = footprint.altitude;
            out.pointOnly = false;
        }
        return true;
    }
}
