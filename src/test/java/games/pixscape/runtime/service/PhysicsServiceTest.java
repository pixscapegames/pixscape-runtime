package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsServiceTest {

    @Test
    public void rebuildPreparedBodyCachesNormalizesBodyWithoutShapes() {
        World world = new World();
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        world.getMapper(PhysicsBodyComponent.class).create(entityId).enabled = true;

        PhysicsService.rebuildPreparedBodyCaches(world);

        PhysicsShapesComponent shapes =
                world.getMapper(PhysicsShapesComponent.class).get(entityId);
        PhysicsCompiledFixturesComponent compiled =
                world.getMapper(PhysicsCompiledFixturesComponent.class).get(entityId);
        Assert.assertNotNull(shapes);
        Assert.assertNotNull(shapes.shapes);
        Assert.assertEquals(0, shapes.shapes.size);
        Assert.assertNotNull(compiled);
        Assert.assertTrue(compiled.valid);
        Assert.assertNotNull(compiled.fixtures);
        Assert.assertEquals(0, compiled.fixtures.size);
    }

    @Test
    public void rebuildPreparedBodyCachesPublishesNothingWhenAnyBodyIsInvalid() {
        World world = new World();
        int bodyA = world.create();
        world.getMapper(PhysicsBodyComponent.class).create(bodyA);
        PhysicsCompiledFixturesComponent sentinel =
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(bodyA);
        CompiledFixtureData sentinelFixture = new CompiledFixtureData();
        sentinel.fixtures.add(sentinelFixture);
        sentinel.generation = 7;
        sentinel.valid = true;

        int bodyB = world.create();
        world.getMapper(PhysicsBodyComponent.class).create(bodyB);
        PhysicsShapesComponent invalidShapes =
                world.getMapper(PhysicsShapesComponent.class).create(bodyB);
        PhysicsShapeData invalid = new PhysicsShapeData();
        invalid.physicsShapeId = 1;
        invalid.directGeometry = new PhysicsDirectGeometryData();
        invalid.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        invalid.directGeometry.polygonVertices = new float[]{0f, 0f, 1f, 0f};
        invalid.directGeometry.polygonVertexCount = 2;
        invalidShapes.shapes.add(invalid);

        try {
            PhysicsService.rebuildPreparedBodyCaches(world);
            Assert.fail("An invalid body must reject the complete cache rebuild.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("PhysicsShapeData"));
        }

        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(bodyA));
        Assert.assertSame(sentinel, world.getMapper(
                PhysicsCompiledFixturesComponent.class).get(bodyA));
        Assert.assertSame(sentinelFixture, sentinel.fixtures.first());
        Assert.assertEquals(1, sentinel.fixtures.size);
        Assert.assertEquals(7, sentinel.generation);
        Assert.assertTrue(sentinel.valid);
        Assert.assertFalse(
                world.getMapper(PhysicsCompiledFixturesComponent.class).has(bodyB));
    }

    @Test
    public void removingPhysicsDeletesRuntimeBodyFixturesAndJoints() {
        // Test: deleting a physics entity removes its body, shapes, and joints.
        // Arrange
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        World world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
        PhysicsService physics = new PhysicsService(world, box2d, new games.pixscape.runtime.loading.SceneMetaRuntime());

        int bodyA = world.create();
        TransformComponent tA = world.getMapper(TransformComponent.class).create(bodyA);
        tA.x = 0f;
        tA.y = 0f;
        physics.ensurePhysics(bodyA);

        int bodyB = world.create();
        TransformComponent tB = world.getMapper(TransformComponent.class).create(bodyB);
        tB.x = 100f;
        tB.y = 0f;
        physics.ensurePhysics(bodyB);

        int jointEid = physics.createDistanceJoint(bodyA, bodyB);

        world.process();

        Assert.assertEquals("Box2D should have two bodies", 2, box2d.world.getBodyCount());
        Assert.assertEquals("Box2D should have one joint", 1, box2d.world.getJointCount());

        // Act
        physics.removePhysics(bodyA);
        world.process();

        // Assert
        Assert.assertEquals("Box2D should have one body after removal", 1, box2d.world.getBodyCount());
        Assert.assertEquals("Box2D should have zero joints after removal", 0, box2d.world.getJointCount());
        Assert.assertFalse("Body A should no longer have physics components", physics.hasPhysics(bodyA));
        Assert.assertFalse("Joint entity should be deleted", world.getEntityManager().isActive(jointEid));
    }

    @Test
    public void movingBodyInAuthoringRefreshesDistanceJointLength() {
        // Arrange
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        World world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
        PhysicsService physics = new PhysicsService(world, box2d, new games.pixscape.runtime.loading.SceneMetaRuntime());

        int bodyA = world.create();
        TransformComponent tA = world.getMapper(TransformComponent.class).create(bodyA);
        tA.x = 0f;
        tA.y = 0f;
        physics.ensurePhysics(bodyA);

        int bodyB = world.create();
        TransformComponent tB = world.getMapper(TransformComponent.class).create(bodyB);
        tB.x = 100f;
        tB.y = 0f;
        physics.ensurePhysics(bodyB);

        int jointEid = physics.createDistanceJoint(bodyA, bodyB);
        world.process();

        var dist = world.getMapper(games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent.class).get(jointEid);
        Assert.assertEquals(1f, dist.lengthM, 1e-4f);

        // Act: authoring move without physics dirty/rebuild
        tB.x = 200f;
        world.process();

        // Assert: distance joint target length follows moved transforms
        Assert.assertEquals(2f, dist.lengthM, 1e-4f);
    }

}
