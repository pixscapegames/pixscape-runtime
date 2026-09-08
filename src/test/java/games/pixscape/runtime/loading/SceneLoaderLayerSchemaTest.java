package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledMapOwnership;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class SceneLoaderLayerSchemaTest {
    @Test
    public void finalSchemaThreeLayerRoundTripsWithoutType() throws Exception {
        FileHandle scene = finalSchemaScene();
        String serialized = scene.readString("UTF-8");
        JsonValue serializedLayer = serializedLayer(serialized);

        Assert.assertNotNull(serializedLayer);
        Assert.assertEquals(4, serializedLayer.getInt("layerIndex"));
        Assert.assertTrue(serializedLayer.getBoolean("spatialEnabled"));
        Assert.assertFalse(serializedLayer.has("type"));

        World loaded = world();
        try {
            SceneLoader.loadScene(loaded, scene, false, new SceneMetaRuntime());
            TiledMapOwnership.validateWorld(loaded);
            Assert.assertEquals(1, loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(LayerComponent.class)).getEntities().size());
            Assert.assertEquals(1, loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(TiledLayerComponent.class)).getEntities().size());
            Assert.assertEquals(1, loaded.getAspectSubscriptionManager()
                    .get(Aspect.all(TextureRegionComponent.class)).getEntities().size());
        } finally {
            loaded.dispose();
        }
    }

    @Test
    public void obsoleteSchemaThreeLayerTypeZeroIsRejected() throws Exception {
        assertObsoleteTypeRejected(0);
    }

    @Test
    public void obsoleteSchemaThreeLayerTypeThreeIsRejected() throws Exception {
        assertObsoleteTypeRejected(3);
    }

    private static void assertObsoleteTypeRejected(int value) throws Exception {
        FileHandle valid = finalSchemaScene();
        String stale = valid.readString("UTF-8").replace(
                "\"LayerComponent\":{\"layerIndex\":4,\"spatialEnabled\":true}",
                "\"LayerComponent\":{\"layerIndex\":4,\"type\":" + value
                        + ",\"spatialEnabled\":true}");
        Assert.assertTrue("Fixture injection failed", stale.contains("\"type\":" + value));
        FileHandle scene = new FileHandle(File.createTempFile("pixscape-stale-layer-", ".json"));
        scene.writeString(stale, false, "UTF-8");

        World loaded = world();
        try {
            RuntimeException failure = Assert.assertThrows(
                    RuntimeException.class,
                    () -> SceneLoader.loadScene(loaded, scene, false, new SceneMetaRuntime()));
            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains("obsolete schema-3 LayerComponent"));
            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains("field 'type' is unsupported"));
        } finally {
            loaded.dispose();
        }
    }

    private static FileHandle finalSchemaScene() throws Exception {
        World source = world();
        try {
            int layerEntity = source.create();
            LayerComponent layer = source.getMapper(LayerComponent.class).create(layerEntity);
            layer.layerIndex = 4;
            layer.spatialEnabled = true;

            int mapEntity = source.create();
            source.getMapper(EntityIndexComponent.class).create(mapEntity).layerIndex = 4;
            TiledLayerComponent map = source.getMapper(TiledLayerComponent.class).create(mapEntity);
            map.projection = TiledProjection.ORTHO;
            map.tileWidth = 16;
            map.tileHeight = 16;
            map.mapWidthCells = 2;
            map.mapHeightCells = 2;
            map.chunkSize = 2;

            int spriteEntity = source.create();
            source.getMapper(EntityIndexComponent.class).create(spriteEntity).layerIndex = 4;
            source.getMapper(TextureRegionComponent.class).create(spriteEntity);
            source.process();

            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(
                    source.getAspectSubscriptionManager().get(Aspect.all()).getEntities());
            FileHandle scene = new FileHandle(File.createTempFile("pixscape-final-layer-", ".json"));
            try (OutputStream output = scene.write(false)) {
                serialization.save(output, format);
            }
            return scene;
        } finally {
            source.dispose();
        }
    }

    private static JsonValue serializedLayer(String serialized) {
        JsonValue root = new JsonReader().parse(serialized);
        String identifier = root.get("componentIdentifiers")
                .get(LayerComponent.class.getName()).asString();
        JsonValue entities = root.get("entities");
        for (JsonValue entity = entities.child; entity != null; entity = entity.next) {
            JsonValue components = entity.get("components");
            if (components != null && components.has(identifier)) {
                return components.get(identifier);
            }
        }
        return null;
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
