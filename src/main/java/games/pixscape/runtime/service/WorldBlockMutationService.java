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

    /** Captures the reusable, detached authored state used by editor history. */
    public WorldBlockOwnerSnapshot captureOwnerState(int ownerStableId) {
        requireAttached();
        OwnerState owner = resolveOwner(ownerStableId);
        validateOwnerAggregate(owner.entityId, ownerStableId, owner.bindings, owner.shapes);
        repository.validatePublishedOwnerState(ownerStableId, owner.entityId, owner.blocks,
                owner.bindings, owner.shapes);
        PhysicsBodyComponent body = bodies.getSafe(owner.entityId, null);
        TransformComponent transform = transforms.getSafe(owner.entityId, null);
        WorldBlockOwnerSnapshot.BodyState bodyState = body == null ? null
                : new WorldBlockOwnerSnapshot.BodyState(body.type, body.fixedRotation, body.bullet,
                body.allowSleep, body.awake, body.gravityScale, body.linearDamping,
                body.angularDamping);
        WorldBlockOwnerSnapshot.TransformState transformState = transform == null ? null
                : new WorldBlockOwnerSnapshot.TransformState(transform.x, transform.y, transform.originX,
                transform.originY, transform.rotationRad, transform.scaleX, transform.scaleY);
        return new WorldBlockOwnerSnapshot(ownerStableId, owner.blocks.nextSpatialBlockId,
                owner.blocks.blocks, owner.bindings != null,
                owner.bindings != null ? owner.bindings.bindings : null, owner.shapes != null,
                owner.shapes != null ? owner.shapes.shapes : null, body != null, bodyState,
                transform != null, transformState);
    }

    /** Restores a reusable owner snapshot without allocating an authored physics-shape ID. */
    public void restoreOwnerState(WorldBlockOwnerSnapshot snapshot) {
        requireAttached();
        if (snapshot == null) throw new IllegalArgumentException("Owner snapshot is required.");
        OwnerState owner = resolveOwner(snapshot.ownerStableId);
        validateOwnerAggregate(owner.entityId, snapshot.ownerStableId, owner.bindings, owner.shapes);
        repository.validatePublishedOwnerState(snapshot.ownerStableId, owner.entityId, owner.blocks,
                owner.bindings, owner.shapes);
        validateSnapshotHighWater(snapshot);
        Array<SpatialBlockData> nextBlocks = snapshot.blocks();
        Array<BlockPhysicsBindingData> nextBindings = snapshot.bindings();
        Array<PhysicsShapeData> nextShapes = snapshot.shapes();
        BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot;
        PreparedPhysicsBodyCandidate preparedPhysics = null;
        boolean physicalStateUnchanged = samePhysicalState(owner, snapshot, nextBlocks,
                nextBindings, nextShapes);
        if (snapshot.hasBindings) {
            if (!snapshot.hasShapes || !snapshot.hasBody || !snapshot.hasTransform) {
                throw new IllegalArgumentException("Invalid owner snapshot: bound aggregate is incomplete.");
            }
            repositorySnapshot = repository.prepareOwnerSnapshot(snapshot.ownerStableId, owner.entityId,
                    snapshot.nextSpatialBlockId, nextBlocks, nextBindings, nextShapes);
            if (!physicalStateUnchanged) {
                preparedPhysics = PhysicsService.prepareLinkedBodyCandidate(nextShapes, owner.entityId,
                        snapshot.ownerStableId, owner.tiled.data, sceneMeta.pixelsPerMeter,
                        repositorySnapshot);
            }
        } else {
            if (snapshot.hasShapes || snapshot.hasBody) {
                throw new IllegalArgumentException("Invalid owner snapshot: unbound owner contains physics.");
            }
            repositorySnapshot = repository.prepareOwnerRemovalIfPresent(snapshot.ownerStableId, owner.entityId);
        }
        // All validations and compilation above must complete before this first ECS mutation.
        owner.blocks.blocks = nextBlocks;
        owner.blocks.nextSpatialBlockId = snapshot.nextSpatialBlockId;
        owner.blocks.revision++;
        if (physicalStateUnchanged) {
            repositorySnapshot.applyTo(repository);
            return;
        }
        if (snapshot.hasTransform) applyTransform(transforms.has(owner.entityId)
                ? transforms.get(owner.entityId) : transforms.create(owner.entityId), snapshot.transform);
        else transforms.remove(owner.entityId);
        if (!snapshot.hasBindings) {
            bindings.remove(owner.entityId);
            shapes.remove(owner.entityId);
            compiled.remove(owner.entityId);
            bodies.remove(owner.entityId);
            repositorySnapshot.applyTo(repository);
            markPhysicsDirty(owner.entityId);
            return;
        }
        applyBody(bodies.has(owner.entityId) ? bodies.get(owner.entityId) : bodies.create(owner.entityId), snapshot.body);
        BlockPhysicsBindingsComponent targetBindings = bindings.has(owner.entityId)
                ? bindings.get(owner.entityId) : bindings.create(owner.entityId);
        targetBindings.bindings = nextBindings;
        PhysicsShapesComponent targetShapes = shapes.has(owner.entityId)
                ? shapes.get(owner.entityId) : shapes.create(owner.entityId);
        PhysicsCompiledFixturesComponent targetCompiled = compiled.has(owner.entityId)
                ? compiled.get(owner.entityId) : compiled.create(owner.entityId);
        PhysicsService.publishPreparedData(targetShapes, targetCompiled, preparedPhysics.takeShapes(),
                preparedPhysics.takeCompiledFixtures().takeFixtures());
        repositorySnapshot.applyTo(repository);
        markPhysicsDirty(owner.entityId);
    }

    /** Replaces authored blocks while preserving all existing linked relations. */
    public void replaceSpatialBlocks(int ownerStableId, int nextSpatialBlockId,
                                     Array<SpatialBlockData> replacementBlocks) {
        requireAttached();
        OwnerState owner = resolveOwner(ownerStableId);
        validateOwnerAggregate(owner.entityId, ownerStableId, owner.bindings, owner.shapes);
        repository.validatePublishedOwnerState(ownerStableId, owner.entityId, owner.blocks,
                owner.bindings, owner.shapes);
        if (nextSpatialBlockId <= 0) throw invalid(owner.entityId, ownerStableId, -1, -1,
                "nextSpatialBlockId must be positive");
        Array<SpatialBlockData> nextBlocks = WorldBlockOwnerSnapshot.copyBlocks(replacementBlocks);
        if (owner.bindings != null) {
            for (int i = 0; i < owner.bindings.bindings.size; i++) {
                BlockPhysicsBindingData binding = owner.bindings.bindings.get(i);
                if (findBlock(nextBlocks, binding.spatialBlockId) == null) {
                    throw invalid(owner.entityId, ownerStableId, binding.spatialBlockId,
                            binding.physicsShapeId, "replacement removes a bound spatial block");
                }
            }
        }
        publishReplacement(owner, ownerStableId, nextSpatialBlockId, nextBlocks,
                copyBindings(owner.bindings), copyShapes(owner.shapes));
    }

    /** Deletes one block and, when necessary, its linked shape in a single publication. */
    public void deleteSpatialBlock(int ownerStableId, int spatialBlockId, int nextSpatialBlockId,
                                   Array<SpatialBlockData> replacementBlocks) {
        requireAttached();
        OwnerState owner = validateOwner(ownerStableId, spatialBlockId, false);
        Array<SpatialBlockData> nextBlocks = WorldBlockOwnerSnapshot.copyBlocks(replacementBlocks);
        if (findBlock(nextBlocks, spatialBlockId) != null) throw invalid(owner.entityId, ownerStableId,
                spatialBlockId, -1, "replacement retains deleted spatial block");
        Array<BlockPhysicsBindingData> nextBindings = copyBindings(owner.bindings);
        Array<PhysicsShapeData> nextShapes = copyShapes(owner.shapes);
        BlockPhysicsBindingData linked = findBinding(nextBindings, spatialBlockId);
        if (linked != null) {
            removeBinding(nextBindings, spatialBlockId, linked.physicsShapeId);
            removeShape(nextShapes, linked.physicsShapeId);
        }
        if (owner.bindings != null) for (int i = 0; i < owner.bindings.bindings.size; i++) {
            BlockPhysicsBindingData binding = owner.bindings.bindings.get(i);
            if (binding.spatialBlockId != spatialBlockId && findBlock(nextBlocks, binding.spatialBlockId) == null) {
                throw invalid(owner.entityId, ownerStableId, binding.spatialBlockId,
                        binding.physicsShapeId, "replacement removes a bound spatial block");
            }
        }
        publishReplacement(owner, ownerStableId, nextSpatialBlockId, nextBlocks, nextBindings, nextShapes);
    }

    /** Replaces only material, sensor, and filter values of an existing linked shape. */
    public void updateBlockCollisionProperties(int ownerStableId, int spatialBlockId,
                                               PhysicsShapeData replacement) {
        requireAttached();
        OwnerState owner = validateOwner(ownerStableId, spatialBlockId, true);
        BlockPhysicsBindingData binding = findBinding(owner.bindings.bindings, spatialBlockId);
        if (binding == null || replacement == null) throw unbindInvalid(owner.entityId, ownerStableId,
                spatialBlockId, -1, "linked shape and replacement are required");
        PhysicsShapeData original = findShape(owner.shapes.shapes, binding.physicsShapeId);
        if (original == null || replacement.physicsShapeId != original.physicsShapeId
                || replacement.directGeometry != null || !replacement.enabled) {
            throw unbindInvalid(owner.entityId, ownerStableId, spatialBlockId, binding.physicsShapeId,
                    "linked shape identity and geometry are immutable");
        }
        PhysicsShapeData next = original.copy();
        next.density = replacement.density;
        next.friction = replacement.friction;
        next.restitution = replacement.restitution;
        next.sensor = replacement.sensor;
        next.categoryBits = replacement.categoryBits;
        next.maskBits = replacement.maskBits;
        next.groupIndex = replacement.groupIndex;
        Array<PhysicsShapeData> nextShapes = copyShapes(owner.shapes);
        for (int i = 0; i < nextShapes.size; i++) if (nextShapes.get(i).physicsShapeId == next.physicsShapeId) nextShapes.set(i, next);
        publishReplacement(owner, ownerStableId, owner.blocks.nextSpatialBlockId,
                copyBlocks(owner.blocks), copyBindings(owner.bindings), nextShapes, true);
    }

    public void detach() {
        attached = false;
    }

    public boolean isPhysicsEnabled() {
        return attached && sceneMeta.physicsEnabled;
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

    private void publishReplacement(OwnerState owner, int ownerStableId, int nextSpatialBlockId,
                                    Array<SpatialBlockData> nextBlocks,
                                    Array<BlockPhysicsBindingData> nextBindings,
                                    Array<PhysicsShapeData> nextShapes) {
        publishReplacement(owner, ownerStableId, nextSpatialBlockId, nextBlocks, nextBindings,
                nextShapes, false);
    }

    private void publishReplacement(OwnerState owner, int ownerStableId, int nextSpatialBlockId,
                                    Array<SpatialBlockData> nextBlocks,
                                    Array<BlockPhysicsBindingData> nextBindings,
                                    Array<PhysicsShapeData> nextShapes,
                                    boolean forcePhysicsRebuild) {
        if (nextSpatialBlockId <= 0) throw invalid(owner.entityId, ownerStableId, -1, -1,
                "nextSpatialBlockId must be positive");
        boolean hasLinked = nextBindings.size > 0;
        BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot;
        PreparedPhysicsBodyCandidate preparedPhysics = null;
        boolean physicalChanged = hasLinked && (forcePhysicsRebuild
                || owner.bindings == null || owner.bindings.bindings.size != nextBindings.size
                || linkedBlockGeometryChanged(owner.blocks.blocks, nextBlocks, nextBindings));
        if (hasLinked) {
            repositorySnapshot = repository.prepareOwnerSnapshot(ownerStableId, owner.entityId,
                    nextSpatialBlockId, nextBlocks, nextBindings, nextShapes);
            if (physicalChanged) {
                preparedPhysics = PhysicsService.prepareLinkedBodyCandidate(nextShapes, owner.entityId,
                        ownerStableId, owner.tiled.data, sceneMeta.pixelsPerMeter, repositorySnapshot);
            }
        } else {
            if (nextShapes.size != 0) throw invalid(owner.entityId, ownerStableId, -1, -1,
                    "unbound replacement contains physics shapes");
            repositorySnapshot = repository.prepareOwnerRemovalIfPresent(ownerStableId, owner.entityId);
        }
        // Candidate validation and linked compilation are complete before the authoritative arrays move.
        owner.blocks.blocks = nextBlocks;
        owner.blocks.nextSpatialBlockId = nextSpatialBlockId;
        owner.blocks.revision++;
        if (!hasLinked) {
            bindings.remove(owner.entityId);
            shapes.remove(owner.entityId);
            compiled.remove(owner.entityId);
            bodies.remove(owner.entityId);
            repositorySnapshot.applyTo(repository);
            markPhysicsDirty(owner.entityId);
            return;
        }
        if (!physicalChanged) {
            repositorySnapshot.applyTo(repository);
            return;
        }
        BlockPhysicsBindingsComponent targetBindings = bindings.has(owner.entityId)
                ? bindings.get(owner.entityId) : bindings.create(owner.entityId);
        targetBindings.bindings = nextBindings;
        PhysicsShapesComponent targetShapes = shapes.has(owner.entityId)
                ? shapes.get(owner.entityId) : shapes.create(owner.entityId);
        PhysicsCompiledFixturesComponent targetCompiled = compiled.has(owner.entityId)
                ? compiled.get(owner.entityId) : compiled.create(owner.entityId);
        PhysicsService.publishPreparedData(targetShapes, targetCompiled, preparedPhysics.takeShapes(),
                preparedPhysics.takeCompiledFixtures().takeFixtures());
        repositorySnapshot.applyTo(repository);
        markPhysicsDirty(owner.entityId);
    }

    private static boolean linkedBlockGeometryChanged(Array<SpatialBlockData> current,
                                                      Array<SpatialBlockData> replacement,
                                                      Array<BlockPhysicsBindingData> bindings) {
        for (int i = 0; i < bindings.size; i++) {
            int blockId = bindings.get(i).spatialBlockId;
            SpatialBlockData before = findBlock(current, blockId);
            SpatialBlockData after = findBlock(replacement, blockId);
            if (before == null || after == null || Float.compare(before.x, after.x) != 0
                    || Float.compare(before.y, after.y) != 0
                    || Float.compare(before.width, after.width) != 0
                    || Float.compare(before.depth, after.depth) != 0
                    || Float.compare(before.altitude, after.altitude) != 0) return true;
        }
        return false;
    }

    private boolean samePhysicalState(OwnerState owner, WorldBlockOwnerSnapshot snapshot,
                                      Array<SpatialBlockData> targetBlocks,
                                      Array<BlockPhysicsBindingData> targetBindings,
                                      Array<PhysicsShapeData> targetShapes) {
        if (owner.bindings == null != !snapshot.hasBindings || owner.shapes == null != !snapshot.hasShapes
                || owner.bindings != null && !sameBindings(owner.bindings.bindings, targetBindings)
                || owner.shapes != null && !sameShapes(owner.shapes.shapes, targetShapes)
                || !sameBody(owner, snapshot) || !sameTransform(owner, snapshot)) return false;
        return !snapshot.hasBindings || !linkedBlockGeometryChanged(owner.blocks.blocks, targetBlocks,
                targetBindings);
    }

    private static boolean sameBindings(Array<BlockPhysicsBindingData> a,
                                        Array<BlockPhysicsBindingData> b) {
        if (a == null || b == null || a.size != b.size) return false;
        for (int i = 0; i < a.size; i++) if (!sameBinding(a.get(i), b.get(i))) return false;
        return true;
    }

    private static boolean sameShapes(Array<PhysicsShapeData> a, Array<PhysicsShapeData> b) {
        if (a == null || b == null || a.size != b.size) return false;
        for (int i = 0; i < a.size; i++) if (!a.get(i).contentEquals(b.get(i))) return false;
        return true;
    }

    private boolean sameBody(OwnerState owner, WorldBlockOwnerSnapshot snapshot) {
        PhysicsBodyComponent body = bodies.getSafe(owner.entityId, null);
        if (body == null) return !snapshot.hasBody;
        WorldBlockOwnerSnapshot.BodyState expected = snapshot.body;
        return expected != null && body.type == expected.type && body.fixedRotation == expected.fixedRotation
                && body.bullet == expected.bullet && body.allowSleep == expected.allowSleep
                && body.awake == expected.awake && Float.compare(body.gravityScale, expected.gravityScale) == 0
                && Float.compare(body.linearDamping, expected.linearDamping) == 0
                && Float.compare(body.angularDamping, expected.angularDamping) == 0;
    }

    private boolean sameTransform(OwnerState owner, WorldBlockOwnerSnapshot snapshot) {
        TransformComponent transform = transforms.getSafe(owner.entityId, null);
        if (transform == null) return !snapshot.hasTransform;
        WorldBlockOwnerSnapshot.TransformState expected = snapshot.transform;
        return expected != null && Float.compare(transform.x, expected.x) == 0
                && Float.compare(transform.y, expected.y) == 0
                && Float.compare(transform.originX, expected.originX) == 0
                && Float.compare(transform.originY, expected.originY) == 0
                && Float.compare(transform.rotationRad, expected.rotationRad) == 0
                && Float.compare(transform.scaleX, expected.scaleX) == 0
                && Float.compare(transform.scaleY, expected.scaleY) == 0;
    }

    private OwnerState resolveOwner(int ownerStableId) {
        if (ownerStableId <= 0) throw invalid(-1, ownerStableId, -1, -1,
                "ownerStableId must be positive");
        int ownerEntityId = identityRegistry.findByStableId(ownerStableId);
        if (ownerEntityId < 0 || !world.getEntityManager().isActive(ownerEntityId)) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1, "owner is absent or inactive");
        }
        PixscapeIdentityComponent identity = identities.getSafe(ownerEntityId, null);
        if (identity == null || identity.stableId != ownerStableId) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1, "owner identity is inconsistent");
        }
        SpatialBlocksComponent ownerBlocks = blocks.getSafe(ownerEntityId, null);
        if (ownerBlocks == null || ownerBlocks.blocks == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1, "SpatialBlocksComponent is required");
        }
        TiledLayerComponent layer = tiled.getSafe(ownerEntityId, null);
        if (layer == null || layer.data == null) {
            throw invalid(ownerEntityId, ownerStableId, -1, -1, "TiledLayerComponent.data is required");
        }
        return new OwnerState(ownerEntityId, ownerBlocks, bindings.getSafe(ownerEntityId, null),
                shapes.getSafe(ownerEntityId, null), layer);
    }

    private void validateSnapshotHighWater(WorldBlockOwnerSnapshot snapshot) {
        int maxShapeId = 0;
        Array<PhysicsShapeData> snapshotShapes = snapshot.shapes();
        for (int i = 0; i < snapshotShapes.size; i++) {
            PhysicsShapeData shape = snapshotShapes.get(i);
            if (shape != null && shape.physicsShapeId > maxShapeId) maxShapeId = shape.physicsShapeId;
        }
        if (sceneMeta.nextPhysicsShapeId <= maxShapeId) {
            throw new IllegalArgumentException("Invalid owner snapshot: nextPhysicsShapeId must exceed "
                    + "every restored physicsShapeId.");
        }
        int maxBlockId = 0;
        Array<SpatialBlockData> snapshotBlocks = snapshot.blocks();
        for (int i = 0; i < snapshotBlocks.size; i++) {
            SpatialBlockData block = snapshotBlocks.get(i);
            if (block != null && block.id > maxBlockId) maxBlockId = block.id;
        }
        if (snapshot.nextSpatialBlockId <= maxBlockId) {
            throw new IllegalArgumentException("Invalid owner snapshot: nextSpatialBlockId must exceed "
                    + "every restored spatialBlockId.");
        }
    }

    private static void applyTransform(TransformComponent target,
                                       WorldBlockOwnerSnapshot.TransformState source) {
        target.x = source.x;
        target.y = source.y;
        target.originX = source.originX;
        target.originY = source.originY;
        target.rotationRad = source.rotationRad;
        target.scaleX = source.scaleX;
        target.scaleY = source.scaleY;
        target.refreshCaches();
    }

    private static void applyBody(PhysicsBodyComponent target,
                                  WorldBlockOwnerSnapshot.BodyState source) {
        target.type = source.type;
        target.fixedRotation = source.fixedRotation;
        target.bullet = source.bullet;
        target.allowSleep = source.allowSleep;
        target.awake = source.awake;
        target.gravityScale = source.gravityScale;
        target.linearDamping = source.linearDamping;
        target.angularDamping = source.angularDamping;
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
