package games.pixscape.runtime.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;

/**
 * Pure validation of the scene-wide persisted physics-shape identity domain.
 */
public final class PhysicsShapeIdentityValidator {
    private PhysicsShapeIdentityValidator() {}

    public static void validateEntities(
            World world, IntBag entities, PhysicsShapeIdState state) {
        if (world == null) throw new IllegalArgumentException(
                "World is required to validate physics shape identities.");
        if (entities == null) throw new IllegalArgumentException(
                "Entity set is required to validate physics shape identities.");

        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<SpatialBlocksComponent> blocksMapper =
                world.getMapper(SpatialBlocksComponent.class);
        int nextPhysicsShapeId =
                new PhysicsShapeIdAllocator(state).nextPhysicsShapeId();
        IntSet physicsShapeIds = new IntSet();
        int maxPhysicsShapeId = 0;
        int[] entityIds = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int ownerEntityId = entityIds[i];
            PhysicsShapesComponent component =
                    shapesMapper.getSafe(ownerEntityId, null);
            if (component == null) continue;
            if (component.shapes == null) throw new IllegalArgumentException(
                    "PhysicsShapesComponent on ownerEntityId " + ownerEntityId
                            + " has a null shapes collection.");
            try {
                PhysicsSpatialFootprintValidator.validateCollection(component.shapes);
            } catch (IllegalArgumentException invalidFootprint) {
                throw new IllegalArgumentException(
                        "Invalid Spatial footprint ownership on ownerEntityId "
                                + ownerEntityId + ": " + invalidFootprint.getMessage(),
                        invalidFootprint);
            }

            IntSet existingBlockIds = null;
            IntSet linkedBlockIds = null;
            for (int shapeIndex = 0; shapeIndex < component.shapes.size; shapeIndex++) {
                PhysicsShapeData shape = component.shapes.get(shapeIndex);
                if (shape == null) throw new IllegalArgumentException(
                        "Null PhysicsShapeData on ownerEntityId " + ownerEntityId
                                + " at shapeIndex " + shapeIndex + ".");
                try {
                    shape.validateStructure();
                } catch (IllegalArgumentException invalidShape) {
                    throw new IllegalArgumentException(
                            "Invalid physics shape on ownerEntityId " + ownerEntityId
                                    + ", physicsShapeId " + shape.physicsShapeId + ": "
                                    + invalidShape.getMessage(),
                            invalidShape);
                }
                if (!physicsShapeIds.add(shape.physicsShapeId)) {
                    throw invalid(ownerEntityId, shape,
                            "Duplicate scene-wide physicsShapeId.");
                }
                maxPhysicsShapeId =
                        Math.max(maxPhysicsShapeId, shape.physicsShapeId);
                if (shape.spatialBlockId == 0) continue;

                if (existingBlockIds == null) {
                    existingBlockIds = collectBlockIds(
                            blocksMapper, ownerEntityId, shape);
                    linkedBlockIds = new IntSet();
                }
                if (!existingBlockIds.contains(shape.spatialBlockId)) {
                    throw invalid(ownerEntityId, shape,
                            "referenced spatial block is missing on the same owner.");
                }
                if (!linkedBlockIds.add(shape.spatialBlockId)) {
                    throw invalid(ownerEntityId, shape,
                            "another linked shape already references this owner-local block.");
                }
            }
        }

        if (nextPhysicsShapeId <= maxPhysicsShapeId) {
            throw new IllegalArgumentException(
                    "nextPhysicsShapeId " + nextPhysicsShapeId
                            + " must be greater than maximum persisted physicsShapeId "
                            + maxPhysicsShapeId + ".");
        }
    }

    private static IntSet collectBlockIds(
            ComponentMapper<SpatialBlocksComponent> blocksMapper,
            int ownerEntityId,
            PhysicsShapeData diagnosticShape) {
        SpatialBlocksComponent blocks =
                blocksMapper.getSafe(ownerEntityId, null);
        if (blocks == null) throw invalid(ownerEntityId, diagnosticShape,
                "owner has no SpatialBlocksComponent.");
        if (blocks.blocks == null) throw invalid(ownerEntityId, diagnosticShape,
                "SpatialBlocksComponent has a null blocks collection.");

        IntSet blockIds = new IntSet(Math.max(1, blocks.blocks.size));
        for (int blockIndex = 0; blockIndex < blocks.blocks.size; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null) throw invalid(ownerEntityId, diagnosticShape,
                    "SpatialBlocksComponent contains a null block.");
            blockIds.add(block.id);
        }
        return blockIds;
    }

    private static IllegalArgumentException invalid(
            int ownerEntityId,
            PhysicsShapeData shape,
            String detail) {
        return new IllegalArgumentException(
                "Invalid physics shape relation on ownerEntityId " + ownerEntityId
                        + ", physicsShapeId " + shape.physicsShapeId
                        + ", spatialBlockId " + shape.spatialBlockId + ": " + detail);
    }
}
