package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class LayerComponent extends PooledComponent {
    public int layerIndex;
    public boolean spatialEnabled;

    @Override
    protected void reset() {
        layerIndex = 0;
        spatialEnabled = false;
    }
}
