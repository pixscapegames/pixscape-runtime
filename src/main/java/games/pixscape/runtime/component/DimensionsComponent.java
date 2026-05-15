package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public class DimensionsComponent extends PooledComponent {
    public float width, height; // logical quad size (before scale)

    @Override
    protected void reset() {

    }
}
