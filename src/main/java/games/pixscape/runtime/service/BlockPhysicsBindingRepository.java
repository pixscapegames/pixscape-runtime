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
public final class BlockPhysicsBindingRepository implements BlockPhysicsBindingLookup {
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

    boolean isBoundTo(World expectedWorld, IdentityRegistry expectedIdentityRegistry) {
        return world == expectedWorld && identityRegistry == expectedIdentityRegistry;
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

    /** Rejects an owner whose published ECS relation no longer matches these indexes. */
    void validatePublishedOwnerState(int ownerStableId, int ownerEntityId,
                                     SpatialBlocksComponent blocks,
                                     BlockPhysicsBindingsComponent bindings,
                                     PhysicsShapesComponent shapes) {
        requireBound();
        OwnerBindingIndex indexed = indexes.bindingByOwnerAndBlock.get(ownerStableId);
        if (bindings == null) {
            if (indexed != null || indexes.blockByOwnerAndId.containsKey(ownerStableId)) {
                throw new IllegalStateException("Published binding repository is stale: ownerStableId="
                        + ownerStableId + " has no bindings component.");
            }
            return;
        }
        PreparedOwnerSnapshot current = prepareOwnerSnapshot(ownerStableId, ownerEntityId,
                blocks != null ? blocks.nextSpatialBlockId : -1,
                copyBlocks(blocks), copyBindings(bindings), copyShapes(shapes));
        if (!matches(indexed, indexes.blockByOwnerAndId.get(ownerStableId), current)) {
            throw new IllegalStateException("Published binding repository is stale: ownerEntityId="
                    + ownerEntityId + ", ownerStableId=" + ownerStableId + ".");
        }
    }

    /**
     * Prepares an owner-local replacement without scanning the World.  The
     * returned delta is deliberately small: it owns only this owner's indexes.
     */
    PreparedOwnerSnapshot prepareOwnerSnapshot(int ownerStableId, int ownerEntityId,
                                               int nextSpatialBlockId,
                                               Array<SpatialBlockData> blocks,
                                               Array<BlockPhysicsBindingData> bindings,
                                               Array<PhysicsShapeData> shapes) {
        requireBound();
        ValidatedOwnerState state = buildValidatedOwnerState(ownerEntityId, ownerStableId,
                nextSpatialBlockId, blocks, bindings, shapes);
        OwnerBindingIndex ownerIndex = state.ownerIndex;
        IntMap<SpatialBlockData> blockIndex = state.blockIndex;
        for (int i = 0; i < ownerIndex.ordered.size; i++) {
            BlockPhysicsBindingData binding = ownerIndex.ordered.get(i);
            Integer existingOwner = indexes.ownerEntityByPhysicsShapeId.get(binding.physicsShapeId);
            if (existingOwner != null && existingOwner.intValue() != ownerEntityId) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, "physicsShapeId is bound more than once in the World");
            }
        }
        return new PreparedOwnerSnapshot(this, world, ownerStableId, ownerEntityId, ownerIndex, blockIndex);
    }

    private ValidatedOwnerState buildValidatedOwnerState(int ownerEntityId, int ownerStableId,
                                                         int nextSpatialBlockId,
                                                         Array<SpatialBlockData> blocks,
                                                         Array<BlockPhysicsBindingData> bindings,
                                                         Array<PhysicsShapeData> shapes) {
        if (ownerEntityId < 0) throw invalid(ownerEntityId, ownerStableId, -1, -1,
                "owner entity ID must be non-negative");
        if (ownerStableId <= 0) throw invalid(ownerEntityId, ownerStableId, -1, -1,
                "owner stableId must be positive");
        if (blocks == null) throw invalid(ownerEntityId, ownerStableId, -1, -1,
                "blocks collection is null");
        if (bindings == null) throw invalid(ownerEntityId, ownerStableId, -1, -1,
                "bindings collection is null");
        if (shapes == null) throw invalid(ownerEntityId, ownerStableId, -1, -1,
                "physics shapes collection is null");
        if (nextSpatialBlockId <= 0) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "nextSpatialBlockId must be positive");
        }
        if (bindings.size == 0) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "published bindings component is empty");
        }
        OwnerBindingIndex ownerIndex = new OwnerBindingIndex();
        IntMap<SpatialBlockData> blockIndex = new IntMap<>();
        IntMap<PhysicsShapeData> shapesById = new IntMap<>();
        IntSet boundShapeIds = new IntSet();
        for (int i = 0; i < blocks.size; i++) {
            SpatialBlockData block = blocks.get(i);
            if (block == null) throw invalid(ownerEntityId, ownerStableId, -1, -1, "spatial block entry is null");
            if (block.id <= 0) throw invalid(ownerEntityId, ownerStableId, block.id, -1, "spatial block ID must be positive");
            if (blockIndex.containsKey(block.id)) throw invalid(ownerEntityId, ownerStableId, block.id, -1, "duplicate spatial block ID on the same owner");
            blockIndex.put(block.id, block.copy());
        }
        int maxBlockId = 0;
        for (IntMap.Entry<SpatialBlockData> entry : blockIndex) {
            if (entry.key > maxBlockId) maxBlockId = entry.key;
        }
        if (nextSpatialBlockId <= maxBlockId) {
                throw invalid(ownerEntityId, ownerStableId, maxBlockId, -1,
                    "nextSpatialBlockId must be greater than the maximum block ID");
        }
        ownerIndex.nextSpatialBlockId = nextSpatialBlockId;
        for (int i = 0; i < shapes.size; i++) {
            PhysicsShapeData shape = shapes.get(i);
            if (shape == null) throw invalid(ownerEntityId, ownerStableId, -1, -1, "physics shape entry is null");
            if (shape.physicsShapeId <= 0) throw invalid(ownerEntityId, ownerStableId, -1, shape.physicsShapeId, "physicsShapeId must be positive");
            if (shapesById.containsKey(shape.physicsShapeId)) throw invalid(ownerEntityId, ownerStableId, -1, shape.physicsShapeId, "duplicate physicsShapeId on the same owner");
            shapesById.put(shape.physicsShapeId, shape.copy());
        }
        for (int i = 0; i < bindings.size; i++) {
            BlockPhysicsBindingData source = bindings.get(i);
            if (source == null) throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "binding entry is null");
            BlockPhysicsBindingData binding = source.copy();
            if (ownerIndex.byBlock.containsKey(binding.spatialBlockId)) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, "block has more than one binding");
            }
            if (!boundShapeIds.add(binding.physicsShapeId)) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, "physics shape is bound more than once");
            }
            if (!blockIndex.containsKey(binding.spatialBlockId)) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, "binding references a block absent from the same owner");
            }
            if (!shapesById.containsKey(binding.physicsShapeId)) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, "binding references a physics shape absent from the same owner");
            }
            PhysicsShapeData shape = shapesById.get(binding.physicsShapeId);
            if (shape.directGeometry != null || !shape.enabled) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, shape.directGeometry != null
                                ? "direct-geometry shape cannot be bound"
                                : "linked shape must be enabled");
            }
            ownerIndex.byBlock.put(binding.spatialBlockId, binding);
            ownerIndex.ordered.add(binding);
        }
        for (IntMap.Entry<PhysicsShapeData> entry : shapesById) {
            if (entry.value.directGeometry == null && !boundShapeIds.contains(entry.key)) {
                throw invalid(ownerEntityId, ownerStableId, -1, entry.key,
                        "linked shape has no binding on the same owner; linked shape has no owner-local binding");
            }
        }
        return new ValidatedOwnerState(ownerIndex, blockIndex);
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

    private static Array<SpatialBlockData> copyBlocks(SpatialBlocksComponent source) {
        if (source == null) return null;
        Array<SpatialBlockData> result = new Array<>(SpatialBlockData[]::new);
        if (source.blocks == null) return null;
        for (int i = 0; i < source.blocks.size; i++) result.add(source.blocks.get(i));
        return result;
    }

    private static Array<BlockPhysicsBindingData> copyBindings(BlockPhysicsBindingsComponent source) {
        return source == null ? null : source.bindings;
    }

    private static Array<PhysicsShapeData> copyShapes(PhysicsShapesComponent source) {
        return source == null ? null : source.shapes;
    }

    private static boolean matches(OwnerBindingIndex indexed,
                                   IntMap<SpatialBlockData> indexedBlocks,
                                   PreparedOwnerSnapshot current) {
        if (indexed == null || indexedBlocks == null
                || indexed.nextSpatialBlockId != current.ownerIndex.nextSpatialBlockId
                || indexed.ordered.size != current.ownerIndex.ordered.size
                || indexedBlocks.size != current.blockIndex.size) return false;
        for (int i = 0; i < indexed.ordered.size; i++) {
            BlockPhysicsBindingData a = indexed.ordered.get(i);
            BlockPhysicsBindingData b = current.ownerIndex.ordered.get(i);
            if (a.spatialBlockId != b.spatialBlockId || a.physicsShapeId != b.physicsShapeId) return false;
        }
        for (IntMap.Entry<SpatialBlockData> entry : indexedBlocks) {
            SpatialBlockData other = current.blockIndex.get(entry.key);
            if (!sameBlock(entry.value, other)) return false;
        }
        return true;
    }

    private static boolean sameBlock(SpatialBlockData a, SpatialBlockData b) {
        if (a == b) return true;
        if (a == null || b == null || a.id != b.id || a.structureId != b.structureId
                || a.x != b.x || a.y != b.y || a.width != b.width || a.depth != b.depth
                || a.altitude != b.altitude || a.height != b.height
                || a.actorOccluder != b.actorOccluder || a.lightOccluder != b.lightOccluder
                || a.shadowCaster != b.shadowCaster || a.particleOccluder != b.particleOccluder
                || a.linkedTileRefsAuthored != b.linkedTileRefsAuthored) return false;
        if (a.name == null ? b.name != null : !a.name.equals(b.name)) return false;
        if (a.linkedTileRefs == null || b.linkedTileRefs == null) return a.linkedTileRefs == b.linkedTileRefs;
        if (a.linkedTileRefs.size != b.linkedTileRefs.size) return false;
        for (int i = 0; i < a.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef x = a.linkedTileRefs.get(i);
            SpatialBlockData.LinkedTileRef y = b.linkedTileRefs.get(i);
            if (x == null || y == null || x.gx != y.gx || x.gy != y.gy || x.tileAssetId != y.tileAssetId) return false;
        }
        return true;
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
        BlockPhysicsBindingsComponent bindings = mBindings.get(ownerEntityId);
        if (bindings == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "BlockPhysicsBindingsComponent is missing");
        }
        PhysicsShapesComponent shapes = mShapes.getSafe(ownerEntityId, null);
        if (shapes == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1,
                    "PhysicsShapesComponent is missing");
        }
        ValidatedOwnerState state = buildValidatedOwnerState(ownerEntityId, ownerStableId,
                blocks != null ? blocks.nextSpatialBlockId : -1,
                blocks != null ? blocks.blocks : null,
                bindings != null ? bindings.bindings : null,
                shapes != null ? shapes.shapes : null);
        for (int i = 0; i < state.ownerIndex.ordered.size; i++) {
            BlockPhysicsBindingData binding = state.ownerIndex.ordered.get(i);
            int physicsShapeId = binding.physicsShapeId;
            if (candidate.bindingByPhysicsShapeId.containsKey(physicsShapeId)) {
                throw invalid(ownerEntityId, ownerStableId, binding.spatialBlockId, physicsShapeId,
                        "physicsShapeId is bound more than once in the World");
            }
            candidate.bindingByPhysicsShapeId.put(physicsShapeId, binding);
            candidate.ownerEntityByPhysicsShapeId.put(physicsShapeId, ownerEntityId);
        }
        candidate.bindingByOwnerAndBlock.put(ownerStableId, state.ownerIndex);
        candidate.blockByOwnerAndId.put(ownerStableId, state.blockIndex);
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
        int nextSpatialBlockId;
        final IntMap<BlockPhysicsBindingData> byBlock = new IntMap<>();
        final Array<BlockPhysicsBindingData> ordered =
                new Array<>(BlockPhysicsBindingData[]::new);
    }

    private static final class ValidatedOwnerState {
        final OwnerBindingIndex ownerIndex;
        final IntMap<SpatialBlockData> blockIndex;

        ValidatedOwnerState(OwnerBindingIndex ownerIndex, IntMap<SpatialBlockData> blockIndex) {
            this.ownerIndex = ownerIndex;
            this.blockIndex = blockIndex;
        }
    }

    static final class PreparedOwnerSnapshot implements BlockPhysicsBindingLookup {
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

        @Override
        public BlockPhysicsBindingData findByPhysicsShapeId(int physicsShapeId) {
            if (ownerIndex == null || physicsShapeId <= 0) return null;
            for (int i = 0; i < ownerIndex.ordered.size; i++) {
                BlockPhysicsBindingData binding = ownerIndex.ordered.get(i);
                if (binding.physicsShapeId == physicsShapeId) return binding.copy();
            }
            return null;
        }

        @Override
        public SpatialBlockData findBlock(int stableId, int blockId) {
            if (ownerStableId != stableId || blockIndex == null) return null;
            SpatialBlockData block = blockIndex.get(blockId);
            return block != null ? block.copy() : null;
        }

        @Override
        public int findOwnerEntityByPhysicsShapeId(int physicsShapeId) {
            return findByPhysicsShapeId(physicsShapeId) != null ? ownerEntityId : -1;
        }
    }
}
