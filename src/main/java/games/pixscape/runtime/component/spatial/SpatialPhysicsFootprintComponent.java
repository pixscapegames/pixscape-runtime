package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;

/**
 * Passive Runtime cache of the spatial footprint projected from a compiled physics body.
 * Offsets and radius are stored in render pixels, ready for spatial hot-path consumption.
 */
public final class SpatialPhysicsFootprintComponent extends PooledComponent {
    public transient boolean valid;
    public transient float localOffsetXPx;
    public transient float localOffsetYPx;
    public transient float radiusPx;
    public transient int physicsGeneration;

    @Override
    protected void reset() {
        valid = false;
        localOffsetXPx = 0f;
        localOffsetYPx = 0f;
        radiusPx = 0f;
        physicsGeneration = 0;
    }
}
