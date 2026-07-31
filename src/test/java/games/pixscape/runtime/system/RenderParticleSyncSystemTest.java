package games.pixscape.runtime.system;

import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.api.ParticleFacade;
import games.pixscape.runtime.particle.ParticleEffect;
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
