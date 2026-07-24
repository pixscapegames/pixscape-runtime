package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsSpatialFootprintSyncSystemTest {
    @Test
    public void ppmChangeReprojectsWithoutChangingPhysicsGeneration() {
        PhysicsSpatialFootprintSyncSystem sync =
                new PhysicsSpatialFootprintSyncSystem(100f);
        ProjectionCounter counter = new ProjectionCounter();
        sync.setTestObserver(counter);
        World world = new World(new WorldConfigurationBuilder().with(sync).build());
        int entityId = world.create();
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        compiled.fixtures.add(circle(0.5f, 0f));
        compiled.generation = 1;
        compiled.valid = true;

        world.process();
        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId);
        Assert.assertEquals(50f, footprint.radiusPx, 0f);
        Assert.assertEquals(1, counter.count);

        sync.setPixelsPerMeter(50f);
        world.process();

        Assert.assertSame(footprint,
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId));
        Assert.assertEquals(25f, footprint.radiusPx, 0f);
        Assert.assertEquals(1, compiled.generation);
        Assert.assertEquals(1, footprint.physicsGeneration);
        Assert.assertEquals(2, counter.count);

        for (int frame = 0; frame < 1000; frame++) {
            world.process();
        }

        Assert.assertSame(footprint,
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId));
        Assert.assertEquals(2, counter.count);
        world.dispose();
    }

    @Test
    public void ppmSetterRejectsInvalidValuesAndIgnoresIdenticalValue() {
        PhysicsSpatialFootprintSyncSystem sync =
                new PhysicsSpatialFootprintSyncSystem(100f);
        sync.setPixelsPerMeter(100f);
        assertInvalidPpm(sync, 0f);
        assertInvalidPpm(sync, -1f);
        assertInvalidPpm(sync, Float.NaN);
        assertInvalidPpm(sync, Float.POSITIVE_INFINITY);
        assertInvalidPpm(sync, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void interruptedPpmPassRemainsRequestedUntilSuccessful() {
        PhysicsSpatialFootprintSyncSystem sync =
                new PhysicsSpatialFootprintSyncSystem(100f);
        World world = new World(new WorldConfigurationBuilder().with(sync).build());
        int entityId = world.create();
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        compiled.fixtures.add(circle(0.5f, 0f));
        compiled.generation = 1;
        compiled.valid = true;
        world.process();
        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId);

        sync.setPixelsPerMeter(50f);
        sync.setTestObserver(() -> {
            throw new IllegalStateException("injected interrupted projection pass");
        });
        try {
            world.process();
            Assert.fail("The injected projection failure must escape.");
        } catch (IllegalStateException expected) {
            Assert.assertEquals(
                    "injected interrupted projection pass", expected.getMessage());
        }

        ProjectionCounter counter = new ProjectionCounter();
        sync.setTestObserver(counter);
        world.process();

        Assert.assertEquals(25f, footprint.radiusPx, 0f);
        Assert.assertEquals(1, counter.count);
        world.dispose();
    }

    @Test
    public void projectsGenerationInSameFrameAndSkipsOneThousandStableFrames() {
        PhysicsSpatialFootprintSyncSystem sync =
                new PhysicsSpatialFootprintSyncSystem(100f);
        ProjectionCounter counter = new ProjectionCounter();
        sync.setTestObserver(counter);
        World world = new World(new WorldConfigurationBuilder().with(sync).build());
        int entityId = world.create();
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        compiled.fixtures.add(circle(0.5f, 0.25f));
        compiled.generation = 1;
        compiled.valid = true;

        world.process();

        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId);
        Assert.assertTrue(footprint.valid);
        Assert.assertEquals(50f, footprint.radiusPx, 0f);
        Assert.assertEquals(25f, footprint.localOffsetXPx, 0f);
        Assert.assertEquals(1, footprint.physicsGeneration);
        Assert.assertEquals(1, counter.count);

        for (int frame = 0; frame < 1000; frame++) {
            world.process();
        }

        Assert.assertSame(footprint,
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId));
        Assert.assertEquals(1, counter.count);

        compiled.fixtures.first().radius = 0.75f;
        compiled.generation = 2;
        world.process();

        Assert.assertEquals(75f, footprint.radiusPx, 0f);
        Assert.assertEquals(2, footprint.physicsGeneration);
        Assert.assertEquals(2, counter.count);
        world.dispose();
    }

    @Test
    public void invalidOrRemovedPhysicsCacheInvalidatesFootprint() {
        PhysicsSpatialFootprintSyncSystem sync =
                new PhysicsSpatialFootprintSyncSystem(100f);
        World world = new World(new WorldConfigurationBuilder().with(sync).build());
        int entityId = world.create();
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(entityId);
        compiled.fixtures.add(circle(0.5f, 0f));
        compiled.generation = 1;
        compiled.valid = true;
        world.process();
        SpatialPhysicsFootprintComponent footprint =
                world.getMapper(SpatialPhysicsFootprintComponent.class).get(entityId);
        Assert.assertTrue(footprint.valid);

        compiled.valid = false;
        compiled.generation = 2;
        world.process();
        Assert.assertFalse(footprint.valid);
        Assert.assertEquals(2, footprint.physicsGeneration);

        compiled.valid = true;
        compiled.generation = 3;
        world.process();
        Assert.assertTrue(footprint.valid);
        world.getMapper(PhysicsCompiledFixturesComponent.class).remove(entityId);
        world.process();
        Assert.assertFalse(footprint.valid);
        world.dispose();
    }

    private static CompiledFixtureData circle(float radius, float offsetX) {
        CompiledFixtureData circle = new CompiledFixtureData();
        circle.shapeType = CompiledFixtureData.SHAPE_CIRCLE;
        circle.radius = radius;
        circle.offsetX = offsetX;
        return circle;
    }

    private static void assertInvalidPpm(
            PhysicsSpatialFootprintSyncSystem sync, float value) {
        try {
            sync.setPixelsPerMeter(value);
            Assert.fail("Invalid pixelsPerMeter must be rejected: " + value);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("pixelsPerMeter"));
        }
    }

    private static final class ProjectionCounter
            implements PhysicsSpatialFootprintSyncSystem.TestObserver {
        int count;

        @Override
        public void onProjection() {
            count++;
        }
    }
}
