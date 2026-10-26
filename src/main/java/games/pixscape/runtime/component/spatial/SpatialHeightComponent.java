package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;

public final class SpatialHeightComponent extends PooledComponent {
    public float altitude = 0f;
    /** Authored per-representation rectangle shared by sorting and shadow casting. */
    public games.pixscape.runtime.spatial.ActorSpatialRectangles rectangles;

    @Override
    protected void reset() {
        altitude = 0f;
        rectangles = null;
    }
}
