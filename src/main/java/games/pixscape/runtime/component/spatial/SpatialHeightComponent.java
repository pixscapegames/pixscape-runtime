package games.pixscape.runtime.component.spatial;

import com.artemis.PooledComponent;

public final class SpatialHeightComponent extends PooledComponent {
    public float altitude = 0f;
    public float height = 0f;
    /** Authored shadow envelope. Sorting retains its independent height/footprint contract. */
    public games.pixscape.runtime.spatial.ActorVolumes volumes;

    @Override
    protected void reset() {
        altitude = 0f;
        height = 0f;
        volumes = null;
    }
}
