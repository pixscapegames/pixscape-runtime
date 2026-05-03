package games.pixscape.runtime.prefab;

import com.artemis.World;
import com.artemis.Aspect;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsWheelJointComponent;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderSpriteSyncSystem;
import org.junit.Assert;
import org.junit.Test;

public class PrefabSpawnServiceTest {
    @Test
    public void loadPrefabAssetFromJson() {
        GdxNativesLoader.load();
        PrefabLoader loader = new PrefabLoader();
        FileHandle file = FileHandle.tempFile("prefab-test.pixprefab");
        file.writeString("{\"type\":\"pixscape-prefab\",\"version\":1,\"entities\":[{\"sourceEntityId\":11,\"transform\":{\"x\":2,\"y\":4}}]}", false, "UTF-8");
        PrefabAsset asset = loader.load(file);
        Assert.assertEquals(1, asset.entities.size());
    }

    @Test
    public void spawnRemapsJointReferencesAndCentersPrefab() {
        World world = new World(new WorldConfigurationBuilder().build());
        RuntimeConfig cfg = new RuntimeConfig();
        IdentityRegistry identityRegistry = new IdentityRegistry();
        identityRegistry.bind(world);
        PrefabSpawnService service = new PrefabSpawnService(world, new PrefabLoader(), FileHandle.tempDirectory("runtime-proj"), cfg, identityRegistry, new AtlasRuntimeService());

        PrefabAsset asset = new PrefabAsset();
        PrefabAsset.PrefabEntityData bodyA = new PrefabAsset.PrefabEntityData();
        bodyA.sourceEntityId = 1; bodyA.transform = new PrefabAsset.TransformData(); bodyA.transform.x = 0; bodyA.transform.scaleX = 1; bodyA.transform.scaleY = 1;
        PrefabAsset.PrefabEntityData bodyB = new PrefabAsset.PrefabEntityData();
        bodyB.sourceEntityId = 2; bodyB.transform = new PrefabAsset.TransformData(); bodyB.transform.x = 10; bodyB.transform.scaleX = 1; bodyB.transform.scaleY = 1;
        PrefabAsset.PrefabEntityData wheel = new PrefabAsset.PrefabEntityData();
        wheel.sourceEntityId = 3; wheel.joint = new PrefabAsset.JointBaseData(); wheel.joint.type = PhysicsJointComponent.TYPE_WHEEL; wheel.joint.aEid = 1; wheel.joint.bEid = 2; wheel.wheelJoint = new PrefabAsset.WheelJointData();
        PrefabAsset.PrefabEntityData gear = new PrefabAsset.PrefabEntityData();
        gear.sourceEntityId = 4; gear.joint = new PrefabAsset.JointBaseData(); gear.joint.type = PhysicsJointComponent.TYPE_GEAR; gear.joint.aEid = 1; gear.joint.bEid = 2; gear.gearJoint = new PrefabAsset.GearJointData(); gear.gearJoint.joint1Eid = 3; gear.gearJoint.joint2Eid = 3;
        asset.entities.add(bodyA); asset.entities.add(bodyB); asset.entities.add(wheel); asset.entities.add(gear);

        PrefabInstance instance = service.spawn(asset, 100f, 50f, -1);
        int a = instance.getEntityForLocalId(1), b = instance.getEntityForLocalId(2), w = instance.getEntityForLocalId(3), g = instance.getEntityForLocalId(4);
        Assert.assertEquals(95f, world.getMapper(TransformComponent.class).get(a).x, 0.001f);
        Assert.assertEquals(105f, world.getMapper(TransformComponent.class).get(b).x, 0.001f);
        Assert.assertEquals(a, world.getMapper(PhysicsJointComponent.class).get(w).aEid);
        Assert.assertNotNull(world.getMapper(PhysicsWheelJointComponent.class).get(w));
        Assert.assertEquals(w, world.getMapper(PhysicsGearJointComponent.class).get(g).joint1Eid);
    }

    @Test
    public void spawnVisualEntityCreatesOrientedBounds() {
        World world = new World(new WorldConfigurationBuilder().build());
        RuntimeConfig cfg = new RuntimeConfig();
        IdentityRegistry identityRegistry = new IdentityRegistry();
        identityRegistry.bind(world);
        PrefabSpawnService service = new PrefabSpawnService(world, new PrefabLoader(), FileHandle.tempDirectory("runtime-proj"), cfg, identityRegistry, new AtlasRuntimeService());

        PrefabAsset asset = new PrefabAsset();
        PrefabAsset.PrefabEntityData sprite = new PrefabAsset.PrefabEntityData();
        sprite.sourceEntityId = 7;
        sprite.transform = new PrefabAsset.TransformData();
        sprite.transform.scaleX = 1f;
        sprite.transform.scaleY = 1f;
        sprite.dimensions = new PrefabAsset.DimensionsData();
        sprite.dimensions.width = 16f;
        sprite.dimensions.height = 16f;
        sprite.textureRegion = new PrefabAsset.TextureRegionData();
        sprite.textureRegion.valid = true;
        sprite.renderMaterial = new PrefabAsset.RenderMaterialData();
        sprite.renderMaterial.textureHandle = 123;
        sprite.visibility = new PrefabAsset.VisibilityData();
        sprite.visibility.visible = true;
        asset.entities.add(sprite);

        PrefabInstance instance = service.spawn(asset, 0f, 0f, -1);
        int eid = instance.getEntityForLocalId(7);
        Assert.assertNotNull(world.getMapper(OrientedBoundsComponent.class).getSafe(eid, null));
        Assert.assertNotNull(world.getMapper(DimensionsComponent.class).getSafe(eid, null));
        Assert.assertNotNull(world.getMapper(RenderMaterialComponent.class).getSafe(eid, null));
        Assert.assertNotNull(world.getMapper(VisibilityComponent.class).getSafe(eid, null));
    }
    @Test
    public void spawnVisualEntityRegistersInRenderPipelineAfterRefresh() {
        RenderStateSOA renderState = new RenderStateSOA(64);
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64), new RenderSpriteSyncSystem(renderState))
                .build());
        RuntimeConfig cfg = new RuntimeConfig();
        IdentityRegistry identityRegistry = new IdentityRegistry();
        identityRegistry.bind(world);
        PrefabSpawnService service = new PrefabSpawnService(world, new PrefabLoader(), FileHandle.tempDirectory("runtime-proj"), cfg, identityRegistry, new AtlasRuntimeService());

        PrefabAsset asset = new PrefabAsset();
        PrefabAsset.PrefabEntityData sprite = new PrefabAsset.PrefabEntityData();
        sprite.sourceEntityId = 9;
        sprite.transform = new PrefabAsset.TransformData();
        sprite.transform.scaleX = 1f;
        sprite.transform.scaleY = 1f;
        sprite.dimensions = new PrefabAsset.DimensionsData();
        sprite.dimensions.width = 16f;
        sprite.dimensions.height = 16f;
        sprite.textureRegion = new PrefabAsset.TextureRegionData();
        sprite.textureRegion.valid = true;
        sprite.renderMaterial = new PrefabAsset.RenderMaterialData();
        sprite.renderMaterial.textureHandle = 456;
        sprite.visibility = new PrefabAsset.VisibilityData();
        sprite.visibility.visible = true;
        sprite.entityIndex = new PrefabAsset.EntityIndexData();
        sprite.entityIndex.layerIndex = 2;
        asset.entities.add(sprite);

        PrefabInstance instance = service.spawn(asset, 0f, 0f, -1);
        int eid = instance.getEntityForLocalId(9);

        Assert.assertNotNull(world.getMapper(OrientedBoundsComponent.class).getSafe(eid, null));
        Assert.assertNotNull(world.getMapper(TextureRegionComponent.class).getSafe(eid, null));
        Assert.assertNotNull(world.getMapper(RenderMaterialComponent.class).getSafe(eid, null));
        Assert.assertNotNull(world.getMapper(EntityIndexComponent.class).getSafe(eid, null));

        IntBag sub = world.getAspectSubscriptionManager().get(Aspect.all(
                OrientedBoundsComponent.class,
                RenderMaterialComponent.class,
                EntityIndexComponent.class,
                VisibilityComponent.class
        ).one(TextureRegionComponent.class)).getEntities();
        Assert.assertTrue(sub.contains(eid));

        world.process();
        Assert.assertEquals(RenderStateSOA.KIND_SPRITE, renderState.kind[eid]);
        Assert.assertTrue(renderState.enabled[eid]);
    }

}
