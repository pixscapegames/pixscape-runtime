package games.pixscape.runtime.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsFrictionJointComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsMotorJointComponent;
import games.pixscape.runtime.component.physics.PhysicsPrismaticJointComponent;
import games.pixscape.runtime.component.physics.PhysicsPulleyJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRevoluteJointComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsWeldJointComponent;
import games.pixscape.runtime.component.physics.PhysicsWheelJointComponent;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;
    private final SceneMetaRuntime sceneMeta;
    private final PhysicsShapeIdAllocator physicsShapeIdAllocator;

    public RuntimePrefabFragmentSpawner(
            IdentityRegistry identityRegistry, SceneMetaRuntime sceneMeta) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
        this.sceneMeta = sceneMeta;
        this.physicsShapeIdAllocator = new PhysicsShapeIdAllocator(sceneMeta);
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
        identityRegistry.bind(world, sceneMeta);
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
            Array<PreparedPhysicsBodyCandidate> physicsCandidates =
                    prepareAndValidateStaged(stagingWorld, staged, offsetX, offsetY);

            ByteArrayOutputStream preparedBytes = new ByteArrayOutputStream();
            stagingSerialization.save(preparedBytes, staged);
            byte[] serializedEntities = preparedBytes.toByteArray();
            validatePreparedPayload(serializedEntities);
            preparedSpawn = new PreparedPrefabSpawn(serializedEntities, physicsCandidates);
        } finally {
            stagingWorld.dispose();
        }

        IntBag created = new IntBag();
        SaveFileFormat committed = targetSerialization.load(
                new ByteArrayInputStream(preparedSpawn.serializedEntities),
                SaveFileFormat.class);
        for (int i = 0; i < committed.entities.size(); i++) {
            int entityId = committed.entities.get(i);
            created.add(entityId);
            PreparedPhysicsBodyCandidate candidate = preparedSpawn.physicsCandidates.get(i);
            if (candidate != null) {
                PhysicsShapesComponent shapes =
                        world.getMapper(PhysicsShapesComponent.class).get(entityId);
                PhysicsCompiledFixturesComponent compiled =
                        world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
                PhysicsService.publishPreparedCandidate(shapes, compiled, candidate);
            }
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).getSafe(entityId, null);
            if (transform != null) transform.refreshCaches();
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

    private Array<PreparedPhysicsBodyCandidate> prepareAndValidateStaged(
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
        IntSet stagedEntities = new IntSet(Math.max(1, staged.entities.size()));
        IntSet stableIds = new IntSet(Math.max(1, staged.entities.size()));
        IntSet physicsShapeIds = new IntSet(Math.max(1, staged.entities.size()));
        Array<PreparedPhysicsBodyCandidate> physicsCandidates =
                new Array<>(true, staged.entities.size(), PreparedPhysicsBodyCandidate.class);
        for (int i = 0; i < staged.entities.size(); i++) {
            stagedEntities.add(staged.entities.get(i));
        }

        for (int i = 0; i < staged.entities.size(); i++) {
            int entityId = staged.entities.get(i);
            TransformComponent transform = transforms.getSafe(entityId, null);
            if (transform != null) {
                transform.x += offsetX;
                transform.y += offsetY;
                transform.refreshCaches();
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
                physicsCandidates.add(PhysicsService.prepareBodyCandidate(shapes.shapes));
            } else {
                physicsCandidates.add(null);
            }
            if (compiledMapper.has(entityId)) {
                compiledMapper.remove(entityId);
            }
            if (spatialFootprintMapper.has(entityId)) {
                spatialFootprintMapper.remove(entityId);
            }

        }
        new StagedJointValidator(stagingWorld, stagedEntities)
                .validate(staged.entities);
        return physicsCandidates;
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
        final Array<PreparedPhysicsBodyCandidate> physicsCandidates;

        PreparedPrefabSpawn(
                byte[] serializedEntities,
                Array<PreparedPhysicsBodyCandidate> physicsCandidates) {
            this.serializedEntities = serializedEntities;
            this.physicsCandidates = physicsCandidates;
        }
    }

    private static final class StagedJointValidator {
        private final IntSet stagedEntities;
        private final ComponentMapper<PhysicsJointComponent> joints;
        private final ComponentMapper<PhysicsBodyComponent> bodies;
        private final ComponentMapper<PhysicsShapesComponent> shapes;
        private final ComponentMapper<PhysicsDistanceJointComponent> distances;
        private final ComponentMapper<PhysicsRevoluteJointComponent> revolutes;
        private final ComponentMapper<PhysicsPrismaticJointComponent> prismatics;
        private final ComponentMapper<PhysicsWheelJointComponent> wheels;
        private final ComponentMapper<PhysicsFrictionJointComponent> frictions;
        private final ComponentMapper<PhysicsMotorJointComponent> motors;
        private final ComponentMapper<PhysicsWeldJointComponent> welds;
        private final ComponentMapper<PhysicsPulleyJointComponent> pulleys;
        private final ComponentMapper<PhysicsGearJointComponent> gears;

        StagedJointValidator(World world, IntSet stagedEntities) {
            this.stagedEntities = stagedEntities;
            joints = world.getMapper(PhysicsJointComponent.class);
            bodies = world.getMapper(PhysicsBodyComponent.class);
            shapes = world.getMapper(PhysicsShapesComponent.class);
            distances = world.getMapper(PhysicsDistanceJointComponent.class);
            revolutes = world.getMapper(PhysicsRevoluteJointComponent.class);
            prismatics = world.getMapper(PhysicsPrismaticJointComponent.class);
            wheels = world.getMapper(PhysicsWheelJointComponent.class);
            frictions = world.getMapper(PhysicsFrictionJointComponent.class);
            motors = world.getMapper(PhysicsMotorJointComponent.class);
            welds = world.getMapper(PhysicsWeldJointComponent.class);
            pulleys = world.getMapper(PhysicsPulleyJointComponent.class);
            gears = world.getMapper(PhysicsGearJointComponent.class);
        }

        void validate(IntBag entities) {
            for (int i = 0; i < entities.size(); i++) {
                int entityId = entities.get(i);
                PhysicsJointComponent joint = joints.getSafe(entityId, null);
                if (joint == null) continue;
                requireSpecificComponent(entityId, joint.type);
                requireBodyEndpoint(entityId, joint.aEid, "aEid");
                requireBodyEndpoint(entityId, joint.bEid, "bEid");
                if (joint.aEid == joint.bEid) {
                    throw invalid(entityId, "body endpoints must be distinct.");
                }
                if (joint.type == PhysicsJointComponent.TYPE_GEAR) {
                    validateGear(entityId);
                }
            }
        }

        private void requireSpecificComponent(int entityId, int type) {
            boolean present;
            switch (type) {
                case PhysicsJointComponent.TYPE_DISTANCE:
                    present = distances.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_REVOLUTE:
                    present = revolutes.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_PRISMATIC:
                    present = prismatics.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_WHEEL:
                    present = wheels.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_FRICTION:
                    present = frictions.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_MOTOR:
                    present = motors.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_WELD:
                    present = welds.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_PULLEY:
                    present = pulleys.has(entityId);
                    break;
                case PhysicsJointComponent.TYPE_GEAR:
                    present = gears.has(entityId);
                    break;
                default:
                    throw invalid(entityId, "unsupported joint type " + type + ".");
            }
            if (!present) {
                throw invalid(
                        entityId,
                        "missing specific component for joint type " + type + ".");
            }
        }

        private void requireBodyEndpoint(
                int jointEntityId, int endpointEntityId, String field) {
            if (!stagedEntities.contains(endpointEntityId)) {
                throw invalid(
                        jointEntityId,
                        field + " references entity " + endpointEntityId
                                + " outside the prepared prefab.");
            }
            if (!bodies.has(endpointEntityId)) {
                throw invalid(
                        jointEntityId,
                        field + " entity " + endpointEntityId
                                + " has no PhysicsBodyComponent.");
            }
            PhysicsShapesComponent endpointShapes =
                    shapes.getSafe(endpointEntityId, null);
            if (endpointShapes == null || !endpointShapes.hasShapes()) {
                throw invalid(
                        jointEntityId,
                        field + " entity " + endpointEntityId
                                + " has no non-empty PhysicsShapesComponent.");
            }
        }

        private void validateGear(int gearEntityId) {
            PhysicsGearJointComponent gear = gears.get(gearEntityId);
            if (gear.joint1Eid == gear.joint2Eid) {
                throw invalid(
                        gearEntityId, "gear joint dependencies must be distinct.");
            }
            requireGearSource(gearEntityId, gear.joint1Eid, "joint1Eid");
            requireGearSource(gearEntityId, gear.joint2Eid, "joint2Eid");
        }

        private void requireGearSource(
                int gearEntityId, int sourceEntityId, String field) {
            if (sourceEntityId == gearEntityId) {
                throw invalid(
                        gearEntityId, field + " cannot reference the gear itself.");
            }
            if (!stagedEntities.contains(sourceEntityId)) {
                throw invalid(
                        gearEntityId,
                        field + " references entity " + sourceEntityId
                                + " outside the prepared prefab.");
            }
            PhysicsJointComponent source = joints.getSafe(sourceEntityId, null);
            if (source == null) {
                throw invalid(
                        gearEntityId,
                        field + " entity " + sourceEntityId
                                + " has no PhysicsJointComponent.");
            }
            if (source.type != PhysicsJointComponent.TYPE_REVOLUTE
                    && source.type != PhysicsJointComponent.TYPE_PRISMATIC) {
                throw invalid(
                        gearEntityId,
                        field + " entity " + sourceEntityId
                                + " must be revolute or prismatic, but has type "
                                + source.type + ".");
            }
            requireSpecificComponent(sourceEntityId, source.type);
        }

        private static IllegalArgumentException invalid(
                int jointEntityId, String detail) {
            return new IllegalArgumentException(
                    "Invalid staged prefab joint entity "
                            + jointEntityId + ": " + detail);
        }
    }
}
