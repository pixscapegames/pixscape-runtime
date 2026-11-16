package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialActorGeometry;
import org.junit.Assert;
import org.junit.Test;

public class SpatialFootprintIndependenceTest {
    @Test
    public void spatialMutationAndRemovalNeverRecompileOrChangeIndependentCollision() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            world.getMapper(TransformComponent.class).create(actor);
            world.getMapper(PhysicsBodyComponent.class).create(actor);
            SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).create(actor);
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(actor);
            PhysicsShapeData source = PhysicsService.createDefaultShape(1);
            source.geometry.halfWidth = 2.5f;
            source.sensor = true;
            shapes.shapes.add(source);
            PhysicsShapeData before = source.copy();
            PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class).create(actor);
            PhysicsService.publishPreparedCandidate(shapes, compiled, PhysicsService.prepareBodyCandidate(shapes.shapes));
            int generation = compiled.generation;
            footprint.radiusPx = 55;
            footprint.localOffsetXPx = 90;
            footprint.localOffsetYPx = -14;
            footprint.altitude = 28;
            footprint.validate();
            world.process();
            Assert.assertEquals(generation, compiled.generation);
            Assert.assertTrue(before.contentEquals(shapes.shapes.first()));
            new PhysicsService(world, null).removePhysics(actor);
            Assert.assertSame(footprint, world.getMapper(SpatialFootprintComponent.class).get(actor));
            Assert.assertEquals(55, footprint.radiusPx, 0);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void visualScaleAndDistinctCollisionDoNotChangeOwnerGeometry() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            TransformComponent transform = world.getMapper(TransformComponent.class).create(actor);
            transform.x = 10;
            transform.y = 20;
            SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).create(actor);
            footprint.localOffsetXPx = 4;
            footprint.radiusPx = 11;
            SpatialActorGeometry.Footprint output = new SpatialActorGeometry.Footprint();
            Assert.assertTrue(SpatialActorPose.resolve(actor, transform, footprint, null, output));
            float x = output.footX, y = output.footY;
            transform.scaleX = -4;
            transform.scaleY = .2f;
            transform.originX = 800;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(actor);
            shapes.shapes.add(PhysicsService.createDefaultShape(1));
            PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class).create(actor);
            PhysicsService.publishPreparedCandidate(shapes, compiled, PhysicsService.prepareBodyCandidate(shapes.shapes));
            shapes.shapes.first().geometry.halfWidth = 50;
            PhysicsService.publishPreparedCandidate(shapes, compiled, PhysicsService.prepareBodyCandidate(shapes.shapes));
            Assert.assertTrue(SpatialActorPose.resolve(actor, transform, footprint, null, output));
            Assert.assertEquals(x, output.footX, 0);
            Assert.assertEquals(y, output.footY, 0);
            Assert.assertEquals(11, footprint.radiusPx, 0);
            world.process();
            world.delete(actor);
            world.process();
            int recycled = world.create();
            Assert.assertEquals(actor, recycled);
            Assert.assertNull(world.getMapper(SpatialFootprintComponent.class).getSafe(recycled, null));
            SpatialFootprintComponent fresh = world.getMapper(SpatialFootprintComponent.class).create(recycled);
            Assert.assertEquals(0, fresh.localOffsetXPx, 0);
            Assert.assertEquals(8, fresh.radiusPx, 0);
        } finally {
            world.dispose();
        }
    }
}
