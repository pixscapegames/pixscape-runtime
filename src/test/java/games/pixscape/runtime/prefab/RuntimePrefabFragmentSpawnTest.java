package games.pixscape.runtime.prefab;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class RuntimePrefabFragmentSpawnTest {

    @Test
    public void spawnAllocatesFreshFixtureIdsAndRemapsSpatialReferences() {
        World world = runtimeWorld();
        games.pixscape.runtime.system.FixtureIdAllocatorSystem allocator =
                world.getSystem(games.pixscape.runtime.system.FixtureIdAllocatorSystem.class);
        allocator.sceneMeta().nextFixtureId = 51;

        int source = world.create();
        FixtureDefData sourceFixture = new FixtureDefData();
        sourceFixture.fixtureId = 50;
        world.getMapper(PhysicsFixturesComponent.class).create(source)
                .fixtures.add(sourceFixture);
        SpatialBlockData sourceBlock = new SpatialBlockData();
        sourceBlock.id = 5;
        sourceBlock.physicsCollision = true;
        sourceBlock.fixtureId = 50;
        world.getMapper(SpatialBlocksComponent.class).create(source)
                .blocks.add(sourceBlock);
        world.process();

        SaveFileFormat fragment = new SaveFileFormat();
        fragment.entities.add(source);
        SpawnResult result = new RuntimePrefabFragmentSpawner(new IdentityRegistry())
                .spawn(world, fragment, 0f, 0f);
        int created = result.createdEntityIds().get(0);
        FixtureDefData createdFixture = world.getMapper(PhysicsFixturesComponent.class)
                .get(created).fixtures.first();
        SpatialBlockData createdBlock = world.getMapper(SpatialBlocksComponent.class)
                .get(created).blocks.first();

        Assert.assertEquals(51, createdFixture.fixtureId);
        Assert.assertEquals(51, createdBlock.fixtureId);
        Assert.assertEquals(52, allocator.sceneMeta().nextFixtureId);
        Assert.assertEquals(50, sourceFixture.fixtureId);
    }

    @Test
    public void spawnDoesNotClearExistingWorld() {
        World world = runtimeWorld();
        RuntimePrefabFragmentSpawner spawner = new RuntimePrefabFragmentSpawner(new IdentityRegistry());
        PrefabFixture fixture = buildPrefabFixture(world);

        int existing = world.create();
        world.getMapper(TransformComponent.class).create(existing).x = 42f;
        world.process();

        spawner.spawn(world, fixture.fragment, 0f, 0f);

        Assert.assertTrue("Spawn must not clear pre-existing world entities", world.getEntityManager().isActive(existing));
    }

    @Test
    public void spawnReturnsOnlyCreatedEntitiesUsingSubscriptionDiff() {
        World world = runtimeWorld();
        RuntimePrefabFragmentSpawner spawner = new RuntimePrefabFragmentSpawner(new IdentityRegistry());
        PrefabFixture fixture = buildPrefabFixture(world);

        int preExisting = world.create();
        world.process();

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);

        IntBag created = result.createdEntityIds();
        Assert.assertNotNull("SpawnResult must expose created entity ids", created);
        Assert.assertEquals("Fixture spawn should create exactly 3 entities (2 bodies + 1 joint)", 3, created.size());

        for (int i = 0; i < created.size(); i++) {
            Assert.assertNotEquals("SpawnResult must not include pre-existing entities", preExisting, created.get(i));
            Assert.assertTrue("SpawnResult ids must be active entities", world.getEntityManager().isActive(created.get(i)));
        }
    }

    @Test
    public void spawnRegeneratesStableIds() {
        World world = runtimeWorld();
        RuntimePrefabFragmentSpawner spawner = new RuntimePrefabFragmentSpawner(new IdentityRegistry());
        PrefabFixture fixture = buildPrefabFixture(world);

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);

        IntBag created = result.createdEntityIds();
        Set<Long> spawnedStableIds = new HashSet<>();

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(eid);

            Assert.assertNotNull("Spawned entities must carry identity after spawn", identity);

            long stableId = identity.stableId;
            Assert.assertNotEquals("Spawn must not keep UNASSIGNED stable id", -1L, stableId);
            Assert.assertTrue("Spawn must regenerate stable ids immediately", stableId > 0L);
            Assert.assertNotEquals("Spawned stable ids must be regenerated (not prefab id A)", fixture.sourceStableIdA, stableId);
            Assert.assertNotEquals("Spawned stable ids must be regenerated (not prefab id B)", fixture.sourceStableIdB, stableId);
            Assert.assertTrue("Spawned stable ids must be unique", spawnedStableIds.add(stableId));
        }
    }

    @Test
    public void spawnAppliesTransformOffset() {
        World world = runtimeWorld();
        RuntimePrefabFragmentSpawner spawner = new RuntimePrefabFragmentSpawner(new IdentityRegistry());
        PrefabFixture fixture = buildPrefabFixture(world);

        float offsetX = 10f;
        float offsetY = 20f;

        SpawnResult result = spawner.spawn(world, fixture.fragment, offsetX, offsetY);

        Assert.assertTrue("Test fixture expects at least one spawned entity", result.createdEntityIds().size() > 0);

        boolean foundOffsetTransform = false;

        for (int i = 0; i < result.createdEntityIds().size(); i++) {
            int eid = result.createdEntityIds().get(i);
            TransformComponent t = world.getMapper(TransformComponent.class).get(eid);

            if (t != null
                    && Math.abs(t.x - (fixture.sourceX + offsetX)) < 1e-4f
                    && Math.abs(t.y - (fixture.sourceY + offsetY)) < 1e-4f) {
                foundOffsetTransform = true;
                break;
            }
        }

        Assert.assertTrue("At least one spawned transform must include spawn offset", foundOffsetTransform);
    }

    @Test
    public void spawnPreservesAndRemapsJointReferences() {
        World world = runtimeWorld();
        RuntimePrefabFragmentSpawner spawner = new RuntimePrefabFragmentSpawner(new IdentityRegistry());
        PrefabFixture fixture = buildPrefabFixture(world);

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);

        IntBag created = result.createdEntityIds();
        Assert.assertTrue("Test fixture expects spawned entities", created.size() > 0);

        PhysicsJointComponent joint = null;

        for (int i = 0; i < created.size(); i++) {
            PhysicsJointComponent candidate = world.getMapper(PhysicsJointComponent.class).get(created.get(i));
            if (candidate != null) {
                joint = candidate;
                break;
            }
        }

        Assert.assertNotNull("Spawned fragment should contain a remapped joint entity", joint);
        Assert.assertNotEquals("Joint aEid must be remapped from source id", fixture.sourceBodyAId, joint.aEid);
        Assert.assertNotEquals("Joint bEid must be remapped from source id", fixture.sourceBodyBId, joint.bEid);
        Assert.assertTrue("Joint aEid must point to active spawned body", world.getEntityManager().isActive(joint.aEid));
        Assert.assertTrue("Joint bEid must point to active spawned body", world.getEntityManager().isActive(joint.bEid));
    }

    @Test
    public void spawnMarksRenderAndPhysicsDirty() {
        World world = runtimeWorld();
        RuntimePrefabFragmentSpawner spawner = new RuntimePrefabFragmentSpawner(new IdentityRegistry());
        PrefabFixture fixture = buildPrefabFixture(world);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);

        IntBag created = result.createdEntityIds();
        Assert.assertTrue("Test fixture expects at least one spawned entity", created.size() > 0);

        boolean sawPhysicsOrJointDirty = false;

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);

            boolean renderDirty = dirty.isDirty(
                    eid,
                    DirtyBits.GEOMETRY | DirtyBits.MATERIAL | DirtyBits.COLOR | DirtyBits.ORDER | DirtyBits.LAYER
            );

            boolean physicsDirty = dirty.isDirty(eid, DirtyBits.PHYSICS)
                    || dirty.isDirty(eid, DirtyBits.JOINTS);

            if (renderDirty && physicsDirty) {
                sawPhysicsOrJointDirty = true;
                break;
            }
        }

        Assert.assertTrue("Spawn should mark render + physics/joint dirty for spawned runtime entities", sawPhysicsOrJointDirty);
    }

    private static World runtimeWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(),
                        new games.pixscape.runtime.system.FixtureIdAllocatorSystem(
                                new games.pixscape.runtime.loading.SceneMetaRuntime()),
                        new DirtyTrackerSystem(64))
                .build());
    }

    private static PrefabFixture buildPrefabFixture(World fragmentOwnerWorld) {
        GdxNativesLoader.load();

        String sourceJson = buildSourcePrefabJson();

        WorldSerializationManager wsm = fragmentOwnerWorld.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(fragmentOwnerWorld));

        SaveFileFormat fragment = wsm.load(
                new ByteArrayInputStream(sourceJson.getBytes(StandardCharsets.UTF_8)),
                SaveFileFormat.class
        );

        fragmentOwnerWorld.process();

        int sourceBodyAId = -1;
        int sourceBodyBId = -1;
        float sourceX = 0f;
        float sourceY = 0f;

        for (int i = 0; i < fragment.entities.size(); i++) {
            int eid = fragment.entities.get(i);

            PixscapeIdentityComponent id = fragmentOwnerWorld.getMapper(PixscapeIdentityComponent.class).get(eid);
            TransformComponent t = fragmentOwnerWorld.getMapper(TransformComponent.class).get(eid);

            if (id != null && id.stableId == 101L) {
                sourceBodyAId = eid;
                if (t != null) {
                    sourceX = t.x;
                    sourceY = t.y;
                }
            }

            if (id != null && id.stableId == 102L) {
                sourceBodyBId = eid;
            }
        }

        Assert.assertTrue("Fixture body A must be loaded into target world", sourceBodyAId >= 0);
        Assert.assertTrue("Fixture body B must be loaded into target world", sourceBodyBId >= 0);

        return new PrefabFixture(
                fragment,
                sourceJson,
                sourceX,
                sourceY,
                101L,
                102L,
                sourceBodyAId,
                sourceBodyBId
        );
    }

    private static String buildSourcePrefabJson() {
        World sourceWorld = runtimeWorld();

        WorldSerializationManager wsm = sourceWorld.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(sourceWorld));

        int bodyA = sourceWorld.create();
        TransformComponent ta = sourceWorld.getMapper(TransformComponent.class).create(bodyA);
        ta.x = 5f;
        ta.y = -3f;
        sourceWorld.getMapper(PhysicsBodyComponent.class).create(bodyA);

        PixscapeIdentityComponent ida = sourceWorld.getMapper(PixscapeIdentityComponent.class).create(bodyA);
        ida.stableId = 101;

        int bodyB = sourceWorld.create();
        TransformComponent tb = sourceWorld.getMapper(TransformComponent.class).create(bodyB);
        tb.x = 6f;
        tb.y = -2f;
        sourceWorld.getMapper(PhysicsBodyComponent.class).create(bodyB);

        PixscapeIdentityComponent idb = sourceWorld.getMapper(PixscapeIdentityComponent.class).create(bodyB);
        idb.stableId = 102;

        int joint = sourceWorld.create();
        PhysicsJointComponent jointBase = sourceWorld.getMapper(PhysicsJointComponent.class).create(joint);
        jointBase.type = PhysicsJointComponent.TYPE_DISTANCE;
        jointBase.aEid = bodyA;
        jointBase.bEid = bodyB;

        PhysicsDistanceJointComponent distance = sourceWorld.getMapper(PhysicsDistanceJointComponent.class).create(joint);
        distance.lengthM = 1f;

        sourceWorld.process();

        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(bodyA);
        request.entities.add(bodyB);
        request.entities.add(joint);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, request);

        return out.toString(StandardCharsets.UTF_8);
    }

    private static void assertFixtureSanity(String json) {
        JsonValue root = new JsonReader().parse(json);
        JsonValue entities = root.get("entities");

        Assert.assertNotNull("Fixture serialization must contain entities", entities);

        Set<Integer> entityIds = new HashSet<>();
        Set<Long> stableIds = new HashSet<>();

        int bodyCount = 0;
        int jointCount = 0;
        boolean foundTransformFiveMinusThree = false;
        Integer jointAEid = null;
        Integer jointBEid = null;

        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            int eid = Integer.parseInt(entity.name);
            entityIds.add(eid);

            JsonValue components = entity.get("components");
            if (components == null) continue;

            if (component(components, "PhysicsBodyComponent") != null) {
                bodyCount++;
            }

            JsonValue joint = component(components, "PhysicsJointComponent");
            if (joint != null) {
                jointCount++;
                jointAEid = joint.getInt("aEid");
                jointBEid = joint.getInt("bEid");
            }

            JsonValue transform = component(components, "TransformComponent");
            if (transform != null) {
                float x = transform.getFloat("x");
                float y = transform.getFloat("y");

                if (Math.abs(x - 5f) < 1e-5f && Math.abs(y + 3f) < 1e-5f) {
                    foundTransformFiveMinusThree = true;
                }
            }

            JsonValue identity = component(components, "PixscapeIdentityComponent");
            if (identity != null) {
                stableIds.add(identity.getLong("stableId"));
            }
        }

        Assert.assertEquals("Fixture must include exactly two physics body entities", 2, bodyCount);
        Assert.assertEquals("Fixture must include exactly one physics joint entity", 1, jointCount);
        Assert.assertNotNull("Joint aEid must be present", jointAEid);
        Assert.assertNotNull("Joint bEid must be present", jointBEid);
        Assert.assertTrue("Joint aEid must reference an entity in fragment closure", entityIds.contains(jointAEid));
        Assert.assertTrue("Joint bEid must reference an entity in fragment closure", entityIds.contains(jointBEid));
        Assert.assertTrue("Fixture must include transform x=5,y=-3", foundTransformFiveMinusThree);
        Assert.assertTrue("Fixture must include source stableId 101", stableIds.contains(101L));
        Assert.assertTrue("Fixture must include source stableId 102", stableIds.contains(102L));
    }

    private static JsonValue component(JsonValue components, String simpleName) {
        if (components == null || simpleName == null) return null;

        for (JsonValue child = components.child; child != null; child = child.next) {
            if (child.name == null) continue;
            if (child.name.equals(simpleName) || child.name.endsWith("." + simpleName)) {
                return child;
            }
        }

        return null;
    }

    private static final class PrefabFixture {
        final SaveFileFormat fragment;
        final String sourceJson;
        final float sourceX;
        final float sourceY;
        final long sourceStableIdA;
        final long sourceStableIdB;
        final int sourceBodyAId;
        final int sourceBodyBId;

        PrefabFixture(SaveFileFormat fragment,
                      String sourceJson,
                      float sourceX,
                      float sourceY,
                      long sourceStableIdA,
                      long sourceStableIdB,
                      int sourceBodyAId,
                      int sourceBodyBId) {
            this.fragment = fragment;
            this.sourceJson = sourceJson;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceStableIdA = sourceStableIdA;
            this.sourceStableIdB = sourceStableIdB;
            this.sourceBodyAId = sourceBodyAId;
            this.sourceBodyBId = sourceBodyBId;
        }
    }
}
