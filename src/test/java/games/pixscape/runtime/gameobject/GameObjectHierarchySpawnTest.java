package games.pixscape.runtime.gameobject;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class GameObjectHierarchySpawnTest {
    @Test
    public void incompleteSpatialVisualRejectsBeforePrepareOrSpawnAllocatesAnything() throws Exception {
        World world = new World(new WorldConfigurationBuilder().build());
        IdentityRegistry identities = new IdentityRegistry();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        identities.bind(world, meta);
        var file = new com.badlogic.gdx.files.FileHandle(java.io.File.createTempFile("spatial-invalid-", ".gameobject"));
        try {
            // Literal input bypasses the writer's validation, as an incomplete file would.
            file.writeString("{schemaVersion:7,rootSourceEntityId:1,entities:["
                    + "{sourceEntityId:1,transform:{scaleX:1,scaleY:1},gameObject:{},spatialFootprint:{}},"
                    + "{sourceEntityId:2,parentSourceEntityId:1,transform:{scaleX:1,scaleY:1},assetRef:{assetId:7}}]}", false);
            var spawner = new GameObjectRuntimeFragmentSpawner(identities, meta, new AtlasRuntimeService());
            int nextId = meta.nextEntityStableId;
            IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                    () -> spawner.prepare(file, "invalid"));
            Assert.assertTrue(failure.getMessage().contains("sourceEntityId 2"));
            Assert.assertTrue(failure.getMessage().contains("own authored representation"));
            GameObjectAsset asset = hierarchy();
            asset.entities.first().spatialFootprint = new games.pixscape.runtime.component.spatial.SpatialFootprintComponent();
            asset.entities.get(1).assetRef = new GameObjectAsset.AssetRefData();
            asset.entities.get(1).assetRef.assetId = 7;
            failure = Assert.assertThrows(IllegalArgumentException.class,
                    () -> spawner.spawnAsset(world, asset, "invalid", 0, 0));
            Assert.assertTrue(failure.getMessage().contains("own authored representation"));
            world.process();
            Assert.assertEquals(nextId, meta.nextEntityStableId);
            Assert.assertEquals(0, world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
        } finally {
            file.delete();
            identities.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void authoredContoursSurviveSerializedFragmentAndIndependentRuntimeSpawns() {
        GameObjectAsset asset = hierarchy();
        float[] closed = {0, 0, 20, 20, 0, 20, 20, 0};
        float[] open = {-10, 5, 20, -7, 40, 12};
        asset.entities.get(1).polygonVertices = closed.clone();
        asset.entities.get(3).polylineVertices = open.clone();
        GameObjectAssetLoader loader = new GameObjectAssetLoader();
        GameObjectAsset loaded = loader.fromJson(loader.toJson(asset));
        GameObjectRuntimeFragment fragment = GameObjectRuntimeFragment.fromAsset(loaded, "contours.gameobject");
        World world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(64)).build());
        IdentityRegistry identities = new IdentityRegistry();
        try {
            GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                    identities, new SceneMetaRuntime(), new AtlasRuntimeService());
            SpawnResult first = spawner.spawn(world, fragment, 10, 20);
            SpawnResult second = spawner.spawn(world, fragment, 30, 40);
            world.process();
            PolygonComponent polygon = world.getMapper(PolygonComponent.class).get(first.createdEntityIds().get(1));
            PolylineComponent line = world.getMapper(PolylineComponent.class).get(first.createdEntityIds().get(3));
            Assert.assertArrayEquals(closed, polygon.vertices, 0f);
            Assert.assertArrayEquals(open, line.vertices, 0f);
            Assert.assertFalse(world.getMapper(PolygonComponent.class).has(first.createdEntityIds().get(3)));
            polygon.vertices[0] = 999;
            line.vertices[0] = 999;
            Assert.assertArrayEquals(closed, world.getMapper(PolygonComponent.class)
                    .get(second.createdEntityIds().get(1)).vertices, 0f);
            Assert.assertArrayEquals(open, world.getMapper(PolylineComponent.class)
                    .get(second.createdEntityIds().get(3)).vertices, 0f);
            Assert.assertArrayEquals(closed, loaded.entities.get(1).polygonVertices, 0f);
            Assert.assertArrayEquals(open, loaded.entities.get(3).polylineVertices, 0f);
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void unpublishedOldSpatialFragmentsAndAssetsAreRejectedWithoutConversion() {
        GameObjectRuntimeFragment fragment = new GameObjectRuntimeFragment();
        fragment.schemaVersion = 4;
        Assert.assertThrows(IllegalArgumentException.class, fragment::toAsset);
        GameObjectAsset asset = hierarchy();
        asset.schemaVersion = 4;
        Assert.assertThrows(IllegalArgumentException.class, () -> GameObjectRuntimeFragment.fromAsset(asset, "old"));
    }

    @Test
    public void repeatedPreparedSpawnsKeepInstancesIndependentAndAvoidTopologyRebuild() throws Exception {
        World world = new World(new WorldConfigurationBuilder().with(
                new DirtyTrackerSystem(64), new games.pixscape.runtime.system.GameObjectHierarchySystem()).build());
        IdentityRegistry identities = new IdentityRegistry();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.physicsEnabled = true;
        identities.bind(world, meta);
        world.process();
        var hierarchy = world.getSystem(games.pixscape.runtime.system.GameObjectHierarchySystem.class);
        int rebuilds = hierarchy.rebuildCount();
        var file = new com.badlogic.gdx.files.FileHandle(java.io.File.createTempFile("prepared-", ".gameobject"));
        try {
            new GameObjectAssetLoader().save(file, physicalHierarchy());
            var spawner = new GameObjectRuntimeFragmentSpawner(identities, meta, new AtlasRuntimeService());
            var model = spawner.prepare(file, "gameobjects/physical.gameobject");
            file.writeString("invalid JSON", false);
            SpawnResult first = spawner.spawnPrepared(world, model, 10, 20);
            SpawnResult second = spawner.spawnPrepared(world, model, 30, 40);
            int firstStableId = stable(world, first.rootEntityId());
            Assert.assertEquals(first.rootEntityId(), identities.findByStableId(stable(world, first.rootEntityId())));
            Assert.assertEquals(second.rootEntityId(), identities.findByStableId(stable(world, second.rootEntityId())));
            world.process();
            Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
            var fixtures = world.getMapper(games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent.class);
            var firstFixture = fixtures.get(first.rootEntityId()).fixtures.first();
            var secondFixture = fixtures.get(second.rootEntityId()).fixtures.first();
            Assert.assertNotSame(firstFixture, secondFixture);
            Assert.assertNotEquals(firstFixture.physicsShapeId, secondFixture.physicsShapeId);
            firstFixture.halfWidth = 999;
            SpawnResult third = spawner.spawnPrepared(world, model, 0, 0);
            Assert.assertNotEquals(999f, fixtures.get(third.rootEntityId()).fixtures.first().halfWidth, 0f);
            world.delete(first.rootEntityId());
            // Remove the complete hierarchy; deletion is not a pool return.
            for (int i = 1; i < first.createdEntityIds().size(); i++) world.delete(first.createdEntityIds().get(i));
            world.process();
            Assert.assertEquals(-1, identities.findByStableId(firstStableId));
            Assert.assertEquals(rebuilds, hierarchy.rebuildCount());
            Assert.assertTrue(world.getEntityManager().isActive(second.rootEntityId()));
            // Expert integrations can explicitly replace the allocator metadata on the same World.
            SceneMetaRuntime replacementMeta = new SceneMetaRuntime(meta);
            new GameObjectRuntimeFragmentSpawner(identities, replacementMeta, new AtlasRuntimeService())
                    .spawnPrepared(world, model, 50, 60);
            Assert.assertEquals(second.rootEntityId(), identities.findByStableId(stable(world, second.rootEntityId())));
            Assert.assertTrue(replacementMeta.nextEntityStableId > meta.nextEntityStableId);
        } finally {
            file.delete();
            identities.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void actorSpatialRectanglesSurviveAssetSerializationAndRuntimeSpawn() {
        GameObjectAsset asset = hierarchy();
        GameObjectAsset.GameObjectEntityData child = asset.entities.get(1);
        child.spatialFootprint = new games.pixscape.runtime.component.spatial.SpatialFootprintComponent();
        child.spatialRepresentation = new GameObjectAsset.SpatialRepresentationData();
        child.spatialRepresentation.rectangles = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
        child.spatialRepresentation.rectangles.staticRectangle = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
        child.spatialRepresentation.rectangles.staticRectangle.pivotX = 37.25f;
        child.spatialFootprint.localOffsetXPx = 49f;
        child.spatialRepresentation.rectangles.staticRectangle.x = -71f;
        GameObjectAssetLoader loader = new GameObjectAssetLoader();
        GameObjectAsset loaded = loader.fromJson(loader.toJson(asset));
        Assert.assertEquals(37.25f, loaded.entities.get(1).spatialRepresentation.rectangles.staticRectangle.pivotX, 0);
        World world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(64)).build());
        try {
            SpawnResult spawned = new GameObjectRuntimeFragmentSpawner(new IdentityRegistry(), new SceneMetaRuntime(), new AtlasRuntimeService())
                    .spawnAsset(world, loaded, "gameobjects/actor-foot-test.gameobject", 0, 0);
            world.process();
            SpatialRepresentationComponent s = world.getMapper(SpatialRepresentationComponent.class).get(spawned.createdEntityIds().get(1));
            Assert.assertEquals(37.25f, s.rectangles.staticRectangle.pivotX, 0);
            Assert.assertEquals(-71f, s.rectangles.staticRectangle.x, 0);
            Assert.assertEquals(49f, world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class)
                    .get(spawned.createdEntityIds().get(1)).localOffsetXPx, 0);
            loaded.entities.get(1).spatialRepresentation.rectangles.staticRectangle.pivotX = 90;
            Assert.assertEquals(37.25f, s.rectangles.staticRectangle.pivotX, 0);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void fragmentCreatesRealHierarchyWithFreshIdsAndRemappedReferences() {
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64)).build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 1000;
        GameObjectAsset asset = hierarchy();
        GameObjectRuntimeFragment fragment = GameObjectRuntimeFragment.fromAsset(
                asset, "gameobjects/enemy.gameobject");

        SpawnResult result = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), meta, new AtlasRuntimeService())
                .spawn(world, fragment, 30f, 40f);
        world.process();

        Assert.assertEquals(4, result.createdEntityIds().size());
        int root = result.rootEntityId();
        int child = result.createdEntityIds().get(1);
        int nested = result.createdEntityIds().get(2);
        int light = result.createdEntityIds().get(3);
        int rootStable = stable(world, root);
        int childStable = stable(world, child);
        int nestedStable = stable(world, nested);
        Assert.assertTrue(rootStable >= 1000);
        Assert.assertNotEquals(100, rootStable);
        Assert.assertNotEquals(200, childStable);
        Assert.assertEquals(rootStable, parent(world, child));
        Assert.assertEquals(rootStable, parent(world, nested));
        Assert.assertEquals(nestedStable, parent(world, light));

        Assert.assertEquals("gameobjects/enemy.gameobject",
                world.getMapper(GameObjectComponent.class).get(root).sourceAssetId);
        Assert.assertEquals("", world.getMapper(GameObjectComponent.class)
                .get(nested).sourceAssetId);
        TransformComponent rootTransform = world.getMapper(TransformComponent.class).get(root);
        Assert.assertEquals(30f, rootTransform.x, 0f);
        Assert.assertEquals(40f, rootTransform.y, 0f);
        Assert.assertEquals(0.5f, rootTransform.rotationRad, 0f);
        Assert.assertEquals(2f, rootTransform.scaleX, 0f);
        Assert.assertEquals(7f, rootTransform.originX, 0f);
        Assert.assertEquals(0, world.getMapper(EntityIndexComponent.class).get(root).zIndex);
        TransformComponent childTransform = world.getMapper(TransformComponent.class).get(child);
        Assert.assertEquals(4f, childTransform.x, 0f);
        Assert.assertEquals(5f, childTransform.y, 0f);
        Assert.assertEquals(6, world.getMapper(EntityIndexComponent.class).get(child).zIndex);
        Assert.assertTrue(world.getMapper(PointLightComponent.class).has(light));
        Assert.assertTrue(world.getMapper(ConeLightComponent.class).has(light));
        Assert.assertEquals(-33.5f, world.getMapper(ConeLightComponent.class).get(light).inclinationDeg, 0f);

        PropertySet properties = world.getMapper(CustomPropertiesComponent.class)
                .get(root).properties;
        Assert.assertEquals(childStable, properties.getObjectStableId("target", -1));
        Assert.assertEquals(rootStable, properties.getClassValue("nested")
                .properties().getObjectStableId("owner", -1));
        Assert.assertEquals(-1, properties.getObjectStableId("none", 0));
    }

    @Test
    public void nestedListReferencesEnumsAndDoubleValuesSurviveIndependentInstances() {
        World world = new World(new WorldConfigurationBuilder().build());
        try {
            GameObjectAsset asset = hierarchy();
            asset.entities.get(0).customProperties.putList("items", com.badlogic.gdx.utils.Array.with(
                    games.pixscape.runtime.property.PropertyValue.ofObjectStableId(200),
                    games.pixscape.runtime.property.PropertyValue.ofClass("Config", new PropertySet()
                            .putList("self", com.badlogic.gdx.utils.Array.with(
                                    games.pixscape.runtime.property.PropertyValue.ofObjectStableId(100))))))
                    .putFloat("precise", 16777217d)
                    .put("flags", games.pixscape.runtime.property.PropertyValue.ofEnum(
                            new games.pixscape.runtime.property.EnumDefinition("Flags", games.pixscape.runtime.property.PropertyType.INTEGER,
                                    true, "A", "B"), 3));
            GameObjectAsset reloaded = new GameObjectAssetLoader().fromJson(new GameObjectAssetLoader().toJson(asset));
            var spawner = new GameObjectRuntimeFragmentSpawner(new IdentityRegistry(), new SceneMetaRuntime(), new AtlasRuntimeService());
            SpawnResult first = spawner.spawnAsset(world, reloaded, "gameobjects/test.gameobject", 0, 0);
            SpawnResult second = spawner.spawnAsset(world, reloaded, "gameobjects/test.gameobject", 10, 0);
            for (SpawnResult instance : new SpawnResult[]{first, second}) {
                var values = world.getMapper(CustomPropertiesComponent.class).get(instance.rootEntityId()).properties;
                Assert.assertEquals(stable(world, instance.createdEntityIds().get(1)), values.getListValue("items").getObjectStableId(0));
                Assert.assertEquals(stable(world, instance.rootEntityId()), values.getListValue("items").getClassValue(1)
                        .properties().getListValue("self").getObjectStableId(0));
                Assert.assertEquals(16777217d, values.getDouble("precise", 0), 0);
                Assert.assertEquals(3, values.getEnumValue("flags").intValue());
            }
            world.getMapper(CustomPropertiesComponent.class).get(first.rootEntityId()).properties.putList("items", new com.badlogic.gdx.utils.Array<>());
            Assert.assertEquals(2, world.getMapper(CustomPropertiesComponent.class).get(second.rootEntityId()).properties.getListValue("items").size());
            Assert.assertEquals(200, reloaded.entities.get(0).customProperties.getListValue("items").getObjectStableId(0));
        } finally { world.dispose(); }
    }

    @Test
    public void invalidAssetFailsBeforePublishingAnyEntity() {
        World world = new World(new WorldConfigurationBuilder().build());
        GameObjectAsset asset = hierarchy();
        asset.entities.get(0).customProperties.putObjectStableId("bad", 9999);
        try {
            new GameObjectRuntimeFragmentSpawner(
                    new IdentityRegistry(), new SceneMetaRuntime(), new AtlasRuntimeService())
                    .spawnAsset(world, asset, "gameobjects/bad.gameobject", 0f, 0f);
            Assert.fail("Expected validation failure.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("external OBJECT reference"));
        }
        world.process();
        Assert.assertEquals(0, world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities().size());
    }

    @Test
    public void physicalAssetSpawnAllocatesFreshSceneShapeIdsAndDoesNotMutateAsset() {
        World world = new World(new WorldConfigurationBuilder().build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 40;
        meta.physicsEnabled = true;
        GameObjectAsset asset = physicalHierarchy();
        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), meta, new AtlasRuntimeService());

        SpawnResult first = spawner.spawnAsset(world, asset, "gameobjects/physical.gameobject", 0f, 0f);
        SpawnResult second = spawner.spawn(world, GameObjectRuntimeFragment.fromAsset(
                asset, "gameobjects/physical.gameobject"), 0f, 0f);
        SpawnResult third = spawner.spawnAsset(world, asset, "gameobjects/physical.gameobject", 0f, 0f);
        world.process();

        int firstRoot = first.rootEntityId();
        int firstNested = first.createdEntityIds().get(1);
        int firstMember = first.createdEntityIds().get(2);
        int secondRoot = second.rootEntityId();
        int secondNested = second.createdEntityIds().get(1);
        int secondMember = second.createdEntityIds().get(2);
        int thirdRoot = third.rootEntityId();
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC,
                world.getMapper(PhysicsBodyComponent.class).get(firstRoot).type);
        Assert.assertEquals(2, world.getMapper(PhysicsShapesComponent.class).get(firstRoot).shapes.size);
        int firstRootShape = shapeId(world, firstRoot, 0);
        int firstNestedShape = shapeId(world, firstNested, 0);
        int secondRootShape = shapeId(world, secondRoot, 0);
        int secondNestedShape = shapeId(world, secondNested, 0);
        Assert.assertEquals(40, firstRootShape);
        Assert.assertEquals(41, firstRootShape + 1);
        Assert.assertEquals(42, firstNestedShape);
        Assert.assertEquals(43, shapeId(world, firstMember, 0));
        Assert.assertEquals(44, secondRootShape);
        Assert.assertEquals(46, secondNestedShape);
        Assert.assertEquals(47, shapeId(world, secondMember, 0));
        Assert.assertEquals(48, shapeId(world, thirdRoot, 0));
        Assert.assertEquals(52, meta.nextPhysicsShapeId);

        PhysicsShapeData sceneShape = world.getMapper(PhysicsShapesComponent.class)
                .get(firstRoot).shapes.first();
        sceneShape.geometry.halfWidth = 99f;
        Assert.assertEquals(2f, asset.entities.get(0).physicsShapes.get(0).geometry.halfWidth, 0f);
        Assert.assertEquals(2f, world.getMapper(PhysicsShapesComponent.class)
                .get(secondRoot).shapes.first().geometry.halfWidth, 0f);
        Assert.assertEquals(1, asset.entities.get(0).physicsShapes.get(0).localShapeId);
    }

    @Test
    public void physicalSpatialMemberRestoresAuthoredHeightAndFootprintMarker() {
        World world = new World(new WorldConfigurationBuilder().build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextPhysicsShapeId = 40;
        meta.physicsEnabled = true;
        GameObjectAsset asset = physicalHierarchy();
        GameObjectAsset.GameObjectEntityData member = asset.entities.get(2);
        member.spatialRepresentation = new GameObjectAsset.SpatialRepresentationData();
        member.spatialRepresentation.rectangles = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
        member.spatialRepresentation.rectangles.staticRectangle = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
        member.spatialFootprint = new games.pixscape.runtime.component.spatial.SpatialFootprintComponent();
        member.spatialFootprint.altitude = 3f;
        member.spatialRepresentation.rectangles = games.pixscape.runtime.spatial.SpatialRectangleTestData.rectangles(6f);
        GameObjectAsset.PhysicsShapeData shape = member.physicsShapes.get(0);
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = 2f;

        SpawnResult result = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), meta, new AtlasRuntimeService())
                .spawnAsset(world, asset, "gameobjects/spatial.gameobject", 0f, 0f);
        world.process();

        int spawnedMember = result.createdEntityIds().get(2);
        SpatialRepresentationComponent height = world.getMapper(SpatialRepresentationComponent.class).get(spawnedMember);
        Assert.assertEquals(3f, world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).get(spawnedMember).altitude, 0f);
        Assert.assertEquals(6f, height.rectangles.staticRectangle.height, 0f);
        PhysicsShapeData spawnedShape = world.getMapper(PhysicsShapesComponent.class)
                .get(spawnedMember).shapes.first();
        Assert.assertNotNull(spawnedShape.geometry);
        Assert.assertNotEquals(shape.localShapeId, spawnedShape.physicsShapeId);
    }

    @Test
    public void physicalAssetSpawnsJointsAfterBodiesWithRemappedEndpoints() {
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64)).build());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 50;
        meta.physicsEnabled = true;
        GameObjectAsset asset = physicalHierarchy();
        GameObjectAsset.GameObjectJointData joint = new GameObjectAsset.GameObjectJointData();
        joint.jointLocalId = 11;
        joint.type = PhysicsJointComponent.TYPE_DISTANCE;
        joint.bodyALocalEntityId = 1;
        joint.bodyBLocalEntityId = 2;
        joint.collideConnected = true;
        joint.anchorAx = .1f;
        joint.anchorAy = .2f;
        joint.anchorBx = .3f;
        joint.anchorBy = .4f;
        joint.distance = new GameObjectAsset.DistanceJointData();
        joint.distance.lengthM = 2.5f;
        joint.distance.frequencyHz = 4f;
        joint.distance.dampingRatio = .6f;
        asset.joints.add(joint);

        SpawnResult result = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(), meta, new AtlasRuntimeService())
                .spawnAsset(world, asset, "gameobjects/joint.gameobject", 0f, 0f);
        world.process();

        Assert.assertEquals(4, result.createdEntityIds().size());
        int jointEntityId = result.createdEntityIds().get(
                result.createdEntityIds().size() - 1);
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).get(jointEntityId);
        Assert.assertEquals(PhysicsJointComponent.TYPE_DISTANCE, base.type);
        Assert.assertEquals(result.createdEntityIds().get(0), base.aEid);
        Assert.assertEquals(result.createdEntityIds().get(1), base.bEid);
        Assert.assertTrue(base.collideConnected);
        PhysicsDistanceJointComponent distance = world.getMapper(
                PhysicsDistanceJointComponent.class).get(jointEntityId);
        Assert.assertEquals(2.5f, distance.lengthM, 0f);
        Assert.assertEquals(4f, distance.frequencyHz, 0f);
        Assert.assertEquals(.6f, distance.dampingRatio, 0f);
    }

    private static GameObjectAsset hierarchy() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 100;
        GameObjectAsset.GameObjectEntityData root = data(100, -1, true, 0f, 0f, 0);
        root.transform.rotationRad = 0.5f;
        root.transform.scaleX = 2f;
        root.transform.scaleY = 2f;
        root.transform.originX = 7f;
        root.customProperties = new PropertySet()
                .putObjectStableId("target", 200)
                .putObjectStableId("none", -1)
                .putClass("nested", "Link", new PropertySet()
                        .putObjectStableId("owner", 100));
        GameObjectAsset.GameObjectEntityData child = data(200, 100, false, 4f, 5f, 6);
        GameObjectAsset.GameObjectEntityData nested = data(300, 100, true, 8f, 9f, 3);
        GameObjectAsset.GameObjectEntityData light = data(400, 300, false, 1f, 2f, 4);
        light.pointLight = new GameObjectAsset.PointLightData();
        light.pointLight.enabled = true;
        light.coneLight = new GameObjectAsset.ConeLightData();
        light.coneLight.inclinationDeg = -33.5f;
        light.coneLight.enabled = true;
        asset.entities.add(root);
        asset.entities.add(child);
        asset.entities.add(nested);
        asset.entities.add(light);
        return asset;
    }

    private static GameObjectAsset.GameObjectEntityData data(
            int source, int parent, boolean gameObject, float x, float y, int z) {
        GameObjectAsset.GameObjectEntityData value = new GameObjectAsset.GameObjectEntityData();
        value.sourceEntityId = source;
        value.parentSourceEntityId = parent;
        value.transform = new GameObjectAsset.TransformData();
        value.transform.x = x;
        value.transform.y = y;
        value.transform.scaleX = 1f;
        value.transform.scaleY = 1f;
        value.entityIndex = new GameObjectAsset.EntityIndexData();
        value.entityIndex.zIndex = z;
        if (gameObject) value.gameObject = new GameObjectAsset.GameObjectData();
        return value;
    }

    private static GameObjectAsset physicalHierarchy() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        GameObjectAsset.GameObjectEntityData root = data(1, -1, true, 0f, 0f, 0);
        root.physicsBody = physicsBody(PhysicsBodyComponent.DYNAMIC);
        root.physicsShapes.add(physicsShape(1, 2f));
        root.physicsShapes.add(physicsShape(2, 3f));
        GameObjectAsset.GameObjectEntityData nested = data(2, 1, true, 4f, 5f, 0);
        nested.physicsBody = physicsBody(PhysicsBodyComponent.KINEMATIC);
        nested.physicsShapes.add(physicsShape(1, 4f));
        GameObjectAsset.GameObjectEntityData member = data(3, 1, false, 2f, 3f, 0);
        member.physicsBody = physicsBody(PhysicsBodyComponent.STATIC);
        member.physicsShapes.add(physicsShape(1, 5f));
        asset.entities.add(root);
        asset.entities.add(nested);
        asset.entities.add(member);
        return asset;
    }

    private static GameObjectAsset.PhysicsBodyData physicsBody(int type) {
        GameObjectAsset.PhysicsBodyData body = new GameObjectAsset.PhysicsBodyData();
        body.type = type;
        return body;
    }

    private static GameObjectAsset.PhysicsShapeData physicsShape(int localId, float halfWidth) {
        GameObjectAsset.PhysicsShapeData shape = new GameObjectAsset.PhysicsShapeData();
        shape.localShapeId = localId;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        shape.geometry.halfWidth = halfWidth;
        shape.geometry.halfHeight = 1f;
        return shape;
    }

    private static int shapeId(World world, int entityId, int shapeIndex) {
        return world.getMapper(PhysicsShapesComponent.class).get(entityId)
                .shapes.get(shapeIndex).physicsShapeId;
    }

    private static int stable(World world, int entityId) {
        return world.getMapper(PixscapeIdentityComponent.class).get(entityId).stableId;
    }

    private static int parent(World world, int entityId) {
        return world.getMapper(GameObjectMemberComponent.class).get(entityId).parentStableId;
    }
}
