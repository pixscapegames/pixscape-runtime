package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class SpatialHeightComponent extends PooledComponent {
    public float altitude = 0f;
    public float height = 0f;

    @Override
    protected void reset() {
        altitude = 0f;
        height = 0f;
    }
}
