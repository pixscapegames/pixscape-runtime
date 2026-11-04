package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.render.JointDirtyBits;
import org.junit.Test;
import static org.junit.Assert.*;

public class JointRemovalIndexTest {
    @Test public void sameFrameRetargetingAndGhostDeletionKeepOnlyCurrentRelations() {
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        Box2dSyncSystem sync = new Box2dSyncSystem(null);
        World world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
        try {
            int a = world.create(), b = world.create(), c = world.create(), joint = world.create();
            var base = world.getMapper(PhysicsJointComponent.class).create(joint);
            base.aEid = a; base.bEid = b;
            dirty.joint(joint, JointDirtyBits.ALL);
            assertArrayEquals(new int[]{joint}, closure(sync, a).toArray());
            base.aEid = c;
            dirty.joint(joint, JointDirtyBits.ALL); // already dirty: still publishes new endpoints
            assertEquals(0, closure(sync, a).size);
            assertArrayEquals(new int[]{joint}, closure(sync, c).toArray());
            world.delete(joint); // never inserted into the joint subscription
            world.process();
            int replacement = world.create();
            assertEquals(joint, replacement);
            assertEquals(0, closure(sync, c).size);
            assertEquals(0, closure(sync, b).size);
        } finally { world.dispose(); }
    }

    @Test public void gearRetargetAndComponentRemovalUpdateInverseDependenciesWithoutNativePhysics() {
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        Box2dSyncSystem sync = new Box2dSyncSystem(null);
        World world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
        try {
            int first = world.create(), second = world.create(), third = world.create(), gear = world.create();
            for (int entity : new int[]{first, second, third, gear}) {
                world.getMapper(PhysicsJointComponent.class).create(entity);
            }
            var data = world.getMapper(PhysicsGearJointComponent.class).create(gear);
            data.joint1Eid = first; data.joint2Eid = second;
            dirty.joint(gear, JointDirtyBits.ALL);
            assertArrayEquals(new int[]{gear, first}, closure(sync, first).toArray());
            data.joint1Eid = third;
            dirty.joint(gear, JointDirtyBits.ALL);
            assertArrayEquals(new int[]{first}, closure(sync, first).toArray());
            assertArrayEquals(new int[]{gear, third}, closure(sync, third).toArray());
            world.process();
            world.getMapper(PhysicsGearJointComponent.class).remove(gear);
            world.process();
            assertArrayEquals(new int[]{third}, closure(sync, third).toArray());
            assertArrayEquals(new int[]{second}, closure(sync, second).toArray());
        } finally { world.dispose(); }
    }

    @Test public void sharedAndCyclicDependenciesAreDeduplicatedAndDoNotVisitOtherSources() {
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        Box2dSyncSystem sync = new Box2dSyncSystem(null);
        World world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
        try {
            int a = world.create(), b = world.create(), gear = world.create();
            for (int entity : new int[]{a, b, gear}) world.getMapper(PhysicsJointComponent.class).create(entity);
            var links = world.getMapper(PhysicsGearJointComponent.class).create(gear);
            links.joint1Eid = a; links.joint2Eid = a;
            dirty.joint(gear, JointDirtyBits.ALL);
            links = world.getMapper(PhysicsGearJointComponent.class).create(a);
            links.joint1Eid = gear; links.joint2Eid = b;
            dirty.joint(a, JointDirtyBits.ALL);
            IntArray found = closure(sync, a);
            assertEquals(2, found.size);
            assertTrue(found.contains(a)); assertTrue(found.contains(gear)); assertFalse(found.contains(b));
        } finally { world.dispose(); }
    }

    private static IntArray closure(Box2dSyncSystem sync, int seed) {
        return sync.collectJointsForRemoval(IntArray.with(seed), null);
    }
}
