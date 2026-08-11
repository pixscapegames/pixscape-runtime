package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.PhysicsShapeData;

/**
 * Persistent ordered source collection for a body's logical physics shapes.
 */
public final class PhysicsShapesComponent extends PooledComponent {
    public Array<PhysicsShapeData> shapes = new Array<>(true, 4, PhysicsShapeData.class);

    @Override
    protected void reset() {
        if (shapes == null) {
            shapes = new Array<>(true, 4, PhysicsShapeData.class);
        } else {
            shapes.clear();
        }
    }
}
