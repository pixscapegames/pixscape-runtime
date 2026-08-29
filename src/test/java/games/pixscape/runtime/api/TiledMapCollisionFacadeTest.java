package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.spatial.SpatialLayerFaceRuntime;
import games.pixscape.runtime.spatial.SpatialLayerRuntimeRegistry;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

public class TiledMapCollisionFacadeTest {
    @Test
    public void runtimeTogglePreservesAuthoredPhysicsAcrossRepeatedCycles() throws Exception {
        Harness harness = new Harness(true);
        try {
            int map = harness.createMapWithManualPhysics(11);
            PhysicsBodyComponent body = harness.body(map);
            PhysicsShapesComponent shapes = harness.shapes(map);
            PhysicsShapeData shape = shapes.shapes.first();
            body.fixedRotation = true;
            body.allowSleep = false;
            body.gravityScale = 0.25f;
            body.linearDamping = 0.75f;
            body.angularDamping = 1.25f;
            shape.categoryBits = 0x0004;
            shape.maskBits = 0x0008;
            shape.groupIndex = -2;
            harness.publish(map, shapes, false);
            shapes = harness.shapes(map);
            shape = shapes.shapes.first();
            harness.process();

            Assert.assertEquals(1, harness.nativeBody(map).getFixtureList().size);
            TiledMapFacade collisions = harness.mapFacade(map);
            for (int cycle = 0; cycle < 2; cycle++) {
                collisions.setCollisionEnabled(false);
                harness.process();
                Assert.assertNull(harness.nativeBody(map));
                Assert.assertSame(body, harness.body(map));
                Assert.assertSame(shapes, harness.shapes(map));
                Assert.assertSame(shape, harness.shapes(map).shapes.first());
                Assert.assertEquals(11, shape.physicsShapeId);
                Assert.assertTrue(body.fixedRotation);
                Assert.assertFalse(body.allowSleep);
                Assert.assertEquals(0.25f, body.gravityScale, 0f);
                Assert.assertEquals(0.75f, body.linearDamping, 0f);
                Assert.assertEquals(1.25f, body.angularDamping, 0f);
                Assert.assertEquals(0x0004, shape.categoryBits);
                Assert.assertEquals(0x0008, shape.maskBits);
                Assert.assertEquals(-2, shape.groupIndex);

                collisions.setCollisionEnabled(true);
                harness.process();
                Assert.assertNotNull(harness.nativeBody(map));
                Assert.assertEquals(1, harness.nativeBody(map).getFixtureList().size);
                Assert.assertEquals(11, harness.shapes(map).shapes.first().physicsShapeId);
            }
        } finally {
            harness.dispose();
        }
    }

    @Test
    public void runtimeToggleIsMapLocalAndDoesNotAffectOrdinaryPhysics() throws Exception {
        Harness harness = new Harness(true);
        try {
            int mapA = harness.createMapWithManualPhysics(21);
            int mapB = harness.createMapWithManualPhysics(22);
            int ordinary = harness.createOrdinaryPhysics(23);
            harness.process();
            com.badlogic.gdx.physics.box2d.Body mapBNative = harness.nativeBody(mapB);
            com.badlogic.gdx.physics.box2d.Body ordinaryNative = harness.nativeBody(ordinary);

            harness.mapFacade(mapA).setCollisionEnabled(false);
            harness.process();
            Assert.assertNull(harness.nativeBody(mapA));
            Assert.assertSame(mapBNative, harness.nativeBody(mapB));
            Assert.assertSame(ordinaryNative, harness.nativeBody(ordinary));

            harness.mapFacade(mapA).setCollisionEnabled(true);
            harness.process();
            Assert.assertNotNull(harness.nativeBody(mapA));
            Assert.assertSame(mapBNative, harness.nativeBody(mapB));
            Assert.assertSame(ordinaryNative, harness.nativeBody(ordinary));
        } finally {
            harness.dispose();
        }
    }

    @Test
    public void enablingDoesNotCreateAuthoredPhysicsAndRejectsDisabledScene() throws Exception {
        Harness harness = new Harness(true);
        try {
            int emptyMap = harness.createMap(false);
            harness.mapFacade(emptyMap).setCollisionEnabled(false).setCollisionEnabled(true);
            harness.process();
            Assert.assertFalse(harness.world.getMapper(PhysicsBodyComponent.class).has(emptyMap));
            Assert.assertFalse(harness.world.getMapper(PhysicsShapesComponent.class).has(emptyMap));
            Assert.assertNull(harness.nativeBody(emptyMap));

            int authoredMap = harness.createMapWithManualPhysics(31);
            harness.process();
            TiledMapFacade collisions = harness.mapFacade(authoredMap);
            collisions.setCollisionEnabled(false);
            harness.process();
            harness.scene.physicsEnabled = false;

            try {
                collisions.setCollisionEnabled(true);
                Assert.fail("Enabling collisions must reject a scene with physics disabled.");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("scene physics is disabled"));
            }

            Assert.assertFalse(harness.tiled(authoredMap).data.collisionEnabled);
            Assert.assertTrue(harness.world.getMapper(PhysicsBodyComponent.class).has(authoredMap));
            Assert.assertTrue(harness.world.getMapper(PhysicsShapesComponent.class).has(authoredMap));
            Assert.assertNull(harness.nativeBody(authoredMap));
        } finally {
            harness.dispose();
        }
    }

    @Test
    public void authoredJointSurvivesWhileNativeJointFollowsMapBody() throws Exception {
        Harness harness = new Harness(true);
        try {
            int map = harness.createMapWithManualPhysics(41);
            int ordinary = harness.createOrdinaryPhysics(42);
            int joint = harness.createDistanceJoint(map, ordinary);
            harness.process();
            harness.process();
            Assert.assertTrue(harness.world.getMapper(PhysicsJointComponent.class).has(joint));
            Assert.assertNotNull(harness.runtimeJoint(joint));

            harness.mapFacade(map).setCollisionEnabled(false);
            harness.process();
            Assert.assertTrue(harness.world.getMapper(PhysicsJointComponent.class).has(joint));
            Assert.assertTrue(harness.world.getMapper(PhysicsDistanceJointComponent.class).has(joint));
            Assert.assertNull(harness.runtimeJoint(joint));

            harness.mapFacade(map).setCollisionEnabled(true);
            harness.process();
            Assert.assertTrue(harness.world.getMapper(PhysicsJointComponent.class).has(joint));
            Assert.assertNotNull(harness.runtimeJoint(joint));
        } finally {
            harness.dispose();
        }
    }

    @Test
    public void linkedSpatialFixtureAndProjectedGeometrySurviveRuntimeToggle() throws Exception {
        Harness harness = new Harness(true);
        try {
            int map = harness.createMapWithLinkedSpatialPhysics(51);
            TiledLayerComponent tiled = harness.tiled(map);
            SpatialBlocksComponent blocks = harness.world.getMapper(
                    SpatialBlocksComponent.class).get(map);
            PhysicsShapesComponent shapes = harness.shapes(map);
            PhysicsShapeData linked = shapes.shapes.first();
            SpatialBlockData block = blocks.blocks.first();
            SpatialLayerFaceRuntime spatialRuntime = new SpatialLayerRuntimeRegistry()
                    .forLayer(map, tiled.data);
            spatialRuntime.compiled.ensure(blocks);
            spatialRuntime.projected.ensure(spatialRuntime.compiled, tiled.data);
            int projectedFaces = spatialRuntime.projected.faceCount;
            int projectionCount = spatialRuntime.projected.projectionCount();
            harness.process();
            Assert.assertTrue(projectedFaces > 0);
            Assert.assertEquals(1, harness.nativeBody(map).getFixtureList().size);

            harness.mapFacade(map).setCollisionEnabled(false);
            harness.process();
            Assert.assertNull(harness.nativeBody(map));
            Assert.assertSame(blocks, harness.world.getMapper(SpatialBlocksComponent.class).get(map));
            Assert.assertSame(block, blocks.blocks.first());
            Assert.assertSame(linked, harness.shapes(map).shapes.first());
            Assert.assertEquals(1, linked.spatialBlockId);
            Assert.assertEquals(51, linked.physicsShapeId);
            Assert.assertTrue(tiled.spatialEnabled);
            Assert.assertTrue(tiled.data.spatialEnabled);
            Assert.assertEquals(3f, tiled.defaultTileAltitude, 0f);
            Assert.assertEquals(12f, tiled.defaultTileHeight, 0f);
            Assert.assertFalse(spatialRuntime.projected.ensure(
                    spatialRuntime.compiled, tiled.data));
            Assert.assertEquals(projectedFaces, spatialRuntime.projected.faceCount);
            Assert.assertEquals(projectionCount, spatialRuntime.projected.projectionCount());

            harness.mapFacade(map).setCollisionEnabled(true);
            harness.process();
            Assert.assertNotNull(harness.nativeBody(map));
            Assert.assertEquals(1, harness.nativeBody(map).getFixtureList().size);
            Assert.assertSame(block, blocks.blocks.first());
            Assert.assertSame(linked, harness.shapes(map).shapes.first());
            Assert.assertEquals(1, linked.spatialBlockId);
            Assert.assertEquals(51, linked.physicsShapeId);
        } finally {
            harness.dispose();
        }
    }

    private static final class Harness {
        final PixscapeEngine engine;
        final SceneMetaRuntime scene;
        final Box2dWorldService box2d;
        final DirtyTrackerSystem dirty;
        final Box2dSyncSystem sync;
        final World world;

        Harness(boolean physicsEnabled) throws Exception {
            GdxNativesLoader.load();
            scene = new SceneMetaRuntime();
            scene.physicsEnabled = physicsEnabled;
            scene.pixelsPerMeter = 100f;
            box2d = new Box2dWorldService(100f, new Vector2(0f, -9.81f));
            dirty = new DirtyTrackerSystem(32);
            sync = new Box2dSyncSystem(box2d);
            sync.setSceneMeta(scene);
            sync.setStepEnabled(false);
            world = new World(new WorldConfigurationBuilder()
                    .with(dirty, sync, new PhysicsSpatialFootprintSyncSystem(100f))
                    .build());
            engine = new PixscapeEngine();
            setField(engine, "world", world);
            setField(engine, "activeSceneMeta", scene);
            setField(engine, "box2dWorldService", box2d);
            setField(engine, "box2dSyncSystem", sync);
        }

        int createMapWithManualPhysics(int physicsShapeId) {
            int map = createMap(true);
            PhysicsShapesComponent shapes = shapes(map);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.geometry = new PhysicsGeometryData();
            shapes.shapes.add(shape);
            publish(map, shapes, false);
            return map;
        }

        int createMapWithLinkedSpatialPhysics(int physicsShapeId) {
            int map = createMap(true);
            TiledLayerComponent tiled = tiled(map);
            tiled.spatialEnabled = true;
            tiled.defaultTileAltitude = 3f;
            tiled.defaultTileHeight = 12f;
            tiled.data.spatialEnabled = true;
            tiled.data.defaultTileAltitude = 3f;
            tiled.data.defaultTileHeight = 12f;

            SpatialBlocksComponent blocks = world.getMapper(
                    SpatialBlocksComponent.class).create(map);
            blocks.nextSpatialBlockId = 2;
            SpatialBlockData block = new SpatialBlockData();
            block.id = 1;
            block.structureId = 1;
            block.name = "Wall";
            block.x = 1f;
            block.y = 1f;
            block.width = 2f;
            block.depth = 1f;
            block.altitude = 3f;
            block.height = 12f;
            blocks.blocks.add(block);

            PhysicsShapesComponent shapes = shapes(map);
            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = physicsShapeId;
            linked.spatialBlockId = 1;
            linked.geometry = null;
            shapes.shapes.add(linked);
            publish(map, shapes, true);
            return map;
        }

        int createMap(boolean withPhysics) {
            int map = world.create();
            world.getMapper(TransformComponent.class).create(map);
            world.getMapper(EntityIndexComponent.class).create(map).layerIndex = 0;
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(map);
            tiled.atlasTag = "main";
            tiled.projection = TiledProjection.ISO;
            tiled.tileWidth = 32;
            tiled.tileHeight = 16;
            tiled.mapWidthCells = 8;
            tiled.mapHeightCells = 8;
            tiled.chunkSize = 4;
            tiled.data = tiled.createMapData();
            if (withPhysics) {
                PhysicsBodyComponent body = world.getMapper(
                        PhysicsBodyComponent.class).create(map);
                PhysicsService.initDefaultBody(body);
                body.type = PhysicsBodyComponent.STATIC;
                world.getMapper(PhysicsShapesComponent.class).create(map);
            }
            return map;
        }

        int createOrdinaryPhysics(int physicsShapeId) {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity);
            PhysicsBodyComponent body = world.getMapper(
                    PhysicsBodyComponent.class).create(entity);
            PhysicsService.initDefaultBody(body);
            PhysicsShapesComponent shapes = world.getMapper(
                    PhysicsShapesComponent.class).create(entity);
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.geometry = new PhysicsGeometryData();
            shapes.shapes.add(shape);
            publish(entity, shapes, false);
            return entity;
        }

        int createDistanceJoint(int bodyA, int bodyB) {
            int joint = world.create();
            PhysicsJointComponent base = world.getMapper(
                    PhysicsJointComponent.class).create(joint);
            base.type = PhysicsJointComponent.TYPE_DISTANCE;
            base.aEid = bodyA;
            base.bEid = bodyB;
            world.getMapper(PhysicsDistanceJointComponent.class).create(joint).lengthM = 1f;
            return joint;
        }

        void publish(int entity, PhysicsShapesComponent shapes, boolean linked) {
            PhysicsCompiledFixturesComponent compiled = world.getMapper(
                    PhysicsCompiledFixturesComponent.class).has(entity)
                    ? world.getMapper(PhysicsCompiledFixturesComponent.class).get(entity)
                    : world.getMapper(PhysicsCompiledFixturesComponent.class).create(entity);
            PhysicsService.publishPreparedCandidate(
                    shapes,
                    compiled,
                    linked
                            ? PhysicsService.prepareBodyCandidate(
                                    world, entity, shapes.shapes, scene.pixelsPerMeter)
                            : PhysicsService.prepareBodyCandidate(shapes.shapes));
            dirty.physics(entity, games.pixscape.runtime.render.PhysicsDirtyBits.ALL);
        }

        void process() {
            world.process();
        }

        TiledMapFacade mapFacade(int map) {
            return engine.api().tiled().requireEntityId(map).map();
        }

        TiledLayerComponent tiled(int map) {
            return world.getMapper(TiledLayerComponent.class).get(map);
        }

        PhysicsBodyComponent body(int entity) {
            return world.getMapper(PhysicsBodyComponent.class).get(entity);
        }

        PhysicsShapesComponent shapes(int entity) {
            return world.getMapper(PhysicsShapesComponent.class).get(entity);
        }

        com.badlogic.gdx.physics.box2d.Body nativeBody(int entity) {
            PhysicsRuntimeBodyComponent runtime = world.getMapper(
                    PhysicsRuntimeBodyComponent.class).getSafe(entity, null);
            return runtime != null ? runtime.body : null;
        }

        com.badlogic.gdx.physics.box2d.Joint runtimeJoint(int entity) {
            PhysicsRuntimeJointComponent runtime = world.getMapper(
                    PhysicsRuntimeJointComponent.class).getSafe(entity, null);
            return runtime != null ? runtime.joint : null;
        }

        void dispose() {
            world.dispose();
            box2d.dispose();
        }
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
