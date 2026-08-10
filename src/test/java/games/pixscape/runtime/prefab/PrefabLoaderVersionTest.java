package games.pixscape.runtime.prefab;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class PrefabLoaderVersionTest {
    @Test
    public void versionTwoRoundTripPreservesPhysicsAndAnimationData() throws Exception {
        PrefabAsset asset = new PrefabAsset();
        asset.name = "body";
        PrefabAsset.PrefabEntityData entity = new PrefabAsset.PrefabEntityData();
        entity.physicsBody = new PrefabAsset.PhysicsBodyData();
        entity.physicsBody.type = 2;
        entity.physicsBody.fixedRotation = true;
        entity.physicsBody.gravityScale = 0.75f;
        entity.joint = new PrefabAsset.JointBaseData();
        entity.joint.type = 3;
        entity.joint.aEid = 4;
        entity.joint.bEid = 7;
        entity.animation = new PrefabAsset.AnimationData();
        entity.animation.animationAssetIds.add(12);
        entity.animation.animationAssetIds.add(34);
        entity.animation.currentClip = "walk";
        entity.animation.fps = 18f;
        entity.animation.playing = true;
        entity.animation.loop = false;
        entity.animation.stateTime = 1.25f;
        entity.animation.frame = 5;
        asset.entities.add(entity);
        FileHandle file = new FileHandle(
                File.createTempFile("pixscape-prefab", ".json"));
        PrefabLoader loader = new PrefabLoader();

        loader.save(file, asset);
        PrefabAsset restored = loader.load(file);

        Assert.assertEquals(2, restored.version);
        Assert.assertEquals(2, restored.entities.get(0).physicsBody.type);
        Assert.assertTrue(restored.entities.get(0).physicsBody.fixedRotation);
        Assert.assertEquals(
                0.75f, restored.entities.get(0).physicsBody.gravityScale, 0f);
        Assert.assertEquals(3, restored.entities.get(0).joint.type);
        Assert.assertEquals(4, restored.entities.get(0).joint.aEid);
        Assert.assertEquals(7, restored.entities.get(0).joint.bEid);
        PrefabAsset.AnimationData animation = restored.entities.get(0).animation;
        Assert.assertArrayEquals(new int[]{12, 34}, animation.animationAssetIds.toArray());
        Assert.assertEquals("walk", animation.currentClip);
        Assert.assertEquals(18f, animation.fps, 0f);
        Assert.assertTrue(animation.playing);
        Assert.assertFalse(animation.loop);
        Assert.assertEquals(1.25f, animation.stateTime, 0f);
        Assert.assertEquals(5, animation.frame);
    }

    @Test
    public void missingVersionIsRejected() throws Exception {
        assertLoadRejected("{\"type\":\"pixscape-prefab\",\"entities\":[]}");
    }

    @Test
    public void differentVersionIsRejected() throws Exception {
        assertLoadRejected(
                "{\"type\":\"pixscape-prefab\",\"version\":1,\"entities\":[]}");
    }

    @Test
    public void unknownFieldIsRejected() throws Exception {
        assertLoadRejected(
                "{\"type\":\"pixscape-prefab\",\"version\":2,"
                        + "\"entities\":[],\"unexpected\":true}");
    }

    private static void assertLoadRejected(String serialized) throws Exception {
        FileHandle file = new FileHandle(
                File.createTempFile("pixscape-prefab-invalid", ".json"));
        file.writeString(serialized, false, "UTF-8");
        try {
            new PrefabLoader().load(file);
            Assert.fail("Prefab must be rejected.");
        } catch (RuntimeException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }
}
