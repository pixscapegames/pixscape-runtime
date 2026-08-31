package games.pixscape.runtime.gameobject;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class GameObjectRuntimeFragmentSpawnTest {

    @Test
    public void fragmentSerializationAndSpawnPreserveQuadDeformation() {
        World world = runtimeWorld();
        int source = world.create();
        sourceWorldQuadComponents(world, source);
        QuadDeformComponent sourceQuad =
                world.getMapper(QuadDeformComponent.class).create(source);
        setQuad(sourceQuad, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        world.process();
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.entities.add(source);

        SpawnResult result = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService())
                .spawn(world, fragment, 0f, 0f);

        Assert.assertEquals(1, result.createdEntityIds().size());
        int spawned = result.createdEntityIds().get(0);
        QuadDeformComponent restored =
                world.getMapper(QuadDeformComponent.class).getSafe(spawned, null);
        assertQuad(restored, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        Assert.assertTrue(world.getSystem(DirtyTrackerSystem.class)
                .isDirty(spawned, DirtyBits.GEOMETRY));
    }

    @Test
    public void schemaVersionTwoJsonFragmentIsAccepted() {
        World world = runtimeWorld();
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());

        SpawnResult result = spawner.spawn(
                world,
                new JsonReader().parse(buildSourceGameObjectJson()),
                0f,
                0f);

        Assert.assertEquals(3, result.createdEntityIds().size());
    }

    @Test
    public void missingSchemaVersionIsRejectedBeforeInstantiation() {
        assertJsonSchemaRejected("{}");
    }

    @Test
    public void schemaVersionZeroIsRejectedBeforeInstantiation() {
        assertJsonSchemaRejected("{\"schemaVersion\":0}");
    }

    @Test
    public void schemaVersionOneIsRejectedBeforeInstantiation() {
        assertJsonSchemaRejected("{\"schemaVersion\":1}");
    }

    @Test
    public void schemaVersionThreeIsRejectedBeforeInstantiation() {
        assertJsonSchemaRejected("{\"schemaVersion\":3}");
    }

    @Test
    public void inMemorySchemaVersionZeroIsRejectedWithoutPublishingEntities() {
        World world = runtimeWorld();
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.schemaVersion = 0;
        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService())
                    .spawn(world, fragment, 0f, 0f);
            Assert.fail("Fragment schema version must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "schemaVersion"));
        }
        Assert.assertEquals(0, activeEntityCount(world));
    }

    @Test
    public void inMemorySchemaVersionOneIsRejectedWithoutPublishingEntities() {
        World world = runtimeWorld();
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.schemaVersion = 1;
        games.pixscape.runtime.loading.SceneMetaRuntime meta = sceneMeta();
        int nextEntityStableId = meta.nextEntityStableId;
        int nextPhysicsShapeId = meta.nextPhysicsShapeId;
        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), meta, new AtlasRuntimeService())
                    .spawn(world, fragment, 0f, 0f);
            Assert.fail("Fragment schema version must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "schemaVersion"));
        }
        Assert.assertEquals(0, activeEntityCount(world));
        Assert.assertEquals(nextEntityStableId, meta.nextEntityStableId);
        Assert.assertEquals(nextPhysicsShapeId, meta.nextPhysicsShapeId);
    }

    @Test
    public void physicsFragmentIsRejectedWithoutChangingDisabledSceneWorld() {
        World world = runtimeWorld();
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;
        meta.nextEntityStableId = 103;
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        int activeBefore = activeEntityCount(world);

        try {
            spawner.spawn(
                    world,
                    new JsonReader().parse(buildSourceGameObjectJson()),
                    0f,
                    0f);
            Assert.fail("Physics fragment must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "PhysicsBodyComponent"));
        }

        Assert.assertEquals(activeBefore, activeEntityCount(world));
    }

    @Test
    public void physicsSaveFileFragmentIsRejectedInDisabledScene() {
        World world = runtimeWorld();
        GameObjectFixture fixture = buildGameObjectFixture(world);
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;
        meta.nextEntityStableId = 103;
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        int activeBefore = activeEntityCount(world);

        try {
            spawner.spawn(world, fixture.fragment, 0f, 0f);
            Assert.fail("Physics fragment must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "PhysicsBodyComponent"));
        }

        Assert.assertEquals(activeBefore, activeEntityCount(world));
    }

    @Test
    public void gameObjectSpawnPreservesExplicitSpatialFootprintOwnership() {
        World world = runtimeWorld();
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);
        PhysicsShapeData source = world.getMapper(PhysicsShapesComponent.class)
                .get(fixture.sourceBodyAId).shapes.first();
        source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        source.geometry.radius = 1f;
        source.spatialFootprint = true;

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);

        boolean found = false;
        for (int i = 0; i < result.createdEntityIds().size(); i++) {
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                    .getSafe(result.createdEntityIds().get(i), null);
            if (shapes == null) continue;
            for (int shapeIndex = 0; shapeIndex < shapes.shapes.size; shapeIndex++) {
                PhysicsShapeData shape = shapes.shapes.get(shapeIndex);
                if (shape.spatialFootprint) {
                    found = true;
                    Assert.assertEquals(PhysicsGeometryData.SHAPE_CIRCLE,
                            shape.geometry.shapeType);
                }
            }
        }
        Assert.assertTrue(found);
    }

    @Test
    public void nonPhysicsFragmentIsAcceptedInDisabledScene() {
        World world = runtimeWorld();
        int source = world.create();
        world.getMapper(TransformComponent.class).create(source);
        world.process();
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.entities.add(source);
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;

        SpawnResult result = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), meta, new AtlasRuntimeService())
                .spawn(world, fragment, 0f, 0f);

        Assert.assertEquals(1, result.createdEntityIds().size());
        Assert.assertTrue(world.getMapper(TransformComponent.class)
                .has(result.createdEntityIds().get(0)));
    }

    @Test
    public void disabledSceneRejectsShapesOnlyJsonBeforeAllocations() {
        World world = runtimeWorld();
        int existing = world.create();
        world.process();
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;
        meta.nextEntityStableId = 103;
        meta.nextPhysicsShapeId = 29;
        int activeBefore = activeEntityCount(world);

        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), meta, new AtlasRuntimeService())
                    .spawn(
                            world,
                            new JsonReader().parse(buildShapesOnlyGameObjectJson()),
                            0f,
                            0f);
            Assert.fail("Shapes-only fragment must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "PhysicsShapesComponent"));
        }

        Assert.assertTrue(world.getEntityManager().isActive(existing));
        Assert.assertEquals(activeBefore, activeEntityCount(world));
        Assert.assertEquals(103, meta.nextEntityStableId);
        Assert.assertEquals(29, meta.nextPhysicsShapeId);
    }

    @Test
    public void disabledSceneRejectsOrphanJointComponentBeforeAllocations() {
        World world = runtimeWorld();
        int source = world.create();
        world.getMapper(PhysicsMotorJointComponent.class).create(source);
        world.process();
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.entities.add(source);
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;
        meta.nextEntityStableId = 211;
        meta.nextPhysicsShapeId = 37;
        int activeBefore = activeEntityCount(world);

        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), meta, new AtlasRuntimeService())
                    .spawn(world, fragment, 0f, 0f);
            Assert.fail("Orphan joint component must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(
                    "PhysicsMotorJointComponent"));
        }

        Assert.assertTrue(world.getEntityManager().isActive(source));
        Assert.assertEquals(activeBefore, activeEntityCount(world));
        Assert.assertEquals(211, meta.nextEntityStableId);
        Assert.assertEquals(37, meta.nextPhysicsShapeId);
    }

    @Test
    public void invalidInMemoryAssetRefIsRejectedBeforeTargetMutation() {
        SentinelSystem sentinel = new SentinelSystem();
        World world = runtimeWorld(sentinel);
        int source = world.create();
        AssetRefComponent assetRef =
                world.getMapper(AssetRefComponent.class).create(source);
        assetRef.assetId = -1;
        assetRef.atlasTag = "main";
        world.getMapper(TextureRegionComponent.class).create(source);
        world.getMapper(RenderMaterialComponent.class).create(source);
        world.process();
        sentinel.processCount = 0;

        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.entities.add(source);
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;
        meta.nextEntityStableId = 307;
        meta.nextPhysicsShapeId = 41;
        int[] activeBefore = activeEntityIds(world);

        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), meta, new AtlasRuntimeService())
                    .spawn(world, fragment, 0f, 0f);
            Assert.fail("Invalid assetId must reject the fragment.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(
                    expected.getMessage(),
                    expected.getMessage().contains("assetId"));
        }

        Assert.assertArrayEquals(activeBefore, activeEntityIds(world));
        Assert.assertTrue(world.getEntityManager().isActive(source));
        Assert.assertEquals(307, meta.nextEntityStableId);
        Assert.assertEquals(41, meta.nextPhysicsShapeId);
        Assert.assertEquals(0, sentinel.processCount);
    }

    @Test
    public void blankJsonAtlasTagIsRejectedBeforeTargetMutation() {
        World world = runtimeWorld();
        int existing = world.create();
        world.process();
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.physicsEnabled = false;
        meta.nextEntityStableId = 401;
        meta.nextPhysicsShapeId = 53;
        int[] activeBefore = activeEntityIds(world);

        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), meta, new AtlasRuntimeService())
                    .spawn(
                            world,
                            new JsonReader().parse(
                                    buildAssetGameObjectJson(17, "   ")),
                            0f,
                            0f);
            Assert.fail("Blank atlasTag must reject the fragment.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(
                    expected.getMessage(),
                    expected.getMessage().contains("atlasTag"));
        }

        Assert.assertArrayEquals(activeBefore, activeEntityIds(world));
        Assert.assertTrue(world.getEntityManager().isActive(existing));
        Assert.assertEquals(401, meta.nextEntityStableId);
        Assert.assertEquals(53, meta.nextPhysicsShapeId);
    }

    @Test
    public void missingAtlasRegionRemainsNonFatal() {
        World world = runtimeWorld();
        int source = world.create();
        AssetRefComponent assetRef =
                world.getMapper(AssetRefComponent.class).create(source);
        assetRef.assetId = 23;
        assetRef.atlasTag = "main";
        world.getMapper(TextureRegionComponent.class).create(source);
        world.getMapper(RenderMaterialComponent.class).create(source);
        world.process();
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.entities.add(source);

        SpawnResult result = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService())
                .spawn(world, fragment, 0f, 0f);

        Assert.assertEquals(1, result.createdEntityIds().size());
        int spawned = result.createdEntityIds().get(0);
        Assert.assertFalse(world.getMapper(TextureRegionComponent.class)
                .get(spawned).valid);
        Assert.assertEquals(0, world.getMapper(RenderMaterialComponent.class)
                .get(spawned).textureHandle);
    }

    @Test
    public void bodyOnlyGameObjectPublishesEmptyCacheWithoutNativeBody() {
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2());
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(16), sync)
                .build());
        int source = world.create();
        world.getMapper(TransformComponent.class).create(source);
        world.getMapper(PhysicsBodyComponent.class).create(source);
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.entities.add(source);

        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        SpawnResult result = spawner.spawn(world, fragment, 0f, 0f);
        int spawned = result.createdEntityIds().get(0);
        world.getMapper(PhysicsBodyComponent.class).remove(source);
        world.process();

        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).get(spawned);
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).get(spawned);
        Assert.assertNotNull(shapes);
        Assert.assertEquals(0, shapes.shapes.size);
        Assert.assertTrue(compiled.valid);
        Assert.assertEquals(0, compiled.fixtures.size);
        Assert.assertEquals(0, box2d.world.getBodyCount());
        Assert.assertFalse(world.getMapper(PhysicsRuntimeBodyComponent.class).has(spawned));
    }

    @Test
    public void spawnDoesNotClearExistingWorld() {
        World world = runtimeWorld();
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);

        int existing = world.create();
        world.getMapper(TransformComponent.class).create(existing).x = 42f;
        world.process();

        spawner.spawn(world, fixture.fragment, 0f, 0f);

        Assert.assertTrue("Spawn must not clear pre-existing world entities", world.getEntityManager().isActive(existing));
    }

    @Test
    public void spawnReturnsOnlyCreatedEntitiesUsingSubscriptionDiff() {
        World world = runtimeWorld();
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);

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
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);

        IntBag created = result.createdEntityIds();
        Set<Integer> spawnedStableIds = new HashSet<>();

        for (int i = 0; i < created.size(); i++) {
            int eid = created.get(i);
            PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(eid);

            Assert.assertNotNull("Spawned entities must carry identity after spawn", identity);

            int stableId = identity.stableId;
            Assert.assertNotEquals("Spawn must not keep UNASSIGNED stable id", -1L, stableId);
            Assert.assertTrue("Spawn must regenerate stable ids immediately", stableId > 0L);
            Assert.assertNotEquals("Spawned stable ids must be regenerated (not gameObject id A)", fixture.sourceStableIdA, stableId);
            Assert.assertNotEquals("Spawned stable ids must be regenerated (not gameObject id B)", fixture.sourceStableIdB, stableId);
            Assert.assertTrue("Spawned stable ids must be unique", spawnedStableIds.add(stableId));
        }
    }

    @Test
    public void spawnAppliesTransformOffset() {
        World world = runtimeWorld();
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);

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
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);

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
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), sceneMeta(), new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);
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

    @Test
    public void spawnAllocatesFreshShapeIdsFromTargetSceneAuthority() {
        World world = runtimeWorld();
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.nextEntityStableId = 103;
        meta.physicsEnabled = true;
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);

        SpawnResult result = spawner.spawn(world, fixture.fragment, 0f, 0f);
        Set<Integer> shapeIds = new HashSet<>();
        for (int i = 0; i < result.createdEntityIds().size(); i++) {
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class)
                    .getSafe(result.createdEntityIds().get(i), null);
            if (shapes == null) continue;
            for (PhysicsShapeData shape : shapes.shapes) {
                Assert.assertTrue(shape.physicsShapeId > 0);
                Assert.assertNotEquals(10, shape.physicsShapeId);
                Assert.assertNotEquals(11, shape.physicsShapeId);
                Assert.assertTrue(shapeIds.add(shape.physicsShapeId));
            }
        }
        Assert.assertEquals(2, shapeIds.size());
        Assert.assertEquals(3, meta.nextPhysicsShapeId);
    }

    @Test
    public void validationFailurePublishesNoTargetEntitiesAndDoesNotRewindIds() {
        World world = runtimeWorld();
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.nextEntityStableId = 103;
        meta.physicsEnabled = true;
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);
        int activeBefore = activeEntityCount(world);

        PhysicsShapeData invalid = world.getMapper(PhysicsShapesComponent.class)
                .get(fixture.sourceBodyAId).shapes.first();
        invalid.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        invalid.geometry.polygonVertices = new float[]{0f, 0f, 1f, 0f};
        invalid.geometry.polygonVertexCount = 2;

        try {
            spawner.spawn(world, fixture.fragment, 0f, 0f);
            Assert.fail("Invalid staged physics must reject the gameObject before commit.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("PhysicsShapeData"));
        }

        Assert.assertEquals(activeBefore, activeEntityCount(world));
        Assert.assertTrue(world.getEntityManager().isActive(fixture.sourceBodyAId));
        Assert.assertTrue(world.getEntityManager().isActive(fixture.sourceBodyBId));
        Assert.assertTrue(meta.nextPhysicsShapeId > 1);
    }

    @Test
    public void preparationFailurePublishesNothingAndDoesNotProcessTargetWorld() {
        SentinelSystem sentinel = new SentinelSystem();
        World world = runtimeWorld(sentinel);
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.nextEntityStableId = 103;
        meta.physicsEnabled = true;
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        GameObjectFixture fixture = buildGameObjectFixture(world);
        sentinel.processCount = 0;
        int activeBefore = activeEntityCount(world);
        PhysicsJointComponent sourceJoint = null;
        for (int i = 0; i < fixture.fragment.entities.size(); i++) {
            sourceJoint = world.getMapper(PhysicsJointComponent.class)
                    .getSafe(fixture.fragment.entities.get(i), null);
            if (sourceJoint != null) break;
        }
        Assert.assertNotNull(sourceJoint);
        int sourceJointEntityId = -1;
        for (int i = 0; i < fixture.fragment.entities.size(); i++) {
            int candidate = fixture.fragment.entities.get(i);
            if (world.getMapper(PhysicsJointComponent.class).has(candidate)) {
                sourceJointEntityId = candidate;
                break;
            }
        }
        Assert.assertTrue(sourceJointEntityId >= 0);
        sourceJoint.bEid = sourceJointEntityId;

        try {
            spawner.spawn(world, fixture.fragment, 0f, 0f);
            Assert.fail("Invalid prepared joint references must reject the spawn.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "has no PhysicsBodyComponent"));
        }

        Assert.assertEquals(activeBefore, activeEntityCount(world));
        Assert.assertTrue(world.getEntityManager().isActive(fixture.sourceBodyAId));
        Assert.assertTrue(world.getEntityManager().isActive(fixture.sourceBodyBId));
        Assert.assertEquals(0, sentinel.processCount);
        Assert.assertEquals(3, meta.nextPhysicsShapeId);
    }

    private static int activeEntityCount(World world) {
        return world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities()
                .size();
    }

    private static int[] activeEntityIds(World world) {
        IntBag active = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();
        int[] ids = new int[active.size()];
        System.arraycopy(active.getData(), 0, ids, 0, active.size());
        return ids;
    }

    private static void assertJsonSchemaRejected(String json) {
        World world = runtimeWorld();
        games.pixscape.runtime.loading.SceneMetaRuntime meta = sceneMeta();
        GameObjectRuntimeFragmentSpawner spawner =
                new GameObjectRuntimeFragmentSpawner(
                        new IdentityRegistry(), meta, new AtlasRuntimeService());
        int activeBefore = activeEntityCount(world);
        int nextEntityStableId = meta.nextEntityStableId;
        int nextPhysicsShapeId = meta.nextPhysicsShapeId;
        try {
            spawner.spawn(
                    world, new JsonReader().parse(json), 0f, 0f);
            Assert.fail("Fragment schema must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "schemaVersion"));
        }
        Assert.assertEquals(activeBefore, activeEntityCount(world));
        Assert.assertEquals(nextEntityStableId, meta.nextEntityStableId);
        Assert.assertEquals(nextPhysicsShapeId, meta.nextPhysicsShapeId);
    }

    private static games.pixscape.runtime.loading.SceneMetaRuntime sceneMeta() {
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                new games.pixscape.runtime.loading.SceneMetaRuntime();
        meta.nextEntityStableId = 103;
        meta.physicsEnabled = true;
        return meta;
    }

    private static World runtimeWorld() {
        return runtimeWorld(null);
    }

    private static World runtimeWorld(BaseSystem sentinel) {
        WorldConfigurationBuilder builder = new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(64));
        if (sentinel != null) builder.with(sentinel);
        return new World(builder
                .build());
    }

    private static void sourceWorldQuadComponents(World world, int entityId) {
        world.getMapper(TransformComponent.class).create(entityId);
        world.getMapper(DimensionsComponent.class).create(entityId);
        world.getMapper(OrientedBoundsComponent.class).create(entityId);
        world.getMapper(AABBComponent.class).create(entityId);
    }

    private static void setQuad(QuadDeformComponent quad,
                                float blX, float blY,
                                float brX, float brY,
                                float trX, float trY,
                                float tlX, float tlY) {
        quad.blX = blX;
        quad.blY = blY;
        quad.brX = brX;
        quad.brY = brY;
        quad.trX = trX;
        quad.trY = trY;
        quad.tlX = tlX;
        quad.tlY = tlY;
    }

    private static void assertQuad(QuadDeformComponent quad,
                                   float blX, float blY,
                                   float brX, float brY,
                                   float trX, float trY,
                                   float tlX, float tlY) {
        Assert.assertNotNull(quad);
        Assert.assertEquals(blX, quad.blX, 0f);
        Assert.assertEquals(blY, quad.blY, 0f);
        Assert.assertEquals(brX, quad.brX, 0f);
        Assert.assertEquals(brY, quad.brY, 0f);
        Assert.assertEquals(trX, quad.trX, 0f);
        Assert.assertEquals(trY, quad.trY, 0f);
        Assert.assertEquals(tlX, quad.tlX, 0f);
        Assert.assertEquals(tlY, quad.tlY, 0f);
    }

    private static final class SentinelSystem extends BaseSystem {
        int processCount;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }

    private static GameObjectFixture buildGameObjectFixture(World fragmentOwnerWorld) {
        GdxNativesLoader.load();

        String sourceJson = buildSourceGameObjectJson();

        WorldSerializationManager wsm = fragmentOwnerWorld.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(fragmentOwnerWorld));

        GameObjectRuntimeFragment fragment = wsm.load(
                new ByteArrayInputStream(sourceJson.getBytes(StandardCharsets.UTF_8)),
                GameObjectRuntimeFragment.class
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

        return new GameObjectFixture(
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

    private static String buildSourceGameObjectJson() {
        World sourceWorld = runtimeWorld();

        WorldSerializationManager wsm = sourceWorld.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(
                new JsonArtemisSerializer(sourceWorld)
                        .setUsePrototypes(false));

        int bodyA = sourceWorld.create();
        TransformComponent ta = sourceWorld.getMapper(TransformComponent.class).create(bodyA);
        ta.x = 5f;
        ta.y = -3f;
        sourceWorld.getMapper(PhysicsBodyComponent.class).create(bodyA);
        PhysicsShapesComponent shapesA =
                sourceWorld.getMapper(PhysicsShapesComponent.class).create(bodyA);
        PhysicsShapeData shapeA = new PhysicsShapeData();
        shapeA.geometry = new PhysicsGeometryData();
        shapeA.physicsShapeId = 10;
        shapesA.shapes.add(shapeA);

        PixscapeIdentityComponent ida = sourceWorld.getMapper(PixscapeIdentityComponent.class).create(bodyA);
        ida.stableId = 101;

        int bodyB = sourceWorld.create();
        TransformComponent tb = sourceWorld.getMapper(TransformComponent.class).create(bodyB);
        tb.x = 6f;
        tb.y = -2f;
        sourceWorld.getMapper(PhysicsBodyComponent.class).create(bodyB);
        PhysicsShapesComponent shapesB =
                sourceWorld.getMapper(PhysicsShapesComponent.class).create(bodyB);
        PhysicsShapeData shapeB = new PhysicsShapeData();
        shapeB.geometry = new PhysicsGeometryData();
        shapeB.physicsShapeId = 11;
        shapesB.shapes.add(shapeB);

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

        GameObjectRuntimeFragment request = new GameObjectRuntimeFragment();
        request.entities.add(bodyA);
        request.entities.add(bodyB);
        request.entities.add(joint);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, request);

        return out.toString(StandardCharsets.UTF_8);
    }

    private static String buildShapesOnlyGameObjectJson() {
        World sourceWorld = runtimeWorld();
        try {
            WorldSerializationManager serialization =
                    sourceWorld.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(
                    new JsonArtemisSerializer(sourceWorld)
                            .setUsePrototypes(false));
            int entityId = sourceWorld.create();
            PhysicsShapesComponent shapes = sourceWorld.getMapper(
                    PhysicsShapesComponent.class).create(entityId);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = 23;
            shape.geometry = new PhysicsGeometryData();
            shapes.shapes.add(shape);
            sourceWorld.process();

            GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
            fragment.entities.add(entityId);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            serialization.save(out, fragment);
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            sourceWorld.dispose();
        }
    }

    private static String buildAssetGameObjectJson(int assetId, String atlasTag) {
        World sourceWorld = runtimeWorld();
        try {
            WorldSerializationManager serialization =
                    sourceWorld.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(
                    new JsonArtemisSerializer(sourceWorld)
                            .setUsePrototypes(false));
            int entityId = sourceWorld.create();
            AssetRefComponent assetRef = sourceWorld.getMapper(
                    AssetRefComponent.class).create(entityId);
            assetRef.assetId = assetId;
            assetRef.atlasTag = atlasTag;
            sourceWorld.getMapper(TextureRegionComponent.class).create(entityId);
            sourceWorld.getMapper(RenderMaterialComponent.class).create(entityId);
            sourceWorld.process();

            GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
            fragment.entities.add(entityId);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            serialization.save(out, fragment);
            return out.toString(StandardCharsets.UTF_8);
        } finally {
            sourceWorld.dispose();
        }
    }

    private static void assertFixtureSanity(String json) {
        JsonValue root = new JsonReader().parse(json);
        JsonValue entities = root.get("entities");

        Assert.assertNotNull("Fixture serialization must contain entities", entities);

        Set<Integer> entityIds = new HashSet<>();
        Set<Integer> stableIds = new HashSet<>();

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
                stableIds.add(identity.getInt("stableId"));
            }
        }

        Assert.assertEquals("Fixture must include exactly two physics body entities", 2, bodyCount);
        Assert.assertEquals("Fixture must include exactly one physics joint entity", 1, jointCount);
        Assert.assertNotNull("Joint aEid must be present", jointAEid);
        Assert.assertNotNull("Joint bEid must be present", jointBEid);
        Assert.assertTrue("Joint aEid must reference an entity in fragment closure", entityIds.contains(jointAEid));
        Assert.assertTrue("Joint bEid must reference an entity in fragment closure", entityIds.contains(jointBEid));
        Assert.assertTrue("Fixture must include transform x=5,y=-3", foundTransformFiveMinusThree);
        Assert.assertTrue("Fixture must include source stableId 101", stableIds.contains(101));
        Assert.assertTrue("Fixture must include source stableId 102", stableIds.contains(102));
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

    private static final class GameObjectFixture {
        final GameObjectRuntimeFragment fragment;
        final String sourceJson;
        final float sourceX;
        final float sourceY;
        final long sourceStableIdA;
        final long sourceStableIdB;
        final int sourceBodyAId;
        final int sourceBodyBId;

        GameObjectFixture(GameObjectRuntimeFragment fragment,
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
