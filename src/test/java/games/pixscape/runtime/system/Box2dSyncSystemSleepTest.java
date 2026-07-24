package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.GeometryDirty;
import games.pixscape.runtime.service.Box2dWorldService;
import org.junit.Assert;
import org.junit.Test;

public class Box2dSyncSystemSleepTest {
    @Test
    public void authoredSleepingStateIsAppliedOnInitialCreation() {
        Harness harness = new Harness(true, false);

        harness.world.process();

        Assert.assertFalse(harness.nativeBody().isAwake());
        harness.dispose();
    }

    @Test
    public void stableFramesDoNotWakeOrRebuildSleepingBody() {
        Harness harness = new Harness(true, true);
        harness.sync.setStepEnabled(true);
        harness.world.setDelta(1f / 60f);
        harness.world.process();

        for (int frame = 0; frame < 180; frame++) {
            harness.world.process();
        }
        Assert.assertFalse("Box2D should put the idle body to sleep.",
                harness.nativeBody().isAwake());
        Body sleepingBody = harness.nativeBody();
        harness.compilationCounter = 0;
        clearGeometryDirty(harness);

        for (int frame = 0; frame < 1000; frame++) {
            harness.world.process();
        }

        Assert.assertSame(sleepingBody, harness.nativeBody());
        Assert.assertFalse(harness.nativeBody().isAwake());
        Assert.assertEquals(0, harness.compilationCounter);
        Assert.assertEquals(GeometryDirty.NONE,
                harness.dirty.geomSub(harness.entityId)
                        & (GeometryDirty.POSITION | GeometryDirty.ROTATION));
        harness.dispose();
    }

    @Test
    public void nativeMovementPublishesOnlyChangedGeometryAxes() {
        Harness harness = new Harness(true, false);
        harness.sync.setStepEnabled(true);
        harness.world.setDelta(1f / 60f);
        harness.world.process();
        clearGeometryDirty(harness);

        Body body = harness.nativeBody();
        body.setTransform(1f, 0f, 0f);
        harness.world.process();

        Assert.assertEquals(100f, harness.transform.x, 0f);
        Assert.assertTrue(
                (harness.dirty.geomSub(harness.entityId)
                        & GeometryDirty.POSITION) != 0);
        Assert.assertEquals(0,
                harness.dirty.geomSub(harness.entityId)
                        & GeometryDirty.ROTATION);
        clearGeometryDirty(harness);

        body.setTransform(1f, 0f, 0.25f);
        harness.world.process();

        Assert.assertEquals(0.25f, harness.transform.rotationRad, 0f);
        Assert.assertTrue(
                (harness.dirty.geomSub(harness.entityId)
                        & GeometryDirty.ROTATION) != 0);
        Assert.assertEquals(0,
                harness.dirty.geomSub(harness.entityId)
                        & GeometryDirty.POSITION);
        harness.dispose();
    }

    @Test
    public void physicsMutationRebuildsAndWakesBody() {
        Harness harness = new Harness(true, false);
        harness.world.process();
        Body previousBody = harness.nativeBody();
        Assert.assertFalse(previousBody.isAwake());

        harness.shape.radius = 0.75f;
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);
        harness.world.process();

        Assert.assertNotSame(previousBody, harness.nativeBody());
        Assert.assertTrue(harness.nativeBody().isAwake());
        harness.dispose();
    }

    @Test
    public void sleepingDisabledKeepsBodyAwake() {
        Harness harness = new Harness(false, false);
        harness.sync.setStepEnabled(true);
        harness.world.setDelta(1f / 60f);
        harness.world.process();

        for (int frame = 0; frame < 180; frame++) {
            harness.world.process();
        }

        Assert.assertFalse(harness.nativeBody().isSleepingAllowed());
        Assert.assertTrue(harness.nativeBody().isAwake());
        harness.dispose();
    }

    @Test
    public void pausedAuthoringOnlyWakesBodyWhenTransformActuallyChanges() {
        Harness harness = new Harness(true, false);
        harness.world.process();
        Assert.assertFalse(harness.nativeBody().isAwake());

        harness.world.process();
        Assert.assertFalse(harness.nativeBody().isAwake());

        harness.transform.x = 100f;
        harness.world.process();
        Assert.assertTrue(harness.nativeBody().isAwake());
        Assert.assertEquals(1f, harness.nativeBody().getPosition().x, 0f);
        harness.dispose();
    }

    private static final class Harness {
        final Box2dWorldService box2d;
        final DirtyTrackerSystem dirty;
        final Box2dSyncSystem sync;
        final World world;
        final int entityId;
        final TransformComponent transform;
        final PhysicsShapeData shape;
        int compilationCounter;

        Harness(boolean allowSleep, boolean awake) {
            GdxNativesLoader.load();
            box2d = new Box2dWorldService(100f, new Vector2(), true);
            dirty = new DirtyTrackerSystem(8);
            sync = new Box2dSyncSystem(box2d);
            sync.setTestObserver(new Box2dSyncSystem.TestObserver() {
                @Override
                public void onBodyCompile() {
                    compilationCounter++;
                }

                @Override
                public void onShapeCompile() {
                }

                @Override
                public void onPolygonDecomposition() {
                }

                @Override
                public void onBodyRebuild() {
                }

                @Override
                public void beforeCreateFixture(
                        int bodyEntityId, CompiledFixtureData fixture) {
                }

                @Override
                public void onFixtureProvenanceCreated(
                        int bodyEntityId, CompiledFixtureData fixture) {
                }

                @Override
                public void beforeCreateOrRebuildJoint(int jointEntityId) {
                }
            });
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.gravityY = 0f;
            sync.setSceneMeta(meta);
            world = new World(new WorldConfigurationBuilder()
                    .with(dirty, sync)
                    .build());
            entityId = world.create();
            transform = world.getMapper(TransformComponent.class).create(entityId);
            PhysicsBodyComponent body =
                    world.getMapper(PhysicsBodyComponent.class).create(entityId);
            body.allowSleep = allowSleep;
            body.awake = awake;
            PhysicsShapesComponent shapes =
                    world.getMapper(PhysicsShapesComponent.class).create(entityId);
            shape = new PhysicsShapeData();
            shape.physicsShapeId = 1;
            shape.shapeType = PhysicsShapeData.SHAPE_CIRCLE;
            shape.radius = 0.5f;
            shapes.add(shape);
        }

        Body nativeBody() {
            return world.getMapper(PhysicsRuntimeBodyComponent.class)
                    .get(entityId).body;
        }

        void dispose() {
            world.dispose();
            box2d.dispose();
        }
    }

    private static void clearGeometryDirty(Harness harness) {
        harness.dirty.consume(
                DirtyBits.GEOMETRY,
                entityId -> harness.dirty.clearAllGeomSub(entityId));
        harness.dirty.clearAllGeomSub(harness.entityId);
    }
}
