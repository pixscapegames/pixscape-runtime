package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.render.JointDirtyBits;
import org.junit.Test;

import static org.junit.Assert.*;

public class JointIntegrityPublicationTest {
    @Test
    public void orphanInsertionFailsEvenWithoutNativePhysics() {
        try (Fixture f = new Fixture()) {
            f.world.getMapper(PhysicsBodyComponent.class).remove(f.a);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, f.world::process);
            assertTrue(failure.getMessage(), failure.getMessage().contains("aEid"));
        }
    }

    @Test
    public void bodyComponentRemovalCannotLeaveSilentJoint() {
        try (Fixture f = new Fixture()) {
            f.world.process();
            f.world.getMapper(PhysicsBodyComponent.class).remove(f.a);
            assertThrows(IllegalArgumentException.class, f.world::process);
        }
    }

    @Test
    public void bodyDeletionCannotLeaveSilentJoint() {
        try (Fixture f = new Fixture()) {
            f.world.process();
            f.world.delete(f.b);
            assertThrows(IllegalArgumentException.class, f.world::process);
        }
    }

    @Test
    public void retargetingRejectsNonBodyAtDirtyPublication() {
        try (Fixture f = new Fixture()) {
            f.world.process();
            f.world.getMapper(PhysicsJointComponent.class).get(f.joint).bEid = f.world.create();
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> f.dirty.joint(f.joint, JointDirtyBits.ALL));
            assertTrue(failure.getMessage(), failure.getMessage().contains("bEid"));
        }
    }

    @Test
    public void deletingJointAndBodyTogetherRemainsValid() {
        try (Fixture f = new Fixture()) {
            f.world.process();
            f.world.delete(f.a);
            f.world.delete(f.joint);
            f.world.process();
            assertFalse(f.world.getMapper(PhysicsJointComponent.class).has(f.joint));
            assertTrue(f.world.getMapper(PhysicsBodyComponent.class).has(f.b));
        }
    }

    private static final class Fixture implements AutoCloseable {
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        final World world = new World(new WorldConfigurationBuilder()
                .with(dirty, new Box2dSyncSystem(null)).build());
        final int a = body(), b = body(), joint = world.create();

        Fixture() {
            PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(joint);
            base.aEid = a;
            base.bEid = b;
            world.getMapper(PhysicsDistanceJointComponent.class).create(joint);
        }

        int body() {
            int e = world.create();
            world.getMapper(PhysicsBodyComponent.class).create(e);
            return e;
        }

        @Override
        public void close() {
            world.dispose();
        }
    }
}
