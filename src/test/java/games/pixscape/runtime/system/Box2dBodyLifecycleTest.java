package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Native integration: structural lifecycle changes must not require a full-world sweep.
 */
public class Box2dBodyLifecycleTest {
    @Test
    public void lateBodiesAndRemovedOrRestoredComponentsAreObservedWithoutManualDirtyMarks() {
        try (Harness h = new Harness()) {
            h.world.process(); // Close bootstrap before spawning.
            int e = h.body();
            h.world.process();
            assertNotNull(h.nativeBody(e));
            h.world.getMapper(TransformComponent.class).remove(e);
            h.world.process();
            assertEquals(0, h.box2d.world.getBodyCount());
            h.world.getMapper(TransformComponent.class).create(e);
            h.world.process();
            assertNotNull(h.nativeBody(e));
            h.world.getMapper(PhysicsBodyComponent.class).remove(e);
            h.world.process();
            assertEquals(0, h.box2d.world.getBodyCount());
            h.world.getMapper(PhysicsBodyComponent.class).create(e);
            h.world.process();
            assertEquals(1, h.box2d.world.getBodyCount());
        }
    }

    @Test
    public void stableRuntimeDoesNoBodyChecksAndOneEditDoesNotVisitOtherBodies() {
        try (Harness h = new Harness()) {
            int changed = h.body();
            for (int i = 0; i < 1000; i++) h.body();
            h.world.process();
            h.counts.reset();
            for (int i = 0; i < 100; i++) h.world.process();
            assertEquals(0, h.counts.checks);
            assertEquals(0, h.counts.rebuilds);
            h.world.getMapper(PhysicsBodyComponent.class).get(changed).fixedRotation = true;
            h.dirty.physics(changed, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(1, h.counts.rebuilds);
            assertTrue("Only the changed body should be checked", h.counts.checks < 10);
            assertTrue(h.nativeBody(changed).isFixedRotation());
            assertEquals(1001, h.box2d.world.getBodyCount());
        }
    }

    @Test
    public void tiledComponentChangesAndCollisionTogglesReconcileOnlyThatBody() {
        try (Harness h = new Harness()) {
            int e = h.body();
            h.world.process();
            TiledLayerComponent map = h.world.getMapper(TiledLayerComponent.class).create(e);
            map.data = new TiledMapLayerData(1, 1, 16, 16, 1, TiledProjection.ORTHO);
            map.data.collisionEnabled = false;
            h.world.process();
            assertEquals(0, h.box2d.world.getBodyCount());
            map.data.collisionEnabled = true;
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(1, h.box2d.world.getBodyCount());
            map.data.collisionEnabled = false;
            h.dirty.physics(e, PhysicsDirtyBits.ALL);
            h.world.process();
            assertEquals(0, h.box2d.world.getBodyCount());
            h.world.getMapper(TiledLayerComponent.class).remove(e);
            h.world.process();
            assertEquals(1, h.box2d.world.getBodyCount());
        }
    }

    @Test
    public void disabledFramesAndDeletedEntitiesDoNotLoseOrResurrectBodies() {
        try (Harness h = new Harness()) {
            int removed = h.body();
            h.world.process();
            h.sync.setEnabled(false);
            h.world.getMapper(PhysicsBodyComponent.class).remove(removed);
            int added = h.body();
            int transientEntity = h.body();
            h.world.delete(transientEntity);
            h.world.process();
            h.dirty.clearAll();
            h.sync.setEnabled(true);
            h.world.process();
            assertEquals(1, h.box2d.world.getBodyCount());
            assertNull(h.nativeBody(removed));
            assertNotNull(h.nativeBody(added));
            h.world.delete(added);
            h.world.process();
            assertEquals(0, h.box2d.world.getBodyCount());
            int replacement = h.body();
            h.world.process();
            assertEquals(1, h.box2d.world.getBodyCount());
            assertNotNull(h.nativeBody(replacement));
        }
    }

    @Test
    public void removingRuntimeComponentRecreatesWantedBodyWithoutLeakingOldNativeBody() {
        try (Harness h = new Harness()) {
            int e = h.body();
            h.world.process();
            h.world.getMapper(PhysicsRuntimeBodyComponent.class).remove(e);
            h.world.process();
            assertEquals(1, h.box2d.world.getBodyCount());
            assertNotNull(h.nativeBody(e));
        }
    }

    @Test
    public void missingCompiledCacheStillFailsAndPublishedCacheAllowsRetry() {
        try (Harness h = new Harness()) {
            h.world.process();
            int e = h.world.create();
            h.world.getMapper(TransformComponent.class).create(e);
            h.world.getMapper(PhysicsBodyComponent.class).create(e);
            assertThrows(IllegalStateException.class, () -> h.world.process());
            h.prepare(e);
            h.world.process();
            assertNotNull(h.nativeBody(e));
        }
    }

    private static class Harness implements AutoCloseable {
        final Box2dWorldService box2d;
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(2048);
        final Box2dSyncSystem sync;
        final World world;
        final Counts counts = new Counts();

        Harness() {
            GdxNativesLoader.load();
            box2d = new Box2dWorldService(100, new Vector2());
            sync = new Box2dSyncSystem(box2d);
            sync.setTestObserver(counts);
            PhysicsPoseAuthority authority = new PhysicsPoseAuthority();
            authority.setMode(PhysicsPoseAuthority.Mode.RUNTIME_PHYSICS);
            world = new World(new WorldConfigurationBuilder().with(dirty, authority, sync).build());
        }

        int body() {
            int e = world.create();
            world.getMapper(TransformComponent.class).create(e);
            world.getMapper(PhysicsBodyComponent.class).create(e);
            prepare(e);
            return e;
        }

        void prepare(int e) {
            PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(e);
            shapes.shapes.add(PhysicsService.createDefaultShape(e + 1));
            PhysicsService.publishPreparedCandidate(shapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(e),
                    PhysicsService.prepareBodyCandidate(shapes.shapes));
        }

        Body nativeBody(int e) {
            PhysicsRuntimeBodyComponent rt = world.getMapper(PhysicsRuntimeBodyComponent.class).getSafe(e, null);
            return rt != null ? rt.body : null;
        }

        @Override
        public void close() {
            world.dispose();
            box2d.dispose();
        }
    }

    private static class Counts implements Box2dSyncSystem.TestObserver {
        int checks, rebuilds;

        void reset() {
            checks = rebuilds = 0;
        }

        @Override
        public void onBodyEligibilityCheck() {
            checks++;
        }

        @Override
        public void onBodyCacheCheck() {
            checks++;
        }

        @Override
        public void onBodyRebuild() {
            rebuilds++;
        }

        @Override
        public void onBodyCompile() {
        }

        @Override
        public void onShapeCompile() {
        }

        @Override
        public void onPolygonDecomposition() {
        }

        @Override
        public void beforeCreateFixture(int e, CompiledFixtureData fixture) {
        }

        @Override
        public void onFixtureProvenanceCreated(int e, CompiledFixtureData fixture) {
        }

        @Override
        public void beforeCreateOrRebuildJoint(int e) {
        }
    }
}
