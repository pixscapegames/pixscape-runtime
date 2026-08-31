package games.pixscape.runtime.spatial;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialShapesComponent;
import games.pixscape.runtime.gameobject.GameObjectRuntimeFragment;
import games.pixscape.runtime.gameobject.GameObjectRuntimeFragmentSpawner;
import games.pixscape.runtime.gameobject.SpawnResult;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class SpatialHeightPhase1SerializationTest {

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void oldSceneDataWithoutSpatialHeightStillLoads() {
        World world = serializationWorld();

        int entity = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = 12f;
        transform.y = 34f;
        world.process();

        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(entity);
        byte[] bytes = save(world, request);

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, bytes);

        Assert.assertEquals(1, loaded.entities.size());
        int loadedEntity = loaded.entities.get(0);
        Assert.assertTrue(loadedWorld.getMapper(TransformComponent.class).has(loadedEntity));
        Assert.assertFalse(loadedWorld.getMapper(SpatialHeightComponent.class).has(loadedEntity));
        Assert.assertFalse(loadedWorld.getMapper(SpatialShapesComponent.class).has(loadedEntity));
    }

    @Test
    public void spatialHeightComponentSerializesAndDeserializes() {
        World world = serializationWorld();

        int entity = world.create();
        SpatialHeightComponent spatial = world.getMapper(SpatialHeightComponent.class).create(entity);
        spatial.altitude = 8.5f;
        spatial.height = 24.25f;
        world.process();

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, saveEntity(world, entity));
        int loadedEntity = loaded.entities.get(0);

        SpatialHeightComponent loadedSpatial =
                loadedWorld.getMapper(SpatialHeightComponent.class).get(loadedEntity);
        Assert.assertEquals(8.5f, loadedSpatial.altitude, 0.0001f);
        Assert.assertEquals(24.25f, loadedSpatial.height, 0.0001f);
    }

    @Test
    public void spatialShapesComponentSerializesAndDeserializes() {
        World world = serializationWorld();

        int entity = world.create();
        SpatialShapesComponent shapes = world.getMapper(SpatialShapesComponent.class).create(entity);
        SpatialShapeData shape = new SpatialShapeData();
        shape.shapeType = SpatialShapeData.SHAPE_POLYGON;
        shape.polyVerts = new float[]{0f, 0f, 10f, 0f, 10f, 4f};
        shape.polyCount = 3;
        shape.actorOccluder = true;
        shape.lightOccluder = true;
        shape.altitude = 3f;
        shape.height = 11f;
        shapes.shapes.add(shape);
        world.process();

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, saveEntity(world, entity));
        int loadedEntity = loaded.entities.get(0);

        SpatialShapesComponent loadedShapes = loadedWorld.getMapper(SpatialShapesComponent.class).get(loadedEntity);
        Assert.assertNotNull(loadedShapes);
        Assert.assertEquals(1, loadedShapes.shapes.size);

        SpatialShapeData loadedShape = loadedShapes.shapes.get(0);
        Assert.assertEquals(SpatialShapeData.SHAPE_POLYGON, loadedShape.shapeType);
        Assert.assertEquals(3, loadedShape.polyCount);
        Assert.assertArrayEquals(shape.polyVerts, loadedShape.polyVerts, 0.0001f);
        Assert.assertTrue(loadedShape.actorOccluder);
        Assert.assertTrue(loadedShape.lightOccluder);
        Assert.assertFalse(loadedShape.particleOccluder);
        Assert.assertEquals(3f, loadedShape.altitude, 0.0001f);
        Assert.assertEquals(11f, loadedShape.height, 0.0001f);
    }

    @Test
    public void tiledCellsWithoutSpatialArraysDefaultToZero() {
        TiledMapLayerData map = new TiledMapLayerData(2, 2, 16, 16, 2);
        map.setTile(0, 0, 1);

        TileChunk chunk = map.getChunk(0, 0);
        Assert.assertNotNull(chunk);
        Assert.assertNull("Spatial arrays should be lazy until non-default data is written", chunk.altitudes);
        Assert.assertNull("Spatial arrays should be lazy until non-default data is written", chunk.heights);
        Assert.assertNull("Spatial arrays should be lazy until non-default data is written", chunk.spatialFlags);

        Assert.assertEquals(0f, map.getTileAltitude(0, 0), 0.0001f);
        Assert.assertEquals(0f, map.getTileHeight(0, 0), 0.0001f);
        Assert.assertEquals(0, map.getTileSpatialFlags(0, 0));
    }

    @Test
    public void tiledCellsWithSpatialValuesRoundTripThroughSparseComponent() {
        World world = serializationWorld();

        int entity = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entity);
        tiled.tileXs.add(1);
        tiled.tileYs.add(2);
        tiled.tileAssetIds.add(44);
        tiled.ensureSparseTileStorageConsistency();
        tiled.setSparseSpatialOverride(0, 7f, 13f, 5);
        world.process();

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, saveEntity(world, entity));
        TiledLayerComponent loadedTiled = loadedWorld.getMapper(TiledLayerComponent.class).get(loaded.entities.get(0));

        Assert.assertEquals(1, loadedTiled.tileXs.get(0));
        Assert.assertEquals(2, loadedTiled.tileYs.get(0));
        Assert.assertEquals(44, loadedTiled.tileAssetIds.get(0));
        Assert.assertEquals(7f, loadedTiled.sparseTileAltitude(0), 0.0001f);
        Assert.assertEquals(13f, loadedTiled.sparseTileHeight(0), 0.0001f);
        Assert.assertEquals(5, loadedTiled.sparseTileSpatialFlags(0));
        Assert.assertTrue(loadedTiled.hasSparseSpatialOverride(0));

        TiledMapLayerData map = new TiledMapLayerData(4, 4, 16, 16, 2);
        map.setTile(1, 2, loadedTiled.tileAssetIds.get(0), loadedTiled.tileTransformFlags.get(0));
        map.setTileSpatial(1, 2,
                loadedTiled.sparseTileAltitude(0),
                loadedTiled.sparseTileHeight(0),
                loadedTiled.sparseTileSpatialFlags(0));

        Assert.assertEquals(7f, map.getTileAltitude(1, 2), 0.0001f);
        Assert.assertEquals(13f, map.getTileHeight(1, 2), 0.0001f);
        Assert.assertEquals(5, map.getTileSpatialFlags(1, 2));
    }

    @Test
    public void allocatedZeroSparseSpatialArraysDoNotCountAsAuthoredOverride() {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.tileXs.add(1);
        tiled.tileYs.add(2);
        tiled.tileAssetIds.add(44);
        tiled.ensureSparseTileStorageConsistency();
        tiled.ensureSparseSpatialStorage();

        Assert.assertFalse(tiled.hasSparseSpatialOverride(0));
    }

    @Test
    public void sparseSpatialValuesWithoutExplicitOverrideDoNotCountAsAuthoredOverride() {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.tileXs.add(1);
        tiled.tileYs.add(2);
        tiled.tileAssetIds.add(44);
        tiled.ensureSparseTileStorageConsistency();
        tiled.tileAltitudes = new float[]{7f};
        tiled.tileHeights = new float[]{13f};
        tiled.tileSpatialFlags = new com.badlogic.gdx.utils.IntArray();
        tiled.tileSpatialFlags.add(5);

        Assert.assertFalse(tiled.hasSparseSpatialOverride(0));
    }

    @Test
    public void explicitZeroSparseSpatialOverrideSuppressesDefaultHeight() {
        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.tileXs.add(1);
        tiled.tileYs.add(2);
        tiled.tileAssetIds.add(44);
        tiled.ensureSparseTileStorageConsistency();
        tiled.setSparseSpatialOverride(0, 0f, 0f, 0);

        Assert.assertTrue(tiled.hasSparseSpatialOverride(0));

        TiledMapLayerData map = new TiledMapLayerData(4, 4, 16, 16, 2);
        map.defaultTileHeight = 12f;
        map.setTile(1, 2, tiled.tileAssetIds.get(0), tiled.tileTransformFlags.get(0));
        if (tiled.hasSparseSpatialOverride(0)) {
            map.setTileSpatialOverride(
                    1,
                    2,
                    tiled.sparseTileAltitude(0),
                    tiled.sparseTileHeight(0),
                    tiled.sparseTileSpatialFlags(0)
            );
        }

        Assert.assertEquals(0f, map.getTileHeight(1, 2), 0.0001f);
    }

    @Test
    public void gameObjectFragmentPreservesSpatialComponents() {
        World targetWorld = runtimeWorld();

        int entity = targetWorld.create();
        targetWorld.getMapper(TransformComponent.class).create(entity);
        SpatialHeightComponent spatial = targetWorld.getMapper(SpatialHeightComponent.class).create(entity);
        spatial.altitude = 2f;
        spatial.height = 9f;

        SpatialShapesComponent shapes = targetWorld.getMapper(SpatialShapesComponent.class).create(entity);
        SpatialShapeData shape = new SpatialShapeData();
        shape.shapeType = SpatialShapeData.SHAPE_BOX;
        shape.halfW = 3f;
        shape.halfH = 4f;
        shape.collisionEnabled = true;
        shape.actorOccluder = true;
        shape.altitude = 2f;
        shape.height = 9f;
        shapes.shapes.add(shape);
        targetWorld.process();

        GameObjectRuntimeFragment request = new GameObjectRuntimeFragment();
        request.entities.add(entity);
        byte[] fragmentBytes = save(targetWorld, request);
        GameObjectRuntimeFragment fragment =
                loadRuntimeFragment(targetWorld, fragmentBytes);

        GameObjectRuntimeFragmentSpawner spawner = new GameObjectRuntimeFragmentSpawner(
                new IdentityRegistry(),
                new games.pixscape.runtime.loading.SceneMetaRuntime(),
                new AtlasRuntimeService());
        SpawnResult result = spawner.spawn(targetWorld, fragment, 0f, 0f);

        IntBag created = result.createdEntityIds();
        Assert.assertEquals(1, created.size());
        int spawned = created.get(0);
        Assert.assertNotEquals(entity, spawned);

        SpatialHeightComponent spawnedSpatial =
                targetWorld.getMapper(SpatialHeightComponent.class).get(spawned);
        Assert.assertEquals(2f, spawnedSpatial.altitude, 0.0001f);
        Assert.assertEquals(9f, spawnedSpatial.height, 0.0001f);

        SpatialShapesComponent spawnedShapes =
                targetWorld.getMapper(SpatialShapesComponent.class).get(spawned);
        Assert.assertEquals(1, spawnedShapes.shapes.size);
        Assert.assertTrue(spawnedShapes.shapes.get(0).collisionEnabled);
        Assert.assertTrue(spawnedShapes.shapes.get(0).actorOccluder);
        Assert.assertEquals(3f, spawnedShapes.shapes.get(0).halfW, 0.0001f);
        Assert.assertEquals(4f, spawnedShapes.shapes.get(0).halfH, 0.0001f);
    }

    private static byte[] saveEntity(World world, int entity) {
        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(entity);
        return save(world, request);
    }

    private static byte[] save(World world, SaveFileFormat request) {
        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(world));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wsm.save(out, request);
        return out.toByteArray();
    }

    private static SaveFileFormat load(World world, byte[] bytes) {
        WorldSerializationManager wsm = world.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat loaded = wsm.load(new ByteArrayInputStream(bytes), SaveFileFormat.class);
        world.process();
        return loaded;
    }

    private static GameObjectRuntimeFragment loadRuntimeFragment(
            World world, byte[] bytes) {
        WorldSerializationManager wsm =
                world.getSystem(WorldSerializationManager.class);
        wsm.setSerializer(new JsonArtemisSerializer(world));
        return wsm.load(
                new ByteArrayInputStream(bytes),
                GameObjectRuntimeFragment.class);
    }

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
    }

    private static World runtimeWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(64))
                .build());
    }
}
