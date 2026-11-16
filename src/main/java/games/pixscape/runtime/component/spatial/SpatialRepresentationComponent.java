package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;

public final class SpatialRepresentationComponent extends PooledComponent {
    /**
     * Authored per-representation rectangle shared by sorting and shadow casting.
     */
    public games.pixscape.runtime.spatial.ActorSpatialRectangles rectangles;

    @Override
    protected void reset() {
        rectangles = null;
    }
}
