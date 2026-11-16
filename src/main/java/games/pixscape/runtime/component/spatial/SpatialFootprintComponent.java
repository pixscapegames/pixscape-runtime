package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;

/**
 * Authored logical circle in Pixscape pixels, independent of visual scale and physics.
 * Position and orientation come from the owner's resolved world pose, never a second pose.
 */
public final class SpatialFootprintComponent extends PooledComponent {
    public float localOffsetXPx;
    public float localOffsetYPx;
    public float radiusPx = 8f;
    public float altitude;

    public void validate() {
        if (!Float.isFinite(localOffsetXPx) || !Float.isFinite(localOffsetYPx)
                || !Float.isFinite(radiusPx) || radiusPx <= 0f || !Float.isFinite(altitude)) {
            throw new IllegalArgumentException("Spatial footprint requires finite offsets/altitude and a positive finite radius.");
        }
    }

    public SpatialFootprintComponent copy() {
        SpatialFootprintComponent copy = new SpatialFootprintComponent();
        copy.set(this);
        return copy;
    }

    public void set(SpatialFootprintComponent source) {
        localOffsetXPx = source.localOffsetXPx;
        localOffsetYPx = source.localOffsetYPx;
        radiusPx = source.radiusPx;
        altitude = source.altitude;
    }

    @Override
    protected void reset() {
        localOffsetXPx = localOffsetYPx = altitude = 0f;
        radiusPx = 8f;
    }
}
