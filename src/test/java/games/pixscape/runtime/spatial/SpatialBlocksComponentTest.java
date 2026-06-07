package games.pixscape.runtime.spatial;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class SpatialBlocksComponentTest {

    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void spatialBlockDefaultsAreDeterministic() {
        SpatialBlockData block = new SpatialBlockData();

        Assert.assertEquals(0, block.id);
        Assert.assertNull(block.name);
        Assert.assertTrue(block.enabled);
        Assert.assertEquals(0f, block.x, 0.0001f);
        Assert.assertEquals(0f, block.y, 0.0001f);
        Assert.assertEquals(0f, block.width, 0.0001f);
        Assert.assertEquals(0f, block.depth, 0.0001f);
        Assert.assertEquals(0f, block.altitude, 0.0001f);
        Assert.assertEquals(SpatialBlockData.DEFAULT_HEIGHT, block.height, 0.0001f);
        Assert.assertEquals(SpatialBlockOrientation.TILE_CELL, block.orientation);
        Assert.assertTrue(block.actorOccluder);
        Assert.assertFalse(block.physicsCollision);
        Assert.assertFalse(block.lightOccluder);
        Assert.assertFalse(block.shadowCaster);
        Assert.assertFalse(block.particleOccluder);
    }

    @Test
    public void orientationEnumUsesGridTerms() {
        Assert.assertNotNull(SpatialBlockOrientation.valueOf("TILE_CELL"));
        Assert.assertNotNull(SpatialBlockOrientation.valueOf("TILE_AXIS_X"));
        Assert.assertNotNull(SpatialBlockOrientation.valueOf("TILE_AXIS_Y"));
        Assert.assertNotNull(SpatialBlockOrientation.valueOf("FREE_AXIS"));
        Assert.assertNotNull(SpatialBlockOrientation.valueOf("CUSTOM"));
    }

    @Test
    public void emptyComponentSerializesAndDeserializes() {
        World world = serializationWorld();
        int entity = world.create();
        world.getMapper(SpatialBlocksComponent.class).create(entity);
        world.process();

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, saveEntity(world, entity));
        SpatialBlocksComponent component =
                loadedWorld.getMapper(SpatialBlocksComponent.class).get(loaded.entities.get(0));

        Assert.assertNotNull(component);
        Assert.assertFalse(component.hasBlocks());
        Assert.assertEquals(0, component.blocks.size);
    }

    @Test
    public void oneBlockRoundTripsAllFieldsAndRoles() {
        SpatialBlockData source = new SpatialBlockData();
        source.id = 42;
        source.name = "north wall";
        source.enabled = false;
        source.x = 3.5f;
        source.y = 4.5f;
        source.width = 7f;
        source.depth = 2f;
        source.altitude = 11f;
        source.height = 64f;
        source.orientation = SpatialBlockOrientation.TILE_AXIS_Y;
        source.actorOccluder = false;
        source.physicsCollision = true;
        source.lightOccluder = true;
        source.shadowCaster = true;
        source.particleOccluder = true;
        source.beginAuthoredLinkedTileRefs();
        source.addLinkedTileRef(1, 2, 101);
        source.addLinkedTileRef(3, 4, 102);

        SpatialBlockData loaded = roundTripBlocks(source).blocks.get(0);

        assertBlockEquals(source, loaded);
    }

    @Test
    public void multipleBlocksRoundTripIndependently() {
        SpatialBlockData first = new SpatialBlockData();
        first.id = 1;
        first.name = "cell";
        first.x = 1f;
        first.y = 2f;
        first.width = 3f;
        first.depth = 4f;
        first.orientation = SpatialBlockOrientation.TILE_CELL;

        SpatialBlockData second = new SpatialBlockData();
        second.id = 2;
        second.name = "axis";
        second.x = 10f;
        second.y = 20f;
        second.width = 30f;
        second.depth = 40f;
        second.altitude = 5f;
        second.height = 12f;
        second.orientation = SpatialBlockOrientation.TILE_AXIS_X;
        second.actorOccluder = false;
        second.lightOccluder = true;

        SpatialBlocksComponent loaded = roundTripBlocks(first, second);

        Assert.assertEquals(2, loaded.blocks.size);
        assertBlockEquals(first, loaded.blocks.get(0));
        assertBlockEquals(second, loaded.blocks.get(1));
    }

    @Test
    public void tiledLayerCanOwnSpatialBlocksWithoutPhysicsAuthoring() {
        World world = serializationWorld();
        int entity = createTiledLayerWithSpatialBlock(world);
        world.process();

        Assert.assertTrue(world.getMapper(TiledLayerComponent.class).has(entity));
        Assert.assertTrue(world.getMapper(LayerComponent.class).has(entity));
        Assert.assertTrue(world.getMapper(SpatialBlocksComponent.class).has(entity));
        Assert.assertFalse(world.getMapper(games.pixscape.runtime.component.physics.PhysicsBodyComponent.class).has(entity));
        Assert.assertFalse(world.getMapper(games.pixscape.runtime.component.physics.PhysicsFixturesComponent.class).has(entity));
    }

    @Test
    public void layerWithoutSpatialBlocksStillLoads() {
        World world = serializationWorld();
        int entity = world.create();
        world.getMapper(TiledLayerComponent.class).create(entity);
        world.process();

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, saveEntity(world, entity));
        int loadedEntity = loaded.entities.get(0);

        Assert.assertTrue(loadedWorld.getMapper(TiledLayerComponent.class).has(loadedEntity));
        Assert.assertFalse(loadedWorld.getMapper(SpatialBlocksComponent.class).has(loadedEntity));
    }

    @Test
    public void spatialBlocksDoNotCreateBox2dFixturesOrRuntimeBodies() {
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(64), new Box2dSyncSystem(box2d))
                .build());

        int entity = createTiledLayerWithSpatialBlock(world);
        world.getMapper(TransformComponent.class).create(entity);
        world.process();

        Assert.assertEquals(0, box2d.world.getBodyCount());
        Assert.assertFalse(world.getMapper(PhysicsRuntimeBodyComponent.class).has(entity));
    }

    private static SpatialBlocksComponent roundTripBlocks(SpatialBlockData... blocks) {
        World world = serializationWorld();
        int entity = world.create();
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(entity);
        for (SpatialBlockData block : blocks) {
            component.blocks.add(block);
        }
        world.process();

        World loadedWorld = serializationWorld();
        SaveFileFormat loaded = load(loadedWorld, saveEntity(world, entity));
        return loadedWorld.getMapper(SpatialBlocksComponent.class).get(loaded.entities.get(0));
    }

    private static int createTiledLayerWithSpatialBlock(World world) {
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 3;
        world.getMapper(TiledLayerComponent.class).create(entity);

        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(entity);
        SpatialBlockData block = new SpatialBlockData();
        block.id = 7;
        block.x = 1f;
        block.y = 2f;
        block.width = 3f;
        block.depth = 4f;
        block.physicsCollision = true;
        blocks.blocks.add(block);
        return entity;
    }

    private static void assertBlockEquals(SpatialBlockData expected, SpatialBlockData actual) {
        Assert.assertEquals(expected.id, actual.id);
        Assert.assertEquals(expected.name, actual.name);
        Assert.assertEquals(expected.enabled, actual.enabled);
        Assert.assertEquals(expected.x, actual.x, 0.0001f);
        Assert.assertEquals(expected.y, actual.y, 0.0001f);
        Assert.assertEquals(expected.width, actual.width, 0.0001f);
        Assert.assertEquals(expected.depth, actual.depth, 0.0001f);
        Assert.assertEquals(expected.altitude, actual.altitude, 0.0001f);
        Assert.assertEquals(expected.height, actual.height, 0.0001f);
        Assert.assertEquals(expected.orientation, actual.orientation);
        Assert.assertEquals(expected.actorOccluder, actual.actorOccluder);
        Assert.assertEquals(expected.physicsCollision, actual.physicsCollision);
        Assert.assertEquals(expected.lightOccluder, actual.lightOccluder);
        Assert.assertEquals(expected.shadowCaster, actual.shadowCaster);
        Assert.assertEquals(expected.particleOccluder, actual.particleOccluder);
        Assert.assertEquals(expected.linkedTileRefsAuthored, actual.linkedTileRefsAuthored);
        int expectedRefs = expected.linkedTileRefs != null ? expected.linkedTileRefs.size : 0;
        int actualRefs = actual.linkedTileRefs != null ? actual.linkedTileRefs.size : 0;
        Assert.assertEquals(expectedRefs, actualRefs);
        for (int i = 0; i < expectedRefs; i++) {
            SpatialBlockData.LinkedTileRef er = expected.linkedTileRefs.get(i);
            SpatialBlockData.LinkedTileRef ar = actual.linkedTileRefs.get(i);
            Assert.assertEquals(er.gx, ar.gx);
            Assert.assertEquals(er.gy, ar.gy);
            Assert.assertEquals(er.tileId, ar.tileId);
        }
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

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
    }
}
