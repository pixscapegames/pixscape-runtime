package games.pixscape.runtime.prefab;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class RuntimePrefabFragmentSpawner {

    private static final String PREFAB_LABEL = "runtime prefab fragment";

    private final IdentityRegistry identityRegistry;

    public RuntimePrefabFragmentSpawner(IdentityRegistry identityRegistry) {
        if (identityRegistry == null) {
            throw new IllegalArgumentException("identityRegistry must not be null");
        }
        this.identityRegistry = identityRegistry;
    }

    public SpawnResult spawn(World world, SaveFileFormat fragment, float offsetX, float offsetY) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (fragment == null) {
            throw new IllegalArgumentException("fragment must not be null");
        }

        WorldSerializationManager wsm = requireSerializationManager(world);
        JsonArtemisSerializer serializer = requireJsonSerializer(world, wsm);
        validateSourceEntityClosure(world, fragment);
        JsonValue prepared = serializeFragment(wsm, fragment);
        return spawnPrepared(world, serializer, prepared, offsetX, offsetY);
    }

    public SpawnResult spawnSerialized(World world, JsonValue fragmentJson,
                                       float offsetX, float offsetY, String fragmentLabel) {
        if (world == null) {
            throw new IllegalArgumentException("world must not be null");
        }
        if (fragmentJson == null) {
            throw new IllegalArgumentException("fragmentJson must not be null");
        }
        WorldSerializationManager wsm = requireSerializationManager(world);
        JsonArtemisSerializer serializer = requireJsonSerializer(world, wsm);
        JsonValue detachedCopy = new JsonReader().parse(
                fragmentJson.toJson(JsonWriter.OutputType.json));
        try {
            return spawnPrepared(world, serializer, detachedCopy, offsetX, offsetY);
        } catch (RuntimeException failure) {
            String label = fragmentLabel != null ? fragmentLabel : PREFAB_LABEL;
            throw new IllegalStateException("Failed to spawn prefab fragment '" + label
                    + "': " + failure.getMessage(), failure);
        }
    }

    private SpawnResult spawnPrepared(World world,
                                      JsonArtemisSerializer serializer,
                                      JsonValue prepared,
                                      float offsetX,
                                      float offsetY) {
        FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
        prepareDetachedFragment(prepared, allocator, offsetX, offsetY);
        preflightDeserialize(prepared);

        identityRegistry.bind(world);
        identityRegistry.rebuild();

        SaveFileFormat loaded = serializer.load(prepared, SaveFileFormat.class);
        IntBag created = copyEntityIds(loaded);
        finalizeCommittedEntities(world, created);
        return new SpawnResult(created);
    }

    private static WorldSerializationManager requireSerializationManager(World world) {
        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        if (wsm == null) {
            throw new IllegalStateException("WorldSerializationManager is required");
        }
        return wsm;
    }

    private static JsonArtemisSerializer requireJsonSerializer(World world,
                                                               WorldSerializationManager wsm) {
        if (!(wsm.getSerializer() instanceof JsonArtemisSerializer)) {
            wsm.setSerializer(new JsonArtemisSerializer(world));
        }
        return (JsonArtemisSerializer) wsm.getSerializer();
    }

    private static JsonValue serializeFragment(WorldSerializationManager wsm,
                                               SaveFileFormat fragment) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, fragment);
        JsonValue root = new JsonReader().parse(new ByteArrayInputStream(out.toByteArray()));
        if (root == null || !root.isObject()) {
            throw invalid("serialized fragment root is missing or is not an object");
        }
        return root;
    }

    private static void validateSourceEntityClosure(World world, SaveFileFormat fragment) {
        if (fragment.entities == null) {
            throw invalid("source entity list is null");
        }
        IntSet sourceEntities = new IntSet();
        for (int i = 0; i < fragment.entities.size(); i++) {
            int entityId = fragment.entities.get(i);
            if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
                throw invalid("source entity ID=" + entityId + " is not active");
            }
            if (!sourceEntities.add(entityId)) {
                throw invalid("duplicate source entity ID=" + entityId);
            }
        }

        ComponentMapper<PhysicsJointComponent> mJoint =
                world.getMapper(PhysicsJointComponent.class);
        ComponentMapper<PhysicsGearJointComponent> mGear =
                world.getMapper(PhysicsGearJointComponent.class);
        for (int i = 0; i < fragment.entities.size(); i++) {
            int entityId = fragment.entities.get(i);
            PhysicsJointComponent joint = mJoint.getSafe(entityId, null);
            if (joint != null) {
                requireSourceReference(entityId, "PhysicsJointComponent.aEid",
                        joint.aEid, sourceEntities);
                requireSourceReference(entityId, "PhysicsJointComponent.bEid",
                        joint.bEid, sourceEntities);
            }
            PhysicsGearJointComponent gear = mGear.getSafe(entityId, null);
            if (gear != null) {
                requireSourceReference(entityId, "PhysicsGearJointComponent.joint1Eid",
                        gear.joint1Eid, sourceEntities);
                requireSourceReference(entityId, "PhysicsGearJointComponent.joint2Eid",
                        gear.joint2Eid, sourceEntities);
            }
        }
    }

    private static void requireSourceReference(int owner, String referenceType,
                                               int referencedEntity,
                                               IntSet sourceEntities) {
        if (!sourceEntities.contains(referencedEntity)) {
            throw invalid("entity=" + owner + ", reference=" + referenceType
                    + " has missing source entity ID=" + referencedEntity);
        }
    }

    private static void prepareDetachedFragment(JsonValue root,
                                                FixtureIdAllocatorSystem allocator,
                                                float offsetX,
                                                float offsetY) {
        JsonValue entities = root.get("entities");
        if (entities == null || !entities.isObject()) {
            throw invalid("entities object is missing");
        }

        IntSet entityIds = new IntSet();
        collectAndValidateEntities(entities, entityIds);
        validateEntityReferences(entities, entityIds);

        IntIntMap fixtureRemap = new IntIntMap();
        IntIntMap fixtureBodies = new IntIntMap();
        collectAndValidateFixtures(entities, fixtureBodies);
        allocateAndRemapFixtures(entities, allocator, fixtureRemap, fixtureBodies);
        remapSpatialFixtureReferences(entities, fixtureRemap, fixtureBodies);
        applyDetachedEntityEdits(entities, offsetX, offsetY);
    }

    private static void collectAndValidateEntities(JsonValue entities,
                                                   IntSet entityIds) {
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int sourceEntityId = sourceEntityId(entity);
            if (sourceEntityId < 0) {
                throw invalid("entity has an invalid source entity ID: " + entity.name);
            }
            if (!entityIds.add(sourceEntityId)) {
                throw invalid("duplicate source entity ID=" + sourceEntityId);
            }
            if (entity.has("tag") || entity.has("groups")) {
                throw invalid("entity=" + sourceEntityId
                        + " uses tag/group manager state, which is not supported in runtime prefab fragments");
            }
            requireComponents(entity, sourceEntityId);
        }
    }

    private static void validateEntityReferences(JsonValue entities,
                                                 IntSet entityIds) {
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int owner = sourceEntityId(entity);
            JsonValue components = requireComponents(entity, owner);
            JsonValue joint = component(components, "PhysicsJointComponent");
            if (joint != null) {
                int aEid = joint.getInt("aEid", -1);
                int bEid = joint.getInt("bEid", -1);
                requireMappedEntity(owner, "PhysicsJointComponent.aEid", aEid, entityIds);
                requireMappedEntity(owner, "PhysicsJointComponent.bEid", bEid, entityIds);
                int type = joint.getInt("type", 0);
                if (type < 0 || type > 9) {
                    throw invalid("entity=" + owner
                            + " has unsupported PhysicsJointComponent.type=" + type);
                }
            }

            JsonValue gear = component(components, "PhysicsGearJointComponent");
            if (gear != null) {
                requireMappedEntity(owner, "PhysicsGearJointComponent.joint1Eid",
                        gear.getInt("joint1Eid", -1), entityIds);
                requireMappedEntity(owner, "PhysicsGearJointComponent.joint2Eid",
                        gear.getInt("joint2Eid", -1), entityIds);
            }
        }
    }

    private static void requireMappedEntity(int owner,
                                            String referenceType,
                                            int sourceReference,
                                            IntSet entityIds) {
        if (!entityIds.contains(sourceReference)) {
            throw invalid("entity=" + owner + ", reference=" + referenceType
                    + " has missing source entity ID=" + sourceReference);
        }
    }

    private static void collectAndValidateFixtures(JsonValue entities,
                                                   IntIntMap fixtureBodies) {
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int owner = sourceEntityId(entity);
            JsonValue fixtures = field(component(requireComponents(entity, owner),
                    "PhysicsFixturesComponent"), "fixtures");
            if (fixtures == null) continue;
            if (!fixtures.isArray()) {
                throw invalid("entity=" + owner + " has a non-array fixtures field");
            }
            for (int index = 0; index < fixtures.size; index++) {
                JsonValue fixture = fixtures.get(index);
                if (fixture == null || !fixture.isObject()) {
                    throw invalid("entity=" + owner + ", fixtureIndex=" + index
                            + " is null or malformed");
                }
                int sourceFixtureId = fixture.getInt("fixtureId", 0);
                if (sourceFixtureId <= 0) {
                    throw invalid("entity=" + owner + ", fixtureIndex=" + index
                            + " has invalid source fixtureId=" + sourceFixtureId);
                }
                if (fixtureBodies.containsKey(sourceFixtureId)) {
                    throw invalid("duplicate source fixtureId=" + sourceFixtureId
                            + " at entity=" + owner + ", fixtureIndex=" + index);
                }
                validateFixtureDefinition(owner, index, fixture);
                fixtureBodies.put(sourceFixtureId, owner);
            }
        }
    }

    private static void allocateAndRemapFixtures(JsonValue entities,
                                                 FixtureIdAllocatorSystem allocator,
                                                 IntIntMap fixtureRemap,
                                                 IntIntMap sourceFixtureBodies) {
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int owner = sourceEntityId(entity);
            JsonValue fixtures = field(component(requireComponents(entity, owner),
                    "PhysicsFixturesComponent"), "fixtures");
            if (fixtures == null) continue;
            if (allocator == null && fixtures.size > 0) {
                throw new IllegalStateException(
                        "FixtureIdAllocatorSystem is required to spawn prefab fixtures.");
            }
            for (int index = 0; index < fixtures.size; index++) {
                JsonValue fixture = fixtures.get(index);
                int sourceFixtureId = fixture.getInt("fixtureId");
                int targetFixtureId;
                do {
                    targetFixtureId = allocator.allocateNewFixtureId();
                } while (sourceFixtureBodies.containsKey(targetFixtureId));
                fixtureRemap.put(sourceFixtureId, targetFixtureId);
                setInt(fixture, "fixtureId", targetFixtureId);
            }
        }
    }

    private static void validateFixtureDefinition(int owner, int index, JsonValue fixture) {
        int shapeType = fixture.getInt("shapeType", 0);
        if (shapeType < 0 || shapeType > 2) {
            throw invalid("entity=" + owner + ", fixtureIndex=" + index
                    + " has unsupported shapeType=" + shapeType);
        }
        if (shapeType == 0
                && (!(fixture.getFloat("halfW", 0.5f) > 0f)
                || !(fixture.getFloat("halfH", 0.5f) > 0f))) {
            throw invalid("entity=" + owner + ", fixtureIndex=" + index
                    + " has non-positive box dimensions");
        }
        if (shapeType == 1 && !(fixture.getFloat("radius", 0.5f) > 0f)) {
            throw invalid("entity=" + owner + ", fixtureIndex=" + index
                    + " has non-positive circle radius");
        }
        if (shapeType == 2) {
            int count = fixture.getInt("polyCount", 0);
            JsonValue vertices = fixture.get("polyVerts");
            if (count < 3 || count > 8 || vertices == null || !vertices.isArray()
                    || vertices.size < count * 2) {
                throw invalid("entity=" + owner + ", fixtureIndex=" + index
                        + " has invalid polygon vertices; polyCount=" + count);
            }
        }
        float density = fixture.getFloat("density", 1f);
        float friction = fixture.getFloat("friction", 0.2f);
        float restitution = fixture.getFloat("restitution", 0f);
        if (!finite(density) || density < 0f
                || !finite(friction) || friction < 0f
                || !finite(restitution) || restitution < 0f) {
            throw invalid("entity=" + owner + ", fixtureIndex=" + index
                    + " has invalid material values");
        }
    }

    private static void remapSpatialFixtureReferences(JsonValue entities,
                                                      IntIntMap fixtureRemap,
                                                      IntIntMap fixtureBodies) {
        IntSet claims = new IntSet();
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int owner = sourceEntityId(entity);
            JsonValue blocks = field(component(requireComponents(entity, owner),
                    "SpatialBlocksComponent"), "blocks");
            if (blocks == null) continue;
            if (!blocks.isArray()) {
                throw invalid("entity=" + owner + " has a non-array spatial blocks field");
            }
            for (int index = 0; index < blocks.size; index++) {
                JsonValue block = blocks.get(index);
                if (block == null || !block.isObject()) {
                    throw invalid("entity=" + owner + ", blockIndex=" + index
                            + " is null or malformed");
                }
                int blockId = block.getInt("id", 0);
                boolean collision = block.getBoolean("physicsCollision", false);
                int sourceFixtureId = block.getInt("fixtureId", 0);
                if (!collision) {
                    if (sourceFixtureId != 0) {
                        throw invalid("entity=" + owner + ", blockId=" + blockId
                                + " owns fixtureId=" + sourceFixtureId
                                + " while physicsCollision is false");
                    }
                    continue;
                }
                int targetFixtureId = fixtureRemap.get(sourceFixtureId, 0);
                if (sourceFixtureId <= 0 || targetFixtureId <= 0) {
                    throw invalid("entity=" + owner + ", blockId=" + blockId
                            + " references missing source fixtureId=" + sourceFixtureId);
                }
                if (fixtureBodies.get(sourceFixtureId, -1) != owner) {
                    throw invalid("entity=" + owner + ", blockId=" + blockId
                            + " references fixtureId=" + sourceFixtureId
                            + " owned by another prefab body");
                }
                if (!claims.add(sourceFixtureId)) {
                    throw invalid("source fixtureId=" + sourceFixtureId
                            + " is claimed by multiple spatial blocks; entity=" + owner
                            + ", blockId=" + blockId);
                }
                setInt(block, "fixtureId", targetFixtureId);
            }
        }
    }

    private static void applyDetachedEntityEdits(JsonValue entities,
                                                 float offsetX,
                                                 float offsetY) {
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int owner = sourceEntityId(entity);
            JsonValue components = requireComponents(entity, owner);
            JsonValue transform = component(components, "TransformComponent");
            if (transform != null) {
                setFloat(transform, "x", transform.getFloat("x", 0f) + offsetX);
                setFloat(transform, "y", transform.getFloat("y", 0f) + offsetY);
            }
            JsonValue identity = component(components, "PixscapeIdentityComponent");
            if (identity != null) {
                setInt(identity, "stableId", IdentityRegistry.UNASSIGNED_STABLE_ID);
            }
        }
    }

    private static void preflightDeserialize(JsonValue prepared) {
        World temporaryWorld = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
        try {
            WorldSerializationManager temporaryWsm =
                    temporaryWorld.getSystem(WorldSerializationManager.class);
            JsonArtemisSerializer temporarySerializer =
                    new JsonArtemisSerializer(temporaryWorld);
            temporaryWsm.setSerializer(temporarySerializer);
            JsonValue copy = new JsonReader().parse(
                    prepared.toJson(JsonWriter.OutputType.json));
            SaveFileFormat loaded = temporarySerializer.load(copy, SaveFileFormat.class);
            validatePreflightWorld(temporaryWorld, loaded);
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Invalid " + PREFAB_LABEL + ": detached deserialization preflight failed",
                    failure);
        } finally {
            temporaryWorld.dispose();
        }
    }

    private static void validatePreflightWorld(World world, SaveFileFormat loaded) {
        ComponentMapper<AssetRefComponent> mAssetRef = world.getMapper(AssetRefComponent.class);
        ComponentMapper<TextureRegionComponent> mTexture =
                world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mMaterial =
                world.getMapper(RenderMaterialComponent.class);
        for (int i = 0; i < loaded.entities.size(); i++) {
            int entityId = loaded.entities.get(i);
            AssetRefComponent assetRef = mAssetRef.getSafe(entityId, null);
            if (assetRef == null
                    || mTexture.getSafe(entityId, null) == null
                    || mMaterial.getSafe(entityId, null) == null) {
                continue;
            }
            if (assetRef.assetId < 0) {
                throw invalid("entity=" + entityId
                        + " has AssetRefComponent without a valid assetId");
            }
            if (assetRef.atlasTag == null || assetRef.atlasTag.trim().isEmpty()) {
                throw invalid("entity=" + entityId
                        + " has AssetRefComponent without atlasTag");
            }
        }
    }

    private void finalizeCommittedEntities(World world, IntBag created) {
        ComponentMapper<PixscapeIdentityComponent> mIdentity =
                world.getMapper(PixscapeIdentityComponent.class);
        for (int i = 0; i < created.size(); i++) {
            int entityId = created.get(i);
            PixscapeIdentityComponent identity = mIdentity.getSafe(entityId, null);
            if (identity != null) {
                identity.stableId = IdentityRegistry.UNASSIGNED_STABLE_ID;
            }
            identityRegistry.ensureStableId(entityId);
        }

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty == null) return;
        for (int i = 0; i < created.size(); i++) {
            dirty.mark(created.get(i), DirtyBits.GEOMETRY
                    | DirtyBits.MATERIAL
                    | DirtyBits.COLOR
                    | DirtyBits.ORDER
                    | DirtyBits.LAYER
                    | DirtyBits.PHYSICS
                    | DirtyBits.JOINTS);
        }
    }

    private static IntBag copyEntityIds(SaveFileFormat loaded) {
        if (loaded == null || loaded.entities == null) {
            throw new IllegalStateException(
                    "Committed " + PREFAB_LABEL + " returned no entity mapping");
        }
        IntBag created = new IntBag(loaded.entities.size());
        for (int i = 0; i < loaded.entities.size(); i++) {
            created.add(loaded.entities.get(i));
        }
        return created;
    }

    private static JsonValue requireComponents(JsonValue entity, int sourceEntityId) {
        JsonValue components = entity.get("components");
        if (components == null || !components.isObject()) {
            throw invalid("entity=" + sourceEntityId + " has no components object");
        }
        return components;
    }

    private static JsonValue component(JsonValue components, String simpleName) {
        if (components == null) return null;
        for (JsonValue value = components.child; value != null; value = value.next) {
            if (simpleName.equals(value.name)
                    || value.name != null && value.name.endsWith("." + simpleName)) {
                return value;
            }
        }
        return null;
    }

    private static JsonValue field(JsonValue object, String name) {
        return object != null ? object.get(name) : null;
    }

    private static int sourceEntityId(JsonValue entity) {
        try {
            return entity != null && entity.name != null
                    ? Integer.parseInt(entity.name) : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static void setInt(JsonValue object, String field, int value) {
        JsonValue current = object.get(field);
        if (current == null) {
            object.addChild(field, new JsonValue((long) value));
        } else {
            current.set(new JsonValue((long) value));
        }
    }

    private static void setFloat(JsonValue object, String field, float value) {
        JsonValue current = object.get(field);
        if (current == null) {
            object.addChild(field, new JsonValue((double) value));
        } else {
            current.set(new JsonValue((double) value));
        }
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static IllegalStateException invalid(String reason) {
        return new IllegalStateException("Invalid " + PREFAB_LABEL + ": " + reason);
    }
}
