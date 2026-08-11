package games.pixscape.runtime.component.physics;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsBodyComponentTest {
    @Test
    public void componentHasNoEnabledFieldAndPoolResetRestoresDefaults()
            throws Exception {
        try {
            PhysicsBodyComponent.class.getDeclaredField("enabled");
            Assert.fail("PhysicsBodyComponent.enabled must not exist.");
        } catch (NoSuchFieldException expected) {
            // expected
        }

        World world = new World(new WorldConfiguration());
        ComponentMapper<PhysicsBodyComponent> bodies =
                world.getMapper(PhysicsBodyComponent.class);
        int firstEntity = world.create();
        PhysicsBodyComponent first = bodies.create(firstEntity);
        first.type = PhysicsBodyComponent.STATIC;
        first.fixedRotation = true;
        first.bullet = true;
        first.allowSleep = false;
        first.awake = false;
        first.gravityScale = 3f;
        first.linearDamping = 4f;
        first.angularDamping = 5f;
        world.process();
        bodies.remove(firstEntity);
        world.process();

        PhysicsBodyComponent reset = bodies.create(world.create());
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC, reset.type);
        Assert.assertFalse(reset.fixedRotation);
        Assert.assertFalse(reset.bullet);
        Assert.assertTrue(reset.allowSleep);
        Assert.assertTrue(reset.awake);
        Assert.assertEquals(1f, reset.gravityScale, 0f);
        Assert.assertEquals(0f, reset.linearDamping, 0f);
        Assert.assertEquals(0f, reset.angularDamping, 0f);
        world.dispose();
    }
}
