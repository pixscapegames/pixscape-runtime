package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public class DimensionsComponent extends PooledComponent {
    public float width, height; // taille "logique" du quad (avant scale)

    @Override
    protected void reset() {

    }
}
