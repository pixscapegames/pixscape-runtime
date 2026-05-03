package games.pixscape.runtime.prefab;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsGearJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsWheelJointComponent;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.service.IdentityRegistry;
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
        PrefabSpawnService service = new PrefabSpawnService(world, new PrefabLoader(), FileHandle.tempDirectory("runtime-proj"), cfg, identityRegistry);

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
}
