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
    public void linkedGeometryIsPreparedAtomicallyAndAltitudeDoesNotRecompile() {
        World world = new World(new WorldConfiguration());
        try {
            int actor = world.create();
            world.getMapper(TransformComponent.class).create(actor);
            world.getMapper(PhysicsBodyComponent.class).create(actor);
            SpatialFootprintComponent footprint = world.getMapper(SpatialFootprintComponent.class).create(actor);
            footprint.radiusPx = 30;
            footprint.localOffsetXPx = 12;
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(actor);
            PhysicsShapeData source = PhysicsService.createDefaultShape(1);
            source.useSpatialFootprint = true;
            source.geometry = null;
            source.sensor = true;
            shapes.shapes.add(source);
            PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class).create(actor);
            PhysicsService.publishPreparedCandidate(shapes, compiled, PhysicsService.prepareBodyCandidate(world, actor, shapes.shapes, 100));
            int generation = compiled.generation;
            Assert.assertEquals(.3f, compiled.fixtures.first().radius, .00001f);
            SpatialFootprintComponent candidate = footprint.copy();
            candidate.radiusPx = 55;
            PhysicsService.setSpatialFootprint(world, actor, candidate, 100);
            Assert.assertEquals(generation + 1, compiled.generation);
            Assert.assertEquals(.55f, compiled.fixtures.first().radius, .00001f);
            Assert.assertEquals(.12f, compiled.fixtures.first().offsetX, .00001f);
            Assert.assertNull(shapes.shapes.first().geometry);
            Assert.assertTrue(compiled.fixtures.first().sensor);
            candidate.radiusPx = Float.NaN;
            Assert.assertThrows(IllegalArgumentException.class, () -> PhysicsService.setSpatialFootprint(world, actor, candidate, 100));
            Assert.assertEquals(55, footprint.radiusPx, 0);
            Assert.assertEquals(generation + 1, compiled.generation);
            candidate.radiusPx = 55;
            candidate.altitude = 28;
            PhysicsService.setSpatialFootprint(world, actor, candidate, 100);
            Assert.assertEquals(generation + 1, compiled.generation);
            Assert.assertEquals(28, footprint.altitude, 0);
            Assert.assertThrows(IllegalStateException.class, () -> PhysicsService.removeSpatialFootprint(world, actor));
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
