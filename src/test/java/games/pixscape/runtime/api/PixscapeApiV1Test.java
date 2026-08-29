package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.animation.AnimationClipDef;
import games.pixscape.runtime.animation.AnimationClipDefData;
import games.pixscape.runtime.animation.AnimationDefData;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleRuntimeAvailability;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasBindingTestFactory;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.DirtyFlushSystem;
import games.pixscape.runtime.system.AnimationSystem;
import games.pixscape.runtime.system.RenderSpriteSyncSystem;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.runtime.system.SpatialRenderOrderSystem;
import games.pixscape.runtime.system.TiledAnimationSystem;
import games.pixscape.runtime.system.UpdateWorldGeometrySystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PixscapeApiV1Test {
    private GL20 previousGl;
    private Graphics previousGraphics;

    @BeforeClass
    public static void loadGdxNatives() {
        GdxNativesLoader.load();
    }

    @Before
    public void installGlProxy() {
        previousGl = Gdx.gl;
        previousGraphics = Gdx.graphics;
        Gdx.gl = (GL20) Proxy.newProxyInstance(
                GL20.class.getClassLoader(),
                new Class[]{GL20.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    @After
    public void restoreGlProxy() {
        Gdx.gl = previousGl;
        Gdx.graphics = previousGraphics;
    }

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
        Assert.assertFalse(ref.spatial().participatesInRenderOrder());
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
    public void spatialParticipationUsesEffectiveRuntimeEligibility() throws Exception {
        DynamicEntityRenderState state = new DynamicEntityRenderState(8);
        SpatialRenderOrderSystem spatialSystem = new SpatialRenderOrderSystem(state, new DrawList(8));
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        World world = new World(new WorldConfigurationBuilder().with(dirty, spatialSystem).build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);

        int layerEntity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(layerEntity);
        layer.layerIndex = 4;
        layer.type = LayerComponent.TYPE_CLASSIC;
        layer.spatialEnabled = true;

        int actor = world.create();
        world.getMapper(TransformComponent.class).create(actor);
        world.getMapper(EntityIndexComponent.class).create(actor).layerIndex = 4;
        world.getMapper(SpatialHeightComponent.class).create(actor).height = 2f;
        int slot = state.acquireSlotForEntity(actor);
        state.kind[slot] = RenderKind.SPRITE;
        state.enabled[slot] = true;
        state.visible[slot] = true;
        state.textureHandle[slot] = 1;
        state.layerIndex[slot] = 4;
        world.process();

        SpatialEntityFacade facade = engine.api().entities().ofEntityId(actor).spatial();
        Assert.assertFalse("Positive height without a footprint is ineligible",
                facade.participatesInRenderOrder());

        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).create(actor);
        footprint.valid = true;
        footprint.radiusPx = 3f;
        layer.spatialEnabled = false;
        Assert.assertFalse("A valid footprint on a non-Spatial layer is ineligible",
                facade.participatesInRenderOrder());

        layer.spatialEnabled = true;
        Assert.assertTrue("A renderable sprite with volume, footprint, and Spatial layer participates",
                facade.participatesInRenderOrder());

        state.visible[slot] = false;
        Assert.assertFalse("A renderer-hidden actor is ineligible",
                facade.participatesInRenderOrder());
        state.visible[slot] = true;

        layer.type = LayerComponent.TYPE_TILED;
        Assert.assertFalse("A Tiled layer does not enable ECS actor participation",
                facade.participatesInRenderOrder());
        layer.type = LayerComponent.TYPE_CLASSIC;
        Assert.assertTrue("Restoring the current layer contract takes effect immediately",
                facade.participatesInRenderOrder());

        footprint.valid = false;
        Assert.assertFalse("Invalidating the footprint takes effect immediately",
                facade.participatesInRenderOrder());
        footprint.valid = true;
        Assert.assertTrue("Restoring the footprint takes effect immediately",
                facade.participatesInRenderOrder());

        facade.setHeight(0f);
        Assert.assertFalse("Zero height is ineligible", facade.participatesInRenderOrder());
        facade.setHeight(2f);
        Assert.assertTrue("Restoring positive height takes effect immediately",
                facade.participatesInRenderOrder());

        world.delete(actor);
        world.process();
        state.releaseSlotForEntity(actor);
        int replacement = world.create();
        Assert.assertEquals("The regression must exercise Artemis ID reuse", actor, replacement);
        world.getMapper(TransformComponent.class).create(replacement);
        world.getMapper(EntityIndexComponent.class).create(replacement).layerIndex = 4;
        world.getMapper(SpatialHeightComponent.class).create(replacement).height = 2f;
        SpatialPhysicsFootprintComponent replacementFootprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).create(replacement);
        replacementFootprint.valid = true;
        replacementFootprint.radiusPx = 3f;
        int replacementSlot = state.acquireSlotForEntity(replacement);
        state.kind[replacementSlot] = RenderKind.SPRITE;
        state.enabled[replacementSlot] = true;
        state.visible[replacementSlot] = true;
        state.textureHandle[replacementSlot] = 1;
        state.layerIndex[replacementSlot] = 4;

        Assert.assertFalse("A stale facade must not resolve a recycled entity ID",
                facade.participatesInRenderOrder());
        Assert.assertTrue("A fresh facade may resolve the eligible replacement",
                engine.api().entities().ofEntityId(replacement).spatial().participatesInRenderOrder());
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
    public void renderOrderApiSurfaceIsIndexOnly() throws Exception {
        Assert.assertEquals(RenderOrderFacade.class,
                EntityRef.class.getMethod("renderOrder").getReturnType());
        Assert.assertEquals(RenderOrderFacade.class,
                RenderOrderFacade.class.getMethod("layerIndex", int.class).getReturnType());
        Assert.assertEquals(RenderOrderFacade.class,
                RenderOrderFacade.class.getMethod("zIndex", int.class).getReturnType());
        Assert.assertEquals(RenderOrderFacade.class,
                RenderOrderFacade.class.getMethod("set", int.class, int.class).getReturnType());

        Method[] methods = RenderOrderFacade.class.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            Class<?>[] parameters = method.getParameterTypes();
            for (int p = 0; p < parameters.length; p++) {
                Assert.assertNotEquals("RenderOrderFacade must not expose layer-name parameters",
                        String.class, parameters[p]);
            }
        }
    }

    @Test
    public void tiledApiSurfaceIsIndexEntityAndStableIdOnly() throws Exception {
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("ofEntityId", int.class).getReturnType());
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("ofStableId", int.class).getReturnType());
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("ofLayerIndex", int.class).getReturnType());
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("layer", int.class).getReturnType());
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("requireEntityId", int.class).getReturnType());
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("requireStableId", int.class).getReturnType());
        Assert.assertEquals(TiledLayerRef.class,
                TiledAPI.class.getMethod("requireLayerIndex", int.class).getReturnType());
        Assert.assertEquals(TiledAnimationsAPI.class,
                TiledAPI.class.getMethod("animations").getReturnType());
        Assert.assertEquals(8, TiledAPI.class.getDeclaredMethods().length);

        Method[] methods = TiledAPI.class.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Class<?>[] parameters = methods[i].getParameterTypes();
            for (int p = 0; p < parameters.length; p++) {
                Assert.assertNotEquals("TiledAPI must not expose layer-name parameters",
                        String.class, parameters[p]);
            }
        }
    }

    @Test
    public void spatialApiIgnoresRenderedActorLayerMetadata() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int sceneLayerEntity = world.create();
        LayerComponent sceneLayer = world.edit(sceneLayerEntity).create(LayerComponent.class);
        sceneLayer.layerIndex = 5;

        int actorEntity = world.create();
        LayerComponent actorLayer = world.edit(actorEntity).create(LayerComponent.class);
        actorLayer.layerIndex = 5;
        actorLayer.spatialEnabled = true;
        world.edit(actorEntity).create(EntityIndexComponent.class).layerIndex = 5;
        world.process();

        Assert.assertFalse(engine.api().spatial().isLayerEnabled(5));
    }

    @Test
    public void spatialApiSetLayerEnabledChangesOnlyAuthoredLayerEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int sceneLayerEntity = world.create();
        LayerComponent sceneLayer = world.edit(sceneLayerEntity).create(LayerComponent.class);
        sceneLayer.layerIndex = 5;

        int actorEntity = world.create();
        LayerComponent actorLayer = world.edit(actorEntity).create(LayerComponent.class);
        actorLayer.layerIndex = 5;
        world.edit(actorEntity).create(EntityIndexComponent.class).layerIndex = 5;
        world.process();

        engine.api().spatial().setLayerEnabled(5, true);

        Assert.assertTrue(sceneLayer.spatialEnabled);
        Assert.assertFalse(actorLayer.spatialEnabled);
    }

    @Test
    public void movingActorDoesNotActivateTargetSpatialLayerFromActorMetadata() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int targetLayerEntity = world.create();
        LayerComponent targetLayer = world.edit(targetLayerEntity).create(LayerComponent.class);
        targetLayer.layerIndex = 4;

        int actorEntity = world.create();
        LayerComponent actorLayer = world.edit(actorEntity).create(LayerComponent.class);
        actorLayer.layerIndex = 0;
        actorLayer.spatialEnabled = true;
        world.edit(actorEntity).create(EntityIndexComponent.class).layerIndex = 0;
        world.process();

        engine.api().entities().ofEntityId(actorEntity).renderOrder().layerIndex(4);

        Assert.assertFalse(engine.api().spatial().isLayerEnabled(4));
        Assert.assertFalse(targetLayer.spatialEnabled);
        Assert.assertTrue(actorLayer.spatialEnabled);
    }

    @Test
    public void tiledSpatialFacadeControlsDefaultsAndTileOverrides() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        int e = createTiledLayer(engine, 3);
        World world = engine.getWorld();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(e);
        SceneLayerResolver resolver = new SceneLayerResolver();
        resolver.bind(world);
        LayerComponent layer = world.getMapper(LayerComponent.class)
                .get(resolver.findLayerEntityId(3));
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
        world.edit(e).create(EntityIndexComponent.class);
        world.edit(e).create(LayerComponent.class);
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
    public void transformRejectsNonFiniteGeometryWithoutMutationOrDirtyState() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = world.create();
        TransformComponent transform = world.edit(entity).create(TransformComponent.class);
        transform.x = 1f;
        transform.y = 2f;
        transform.rotationRad = 0.5f;
        transform.scaleX = 3f;
        transform.scaleY = 4f;
        transform.originX = 5f;
        transform.originY = 6f;
        world.process();
        TransformFacade facade = engine.api().entities().ofEntityId(entity).transform();
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        dirty.clearAll();

        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setPosition(Float.NaN, 9f); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setScale(Float.POSITIVE_INFINITY); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setOrigin(9f, Float.NEGATIVE_INFINITY); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setRotationRad(Float.NaN); }
        });

        Assert.assertEquals(1f, transform.x, 0f);
        Assert.assertEquals(2f, transform.y, 0f);
        Assert.assertEquals(0.5f, transform.rotationRad, 0f);
        Assert.assertEquals(3f, transform.scaleX, 0f);
        Assert.assertEquals(4f, transform.scaleY, 0f);
        Assert.assertEquals(5f, transform.originX, 0f);
        Assert.assertEquals(6f, transform.originY, 0f);
        Assert.assertFalse(dirty.isDirty(
                entity, games.pixscape.runtime.render.DirtyBits.GEOMETRY));

        facade.setScale(-2f, -3f);
        Assert.assertEquals(-2f, transform.scaleX, 0f);
        Assert.assertEquals(-3f, transform.scaleY, 0f);
    }

    @Test
    public void spriteSizeRejectsNonFiniteValuesWithoutMutationOrDirtyState() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        World world = engine.getWorld();
        SpriteRef sprite = engine.api().sprites().spawn(42, 0f, 0f);
        int entity = sprite.entityId();
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        dirty.clearAll();

        assertIllegalArgument(new Runnable() {
            @Override public void run() { sprite.sprite().setSize(Float.NaN, 10f); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { sprite.sprite().setSize(10f, Float.POSITIVE_INFINITY); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { sprite.sprite().setSize(Float.NEGATIVE_INFINITY, 10f); }
        });

        Assert.assertEquals(16f, dimensions.width, 0f);
        Assert.assertEquals(24f, dimensions.height, 0f);
        Assert.assertFalse(dirty.isDirty(
                entity, games.pixscape.runtime.render.DirtyBits.GEOMETRY));
    }

    @Test
    public void spatialHeightRejectsNonFiniteValuesAndPreservesFiniteClamping() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = world.create();
        SpatialHeightComponent spatial = world.edit(entity).create(SpatialHeightComponent.class);
        spatial.altitude = 2f;
        spatial.height = 5f;
        world.process();
        SpatialEntityFacade facade = engine.api().entities().ofEntityId(entity).spatial();
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        dirty.clearAll();

        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setHeight(Float.NaN); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setHeight(Float.POSITIVE_INFINITY); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setHeight(Float.NEGATIVE_INFINITY); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setVolume(Float.NaN, 3f); }
        });

        Assert.assertEquals(2f, spatial.altitude, 0f);
        Assert.assertEquals(5f, spatial.height, 0f);
        Assert.assertFalse(dirty.isDirty(entity,
                games.pixscape.runtime.render.DirtyBits.COARSE_MASK));

        facade.setHeight(-4f);
        Assert.assertEquals(0f, spatial.height, 0f);
    }

    @Test
    public void spriteFacadeDoesNotCompleteArbitraryOrPartialEntities() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int ordinary = world.create();
        int partial = world.create();
        VisibilityComponent visibility = world.edit(partial).create(VisibilityComponent.class);
        visibility.visible = true;
        TintComponent tint = world.edit(partial).create(TintComponent.class);
        int originalTint = tint.rgba;
        world.process();

        SpriteFacade ordinarySprite = engine.api().entities().ofEntityId(ordinary).sprite();
        Assert.assertFalse(ordinarySprite.exists());
        ordinarySprite.setVisible(false)
                .setTint(1f, 0f, 0f, 1f)
                .setSize(42f, 24f)
                .setRepeat(true, true)
                .setAsset(42, "main");

        Assert.assertFalse(world.getMapper(VisibilityComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(TintComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(DimensionsComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(AssetRefComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(TextureRegionComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(RenderMaterialComponent.class).has(ordinary));

        SpriteFacade partialSprite = engine.api().entities().ofEntityId(partial).sprite();
        Assert.assertFalse(partialSprite.exists());
        partialSprite.setVisible(false)
                .setTint(1f, 0f, 0f, 1f)
                .setSize(42f, 24f)
                .setRepeat(true, true)
                .setAssetId(42);

        Assert.assertTrue(visibility.visible);
        Assert.assertEquals(originalTint, tint.rgba);
        Assert.assertFalse(world.getMapper(DimensionsComponent.class).has(partial));
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(partial));
        Assert.assertFalse(world.getMapper(AssetRefComponent.class).has(partial));
        Assert.assertFalse(world.getMapper(TextureRegionComponent.class).has(partial));
        Assert.assertFalse(world.getMapper(RenderMaterialComponent.class).has(partial));
    }

    @Test
    public void validSpriteSettersStillMutateAndMarkDirty() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        World world = engine.getWorld();
        SpriteRef sprite = engine.api().sprites().spawn(42, 0f, 0f);
        Assert.assertTrue(sprite.sprite().exists());
        int e = sprite.entityId();
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(e);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        sprite.sprite().setVisible(false)
                .setTint(1f, 0f, 0f, 0.5f)
                .setSize(42f, 24f)
                .setRepeat(true, false)
                .setAssetId(42);

        Assert.assertFalse(world.getMapper(VisibilityComponent.class).get(e).visible);
        Assert.assertEquals(42f, dimensions.width, 0.0001f);
        Assert.assertEquals(24f, dimensions.height, 0.0001f);
        Assert.assertTrue(world.getMapper(RenderRepeatComponent.class).get(e).repeatX);
        Assert.assertTrue((dirty.geomSub(e) & GeometryDirty.SIZE) != 0);
        Assert.assertTrue(dirty.isDirty(e, games.pixscape.runtime.render.DirtyBits.COLOR));
    }

    @Test
    public void validSpriteAssetReassignmentPublishesPreparedBinding() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new TwoAssetAtlasRuntimeService());
        World world = engine.getWorld();
        SpriteRef sprite = engine.api().sprites().spawn(42, 0f, 0f);
        int entity = sprite.entityId();
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        dirty.clearAll();

        sprite.sprite().setAsset(43, "main");

        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).get(entity);
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        Assert.assertEquals(43, asset.assetId);
        Assert.assertEquals("main", asset.atlasTag);
        Assert.assertTrue(region.valid);
        Assert.assertEquals(32, region.pixW);
        Assert.assertEquals(48, region.pixH);
        Assert.assertEquals(8, material.textureHandle);
        Assert.assertEquals(16f, dimensions.width, 0f);
        Assert.assertEquals(24f, dimensions.height, 0f);
        Assert.assertTrue(dirty.isDirty(
                entity, games.pixscape.runtime.render.DirtyBits.MATERIAL));
    }

    @Test
    public void missingSpriteAssetReassignmentIsFailureAtomicForBothPaths() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new TwoAssetAtlasRuntimeService());
        World world = engine.getWorld();
        SpriteRef sprite = engine.api().sprites().spawn(42, 0f, 0f);
        int entity = sprite.entityId();
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).get(entity);
        RenderMaterialComponent material = world.getMapper(RenderMaterialComponent.class).get(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).get(entity);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        dirty.clearAll();

        assertMissingSpriteAssetRejected(sprite.sprite(), false);
        assertSpriteBindingAUnchanged(asset, region, material, dimensions);
        Assert.assertFalse(dirty.isDirty(
                entity, games.pixscape.runtime.render.DirtyBits.MATERIAL));

        assertMissingSpriteAssetRejected(sprite.sprite(), true);
        assertSpriteBindingAUnchanged(asset, region, material, dimensions);
        Assert.assertFalse(dirty.isDirty(
                entity, games.pixscape.runtime.render.DirtyBits.MATERIAL));
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
    public void invalidTiledRefSetterCannotPartiallyConstructCapability() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = world.create();
        world.process();

        TiledLayerRef ref = engine.api().tiled().ofEntityId(entity);
        Assert.assertFalse(ref.exists());
        ref.map().setAtlasTag("partial");
        ref.map().setAtlasTag("partial-again");

        Assert.assertFalse(world.getMapper(TiledLayerComponent.class).has(entity));
        Assert.assertEquals("", ref.map().atlasTag());
        try {
            engine.api().tiled().requireEntityId(entity);
            Assert.fail("Expected a deterministic invalid-ref failure");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("entityId=" + entity));
        }
    }

    @Test
    public void tiledLayerResolvesByVisualLayerIndex() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        int tiled = createTiledLayer(engine, 3);

        TiledLayerRef ref = engine.api().tiled().ofLayerIndex(3);

        Assert.assertEquals(tiled, ref.entityId());
        Assert.assertTrue(ref.exists());
        Assert.assertEquals(tiled, engine.api().tiled().requireLayerIndex(3).entityId());
        Assert.assertEquals(tiled, engine.api().tiled().layer(3).entityId());
    }

    @Test
    public void tiledLayerIndexTolerantAndStrictResolutionAreDistinct() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createAuthoredLayer(engine, 4, LayerComponent.TYPE_CLASSIC);

        Assert.assertFalse(engine.api().tiled().ofLayerIndex(99).exists());
        Assert.assertFalse(engine.api().tiled().ofLayerIndex(4).exists());
        assertRequiredTiledLayerFails(engine, 99);
        assertRequiredTiledLayerFails(engine, 4);
    }

    @Test
    public void tiledMapIsInsideDistinguishesEmptyCellsFromInvalidCoordinates() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        int entity = createTiledLayer(engine, 3);
        TiledLayerRef ref = engine.api().tiled().ofLayerIndex(3);

        Assert.assertEquals(0, ref.tiles().get(0, 0));
        Assert.assertTrue(ref.map().isInside(0, 0));
        Assert.assertTrue(ref.map().isInside(3, 3));
        Assert.assertFalse(ref.map().isInside(-1, 0));
        Assert.assertFalse(ref.map().isInside(0, -1));
        Assert.assertFalse(ref.map().isInside(4, 0));
        Assert.assertFalse(ref.map().isInside(0, 4));
        Assert.assertFalse(engine.api().tiled().ofEntityId(999).map().isInside(0, 0));

        assertIllegalArgument(new Runnable() {
            @Override public void run() { ref.map().setOrigin(Float.NaN, 2f); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() {
                ref.spatial().setDefaultVolume(1f, Float.POSITIVE_INFINITY);
            }
        });
        TiledMapLayerData data = engine.getWorld().getMapper(TiledLayerComponent.class)
                .get(entity).data;
        Assert.assertEquals(0f, data.originX, 0f);
        Assert.assertEquals(0f, data.originY, 0f);
        Assert.assertEquals(0f, data.defaultTileAltitude, 0f);
        Assert.assertEquals(0f, data.defaultTileHeight, 0f);
        assertIllegalArgument(new Runnable() {
            @Override public void run() { ref.map().resize(0, 4); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { ref.map().resize(4, -1); }
        });
        Assert.assertEquals(4, ref.map().width());
        Assert.assertEquals(4, ref.map().height());

        engine.getWorld().delete(entity);
        engine.getWorld().process();
        Assert.assertFalse(ref.map().isInside(0, 0));
    }

    @Test
    public void tiledLayerIndexRejectsDuplicateAuthoredLayersAsAmbiguous() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createTiledLayer(engine, 3);
        createTiledLayer(engine, 3);

        try {
            engine.api().tiled().layer(3);
            Assert.fail("Expected duplicate authored layer index to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("ambiguous"));
            Assert.assertTrue(expected.getMessage().contains("2 authored scene layers"));
        }
    }

    @Test
    public void tiledLayerIndexRejectsEveryNonTiledAuthoredLayerType() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        final int[] types = {LayerComponent.TYPE_CLASSIC};

        for (int i = 0; i < types.length; i++) {
            int layerIndex = 3 + i;
            createAuthoredLayer(engine, layerIndex, types[i]);
            assertTiledLookupFails(engine, layerIndex, "does not designate a tiled layer");
        }
    }

    @Test
    public void tiledLayerIndexIgnoresRenderedActorMetadata() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createActorLayer(engine, 7, LayerComponent.TYPE_CLASSIC, false);
        createActorLayer(engine, 8, LayerComponent.TYPE_TILED, false);
        createActorLayer(engine, 9, LayerComponent.TYPE_TILED, true);
        int authored = createTiledLayer(engine, 10);
        createActorLayer(engine, 10, LayerComponent.TYPE_TILED, true);

        assertTiledLookupFails(engine, 7, "No tiled layer exists for layer index 7");
        assertTiledLookupFails(engine, 8, "No tiled layer exists for layer index 8");
        assertTiledLookupFails(engine, 9, "No tiled layer exists for layer index 9");
        Assert.assertEquals(authored, engine.api().tiled().layer(10).entityId());
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
        createTiledLayer(engine, 3);

        engine.api().tiled().layer(3).tiles().set(0, 0, 5);

        Assert.assertEquals(5, engine.api().tiled().layer(3).tiles().get(0, 0));
    }

    @Test
    public void tiledLayerApiSetsAnimationByName() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        createTiledLayer(engine, 3);
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
        createTiledLayer(engine, 3);

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
        createTiledLayer(engine, 3);
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102, 103}, new int[]{100, 100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer(3);
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
        createTiledLayer(engine, 3);
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102}, new int[]{100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer(3);
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
        createTiledLayer(engine, 3);
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "water_loop", new int[]{101, 102}, new int[]{100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer(3);
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
        createTiledLayer(engine, 3);
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102, 103}, new int[]{100, 100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer(3);
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
        createTiledLayer(engine, 3);
        engine.getAnimatedTileRegistry().put(tileAnimationData(
                100, "door_open_anim", new int[]{101, 102, 103}, new int[]{100, 100, 100}
        ));

        TiledLayerRef layer = engine.api().tiled().layer(3);
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
    public void shaderFacadeDoesNotCreateRenderCapabilityOrOrphanParams() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int ordinary = world.create();
        int orphanParams = world.create();
        ShaderParamsComponent existingParams =
                world.getMapper(ShaderParamsComponent.class).create(orphanParams);
        existingParams.floats.add(new ShaderFloatParam("existing", 3f));
        world.process();

        ShaderFacade ordinaryShader = engine.api().entities().ofEntityId(ordinary).shader();
        Assert.assertFalse(ordinaryShader.exists());
        ordinaryShader.use("texture-array-default").setFloat("u_time", 1f).clear();

        Assert.assertFalse(world.getMapper(RenderMaterialComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(ShaderParamsComponent.class).has(ordinary));

        ShaderFacade orphanShader = engine.api().entities().ofEntityId(orphanParams).shader();
        Assert.assertFalse(orphanShader.exists());
        orphanShader.setFloat("u_time", 2f).clearFloats();

        Assert.assertFalse(world.getMapper(RenderMaterialComponent.class).has(orphanParams));
        Assert.assertEquals(1, existingParams.floats.size);
        Assert.assertEquals("existing", existingParams.floats.first().name);

        ordinaryShader.use(" ").setFloat(" ", 2f);
        Assert.assertFalse(world.getMapper(ShaderParamsComponent.class).has(ordinary));
    }

    @Test
    public void shaderUseValidatesBeforePublishingMaterialOrDirtyState() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int e = world.create();
        RenderMaterialComponent material = world.edit(e).create(RenderMaterialComponent.class);
        world.edit(e).create(ShaderParamsComponent.class);
        world.process();

        EntityRef ref = engine.api().entities().ofEntityId(e);
        Assert.assertTrue(ref.shader().exists());
        String valid = "__valid_shader_for_facade_test__";
        String unknown = "__missing_shader_for_test__";
        ObjectIntMap<String> shaderNames = shaderNameIndex();
        shaderNames.put(valid, 4242);
        try {
            ref.shader().use(valid).setFloat("u_time", 1f);
            Assert.assertEquals(4242, material.shaderIdx);
            Assert.assertEquals(valid, ref.shader().shader());
            Assert.assertEquals(1f, ref.shader().getFloat("u_time", 0f), 0.0001f);

            DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
            dirty.clearAll();
            assertInvalidShaderRejected(ref.shader(), unknown);
            Assert.assertEquals(4242, material.shaderIdx);
            Assert.assertTrue(ref.shader().hasFloat("u_time"));
            Assert.assertEquals(1f, ref.shader().getFloat("u_time", 0f), 0.0001f);
            Assert.assertFalse(dirty.isDirty(
                    e, games.pixscape.runtime.render.DirtyBits.MATERIAL));

            assertInvalidShaderRejected(ref.shader(), "   ");
            Assert.assertEquals(4242, material.shaderIdx);
        } finally {
            shaderNames.remove(valid, -1);
        }
    }

    @Test
    public void blankShaderUniformMutationFailsBeforeParameterMutation() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int withoutParams = world.create();
        world.edit(withoutParams).create(RenderMaterialComponent.class);
        int withParams = world.create();
        world.edit(withParams).create(RenderMaterialComponent.class);
        ShaderParamsComponent existing = world.edit(withParams).create(ShaderParamsComponent.class);
        existing.floats.add(new ShaderFloatParam("existing", 3f));
        world.process();

        assertInvalidUniformRejected(
                engine.api().entities().ofEntityId(withoutParams).shader(), " ", false);
        Assert.assertFalse(world.getMapper(ShaderParamsComponent.class).has(withoutParams));

        ShaderFacade shader = engine.api().entities().ofEntityId(withParams).shader();
        assertInvalidUniformRejected(shader, null, false);
        assertInvalidUniformRejected(shader, "", true);
        Assert.assertEquals(1, existing.floats.size);
        Assert.assertEquals("existing", existing.floats.first().name);
        Assert.assertEquals(3f, existing.floats.first().value, 0f);
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
    public void spriteRepeatFacadeSynchronizesAuthoredAndDerivedState() throws Exception {
        DynamicEntityRenderState state = new DynamicEntityRenderState(8);
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, new UpdateWorldGeometrySystem(),
                        new RenderSpriteSyncSystem(state), new DirtyFlushSystem())
                .build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);
        int entity = createRenderableSprite(world);
        SpriteFacade sprite = engine.api().entities().ofEntityId(entity).sprite();

        Assert.assertFalse(sprite.repeatsX());
        Assert.assertFalse(sprite.repeatsY());
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).has(entity));
        world.process();
        int slot = state.renderSlotForEntity(entity);
        Assert.assertEquals(RenderRepeatFlags.NONE, state.repeatFlags[slot]);

        assertRepeat(world, state, entity, sprite, true, false, RenderRepeatFlags.REPEAT_X);
        assertRepeat(world, state, entity, sprite, false, true, RenderRepeatFlags.REPEAT_Y);
        assertRepeat(world, state, entity, sprite, true, true, RenderRepeatFlags.ANY);
        assertRepeat(world, state, entity, sprite, false, false, RenderRepeatFlags.NONE);
    }

    @Test
    public void animatedSpriteStoresRepeatButRendererSuppressesIt() throws Exception {
        DynamicEntityRenderState state = new DynamicEntityRenderState(8);
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64), new UpdateWorldGeometrySystem(),
                        new RenderSpriteSyncSystem(state), new DirtyFlushSystem())
                .build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);
        int entity = createRenderableSprite(world);
        world.getMapper(AnimationComponent.class).create(entity);

        SpriteFacade sprite = engine.api().entities().ofEntityId(entity).sprite();
        sprite.setRepeat(true, true);
        world.process();

        int slot = state.renderSlotForEntity(entity);
        Assert.assertTrue(sprite.repeatsX());
        Assert.assertTrue(sprite.repeatsY());
        Assert.assertEquals(RenderRepeatFlags.NONE, state.repeatFlags[slot]);
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
        engine.getAnimationRegistry().put(animationDef(42, "hero"));
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
        Assert.assertFalse(world.getMapper(DimensionsComponent.class).has(ref.entityId()));
        Assert.assertFalse(world.getMapper(AABBComponent.class).has(ref.entityId()));
        Assert.assertFalse(world.getMapper(OrientedBoundsComponent.class).has(ref.entityId()));
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
    public void persistentParticleCompletesWithoutRemovingEntityAndCanRestart() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        ParticleRef ref = engine.api().particles().spawn("impact", 5f, 6f);
        ParticleEmitterComponent emitter = engine.getWorld()
                .getMapper(ParticleEmitterComponent.class).get(ref.entityId());

        ref.loop(false).stop();
        Assert.assertSame(ref, ref.restart());
        Assert.assertTrue(emitter.restartRequested);
        Assert.assertFalse(emitter.paused);
        Assert.assertFalse(emitter.looping);
        Assert.assertFalse(emitter.autoRemoveWhenComplete);
        Assert.assertEquals("impact.p", emitter.effectPath);
        engine.getWorld().process();

        Assert.assertTrue(ref.entity().exists());
        Assert.assertTrue(ref.particles().exists());

        ref.restart();
        engine.getWorld().process();
        Assert.assertTrue(ref.entity().exists());
    }

    @Test
    public void staleParticleRestartIsSafeAndInert() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        ParticleRef ref = engine.api().particles().spawn("impact", 5f, 6f);

        ref.remove();
        engine.getWorld().process();

        Assert.assertFalse(ref.entity().exists());
        Assert.assertSame(ref, ref.restart());
        Assert.assertFalse(ref.entity().exists());
    }

    @Test
    public void particlesSpawnRejectsUndeclaredResourceWithoutCreatingEntity()
            throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entitiesBefore = world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities().size();

        IllegalStateException unavailable = Assert.assertThrows(
                IllegalStateException.class,
                () -> engine.api().particles().spawn("undeclared.p", 0f, 0f));

        Assert.assertTrue(unavailable.getMessage().contains("Runtime Availability"));
        Assert.assertEquals(entitiesBefore,
                world.getAspectSubscriptionManager()
                        .get(Aspect.all()).getEntities().size());
    }

    @Test
    public void particlesOneshotCreatesNonLoopingParticleEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        ParticleRef ref = engine.api().particles().oneshot("impact.p", 1f, 2f);
        ParticleEmitterComponent emitter = engine.getWorld().getMapper(ParticleEmitterComponent.class).get(ref.entityId());
        World world = engine.getWorld();

        Assert.assertEquals("impact.p", emitter.effectPath);
        Assert.assertFalse(emitter.looping);
        Assert.assertTrue(emitter.autoRemoveWhenComplete);
        Assert.assertTrue(emitter.restartRequested);
        Assert.assertFalse(world.getMapper(DimensionsComponent.class).has(ref.entityId()));
        Assert.assertFalse(world.getMapper(AABBComponent.class).has(ref.entityId()));
        Assert.assertFalse(world.getMapper(OrientedBoundsComponent.class).has(ref.entityId()));
    }

    @Test
    public void completedOneshotRemovesItsEntity() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        ParticleRef ref = engine.api().particles().oneshot("impact.p", 1f, 2f);

        engine.getWorld().process();
        engine.getWorld().process();

        Assert.assertFalse(ref.entity().exists());
    }

    @Test
    public void particleFacadeCreationAlsoRequiresTransformWithoutProxies() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = world.create();
        world.process();

        engine.api().entities().ofEntityId(entity).particles().setEffect("fire.p", "main");

        Assert.assertTrue(world.getMapper(ParticleEmitterComponent.class).has(entity));
        Assert.assertTrue(world.getMapper(TransformComponent.class).has(entity));
        Assert.assertFalse(world.getMapper(DimensionsComponent.class).has(entity));
        Assert.assertFalse(world.getMapper(AABBComponent.class).has(entity));
        Assert.assertFalse(world.getMapper(OrientedBoundsComponent.class).has(entity));
    }

    @Test
    public void particleFacadeRejectsMalformedIdentityBeforeAuthoredMutation() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = world.create();
        world.edit(entity).create(TransformComponent.class);
        ParticleEmitterComponent emitter = world.edit(entity).create(ParticleEmitterComponent.class);
        emitter.effectPath = "live-a.p";
        emitter.atlasTag = "atlas-a";
        int arbitrary = world.create();
        world.process();

        ParticleFacade facade = engine.api().entities().ofEntityId(entity).particles();
        assertInvalidParticleIdentityRejected(facade, " ", "atlas-b");
        assertInvalidParticleIdentityRejected(facade, "b.p", null);
        Assert.assertEquals("live-a.p", emitter.effectPath);
        Assert.assertEquals("atlas-a", emitter.atlasTag);

        ParticleFacade arbitraryFacade = engine.api().entities().ofEntityId(arbitrary).particles();
        assertInvalidParticleIdentityRejected(arbitraryFacade, "", "main");
        Assert.assertFalse(world.getMapper(TransformComponent.class).has(arbitrary));
        Assert.assertFalse(world.getMapper(ParticleEmitterComponent.class).has(arbitrary));

        IllegalStateException unavailable = Assert.assertThrows(
                IllegalStateException.class,
                () -> facade.setEffect("unavailable-but-well-formed.p", "missing-atlas"));
        Assert.assertTrue(unavailable.getMessage().contains("Runtime Availability"));
        Assert.assertEquals("live-a.p", emitter.effectPath);
        Assert.assertEquals("atlas-a", emitter.atlasTag);
        Assert.assertTrue(facade.exists());
    }

    @Test
    public void transformAndSpatialFacadesRemainAuthoredBuilders() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int transformEntity = world.create();
        int spatialEntity = world.create();
        world.process();

        engine.api().entities().ofEntityId(transformEntity).transform().setPosition(3f, 4f);
        engine.api().entities().ofEntityId(spatialEntity).spatial().setVolume(2f, 5f);

        TransformComponent transform =
                world.getMapper(TransformComponent.class).get(transformEntity);
        SpatialHeightComponent spatial =
                world.getMapper(SpatialHeightComponent.class).get(spatialEntity);
        Assert.assertEquals(3f, transform.x, 0f);
        Assert.assertEquals(4f, transform.y, 0f);
        Assert.assertEquals(2f, spatial.altitude, 0f);
        Assert.assertEquals(5f, spatial.height, 0f);
    }

    @Test
    public void animationsSpawnCreatesAnimatedEntityFromAuthoredDefinition() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));
        AnimationDefData definition = animationDef(42, "hero");
        definition.currentClip = "default";
        definition.clips.clear();
        definition.clips.add(animationClip("default", 0, 7, false));
        engine.getAnimationRegistry().put(definition);
        AnimationRef ref = engine.api().animations().spawn(42, 7f, 8f)
                .fps(18f)
                .loop(false)
                .play();

        World world = engine.getWorld();
        Assert.assertTrue(world.getMapper(AnimationComponent.class).has(ref.entityId()));
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(ref.entityId());
        Assert.assertEquals("default", animation.currentClip);
        Assert.assertArrayEquals(new int[]{42}, animation.animationAssetIds.toArray());
        Assert.assertNotNull(engine.getAnimationRegistry().getByAssetId(42).clip("default"));
        Assert.assertEquals(18f, animation.fps, 0.0001f);
        Assert.assertFalse(animation.loop);
        Assert.assertTrue(animation.playing);
        Assert.assertSame(ref.animation(), engine.api().animations().get(ref.entity()));

        ref.animation().setClip("default").setStateTime(2f);
        Assert.assertEquals("default", ref.animation().clip());
        Assert.assertEquals(2f, ref.animation().stateTime(), 0f);
    }

    @Test
    public void animationsSpawnRejectsUnknownAnimationAssetsWithoutAtlasFallback() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new FakeAtlasRuntimeService(42));

        assertUnknownAnimationRejected(new Runnable() {
            @Override public void run() { engine.api().animations().spawn(42, 0f, 0f); }
        }, "id: 42");
        assertUnknownAnimationRejected(new Runnable() {
            @Override public void run() { engine.api().animations().spawn("hero", 0f, 0f); }
        }, "name: 'hero'");

        Assert.assertEquals(0, engine.getAnimationRegistry().size());
    }

    @Test
    public void animationClipSelectionIsStrictAndFailureAtomic() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = createAnimatedSprite(
                engine, world, "idle", 0, 2,
                animationClip("walk", 3, 5, false));
        AnimationComponent animation = world.getMapper(AnimationComponent.class).get(entity);
        animation.currentClip = "idle";
        animation.frame = 2;
        animation.stateTime = 1.5f;
        animation.playing = false;
        animation.loop = true;
        world.process();
        AnimationFacade facade = engine.api().entities().ofEntityId(entity).animation();
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        dirty.clearAll();

        assertInvalidClipRejected(facade, "missing", false);
        assertAnimationIdleStateUnchanged(animation);
        assertInvalidClipRejected(facade, "missing", true);
        assertAnimationIdleStateUnchanged(animation);
        assertInvalidClipRejected(facade, null, false);
        assertInvalidClipRejected(facade, "   ", true);
        assertAnimationIdleStateUnchanged(animation);
        Assert.assertEquals(7,
                world.getMapper(RenderMaterialComponent.class).get(entity).textureHandle);
        Assert.assertFalse(dirty.isDirty(
                entity, games.pixscape.runtime.render.DirtyBits.MATERIAL));

        facade.setClip("walk");
        Assert.assertEquals("walk", animation.currentClip);
        Assert.assertEquals(-1, animation.frame);
        Assert.assertEquals(0f, animation.stateTime, 0f);
        Assert.assertFalse(animation.playing);
        facade.play("idle");
        Assert.assertEquals("idle", animation.currentClip);
        Assert.assertTrue(animation.playing);
    }

    @Test
    public void animationFpsAcceptsZeroAndRejectsInvalidValuesAtomically() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = createAnimatedSprite(engine, world, "idle", 0, 2);
        world.process();
        AnimationFacade facade = engine.api().entities().ofEntityId(entity).animation();

        facade.setFps(0f);
        Assert.assertEquals(0f, facade.fps(), 0f);
        facade.setFps(12f);
        assertInvalidFpsRejected(facade, -1f);
        assertInvalidFpsRejected(facade, Float.NaN);
        assertInvalidFpsRejected(facade, Float.POSITIVE_INFINITY);
        assertInvalidFpsRejected(facade, Float.NEGATIVE_INFINITY);
        Assert.assertEquals(12f, facade.fps(), 0f);

        facade.setStateTime(2f);
        assertIllegalArgument(new Runnable() {
            @Override public void run() { facade.setStateTime(Float.NaN); }
        });
        Assert.assertEquals(2f, facade.stateTime(), 0f);
        facade.setStateTime(-1f);
        Assert.assertEquals(0f, facade.stateTime(), 0f);
    }

    @Test
    public void animationFacadeRejectsMissingAndBareAnimationCapabilities() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int ordinary = world.create();
        int bare = world.create();
        AnimationComponent bareAnimation =
                world.getMapper(AnimationComponent.class).create(bare);
        bareAnimation.fps = 7f;
        bareAnimation.playing = false;
        bareAnimation.loop = false;
        bareAnimation.currentClip = "";
        world.process();

        AnimationFacade ordinaryAnimation =
                engine.api().entities().ofEntityId(ordinary).animation();
        ordinaryAnimation.play("missing").setLoop(true).setFps(20f).setStateTime(3f);

        Assert.assertFalse(ordinaryAnimation.exists());
        Assert.assertFalse(world.getMapper(AnimationComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(AssetRefComponent.class).has(ordinary));
        Assert.assertFalse(world.getMapper(RenderMaterialComponent.class).has(ordinary));

        AnimationFacade bareFacade = engine.api().entities().ofEntityId(bare).animation();
        Assert.assertFalse(bareFacade.exists());
        bareFacade.play().setClip("missing").setLoop(true).setFps(20f).setStateTime(3f);

        Assert.assertEquals(7f, bareAnimation.fps, 0f);
        Assert.assertFalse(bareAnimation.playing);
        Assert.assertFalse(bareAnimation.loop);
        Assert.assertEquals("", bareAnimation.currentClip);
        Assert.assertFalse(world.getMapper(AssetRefComponent.class).has(bare));
        Assert.assertFalse(world.getMapper(TextureRegionComponent.class).has(bare));
        Assert.assertFalse(world.getMapper(RenderMaterialComponent.class).has(bare));
    }

    @Test
    public void animationFacadeQueriesRuntimeStateAndCompletionBoundaries() throws Exception {
        FixedFramesAtlasRuntimeService atlas = new FixedFramesAtlasRuntimeService(3);
        PixscapeEngine engine = new PixscapeEngine();
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64),
                        new AnimationSystem(engine.getAnimationRegistry(), atlas))
                .build());
        setField(engine, "world", world);
        int entity = createAnimatedSprite(
                engine, world, "forward", 0, 2,
                animationClip("reverse", 2, 0, false));
        AnimationFacade animation = engine.api().entities().ofEntityId(entity).animation();

        Assert.assertEquals("forward", animation.clip());
        Assert.assertTrue(animation.hasClip("forward"));
        Assert.assertFalse(animation.hasClip("missing"));
        Assert.assertFalse(animation.hasClip(null));
        Assert.assertFalse(animation.hasClip("   "));
        Assert.assertEquals(-1, animation.frame());
        Assert.assertEquals(0f, animation.stateTime(), 0f);

        world.setDelta(0.5f);
        world.process();
        Assert.assertEquals(1, animation.frame());
        Assert.assertEquals(0.5f, animation.stateTime(), 0f);
        Assert.assertFalse(animation.isFinished());

        world.process();
        Assert.assertEquals("Final frame is visible before its full duration is consumed",
                2, animation.frame());
        Assert.assertFalse(animation.isFinished());

        world.process();
        Assert.assertEquals(1.5f, animation.stateTime(), 0f);
        Assert.assertTrue("Exact full-duration boundary is finished", animation.isFinished());

        world.process();
        Assert.assertTrue("Playback after the boundary remains finished", animation.isFinished());

        AnimationComponent component = world.getMapper(AnimationComponent.class).get(entity);
        component.currentClip = "reverse";
        component.stateTime = 1.5f;
        Assert.assertTrue("Reverse clips use the same inclusive frame count", animation.isFinished());

        component.loop = true;
        Assert.assertFalse(animation.isFinished());
        component.loop = false;
        component.fps = 0f;
        Assert.assertFalse(animation.isFinished());
        component.fps = 2f;
        component.currentClip = "missing";
        Assert.assertFalse(animation.isFinished());

        int missingEntity = world.create();
        world.process();
        AnimationFacade missing = engine.api().entities().ofEntityId(missingEntity).animation();
        Assert.assertEquals("", missing.clip());
        Assert.assertFalse(missing.hasClip("forward"));
        Assert.assertEquals(-1, missing.frame());
        Assert.assertEquals(0f, missing.stateTime(), 0f);
        Assert.assertFalse(missing.isFinished());
    }

    @Test
    public void authoredSceneActorAnimationDoesNotRequireLayerComponent() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        World world = engine.getWorld();
        int entity = createAnimatedSprite(
                engine, world, "run_000", 0, 19,
                animationClip("run_090", 40, 59, false));
        world.getMapper(LayerComponent.class).remove(entity);
        world.process();

        AnimationFacade animation = engine.api().entities().ofEntityId(entity).animation();

        Assert.assertTrue(animation.exists());
        Assert.assertTrue(animation.hasClip("run_090"));
        animation.play("run_090");
        Assert.assertEquals("run_090", animation.clip());
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
        Assert.assertArrayEquals(new int[]{42}, animation.animationAssetIds.toArray());
        AnimationClipDef attack = engine.getAnimationRegistry().getByAssetId(42).clip("attack");
        Assert.assertNotNull(attack);
        Assert.assertEquals(4, attack.start());
        Assert.assertEquals(7, attack.end());
        Assert.assertTrue(attack.flipX());
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
        Assert.assertEquals(4, engine.getAnimationRegistry()
                .getByAssetId(42).clip("attack").start());
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

    @Test
    public void animationSwitchByIdAndNameRestoresAuthoredDefaultsAndFps() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new TwoAssetAtlasRuntimeService());
        World world = engine.getWorld();
        int entity = createMultiAnimationEntity(engine, world);
        AnimationComponent state = world.getMapper(AnimationComponent.class).get(entity);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        AnimationFacade animation = engine.api().entities().ofEntityId(entity).animation();

        state.playing = false;
        state.loop = false;
        state.stateTime = 2f;
        state.frame = 3;
        animation.setFps(30f).setAnimation(43);

        assertAnimationState(asset, state, 43, "attack", 18f, false, false, 0f, -1);

        animation.setAnimation("idle-animation");
        assertAnimationState(asset, state, 42, "idle", 6f, false, false, 0f, -1);
    }

    @Test
    public void animationSwitchRejectsUnknownUnauthorizedAndUnavailableTargetsAtomically()
            throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new TwoAssetAtlasRuntimeService());
        World world = engine.getWorld();
        int entity = createMultiAnimationEntity(engine, world);
        AnimationComponent state = world.getMapper(AnimationComponent.class).get(entity);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        AnimationFacade animation = engine.api().entities().ofEntityId(entity).animation();
        state.playing = false;
        state.loop = false;
        state.stateTime = 2f;
        state.frame = 3;

        engine.getAnimationRegistry().put(singleClipAnimation(44, "unauthorized", 24f, "cast"));
        assertIllegalArgument(new Runnable() {
            @Override public void run() { animation.setAnimation(44); }
        });
        assertAnimationState(asset, state, 42, "idle", 6f, false, false, 2f, 3);

        assertIllegalArgument(new Runnable() {
            @Override public void run() { animation.setAnimation("missing"); }
        });
        assertAnimationState(asset, state, 42, "idle", 6f, false, false, 2f, 3);

        state.animationAssetIds.add(45);
        engine.getAnimationRegistry().put(singleClipAnimation(45, "unavailable", 30f, "jump"));
        assertIllegalArgument(new Runnable() {
            @Override public void run() { animation.setAnimation(45); }
        });
        assertAnimationState(asset, state, 42, "idle", 6f, false, false, 2f, 3);
    }

    @Test
    public void animationSwitchAndPlayIsStrictToTargetAndFailureAtomic() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        setField(engine, "atlasRuntimeService", new TwoAssetAtlasRuntimeService());
        World world = engine.getWorld();
        int entity = createMultiAnimationEntity(engine, world);
        AnimationComponent state = world.getMapper(AnimationComponent.class).get(entity);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        AnimationFacade animation = engine.api().entities().ofEntityId(entity).animation();
        state.playing = false;
        state.loop = false;
        state.stateTime = 2f;
        state.frame = 3;

        assertIllegalArgument(new Runnable() {
            @Override public void run() { animation.play(43, "walk"); }
        });
        assertAnimationState(asset, state, 42, "idle", 6f, false, false, 2f, 3);

        animation.play("attack-animation", "attack_alt");
        assertAnimationState(asset, state, 43, "attack_alt", 18f, true, false, 0f, -1);
    }

    @Test
    public void animationDefinitionsAreReadOnlyMetadataResolvedByIdAndName() throws Exception {
        PixscapeEngine engine = setupEngineWithWorld();
        AnimationDefData def = singleClipAnimation(42, "hero", 18f, "attack");
        def.frameCount = 4;
        def.clips.clear();
        def.clips.add(animationClip("attack", 0, 3, false));
        engine.getAnimationRegistry().put(def);

        AnimationDefinition byId = engine.api().animations().definition(42);
        AnimationDefinition byName = engine.api().animations().definition("hero");
        Assert.assertSame(byId, byName);
        Assert.assertEquals(42, byId.assetId());
        Assert.assertEquals("hero", byId.name());
        Assert.assertEquals(18f, byId.fps(), 0f);
        Assert.assertEquals("attack", byId.currentClip());
        Assert.assertEquals(4, byId.frameCount());
        Assert.assertEquals(1, byId.clipCount());
        Assert.assertTrue(byId.hasClip("attack"));
        Assert.assertFalse(byId.hasClip("missing"));
        assertIllegalArgument(new Runnable() {
            @Override public void run() { engine.api().animations().definition(99); }
        });
        assertIllegalArgument(new Runnable() {
            @Override public void run() { engine.api().animations().definition("missing"); }
        });
    }

    @Test
    public void pausedAnimationSwitchResolvesNewVisualOnNextUpdate() throws Exception {
        TwoAssetAtlasRuntimeService atlas = new TwoAssetAtlasRuntimeService();
        PixscapeEngine engine = new PixscapeEngine();
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(64),
                        new AnimationSystem(engine.getAnimationRegistry(), atlas))
                .build());
        setField(engine, "world", world);
        setField(engine, "atlasRuntimeService", atlas);
        int entity = createMultiAnimationEntity(engine, world);
        AnimationComponent state = world.getMapper(AnimationComponent.class).get(entity);
        state.playing = false;
        state.frame = 0;

        engine.api().entities().ofEntityId(entity).animation().setAnimation(43);
        world.process();

        Assert.assertFalse(state.playing);
        Assert.assertEquals(0, state.frame);
        Assert.assertNotEquals(7,
                world.getMapper(RenderMaterialComponent.class).get(entity).textureHandle);
    }

    private static PixscapeEngine setupEngineWithWorld() throws Exception {
        return setupEngineWithWorld(null);
    }

    private static void assertMissingSpriteAssetRejected(SpriteFacade sprite,
                                                         boolean assetIdOnly) {
        try {
            if (assetIdOnly) sprite.setAssetId(99);
            else sprite.setAsset(99, "main");
            Assert.fail("Expected missing sprite asset to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Asset #99"));
        }
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            Assert.fail("Expected invalid facade input to fail");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void assertUnknownAnimationRejected(Runnable action, String identity) {
        try {
            action.run();
            Assert.fail("Expected unknown Animation asset to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(identity));
        }
    }

    private static void assertSpriteBindingAUnchanged(AssetRefComponent asset,
                                                      TextureRegionComponent region,
                                                      RenderMaterialComponent material,
                                                      DimensionsComponent dimensions) {
        Assert.assertEquals(42, asset.assetId);
        Assert.assertEquals("main", asset.atlasTag);
        Assert.assertTrue(region.valid);
        Assert.assertEquals(16, region.pixW);
        Assert.assertEquals(24, region.pixH);
        Assert.assertEquals(7, material.textureHandle);
        Assert.assertEquals("main", material.debugAtlasTag);
        Assert.assertEquals(16f, dimensions.width, 0f);
        Assert.assertEquals(24f, dimensions.height, 0f);
    }

    private static void assertInvalidClipRejected(AnimationFacade animation,
                                                  String clipName,
                                                  boolean play) {
        try {
            if (play) animation.play(clipName);
            else animation.setClip(clipName);
            Assert.fail("Expected invalid animation clip to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("animation clip"));
        }
    }

    private static void assertAnimationIdleStateUnchanged(AnimationComponent animation) {
        Assert.assertEquals("idle", animation.currentClip);
        Assert.assertEquals(2, animation.frame);
        Assert.assertEquals(1.5f, animation.stateTime, 0f);
        Assert.assertFalse(animation.playing);
        Assert.assertTrue(animation.loop);
    }

    private static void assertInvalidFpsRejected(AnimationFacade animation, float fps) {
        try {
            animation.setFps(fps);
            Assert.fail("Expected invalid animation fps to fail: " + fps);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("fps"));
        }
        Assert.assertEquals(12f, animation.fps(), 0f);
    }

    private static void assertInvalidParticleIdentityRejected(ParticleFacade particles,
                                                              String effectPath,
                                                              String atlasTag) {
        try {
            particles.setEffect(effectPath, atlasTag);
            Assert.fail("Expected malformed particle identity to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("must not be blank"));
        }
    }

    private static void assertInvalidShaderRejected(ShaderFacade shader, String shaderName) {
        try {
            shader.use(shaderName);
            Assert.fail("Expected invalid shader name to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().toLowerCase().contains("shader"));
        }
    }

    private static void assertInvalidUniformRejected(ShaderFacade shader,
                                                     String uniform,
                                                     boolean remove) {
        try {
            if (remove) shader.removeFloat(uniform);
            else shader.setFloat(uniform, 1f);
            Assert.fail("Expected blank uniform name to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("uniform name"));
        }
    }

    @SuppressWarnings("unchecked")
    private static ObjectIntMap<String> shaderNameIndex() throws Exception {
        Field field = ShaderRegistry.class.getDeclaredField("nameToIdx");
        field.setAccessible(true);
        return (ObjectIntMap<String>) field.get(null);
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

    private static int createAuthoredLayer(PixscapeEngine engine, int layerIndex, int type) {
        World world = engine.getWorld();
        int e = world.create();
        LayerComponent layer = world.edit(e).create(LayerComponent.class);
        layer.layerIndex = layerIndex;
        layer.type = type;
        world.process();
        return e;
    }

    private static int createTiledLayer(PixscapeEngine engine, int layerIndex) {
        World world = engine.getWorld();
        int host = world.create();
        LayerComponent layer = world.edit(host).create(LayerComponent.class);
        layer.layerIndex = layerIndex;
        layer.type = LayerComponent.TYPE_TILED;

        int map = world.create();
        EntityIndexComponent index = world.edit(map).create(EntityIndexComponent.class);
        index.layerIndex = layerIndex;
        index.zIndex = 0;
        TiledLayerComponent tiled = world.edit(map).create(TiledLayerComponent.class);
        tiled.data = new TiledMapLayerData(4, 4, 16, 16, 2);

        world.process();
        return map;
    }

    private static int createActorLayer(PixscapeEngine engine, int layerIndex,
                                        int type, boolean tiledComponent) {
        World world = engine.getWorld();
        int e = world.create();
        LayerComponent layer = world.edit(e).create(LayerComponent.class);
        layer.layerIndex = layerIndex;
        layer.type = type;
        world.edit(e).create(EntityIndexComponent.class).layerIndex = layerIndex;
        if (tiledComponent) {
            TiledLayerComponent tiled = world.edit(e).create(TiledLayerComponent.class);
            tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
        }
        world.process();
        return e;
    }

    private static void assertTiledLookupFails(PixscapeEngine engine, int layerIndex,
                                               String expectedMessage) {
        try {
            engine.api().tiled().layer(layerIndex);
            Assert.fail("Expected tiled layer lookup to fail for index " + layerIndex);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(expectedMessage));
        }
    }

    private static void assertRequiredTiledLayerFails(PixscapeEngine engine, int layerIndex) {
        try {
            engine.api().tiled().requireLayerIndex(layerIndex);
            Assert.fail("Expected strict tiled lookup to fail for index " + layerIndex);
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("layerIndex=" + layerIndex));
        }
    }

    private static int createRenderableSprite(World world) {
        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        DimensionsComponent dimensions = world.getMapper(DimensionsComponent.class).create(entity);
        dimensions.width = 16f;
        dimensions.height = 16f;
        world.getMapper(OrientedBoundsComponent.class).create(entity);
        world.getMapper(AABBComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(LayerComponent.class).create(entity);
        world.getMapper(VisibilityComponent.class).create(entity);
        world.getMapper(TintComponent.class).create(entity);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).create(entity);
        asset.assetId = 1;
        asset.atlasTag = "main";
        TextureRegionComponent region = world.getMapper(TextureRegionComponent.class).create(entity);
        region.valid = true;
        region.u2 = 1f;
        region.v2 = 1f;
        world.getMapper(RenderMaterialComponent.class).create(entity).textureHandle = 7;
        return entity;
    }

    private static void assertRepeat(World world,
                                     DynamicEntityRenderState state,
                                     int entity,
                                     SpriteFacade sprite,
                                     boolean repeatX,
                                     boolean repeatY,
                                     byte expectedFlags) {
        sprite.setRepeat(repeatX, repeatY);
        RenderRepeatComponent authored = world.getMapper(RenderRepeatComponent.class).get(entity);
        Assert.assertEquals(repeatX, authored.repeatX);
        Assert.assertEquals(repeatY, authored.repeatY);
        Assert.assertEquals(repeatX, sprite.repeatsX());
        Assert.assertEquals(repeatY, sprite.repeatsY());

        world.process();

        int slot = state.renderSlotForEntity(entity);
        Assert.assertEquals(expectedFlags, state.repeatFlags[slot]);
    }

    private static int createAnimatedSprite(PixscapeEngine engine,
                                            World world,
                                            String clipName,
                                            int start,
                                            int end,
                                            AnimationClipDefData... additionalClips) {
        AnimationDefData def = new AnimationDefData();
        def.assetId = 1;
        def.name = "test-animation";
        def.fps = 2f;
        def.currentClip = clipName;
        def.frameCount = Math.max(Math.max(start, end) + 1, 1);
        def.clips.add(animationClip(clipName, start, end, false));
        for (int i = 0; i < additionalClips.length; i++) {
            AnimationClipDefData clip = additionalClips[i];
            def.clips.add(clip);
            def.frameCount = Math.max(def.frameCount, Math.max(clip.start, clip.end) + 1);
        }
        engine.getAnimationRegistry().put(def);

        int entity = createRenderableSprite(world);
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entity);
        animation.animationAssetIds.add(1);
        animation.currentClip = clipName;
        animation.fps = 2f;
        animation.loop = false;
        animation.playing = true;
        animation.frame = -1;
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        asset.assetId = 1;
        asset.atlasTag = "main";
        return entity;
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
        RenderParticleSyncSystem particles = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                new AtlasRuntimeService(), null);
        prepareParticlePool(particles, "main", "impact.p");
        prepareParticlePool(particles, "main", "fire.p");
        WorldConfigurationBuilder builder = new WorldConfigurationBuilder().with(dirty, particles);
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

    @SuppressWarnings("unchecked")
    private static void prepareParticlePool(
            RenderParticleSyncSystem system, String atlasTag, String effectPath)
            throws Exception {
        Field availabilityField = RenderParticleSyncSystem.class
                .getDeclaredField("particleAvailability");
        availabilityField.setAccessible(true);
        ParticleRuntimeAvailability availability =
                (ParticleRuntimeAvailability) availabilityField.get(system);
        Field poolsField = ParticleRuntimeAvailability.class.getDeclaredField("pools");
        poolsField.setAccessible(true);
        ObjectMap<String, ParticleEffectPool> pools =
                (ObjectMap<String, ParticleEffectPool>) poolsField.get(availability);
        pools.put(atlasTag + "|" + effectPath,
                new ParticleEffectPool(new ParticleEffect(), 0, 4));
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

    private static AnimationDefData singleClipAnimation(
            int assetId, String name, float fps, String clipName) {
        AnimationDefData def = new AnimationDefData();
        def.assetId = assetId;
        def.name = name;
        def.fps = fps;
        def.currentClip = clipName;
        def.frameCount = 1;
        def.clips.add(animationClip(clipName, 0, 0, false));
        return def;
    }

    private static int createMultiAnimationEntity(
            PixscapeEngine engine, World world) {
        AnimationDefData idle = singleClipAnimation(42, "idle-animation", 6f, "idle");
        idle.clips.add(animationClip("walk", 0, 0, false));
        AnimationDefData attack = singleClipAnimation(43, "attack-animation", 18f, "attack");
        attack.clips.add(animationClip("attack_alt", 0, 0, false));
        engine.getAnimationRegistry().put(idle);
        engine.getAnimationRegistry().put(attack);

        int entity = createRenderableSprite(world);
        AssetRefComponent asset = world.getMapper(AssetRefComponent.class).get(entity);
        asset.assetId = 42;
        asset.atlasTag = "main";
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entity);
        animation.animationAssetIds.add(42);
        animation.animationAssetIds.add(43);
        animation.currentClip = "idle";
        animation.fps = 6f;
        animation.playing = true;
        animation.loop = true;
        animation.stateTime = 0f;
        animation.frame = -1;
        return entity;
    }

    private static void assertAnimationState(
            AssetRefComponent asset,
            AnimationComponent animation,
            int expectedAssetId,
            String expectedClip,
            float expectedFps,
            boolean expectedPlaying,
            boolean expectedLoop,
            float expectedStateTime,
            int expectedFrame) {
        Assert.assertEquals(expectedAssetId, asset.assetId);
        Assert.assertEquals(expectedClip, animation.currentClip);
        Assert.assertEquals(expectedFps, animation.fps, 0f);
        Assert.assertEquals(expectedPlaying, animation.playing);
        Assert.assertEquals(expectedLoop, animation.loop);
        Assert.assertEquals(expectedStateTime, animation.stateTime, 0f);
        Assert.assertEquals(expectedFrame, animation.frame);
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

    private static final class TwoAssetAtlasRuntimeService extends AtlasRuntimeService {
        private final AtlasAssetBinding bindingA = AtlasBindingTestFactory.single(
                42, "crate__a42", 0f, 0f, 0.5f, 0.5f, 7, 16, 24);
        private final AtlasAssetBinding bindingB = AtlasBindingTestFactory.single(
                43, "barrel__a43", 0.5f, 0.5f, 1f, 1f, 8, 32, 48);

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            if (!"main".equals(tag)) return null;
            if (assetId == 42) return bindingA;
            if (assetId == 43) return bindingB;
            return null;
        }
    }

    private static final class FixedFramesAtlasRuntimeService extends AtlasRuntimeService {
        private final AtlasAssetBinding binding;

        FixedFramesAtlasRuntimeService(int frameCount) {
            binding = AtlasBindingTestFactory.frames(
                    1, "animation__a1", frameCount, 7, 16, 16);
        }

        @Override
        public AtlasAssetBinding resolveBinding(int assetId, String tag) {
            return assetId == 1 ? binding : null;
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return (char) 0;
        return null;
    }

    private static final class ProcessCounterSystem extends BaseSystem {
        int processCount = 0;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }
}
