package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.system.FixtureIdAllocatorSystem;
import org.junit.Assert;
import org.junit.Test;

public class FixtureIdentityContractTest {
    @Test
    public void allocationIsMonotonicAndDeletionDoesNotRecycle() {
        SceneMetaRuntime meta = meta(10);
        FixtureIdAllocatorSystem allocator = new FixtureIdAllocatorSystem(meta);

        Assert.assertEquals(10, allocator.allocateNewFixtureId());
        Assert.assertEquals(11, allocator.allocateNewFixtureId());
        Assert.assertEquals(12, meta.nextFixtureId);
    }

    @Test
    public void allocationRefusesOverflow() {
        SceneMetaRuntime meta = meta(Integer.MAX_VALUE);
        FixtureIdAllocatorSystem allocator = new FixtureIdAllocatorSystem(meta);

        try {
            allocator.allocateNewFixtureId();
            Assert.fail("Expected fixture ID exhaustion");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("exhausted"));
            Assert.assertEquals(Integer.MAX_VALUE, meta.nextFixtureId);
        }
    }

    @Test
    public void validationRejectsHighWaterMarkBehindFixture() {
        SceneMetaRuntime meta = meta(4);
        World world = world(meta);
        addFixture(world, world.create(), 4);

        assertInvalid(world, meta, "must be greater");
    }

    @Test
    public void validationRejectsDuplicateAndNonPositiveFixtureIds() {
        SceneMetaRuntime meta = meta(5);
        World duplicateWorld = world(meta);
        addFixture(duplicateWorld, duplicateWorld.create(), 2);
        addFixture(duplicateWorld, duplicateWorld.create(), 2);
        assertInvalid(duplicateWorld, meta, "duplicate fixtureId");

        World zeroWorld = world(meta(5));
        addFixture(zeroWorld, zeroWorld.create(), 0);
        assertInvalid(zeroWorld, meta(5), "strictly positive");
    }

    @Test
    public void validationRejectsMissingSpatialFixtureReference() {
        SceneMetaRuntime meta = meta(10);
        World world = world(meta);
        int body = world.create();
        SpatialBlockData block = new SpatialBlockData();
        block.id = 7;
        block.physicsCollision = true;
        block.fixtureId = 8;
        world.getMapper(SpatialBlocksComponent.class).create(body).blocks.add(block);

        assertInvalid(world, meta, "missing from its body");
    }

    private static SceneMetaRuntime meta(int next) {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.name = "fixture-contract";
        meta.nextFixtureId = next;
        return meta;
    }

    private static World world(SceneMetaRuntime meta) {
        return new World(new WorldConfiguration().setSystem(new FixtureIdAllocatorSystem(meta)));
    }

    private static void addFixture(World world, int body, int id) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).create(body);
        FixtureDefData fixture = new FixtureDefData();
        fixture.fixtureId = id;
        fixtures.fixtures.add(fixture);
    }

    private static void assertInvalid(World world, SceneMetaRuntime meta, String message) {
        try {
            FixtureIdentityValidator.validate(world, meta, "test-scene");
            Assert.fail("Expected fixture identity validation to fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }
}
