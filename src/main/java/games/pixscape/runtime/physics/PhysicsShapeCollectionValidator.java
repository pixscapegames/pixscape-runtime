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

        int footprintCount = 0;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape == null) {
                throw new IllegalArgumentException(
                        "Physics shape collection contains a null shape at index " + i + ".");
            }
            shape.validateStructure();
            if (!shape.useSpatialFootprint) {
                continue;
            }

            footprintCount++;
            if (footprintCount > 1) {
                throw new IllegalArgumentException(
                        "Physics body has more than one footprint-derived collision.");
            }
        }
    }
}
