package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class SceneLoaderTiledMapConfigurationTest {

    @Test
    public void missingMapConfigurationIsRejectedWithoutSceneMetaFallback() throws Exception {
        FileHandle file = writeScene(false);
        World target = world();
        try {
            try {
                SceneLoader.loadScene(target, file, false, new SceneMetaRuntime());
                Assert.fail("Incomplete Tiled map configuration must be rejected.");
            } catch (RuntimeException expected) {
                Assert.assertTrue(expected.getMessage(),
                        expected.getMessage().contains("invalid Tiled map configuration"));
            }
        } finally {
            target.dispose();
        }
    }

    @Test
    public void fullMapConfigurationSurvivesSceneLoad() throws Exception {
        FileHandle file = writeScene(true);
        World target = world();
        try {
            SceneLoader.loadScene(target, file, false, new SceneMetaRuntime());
            int entityId = target.getAspectSubscriptionManager()
                    .get(Aspect.all(TiledLayerComponent.class)).getEntities().get(0);
            TiledLayerComponent tiled = target.getMapper(TiledLayerComponent.class).get(entityId);
            Assert.assertEquals(TiledProjection.ISO, tiled.projection);
            Assert.assertEquals(64, tiled.tileWidth);
            Assert.assertEquals(32, tiled.tileHeight);
            Assert.assertEquals(20, tiled.mapWidthCells);
            Assert.assertEquals(12, tiled.mapHeightCells);
            Assert.assertEquals(8, tiled.chunkSize);
            Assert.assertEquals(5f, tiled.originX, 0f);
            Assert.assertEquals(-7f, tiled.originY, 0f);
        } finally {
            target.dispose();
        }
    }

    private static FileHandle writeScene(boolean complete) throws Exception {
        World source = world();
        try {
            TiledLayerComponent tiled = source.getMapper(TiledLayerComponent.class)
                    .create(source.create());
            if (complete) {
                tiled.projection = TiledProjection.ISO;
                tiled.tileWidth = 64;
                tiled.tileHeight = 32;
                tiled.mapWidthCells = 20;
                tiled.mapHeightCells = 12;
                tiled.chunkSize = 8;
                tiled.originX = 5f;
                tiled.originY = -7f;
            }
            source.process();
            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(
                    source.getAspectSubscriptionManager().get(Aspect.all()).getEntities());
            FileHandle file = new FileHandle(File.createTempFile(
                    "pixscape-tiled-map-config-", ".json"));
            try (OutputStream output = file.write(false)) {
                serialization.save(output, format);
            }
            return file;
        } finally {
            source.dispose();
        }
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
