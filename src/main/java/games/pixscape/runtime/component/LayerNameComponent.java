package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Runtime layer name, exported from Studio's authored layer metadata.
 * Names are exact, case-sensitive and need not be unique.
 * After editing an existing name through ECS, signal
 * {@code DirtyTrackerSystem.layer(layerEntityId)} to invalidate derived lookups.
 */
public final class LayerNameComponent extends PooledComponent {
    public String name = "unnamed";

    @Override
    protected void reset() {
        name = "unnamed";
    }
}
