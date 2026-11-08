package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Joint;
import com.badlogic.gdx.physics.box2d.joints.GearJoint;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native ownership must survive pooled components and delayed subscription observation. */
public class Box2dNativeOwnershipTest {
    @Test public void removedBodyThenRestoredBeforeNextFrameHasOneNativeOwner() {
        try (Harness h = new Harness()) {
            int e = h.body(); h.world.process();
            h.physics.removePhysics(e);
            h.bodyComponents(e);
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(1, h.box.world.getBodyCount());
            assertNotNull(h.nativeBody(e));
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void compiledComponentReplacementWithDirtyPreservesSingleOwner() {
        try (Harness h = new Harness()) {
            int e = h.body(); h.world.process();
            h.world.getMapper(PhysicsCompiledFixturesComponent.class).remove(e);
            h.publish(e);
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(1, h.box.world.getBodyCount());
            assertNotNull(h.nativeBody(e));
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void invalidExistingCacheCanRetryAndThenDeleteWithoutLeak() {
        try (Harness h = new Harness()) {
            int e = h.body(); h.world.process();
            Body original = h.nativeBody(e);
            h.world.getMapper(PhysicsCompiledFixturesComponent.class).get(e).valid = false;
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            assertThrows(IllegalStateException.class, () -> h.world.process());
            assertSame(original, h.nativeBody(e));
            assertEquals(1, h.box.world.getBodyCount());
            h.publish(e);
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(1, h.box.world.getBodyCount());
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void preparedBodyDeletedBeforeRuntimeSubscriptionObservationIsDestroyed() {
        try (Harness h = new Harness()) {
            h.sync.setEnabled(false);
            int e = h.body(); h.world.process();
            h.sync.prepareRuntimeAvailability();
            assertNotNull(h.nativeBody(e));
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void preparedBodyRemovedBeforeRuntimeSubscriptionObservationIsDestroyed() {
        try (Harness h = new Harness()) {
            h.sync.setEnabled(false);
            int e = h.body(); h.world.process();
            h.sync.prepareRuntimeAvailability();
            assertNotNull(h.nativeBody(e));
            h.physics.removePhysics(e);
            h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void preparedBodyRemovedAndRestoredBeforeObservationHasOneNativeOwner() {
        try (Harness h = new Harness()) {
            h.sync.setEnabled(false);
            int e = h.world.create();
            h.world.getMapper(TransformComponent.class).create(e);
            h.physics.ensurePhysics(e);
            h.world.process();
            h.sync.prepareRuntimeAvailability();
            h.physics.removePhysics(e);
            h.physics.ensurePhysics(e);
            h.world.process();
            assertEquals("Restoration must replace the previous native body", 1, h.box.world.getBodyCount());
            h.world.delete(e); h.world.process();
            assertEquals("Deletion must release all native ownership", 0, h.box.world.getBodyCount());
        }
    }

    @Test public void resetRuntimeMirrorReplacesIndexedBodyAndRetainsRuntimePose() {
        try (Harness h = new Harness()) {
            int e = h.body(); h.world.process();
            h.nativeBody(e).setTransform(3f, 4f, 0.5f);
            h.world.getMapper(PhysicsRuntimeBodyComponent.class).get(e).body = null;
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(1, h.box.world.getBodyCount());
            assertEquals(3f, h.nativeBody(e).getPosition().x, 0f);
            assertEquals(4f, h.nativeBody(e).getPosition().y, 0f);
            assertEquals(0.5f, h.nativeBody(e).getAngle(), 0f);
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void replacementBeforeDeferredRuntimeRemovalDoesNotDestroyNewOwner() {
        try (Harness h = new Harness()) {
            int e = h.body(); h.world.process();
            h.world.getMapper(PhysicsRuntimeBodyComponent.class).remove(e);
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.sync.prepareRuntimeAvailability();
            Body replacement = h.nativeBody(e);
            assertEquals(1, h.box.world.getBodyCount());
            for (int i = 0; i < 3; i++) h.world.process();
            assertSame(replacement, h.nativeBody(e));
            assertEquals(1, h.box.world.getBodyCount());
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void deletedPreparedOwnerCannotDestroyRecycledEntityBody() {
        try (Harness h = new Harness()) {
            h.sync.setEnabled(false);
            int e = h.body(); h.world.process();
            h.sync.prepareRuntimeAvailability();
            h.world.delete(e); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
            int replacement = h.body();
            assertEquals("Exercise Artemis ID recycling", e, replacement);
            h.world.process();
            Body owner = h.nativeBody(replacement);
            h.world.process();
            assertSame(owner, h.nativeBody(replacement));
            assertEquals(1, h.box.world.getBodyCount());
            h.world.delete(replacement); h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void candidateFailureWithoutRuntimeComponentPreservesOwnerAndJointThenRetries() {
        try (Harness h = new Harness()) {
            int e = h.body(), other = h.body();
            h.world.getMapper(PhysicsShapesComponent.class).get(e).shapes.add(
                    PhysicsService.createDefaultShape(h.physics.allocateNewPhysicsShapeId()));
            h.publish(e);
            int joint = h.physics.createDistanceJoint(e, other);
            h.world.process();
            Body previous = h.nativeBody(e);
            Joint previousJoint = h.nativeJoint(joint);
            assertNotNull(previousJoint);
            h.world.getMapper(PhysicsRuntimeBodyComponent.class).remove(e);
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.sync.setTestObserver(new FixtureFailure(2));
            assertThrows(IllegalStateException.class, h.sync::prepareRuntimeAvailability);
            assertSame(previous, h.nativeBody(e));
            assertSame(previousJoint, h.nativeJoint(joint));
            assertEquals(2, h.box.world.getBodyCount());
            assertEquals(1, h.box.world.getJointCount());
            h.sync.setTestObserver(null);
            h.sync.prepareRuntimeAvailability();
            assertNotSame(previous, h.nativeBody(e));
            assertSame(h.nativeBody(e), h.nativeJoint(joint).getBodyA());
            assertEquals(2, h.box.world.getBodyCount());
            assertEquals(1, h.box.world.getJointCount());
            h.world.process();
            h.physics.removePhysics(e); h.world.process();
            assertEquals(1, h.box.world.getBodyCount());
            assertEquals(0, h.box.world.getJointCount());
        }
    }

    @Test public void deletionAfterFailedCandidateReleasesPreviousOwnerOnlyOnce() {
        try (Harness h = new Harness()) {
            h.sync.setEnabled(false);
            int e = h.body(); h.world.process();
            h.sync.prepareRuntimeAvailability();
            h.world.getMapper(PhysicsRuntimeBodyComponent.class).remove(e);
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.sync.setTestObserver(new FixtureFailure());
            assertThrows(IllegalStateException.class, h.sync::prepareRuntimeAvailability);
            assertEquals(1, h.box.world.getBodyCount());
            h.sync.setTestObserver(null);
            h.world.delete(e); h.world.process();
            h.world.process();
            assertEquals(0, h.box.world.getBodyCount());
        }
    }

    @Test public void removingBodyReleasesNativeSourceAndDependentGearJoints() {
        try (Harness h = new Harness()) {
            int ground = h.body(), a = h.body(), b = h.body();
            h.world.getMapper(PhysicsBodyComponent.class).get(a).type = 2;
            h.world.getMapper(PhysicsBodyComponent.class).get(b).type = 2;
            int source1 = h.physics.createRevoluteJoint(ground, a, 0f, 0f);
            int source2 = h.physics.createRevoluteJoint(ground, b, 0f, 0f);
            int gear = h.physics.createGearJoint(source1, source2, 1f);
            h.world.process(); h.world.process();
            assertNotNull(h.nativeJoint(gear));
            assertEquals(3, h.box.world.getJointCount());
            h.physics.removePhysics(a); h.world.process();
            assertEquals(2, h.box.world.getBodyCount());
            assertEquals(1, h.box.world.getJointCount());
            assertNull(h.nativeJoint(gear));
            assertNull(h.nativeJoint(source1));
            assertNotNull(h.nativeJoint(source2));
        }
    }

    @Test public void replacingIndexedGroundBodyRebindsNativeSourcesAndDependentGear() {
        try (Harness h = new Harness()) {
            int ground = h.body(), a = h.body(), b = h.body();
            h.world.getMapper(PhysicsBodyComponent.class).get(a).type = 2;
            h.world.getMapper(PhysicsBodyComponent.class).get(b).type = 2;
            int source1 = h.physics.createRevoluteJoint(ground, a, 0f, 0f);
            int source2 = h.physics.createRevoluteJoint(ground, b, 0f, 0f);
            int gear = h.physics.createGearJoint(source1, source2, 1f);
            h.world.process(); h.world.process();
            assertNotNull(h.nativeJoint(gear));
            h.world.getMapper(PhysicsRuntimeBodyComponent.class).remove(ground);
            h.dirty.physics(ground, PhysicsDirtyBits.ALL);
            h.sync.prepareRuntimeAvailability();
            h.world.process(); h.world.process();
            assertEquals(3, h.box.world.getBodyCount());
            assertEquals(3, h.box.world.getJointCount());
            assertSame(h.nativeBody(ground), h.nativeJoint(source1).getBodyA());
            assertSame(h.nativeBody(ground), h.nativeJoint(source2).getBodyA());
            GearJoint nativeGear = (GearJoint) h.nativeJoint(gear);
            assertSame(h.nativeJoint(source1), nativeGear.getJoint1());
            assertSame(h.nativeJoint(source2), nativeGear.getJoint2());
            h.physics.removePhysics(ground); h.world.process();
            assertEquals(2, h.box.world.getBodyCount());
            assertEquals(0, h.box.world.getJointCount());
        }
    }

    private static final class FixtureFailure implements Box2dSyncSystem.TestObserver {
        private final int failAt;
        private int fixtureCount;
        FixtureFailure() { this(1); }
        FixtureFailure(int failAt) { this.failAt = failAt; }
        @Override public void onBodyCompile() { }
        @Override public void onShapeCompile() { }
        @Override public void onPolygonDecomposition() { }
        @Override public void onBodyRebuild() { }
        @Override public void beforeCreateFixture(int e, CompiledFixtureData fixture) {
            if (++fixtureCount == failAt) {
                throw new IllegalStateException("Injected candidate fixture failure");
            }
        }
        @Override public void onFixtureProvenanceCreated(int e, CompiledFixtureData fixture) { }
        @Override public void beforeCreateOrRebuildJoint(int e) { }
    }

    static final class Harness implements AutoCloseable {
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(16);
        final Box2dWorldService box;
        final Box2dSyncSystem sync;
        final World world;
        final PhysicsService physics;
        Harness() {
            GdxNativesLoader.load();
            box = new Box2dWorldService(100, new Vector2());
            sync = new Box2dSyncSystem(box);
            PhysicsPoseAuthority authority = new PhysicsPoseAuthority();
            authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
            world = new World(new WorldConfigurationBuilder().with(dirty, authority, sync).build());
            physics = new PhysicsService(world, box, new SceneMetaRuntime());
        }
        int body() { int e = world.create(); bodyComponents(e); return e; }
        void bodyComponents(int e) {
            world.getMapper(TransformComponent.class).create(e);
            physics.ensurePhysics(e);
        }
        void publish(int e) {
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(e);
            PhysicsService.publishPreparedCandidate(shapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(e),
                    PhysicsService.prepareBodyCandidate(shapes.shapes));
        }
        Body nativeBody(int e) {
            PhysicsRuntimeBodyComponent rt = world.getMapper(PhysicsRuntimeBodyComponent.class).getSafe(e, null);
            return rt == null ? null : rt.body;
        }
        Joint nativeJoint(int e) {
            PhysicsRuntimeJointComponent rt = world.getMapper(PhysicsRuntimeJointComponent.class).getSafe(e, null);
            return rt == null ? null : rt.joint;
        }
        public void close() { world.dispose(); box.dispose(); }
    }
}
