package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

public class IdentityRegistryTest {

    @Test
    public void differentRegistriesCannotBindTheSameWorld() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry first = new IdentityRegistry();
        IdentityRegistry second = new IdentityRegistry();

        first.bind(world, new SceneMetaRuntime());
        try {
            second.bind(world, new SceneMetaRuntime());
            Assert.fail("Expected a second registry for the same World to be rejected.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("different IdentityRegistry"));
        } finally {
            first.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void bindingTheSameRegistryToTheSameWorldIsIdempotent() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry registry = new IdentityRegistry();

        SceneMetaRuntime meta = new SceneMetaRuntime();
        registry.bind(world, meta);
        registry.bind(world, meta);

        registry.bind(null, null);
        world.dispose();
    }

    @Test
    public void rebindingReleasesThePreviousWorld() {
        World firstWorld = new World(new WorldConfiguration());
        World secondWorld = new World(new WorldConfiguration());
        IdentityRegistry first = new IdentityRegistry();
        IdentityRegistry second = new IdentityRegistry();

        first.bind(firstWorld, new SceneMetaRuntime());
        first.bind(secondWorld, new SceneMetaRuntime());
        second.bind(firstWorld, new SceneMetaRuntime());

        second.bind(null, null);
        first.bind(null, null);
        firstWorld.dispose();
        secondWorld.dispose();
    }

    @Test
    public void allocationUsesAndAdvancesSceneHighWaterWithoutRecycling() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 7;
        IdentityRegistry registry = new IdentityRegistry();
        registry.bind(world, meta);

        Assert.assertEquals(7, registry.allocateStableId());
        Assert.assertEquals(8, meta.nextEntityStableId);
        Assert.assertEquals(8, registry.allocateStableId());
        Assert.assertEquals(9, meta.nextEntityStableId);

        registry.bind(null, null);
        world.dispose();
    }

    @Test
    public void rebuildAndDeletionNeverRewindOrRecycleHighWater() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        IdentityRegistry registry = new IdentityRegistry();
        registry.bind(world, meta);
        int entity = world.create();
        Assert.assertEquals(1, registry.ensureStableId(entity));
        registry.removeIdentity(entity);
        registry.rebuild();
        Assert.assertEquals(2, meta.nextEntityStableId);
        Assert.assertEquals(2, registry.allocateStableId());
        registry.bind(null, null);
        world.dispose();
    }

    @Test
    public void restoredIdAtOrAboveHighWaterIsRejected() {
        World world = new World(new WorldConfiguration());
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 5;
        IdentityRegistry registry = new IdentityRegistry();
        registry.bind(world, meta);
        int entity = world.create();
        Assert.assertThrows(
                IllegalArgumentException.class,
                () -> registry.setStableId(entity, 5));
        Assert.assertEquals(5, meta.nextEntityStableId);
        registry.bind(null, null);
        world.dispose();
    }

    @Test
    public void rebindingSameWorldUsesOnlyExplicitActiveSceneMetadata() {
        World world = new World(new WorldConfiguration());
        IdentityRegistry registry = new IdentityRegistry();
        SceneMetaRuntime first = new SceneMetaRuntime();
        SceneMetaRuntime second = new SceneMetaRuntime();
        first.nextEntityStableId = 11;
        second.nextEntityStableId = 31;
        registry.bind(world, first);
        Assert.assertEquals(11, registry.allocateStableId());
        registry.bind(world, second);
        Assert.assertEquals(31, registry.allocateStableId());
        Assert.assertEquals(12, first.nextEntityStableId);
        Assert.assertEquals(32, second.nextEntityStableId);
        registry.bind(null, null);
        world.dispose();
    }
}
