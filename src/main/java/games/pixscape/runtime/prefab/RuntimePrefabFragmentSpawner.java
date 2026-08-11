package games.pixscape.runtime.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PhysicsShapeIdAllocator;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.DirtyTrackerSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API. Use {@code PixscapeAPI.prefabs()} for supported spawning.
 */
public class RuntimePrefabFragmentSpawner {

    private final IdentityRegistry identityRegistry;
    private final SceneMetaRuntime sceneMeta;
    private final AtlasRuntimeService atlasRuntimeService;
    private final PhysicsShapeIdAllocator physicsShapeIdAllocator;

    public RuntimePrefabFragmentSpawner(
            IdentityRegistry identityRegistry,
            SceneMetaRuntime sceneMeta,
            AtlasRuntimeService atlasRuntimeService) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        if (atlasRuntimeService == null) {
            throw new IllegalArgumentException("atlasRuntimeService must not be null");
        }
        this.identityRegistry = identityRegistry;
        this.sceneMeta = sceneMeta;
        this.atlasRuntimeService = atlasRuntimeService;
        this.physicsShapeIdAllocator = new PhysicsShapeIdAllocator(sceneMeta);
    }

    public SpawnResult spawn(World world, RuntimePrefabFragment fragment, float offsetX, float offsetY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        RuntimePrefabFragment.requireCurrentSchema(fragment);

        WorldSerializationManager targetSerialization = prepareTarget(world);

        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        targetSerialization.save(sourceBytes, fragment);

        PreparedPrefabSpawn preparedSpawn = prepareFromBytes(
                sourceBytes.toByteArray(), offsetX, offsetY);
        return commit(world, targetSerialization, preparedSpawn);
    }

    public SpawnResult spawn(World world, JsonValue fragmentRoot, float offsetX, float offsetY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        RuntimePrefabFragment.requireCurrentSchema(fragmentRoot);

        WorldSerializationManager targetSerialization = prepareTarget(world);
        PreparedPrefabSpawn preparedSpawn = prepareFromJson(
                fragmentRoot, offsetX, offsetY);
        return commit(world, targetSerialization, preparedSpawn);
    }

    private WorldSerializationManager prepareTarget(World world) {
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
        return targetSerialization;
    }

    private PreparedPrefabSpawn prepareFromBytes(
            byte[] sourceBytes, float offsetX, float offsetY) {
        World stagingWorld = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
        try {
            WorldSerializationManager stagingSerialization =
                    stagingWorld.getSystem(WorldSerializationManager.class);
            stagingSerialization.setSerializer(new JsonArtemisSerializer(stagingWorld));
            RuntimePrefabFragment staged = stagingSerialization.load(
                    new ByteArrayInputStream(sourceBytes),
                    RuntimePrefabFragment.class);
            resolveAssetRefsForStagedEntities(stagingWorld, staged.entities);
            return prepareStaged(
                    stagingWorld, stagingSerialization, staged, offsetX, offsetY);
        } finally {
            stagingWorld.dispose();
        }
    }

    private PreparedPrefabSpawn prepareFromJson(
            JsonValue fragmentRoot, float offsetX, float offsetY) {
        World stagingWorld = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
        try {
            JsonArtemisSerializer serializer =
                    new JsonArtemisSerializer(stagingWorld);
            stagingWorld.getSystem(WorldSerializationManager.class)
                    .setSerializer(serializer);
            RuntimePrefabFragment staged = serializer.load(
                    fragmentRoot, RuntimePrefabFragment.class);
            resolveAssetRefsForStagedEntities(stagingWorld, staged.entities);
            return prepareStaged(
                    stagingWorld,
                    stagingWorld.getSystem(WorldSerializationManager.class),
                    staged,
                    offsetX,
                    offsetY);
        } finally {
            stagingWorld.dispose();
        }
    }

    private PreparedPrefabSpawn prepareStaged(
            World stagingWorld,
            WorldSerializationManager stagingSerialization,
            RuntimePrefabFragment staged,
            float offsetX,
            float offsetY) {
        Array<PreparedPhysicsBodyCandidate> physicsCandidates =
                prepareAndValidateStaged(stagingWorld, staged, offsetX, offsetY);
        boolean[] preparedAssetRefs = new boolean[staged.entities.size()];
        boolean[] preparedRegionValid = new boolean[staged.entities.size()];
        float[] preparedUvs = new float[staged.entities.size() * 4];
        int[] preparedRegionData = new int[staged.entities.size() * 3];
        capturePreparedAssetState(
                stagingWorld,
                staged.entities,
                preparedAssetRefs,
                preparedRegionValid,
                preparedUvs,
                preparedRegionData);

        ByteArrayOutputStream preparedBytes = new ByteArrayOutputStream();
        stagingSerialization.save(preparedBytes, staged);
        byte[] serializedEntities = preparedBytes.toByteArray();
        validatePreparedPayload(serializedEntities);
        return new PreparedPrefabSpawn(
                serializedEntities,
                physicsCandidates,
                preparedAssetRefs,
                preparedRegionValid,
                preparedUvs,
                preparedRegionData);
    }

    private SpawnResult commit(
            World world,
            WorldSerializationManager targetSerialization,
            PreparedPrefabSpawn preparedSpawn) {
        IntBag created = new IntBag();
        RuntimePrefabFragment committed = targetSerialization.load(
                new ByteArrayInputStream(preparedSpawn.serializedEntities),
                RuntimePrefabFragment.class);
        for (int i = 0; i < committed.entities.size(); i++) {
            int entityId = committed.entities.get(i);
            created.add(entityId);
            PreparedPhysicsBodyCandidate candidate = preparedSpawn.physicsCandidates.get(i);
            if (candidate != null) {
                ComponentMapper<PhysicsShapesComponent> shapesMapper =
                        world.getMapper(PhysicsShapesComponent.class);
                PhysicsShapesComponent shapes = shapesMapper.has(entityId)
                        ? shapesMapper.get(entityId)
                        : shapesMapper.create(entityId);
                ComponentMapper<PhysicsCompiledFixturesComponent> compiledMapper =
                        world.getMapper(PhysicsCompiledFixturesComponent.class);
                PhysicsCompiledFixturesComponent compiled = compiledMapper.has(entityId)
                        ? compiledMapper.get(entityId)
                        : compiledMapper.create(entityId);
                PhysicsService.publishPreparedCandidate(shapes, compiled, candidate);
            }
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).getSafe(entityId, null);
            if (transform != null) transform.refreshCaches();
            publishPreparedAssetState(world, entityId, i, preparedSpawn);
        }

        markCreatedDirty(world, created);

        return new SpawnResult(created);
    }

    private void resolveAssetRefsForStagedEntities(
            World stagingWorld, IntBag entityIds) {
        ComponentMapper<AssetRefComponent> assetRefs =
                stagingWorld.getMapper(AssetRefComponent.class);
        ComponentMapper<TextureRegionComponent> regions =
                stagingWorld.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> materials =
                stagingWorld.getMapper(RenderMaterialComponent.class);

        for (int i = 0; i < entityIds.size(); i++) {
            int entityId = entityIds.get(i);
            AssetRefComponent assetRef = assetRefs.getSafe(entityId, null);
            TextureRegionComponent region = regions.getSafe(entityId, null);
            RenderMaterialComponent material = materials.getSafe(entityId, null);
            if (assetRef == null || region == null || material == null) {
                continue;
            }

            if (assetRef.assetId <= 0) {
                throw new IllegalStateException(
                        "AssetRef assetId must be > 0 during prefab resolve: e="
                                + entityId + ", got " + assetRef.assetId);
            }
            String atlasTag = assetRef.atlasTag;
            if (isBlank(atlasTag)) {
                throw new IllegalStateException(
                        "AssetRef atlasTag not set for entity " + entityId);
            }

            AtlasAssetBinding binding =
                    atlasRuntimeService.resolveBinding(assetRef.assetId, atlasTag);
            if (binding == null) {
                region.valid = false;
                material.textureHandle = 0;
                continue;
            }
            AtlasRegionMetadata metadata = binding.metadata();

            region.u1 = metadata.u1();
            region.v1 = metadata.v1();
            region.u2 = metadata.u2();
            region.v2 = metadata.v2();
            region.pixW = metadata.pixelWidth();
            region.pixH = metadata.pixelHeight();
            region.valid = true;
            material.textureHandle = metadata.textureHandle();
        }
    }

    private static void capturePreparedAssetState(
            World stagingWorld,
            IntBag entityIds,
            boolean[] preparedAssetRefs,
            boolean[] preparedRegionValid,
            float[] preparedUvs,
            int[] preparedRegionData) {
        ComponentMapper<AssetRefComponent> assetRefs =
                stagingWorld.getMapper(AssetRefComponent.class);
        ComponentMapper<TextureRegionComponent> regions =
                stagingWorld.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> materials =
                stagingWorld.getMapper(RenderMaterialComponent.class);
        for (int i = 0; i < entityIds.size(); i++) {
            int entityId = entityIds.get(i);
            if (!assetRefs.has(entityId)
                    || !regions.has(entityId)
                    || !materials.has(entityId)) {
                continue;
            }
            TextureRegionComponent region = regions.get(entityId);
            RenderMaterialComponent material = materials.get(entityId);
            preparedAssetRefs[i] = true;
            preparedRegionValid[i] = region.valid;
            int uvOffset = i * 4;
            preparedUvs[uvOffset] = region.u1;
            preparedUvs[uvOffset + 1] = region.v1;
            preparedUvs[uvOffset + 2] = region.u2;
            preparedUvs[uvOffset + 3] = region.v2;
            int dataOffset = i * 3;
            preparedRegionData[dataOffset] = region.pixW;
            preparedRegionData[dataOffset + 1] = region.pixH;
            preparedRegionData[dataOffset + 2] = material.textureHandle;
        }
    }

    private static void publishPreparedAssetState(
            World world,
            int entityId,
            int preparedIndex,
            PreparedPrefabSpawn preparedSpawn) {
        if (!preparedSpawn.preparedAssetRefs[preparedIndex]) {
            return;
        }
        TextureRegionComponent region =
                world.getMapper(TextureRegionComponent.class).get(entityId);
        RenderMaterialComponent material =
                world.getMapper(RenderMaterialComponent.class).get(entityId);
        int uvOffset = preparedIndex * 4;
        region.u1 = preparedSpawn.preparedUvs[uvOffset];
        region.v1 = preparedSpawn.preparedUvs[uvOffset + 1];
        region.u2 = preparedSpawn.preparedUvs[uvOffset + 2];
        region.v2 = preparedSpawn.preparedUvs[uvOffset + 3];
        int dataOffset = preparedIndex * 3;
        region.pixW = preparedSpawn.preparedRegionData[dataOffset];
        region.pixH = preparedSpawn.preparedRegionData[dataOffset + 1];
        region.valid = preparedSpawn.preparedRegionValid[preparedIndex];
        material.textureHandle =
                preparedSpawn.preparedRegionData[dataOffset + 2];
    }

    private static boolean isBlank(String value) {
        if (value == null || value.length() == 0) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) {
                return false;
            }
        }
        return true;
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
                    RuntimePrefabFragment.class);
        } finally {
            validationWorld.dispose();
        }
    }

    private Array<PreparedPhysicsBodyCandidate> prepareAndValidateStaged(
            World stagingWorld,
            RuntimePrefabFragment staged,
            float offsetX,
            float offsetY) {
        ComponentMapper<TransformComponent> transforms =
                stagingWorld.getMapper(TransformComponent.class);
        ComponentMapper<PixscapeIdentityComponent> identities =
                stagingWorld.getMapper(PixscapeIdentityComponent.class);
        ComponentMapper<PhysicsShapesComponent> shapesMapper =
                stagingWorld.getMapper(PhysicsShapesComponent.class);
        ComponentMapper<PhysicsBodyComponent> bodiesMapper =
                stagingWorld.getMapper(PhysicsBodyComponent.class);
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

        if (!sceneMeta.physicsEnabled) {
            PhysicsService.requireNoAuthoredPhysics(
                    stagingWorld,
                    staged.entities,
                    "Runtime prefab fragment");
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
            }
            if (bodiesMapper.has(entityId)) {
                Array<PhysicsShapeData> sources = shapes != null
                        ? shapes.shapes
                        : new Array<>(true, 0, PhysicsShapeData.class);
                physicsCandidates.add(PhysicsService.prepareBodyCandidate(sources));
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
        final boolean[] preparedAssetRefs;
        final boolean[] preparedRegionValid;
        final float[] preparedUvs;
        final int[] preparedRegionData;

        PreparedPrefabSpawn(
                byte[] serializedEntities,
                Array<PreparedPhysicsBodyCandidate> physicsCandidates,
                boolean[] preparedAssetRefs,
                boolean[] preparedRegionValid,
                float[] preparedUvs,
                int[] preparedRegionData) {
            this.serializedEntities = serializedEntities;
            this.physicsCandidates = physicsCandidates;
            this.preparedAssetRefs = preparedAssetRefs;
            this.preparedRegionValid = preparedRegionValid;
            this.preparedUvs = preparedUvs;
            this.preparedRegionData = preparedRegionData;
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
            if (endpointShapes == null
                    || endpointShapes.shapes == null
                    || endpointShapes.shapes.size == 0) {
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
