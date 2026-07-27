package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;

/**
 * World-scoped transient validator and query index for owner-local block bindings.
 *
 * <p>All mutable query results are defensive copies. Callers therefore cannot
 * mutate either the repository snapshot or the authored ECS components through
 * this API.</p>
 */
public final class BlockPhysicsBindingRepository {
    private World world;
    private IdentityRegistry identityRegistry;
    private IndexState indexes = new IndexState();

    public void bind(World world, IdentityRegistry identityRegistry) {
        if ((world == null) != (identityRegistry == null)) {
            throw new IllegalArgumentException(
                    "World and IdentityRegistry must be bound or detached together.");
        }
        this.world = world;
        this.identityRegistry = identityRegistry;
        this.indexes = new IndexState();
    }

    public void clear() {
        bind(null, null);
    }

    /**
     * Validates the complete owner-local model and atomically replaces all indexes.
     */
    public void rebuild() {
        requireBound();

        IndexState candidate = new IndexState();
        ComponentMapper<BlockPhysicsBindingsComponent> mBindings =
                world.getMapper(BlockPhysicsBindingsComponent.class);
        ComponentMapper<PixscapeIdentityComponent> mIdentity =
                world.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<SpatialBlocksComponent> mBlocks =
                world.getMapper(SpatialBlocksComponent.class);
        ComponentMapper<PhysicsShapesComponent> mShapes =
                world.getMapper(PhysicsShapesComponent.class);
        EntitySubscription subscription = world.getAspectSubscriptionManager().get(
                Aspect.all(BlockPhysicsBindingsComponent.class));

        IntBag entities = subscription.getEntities();
        int[] entityIds = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            validateAndIndexOwner(
                    entityIds[i], candidate, mBindings, mIdentity, mBlocks, mShapes);
        }

        validateAllLinkedShapes(candidate, mIdentity, mShapes);

        indexes = candidate;
    }

    public boolean hasAnyBindings() {
        return indexes.bindingByPhysicsShapeId.size > 0;
    }

    public boolean hasBinding(int ownerStableId, int blockId) {
        return findInternalByBlock(ownerStableId, blockId) != null;
    }

    public BlockPhysicsBindingData findByBlock(int ownerStableId, int blockId) {
        BlockPhysicsBindingData binding = findInternalByBlock(ownerStableId, blockId);
        return binding != null ? binding.copy() : null;
    }

    public BlockPhysicsBindingData findByPhysicsShapeId(int physicsShapeId) {
        if (physicsShapeId <= 0) return null;
        BlockPhysicsBindingData binding = indexes.bindingByPhysicsShapeId.get(physicsShapeId);
        return binding != null ? binding.copy() : null;
    }

    public SpatialBlockData findBlock(int ownerStableId, int blockId) {
        if (ownerStableId <= 0 || blockId <= 0) return null;
        IntMap<SpatialBlockData> ownerBlocks = indexes.blockByOwnerAndId.get(ownerStableId);
        if (ownerBlocks == null) return null;
        SpatialBlockData block = ownerBlocks.get(blockId);
        return block != null ? block.copy() : null;
    }

    public int findOwnerEntityByPhysicsShapeId(int physicsShapeId) {
        if (physicsShapeId <= 0) return -1;
        Integer ownerEntityId = indexes.ownerEntityByPhysicsShapeId.get(physicsShapeId);
        return ownerEntityId != null ? ownerEntityId : -1;
    }

    public void bindingsForOwner(
            int ownerStableId, Array<BlockPhysicsBindingData> out) {
        if (out == null) {
            throw new IllegalArgumentException("Output bindings collection cannot be null.");
        }
        out.clear();
        if (ownerStableId <= 0) return;

        OwnerBindingIndex ownerBindings = indexes.bindingByOwnerAndBlock.get(ownerStableId);
        if (ownerBindings == null) return;
        for (int i = 0, n = ownerBindings.ordered.size; i < n; i++) {
            out.add(ownerBindings.ordered.get(i).copy());
        }
    }

    /**
     * Prepares an owner-local replacement without scanning the World.  The
     * returned delta is deliberately small: it owns only this owner's indexes.
     */
    PreparedOwnerSnapshot prepareOwnerSnapshot(int ownerStableId, int ownerEntityId,
                                               Array<SpatialBlockData> blocks,
                                               Array<BlockPhysicsBindingData> bindings,
                                               Array<PhysicsShapeData> shapes) {
        requireBound();
        if (ownerStableId <= 0 || ownerEntityId < 0 || blocks == null || bindings == null
                || shapes == null) {
            throw new IllegalArgumentException("Validated owner snapshot is required.");
        }
        OwnerBindingIndex ownerIndex = new OwnerBindingIndex();
        IntMap<SpatialBlockData> blockIndex = new IntMap<>();
        IntMap<PhysicsShapeData> shapesById = new IntMap<>();
        IntSet boundShapeIds = new IntSet();
        for (int i = 0; i < blocks.size; i++) {
            SpatialBlockData block = blocks.get(i);
            if (block == null || block.id <= 0 || blockIndex.containsKey(block.id)) {
                throw new IllegalArgumentException("Prepared owner blocks are invalid.");
            }
            blockIndex.put(block.id, block.copy());
        }
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape == null || shape.physicsShapeId <= 0
                    || shape.directGeometry != null || !shape.enabled
                    || shapesById.containsKey(shape.physicsShapeId)) {
                throw new IllegalArgumentException("Prepared linked shapes are invalid.");
            }
            shapesById.put(shape.physicsShapeId, shape.copy());
        }
        for (int i = 0; i < bindings.size; i++) {
            BlockPhysicsBindingData source = bindings.get(i);
            if (source == null) throw new IllegalArgumentException("Prepared binding is null.");
            BlockPhysicsBindingData binding = source.copy();
            if (ownerIndex.byBlock.containsKey(binding.spatialBlockId)
                    || !boundShapeIds.add(binding.physicsShapeId)
                    || !blockIndex.containsKey(binding.spatialBlockId)
                    || !shapesById.containsKey(binding.physicsShapeId)) {
                throw new IllegalArgumentException("Prepared owner binding is invalid.");
            }
            Integer existingOwner = indexes.ownerEntityByPhysicsShapeId.get(binding.physicsShapeId);
            if (existingOwner != null && existingOwner.intValue() != ownerEntityId) {
                throw new IllegalStateException("Prepared binding delta conflicts with active repository.");
            }
            ownerIndex.byBlock.put(binding.spatialBlockId, binding);
            ownerIndex.ordered.add(binding);
        }
        return new PreparedOwnerSnapshot(this, world, ownerStableId, ownerEntityId, ownerIndex, blockIndex);
    }

    private void apply(PreparedOwnerSnapshot prepared) {
        requireBound();
        OwnerBindingIndex previous = indexes.bindingByOwnerAndBlock.get(prepared.ownerStableId);
        if (previous != null) {
            for (int i = 0; i < previous.ordered.size; i++) {
                int shapeId = previous.ordered.get(i).physicsShapeId;
                indexes.bindingByPhysicsShapeId.remove(shapeId);
                indexes.ownerEntityByPhysicsShapeId.remove(shapeId);
            }
        }
        indexes.bindingByOwnerAndBlock.put(prepared.ownerStableId, prepared.ownerIndex);
        indexes.blockByOwnerAndId.put(prepared.ownerStableId, prepared.blockIndex);
        for (int i = 0; i < prepared.ownerIndex.ordered.size; i++) {
            BlockPhysicsBindingData binding = prepared.ownerIndex.ordered.get(i);
            indexes.bindingByPhysicsShapeId.put(binding.physicsShapeId, binding);
            indexes.ownerEntityByPhysicsShapeId.put(binding.physicsShapeId, prepared.ownerEntityId);
        }
    }

    private void validateAndIndexOwner(
            int ownerEntityId,
            IndexState candidate,
            ComponentMapper<BlockPhysicsBindingsComponent> mBindings,
            ComponentMapper<PixscapeIdentityComponent> mIdentity,
            ComponentMapper<SpatialBlocksComponent> mBlocks,
            ComponentMapper<PhysicsShapesComponent> mShapes) {
        if (ownerEntityId < 0 || !world.getEntityManager().isActive(ownerEntityId)) {
            throw invalid(ownerEntityId, -1, -1, -1, "owner entity is inactive");
        }

        PixscapeIdentityComponent identity = mIdentity.getSafe(ownerEntityId, null);
        int ownerStableId = identity != null ? identity.stableId : -1;
        if (identity == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "PixscapeIdentityComponent is missing");
        }
        if (ownerStableId <= 0) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "owner stableId must be positive");
        }
        int indexedOwnerEntityId = identityRegistry.findByStableId(ownerStableId);
        if (indexedOwnerEntityId != ownerEntityId) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "bound IdentityRegistry does not map the owner stableId to this entity");
        }

        SpatialBlocksComponent blocks = mBlocks.getSafe(ownerEntityId, null);
        if (blocks == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "SpatialBlocksComponent is missing");
        }
        IntMap<SpatialBlockData> ownerBlocks =
                validateAndCopyBlocks(ownerEntityId, ownerStableId, blocks);

        BlockPhysicsBindingsComponent bindings = mBindings.get(ownerEntityId);
        if (bindings.bindings == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "bindings collection is null");
        }
        if (bindings.bindings.size == 0) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "published bindings component is empty");
        }

        PhysicsShapesComponent shapes = mShapes.getSafe(ownerEntityId, null);
        if (shapes == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "PhysicsShapesComponent is missing for a bound owner");
        }
        IntMap<PhysicsShapeData> shapesById =
                validateShapes(ownerEntityId, ownerStableId, shapes);

        OwnerBindingIndex ownerBindingIndex = new OwnerBindingIndex();
        IntSet boundShapeIds = new IntSet();
        for (int i = 0, n = bindings.bindings.size; i < n; i++) {
            BlockPhysicsBindingData binding = bindings.bindings.get(i);
            if (binding == null) {
                throw invalid(ownerEntityId, ownerStableId, -1, -1,
                        "binding entry is null");
            }
            int blockId = binding.spatialBlockId;
            int physicsShapeId = binding.physicsShapeId;
            if (blockId <= 0) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "spatialBlockId must be positive");
            }
            if (physicsShapeId <= 0) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "physicsShapeId must be positive");
            }
            if (!ownerBlocks.containsKey(blockId)) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "binding references a block absent from the same owner");
            }
            if (ownerBindingIndex.byBlock.containsKey(blockId)) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "a local block has more than one binding");
            }
            if (candidate.bindingByPhysicsShapeId.containsKey(physicsShapeId)) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "physicsShapeId is bound more than once in the World");
            }

            PhysicsShapeData shape = shapesById.get(physicsShapeId);
            if (shape == null) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "binding references a physics shape absent from the same owner");
            }
            if (shape.directGeometry != null) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "a direct-geometry shape cannot be bound");
            }
            if (!shape.enabled) {
                throw invalid(ownerEntityId, ownerStableId, blockId, physicsShapeId,
                        "a linked shape must be enabled");
            }

            BlockPhysicsBindingData snapshot = binding.copy();
            ownerBindingIndex.byBlock.put(blockId, snapshot);
            ownerBindingIndex.ordered.add(snapshot);
            candidate.bindingByPhysicsShapeId.put(physicsShapeId, snapshot);
            candidate.ownerEntityByPhysicsShapeId.put(physicsShapeId, ownerEntityId);
            boundShapeIds.add(physicsShapeId);
        }

        for (IntMap.Entry<PhysicsShapeData> entry : shapesById) {
            PhysicsShapeData shape = entry.value;
            boolean bound = boundShapeIds.contains(entry.key);
            if (shape.directGeometry == null && !bound) {
                throw invalid(ownerEntityId, ownerStableId, -1, entry.key,
                        "linked shape has no owner-local binding");
            }
            if (shape.directGeometry != null && bound) {
                throw invalid(ownerEntityId, ownerStableId, -1, entry.key,
                        "direct-geometry shape has an owner-local binding");
            }
        }

        candidate.bindingByOwnerAndBlock.put(ownerStableId, ownerBindingIndex);
        candidate.blockByOwnerAndId.put(ownerStableId, ownerBlocks);
    }

    private void validateAllLinkedShapes(
            IndexState candidate,
            ComponentMapper<PixscapeIdentityComponent> mIdentity,
            ComponentMapper<PhysicsShapesComponent> mShapes) {
        EntitySubscription subscription = world.getAspectSubscriptionManager().get(
                Aspect.all(PhysicsShapesComponent.class));
        IntBag entities = subscription.getEntities();
        int[] entityIds = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int entityId = entityIds[i];
            PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
            int ownerStableId = identity != null ? identity.stableId : -1;
            PhysicsShapesComponent shapes = mShapes.get(entityId);
            if (shapes.shapes == null) {
                throw invalid(entityId, ownerStableId, -1, -1,
                        "physics shapes collection is null");
            }
            for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                if (shape == null) {
                    throw invalid(entityId, ownerStableId, -1, -1,
                            "linked shape entry is null");
                }
                if (shape.directGeometry != null) {
                    continue;
                }

                int physicsShapeId = shape.physicsShapeId;
                if (physicsShapeId <= 0) {
                    throw invalid(entityId, ownerStableId, -1, physicsShapeId,
                            "linked shape physicsShapeId must be positive");
                }

                BlockPhysicsBindingData binding =
                        candidate.bindingByPhysicsShapeId.get(physicsShapeId);
                Integer expectedOwnerEntityId =
                        candidate.ownerEntityByPhysicsShapeId.get(physicsShapeId);
                if (binding == null || expectedOwnerEntityId == null) {
                    throw invalid(entityId, ownerStableId, -1, physicsShapeId,
                            "linked shape has no binding; directGeometry is missing and no "
                                    + "owner-local binding supplies geometry"
                                    + "; expected ownerEntityId is absent"
                                    + "; enabled=" + shape.enabled);
                }
                if (expectedOwnerEntityId != entityId) {
                    throw invalid(entityId, ownerStableId, binding.spatialBlockId,
                            physicsShapeId,
                            "linked shape is carried by another entity; expected ownerEntityId="
                                    + expectedOwnerEntityId);
                }
                if (!shape.enabled) {
                    throw invalid(entityId, ownerStableId, binding.spatialBlockId,
                            physicsShapeId, "linked shape is disabled");
                }
            }
        }
    }

    private IntMap<SpatialBlockData> validateAndCopyBlocks(
            int ownerEntityId, int ownerStableId, SpatialBlocksComponent blocks) {
        if (blocks.blocks == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "spatial blocks collection is null");
        }
        if (blocks.nextSpatialBlockId <= 0) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "nextSpatialBlockId must be positive");
        }

        IntMap<SpatialBlockData> ownerBlocks = new IntMap<>();
        int maxBlockId = 0;
        for (int i = 0, n = blocks.blocks.size; i < n; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            if (block == null) {
                throw invalid(ownerEntityId, ownerStableId, -1, -1,
                        "spatial block entry is null");
            }
            int blockId = block.id;
            if (blockId <= 0) {
                throw invalid(ownerEntityId, ownerStableId, blockId, -1,
                        "spatial block ID must be positive");
            }
            if (ownerBlocks.containsKey(blockId)) {
                throw invalid(ownerEntityId, ownerStableId, blockId, -1,
                        "duplicate spatial block ID on the same owner");
            }
            ownerBlocks.put(blockId, block.copy());
            if (blockId > maxBlockId) maxBlockId = blockId;
        }
        if (blocks.nextSpatialBlockId <= maxBlockId) {
            throw invalid(ownerEntityId, ownerStableId, maxBlockId, -1,
                    "nextSpatialBlockId must be greater than the maximum block ID");
        }
        return ownerBlocks;
    }

    private IntMap<PhysicsShapeData> validateShapes(
            int ownerEntityId, int ownerStableId, PhysicsShapesComponent shapes) {
        if (shapes.shapes == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "physics shapes collection is null");
        }
        IntMap<PhysicsShapeData> shapesById = new IntMap<>();
        for (int i = 0, n = shapes.shapes.size; i < n; i++) {
            PhysicsShapeData shape = shapes.shapes.get(i);
            if (shape == null) {
                throw invalid(ownerEntityId, ownerStableId, -1, -1,
                        "physics shape entry is null");
            }
            int physicsShapeId = shape.physicsShapeId;
            if (physicsShapeId <= 0) {
                throw invalid(ownerEntityId, ownerStableId, -1, physicsShapeId,
                        "physicsShapeId must be positive");
            }
            if (shapesById.containsKey(physicsShapeId)) {
                throw invalid(ownerEntityId, ownerStableId, -1, physicsShapeId,
                        "duplicate physicsShapeId on the same owner");
            }
            shapesById.put(physicsShapeId, shape);
        }
        return shapesById;
    }

    private BlockPhysicsBindingData findInternalByBlock(int ownerStableId, int blockId) {
        if (ownerStableId <= 0 || blockId <= 0) return null;
        OwnerBindingIndex ownerBindings = indexes.bindingByOwnerAndBlock.get(ownerStableId);
        return ownerBindings != null ? ownerBindings.byBlock.get(blockId) : null;
    }

    private void requireBound() {
        if (world == null || identityRegistry == null) {
            throw new IllegalStateException(
                    "BlockPhysicsBindingRepository must be bound before rebuild.");
        }
    }

    private IllegalStateException invalid(
            int ownerEntityId,
            int ownerStableId,
            int blockId,
            int physicsShapeId,
            String detail) {
        return new IllegalStateException(
                "Invalid block-physics binding state: ownerEntityId=" + ownerEntityId
                        + ", entityId=" + ownerEntityId
                        + ", ownerStableId=" + ownerStableId
                        + ", blockId=" + blockId
                        + ", physicsShapeId=" + physicsShapeId
                        + " (physicsShapeId " + physicsShapeId + ")"
                        + ": " + detail + ".");
    }

    private static final class IndexState {
        final IntMap<OwnerBindingIndex> bindingByOwnerAndBlock = new IntMap<>();
        final IntMap<IntMap<SpatialBlockData>> blockByOwnerAndId = new IntMap<>();
        final IntMap<BlockPhysicsBindingData> bindingByPhysicsShapeId = new IntMap<>();
        final IntMap<Integer> ownerEntityByPhysicsShapeId = new IntMap<>();
    }

    private static final class OwnerBindingIndex {
        final IntMap<BlockPhysicsBindingData> byBlock = new IntMap<>();
        final Array<BlockPhysicsBindingData> ordered =
                new Array<>(BlockPhysicsBindingData[]::new);
    }

    static final class PreparedOwnerSnapshot {
        private BlockPhysicsBindingRepository repository;
        private final World world;
        private final int ownerStableId;
        private final int ownerEntityId;
        private OwnerBindingIndex ownerIndex;
        private IntMap<SpatialBlockData> blockIndex;

        private PreparedOwnerSnapshot(BlockPhysicsBindingRepository repository, World world,
                                      int ownerStableId,
                                      int ownerEntityId, OwnerBindingIndex ownerIndex,
                                      IntMap<SpatialBlockData> blockIndex) {
            this.repository = repository;
            this.world = world;
            this.ownerStableId = ownerStableId;
            this.ownerEntityId = ownerEntityId;
            this.ownerIndex = ownerIndex;
            this.blockIndex = blockIndex;
        }

        void applyTo(BlockPhysicsBindingRepository target) {
            if (repository == null) {
                throw new IllegalStateException("Prepared repository snapshot was already consumed.");
            }
            if (repository != target) {
                throw new IllegalArgumentException("Prepared repository snapshot belongs to another repository.");
            }
            if (repository.world != world) {
                throw new IllegalStateException("Prepared repository snapshot belongs to a detached World.");
            }
            repository.apply(this);
            repository = null;
            ownerIndex = null;
            blockIndex = null;
        }
    }
}
