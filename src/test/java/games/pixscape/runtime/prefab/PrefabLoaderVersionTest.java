package games.pixscape.runtime.prefab;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class PrefabLoaderVersionTest {
    @Test
    public void newAssetDefaultsToVersionThree() {
        Assert.assertEquals(3, new PrefabAsset().version);
    }

    @Test
    public void versionThreeRoundTripPreservesPhysicsAndAnimationData() throws Exception {
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

        Assert.assertEquals(3, restored.version);
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
    public void versionThreeRoundTripPreservesAllQuadValues() throws Exception {
        PrefabAsset asset = new PrefabAsset();
        PrefabAsset.PrefabEntityData entity = new PrefabAsset.PrefabEntityData();
        entity.quadDeform = quad(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
        asset.entities.add(entity);
        FileHandle file = tempFile("pixscape-prefab-quad");
        PrefabLoader loader = new PrefabLoader();

        loader.save(file, asset);
        PrefabAsset.QuadDeformData restored =
                loader.load(file).entities.get(0).quadDeform;

        assertQuad(restored, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f);
    }

    @Test
    public void versionThreeRoundTripKeepsAbsentQuadNull() throws Exception {
        PrefabAsset asset = new PrefabAsset();
        asset.entities.add(new PrefabAsset.PrefabEntityData());
        FileHandle file = tempFile("pixscape-prefab-no-quad");
        PrefabLoader loader = new PrefabLoader();

        loader.save(file, asset);

        Assert.assertNull(loader.load(file).entities.get(0).quadDeform);
    }

    @Test
    public void validVersionTwoLoadsAndMigratesInMemoryWithoutQuad() throws Exception {
        FileHandle file = tempFile("pixscape-prefab-v2");
        file.writeString(
                "{\"type\":\"pixscape-prefab\",\"version\":2,"
                        + "\"name\":\"legacy\",\"entities\":[{\"sourceEntityId\":9}]}",
                false,
                "UTF-8");

        PrefabLoader loader = new PrefabLoader();
        PrefabAsset restored = loader.load(file);

        Assert.assertEquals(PrefabAsset.PREFAB_VERSION, restored.version);
        Assert.assertEquals(9, restored.entities.get(0).sourceEntityId);
        Assert.assertNull(restored.entities.get(0).quadDeform);

        FileHandle migratedFile = tempFile("pixscape-prefab-v2-migrated");
        loader.save(migratedFile, restored);
        Assert.assertEquals(
                PrefabAsset.PREFAB_VERSION,
                new JsonReader().parse(migratedFile.readString("UTF-8"))
                        .getInt("version"));
    }

    @Test
    public void missingVersionIsRejected() throws Exception {
        assertLoadRejected("{\"type\":\"pixscape-prefab\",\"entities\":[]}");
    }

    @Test
    public void versionOneIsRejected() throws Exception {
        assertLoadRejected(
                "{\"type\":\"pixscape-prefab\",\"version\":1,\"entities\":[]}");
    }

    @Test
    public void unsupportedFutureVersionIsRejected() throws Exception {
        assertLoadRejected(
                "{\"type\":\"pixscape-prefab\",\"version\":4,\"entities\":[]}");
    }

    @Test
    public void malformedTypeIsRejected() throws Exception {
        assertLoadRejected(
                "{\"type\":\"other\",\"version\":3,\"entities\":[]}");
    }

    @Test
    public void unknownFieldIsRejected() throws Exception {
        assertLoadRejected(
                "{\"type\":\"pixscape-prefab\",\"version\":3,"
                        + "\"entities\":[],\"unexpected\":true}");
    }

    private static PrefabAsset.QuadDeformData quad(
            float blX, float blY,
            float brX, float brY,
            float trX, float trY,
            float tlX, float tlY) {
        PrefabAsset.QuadDeformData quad = new PrefabAsset.QuadDeformData();
        quad.blX = blX;
        quad.blY = blY;
        quad.brX = brX;
        quad.brY = brY;
        quad.trX = trX;
        quad.trY = trY;
        quad.tlX = tlX;
        quad.tlY = tlY;
        return quad;
    }

    private static void assertQuad(PrefabAsset.QuadDeformData quad,
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

    private static FileHandle tempFile(String prefix) throws Exception {
        return new FileHandle(File.createTempFile(prefix, ".json"));
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
