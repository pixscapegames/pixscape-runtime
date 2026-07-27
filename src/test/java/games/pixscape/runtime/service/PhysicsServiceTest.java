package games.pixscape.runtime.service;

import com.artemis.Component;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
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
        assertAuthoredPhysicsRejected(BlockPhysicsBindingsComponent.class);
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
    public void linkedReservedBodyPublishesOnlyAfterRepositoryResolution() {
        World world = new World();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.pixelsPerMeter = 32f;
        meta.nextEntityStableId = 2;
        IdentityRegistry identities = new IdentityRegistry();
        BlockPhysicsBindingRepository repository = new BlockPhysicsBindingRepository();
        identities.bind(world, meta);
        repository.bind(world, identities);
        try {
            int owner = world.create();
            PixscapeIdentityComponent identity = world
                    .getMapper(PixscapeIdentityComponent.class).create(owner);
            identity.stableId = 1;
            SpatialBlocksComponent blocks = world
                    .getMapper(SpatialBlocksComponent.class).create(owner);
            SpatialBlockData block = new SpatialBlockData();
            block.id = 4;
            block.structureId = 1;
            block.width = 1f;
            block.depth = 1f;
            blocks.blocks.add(block);
            blocks.nextSpatialBlockId = 5;
            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = 8;
            linked.directGeometry = null;
            linked.density = 3f;
            linked.friction = .4f;
            linked.restitution = .2f;
            linked.sensor = true;
            linked.categoryBits = 2;
            linked.maskBits = 4;
            linked.groupIndex = 6;
            world.getMapper(PhysicsShapesComponent.class).create(owner).shapes.add(linked);
            BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
            binding.spatialBlockId = 4;
            binding.physicsShapeId = 8;
            world.getMapper(BlockPhysicsBindingsComponent.class)
                    .create(owner).bindings.add(binding);
            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(owner);
            body.type = PhysicsBodyComponent.STATIC;
            body.fixedRotation = true;
            world.getMapper(TransformComponent.class).create(owner);
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(owner);
            tiled.data = new TiledMapLayerData(4, 4, 32, 32, 2,
                    SceneMetaRuntime.TiledProjection.ORTHO);

            world.process();
            identities.rebuild();
            repository.rebuild();
            PhysicsService.rebuildPreparedBodyCaches(world, repository, meta);

            PhysicsCompiledFixturesComponent compiled = world
                    .getMapper(PhysicsCompiledFixturesComponent.class).get(owner);
            Assert.assertTrue(compiled.valid);
            Assert.assertEquals(1, compiled.fixtures.size);
            CompiledFixtureData fixture = compiled.fixtures.first();
            Assert.assertEquals(8, fixture.physicsShapeId);
            Assert.assertEquals(3f, fixture.density, 0f);
            Assert.assertEquals(2, fixture.categoryBits);
            Assert.assertTrue(fixture.sensor);
        } finally {
            repository.clear();
            identities.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void linkedIsoMultiBindingAndMixedDirectWorldPreserveAuthoredOrderAndCopies() {
        World world = new World();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.pixelsPerMeter = 32f;
        meta.nextEntityStableId = 2;
        IdentityRegistry identities = new IdentityRegistry();
        BlockPhysicsBindingRepository repository = new BlockPhysicsBindingRepository();
        identities.bind(world, meta);
        repository.bind(world, identities);
        try {
            int owner = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
            SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(owner);
            SpatialBlockData firstBlock = new SpatialBlockData();
            firstBlock.id = 11; firstBlock.structureId = 1; firstBlock.x = 1f;
            firstBlock.y = 2f; firstBlock.width = 2f; firstBlock.depth = 1f;
            SpatialBlockData secondBlock = firstBlock.copy();
            secondBlock.id = 12; secondBlock.x = 4f; secondBlock.y = 1f;
            blocks.blocks.add(firstBlock); blocks.blocks.add(secondBlock);
            blocks.nextSpatialBlockId = 13;
            PhysicsShapesComponent ownerShapes = world.getMapper(PhysicsShapesComponent.class).create(owner);
            PhysicsShapeData shapeA = linkedShape(22, 2f, .3f, .1f, (short) 2, (short) 4, (short) 6);
            PhysicsShapeData shapeB = linkedShape(21, 3f, .4f, .2f, (short) 8, (short) 16, (short) 10);
            ownerShapes.shapes.add(shapeA); ownerShapes.shapes.add(shapeB);
            BlockPhysicsBindingsComponent ownerBindings = world.getMapper(BlockPhysicsBindingsComponent.class).create(owner);
            ownerBindings.bindings.add(binding(12, 22)); ownerBindings.bindings.add(binding(11, 21));
            PhysicsBodyComponent ownerBody = world.getMapper(PhysicsBodyComponent.class).create(owner);
            ownerBody.type = PhysicsBodyComponent.STATIC; ownerBody.fixedRotation = true;
            world.getMapper(TransformComponent.class).create(owner);
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(owner);
            tiled.data = new TiledMapLayerData(16, 16, 32, 32, 4, SceneMetaRuntime.TiledProjection.ISO);

            int direct = world.create();
            world.getMapper(TransformComponent.class).create(direct);
            world.getMapper(PhysicsBodyComponent.class).create(direct);
            PhysicsShapeData directSource = new PhysicsShapeData();
            directSource.physicsShapeId = 30;
            directSource.directGeometry = new PhysicsDirectGeometryData();
            directSource.directGeometry.halfWidth = 1f;
            directSource.directGeometry.halfHeight = 2f;
            world.getMapper(PhysicsShapesComponent.class).create(direct).shapes.add(directSource);

            world.process(); identities.rebuild(); repository.rebuild();
            PhysicsService.rebuildPreparedBodyCaches(world, repository, meta);
            PhysicsCompiledFixturesComponent linkedCache = world.getMapper(PhysicsCompiledFixturesComponent.class).get(owner);
            Assert.assertTrue(linkedCache.valid); Assert.assertEquals(2, linkedCache.fixtures.size);
            Assert.assertEquals(22, linkedCache.fixtures.get(0).physicsShapeId);
            Assert.assertEquals(21, linkedCache.fixtures.get(1).physicsShapeId);
            Assert.assertEquals(4, linkedCache.fixtures.get(0).polygonVertexCount);
            Assert.assertEquals(2f, linkedCache.fixtures.get(0).density, 0f);
            Assert.assertEquals(8, linkedCache.fixtures.get(1).categoryBits);
            Assert.assertTrue(linkedCache.fixtures.get(1).polygonVertices != linkedCache.fixtures.get(0).polygonVertices);
            Assert.assertTrue(world.getMapper(PhysicsCompiledFixturesComponent.class).get(direct).valid);
            Assert.assertNotSame(shapeA, world.getMapper(PhysicsShapesComponent.class).get(owner).shapes.get(0));
            float cachedX = linkedCache.fixtures.get(0).polygonVertices[0];
            firstBlock.x = 99f; shapeA.density = 99f;
            Assert.assertEquals(cachedX, linkedCache.fixtures.get(0).polygonVertices[0], 0f);
            Assert.assertEquals(2f, linkedCache.fixtures.get(0).density, 0f);
        } finally { repository.clear(); identities.bind(null, null); world.dispose(); }
    }

    private static PhysicsShapeData linkedShape(int id, float density, float friction,
                                                float restitution, short category,
                                                short mask, short group) {
        PhysicsShapeData shape = new PhysicsShapeData(); shape.physicsShapeId = id;
        shape.density = density; shape.friction = friction; shape.restitution = restitution;
        shape.categoryBits = category; shape.maskBits = mask; shape.groupIndex = group;
        return shape;
    }

    private static BlockPhysicsBindingData binding(int blockId, int shapeId) {
        BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
        binding.spatialBlockId = blockId; binding.physicsShapeId = shapeId; return binding;
    }

    @Test
    public void reservedLinkedBodyRejectsMissingComponentsAndInvalidCanonicalState() {
        assertReservedRejects("PixscapeIdentityComponent", h ->
                h.world.getMapper(PixscapeIdentityComponent.class).remove(h.owner));
        assertReservedRejects("positive", h ->
                h.world.getMapper(PixscapeIdentityComponent.class).get(h.owner).stableId = 0);
        assertReservedRejects("required component", h ->
                h.world.getMapper(PhysicsBodyComponent.class).remove(h.owner));
        assertReservedRejects("required component", h ->
                h.world.getMapper(TransformComponent.class).remove(h.owner));
        assertReservedRejects("PhysicsShapesComponent", h ->
                h.world.getMapper(PhysicsShapesComponent.class).remove(h.owner));
        assertReservedRejects("required component", h ->
                h.world.getMapper(TiledLayerComponent.class).remove(h.owner));
        assertReservedRejects("TiledLayerComponent.data", h ->
                h.world.getMapper(TiledLayerComponent.class).get(h.owner).data = null);
        assertReservedRejects("identity transform", h ->
                h.world.getMapper(TransformComponent.class).get(h.owner).x = 1f);
        assertReservedRejects("identity transform", h ->
                h.world.getMapper(TransformComponent.class).get(h.owner).originY = 1f);
        assertReservedRejects("identity transform", h ->
                h.world.getMapper(TransformComponent.class).get(h.owner).rotationRad = 1f);
        assertReservedRejects("identity transform", h ->
                h.world.getMapper(TransformComponent.class).get(h.owner).scaleX = 2f);
        assertReservedRejects("canonical static profile", h ->
                h.world.getMapper(PhysicsBodyComponent.class).get(h.owner).bullet = true);
        assertReservedRejects("canonical static profile", h ->
                h.world.getMapper(PhysicsBodyComponent.class).get(h.owner).gravityScale = 2f);
        assertReservedRejects("canonical static profile", h ->
                h.world.getMapper(PhysicsBodyComponent.class).get(h.owner).linearDamping = 1f);
        assertReservedRejects("direct-geometry", h ->
                h.shapes.shapes.first().directGeometry = new PhysicsDirectGeometryData());
        assertReservedRejects("linked shape must be enabled", h -> h.shapes.shapes.first().enabled = false);
        assertReservedRejects("pixelsPerMeter", h -> h.meta.pixelsPerMeter = 0f);
        assertReservedRejects("pixelsPerMeter", h -> h.meta.pixelsPerMeter = Float.NaN);
    }

    @Test
    public void reservedLinkedBodyRejectsJointsAndGlobalFailurePublishesNothing() {
        assertReservedRejects("joint references", h -> {
            int joint = h.world.create();
            h.world.getMapper(PhysicsJointComponent.class).create(joint).aEid = h.owner;
        });
        assertReservedRejects("joint references", h -> {
            int joint = h.world.create();
            h.world.getMapper(PhysicsJointComponent.class).create(joint).bEid = h.owner;
        });
        ReservedHarness h = new ReservedHarness();
        try {
            int directWithoutShapes = h.world.create();
            h.world.getMapper(PhysicsBodyComponent.class).create(directWithoutShapes);
            int direct = h.world.create();
            h.world.getMapper(PhysicsBodyComponent.class).create(direct);
            PhysicsCompiledFixturesComponent sentinel = h.world.getMapper(
                    PhysicsCompiledFixturesComponent.class).create(direct);
            CompiledFixtureData fixture = new CompiledFixtureData();
            sentinel.fixtures.add(fixture); sentinel.generation = 9; sentinel.valid = true;
            h.world.getMapper(TransformComponent.class).create(direct);
            h.world.getMapper(PhysicsShapesComponent.class).create(direct).shapes.add(directShape(40));
            h.world.getMapper(TransformComponent.class).get(h.owner).x = 1f;
            h.activate();
            Assert.assertThrows(IllegalArgumentException.class, () ->
                    PhysicsService.rebuildPreparedBodyCaches(h.world, h.repository, h.meta));
            Assert.assertFalse(h.world.getMapper(PhysicsShapesComponent.class).has(directWithoutShapes));
            Assert.assertSame(sentinel, h.world.getMapper(PhysicsCompiledFixturesComponent.class).get(direct));
            Assert.assertSame(fixture, sentinel.fixtures.first()); Assert.assertEquals(9, sentinel.generation);
            Assert.assertFalse(h.world.getMapper(PhysicsCompiledFixturesComponent.class).has(h.owner));
        } finally { h.close(); }
    }

    private static PhysicsShapeData directShape(int id) {
        PhysicsShapeData shape = new PhysicsShapeData(); shape.physicsShapeId = id;
        shape.directGeometry = new PhysicsDirectGeometryData(); return shape;
    }

    private static void assertReservedRejects(String fragment, ReservedMutation mutation) {
        ReservedHarness h = new ReservedHarness();
        try {
            mutation.apply(h);
            RuntimeException failure = Assert.assertThrows(RuntimeException.class, () -> {
                h.activate();
                h.repository.rebuild();
                PhysicsService.rebuildPreparedBodyCaches(h.world, h.repository, h.meta);
            });
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(fragment));
        } finally { h.close(); }
    }

    private interface ReservedMutation { void apply(ReservedHarness harness); }

    private static final class ReservedHarness {
        final World world = new World(); final SceneMetaRuntime meta = new SceneMetaRuntime();
        final IdentityRegistry identities = new IdentityRegistry();
        final BlockPhysicsBindingRepository repository = new BlockPhysicsBindingRepository();
        final int owner; final PhysicsShapesComponent shapes;
        ReservedHarness() {
            meta.nextEntityStableId = 2; identities.bind(world, meta); repository.bind(world, identities);
            owner = world.create(); world.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
            SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(owner);
            SpatialBlockData block = new SpatialBlockData(); block.id = 1; block.structureId = 1;
            block.width = 1f; block.depth = 1f; blocks.blocks.add(block); blocks.nextSpatialBlockId = 2;
            shapes = world.getMapper(PhysicsShapesComponent.class).create(owner); shapes.shapes.add(linkedShape(1, 1f, .2f, 0f, (short) 1, (short) -1, (short) 0));
            world.getMapper(BlockPhysicsBindingsComponent.class).create(owner).bindings.add(binding(1, 1));
            PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(owner); body.type = PhysicsBodyComponent.STATIC; body.fixedRotation = true;
            world.getMapper(TransformComponent.class).create(owner);
            world.getMapper(TiledLayerComponent.class).create(owner).data = new TiledMapLayerData(4,4,32,32,2,SceneMetaRuntime.TiledProjection.ORTHO);
        }
        void activate() { world.process(); identities.rebuild(); }
        void close() { repository.clear(); identities.bind(null, null); world.dispose(); }
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
        Assert.assertEquals(expectedType == BlockPhysicsBindingsComponent.class,
                world.getMapper(BlockPhysicsBindingsComponent.class).has(entityId));
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
