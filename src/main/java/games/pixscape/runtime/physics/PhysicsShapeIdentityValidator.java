package games.pixscape.runtime.physics;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;

/**
 * Pure validation of the scene-wide persisted physics-shape identity domain.
 */
public final class PhysicsShapeIdentityValidator {
    private PhysicsShapeIdentityValidator() {
    }

    public static void validateWorld(World world, PhysicsShapeIdState state) {
        if (world == null) {
            throw new IllegalArgumentException(
                    "World is required to validate physics shape identities.");
        }
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsShapesComponent.class))
                .getEntities();
        validateEntities(world, entities, state);
    }

    public static void validateEntities(
            World world, IntBag entities, PhysicsShapeIdState state) {
        if (world == null) {
            throw new IllegalArgumentException(
                    "World is required to validate physics shape identities.");
        }
        if (entities == null) {
            throw new IllegalArgumentException(
                    "Entity set is required to validate physics shape identities.");
        }

        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                world.getMapper(PhysicsShapesComponent.class);
        IntArray physicsShapeIds = new IntArray();
        int[] entityIds = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int ownerEntityId = entityIds[i];
            PhysicsShapesComponent component =
                    shapesMapper.getSafe(ownerEntityId, null);
            if (component == null) continue;
            if (component.shapes == null) {
                throw new IllegalArgumentException(
                        "PhysicsShapesComponent on ownerEntityId "
                                + ownerEntityId + " has a null shapes collection.");
            }
            for (int shapeIndex = 0;
                    shapeIndex < component.shapes.size;
                    shapeIndex++) {
                PhysicsShapeData shape = component.shapes.get(shapeIndex);
                if (shape == null) {
                    throw new IllegalArgumentException(
                            "Null PhysicsShapeData on ownerEntityId "
                                    + ownerEntityId + " at shapeIndex "
                                    + shapeIndex + ".");
                }
                try {
                    shape.validateStructure();
                } catch (IllegalArgumentException invalidShape) {
                    throw new IllegalArgumentException(
                            "Invalid physics shape on ownerEntityId "
                                    + ownerEntityId + ", physicsShapeId "
                                    + shape.physicsShapeId + ": "
                                    + invalidShape.getMessage(),
                            invalidShape);
                }
                physicsShapeIds.add(shape.physicsShapeId);
            }
        }

        new PhysicsShapeIdAllocator(state)
                .validatePersistedPhysicsShapeIds(physicsShapeIds.toArray());
    }
}
