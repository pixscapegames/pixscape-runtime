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
        PreparedWorldBlockMutation prepared = prepareBind(ownerStableId, spatialBlockId);
        PreparedWorldBlockMutation.Publication publication = prepared.takePublication();
        Array<PhysicsShapeData> publishedShapes = publication.physics.takeShapes();
        // Consume every throwing transfer before the first ECS mutation.
        com.badlogic.gdx.utils.Array<games.pixscape.runtime.physics.CompiledFixtureData> fixtures =
                publication.physics.takeCompiledFixtures().takeFixtures();
        publish(publication, publishedShapes, fixtures);
        return publication.physicsShapeId;
    }

    PreparedWorldBlockMutation prepareBind(int ownerStableId, int spatialBlockId) {
        validateRequest(ownerStableId, spatialBlockId);
        int ownerEntityId = identityRegistry.findByStableId(ownerStableId);
        SpatialBlocksComponent ownerBlocks = blocks.get(ownerEntityId);
        BlockPhysicsBindingsComponent currentBindings = bindings.getSafe(ownerEntityId, null);
        PhysicsShapesComponent currentShapes = shapes.getSafe(ownerEntityId, null);
        validateOwnerAggregate(ownerEntityId, ownerStableId, currentBindings, currentShapes);

        Array<BlockPhysicsBindingData> nextBindings = copyBindings(currentBindings);
        Array<PhysicsShapeData> nextShapes = copyShapes(currentShapes);
        int physicsShapeId = physicsService.allocateNewPhysicsShapeId();
        PhysicsShapeData shape = PhysicsService.createDefaultShape(physicsShapeId);
        shape.directGeometry = null;
        shape.enabled = true;
        BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
        binding.spatialBlockId = spatialBlockId;
        binding.physicsShapeId = physicsShapeId;
        nextBindings.add(binding);
        nextShapes.add(shape);

        Array<SpatialBlockData> blockCopies = copyBlocks(ownerBlocks);
        PreparedPhysicsBodyCandidate preparedPhysics = PhysicsService.prepareCandidateLinkedBody(
                nextShapes, ownerStableId, tiled.get(ownerEntityId).data, sceneMeta.pixelsPerMeter,
                nextBindings, blockCopies);
        BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot =
                repository.prepareOwnerSnapshot(ownerStableId, ownerEntityId, blockCopies,
                        nextBindings, nextShapes);
        return new PreparedWorldBlockMutation(ownerEntityId, ownerStableId, physicsShapeId,
                nextBindings, preparedPhysics, repositorySnapshot,
                !transforms.has(ownerEntityId), !bodies.has(ownerEntityId));
    }

    private void publish(PreparedWorldBlockMutation.Publication publication,
                         Array<PhysicsShapeData> publishedShapes,
                         Array<games.pixscape.runtime.physics.CompiledFixtureData> fixtures) {
        int entityId = publication.ownerEntityId;
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
        targetShapes.shapes = publishedShapes;
        new PhysicsCompiledFixtureCachePublisher().publish(targetCompiled, fixtures);
        publication.repositorySnapshot.applyTo(repository);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.physics(entityId, PhysicsDirtyBits.ALL);
    }

    private void validateRequest(int ownerStableId, int spatialBlockId) {
        if (!sceneMeta.physicsEnabled) throw invalid(-1, ownerStableId, spatialBlockId, -1, "physics is disabled");
        if (ownerStableId <= 0 || spatialBlockId <= 0) {
            throw invalid(-1, ownerStableId, spatialBlockId, -1, "owner and block IDs must be positive");
        }
        if (Float.isNaN(sceneMeta.pixelsPerMeter) || Float.isInfinite(sceneMeta.pixelsPerMeter)
                || sceneMeta.pixelsPerMeter <= 0f) {
            throw invalid(-1, ownerStableId, spatialBlockId, -1, "pixelsPerMeter must be positive and finite");
        }
        int ownerEntityId = identityRegistry.findByStableId(ownerStableId);
        if (ownerEntityId < 0 || !world.getEntityManager().isActive(ownerEntityId)) {
            throw invalid(ownerEntityId, ownerStableId, spatialBlockId, -1, "owner is absent or inactive");
        }
        PixscapeIdentityComponent identity = identities.getSafe(ownerEntityId, null);
        if (identity == null || identity.stableId != ownerStableId) {
            throw invalid(ownerEntityId, ownerStableId, spatialBlockId, -1, "owner identity is inconsistent");
        }
        SpatialBlocksComponent ownerBlocks = blocks.getSafe(ownerEntityId, null);
        if (ownerBlocks == null || ownerBlocks.blocks == null || findBlock(ownerBlocks.blocks, spatialBlockId) == null) {
            throw invalid(ownerEntityId, ownerStableId, spatialBlockId, -1, "spatial block is absent");
        }
        TiledLayerComponent layer = tiled.getSafe(ownerEntityId, null);
        if (layer == null || layer.data == null) {
            throw invalid(ownerEntityId, ownerStableId, spatialBlockId, -1, "TiledLayerComponent.data is required");
        }
        if (repository.hasBinding(ownerStableId, spatialBlockId)) {
            throw invalid(ownerEntityId, ownerStableId, spatialBlockId, -1, "spatial block is already bound");
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
}
