package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class PixscapeApiV1Test {

    @Test
    public void engineApiReturnsFacadeAndDirectMethodsStillWork() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        PixscapeAPI api = engine.api();
        Assert.assertNotNull(api);
        Assert.assertSame(api, engine.api());
        Assert.assertNotNull(engine.getWorld());
        Assert.assertNotNull(engine.mapper(TransformComponent.class));
    }

    @Test
    public void entitiesApiIdentityBridgeAndDestroyWorks() throws Exception {
        ProcessCounterSystem counter = new ProcessCounterSystem();
        PixscapeEngine engine = setupEngineWithWorld(counter);
        World world = engine.getWorld();
        int e = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(e).name = "hero";
        world.getMapper(PixscapeTagComponent.class).create(e).tags.add("player");
        world.process();
        engine.getIdentityRegistry().rebuild();
        engine.getTagRegistry().rebuild();

        EntitiesAPI entities = engine.api().entities();
        int stableId = entities.ensureStableId(e);

        Assert.assertEquals(e, entities.entityIdOf(stableId));
        Assert.assertEquals(stableId, entities.stableIdOf(e));
        Assert.assertEquals(e, entities.requireStableId(stableId).entityId());
        Assert.assertEquals(e, entities.requireEntityId(e).entityId());
        Assert.assertEquals(e, entities.requireName("hero").entityId());
        Assert.assertEquals(e, entities.requireTag("player").entityId());

        entities.destroyStableId(stableId);
        Assert.assertEquals("destroy should not force a world.process()", 1, counter.processCount);
        Assert.assertTrue(world.getEntityManager().isActive(e));

        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(e));
    }

    @Test
    public void transformAndSpriteAndShaderFacadesMarkDirty() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        world.edit(e).create(TransformComponent.class);
        world.edit(e).create(DimensionsComponent.class);
        world.edit(e).create(VisibilityComponent.class);
        world.edit(e).create(AssetRefComponent.class);
        world.edit(e).create(RenderMaterialComponent.class);
        world.edit(e).create(TintComponent.class);
        world.process();

        EntityRef ref = engine.api().entities().ofEntityId(e);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        ref.transform().setPosition(10f, 20f).setRotationRad(1f).setScale(2f).setOrigin(1f, 2f);
        Assert.assertTrue((dirty.geomSub(e) & GeometryDirty.POSITION) != 0);
        Assert.assertTrue((dirty.geomSub(e) & GeometryDirty.ROTATION) != 0);
        Assert.assertTrue((dirty.geomSub(e) & GeometryDirty.SCALE) != 0);
        Assert.assertTrue((dirty.geomSub(e) & GeometryDirty.ORIGIN) != 0);

        ref.sprite().setTint(1f, 0f, 0f, 0.5f).setSize(5f, 7f).setVisible(false);
        Assert.assertTrue(dirty.isDirty(e, games.pixscape.runtime.render.DirtyBits.COLOR));

        ref.shader().setFloat("u_time", 2f);
        Assert.assertEquals(2f, ref.shader().getFloat("u_time", 0f), 0.0001f);
        Assert.assertTrue(ref.shader().hasFloat("u_time"));
        ref.shader().removeFloat("u_time");
        Assert.assertFalse(ref.shader().hasFloat("u_time"));
    }

    @Test
    public void spriteSetVisibleUpdatesVisibilityComponentWithoutDirtyMark() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        VisibilityComponent visibility = world.edit(e).create(VisibilityComponent.class);
        world.process();

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        EntityRef ref = engine.api().entities().ofEntityId(e);

        ref.sprite().setVisible(false);
        Assert.assertFalse(visibility.visible);
        Assert.assertFalse("Visibility should not require dirty bits in current runtime path",
                dirty.isDirty(e, games.pixscape.runtime.render.DirtyBits.GEOMETRY
                        | games.pixscape.runtime.render.DirtyBits.MATERIAL
                        | games.pixscape.runtime.render.DirtyBits.COLOR));
    }

    @Test
    public void spriteSetSizeUpdatesDimensionsAndSetsGeometrySizeDirty() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        DimensionsComponent dimensions = world.edit(e).create(DimensionsComponent.class);
        world.process();

        EntityRef ref = engine.api().entities().ofEntityId(e);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        ref.sprite().setSize(42f, 24f);
        Assert.assertEquals(42f, dimensions.width, 0.0001f);
        Assert.assertEquals(24f, dimensions.height, 0.0001f);
        Assert.assertTrue((dirty.geomSub(e) & GeometryDirty.SIZE) != 0);
    }

    @Test
    public void tiledMapTileEditAnimationsAndControlWork() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        TiledLayerComponent layer = world.edit(e).create(TiledLayerComponent.class);
        layer.data = new TiledMapLayerData(4, 4, 16, 16, 2);
        layer.data.initSlotRange(0, 16);
        world.process();

        engine.api().tiled().animations().put(100, new int[]{101, 102}, new int[]{100, 100});

        TiledLayerRef ref = engine.api().tiled().ofEntityId(e);
        ref.tiles().set(1, 1, 100);
        Assert.assertEquals(100, ref.tiles().get(1, 1));
        Assert.assertTrue(ref.tileAnimations().isAnimated(1, 1));

        layer.data.markAllChunksContentDirty();
        ref.map().setAtlasTag("alt");
        Assert.assertEquals(TileChunk.DirtyState.FULL, layer.data.getChunk(0, 0).dirtyState);

        layer.data.getChunk(0, 0).dirtyState = TileChunk.DirtyState.CLEAN;
        layer.data.getChunk(0, 0).dirtyLocalIndices.clear();
        layer.data.getChunk(0, 0).contentDirty = false;

        ref.map().setOrigin(3f, 4f);
        Assert.assertEquals(TileChunk.DirtyState.FULL, layer.data.getChunk(0, 0).dirtyState);

        ref.tileAnimations().pause(1, 1);
        Assert.assertTrue(ref.tileAnimations().isPaused(1, 1));
        ref.tileAnimations().play(1, 1);
        Assert.assertTrue(ref.tileAnimations().isPlaying(1, 1));

        int cx = 1 / layer.data.chunkSize;
        int cy = 1 / layer.data.chunkSize;
        TileChunk chunk = layer.data.getChunk(cx, cy);
        int local = chunk.localIndexFor(
                1 - cx * layer.data.chunkSize,
                1 - cy * layer.data.chunkSize
        );

        // Reset chunk dirty state so local dirty tracking can be observed precisely.
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        chunk.dirtyLocalIndices.clear();
        chunk.contentDirty = false;

        ref.tileAnimations().setFrame(1, 1, 1).setElapsedMs(1, 1, 50);
        ref.tileAnimations().stop(1, 1);

        Assert.assertEquals(TileAnimationPlayback.NONE, chunk.getAnimPlaybackState(local));
        Assert.assertEquals(TileChunk.DirtyState.PARTIAL, chunk.dirtyState);
        Assert.assertTrue("Stopping from a non-zero frame should dirty when visual frame changes",
                chunk.dirtyLocalIndices.contains(local));

        // Reset again to verify the no-dirty case from frame 0.
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        chunk.dirtyLocalIndices.clear();
        chunk.contentDirty = false;

        ref.tileAnimations().restart(1, 1).setFrame(1, 1, 0);
        ref.tileAnimations().stop(1, 1);

        Assert.assertEquals("Stopping from frame 0 should keep same visual frame and not dirty",
                0, chunk.dirtyLocalIndices.size);
        Assert.assertEquals(TileChunk.DirtyState.CLEAN, chunk.dirtyState);

        ref.tiles().fillRect(0, 0, 2, 2, 100).clearRect(0, 0, 1, 1);
        ref.tiles().hLine(0, 2, 3, 100).vLine(2, 0, 3, 100).markAllDirty();
        ref.map().setOrigin(3f, 4f).setVisible(false).setCollisionEnabled(false).resize(3, 3);
        Assert.assertEquals(3, ref.map().width());
    }

    @Test
    public void ecsExpertAccessAndLightFacade() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        world.edit(e).create(PointLightComponent.class);
        world.edit(e).create(ConeLightComponent.class);
        world.process();

        ECSAPI ecs = engine.api().ecs();
        Assert.assertSame(world, ecs.world());
        Assert.assertNotNull(ecs.mapper(TransformComponent.class));
        Assert.assertNotNull(ecs.system(DirtyTrackerSystem.class));
        Assert.assertNotNull(ecs.identityRegistry());
        Assert.assertNotNull(ecs.tagRegistry());

        EntityRef ref = engine.api().entities().ofEntityId(e);
        Assert.assertTrue(ref.light().hasPoint());
        Assert.assertTrue(ref.light().hasCone());
    }

    @Test
    public void shaderUseWithUnknownNameFailsWithoutMutatingFloats() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        world.edit(e).create(RenderMaterialComponent.class);
        world.edit(e).create(ShaderParamsComponent.class);
        world.process();

        EntityRef ref = engine.api().entities().ofEntityId(e);
        ref.shader().setFloat("u_time", 1f);
        Assert.assertEquals(1f, ref.shader().getFloat("u_time", 0f), 0.0001f);

        String unknown = "__missing_shader_for_test__";
        Assert.assertEquals(-1, ShaderRegistry.indexOf(unknown));
        try {
            ref.shader().use(unknown);
            Assert.fail("Expected unknown shader to fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }

        Assert.assertTrue(ref.shader().hasFloat("u_time"));
        Assert.assertEquals(1f, ref.shader().getFloat("u_time", 0f), 0.0001f);
    }

    @Test
    public void tiledAnimationsGetReturnsEphemeralReusedView() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        TiledAnimationsAPI animations = engine.api().tiled().animations();

        animations.put(100, new int[]{1, 2}, new int[]{10, 20});
        animations.put(200, new int[]{3, 4, 5}, new int[]{11, 22, 33});

        TileAnimationDefView first = animations.get(100);
        Assert.assertNotNull(first);
        Assert.assertEquals(100, first.id());
        Assert.assertEquals(2, first.frameCount());

        TileAnimationDefView second = animations.get(200);
        Assert.assertNotNull(second);
        Assert.assertSame("View is intentionally reused/ephemeral", first, second);
        Assert.assertEquals(200, first.id());
        Assert.assertEquals(3, first.frameCount());
        Assert.assertEquals(5, first.frameAssetId(2));
    }

    private static PixscapeEngine setupEngineWithWorld() throws Exception {
        return setupEngineWithWorld(null);
    }

    private static PixscapeEngine setupEngineWithWorld(ProcessCounterSystem processCounter) throws Exception {
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(dirty);
        if (processCounter != null) {
            builder.with(processCounter);
        }
        World world = new World(builder.build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);
        engine.getIdentityRegistry().bind(world);
        engine.getTagRegistry().bind(world);
        return engine;
    }

    private static void setField(PixscapeEngine engine, String fieldName, Object value) throws Exception {
        Field field = PixscapeEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private static final class ProcessCounterSystem extends BaseSystem {
        int processCount = 0;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }
}
