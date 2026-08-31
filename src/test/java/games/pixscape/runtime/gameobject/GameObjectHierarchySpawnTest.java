package games.pixscape.runtime.gameobject;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.Aspect;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class GameObjectHierarchySpawnTest {
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
        Assert.assertEquals(40f, rootTransform.x, 0f);
        Assert.assertEquals(60f, rootTransform.y, 0f);
        Assert.assertEquals(0.5f, rootTransform.rotationRad, 0f);
        Assert.assertEquals(2f, rootTransform.scaleX, 0f);
        Assert.assertEquals(7f, rootTransform.originX, 0f);
        TransformComponent childTransform = world.getMapper(TransformComponent.class).get(child);
        Assert.assertEquals(4f, childTransform.x, 0f);
        Assert.assertEquals(5f, childTransform.y, 0f);
        Assert.assertEquals(6, world.getMapper(EntityIndexComponent.class).get(child).zIndex);
        Assert.assertTrue(world.getMapper(PointLightComponent.class).has(light));
        Assert.assertTrue(world.getMapper(ConeLightComponent.class).has(light));

        PropertySet properties = world.getMapper(CustomPropertiesComponent.class)
                .get(root).properties;
        Assert.assertEquals(childStable, properties.getObjectStableId("target", -1));
        Assert.assertEquals(rootStable, properties.getClassValue("nested")
                .properties().getObjectStableId("owner", -1));
        Assert.assertEquals(-1, properties.getObjectStableId("none", 0));
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

    private static GameObjectAsset hierarchy() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 100;
        GameObjectAsset.GameObjectEntityData root = data(100, -1, true, 10f, 20f, 2);
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

    private static int stable(World world, int entityId) {
        return world.getMapper(PixscapeIdentityComponent.class).get(entityId).stableId;
    }

    private static int parent(World world, int entityId) {
        return world.getMapper(GameObjectMemberComponent.class).get(entityId).parentStableId;
    }
}
