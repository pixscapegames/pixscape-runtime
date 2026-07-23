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
    public void allocatorStartsUnboundAndRejectsAllocation() {
        FixtureIdAllocatorSystem allocator = new FixtureIdAllocatorSystem();

        Assert.assertFalse(allocator.isBound());
        Assert.assertNull(allocator.sceneMeta());
        try {
            allocator.allocateNewFixtureId();
            Assert.fail("Expected allocation without an active scene to fail");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(
                    "Cannot allocate fixture ID: no active scene metadata is bound",
                    expected.getMessage());
        }
        Assert.assertFalse(allocator.isBound());
    }

    @Test
    public void bindRejectsInvalidMetadataWithoutReplacingTheActiveScene() {
        SceneMetaRuntime active = meta(7);
        FixtureIdAllocatorSystem allocator = new FixtureIdAllocatorSystem(active);
        SceneMetaRuntime invalid = meta(0);

        try {
            allocator.bind(null);
            Assert.fail("Expected null scene metadata to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("metadata is null"));
        }
        Assert.assertSame(active, allocator.sceneMeta());

        try {
            allocator.bind(invalid);
            Assert.fail("Expected invalid high-water mark to fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("strictly positive"));
        }

        Assert.assertSame(active, allocator.sceneMeta());
        Assert.assertEquals(7, allocator.allocateNewFixtureId());
        Assert.assertEquals(8, active.nextFixtureId);
        Assert.assertEquals(0, invalid.nextFixtureId);
    }

    @Test
    public void allocationIsMonotonicAndDeletionDoesNotRecycle() {
        SceneMetaRuntime meta = meta(10);
        FixtureIdAllocatorSystem allocator = new FixtureIdAllocatorSystem(meta);

        Assert.assertEquals(10, allocator.allocateNewFixtureId());
        Assert.assertEquals(11, allocator.allocateNewFixtureId());
        Assert.assertEquals(12, meta.nextFixtureId);
    }

    @Test
    public void rebindingAcrossScenesKeepsIndependentHighWaterMarks() {
        SceneMetaRuntime sceneA = meta(10);
        sceneA.name = "A";
        SceneMetaRuntime sceneB = meta(100);
        sceneB.name = "B";
        FixtureIdAllocatorSystem allocator = new FixtureIdAllocatorSystem();

        allocator.bind(sceneA);
        Assert.assertEquals(10, allocator.allocateNewFixtureId());
        Assert.assertEquals(11, sceneA.nextFixtureId);

        allocator.bind(sceneB);
        Assert.assertEquals(100, allocator.allocateNewFixtureId());
        Assert.assertEquals(101, sceneB.nextFixtureId);

        allocator.bind(sceneA);
        Assert.assertEquals(11, allocator.allocateNewFixtureId());
        Assert.assertEquals(12, sceneA.nextFixtureId);
        Assert.assertEquals(101, sceneB.nextFixtureId);
    }

    @Test
    public void boundAllocatorWorksBeforeWorldProcess() {
        SceneMetaRuntime meta = meta(5);
        World world = world(meta);

        FixtureIdAllocatorSystem allocator = world.getSystem(FixtureIdAllocatorSystem.class);
        Assert.assertTrue(allocator.isBound());
        Assert.assertEquals(5, allocator.allocateNewFixtureId());
        Assert.assertEquals(6, meta.nextFixtureId);
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
