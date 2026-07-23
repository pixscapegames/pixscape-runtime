package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;

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

    public boolean hasShapes() {
        return shapes != null && shapes.size > 0;
    }

    public int indexOf(int physicsShapeId) {
        if (shapes == null || physicsShapeId <= 0) return -1;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape != null && shape.physicsShapeId == physicsShapeId) return i;
        }
        return -1;
    }

    public PhysicsShapeData getById(int physicsShapeId) {
        int index = indexOf(physicsShapeId);
        return index >= 0 ? shapes.get(index) : null;
    }

    /**
     * Adds an already allocated source identity. This component never allocates IDs.
     */
    public void add(PhysicsShapeData shape) {
        if (shape == null) {
            throw new IllegalArgumentException("Physics shape cannot be null.");
        }
        PhysicsShapeIdAllocator.validatePhysicsShapeId(shape.physicsShapeId);
        if (indexOf(shape.physicsShapeId) >= 0) {
            throw new IllegalArgumentException(
                    "Duplicate physicsShapeId " + shape.physicsShapeId + " in body.");
        }
        shapes.add(shape);
    }

    public boolean removeById(int physicsShapeId) {
        int index = indexOf(physicsShapeId);
        if (index < 0) return false;
        shapes.removeIndex(index);
        return true;
    }

    public void copyFrom(PhysicsShapesComponent source) {
        reset();
        if (source == null || source.shapes == null) return;
        for (int i = 0; i < source.shapes.size; i++) {
            PhysicsShapeData shape = source.shapes.get(i);
            if (shape != null) add(shape.copy());
        }
    }
}
