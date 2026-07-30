package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.animation.AnimationDefData;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.TiledAnimationSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
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
        Assert.assertNotNull(api.spatial());
        Assert.assertNotNull(engine.getWorld());
        Assert.assertNotNull(engine.mapper(TransformComponent.class));
    }

    @Test
    public void spatialEntityFacadeCreatesUpdatesAndRemovesVolume() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        world.process();

        EntityRef ref = engine.api().entities().ofEntityId(e);
        Assert.assertFalse(ref.spatial().enabled());
        Assert.assertFalse(ref.spatial().participatesInRenderOrder());

        ref.spatial().enable().setVolume(3f, 7f);

        Assert.assertTrue(ref.spatial().enabled());
        Assert.assertTrue(ref.spatial().participatesInRenderOrder());
        Assert.assertEquals(3f, ref.spatial().altitude(), 0.0001f);
        Assert.assertEquals(7f, ref.spatial().height(), 0.0001f);
        Assert.assertEquals(3f, world.getMapper(SpatialHeightComponent.class).get(e).altitude, 0.0001f);
        Assert.assertEquals(7f, world.getMapper(SpatialHeightComponent.class).get(e).height, 0.0001f);

        ref.spatial().setHeight(-10f);
        Assert.assertEquals(0f, ref.spatial().height(), 0.0001f);
        Assert.assertFalse(ref.spatial().participatesInRenderOrder());

        ref.spatial().disable();
        Assert.assertFalse(ref.spatial().enabled());
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(e));
    }

    @Test
    public void spatialApiTogglesLayerByLayerIndex() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        LayerComponent layer = world.edit(e).create(LayerComponent.class);
        layer.layerIndex = 5;
        layer.type = LayerComponent.TYPE_CLASSIC;
        world.process();

        Assert.assertFalse(engine.api().spatial().isLayerEnabled(5));

        engine.api().spatial().setLayerEnabled(5, true);
        Assert.assertTrue(layer.spatialEnabled);
        Assert.assertTrue(engine.api().spatial().isLayerEnabled(5));

        engine.api().spatial().setLayerEnabled(5, false);
        Assert.assertFalse(layer.spatialEnabled);
        Assert.assertFalse(engine.api().spatial().isLayerEnabled(5));
    }

    @Test
    public void tiledSpatialFacadeControlsDefaultsAndTileOverrides() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        int e = createTiledLayer(engine, 3, "new layer");
        World world = engine.getWorld();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(e);
        LayerComponent layer = world.getMapper(LayerComponent.class).get(e);
        TiledLayerRef ref = engine.api().tiled().layer(3);

        Assert.assertFalse(ref.spatial().enabled());
        ref.spatial().setEnabled(true).setDefaultVolume(2f, 5f);

        Assert.assertTrue(ref.spatial().enabled());
        Assert.assertTrue(layer.spatialEnabled);
        Assert.assertTrue(tiled.spatialEnabled);
        Assert.assertTrue(tiled.data.spatialEnabled);
        Assert.assertEquals(2f, ref.spatial().defaultAltitude(), 0.0001f);
        Assert.assertEquals(5f, ref.spatial().defaultHeight(), 0.0001f);
        Assert.assertEquals(2f, ref.spatial().tileAltitude(1, 1), 0.0001f);
        Assert.assertEquals(5f, ref.spatial().tileHeight(1, 1), 0.0001f);
        Assert.assertFalse(ref.spatial().hasTileOverride(1, 1));

        ref.spatial().setTileVolume(1, 1, 9f, 4f);
        Assert.assertTrue(ref.spatial().hasTileOverride(1, 1));
        Assert.assertEquals(9f, ref.spatial().tileAltitude(1, 1), 0.0001f);
        Assert.assertEquals(4f, ref.spatial().tileHeight(1, 1), 0.0001f);

        ref.spatial().clearTileOverride(1, 1);
        Assert.assertFalse(ref.spatial().hasTileOverride(1, 1));
        Assert.assertEquals(2f, ref.spatial().tileAltitude(1, 1), 0.0001f);
        Assert.assertEquals(5f, ref.spatial().tileHeight(1, 1), 0.0001f);
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
        Assert.assertFalse(entities.existsStableId(stableId));
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
    public void tiledLayerResolvesByVisualLayerIndex() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        int tiled = createTiledLayer(engine, 3, "new layer");

        TiledLayerRef ref = engine.api().tiled().layer(3);

        Assert.assertEquals(tiled, ref.entityId());
        Assert.assertTrue(ref.exists());
    }

    @Test
    public void tiledLayerResolvesByName() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        int tiled = createTiledLayer(engine, 3, "new layer");

        TiledLayerRef ref = engine.api().tiled().layer("new layer");

        Assert.assertEquals(tiled, ref.entityId());
        Assert.assertTrue(ref.exists());
    }

    @Test
    public void tiledLayerNameRejectsAmbiguousMatches() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createTiledLayer(engine, 3, "new layer");
        createTiledLayer(engine, 4, "new layer");

        try {
            engine.api().tiled().layer("new layer");
            Assert.fail("Expected ambiguous tiled layer name to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("ambiguous"));
            Assert.assertTrue(expected.getMessage().contains("layer(index)"));
        }
    }

    @Test
    public void tiledLayerIndexRejectsNonTiledLayer() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        LayerComponent layer = world.edit(e).create(LayerComponent.class);
        layer.layerIndex = 3;
        layer.type = LayerComponent.TYPE_CLASSIC;
        world.process();

        try {
            engine.api().tiled().layer(3);
            Assert.fail("Expected non-tiled layer index to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("does not designate a tiled layer"));
        }
    }

    @Test
    public void tiledLayerIndexRejectsMissingLayer() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();

        try {
            engine.api().tiled().layer(99);
            Assert.fail("Expected missing tiled layer index to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("No tiled layer exists for layer index 99"));
        }
    }

    @Test
    public void tiledLayerApiSetsStaticTileId() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createTiledLayer(engine, 3, "new layer");

        engine.api().tiled().layer(3).tiles().set(0, 0, 5);

        Assert.assertEquals(5, engine.api().tiled().layer(3).tiles().get(0, 0));
    }

    @Test
    public void tiledLayerApiSetsAnimationByName() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createTiledLayer(engine, 3, "new layer");
        engine.getAnimatedTileRegistry().put(tileAnimationData(100, "test", new int[]{101, 102}, new int[]{100, 100}));

        engine.api().tiled().layer(3).tiles().set(1, 0, "test");

        TiledLayerRef ref = engine.api().tiled().layer(3);
        Assert.assertEquals(100, ref.tiles().get(1, 0));
        Assert.assertTrue(ref.tileAnimations().isAnimated(1, 0));
        Assert.assertEquals(100, engine.api().tiled().animations().animationId("test"));
    }

    @Test
    public void tiledLayerApiRejectsUnknownAnimationName() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createTiledLayer(engine, 3, "new layer");

        try {
            engine.api().tiled().layer(3).tiles().set(1, 0, "missing");
            Assert.fail("Expected unknown tiled animation name to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Unknown tiled animation name 'missing'"));
        }
    }

    @Test
    public void tiledAnimationPlayOnceHoldsLastFrameAndCanReplay() throws Exception {
        PixscapeEngine engine = setupEngineWithTiledAnimationSystem();
        createTiledLayer(engine, 3, "doors");
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102, 103}, new int[]{100, 100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer("doors");
        layer.tiles().set(1, 0, "door_open_anim");

        layer.tileAnimations().playOnce(1, 0);
        Assert.assertTrue(layer.tileAnimations().isPlaying(1, 0));
        Assert.assertFalse(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(0, layer.tileAnimations().currentFrame(1, 0));
        Assert.assertEquals(0, layer.tileAnimations().elapsedMs(1, 0));

        engine.getWorld().setDelta(0.299f);
        engine.getWorld().process();

        Assert.assertTrue(layer.tileAnimations().isPlaying(1, 0));
        Assert.assertFalse(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(2, layer.tileAnimations().currentFrame(1, 0));
        Assert.assertEquals(99, layer.tileAnimations().elapsedMs(1, 0));

        engine.getWorld().setDelta(0.002f);
        engine.getWorld().process();

        Assert.assertFalse(layer.tileAnimations().isPlaying(1, 0));
        Assert.assertTrue(layer.tileAnimations().isPaused(1, 0));
        Assert.assertTrue(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(2, layer.tileAnimations().currentFrame(1, 0));
        Assert.assertEquals(0, layer.tileAnimations().elapsedMs(1, 0));

        layer.tileAnimations().restart(1, 0);
        Assert.assertTrue(layer.tileAnimations().isPlaying(1, 0));
        Assert.assertFalse(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(0, layer.tileAnimations().currentFrame(1, 0));

        engine.getWorld().setDelta(0.301f);
        engine.getWorld().process();

        Assert.assertTrue(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(2, layer.tileAnimations().currentFrame(1, 0));
    }

    @Test
    public void tiledAnimationPlayOnceWithoutHoldUsesStopVisualButKeepsFinishedQuery() throws Exception {
        PixscapeEngine engine = setupEngineWithTiledAnimationSystem();
        createTiledLayer(engine, 3, "doors");
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102}, new int[]{100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer("doors");
        layer.tiles().set(1, 0, "door_open_anim");
        layer.tileAnimations().playOnce(1, 0, false);

        engine.getWorld().setDelta(0.201f);
        engine.getWorld().process();

        Assert.assertFalse(layer.tileAnimations().isPlaying(1, 0));
        Assert.assertFalse(layer.tileAnimations().isPaused(1, 0));
        Assert.assertTrue(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals("Non-holding one-shots return to the same visual frame as stop().",
                0, layer.tileAnimations().currentFrame(1, 0));
    }

    @Test
    public void tiledAnimationLoopingCellsStillLoopByDefault() throws Exception {
        PixscapeEngine engine = setupEngineWithTiledAnimationSystem();
        createTiledLayer(engine, 3, "water");
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "water_loop", new int[]{101, 102}, new int[]{100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer("water");
        layer.tiles().set(1, 0, "water_loop");

        engine.getWorld().setDelta(0.25f);
        engine.getWorld().process();

        Assert.assertTrue(layer.tileAnimations().isPlaying(1, 0));
        Assert.assertFalse(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(0, layer.tileAnimations().currentFrame(1, 0));
        Assert.assertEquals(50, layer.tileAnimations().elapsedMs(1, 0));
    }

    @Test
    public void tiledAnimationPauseAndSetFrameKeepWorkingWithOneShotMode() throws Exception {
        PixscapeEngine engine = setupEngineWithTiledAnimationSystem();
        createTiledLayer(engine, 3, "doors");
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102, 103}, new int[]{100, 100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer("doors");
        layer.tiles().set(1, 0, "door_open_anim");
        layer.tileAnimations().playOnce(1, 0).setFrame(1, 0, 1).setElapsedMs(1, 0, 25).pause(1, 0);

        Assert.assertTrue(layer.tileAnimations().isPaused(1, 0));
        Assert.assertFalse(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(1, layer.tileAnimations().currentFrame(1, 0));
        Assert.assertEquals(25, layer.tileAnimations().elapsedMs(1, 0));

        engine.getWorld().setDelta(0.5f);
        engine.getWorld().process();

        Assert.assertEquals("Paused cells should not advance.",
                1, layer.tileAnimations().currentFrame(1, 0));
        Assert.assertEquals(25, layer.tileAnimations().elapsedMs(1, 0));
    }

    @Test
    public void tiledAnimationCellsUsingSameAssetHaveIndependentPlaybackState() throws Exception {
        PixscapeEngine engine = setupEngineWithTiledAnimationSystem();
        createTiledLayer(engine, 3, "doors");
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102, 103}, new int[]{100, 100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer("doors");
        layer.tiles().set(1, 0, "door_open_anim");
        layer.tiles().set(2, 0, "door_open_anim");

        layer.tileAnimations().playOnce(1, 0, true);
        layer.tileAnimations().pause(2, 0).setFrame(2, 0, 1);

        engine.getWorld().setDelta(0.301f);
        engine.getWorld().process();

        Assert.assertTrue(layer.tileAnimations().isFinished(1, 0));
        Assert.assertEquals(2, layer.tileAnimations().currentFrame(1, 0));

        Assert.assertFalse(layer.tileAnimations().isFinished(2, 0));
        Assert.assertTrue(layer.tileAnimations().isPaused(2, 0));
        Assert.assertEquals(1, layer.tileAnimations().currentFrame(2, 0));
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

    @Test
    public void assetsRegionResolvesKnownAssetId() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));

        AssetRegionRef region = engine.api().assets().region(42);

        Assert.assertEquals(42, region.assetId());
        Assert.assertNotNull(region);
        Assert.assertNotNull(region.region());
        Assert.assertEquals(16f, region.width(), 0.0001f);
        Assert.assertEquals(24f, region.height(), 0.0001f);
        Assert.assertTrue(engine.api().assets().contains(42));
        Assert.assertFalse(engine.api().assets().contains(99));
    }

    @Test
    public void assetsByIdUseOneBindingLookupAndNoGlobalRegionInspection() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        FakeAtlasRuntimeService atlas = new FakeAtlasRuntimeService(42);
        setField(engine, "atlasRuntimeService", atlas);

        for (int i = 0; i < 10000; i++) {
            Assert.assertNotNull(engine.api().assets().region(42));
        }
        Assert.assertEquals(10000, atlas.resolveBindingCalls);

        for (int i = 0; i < 10000; i++) {
            Assert.assertTrue(engine.api().assets().contains(42));
        }
        Assert.assertEquals(20000, atlas.resolveBindingCalls);

        for (int i = 0; i < 10000; i++) {
            Assert.assertFalse(engine.api().assets().contains(99));
        }
        Assert.assertEquals(30000, atlas.resolveBindingCalls);
    }

    @Test
    public void publicAssetRegionMutationCannotCorruptIndexedOrSpawnedUvs() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        FakeAtlasRuntimeService atlas = new FakeAtlasRuntimeService(42);
        setField(engine, "atlasRuntimeService", atlas);

        AssetRegionRef first = engine.api().assets().region(42);
        Assert.assertFalse(first.region() instanceof TextureAtlas.AtlasRegion);
        first.region().setRegion(0.25f, 0.25f, 0.75f, 0.75f);

        Assert.assertEquals(0f, atlas.binding.firstRegion().getU(), 0f);
        Assert.assertEquals(1f, atlas.binding.firstRegion().getU2(), 0f);
        AssetRegionRef second = engine.api().assets().region(42);
        Assert.assertEquals(0f, second.region().getU(), 0f);
        Assert.assertEquals(1f, second.region().getU2(), 0f);

        SpriteRef sprite = engine.api().sprites().spawn(42, 0f, 0f);
        TextureRegionComponent rendered = engine.getWorld()
                .getMapper(TextureRegionComponent.class)
                .get(sprite.entityId());
        Assert.assertEquals(0f, rendered.u1, 0f);
        Assert.assertEquals(1f, rendered.u2, 0f);
    }

    @Test
    public void massiveSpriteSpawnUsesOneBindingLookupPerEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        FakeAtlasRuntimeService atlas = new FakeAtlasRuntimeService(42);
        setField(engine, "atlasRuntimeService", atlas);

        for (int i = 0; i < 2000; i++) {
            engine.api().sprites().spawn(42, i, i);
        }

        Assert.assertEquals(2000, atlas.resolveBindingCalls);
    }

    @Test
    public void spritesSpawnCreatesRenderableEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        World world = engine.getWorld();

        SpriteRef ref = engine.api().sprites().spawn(42, 10f, 20f);

        Assert.assertTrue(world.getEntityManager().isActive(ref.entityId()));
        Assert.assertTrue(world.getMapper(TransformComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(DimensionsComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(AssetRefComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(TextureRegionComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(RenderMaterialComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(VisibilityComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(LayerComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(EntityIndexComponent.class).has(ref.entityId()));

        Assert.assertEquals(10f, ref.transform().x(), 0.0001f);
        Assert.assertEquals(20f, ref.transform().y(), 0.0001f);
        Assert.assertEquals(42, ref.sprite().assetId());
        Assert.assertTrue(world.getMapper(TextureRegionComponent.class).get(ref.entityId()).valid);
        Assert.assertEquals(7, world.getMapper(RenderMaterialComponent.class).get(ref.entityId()).textureHandle);
    }

    @Test
    public void spritesSpawnMissingAssetGivesClearError() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));

        try {
            engine.api().sprites().spawn(99, 0f, 0f);
            Assert.fail("Expected missing asset to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Runtime Availability"));
            Assert.assertTrue(expected.getMessage().contains("current scene atlas"));
        }
    }

    @Test
    public void spriteRefDelegatesTransformSpriteAndShader() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        SpriteRef ref = engine.api().sprites().spawn(42, 0f, 0f)
                .position(2f, 3f)
                .scale(2f)
                .rotationRad(1f)
                .tint(1f, 0f, 0f, 1f)
                .alpha(0.5f);

        Assert.assertEquals(2f, ref.transform().x(), 0.0001f);
        Assert.assertEquals(3f, ref.transform().y(), 0.0001f);
        Assert.assertEquals(2f, ref.transform().scaleX(), 0.0001f);
        Assert.assertEquals(1f, ref.transform().rotationRad(), 0.0001f);
        Assert.assertSame(ref.entity(), ref.entity());
        Assert.assertNotNull(ref.shader());
    }

    @Test
    public void highLevelRefsRemoveThroughEntityRefAndAreIdempotent() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        World world = engine.getWorld();

        SpriteRef sprite = engine.api().sprites().spawn(42, 0f, 0f);
        int spriteEntity = sprite.entityId();
        sprite.remove();
        sprite.remove();
        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(spriteEntity));

        AnimationRef animation = engine.api().animations().spawn(42, 0f, 0f);
        int animationEntity = animation.entityId();
        animation.remove();
        animation.remove();
        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(animationEntity));

        ParticleRef particle = engine.api().particles().spawn("impact", 0f, 0f);
        int particleEntity = particle.entityId();
        particle.remove();
        particle.remove();
        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(particleEntity));
    }

    @Test
    public void particlesSpawnCreatesParticleEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        ParticleRef ref = engine.api().particles().spawn("impact", 5f, 6f);
        World world = engine.getWorld();

        Assert.assertTrue(world.getMapper(TransformComponent.class).has(ref.entityId()));
        Assert.assertTrue(world.getMapper(ParticleEmitterComponent.class).has(ref.entityId()));
        ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class).get(ref.entityId());
        Assert.assertEquals("impact.p", emitter.effectPath);
        Assert.assertTrue(emitter.looping);
        Assert.assertFalse(emitter.autoRemoveWhenComplete);
        Assert.assertTrue(emitter.playRequested);

        ref.loop(false).pause().play().scale(3f);
        Assert.assertFalse(emitter.looping);
        Assert.assertEquals(3f, ref.transform().scaleX(), 0.0001f);
    }

    @Test
    public void particlesOneshotCreatesNonLoopingParticleEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        ParticleRef ref = engine.api().particles().oneshot("impact.p", 1f, 2f);
        ParticleEmitterComponent emitter = engine.getWorld().getMapper(ParticleEmitterComponent.class).get(ref.entityId());

        Assert.assertEquals("impact.p", emitter.effectPath);
        Assert.assertFalse(emitter.looping);
        Assert.assertTrue(emitter.autoRemoveWhenComplete);
        Assert.assertTrue(emitter.restartRequested);
    }

    @Test
    public void animationsSpawnCreatesAnimatedEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        AnimationRef ref = engine.api().animations().spawn(42, 7f, 8f)
                .fps(18f)
                .loop(false)
                .play();

        World world = engine.getWorld();
        Assert.assertTrue(world.getMapper(AnimationComponent.class).has(ref.entityId()));
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(ref.entityId());
        Assert.assertEquals("default", animation.currentClip);
        Assert.assertNotNull(animation.clips.get("default"));
        Assert.assertEquals(18f, animation.fps, 0.0001f);
        Assert.assertFalse(animation.loop);
        Assert.assertTrue(animation.playing);
        Assert.assertSame(ref.animation(), engine.api().animations().get(ref.entity()));
    }

    @Test
    public void animationsSpawnAssetIdUsesRegistryClips() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        engine.getAnimationRegistry().put(animationDef(42, "hero"));

        AnimationRef ref = engine.api().animations().spawn(42, 7f, 8f);

        AnimationComponent animation = engine.getWorld().getMapper(AnimationComponent.class).get(ref.entityId());
        Assert.assertEquals("idle", animation.currentClip);
        Assert.assertEquals(12f, animation.fps, 0.0001f);
        Assert.assertNotNull(animation.clips.get("attack"));
        Assert.assertEquals(4, animation.clips.get("attack").start);
        Assert.assertEquals(7, animation.clips.get("attack").end);
        Assert.assertTrue(animation.clips.get("attack").flipX);
    }

    @Test
    public void animationsSpawnNameUsesRegistryClipsAndPlaySelectsClip() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        engine.getAnimationRegistry().put(animationDef(42, "hero"));

        AnimationRef ref = engine.api().animations().spawn("hero", 7f, 8f).play("attack");

        AnimationComponent animation = engine.getWorld().getMapper(AnimationComponent.class).get(ref.entityId());
        Assert.assertEquals("attack", animation.currentClip);
        Assert.assertTrue(animation.playing);
        Assert.assertEquals(4, animation.clips.get("attack").start);
    }

    @Test
    public void animationsSpawnRegistryNameMissingFromAtlasGivesAnimationError() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(99));
        engine.getAnimationRegistry().put(animationDef(42, "hero"));

        try {
            engine.api().animations().spawn("hero", 0f, 0f);
            Assert.fail("Expected unavailable registered animation to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Animation 'hero'"));
            Assert.assertTrue(expected.getMessage().contains("Runtime Availability"));
        }
    }

    private static PixscapeEngine setupEngineWithWorld() throws Exception {
        return setupEngineWithWorld(null);
    }

    private static PixscapeEngine setupEngineWithTiledAnimationSystem() throws Exception {
        PixscapeEngine engine = new PixscapeEngine();
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        TiledAnimationSystem tiledAnimationSystem = new TiledAnimationSystem(engine.getAnimatedTileRegistry());
        tiledAnimationSystem.setAdvanceOnlyVisibleChunks(false);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, tiledAnimationSystem)
                .build());
        setField(engine, "world", world);
        engine.getIdentityRegistry().bind(world, new SceneMetaRuntime());
        engine.getTagRegistry().bind(world);
        return engine;
    }

    private static int createTiledLayer(PixscapeEngine engine, int layerIndex, String name) {
        World world = engine.getWorld();
        int e = world.create();

        PixscapeIdentityComponent identity = world.edit(e).create(PixscapeIdentityComponent.class);
        identity.name = name;

        LayerComponent layer = world.edit(e).create(LayerComponent.class);
        layer.layerIndex = layerIndex;
        layer.type = LayerComponent.TYPE_TILED;

        TiledLayerComponent tiled = world.edit(e).create(TiledLayerComponent.class);
        tiled.data = new TiledMapLayerData(4, 4, 16, 16, 2);

        world.process();
        return e;
    }

    private static TileAnimationDefData tileAnimationData(int id,
                                                          String name,
                                                          int[] frameAssetIds,
                                                          int[] frameDurationsMs) {
        TileAnimationDefData def = new TileAnimationDefData();
        def.id = id;
        def.name = name;
        def.frameAssetIds = frameAssetIds;
        def.frameDurationsMs = frameDurationsMs;
        return def;
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
        engine.getIdentityRegistry().bind(world, new SceneMetaRuntime());
        engine.getTagRegistry().bind(world);
        return engine;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Class<?> type = target.getClass();
        Field field = null;
        while (type != null && field == null) {
            try {
                field = type.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        if (field == null) throw new NoSuchFieldException(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static AnimationDefData animationDef(int assetId, String name) {
        AnimationDefData def = new AnimationDefData();
        def.assetId = assetId;
        def.name = name;
        def.fps = 12f;
        def.currentClip = "idle";
        def.frameCount = 8;
        def.clips.add(animationClip("idle", 0, 3, false));
        def.clips.add(animationClip("attack", 4, 7, true));
        return def;
    }

    private static AnimationClipDefData animationClip(String name, int start, int end, boolean flipX) {
        AnimationClipDefData clip = new AnimationClipDefData();
        clip.name = name;
        clip.start = start;
        clip.end = end;
        clip.flipX = flipX;
        return clip;
    }

    private static final class FakeAtlasRuntimeService extends AtlasRuntimeService {
        private final int availableAssetId;
        private final AtlasAssetBinding binding;
        int resolveBindingCalls;

        FakeAtlasRuntimeService(int availableAssetId) {
            this.availableAssetId = availableAssetId;
            this.binding = AtlasBindingTestFactory.single(
                    availableAssetId,
                    "crate__a" + availableAssetId,
                    0f,
                    0f,
                    1f,
                    1f,
                    7,
                    16,
                    24);
        }

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            resolveBindingCalls++;
            if (assetId != availableAssetId) return null;
            return binding;
        }
    }

    private static final class ProcessCounterSystem extends BaseSystem {
        int processCount = 0;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }
}
