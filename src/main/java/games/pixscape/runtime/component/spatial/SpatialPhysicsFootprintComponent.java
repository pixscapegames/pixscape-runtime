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
    /** Source authored shape identity for the currently projected footprint. */
    public transient int sourcePhysicsShapeId;
    /** True when the authored shape explicitly owns the projected footprint. */
    public transient boolean explicitOwnership;
    /** True when compiled fixtures contain malformed explicit footprint ownership. */
    public transient boolean invalidExplicitOwnership;

    @Override
    protected void reset() {
        valid = false;
        localOffsetXPx = 0f;
        localOffsetYPx = 0f;
        radiusPx = 0f;
        physicsGeneration = 0;
        sourcePhysicsShapeId = 0;
        explicitOwnership = false;
        invalidExplicitOwnership = false;
    }
}
