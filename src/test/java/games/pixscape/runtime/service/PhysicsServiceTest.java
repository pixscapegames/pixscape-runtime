package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsServiceTest {

    @Test
    public void removingPhysicsDeletesRuntimeBodyFixturesAndJoints() {
        // Test: la suppression d'une entité physique supprime son body, ses shapes et ses joints.
        // Arrange
        GdxNativesLoader.load();
        Box2dWorldService box2d = new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        World world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
        PhysicsService physics = new PhysicsService(world, box2d);

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
        PhysicsService physics = new PhysicsService(world, box2d);

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
