package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdentityValidator;
import games.pixscape.runtime.physics.PhysicsShapeIdState;
import games.pixscape.runtime.spatial.SpatialBlockData;

/**
 * World-scoped derived index for physics shapes linked to authored spatial blocks.
 */
public final class SpatialBlockPhysicsRegistry {
    private World world;
    private IdentityRegistry identityRegistry;
    private PhysicsShapeIdState physicsShapeIdState;

    private IntMap<LinkedShapeRef> byPhysicsShapeId = new IntMap<>();
    private IntMap<IntMap<Integer>> physicsShapeIdByOwnerThenBlock =
            new IntMap<>();
    private int generation;

    public void bind(
            World world,
            IdentityRegistry identityRegistry,
            PhysicsShapeIdState physicsShapeIdState) {
        boolean allNull = world == null
                && identityRegistry == null
                && physicsShapeIdState == null;
        boolean allPresent = world != null
                && identityRegistry != null
                && physicsShapeIdState != null;
        if (!allNull && !allPresent) {
            throw new IllegalArgumentException(
                    "World, IdentityRegistry, and PhysicsShapeIdState "
                            + "must be either all null or all non-null.");
        }
        if (allNull) {
            detach();
            return;
        }
        if (!identityRegistry.isBoundTo(world)) {
            throw new IllegalArgumentException(
                    "IdentityRegistry must be bound to the same World as "
                            + "SpatialBlockPhysicsRegistry.");
        }
        if (this.world == world
                && this.identityRegistry == identityRegistry
                && this.physicsShapeIdState == physicsShapeIdState) {
            return;
        }

        clearPublishedState();
        this.world = world;
        this.identityRegistry = identityRegistry;
        this.physicsShapeIdState = physicsShapeIdState;
        generation++;
    }

    public void detach() {
        if (world == null
                && identityRegistry == null
                && physicsShapeIdState == null) {
            return;
        }
        clearPublishedState();
        world = null;
        identityRegistry = null;
        physicsShapeIdState = null;
        generation++;
    }

    public void rebuild() {
        if (world == null
                || identityRegistry == null
                || physicsShapeIdState == null) {
            throw new IllegalStateException(
                    "SpatialBlockPhysicsRegistry is detached.");
        }
        if (!identityRegistry.isBoundTo(world)) {
            throw new IllegalStateException(
                    "SpatialBlockPhysicsRegistry dependencies are no longer "
                            + "bound to the same World.");
        }

        PhysicsShapeIdentityValidator.validateWorld(
                world, physicsShapeIdState);

        IntMap<LinkedShapeRef> candidateByPhysicsShapeId = new IntMap<>();
        IntMap<IntMap<Integer>> candidateByOwnerThenBlock = new IntMap<>();
        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                world.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PixscapeIdentityComponent> identityMapper =
                world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<SpatialBlocksComponent> blocksMapper =
                world.getMapper(SpatialBlocksComponent.class);
        IntBag owners = world.getAspectSubscriptionManager()
                .get(Aspect.all(PhysicsShapesComponent.class))
                .getEntities();
        int[] ownerEntityIds = owners.getData();

        for (int ownerIndex = 0; ownerIndex < owners.size(); ownerIndex++) {
            int ownerEntityId = ownerEntityIds[ownerIndex];
            if (!world.getEntityManager().isActive(ownerEntityId)) continue;
            PhysicsShapesComponent shapes = shapesMapper.get(ownerEntityId);
            OwnerBlockContext ownerContext = null;
            for (int shapeIndex = 0;
                    shapeIndex < shapes.shapes.size;
                    shapeIndex++) {
                PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                if (shape.spatialBlockId <= 0) continue;

                // The complete block set is validated and indexed once per linked owner.
                // Per-shape relation checks below are expected O(1).
                if (ownerContext == null) {
                    ownerContext = buildOwnerBlockContext(
                            identityMapper, blocksMapper,
                            ownerEntityId, shape);
                }
                indexLinkedRelation(
                        candidateByPhysicsShapeId,
                        candidateByOwnerThenBlock,
                        ownerContext,
                        shape);
            }
        }

        byPhysicsShapeId = candidateByPhysicsShapeId;
        physicsShapeIdByOwnerThenBlock = candidateByOwnerThenBlock;
        generation++;
    }

    public boolean isBoundTo(World world) {
        return world != null && this.world == world;
    }

    public int generation() {
        return generation;
    }

    public LinkedShapeRef findByPhysicsShapeId(int physicsShapeId) {
        if (world == null) return null;
        return byPhysicsShapeId.get(physicsShapeId);
    }

    public int findPhysicsShapeId(
            int ownerStableId, int spatialBlockId) {
        if (world == null) return -1;
        IntMap<Integer> byBlock =
                physicsShapeIdByOwnerThenBlock.get(ownerStableId);
        if (byBlock == null) return -1;
        Integer physicsShapeId = byBlock.get(spatialBlockId);
        return physicsShapeId != null ? physicsShapeId.intValue() : -1;
    }

    public boolean hasLinkedShape(
            int ownerStableId, int spatialBlockId) {
        return findPhysicsShapeId(ownerStableId, spatialBlockId) != -1;
    }

    private OwnerBlockContext buildOwnerBlockContext(
            ComponentMapper<PixscapeIdentityComponent> identityMapper,
            ComponentMapper<SpatialBlocksComponent> blocksMapper,
            int ownerEntityId,
            PhysicsShapeData diagnosticShape) {
        int spatialBlockId = diagnosticShape != null
                ? diagnosticShape.spatialBlockId : -1;
        int physicsShapeId = diagnosticShape != null
                ? diagnosticShape.physicsShapeId : -1;
        PixscapeIdentityComponent identity =
                identityMapper.getSafe(ownerEntityId, null);
        int ownerStableId = identity != null ? identity.stableId : -1;

        if (identity == null || ownerStableId <= 0) {
            throw invalidLink(
                    ownerEntityId, ownerStableId, spatialBlockId,
                    physicsShapeId, "owner has no positive stable identity.");
        }
        if (identityRegistry.findByStableId(ownerStableId)
                != ownerEntityId) {
            throw invalidLink(
                    ownerEntityId, ownerStableId, spatialBlockId,
                    physicsShapeId,
                    "IdentityRegistry does not resolve the owner identity.");
        }

        SpatialBlocksComponent blocks =
                blocksMapper.getSafe(ownerEntityId, null);
        if (blocks == null) {
            throw invalidLink(
                    ownerEntityId, ownerStableId, spatialBlockId,
                    physicsShapeId,
                    "owner has no SpatialBlocksComponent.");
        }
        if (blocks.blocks == null) {
            throw invalidLink(
                    ownerEntityId, ownerStableId, spatialBlockId,
                    physicsShapeId,
                    "SpatialBlocksComponent has a null blocks collection.");
        }
        if (blocks.nextSpatialBlockId <= 0) {
            throw invalidLink(
                    ownerEntityId, ownerStableId, spatialBlockId,
                    physicsShapeId,
                    "nextSpatialBlockId must be strictly positive.");
        }

        IntSet blockIds = new IntSet(Math.max(1, blocks.blocks.size));
        int maxBlockId = 0;
        for (int blockIndex = 0;
                blockIndex < blocks.blocks.size;
                blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null) {
                throw invalidLink(
                        ownerEntityId, ownerStableId, spatialBlockId,
                        physicsShapeId,
                        "SpatialBlocksComponent contains a null block.");
            }
            if (block.id <= 0) {
                throw invalidLink(
                        ownerEntityId, ownerStableId, block.id,
                        physicsShapeId,
                        "spatial block ID must be strictly positive.");
            }
            if (!blockIds.add(block.id)) {
                throw invalidLink(
                        ownerEntityId, ownerStableId, block.id,
                        physicsShapeId,
                        "duplicate spatial block ID " + block.id + ".");
            }
            if (block.id > maxBlockId) maxBlockId = block.id;
        }
        if (blocks.nextSpatialBlockId <= maxBlockId) {
            throw invalidLink(
                    ownerEntityId, ownerStableId, spatialBlockId,
                    physicsShapeId,
                    "nextSpatialBlockId " + blocks.nextSpatialBlockId
                            + " must be greater than maximum block ID "
                            + maxBlockId + ".");
        }

        return new OwnerBlockContext(
                ownerEntityId, ownerStableId, blockIds);
    }

    private static void indexLinkedRelation(
            IntMap<LinkedShapeRef> candidateByPhysicsShapeId,
            IntMap<IntMap<Integer>> candidateByOwnerThenBlock,
            OwnerBlockContext owner,
            PhysicsShapeData shape) {
        int spatialBlockId = shape.spatialBlockId;
        int physicsShapeId = shape.physicsShapeId;
        if (!owner.blockIds.contains(spatialBlockId)) {
            throw invalidLink(
                    owner.ownerEntityId, owner.ownerStableId,
                    spatialBlockId, physicsShapeId,
                    "referenced spatial block is missing.");
        }
        if (candidateByPhysicsShapeId.containsKey(physicsShapeId)) {
            throw invalidLink(
                    owner.ownerEntityId, owner.ownerStableId,
                    spatialBlockId, physicsShapeId,
                    "physicsShapeId is already indexed.");
        }

        IntMap<Integer> byBlock =
                candidateByOwnerThenBlock.get(owner.ownerStableId);
        if (byBlock == null) {
            byBlock = new IntMap<>();
            candidateByOwnerThenBlock.put(owner.ownerStableId, byBlock);
        }
        Integer existing = byBlock.get(spatialBlockId);
        if (existing != null) {
            throw invalidLink(
                    owner.ownerEntityId, owner.ownerStableId, spatialBlockId,
                    physicsShapeId,
                    "owner/block is already linked to physicsShapeId "
                            + existing + ".");
        }

        LinkedShapeRef ref = new LinkedShapeRef(
                physicsShapeId, owner.ownerEntityId,
                owner.ownerStableId, spatialBlockId);
        candidateByPhysicsShapeId.put(physicsShapeId, ref);
        byBlock.put(spatialBlockId, physicsShapeId);
    }

    private static final class OwnerBlockContext {
        final int ownerEntityId;
        final int ownerStableId;
        final IntSet blockIds;

        OwnerBlockContext(
                int ownerEntityId,
                int ownerStableId,
                IntSet blockIds) {
            this.ownerEntityId = ownerEntityId;
            this.ownerStableId = ownerStableId;
            this.blockIds = blockIds;
        }
    }

    private void clearPublishedState() {
        byPhysicsShapeId = new IntMap<>();
        physicsShapeIdByOwnerThenBlock = new IntMap<>();
    }

    private static IllegalArgumentException invalidLink(
            int ownerEntityId,
            int ownerStableId,
            int spatialBlockId,
            int physicsShapeId,
            String detail) {
        return new IllegalArgumentException(
                "Invalid linked physics shape: ownerEntityId "
                        + ownerEntityId + ", ownerStableId "
                        + ownerStableId + ", spatialBlockId "
                        + spatialBlockId + ", physicsShapeId "
                        + physicsShapeId + ": " + detail);
    }

    /**
     * Immutable durable lookup result without authored or ECS references.
     */
    public static final class LinkedShapeRef {
        private final int physicsShapeId;
        private final int ownerEntityId;
        private final int ownerStableId;
        private final int spatialBlockId;

        private LinkedShapeRef(
                int physicsShapeId,
                int ownerEntityId,
                int ownerStableId,
                int spatialBlockId) {
            this.physicsShapeId = physicsShapeId;
            this.ownerEntityId = ownerEntityId;
            this.ownerStableId = ownerStableId;
            this.spatialBlockId = spatialBlockId;
        }

        public int physicsShapeId() {
            return physicsShapeId;
        }

        public int ownerEntityId() {
            return ownerEntityId;
        }

        public int ownerStableId() {
            return ownerStableId;
        }

        public int spatialBlockId() {
            return spatialBlockId;
        }
    }
}
