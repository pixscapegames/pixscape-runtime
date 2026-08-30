package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsFixtureProvenance;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.JointDirtyBits;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import org.junit.Assert;
import org.junit.Test;

public class Box2dSyncSystemPhysicsContractTest {
    @Test
    public void parentedPhysicsFailsBeforeBox2dCanOverwriteAuthoredLocalTransform() {
        Harness harness = new Harness();
        TransformComponent local = harness.world.getMapper(TransformComponent.class)
                .get(harness.entityId);
        local.x = 17f;
        local.y = -9f;
        harness.world.getMapper(GameObjectMemberComponent.class)
                .create(harness.entityId).parentStableId = 1;

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class, harness.world::process);

        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains("Physics hierarchy is unsupported"));
        Assert.assertEquals(17f, local.x, 0f);
        Assert.assertEquals(-9f, local.y, 0f);
        Assert.assertFalse(harness.world.getMapper(PhysicsRuntimeBodyComponent.class)
                .has(harness.entityId));
    }

    @Test
    public void emptyPreparedCacheCreatesNoNativeOrRuntimeBody() {
        Harness harness = new Harness();
        harness.shapes.shapes.clear();
        harness.prepareAndProcess();

        Assert.assertEquals(0, harness.box2d.world.getBodyCount());
        Assert.assertFalse(harness.world.getMapper(
                PhysicsRuntimeBodyComponent.class).has(harness.entityId));
        PhysicsCompiledFixturesComponent compiled = harness.world.getMapper(
                PhysicsCompiledFixturesComponent.class).get(harness.entityId);
        Assert.assertTrue(compiled.valid);
        Assert.assertEquals(0, compiled.fixtures.size);
    }

    @Test
    public void nonEmptyAndEmptyCachesMaterializeAndDestroyNativeBody() {
        Harness harness = new Harness();
        harness.prepareAndProcess();
        Assert.assertEquals(1, harness.box2d.world.getBodyCount());

        harness.shapes.shapes.clear();
        harness.publishCache(harness.entityId, harness.shapes);
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);
        harness.world.process();

        Assert.assertEquals(0, harness.box2d.world.getBodyCount());
        Assert.assertFalse(harness.world.getMapper(
                PhysicsRuntimeBodyComponent.class).has(harness.entityId));

        PhysicsShapeData restored = PhysicsService.createDefaultShape(1);
        harness.shapes.shapes.add(restored);
        harness.publishCache(harness.entityId, harness.shapes);
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);
        harness.world.process();

        Assert.assertEquals(1, harness.box2d.world.getBodyCount());
        PhysicsRuntimeBodyComponent runtime = harness.world.getMapper(
                PhysicsRuntimeBodyComponent.class).get(harness.entityId);
        Assert.assertNotNull(runtime.body);
        Assert.assertEquals(1, runtime.body.getFixtureList().size);
    }

    @Test
    public void sensorSpatialFootprintMaterializesAsBox2dSensor() {
        Harness harness = new Harness();
        harness.source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        harness.source.geometry.radius = 0.5f;
        harness.source.spatialFootprint = true;
        harness.source.sensor = true;

        harness.prepareAndProcess();

        PhysicsRuntimeBodyComponent runtime = harness.world.getMapper(
                PhysicsRuntimeBodyComponent.class).get(harness.entityId);
        Assert.assertTrue(runtime.body.getFixtureList().first().isSensor());
        SpatialPhysicsFootprintComponent footprint = harness.world.getMapper(
                SpatialPhysicsFootprintComponent.class).get(harness.entityId);
        Assert.assertTrue(footprint.valid);
        Assert.assertEquals(harness.source.physicsShapeId, footprint.sourcePhysicsShapeId);
    }

    @Test
    public void invalidRecompileKeepsPreviousNativeBodyAndCompiledCache() {
        Harness harness = new Harness();
        harness.source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        harness.source.geometry.radius = 0.5f;
        harness.source.geometry.offsetX = 0.25f;
        harness.source.spatialFootprint = true;
        harness.prepareAndProcess();

        PhysicsRuntimeBodyComponent runtime =
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class).get(harness.entityId);
        PhysicsCompiledFixturesComponent compiled =
                harness.world.getMapper(PhysicsCompiledFixturesComponent.class).get(harness.entityId);
        SpatialPhysicsFootprintComponent footprint =
                harness.world.getMapper(SpatialPhysicsFootprintComponent.class).get(harness.entityId);
        Body originalBody = runtime.body;
        int originalGeneration = compiled.generation;
        int originalFootprintGeneration = footprint.physicsGeneration;

        harness.source.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        harness.source.geometry.polygonVertices = new float[]{0f, 0f, 1f, 0f};
        harness.source.geometry.polygonVertexCount = 2;
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);

        try {
            harness.prepareAndProcess();
            Assert.fail("Invalid polygon source must reject the rebuild.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("PhysicsShapeData"));
        }

        Assert.assertSame(originalBody, runtime.body);
        Assert.assertEquals(1, harness.box2d.world.getBodyCount());
        Assert.assertEquals(1, runtime.body.getFixtureList().size);
        Assert.assertEquals(originalGeneration, compiled.generation);
        Assert.assertTrue(compiled.valid);
        Assert.assertTrue(footprint.valid);
        Assert.assertEquals(50f, footprint.radiusPx, 0f);
        Assert.assertEquals(25f, footprint.localOffsetXPx, 0f);
        Assert.assertEquals(originalFootprintGeneration, footprint.physicsGeneration);
    }

    @Test
    public void concaveSourceBuildsPartsWithSourceProvenanceAndBodyRemovalDestroysNativeBody() {
        Harness harness = new Harness();
        harness.source.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        harness.source.geometry.polygonVertices = new float[]{
                0f, 0f, 2f, 0f, 2f, 2f, 1f, 1f, 0f, 2f
        };
        harness.source.geometry.polygonVertexCount = 5;
        harness.prepareAndProcess();

        PhysicsRuntimeBodyComponent runtime =
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class).get(harness.entityId);
        Assert.assertTrue(runtime.body.getFixtureList().size > 1);
        for (int i = 0; i < runtime.body.getFixtureList().size; i++) {
            Object userData = runtime.body.getFixtureList().get(i).getUserData();
            Assert.assertTrue(userData instanceof PhysicsFixtureProvenance);
            PhysicsFixtureProvenance provenance = (PhysicsFixtureProvenance) userData;
            Assert.assertEquals(harness.entityId, provenance.bodyEntityId);
            Assert.assertEquals(1, provenance.physicsShapeId);
            Assert.assertEquals(i, provenance.partIndex);
        }

        harness.world.getMapper(PhysicsBodyComponent.class).remove(harness.entityId);
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);
        harness.prepareAndProcess();

        Assert.assertEquals(0, harness.box2d.world.getBodyCount());
        Assert.assertEquals(1, harness.shapes.shapes.size);
        Assert.assertSame(harness.source, harness.shapes.shapes.first());
    }

    @Test
    public void fixtureCreationFailureKeepsPreviousBodyAndReportsFullProvenance() {
        Harness harness = new Harness();
        harness.world.getMapper(PixscapeIdentityComponent.class)
                .create(harness.entityId).stableId = 91;
        harness.prepareAndProcess();
        PhysicsRuntimeBodyComponent runtime =
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class)
                        .get(harness.entityId);
        PhysicsCompiledFixturesComponent compiled =
                harness.world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .get(harness.entityId);
        Body originalBody = runtime.body;
        int originalGeneration = compiled.generation;
        IllegalArgumentException nativeFailure =
                new IllegalArgumentException("injected native fixture failure");
        harness.sync.setTestObserver(new ObserverAdapter() {
            @Override
            public void beforeCreateFixture(
                    int bodyEntityId, CompiledFixtureData fixture) {
                throw nativeFailure;
            }
        });
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);

        try {
            harness.world.process();
            Assert.fail("The injected fixture failure must reject the candidate body.");
        } catch (IllegalStateException expected) {
            Assert.assertSame(nativeFailure, expected.getCause());
            Assert.assertTrue(expected.getMessage().contains(
                    "body entityId " + harness.entityId));
            Assert.assertTrue(expected.getMessage().contains("stableId 91"));
            Assert.assertTrue(expected.getMessage().contains("physicsShapeId 1"));
            Assert.assertTrue(expected.getMessage().contains("partIndex 0"));
            Assert.assertTrue(expected.getMessage().contains(
                    "fixtureType " + PhysicsGeometryData.SHAPE_BOX));
        }

        Assert.assertSame(originalBody, runtime.body);
        Assert.assertEquals(originalGeneration, compiled.generation);
        Assert.assertEquals(1, harness.box2d.world.getBodyCount());
    }

    @Test
    public void failedJointRecreationLeavesNoNativeReferenceAndRemainsDirty() {
        Harness harness = new Harness();
        int secondBody = harness.createBody(2, 100f);
        int jointEntity = harness.world.create();
        PhysicsJointComponent joint =
                harness.world.getMapper(PhysicsJointComponent.class)
                        .create(jointEntity);
        joint.type = PhysicsJointComponent.TYPE_DISTANCE;
        joint.aEid = harness.entityId;
        joint.bEid = secondBody;
        PhysicsDistanceJointComponent distance =
                harness.world.getMapper(PhysicsDistanceJointComponent.class)
                        .create(jointEntity);
        distance.lengthM = 1f;
        harness.prepareAndProcess();
        Assert.assertNotNull(
                harness.world.getMapper(PhysicsRuntimeJointComponent.class)
                        .get(jointEntity).joint);

        IllegalStateException nativeFailure =
                new IllegalStateException("injected joint failure");
        harness.sync.setTestObserver(new ObserverAdapter() {
            @Override
            public void beforeCreateOrRebuildJoint(int currentJointEntityId) {
                if (currentJointEntityId == jointEntity) {
                    throw nativeFailure;
                }
            }
        });
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);

        try {
            harness.world.process();
            Assert.fail("The injected joint failure must be reported.");
        } catch (IllegalStateException expected) {
            Assert.assertSame(nativeFailure, expected.getCause());
            Assert.assertTrue(expected.getMessage().contains(
                    "joint entityId " + jointEntity));
        }

        PhysicsRuntimeJointComponent runtimeJoint =
                harness.world.getMapper(PhysicsRuntimeJointComponent.class)
                        .getSafe(jointEntity, null);
        Assert.assertTrue(runtimeJoint == null || runtimeJoint.joint == null);
        Assert.assertTrue(harness.dirty.isDirty(jointEntity, DirtyBits.JOINTS));
        Assert.assertTrue(
                (harness.dirty.jointSub(jointEntity) & JointDirtyBits.ALL) != 0);
        Assert.assertNotNull(
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class)
                        .get(harness.entityId).body);
        Assert.assertNotNull(
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class)
                        .get(secondBody).body);
    }

    private static final class Harness {
        final Box2dWorldService box2d;
        final DirtyTrackerSystem dirty;
        final Box2dSyncSystem sync;
        final World world;
        final int entityId;
        final PhysicsBodyComponent body;
        final PhysicsShapesComponent shapes;
        PhysicsShapeData source;

        Harness() {
            GdxNativesLoader.load();
            box2d = new Box2dWorldService(100f, new Vector2());
            dirty = new DirtyTrackerSystem(16);
            sync = new Box2dSyncSystem(box2d);
            world = new World(new WorldConfigurationBuilder()
                    .with(dirty, sync, new PhysicsSpatialFootprintSyncSystem(100f))
                    .build());
            entityId = world.create();
            world.getMapper(TransformComponent.class).create(entityId);
            body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
            shapes = world.getMapper(PhysicsShapesComponent.class).create(entityId);
            source = new PhysicsShapeData();
            source.geometry = new PhysicsGeometryData();
            source.physicsShapeId = 1;
            source.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
            shapes.shapes.add(source);
        }

        int createBody(int physicsShapeId, float x) {
            int created = world.create();
            TransformComponent transform =
                    world.getMapper(TransformComponent.class).create(created);
            transform.x = x;
            world.getMapper(PhysicsBodyComponent.class).create(created);
            PhysicsShapesComponent createdShapes =
                    world.getMapper(PhysicsShapesComponent.class).create(created);
            PhysicsShapeData createdShape = new PhysicsShapeData();
            createdShape.geometry = new PhysicsGeometryData();
            createdShape.physicsShapeId = physicsShapeId;
            createdShape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
            createdShapes.shapes.add(createdShape);
            publishCache(created, createdShapes);
            return created;
        }

        void prepareAndProcess() {
            publishCache(entityId, shapes);
            if (shapes.shapes.size > 0) {
                source = shapes.shapes.first();
            }
            world.process();
        }

        private void publishCache(int entity, PhysicsShapesComponent sourceShapes) {
            PhysicsService.publishPreparedCandidate(
                    sourceShapes,
                    world.getMapper(PhysicsCompiledFixturesComponent.class).create(entity),
                    PhysicsService.prepareBodyCandidate(sourceShapes.shapes));
        }
    }

    private abstract static class ObserverAdapter
            implements Box2dSyncSystem.TestObserver {
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
    }
}
