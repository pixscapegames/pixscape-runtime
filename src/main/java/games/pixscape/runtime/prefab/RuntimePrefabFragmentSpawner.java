package games.pixscape.runtime.prefab;

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
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
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

    public RuntimePrefabFragmentSpawner(
            IdentityRegistry identityRegistry, PhysicsShapeIdState physicsShapeIdState) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
        this.physicsShapeIdAllocator = new PhysicsShapeIdAllocator(physicsShapeIdState);
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
        PreparedPrefabSpawn preparedSpawn;
        try {
            WorldSerializationManager stagingSerialization =
                    stagingWorld.getSystem(WorldSerializationManager.class);
            stagingSerialization.setSerializer(new JsonArtemisSerializer(stagingWorld));
            SaveFileFormat staged = stagingSerialization.load(
                    new ByteArrayInputStream(sourceBytes.toByteArray()),
                    SaveFileFormat.class);
            prepareAndValidateStaged(stagingWorld, staged, offsetX, offsetY);

            ByteArrayOutputStream preparedBytes = new ByteArrayOutputStream();
            stagingSerialization.save(preparedBytes, staged);
            byte[] serializedEntities = preparedBytes.toByteArray();
            validatePreparedPayload(serializedEntities);
            preparedSpawn = new PreparedPrefabSpawn(serializedEntities);
        } finally {
            stagingWorld.dispose();
        }

        IntBag created = new IntBag();
        SaveFileFormat committed = targetSerialization.load(
                new ByteArrayInputStream(preparedSpawn.serializedEntities),
                SaveFileFormat.class);
        for (int i = 0; i < committed.entities.size(); i++) {
            created.add(committed.entities.get(i));
        }

        markCreatedDirty(world, created);

        return new SpawnResult(created);
    }

    /**
     * Proves that the exact commit payload can be deserialized before the target world is touched.
     * The later target load therefore contains no data, remapping, or compilation branch that has
     * not already completed successfully; only unexpected VM/native failures remain outside the
     * transaction guarantee.
     */
    private static void validatePreparedPayload(byte[] serializedEntities) {
        World validationWorld = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
        try {
            WorldSerializationManager serialization =
                    validationWorld.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(validationWorld));
            serialization.load(
                    new ByteArrayInputStream(serializedEntities),
                    SaveFileFormat.class);
        } finally {
            validationWorld.dispose();
        }
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
        ComponentMapper<PhysicsBodyComponent> bodies =
                stagingWorld.getMapper(PhysicsBodyComponent.class);
        ComponentMapper<PhysicsGearJointComponent> gears =
                stagingWorld.getMapper(PhysicsGearJointComponent.class);

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
                    || joint.aEid == joint.bEid
                    || !bodies.has(joint.aEid)
                    || !bodies.has(joint.bEid)
                    || !hasShapes(shapesMapper, joint.aEid)
                    || !hasShapes(shapesMapper, joint.bEid))) {
                throw new IllegalArgumentException(
                        "Prefab joint entity " + entityId
                                + " has invalid staged endpoints aEid=" + joint.aEid
                                + ", bEid=" + joint.bEid + ".");
            }
            if (joint != null && joint.type == PhysicsJointComponent.TYPE_GEAR) {
                PhysicsGearJointComponent gear = gears.getSafe(entityId, null);
                if (gear == null
                        || gear.joint1Eid == gear.joint2Eid
                        || !stagedEntities.contains(gear.joint1Eid)
                        || !stagedEntities.contains(gear.joint2Eid)
                        || !joints.has(gear.joint1Eid)
                        || !joints.has(gear.joint2Eid)) {
                    throw new IllegalArgumentException(
                            "Prefab gear joint entity " + entityId
                                    + " has invalid staged joint dependencies.");
                }
            }
        }
    }

    private static boolean hasShapes(
            ComponentMapper<PhysicsShapesComponent> shapesMapper, int entityId) {
        PhysicsShapesComponent shapes = shapesMapper.getSafe(entityId, null);
        return shapes != null && shapes.hasShapes();
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

    private static final class PreparedPrefabSpawn {
        final byte[] serializedEntities;

        PreparedPrefabSpawn(byte[] serializedEntities) {
            this.serializedEntities = serializedEntities;
        }
    }
}
