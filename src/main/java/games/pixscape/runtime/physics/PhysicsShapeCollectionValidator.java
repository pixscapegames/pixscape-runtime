package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

/**
 * Validates physics shape sources within one physics body.
 */
public final class PhysicsShapeCollectionValidator {
    private PhysicsShapeCollectionValidator() {
    }

    public static void validateCollection(Array<PhysicsShapeData> shapes) {
        if (shapes == null) {
            throw new IllegalArgumentException("Physics shape collection cannot be null.");
        }

        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape == null) {
                throw new IllegalArgumentException(
                        "Physics shape collection contains a null shape at index " + i + ".");
            }
            shape.validateStructure();

        }
    }
}
