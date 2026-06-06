package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TransformComponent;

/**
 * Computes actor spatial data for depth ordering. The spatial footprint is an
 * axis-aligned world/render-space rectangle relative to the actor foot point;
 * rotation, visual bounds, transparent pixels, and Box2D fixtures are ignored.
 */
public final class SpatialActorGeometry {
    private SpatialActorGeometry() {
    }

    public static float footX(TransformComponent transform) {
        return transform != null ? transform.x - transform.originX * transform.scaleX : 0f;
    }

    public static float footY(TransformComponent transform) {
        return transform != null ? transform.y - transform.originY * transform.scaleY : 0f;
    }

    public static boolean writeFootprint(TransformComponent transform,
                                         SpatialHeightComponent height,
                                         Footprint out) {
        if (out == null) return false;
        float footX = footX(transform);
        float footY = footY(transform);
        out.footX = footX;
        out.footY = footY;
        out.bottom = height != null ? height.altitude : 0f;
        out.top = height != null ? height.altitude + height.height : 0f;

        if (height == null || height.footprintWidth <= 0f || height.footprintDepth <= 0f) {
            out.minX = footX;
            out.maxX = footX;
            out.minY = footY;
            out.maxY = footY;
            out.pointOnly = true;
            return false;
        }

        float centerX = footX + height.footprintOffsetX;
        float centerY = footY + height.footprintOffsetY;
        float halfWidth = height.footprintWidth * 0.5f;
        float halfDepth = height.footprintDepth * 0.5f;
        out.minX = centerX - halfWidth;
        out.maxX = centerX + halfWidth;
        out.minY = centerY - halfDepth;
        out.maxY = centerY + halfDepth;
        out.pointOnly = false;
        return true;
    }

    public static final class Footprint {
        public float footX;
        public float footY;
        public float minX;
        public float maxX;
        public float minY;
        public float maxY;
        public float bottom;
        public float top;
        public boolean pointOnly;
    }
}
