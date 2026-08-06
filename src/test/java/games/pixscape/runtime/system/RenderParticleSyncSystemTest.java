package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.api.ParticleFacade;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class RenderParticleSyncSystemTest {

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
