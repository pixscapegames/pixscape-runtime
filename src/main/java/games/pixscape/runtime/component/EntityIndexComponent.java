package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class EntityIndexComponent extends PooledComponent {
    public int layerIndex = 0;
    public int zIndex = 0;

    public int getLayerIndex() {
        return layerIndex;
    }

    public int getZIndex() {
        return zIndex;
    }

    @Override
    protected void reset() {
        layerIndex = 0;
        zIndex = 0;
    }
}
