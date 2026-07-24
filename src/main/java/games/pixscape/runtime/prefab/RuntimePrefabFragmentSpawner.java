package games.pixscape.runtime.prefab;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsBodyCompiler;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.physics.PhysicsShapeIdState;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;
    private final PhysicsShapeIdAllocator physicsShapeIdAllocator;
    private final PhysicsBodyCompiler physicsBodyCompiler = new PhysicsBodyCompiler();
    private final CommitObserver commitObserver;

    public RuntimePrefabFragmentSpawner(
            IdentityRegistry identityRegistry, PhysicsShapeIdState physicsShapeIdState) {
        this(identityRegistry, physicsShapeIdState, null);
    }

    RuntimePrefabFragmentSpawner(
            IdentityRegistry identityRegistry,
            PhysicsShapeIdState physicsShapeIdState,
            CommitObserver commitObserver) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
        this.physicsShapeIdAllocator = new PhysicsShapeIdAllocator(physicsShapeIdState);
        this.commitObserver = commitObserver;
    }

    public SpawnResult spawn(World world, SaveFileFormat fragment, float offsetX, float offsetY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }

        WorldSerializationManager targetSerialization =
                world.getSystem(WorldSerializationManager.class);
        if (targetSerialization == null) {
            throw new IllegalStateException("WorldSerializationManager is required");
        }
        if (!(targetSerialization.getSerializer() instanceof JsonArtemisSerializer)) {
            targetSerialization.setSerializer(new JsonArtemisSerializer(world));
        }
        identityRegistry.bind(world);
        identityRegistry.rebuild();

        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        targetSerialization.save(sourceBytes, fragment);

        World stagingWorld = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
        byte[] commitBytes;
        try {
            WorldSerializationManager stagingSerialization =
                    stagingWorld.getSystem(WorldSerializationManager.class);
            stagingSerialization.setSerializer(new JsonArtemisSerializer(stagingWorld));
            SaveFileFormat staged = stagingSerialization.load(
                    new ByteArrayInputStream(sourceBytes.toByteArray()),
                    SaveFileFormat.class);
            stagingWorld.process();
            prepareAndValidateStaged(stagingWorld, staged, offsetX, offsetY);

            ByteArrayOutputStream preparedBytes = new ByteArrayOutputStream();
            stagingSerialization.save(preparedBytes, staged);
            commitBytes = preparedBytes.toByteArray();
        } finally {
            stagingWorld.dispose();
        }

        IntSet activeBefore = activeEntities(world);
        IntBag created = new IntBag();
        try {
            SaveFileFormat committed = targetSerialization.load(
                    new ByteArrayInputStream(commitBytes),
                    SaveFileFormat.class);
            for (int i = 0; i < committed.entities.size(); i++) {
                created.add(committed.entities.get(i));
            }
            for (int i = 0; i < created.size(); i++) {
                if (commitObserver != null) {
                    commitObserver.afterEntityPublished(i, created.get(i));
                }
            }
        } catch (Throwable failure) {
            appendNewActiveEntities(world, activeBefore, created);
            rollbackCreated(world, created, failure);
            throw propagateCommitFailure(failure);
        }

        markCreatedDirty(world, created);

        return new SpawnResult(created);
    }

    private void prepareAndValidateStaged(
            World stagingWorld,
            SaveFileFormat staged,
            float offsetX,
            float offsetY) {
        ComponentMapper<TransformComponent> transforms =
                stagingWorld.getMapper(TransformComponent.class);
        ComponentMapper<PixscapeIdentityComponent> identities =
                stagingWorld.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                stagingWorld.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsCompiledFixturesComponent> compiledMapper =
                stagingWorld.getMapper(PhysicsCompiledFixturesComponent.class);
        ComponentMapper<SpatialPhysicsFootprintComponent> spatialFootprintMapper =
                stagingWorld.getMapper(SpatialPhysicsFootprintComponent.class);
        ComponentMapper<PhysicsJointComponent> joints =
                stagingWorld.getMapper(PhysicsJointComponent.class);

        IntSet stagedEntities = new IntSet(Math.max(1, staged.entities.size()));
        IntSet stableIds = new IntSet(Math.max(1, staged.entities.size()));
        IntSet physicsShapeIds = new IntSet(Math.max(1, staged.entities.size()));
        for (int i = 0; i < staged.entities.size(); i++) {
            stagedEntities.add(staged.entities.get(i));
        }

        for (int i = 0; i < staged.entities.size(); i++) {
            int entityId = staged.entities.get(i);
            TransformComponent transform = transforms.getSafe(entityId, null);
            if (transform != null) {
                transform.x += offsetX;
                transform.y += offsetY;
            }

            PixscapeIdentityComponent identity = identities.has(entityId)
                    ? identities.get(entityId)
                    : identities.create(entityId);
            identity.stableId = identityRegistry.allocateStableId();
            if (!stableIds.add(identity.stableId)) {
                throw new IllegalStateException(
                        "Staged prefab produced duplicate stableId " + identity.stableId + ".");
            }

            PhysicsShapesComponent shapes = shapesMapper.getSafe(entityId, null);
            if (shapes != null && shapes.shapes != null) {
                for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                    PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                    if (shape == null) {
                        throw new IllegalArgumentException(
                                "Prefab contains a null physics shape for staged entity "
                                        + entityId + ".");
                    }
                    shape.physicsShapeId = physicsShapeIdAllocator.allocateNewPhysicsShapeId();
                    if (!physicsShapeIds.add(shape.physicsShapeId)) {
                        throw new IllegalStateException(
                                "Staged prefab produced duplicate physicsShapeId "
                                        + shape.physicsShapeId + ".");
                    }
                }
                physicsBodyCompiler.compile(shapes);
            }
            if (compiledMapper.has(entityId)) {
                compiledMapper.remove(entityId);
            }
            if (spatialFootprintMapper.has(entityId)) {
                spatialFootprintMapper.remove(entityId);
            }

            PhysicsJointComponent joint = joints.getSafe(entityId, null);
            if (joint != null
                    && (!stagedEntities.contains(joint.aEid)
                    || !stagedEntities.contains(joint.bEid)
                    || joint.aEid == joint.bEid)) {
                throw new IllegalArgumentException(
                        "Prefab joint entity " + entityId
                                + " has invalid staged endpoints aEid=" + joint.aEid
                                + ", bEid=" + joint.bEid + ".");
            }
        }
    }

    private static void markCreatedDirty(World world, IntBag created) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty == null) return;
        for (int i = 0; i < created.size(); i++) {
            dirty.mark(
                    created.get(i),
                    DirtyBits.GEOMETRY
                            | DirtyBits.MATERIAL
                            | DirtyBits.COLOR
                            | DirtyBits.ORDER
                            | DirtyBits.LAYER
                            | DirtyBits.PHYSICS
                            | DirtyBits.JOINTS);
        }
    }

    private static IntSet activeEntities(World world) {
        IntBag active = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();
        IntSet result = new IntSet(Math.max(1, active.size()));
        for (int i = 0; i < active.size(); i++) {
            result.add(active.get(i));
        }
        return result;
    }

    private static void appendNewActiveEntities(
            World world, IntSet activeBefore, IntBag created) {
        IntBag active = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();
        for (int i = 0; i < active.size(); i++) {
            int entityId = active.get(i);
            if (!activeBefore.contains(entityId) && !contains(created, entityId)) {
                created.add(entityId);
            }
        }
    }

    private static boolean contains(IntBag entities, int entityId) {
        for (int i = 0; i < entities.size(); i++) {
            if (entities.get(i) == entityId) return true;
        }
        return false;
    }

    private static void rollbackCreated(
            World world, IntBag created, Throwable originalFailure) {
        for (int i = created.size() - 1; i >= 0; i--) {
            int entityId = created.get(i);
            IdentityRegistry.unindexEntityImmediately(world, entityId);
            if (world.getEntityManager().isActive(entityId)) {
                world.delete(entityId);
            }
        }
        try {
            world.process();
        } catch (Throwable rollbackFailure) {
            originalFailure.addSuppressed(rollbackFailure);
        }
        for (int i = 0; i < created.size(); i++) {
            int entityId = created.get(i);
            if (world.getEntityManager().isActive(entityId)) {
                IllegalStateException incomplete = new IllegalStateException(
                        "Prefab rollback left entity " + entityId + " active.");
                originalFailure.addSuppressed(incomplete);
            }
        }
    }

    private static RuntimeException propagateCommitFailure(Throwable failure) {
        if (failure instanceof RuntimeException) {
            return (RuntimeException) failure;
        }
        return new IllegalStateException("Runtime prefab commit failed.", failure);
    }

    interface CommitObserver {
        void afterEntityPublished(int index, int entityId);
    }
}
