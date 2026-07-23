package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class SceneLoaderPhysicsModelV2Test {
    @Test
    public void legacyPhysicsSceneIsRejectedBeforeWorldMutation() {
        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"), "pixscape-v1-physics-scene.json"));
        file.writeString("{\"Physics" + "FixturesComponent\":{}}", false, "UTF-8");
        World world = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        int existing = world.create();

        try {
            SceneLoader.loadScene(world, file, false, new SceneMetaRuntime());
            Assert.fail("Physics Model V1 scene must be rejected.");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains("Physics Model V1"));
        }
        Assert.assertTrue(world.getEntityManager().isActive(existing));
    }
}
