package games.pixscape.runtime.service;

import com.artemis.Component;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsServiceTest {

    @Test
    public void authoredPhysicsAuthorityRejectsEveryAuthoredComponentIndividually() {
        assertAuthoredPhysicsRejected(PhysicsBodyComponent.class);
        assertAuthoredPhysicsRejected(PhysicsShapesComponent.class);
        assertAuthoredPhysicsRejected(PhysicsJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsDistanceJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsRevoluteJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsPrismaticJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsWheelJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsFrictionJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsMotorJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsWeldJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsPulleyJointComponent.class);
        assertAuthoredPhysicsRejected(PhysicsGearJointComponent.class);
    }

    @Test
    public void authoredPhysicsAuthorityAcceptsTransformOnlyEntity() {
        World world = new World();
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        IntBag entities = new IntBag();
        entities.add(entityId);

        PhysicsService.requireNoAuthoredPhysics(world, entities, "Test content");

        Assert.assertTrue(world.getEntityManager().isActive(entityId));
        Assert.assertTrue(world.getMapper(TransformComponent.class).has(entityId));
        assertOnlyExpectedAuthoredComponent(world, entityId, null);
    }

    @Test
    public void rebuildPreparedBodyCachesNormalizesBodyWithoutShapes() {
        World world = new World();
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        world.getMapper(PhysicsBodyComponent.class).create(entityId);

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
    public void bodyPresenceDefinesAuthoredPhysicsEvenWithoutShapes() {
        World world = new World();
        PhysicsService physics = new PhysicsService(
                world, null, new games.pixscape.runtime.loading.SceneMetaRuntime());
        int entityId = world.create();

        Assert.assertFalse(physics.hasPhysics(entityId));
        world.getMapper(PhysicsBodyComponent.class).create(entityId);
        Assert.assertTrue(physics.hasPhysics(entityId));
        Assert.assertFalse(physics.hasShapes(entityId));
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
        invalid.geometry = new PhysicsGeometryData();
        invalid.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        invalid.geometry.polygonVertices = new float[]{0f, 0f, 1f, 0f};
        invalid.geometry.polygonVertexCount = 2;
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
    public void bodyRemovalClosureIncludesDependentGearBeforeDirectSource() {
        GdxNativesLoader.load();
        Box2dWorldService box2d =
                new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        try {
            DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
            Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
            World world = new World(
                    new WorldConfigurationBuilder().with(dirty, sync).build());
            PhysicsService physics = new PhysicsService(
                    world,
                    box2d,
                    new games.pixscape.runtime.loading.SceneMetaRuntime());

            int staticA = createBody(world, physics, 0f, PhysicsBodyComponent.STATIC);
            int dynamicA = createBody(world, physics, 100f, PhysicsBodyComponent.DYNAMIC);
            int staticB = createBody(world, physics, 200f, PhysicsBodyComponent.STATIC);
            int dynamicB = createBody(world, physics, 300f, PhysicsBodyComponent.DYNAMIC);
            int source1 = physics.createRevoluteJoint(staticA, dynamicA, 50f, 0f);
            int source2 = physics.createPrismaticJoint(staticB, dynamicB, 250f, 0f);
            int gear = physics.createGearJoint(source1, source2, 2f);

            processPhysics(world);

            IntArray affected = physics.collectJointsAffectedByBodyRemoval(
                    staticA, new IntArray(false, 4));
            Assert.assertEquals(2, affected.size);
            Assert.assertEquals(gear, affected.get(0));
            Assert.assertEquals(source1, affected.get(1));
            Assert.assertFalse(affected.contains(source2));
            Assert.assertEquals(3, box2d.world.getJointCount());

            physics.removePhysics(staticA);
            processPhysics(world);

            Assert.assertFalse(world.getEntityManager().isActive(source1));
            Assert.assertFalse(world.getEntityManager().isActive(gear));
            Assert.assertTrue(world.getEntityManager().isActive(source2));
            Assert.assertEquals(1, box2d.world.getJointCount());
        } finally {
            box2d.dispose();
        }
    }

    @Test
    public void entityRemovalClosureIncludesDependentGearBeforeRequestedSourceOnly() {
        GdxNativesLoader.load();
        Box2dWorldService box2d =
                new Box2dWorldService(100f, new Vector2(0f, -9.8f));
        try {
            DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
            Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
            World world = new World(
                    new WorldConfigurationBuilder().with(dirty, sync).build());
            PhysicsService physics = new PhysicsService(
                    world,
                    box2d,
                    new games.pixscape.runtime.loading.SceneMetaRuntime());

            int staticA = createBody(world, physics, 0f, PhysicsBodyComponent.STATIC);
            int dynamicA = createBody(world, physics, 100f, PhysicsBodyComponent.DYNAMIC);
            int staticB = createBody(world, physics, 200f, PhysicsBodyComponent.STATIC);
            int dynamicB = createBody(world, physics, 300f, PhysicsBodyComponent.DYNAMIC);
            int source1 = physics.createRevoluteJoint(staticA, dynamicA, 50f, 0f);
            int source2 = physics.createPrismaticJoint(staticB, dynamicB, 250f, 0f);
            int gear = physics.createGearJoint(source1, source2, 2f);
            processPhysics(world);

            IntArray removed = new IntArray(1);
            removed.add(source1);
            IntArray affected = PhysicsService.collectJointsAffectedByEntityRemoval(
                    world, removed, null);

            Assert.assertEquals(2, affected.size);
            Assert.assertEquals(gear, affected.get(0));
            Assert.assertEquals(source1, affected.get(1));
            Assert.assertFalse(affected.contains(source2));
            Assert.assertFalse(affected.contains(staticA));
            Assert.assertFalse(affected.contains(dynamicA));
            Assert.assertFalse(affected.contains(staticB));
            Assert.assertFalse(affected.contains(dynamicB));

            removed.clear();
            removed.add(gear);
            affected = PhysicsService.collectJointsAffectedByEntityRemoval(
                    world, removed, affected);

            Assert.assertEquals(1, affected.size);
            Assert.assertEquals(gear, affected.get(0));
            Assert.assertFalse(affected.contains(source1));
            Assert.assertFalse(affected.contains(source2));
        } finally {
            box2d.dispose();
        }
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

    private static int createBody(
            World world, PhysicsService physics, float x, int type) {
        int entityId = world.create();
        TransformComponent transform =
                world.getMapper(TransformComponent.class).create(entityId);
        transform.x = x;
        physics.ensurePhysics(entityId);
        world.getMapper(PhysicsBodyComponent.class).get(entityId).type = type;
        return entityId;
    }

    private static void processPhysics(World world) {
        world.process();
        world.process();
    }

    private static <T extends Component> void assertAuthoredPhysicsRejected(
            Class<T> componentType) {
        World world = new World();
        int entityId = world.create();
        world.getMapper(componentType).create(entityId);
        IntBag entities = new IntBag();
        entities.add(entityId);

        try {
            PhysicsService.requireNoAuthoredPhysics(
                    world, entities, "Test content");
            Assert.fail(componentType.getSimpleName() + " must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(
                    expected.getMessage(),
                    expected.getMessage().contains(componentType.getSimpleName()));
        }

        Assert.assertTrue(world.getEntityManager().isActive(entityId));
        assertOnlyExpectedAuthoredComponent(world, entityId, componentType);
    }

    private static void assertOnlyExpectedAuthoredComponent(
            World world, int entityId, Class<? extends Component> expectedType) {
        Assert.assertEquals(expectedType == PhysicsBodyComponent.class,
                world.getMapper(PhysicsBodyComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsShapesComponent.class,
                world.getMapper(PhysicsShapesComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsJointComponent.class,
                world.getMapper(PhysicsJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsDistanceJointComponent.class,
                world.getMapper(PhysicsDistanceJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsRevoluteJointComponent.class,
                world.getMapper(PhysicsRevoluteJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsPrismaticJointComponent.class,
                world.getMapper(PhysicsPrismaticJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsWheelJointComponent.class,
                world.getMapper(PhysicsWheelJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsFrictionJointComponent.class,
                world.getMapper(PhysicsFrictionJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsMotorJointComponent.class,
                world.getMapper(PhysicsMotorJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsWeldJointComponent.class,
                world.getMapper(PhysicsWeldJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsPulleyJointComponent.class,
                world.getMapper(PhysicsPulleyJointComponent.class).has(entityId));
        Assert.assertEquals(expectedType == PhysicsGearJointComponent.class,
                world.getMapper(PhysicsGearJointComponent.class).has(entityId));
    }

}
