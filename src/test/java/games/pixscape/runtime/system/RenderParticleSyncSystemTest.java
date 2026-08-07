package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class RenderParticleSyncSystemTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        ObjectMap<String, ParticleEffectPool> pools = field(system, "effectPools");
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
        world.process();

        ParticleEffectPool.PooledEffect liveB = effects.get(entity);
        Assert.assertNotNull(liveB);
        Assert.assertNotSame(liveA, liveB);
        Assert.assertEquals(1, poolA.getFree());
        Assert.assertEquals(0, poolB.getFree());
        Assert.assertEquals("b.p", emitter.effectPath);
        Assert.assertEquals("atlas-b", emitter.atlasTag);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
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
