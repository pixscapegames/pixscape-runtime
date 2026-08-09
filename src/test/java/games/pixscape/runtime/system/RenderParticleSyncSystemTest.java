package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.api.ParticleFacade;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.io.StringWriter;

public class RenderParticleSyncSystemTest {

    private Application previousApp;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void installApplication() {
        previousApp = Gdx.app;
        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> null);
    }

    @After
    public void restoreApplication() {
        Gdx.app = previousApp;
    }

    @Test
    public void particleContractsDoNotExposeLocalSpace() {
        try {
            ParticleEmitterComponent.class.getDeclaredField("localSpace");
            Assert.fail("ParticleEmitterComponent must not expose localSpace");
        } catch (NoSuchFieldException expected) {
            // Expected: particles always follow Transform.x/y.
        }

        Method[] methods = ParticleFacade.class.getMethods();
        for (int i = 0; i < methods.length; i++) {
            Assert.assertNotEquals("setLocalSpace", methods[i].getName());
        }
    }

    @Test
    public void positionsEffectAtTransformPositionIgnoringOrigin() {
        CapturingParticleEffect effect = new CapturingParticleEffect();
        TransformComponent transform = new TransformComponent();
        transform.x = 12f;
        transform.y = -7f;
        transform.originX = 100f;
        transform.originY = 200f;

        RenderParticleSyncSystem.positionEffect(effect, transform);

        Assert.assertEquals(12f, effect.x, 0f);
        Assert.assertEquals(-7f, effect.y, 0f);
    }

    @Test
    public void followsChangedTransformPosition() {
        CapturingParticleEffect effect = new CapturingParticleEffect();
        TransformComponent transform = new TransformComponent();
        transform.x = 1f;
        transform.y = 2f;
        RenderParticleSyncSystem.positionEffect(effect, transform);

        transform.x = 30f;
        transform.y = 40f;
        RenderParticleSyncSystem.positionEffect(effect, transform);

        Assert.assertEquals(30f, effect.x, 0f);
        Assert.assertEquals(40f, effect.y, 0f);
    }

    @Test
    public void layerVisibilityComesOnlyFromAuthoredSceneLayerEntities() {
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0, null, null);
        World world = new World(new WorldConfigurationBuilder().with(system).build());

        int sceneLayerEntity = world.create();
        LayerComponent sceneLayer = world.getMapper(LayerComponent.class).create(sceneLayerEntity);
        sceneLayer.layerIndex = 4;
        VisibilityComponent sceneVisibility = world.getMapper(VisibilityComponent.class).create(sceneLayerEntity);
        sceneVisibility.visible = false;

        int actorEntity = world.create();
        LayerComponent actorLayer = world.getMapper(LayerComponent.class).create(actorEntity);
        actorLayer.layerIndex = 4;
        world.getMapper(EntityIndexComponent.class).create(actorEntity).layerIndex = 4;
        VisibilityComponent actorVisibility = world.getMapper(VisibilityComponent.class).create(actorEntity);
        actorVisibility.visible = true;
        actorVisibility.inView = true;

        world.process();

        Assert.assertFalse(system.isLayerVisible(actorEntity));
    }

    @Test
    public void extractsEmitterBlendModesWithBatchEquivalentPrecedence() throws Exception {
        VfxRenderState vfxState = new VfxRenderState(4);
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                vfxState, new OrthographicCamera(), 0, null, null);

        assertExtractedBlend(system, vfxState, false, false, BlendMode.ALPHA);
        assertExtractedBlend(system, vfxState, true, false, BlendMode.ADDITIVE_ALPHA);
        assertExtractedBlend(system, vfxState, false, true, BlendMode.PREMULT_ALPHA);
        assertExtractedBlend(system, vfxState, true, true, BlendMode.PREMULT_ALPHA);
    }

    @Test
    public void replacesLiveEffectAndReturnsPreviousEffectToItsPool() throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("effects"));
        temporaryFolder.newFile("effects/a.p");
        temporaryFolder.newFile("effects/b.p");
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                new AtlasRuntimeService(), effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(system).build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);

        ParticleEffectPool poolA = new ParticleEffectPool(new ParticleEffect(), 0, 4);
        ParticleEffectPool poolB = new ParticleEffectPool(new ParticleEffect(), 0, 4);
        ObjectMap<String, ParticleEffectPool> pools =
                field(system.particleAvailability(), "pools");
        pools.put("atlas-a|a.p", poolA);
        pools.put("atlas-b|b.p", poolB);

        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class).create(entity);
        emitter.effectPath = "a.p";
        emitter.atlasTag = "atlas-a";
        world.process();

        IntMap<ParticleEffectPool.PooledEffect> effects = field(system, "effects");
        ParticleEffectPool.PooledEffect liveA = effects.get(entity);
        Assert.assertNotNull(liveA);
        Assert.assertEquals(0, poolA.getFree());

        engine.api().entities().ofEntityId(entity).particles().setEffect("b.p", "atlas-b");
        emitter.playRequested = true;
        world.process();

        ParticleEffectPool.PooledEffect liveB = effects.get(entity);
        Assert.assertNotNull(liveB);
        Assert.assertNotSame(liveA, liveB);
        Assert.assertEquals(1, poolA.getFree());
        Assert.assertEquals(0, poolB.getFree());
        Assert.assertEquals("b.p", emitter.effectPath);
        Assert.assertEquals("atlas-b", emitter.atlasTag);
        Assert.assertFalse(emitter.playRequested);
    }

    @Test
    public void unavailableReplacementIsRejectedAndPreparedReplacementRecovers()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("recovery-effects"));
        temporaryFolder.newFile("recovery-effects/a.p");
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                new AtlasRuntimeService(), effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(system).build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);

        ParticleEffectPool poolA = new ParticleEffectPool(new ParticleEffect(), 0, 4);
        ParticleEffectPool poolB = new ParticleEffectPool(new ParticleEffect(), 0, 4);
        ObjectMap<String, ParticleEffectPool> pools =
                field(system.particleAvailability(), "pools");
        pools.put("atlas-a|a.p", poolA);

        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class)
                .create(entity);
        emitter.effectPath = "a.p";
        emitter.atlasTag = "atlas-a";
        world.process();

        IntMap<ParticleEffectPool.PooledEffect> effects = field(system, "effects");
        IntMap<String> effectPaths = field(system, "effectPaths");
        IntMap<String> effectAtlasTags = field(system, "effectAtlasTags");
        ParticleEffectPool.PooledEffect liveA = effects.get(entity);

        Assert.assertThrows(IllegalStateException.class, () ->
                engine.api().entities().ofEntityId(entity).particles()
                        .setEffect("b.p", "atlas-b"));
        world.process();
        world.process();

        Assert.assertSame(liveA, effects.get(entity));
        Assert.assertEquals(0, poolA.getFree());
        Assert.assertEquals("a.p", emitter.effectPath);
        Assert.assertEquals("atlas-a", emitter.atlasTag);
        Assert.assertEquals("a.p", effectPaths.get(entity));
        Assert.assertEquals("atlas-a", effectAtlasTags.get(entity));

        pools.put("atlas-b|b.p", poolB);
        engine.api().entities().ofEntityId(entity).particles()
                .setEffect("b.p", "atlas-b");
        world.process();

        ParticleEffectPool.PooledEffect liveB = effects.get(entity);
        Assert.assertNotNull(liveB);
        Assert.assertNotSame(liveA, liveB);
        Assert.assertEquals(1, poolA.getFree());
        Assert.assertEquals(0, poolB.getFree());
        Assert.assertEquals("b.p", effectPaths.get(entity));
        Assert.assertEquals("atlas-b", effectAtlasTags.get(entity));
    }

    @Test
    public void unavailableAtlasReplacementPreservesLiveEffectAndIdentity()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("atlas-effects"));
        temporaryFolder.newFile("atlas-effects/a.p");
        temporaryFolder.newFile("atlas-effects/b.p");
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                new AtlasRuntimeService(), effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(system).build());
        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);

        ParticleEffectPool poolA = new ParticleEffectPool(new ParticleEffect(), 0, 4);
        ObjectMap<String, ParticleEffectPool> pools =
                field(system.particleAvailability(), "pools");
        pools.put("atlas-a|a.p", poolA);

        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class)
                .create(entity);
        emitter.effectPath = "a.p";
        emitter.atlasTag = "atlas-a";
        world.process();

        IntMap<ParticleEffectPool.PooledEffect> effects = field(system, "effects");
        IntMap<String> effectPaths = field(system, "effectPaths");
        IntMap<String> effectAtlasTags = field(system, "effectAtlasTags");
        ParticleEffectPool.PooledEffect liveA = effects.get(entity);

        Assert.assertThrows(IllegalStateException.class, () ->
                engine.api().entities().ofEntityId(entity).particles()
                        .setEffect("b.p", "missing-atlas"));
        world.process();

        Assert.assertSame(liveA, effects.get(entity));
        Assert.assertEquals(0, poolA.getFree());
        Assert.assertEquals("a.p", emitter.effectPath);
        Assert.assertEquals("atlas-a", emitter.atlasTag);
        Assert.assertEquals("a.p", effectPaths.get(entity));
        Assert.assertEquals("atlas-a", effectAtlasTags.get(entity));
        Assert.assertNull(pools.get("missing-atlas|b.p"));
    }

    @Test
    public void frameSynchronizationDoesNotPrepareUndeclaredParticle() throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("undeclared-effects"));
        writeEffect(effectsRoot.child("a.p"));
        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", new TextureAtlas());
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(system).build());

        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class)
                .create(entity);
        emitter.effectPath = "a.p";
        emitter.atlasTag = "scene";

        world.process();
        world.process();

        Assert.assertFalse(system.particleAvailability().isPrepared("scene", "a.p"));
        Assert.assertNull(field(system, "effects", IntMap.class).get(entity));
        world.dispose();
    }

    @Test
    public void studioInvalidationAfterAtlasPublicationPreparesCurrentParticles() throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("authoring-effects"));
        writeEffect(effectsRoot.child("fire.p"));
        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", new TextureAtlas());
        RenderParticleSyncSystem system = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(system).build());

        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        ParticleEmitterComponent emitter = world.getMapper(ParticleEmitterComponent.class)
                .create(entity);
        emitter.effectPath = "fire.p";
        emitter.atlasTag = "scene";

        world.process();
        Assert.assertFalse(system.particleAvailability().isPrepared("scene", "fire.p"));
        system.invalidateAllEffects();
        Assert.assertTrue(system.particleAvailability().isPrepared("scene", "fire.p"));

        world.process();

        Assert.assertNotNull(field(system, "effects", IntMap.class).get(entity));
        world.dispose();
    }

    private static void writeEffect(FileHandle file) throws Exception {
        ParticleEffect source = new ParticleEffect();
        source.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static <T> T field(Object target, String name, Class<T> type) throws Exception {
        return type.cast(field(target, name));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void assertExtractedBlend(RenderParticleSyncSystem system,
                                             VfxRenderState vfxState,
                                             boolean additive,
                                             boolean premultipliedAlpha,
                                             BlendMode expected) throws Exception {
        ParticleEmitter emitter = new ParticleEmitter();
        emitter.setAdditive(additive);
        emitter.setPremultipliedAlpha(premultipliedAlpha);
        emitter.setCleansUpBlendFunction(false);
        emitter.setMaxParticleCount(1);
        emitter.getParticles()[0] = new ParticleEmitter.Particle(new Sprite());
        emitter.getActiveArray()[0] = true;

        int[] batchBlend = new int[2];
        Batch batch = (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class},
                (proxy, method, args) -> {
                    if ("setBlendFunction".equals(method.getName())) {
                        batchBlend[0] = (Integer) args[0];
                        batchBlend[1] = (Integer) args[1];
                    }
                    return null;
                });
        emitter.draw(batch);
        Assert.assertEquals(expected.srcFactor, batchBlend[0]);
        Assert.assertEquals(expected.dstFactor, batchBlend[1]);

        ParticleEffect effect = new ParticleEffect();
        effect.getEmitters().add(emitter);

        Method collectEffect = RenderParticleSyncSystem.class.getDeclaredMethod(
                "collectEffect",
                ParticleEffect.class,
                int.class,
                int.class,
                int.class,
                games.pixscape.runtime.component.ParticleOverridesComponent.class);
        collectEffect.setAccessible(true);

        vfxState.clearFrame();
        collectEffect.invoke(system, effect, 0, 0, 0, null);

        Assert.assertEquals(1, vfxState.activeCount);
        Assert.assertEquals(expected.id, vfxState.blend[0]);
    }

    private static final class CapturingParticleEffect extends ParticleEffect {
        float x;
        float y;

        @Override
        public void setPosition(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
}
