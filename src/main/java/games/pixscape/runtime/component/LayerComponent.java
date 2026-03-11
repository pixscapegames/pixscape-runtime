package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class LayerComponent extends PooledComponent {

    public static final int TYPE_CLASSIC = 0;
    public static final int TYPE_PHYSICS = 1;
    public static final int TYPE_LIGHT   = 2;
    public static final int TYPE_TILED   = 3;

    public int layerIndex = 0;
    public int type = TYPE_CLASSIC;

    @Override
    protected void reset() {
        layerIndex = 0;
        type = TYPE_CLASSIC;
    }
}
