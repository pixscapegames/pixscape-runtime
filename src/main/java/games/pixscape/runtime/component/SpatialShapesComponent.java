package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;

public final class SpatialShapesComponent extends PooledComponent {
    public Array<SpatialShapeData> shapes = new Array<>(SpatialShapeData[]::new);

    @Override
    protected void reset() {
        if (shapes == null) {
            shapes = new Array<>(SpatialShapeData[]::new);
        } else {
            shapes.clear();
        }
    }

    public boolean hasShapes() {
        return shapes != null && shapes.size > 0;
    }
}
