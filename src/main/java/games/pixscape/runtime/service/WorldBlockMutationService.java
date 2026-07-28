package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.DirtyTrackerSystem;

/** The sole runtime authority for transactional spatial block collision binds. */
public final class WorldBlockMutationService {
    private final World world;
    private final SceneMetaRuntime sceneMeta;
    private final IdentityRegistry identityRegistry;
    private final BlockPhysicsBindingRepository repository;
    private final PhysicsService physicsService;
    private final ComponentMapper<PixscapeIdentityComponent> identities;
    private final ComponentMapper<SpatialBlocksComponent> blocks;
    private final ComponentMapper<TiledLayerComponent> tiled;
    private final ComponentMapper<TransformComponent> transforms;
    private final ComponentMapper<PhysicsBodyComponent> bodies;
    private final ComponentMapper<PhysicsShapesComponent> shapes;
    private final ComponentMapper<BlockPhysicsBindingsComponent> bindings;
    private final ComponentMapper<PhysicsCompiledFixturesComponent> compiled;
    private boolean attached = true;

    public WorldBlockMutationService(World world, SceneMetaRuntime sceneMeta,
                                     IdentityRegistry identityRegistry,
                                     BlockPhysicsBindingRepository repository,
                                     PhysicsService physicsService) {
        if (world == null || sceneMeta == null || identityRegistry == null
                || repository == null || physicsService == null) {
            throw new IllegalArgumentException("World mutation service dependencies are required.");
        }
        this.world = world;
        this.sceneMeta = sceneMeta;
        this.identityRegistry = identityRegistry;
        this.repository = repository;
        this.physicsService = physicsService;
        identities = world.getMapper(PixscapeIdentityComponent.class);
        blocks = world.getMapper(SpatialBlocksComponent.class);
        tiled = world.getMapper(TiledLayerComponent.class);
        transforms = world.getMapper(TransformComponent.class);
        bodies = world.getMapper(PhysicsBodyComponent.class);
        shapes = world.getMapper(PhysicsShapesComponent.class);
        bindings = world.getMapper(BlockPhysicsBindingsComponent.class);
        compiled = world.getMapper(PhysicsCompiledFixturesComponent.class);
    }

    public int bindBlockCollision(int ownerStableId, int spatialBlockId) {
        requireAttached();
        PreparedWorldBlockMutation prepared = prepareBind(ownerStableId, spatialBlockId);
        PreparedWorldBlockMutation.Publication publication = prepared.takePublication();
        // Consume every throwing transfer before the first ECS mutation.
        publish(publication);
        return publication.physicsShapeId;
    }

    /** Removes the dedicated linked shape and binding for one spatial block. */
    public void removeBlockCollision(int ownerStableId, int spatialBlockId) {
        requireAttached();
        PreparedWorldBlockMutation prepared = prepareRemove(ownerStableId, spatialBlockId);
        publish(prepared.takePublication());
    }

    public void detach() {
        attached = false;
    }

    PreparedWorldBlockMutation prepareBind(int ownerStableId, int spatialBlockId) {
        requireAttached();
        OwnerState owner = validateOwner(ownerStableId, spatialBlockId, false);
        if (repository.hasBinding(ownerStableId, spatialBlockId)) {
            throw invalid(owner.entityId, ownerStableId, spatialBlockId, -1,
                    "spatial block is already bound");
        }

        Array<BlockPhysicsBindingData> nextBindings = copyBindings(owner.bindings);
        Array<PhysicsShapeData> nextShapes = copyShapes(owner.shapes);
        int physicsShapeId = physicsService.allocateNewPhysicsShapeId();
        PhysicsShapeData shape = PhysicsService.createDefaultShape(physicsShapeId);
        shape.directGeometry = null;
        shape.enabled = true;
        BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
        binding.spatialBlockId = spatialBlockId;
        binding.physicsShapeId = physicsShapeId;
        nextBindings.add(binding);
        nextShapes.add(shape);

        Array<SpatialBlockData> blockCopies = copyBlocks(owner.blocks);
        BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot =
                repository.prepareOwnerSnapshot(ownerStableId, owner.entityId,
                        owner.blocks.nextSpatialBlockId, blockCopies,
                        nextBindings, nextShapes);
        PreparedPhysicsBodyCandidate preparedPhysics = PhysicsService.prepareLinkedBodyCandidate(
                nextShapes, owner.entityId, ownerStableId, owner.tiled.data,
                sceneMeta.pixelsPerMeter, repositorySnapshot);
        return new PreparedWorldBlockMutation(owner.entityId, physicsShapeId,
                nextBindings, preparedPhysics, repositorySnapshot,
                !transforms.has(owner.entityId), !bodies.has(owner.entityId));
    }

    PreparedWorldBlockMutation prepareRemove(int ownerStableId, int spatialBlockId) {
        requireAttached();
        OwnerState owner = validateOwner(ownerStableId, spatialBlockId, true);
        if (owner.bindings == null || owner.shapes == null) {
            throw unbindInvalid(owner.entityId, ownerStableId, spatialBlockId, -1,
                    "spatial block is not bound");
        }
        BlockPhysicsBindingData targetBinding = findBinding(owner.bindings.bindings, spatialBlockId);
        if (targetBinding == null) {
            throw unbindInvalid(owner.entityId, ownerStableId, spatialBlockId, -1,
                    "spatial block is not bound");
        }
        PhysicsShapeData targetShape = findShape(owner.shapes.shapes, targetBinding.physicsShapeId);
        if (targetShape == null || targetShape.directGeometry != null || !targetShape.enabled
                || repository.findOwnerEntityByPhysicsShapeId(targetBinding.physicsShapeId) != owner.entityId
                || !sameBinding(repository.findByBlock(ownerStableId, spatialBlockId), targetBinding)) {
            throw unbindInvalid(owner.entityId, ownerStableId, spatialBlockId,
                    targetBinding.physicsShapeId, "target binding or linked shape is inconsistent");
        }

        Array<BlockPhysicsBindingData> remainingBindings = copyBindings(owner.bindings);
        Array<PhysicsShapeData> remainingShapes = copyShapes(owner.shapes);
        removeBinding(remainingBindings, spatialBlockId, targetBinding.physicsShapeId);
        removeShape(remainingShapes, targetBinding.physicsShapeId);
        if (remainingBindings.size != remainingShapes.size) {
            throw unbindInvalid(owner.entityId, ownerStableId, spatialBlockId,
                    targetBinding.physicsShapeId, "remaining binding and shape counts differ");
        }
        if (remainingBindings.size == 0) {
            if (remainingShapes.size != 0) {
                throw unbindInvalid(owner.entityId, ownerStableId, spatialBlockId,
                        targetBinding.physicsShapeId, "last binding removal left a shape");
            }
            return PreparedWorldBlockMutation.removeReservedAggregate(owner.entityId,
                    repository.prepareOwnerRemoval(ownerStableId, owner.entityId));
        }

        Array<SpatialBlockData> blockCopies = copyBlocks(owner.blocks);
        BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot =
                repository.prepareOwnerSnapshot(ownerStableId, owner.entityId,
                        owner.blocks.nextSpatialBlockId, blockCopies,
                        remainingBindings, remainingShapes);
        PreparedPhysicsBodyCandidate preparedPhysics = PhysicsService.prepareLinkedBodyCandidate(
                remainingShapes, owner.entityId, ownerStableId, owner.tiled.data,
                sceneMeta.pixelsPerMeter, repositorySnapshot);
        return new PreparedWorldBlockMutation(owner.entityId, -1, remainingBindings,
                preparedPhysics, repositorySnapshot, false, false);
    }

    private void publish(PreparedWorldBlockMutation.Publication publication) {
        int entityId = publication.ownerEntityId;
        if (publication.removeReservedAggregate) {
            bindings.remove(entityId);
            shapes.remove(entityId);
            compiled.remove(entityId);
            bodies.remove(entityId);
            publication.repositorySnapshot.applyTo(repository);
            markPhysicsDirty(entityId);
            return;
        }
        TransformComponent transform = transforms.has(entityId)
                ? transforms.get(entityId) : transforms.create(entityId);
        if (publication.createTransform) transform.refreshCaches();
        PhysicsBodyComponent body = bodies.has(entityId) ? bodies.get(entityId) : bodies.create(entityId);
        if (publication.createBody) initializeReservedBody(body);
        BlockPhysicsBindingsComponent targetBindings = bindings.has(entityId)
                ? bindings.get(entityId) : bindings.create(entityId);
        targetBindings.bindings = publication.bindings;
        PhysicsShapesComponent targetShapes = shapes.has(entityId) ? shapes.get(entityId) : shapes.create(entityId);
        PhysicsCompiledFixturesComponent targetCompiled = compiled.has(entityId)
                ? compiled.get(entityId) : compiled.create(entityId);
        PhysicsService.publishPreparedData(targetShapes, targetCompiled,
                publication.shapes, publication.fixtures);
        publication.repositorySnapshot.applyTo(repository);
        markPhysicsDirty(entityId);
    }

    private OwnerState validateOwner(int ownerStableId, int spatialBlockId, boolean unbinding) {
        if (!sceneMeta.physicsEnabled) throw operationInvalid(unbinding, -1, ownerStableId, spatialBlockId, -1, "physics is disabled");
        if (ownerStableId <= 0 || spatialBlockId <= 0) {
            throw operationInvalid(unbinding, -1, ownerStableId, spatialBlockId, -1, "owner and block IDs must be positive");
        }
        if (Float.isNaN(sceneMeta.pixelsPerMeter) || Float.isInfinite(sceneMeta.pixelsPerMeter)
                || sceneMeta.pixelsPerMeter <= 0f) {
            throw operationInvalid(unbinding, -1, ownerStableId, spatialBlockId, -1, "pixelsPerMeter must be positive and finite");
        }
        int ownerEntityId = identityRegistry.findByStableId(ownerStableId);
        if (ownerEntityId < 0 || !world.getEntityManager().isActive(ownerEntityId)) {
            throw operationInvalid(unbinding, ownerEntityId, ownerStableId, spatialBlockId, -1, "owner is absent or inactive");
        }
        PixscapeIdentityComponent identity = identities.getSafe(ownerEntityId, null);
        if (identity == null || identity.stableId != ownerStableId) {
            throw operationInvalid(unbinding, ownerEntityId, ownerStableId, spatialBlockId, -1, "owner identity is inconsistent");
        }
        SpatialBlocksComponent ownerBlocks = blocks.getSafe(ownerEntityId, null);
        if (ownerBlocks == null || ownerBlocks.blocks == null || findBlock(ownerBlocks.blocks, spatialBlockId) == null) {
            throw operationInvalid(unbinding, ownerEntityId, ownerStableId, spatialBlockId, -1, "spatial block is absent");
        }
        TiledLayerComponent layer = tiled.getSafe(ownerEntityId, null);
        if (layer == null || layer.data == null) {
            throw operationInvalid(unbinding, ownerEntityId, ownerStableId, spatialBlockId, -1, "TiledLayerComponent.data is required");
        }
        BlockPhysicsBindingsComponent ownerBindings = bindings.getSafe(ownerEntityId, null);
        PhysicsShapesComponent ownerShapes = shapes.getSafe(ownerEntityId, null);
        try {
            validateOwnerAggregate(ownerEntityId, ownerStableId, ownerBindings, ownerShapes);
            repository.validatePublishedOwnerState(ownerStableId, ownerEntityId, ownerBlocks,
                    ownerBindings, ownerShapes);
        } catch (RuntimeException error) {
            if (unbinding) {
                throw unbindInvalid(ownerEntityId, ownerStableId, spatialBlockId, -1,
                        "published owner aggregate is invalid or stale: " + error.getMessage());
            }
            throw error;
        }
        return new OwnerState(ownerEntityId, ownerBlocks, ownerBindings, ownerShapes, layer);
    }

    private void requireAttached() {
        if (!attached || !identityRegistry.isBoundTo(world, sceneMeta)
                || !repository.isBoundTo(world, identityRegistry)) {
            throw new IllegalStateException("WorldBlockMutationService is detached from its World.");
        }
    }

    private void validateOwnerAggregate(int entityId, int stableId,
                                        BlockPhysicsBindingsComponent ownerBindings,
                                        PhysicsShapesComponent ownerShapes) {
        if (ownerBindings == null) {
            if (bodies.has(entityId) || shapes.has(entityId) || compiled.has(entityId)) {
                throw invalid(entityId, stableId, -1, -1, "unbound owner contains manual physics");
            }
            if (transforms.has(entityId)) PhysicsService.validateReservedTransform(entityId, stableId, transforms.get(entityId));
            rejectJoint(entityId, stableId);
            return;
        }
        if (ownerBindings.bindings == null || ownerBindings.bindings.size == 0 || ownerShapes == null
                || !bodies.has(entityId) || !compiled.has(entityId) || !transforms.has(entityId)) {
            throw invalid(entityId, stableId, -1, -1, "bound owner aggregate is incomplete");
        }
        PhysicsService.validateReservedTransform(entityId, stableId, transforms.get(entityId));
        PhysicsService.validateReservedBody(entityId, stableId, bodies.get(entityId));
        if (!compiled.get(entityId).valid) throw invalid(entityId, stableId, -1, -1, "compiled cache is invalid");
        if (ownerBindings.bindings.size != ownerShapes.shapes.size) {
            throw invalid(entityId, stableId, -1, -1, "binding and shape counts differ");
        }
        for (int i = 0; i < ownerShapes.shapes.size; i++) {
            PhysicsShapeData shape = ownerShapes.shapes.get(i);
            if (shape == null || shape.directGeometry != null || !shape.enabled
                    || repository.findByPhysicsShapeId(shape.physicsShapeId) == null) {
                throw invalid(entityId, stableId, -1, shape != null ? shape.physicsShapeId : -1,
                        "bound owner shape is invalid or orphaned");
            }
        }
        rejectJoint(entityId, stableId);
    }

    private void rejectJoint(int entityId, int stableId) {
        IntBag jointIds = world.getAspectSubscriptionManager().get(Aspect.all(PhysicsJointComponent.class)).getEntities();
        ComponentMapper<PhysicsJointComponent> jointMapper = world.getMapper(PhysicsJointComponent.class);
        for (int i = 0; i < jointIds.size(); i++) {
            PhysicsJointComponent joint = jointMapper.get(jointIds.get(i));
            if (joint.aEid == entityId || joint.bEid == entityId) {
                throw invalid(entityId, stableId, -1, -1, "joint references reserved spatial body");
            }
        }
    }

    private static Array<BlockPhysicsBindingData> copyBindings(BlockPhysicsBindingsComponent source) {
        Array<BlockPhysicsBindingData> result = new Array<>(BlockPhysicsBindingData[]::new);
        if (source != null && source.bindings != null) for (int i = 0; i < source.bindings.size; i++) result.add(source.bindings.get(i).copy());
        return result;
    }

    private static Array<PhysicsShapeData> copyShapes(PhysicsShapesComponent source) {
        Array<PhysicsShapeData> result = new Array<>(true, source != null && source.shapes != null ? source.shapes.size : 1, PhysicsShapeData.class);
        if (source != null && source.shapes != null) for (int i = 0; i < source.shapes.size; i++) result.add(source.shapes.get(i).copy());
        return result;
    }

    private static Array<SpatialBlockData> copyBlocks(SpatialBlocksComponent source) {
        Array<SpatialBlockData> result = new Array<>(SpatialBlockData[]::new);
        for (int i = 0; i < source.blocks.size; i++) result.add(source.blocks.get(i).copy());
        return result;
    }

    private static SpatialBlockData findBlock(Array<SpatialBlockData> source, int blockId) {
        for (int i = 0; i < source.size; i++) if (source.get(i) != null && source.get(i).id == blockId) return source.get(i);
        return null;
    }

    private static BlockPhysicsBindingData findBinding(Array<BlockPhysicsBindingData> source, int blockId) {
        if (source == null) return null;
        for (int i = 0; i < source.size; i++) {
            BlockPhysicsBindingData binding = source.get(i);
            if (binding != null && binding.spatialBlockId == blockId) return binding;
        }
        return null;
    }

    private static PhysicsShapeData findShape(Array<PhysicsShapeData> source, int shapeId) {
        if (source == null) return null;
        for (int i = 0; i < source.size; i++) {
            PhysicsShapeData shape = source.get(i);
            if (shape != null && shape.physicsShapeId == shapeId) return shape;
        }
        return null;
    }

    private static void removeBinding(Array<BlockPhysicsBindingData> source, int blockId, int shapeId) {
        for (int i = 0; i < source.size; i++) {
            BlockPhysicsBindingData binding = source.get(i);
            if (binding.spatialBlockId == blockId && binding.physicsShapeId == shapeId) {
                source.removeIndex(i);
                return;
            }
        }
        throw new IllegalStateException("Prepared target binding is missing.");
    }

    private static void removeShape(Array<PhysicsShapeData> source, int shapeId) {
        for (int i = 0; i < source.size; i++) {
            if (source.get(i).physicsShapeId == shapeId) {
                source.removeIndex(i);
                return;
            }
        }
        throw new IllegalStateException("Prepared target shape is missing.");
    }

    private static boolean sameBinding(BlockPhysicsBindingData a, BlockPhysicsBindingData b) {
        return a != null && b != null && a.spatialBlockId == b.spatialBlockId
                && a.physicsShapeId == b.physicsShapeId;
    }

    private void markPhysicsDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.physics(entityId, PhysicsDirtyBits.ALL);
    }

    private static void initializeReservedBody(PhysicsBodyComponent body) {
        body.type = PhysicsBodyComponent.STATIC;
        body.fixedRotation = true;
        body.bullet = false;
        body.allowSleep = true;
        body.awake = true;
        body.gravityScale = 1f;
        body.linearDamping = 0f;
        body.angularDamping = 0f;
    }

    private static IllegalArgumentException invalid(int entityId, int stableId, int blockId,
                                                    int shapeId, String detail) {
        return new IllegalArgumentException("Invalid spatial physics bind: ownerEntityId=" + entityId
                + ", ownerStableId=" + stableId + ", spatialBlockId=" + blockId
                + ", physicsShapeId=" + shapeId + ": " + detail + ".");
    }

    private static IllegalArgumentException unbindInvalid(int entityId, int stableId, int blockId,
                                                          int shapeId, String detail) {
        return new IllegalArgumentException("Invalid spatial physics unbind: ownerEntityId=" + entityId
                + ", ownerStableId=" + stableId + ", spatialBlockId=" + blockId
                + ", physicsShapeId=" + shapeId + ": " + detail + ".");
    }

    private static IllegalArgumentException operationInvalid(boolean unbinding, int entityId,
                                                             int stableId, int blockId,
                                                             int shapeId, String detail) {
        return unbinding ? unbindInvalid(entityId, stableId, blockId, shapeId, detail)
                : invalid(entityId, stableId, blockId, shapeId, detail);
    }

    private static final class OwnerState {
        final int entityId;
        final SpatialBlocksComponent blocks;
        final BlockPhysicsBindingsComponent bindings;
        final PhysicsShapesComponent shapes;
        final TiledLayerComponent tiled;

        OwnerState(int entityId, SpatialBlocksComponent blocks,
                   BlockPhysicsBindingsComponent bindings, PhysicsShapesComponent shapes,
                   TiledLayerComponent tiled) {
            this.entityId = entityId;
            this.blocks = blocks;
            this.bindings = bindings;
            this.shapes = shapes;
            this.tiled = tiled;
        }
    }
}
