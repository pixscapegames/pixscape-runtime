package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class AABBComponent extends PooledComponent {
    public float minX, minY, maxX, maxY;

    @Override
    protected void reset() {
        minX = minY = maxX = maxY = 0f;
    }
}
