package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class IdentityRegistryTest {

    @Test
    public void differentRegistriesCannotBindTheSameWorld() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry first = new IdentityRegistry();
        IdentityRegistry second = new IdentityRegistry();

        first.bind(world);
        try {
            second.bind(world);
            Assert.fail("Expected a second registry for the same World to be rejected.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("different IdentityRegistry"));
        } finally {
            first.bind(null);
            world.dispose();
        }
    }

    @Test
    public void bindingTheSameRegistryToTheSameWorldIsIdempotent() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry registry = new IdentityRegistry();

        registry.bind(world);
        registry.bind(world);

        registry.bind(null);
        world.dispose();
    }

    @Test
    public void rebindingReleasesThePreviousWorld() {
        World firstWorld = new World(new WorldConfiguration());
        World secondWorld = new World(new WorldConfiguration());
        IdentityRegistry first = new IdentityRegistry();
        IdentityRegistry second = new IdentityRegistry();

        first.bind(firstWorld);
        first.bind(secondWorld);
        second.bind(firstWorld);

        second.bind(null);
        first.bind(null);
        firstWorld.dispose();
        secondWorld.dispose();
    }
}
