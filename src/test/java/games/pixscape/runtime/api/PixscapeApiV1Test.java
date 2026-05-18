package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.AtlasRuntimeService;
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

    @Test
    public void assetsRegionResolvesKnownAssetId() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, true));

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
    public void assetsRegionResolvedWithoutTextureRegionFailsClearly() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, false));

        try {
            engine.api().assets().region(42);
            Assert.fail("Expected resolved asset without TextureRegion to fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("no TextureRegion could be created"));
        }
    }

    @Test
    public void spritesSpawnCreatesRenderableEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, true));
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
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, true));

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
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, true));
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
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, true));
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
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42, true));
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

    private static final class FakeAtlasRuntimeService extends AtlasRuntimeService {
        private final int availableAssetId;
        private final boolean includeRegion;

        FakeAtlasRuntimeService(int availableAssetId, boolean includeRegion) {
            this.availableAssetId = availableAssetId;
            this.includeRegion = includeRegion;
        }

        @Override
        public CachedRegion resolveCached(int assetId, String tag) {
            if (assetId != availableAssetId) return null;
            return new CachedRegion("crate__a" + assetId, 0f, 0f, 1f, 1f, 7, 16, 24);
        }

        @Override
        public com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> resolve(int assetId, String tag) {
            com.badlogic.gdx.utils.Array<TextureAtlas.AtlasRegion> out = new com.badlogic.gdx.utils.Array<>();
            if (assetId == availableAssetId && includeRegion) {
                out.add(new TextureAtlas.AtlasRegion(new DummyTexture(), 0, 0, 16, 24));
                out.first().name = "crate__a" + assetId;
                out.first().packedWidth = 16;
                out.first().packedHeight = 24;
            }
            return out;
        }
    }

    private static final class DummyTexture extends Texture {
        DummyTexture() {
            super();
        }

        @Override
        public int getWidth() {
            return 16;
        }

        @Override
        public int getHeight() {
            return 24;
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
