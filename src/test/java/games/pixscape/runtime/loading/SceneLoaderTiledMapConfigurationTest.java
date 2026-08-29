package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.tiled.TiledMapOwnership;
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

    @Test
    public void multipleDifferentlyConfiguredMapsSurviveInOneOrdinaryLayer() throws Exception {
        World source = world();
        FileHandle file;
        try {
            int layerEntity = source.create();
            LayerComponent layer = source.getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 0;
            layer.type = LayerComponent.TYPE_CLASSIC;
            configuredMap(source, 0, 4, TiledProjection.ISO, 64, 32);
            configuredMap(source, 0, 9, TiledProjection.ORTHO, 32, 32);
            ordinaryEntity(source, 0, 2, true, false);
            ordinaryEntity(source, 0, 7, false, true);
            source.process();
            file = save(source);
        } finally {
            source.dispose();
        }

        World target = world();
        try {
            SceneLoader.loadScene(target, file, false, new SceneMetaRuntime());
            TiledMapOwnership.validateTransitionalWorld(target);
            Assert.assertEquals(2, target.getAspectSubscriptionManager()
                    .get(Aspect.all(TiledLayerComponent.class)).getEntities().size());
            Assert.assertEquals(1, target.getAspectSubscriptionManager()
                    .get(Aspect.all(TextureRegionComponent.class)).getEntities().size());
            Assert.assertEquals(1, target.getAspectSubscriptionManager()
                    .get(Aspect.all(PointLightComponent.class)).getEntities().size());
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

    private static void configuredMap(World world, int layerIndex, int zIndex,
                                      TiledProjection projection, int tileWidth, int tileHeight) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layerIndex;
        index.zIndex = zIndex;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
        tiled.projection = projection;
        tiled.tileWidth = tileWidth;
        tiled.tileHeight = tileHeight;
        tiled.mapWidthCells = 12;
        tiled.mapHeightCells = 8;
        tiled.chunkSize = 4;
    }

    private static void ordinaryEntity(World world, int layerIndex, int zIndex,
                                       boolean sprite, boolean light) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layerIndex;
        index.zIndex = zIndex;
        if (sprite) world.getMapper(TextureRegionComponent.class).create(entity);
        if (light) world.getMapper(PointLightComponent.class).create(entity);
    }

    private static FileHandle save(World source) throws Exception {
        WorldSerializationManager serialization = source.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(source));
        SaveFileFormat format = new SaveFileFormat(
                source.getAspectSubscriptionManager().get(Aspect.all()).getEntities());
        FileHandle file = new FileHandle(File.createTempFile("pixscape-multi-map-", ".json"));
        try (OutputStream output = file.write(false)) {
            serialization.save(output, format);
        }
        return file;
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
