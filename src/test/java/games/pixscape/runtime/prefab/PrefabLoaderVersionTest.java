package games.pixscape.runtime.prefab;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class PrefabLoaderVersionTest {
    @Test
    public void currentPrefabRoundTripOmitsRemovedBodyEnabledField() throws Exception {
        PrefabAsset asset = new PrefabAsset();
        asset.name = "body";
        PrefabAsset.PrefabEntityData entity = new PrefabAsset.PrefabEntityData();
        entity.physicsBody = new PrefabAsset.PhysicsBodyData();
        asset.entities.add(entity);
        FileHandle file = new FileHandle(
                File.createTempFile("pixscape-prefab-v2", ".json"));
        PrefabLoader loader = new PrefabLoader();

        loader.save(file, asset);
        String serialized = file.readString("UTF-8");
        PrefabAsset restored = loader.load(file);

        Assert.assertEquals(PrefabLoader.PREFAB_VERSION, restored.version);
        Assert.assertNotNull(restored.entities.get(0).physicsBody);
        Assert.assertFalse(serialized.contains("\"enabled\""));
    }

    @Test
    public void previousPrefabVersionIsRejected() {
        PrefabAsset legacy = new PrefabAsset();
        legacy.version = 1;

        try {
            new PrefabLoader().validate(legacy, null);
            Assert.fail("Previous prefab versions must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "Unsupported prefab version: 1"));
        }
    }
}
