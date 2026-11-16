package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.tiled.TiledMapOwnership;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class SceneLoaderTiledMapConfigurationTest {

    @Test
    public void schemaThreeWrapsEachGridWithoutMovingCellsOrChangingExistingIdentity() throws Exception {
        World source = world();
        World target = world();
        try {
            source.getMapper(LayerComponent.class).create(source.create()).layerIndex = 0;
            configuredMap(source, 0, 7, TiledProjection.ISO, 64, 32);
            configuredMap(source, 0, 8, TiledProjection.ISO, 64, 32);
            source.process();
            int oldEntity = source.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities().get(0);
            varIdentity(source, oldEntity, 10);
            TiledLayerComponent tiles = source.getMapper(TiledLayerComponent.class).get(oldEntity);
            tiles.originX = 17f;
            tiles.originY = -23f;
            tiles.defaultTileAltitude = 48f;
            tiles.tileXs.add(2);
            tiles.tileYs.add(3);
            tiles.tileAssetIds.add(9);
            tiles.tileTransformFlags.add((byte) 1);
            games.pixscape.runtime.spatial.SpatialBlockData block = new games.pixscape.runtime.spatial.SpatialBlockData();
            block.id = block.structureId = 1;
            block.width = block.depth = 1;
            block.altitude = 48;
            block.beginAuthoredLinkedTileRefs();
            block.addLinkedTileRef(2, 3, 9);
            varBlocks(source, oldEntity, block);
            source.process();
            SceneMetaRuntime metadata = new SceneMetaRuntime();
            metadata.sceneSchemaVersion = 3;
            metadata.nextEntityStableId = 20;
            SceneLoader.loadScene(target, save(source), false, metadata);
            target.process();
            Assert.assertEquals(5, metadata.sceneSchemaVersion);
            Assert.assertEquals(2, target.getAspectSubscriptionManager().get(
                    Aspect.all(games.pixscape.runtime.component.TiledMapComponent.class)).getEntities().size());
            int restored = target.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class)).getEntities().get(0);
            TiledLayerComponent loaded = target.getMapper(TiledLayerComponent.class).get(restored);
            Assert.assertEquals(10, target.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class).get(restored).stableId);
            Assert.assertEquals(17f, loaded.createLayerData().originX, 0f);
            Assert.assertEquals(-23f, loaded.createLayerData().originY, 0f);
            Assert.assertEquals(48f, loaded.defaultTileAltitude, 0f);
            Assert.assertEquals(2, loaded.tileXs.get(0));
            Assert.assertEquals(3, loaded.tileYs.get(0));
            Assert.assertEquals(9, loaded.tileAssetIds.get(0));
            Assert.assertEquals(1, target.getMapper(games.pixscape.runtime.component.spatial.SpatialBlocksComponent.class)
                    .get(restored).blocks.first().linkedTileRefs.size);
            Assert.assertTrue(metadata.nextEntityStableId > 20);
        } finally {
            source.dispose();
            target.dispose();
        }
    }

    private static void varIdentity(World world, int entity, int stableId) {
        world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class).create(entity).stableId = stableId;
    }

    private static void varBlocks(World world, int entity, games.pixscape.runtime.spatial.SpatialBlockData block) {
        games.pixscape.runtime.component.spatial.SpatialBlocksComponent blocks =
                world.getMapper(games.pixscape.runtime.component.spatial.SpatialBlocksComponent.class).create(entity);
        blocks.blocks.add(block);
        blocks.nextSpatialBlockId = 2;
    }

    @Test
    public void obsoleteIndependentLightingPlaneIsRejectedBeforeClearingWorld() throws Exception {
        FileHandle file = writeScene(true);
        String serialized = file.readString("UTF-8");
        serialized = serialized.replace("\"TiledLayerComponent\": {", "\"TiledLayerComponent\": {\"lightingPlaneAltitude\":128,");
        if (!serialized.contains("lightingPlaneAltitude"))
            serialized = serialized.replace("\"TiledLayerComponent\":{", "\"TiledLayerComponent\":{\"lightingPlaneAltitude\":128,");
        Assert.assertTrue(serialized, serialized.contains("lightingPlaneAltitude"));
        file.writeString(serialized, false, "UTF-8");
        World target = world();
        int existing = target.create();
        target.process();
        try {
            try {
                SceneLoader.loadScene(target, file, true, new SceneMetaRuntime());
                Assert.fail();
            } catch (RuntimeException expected) {
                Assert.assertTrue(expected.toString(), expected.toString().contains("lightingPlaneAltitude"));
            }
            Assert.assertTrue(target.getEntityManager().isActive(existing));
        } finally {
            target.dispose();
        }
    }

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
            TiledMapOwnership.validateWorld(target);
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
