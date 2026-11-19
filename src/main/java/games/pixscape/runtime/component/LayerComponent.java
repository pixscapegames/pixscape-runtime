package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class LayerComponent extends PooledComponent {
    public int layerIndex;
    /** Only one scene layer may admit spatial actors. Independent of Physics. */
    public boolean spatialEnabled;

    @Override
    protected void reset() {
        layerIndex = 0;
        spatialEnabled = false;
    }
}
