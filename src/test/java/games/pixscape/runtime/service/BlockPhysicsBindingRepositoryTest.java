package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BlockPhysicsBindingRepositoryTest {

    @Test
    public void bindingDataHasOnlyTheTwoOwnerLocalFieldsAndCopiesIndependently() {
        Field[] fields = BlockPhysicsBindingData.class.getDeclaredFields();
        Assert.assertEquals(2, fields.length);
        assertField(fields, "spatialBlockId", int.class);
        assertField(fields, "physicsShapeId", int.class);

        BlockPhysicsBindingData source = binding(7, 41);
        BlockPhysicsBindingData copy = source.copy();
        copy.spatialBlockId = 8;
        copy.physicsShapeId = 42;

        Assert.assertEquals(7, source.spatialBlockId);
        Assert.assertEquals(41, source.physicsShapeId);
    }

    @Test
    public void pooledComponentResetClearsAndReusesItsTypedCollection() throws Exception {
        BlockPhysicsBindingsComponent component = new BlockPhysicsBindingsComponent();
        Array<BlockPhysicsBindingData> original = component.bindings;
        BlockPhysicsBindingData oldBinding = binding(1, 101);
        original.add(oldBinding);

        Method reset = BlockPhysicsBindingsComponent.class.getDeclaredMethod("reset");
        reset.setAccessible(true);
        reset.invoke(component);

        Assert.assertSame(original, component.bindings);
        Assert.assertEquals(0, component.bindings.size);
        BlockPhysicsBindingData replacement = binding(2, 102);
        component.bindings.add(replacement);
        Assert.assertSame(replacement, component.bindings.first());
        Assert.assertFalse(component.bindings.contains(oldBinding, true));

        component.bindings = null;
        reset.invoke(component);
        Assert.assertNotNull(component.bindings);
        Assert.assertEquals(0, component.bindings.size);
    }

    @Test
    public void bindDetachClearAndUnboundLifecycleAreStrict() {
        BlockPhysicsBindingRepository repository = new BlockPhysicsBindingRepository();
        Assert.assertFalse(repository.hasAnyBindings());
        Assert.assertThrows(IllegalStateException.class, repository::rebuild);

        try (Harness harness = new Harness()) {
            Assert.assertThrows(
                    IllegalArgumentException.class,
                    () -> repository.bind(harness.world, null));
            Assert.assertThrows(
                    IllegalArgumentException.class,
                    () -> repository.bind(null, harness.identityRegistry));

            Owner owner = validOwner(harness, 11, 1, 101);
            harness.activate();
            repository.bind(harness.world, harness.identityRegistry);
            Assert.assertFalse(repository.hasAnyBindings());
            repository.rebuild();
            Assert.assertTrue(repository.hasAnyBindings());
            Assert.assertTrue(repository.hasBinding(11, 1));

            repository.bind(harness.world, harness.identityRegistry);
            Assert.assertFalse(repository.hasAnyBindings());
            Assert.assertFalse(repository.hasBinding(11, 1));
            repository.rebuild();
            Assert.assertTrue(repository.hasAnyBindings());
            Assert.assertTrue(repository.hasBinding(11, 1));

            repository.bind(null, null);
            Assert.assertFalse(repository.hasAnyBindings());
            Assert.assertFalse(repository.hasBinding(11, 1));
            Assert.assertThrows(IllegalStateException.class, repository::rebuild);

            repository.bind(harness.world, harness.identityRegistry);
            repository.rebuild();
            repository.clear();
            Assert.assertFalse(repository.hasAnyBindings());
            Assert.assertFalse(repository.hasBinding(11, 1));
            Assert.assertThrows(IllegalStateException.class, repository::rebuild);
            Assert.assertTrue(harness.world.getEntityManager().isActive(owner.entityId));
        }
    }

    @Test
    public void emptyWorldAndOwnersWithoutBindingBuildEmptyIndexesWithoutMutation() {
        try (Harness harness = new Harness()) {
            int emptyOwner = harness.world.create();
            harness.identityRegistry.setIdentity(emptyOwner, 12, "empty-owner");
            harness.world.getMapper(SpatialBlocksComponent.class).create(emptyOwner);
            harness.activate();
            int entityCount = entityCount(harness.world);

            harness.repository.rebuild();

            Assert.assertFalse(harness.repository.hasAnyBindings());
            Assert.assertEquals(entityCount, entityCount(harness.world));
            assertEmptyQueries(harness.repository, 12);
            Assert.assertFalse(
                    harness.world.getMapper(BlockPhysicsBindingsComponent.class).has(emptyOwner));
        }

        try (Harness harness = new Harness()) {
            harness.repository.rebuild();
            Assert.assertFalse(harness.repository.hasAnyBindings());
            Assert.assertEquals(0, entityCount(harness.world));
            assertEmptyQueries(harness.repository, 1);
        }
    }

    @Test
    public void validOwnerBuildsAllIndexesAndPreservesOwnerOrder() {
        try (Harness harness = new Harness()) {
            Owner owner = owner(harness, 21, new int[]{5, 2}, new int[]{105, 102});
            PhysicsShapeData manual = directShape(999);
            owner.shapes.shapes.add(manual);
            harness.activate();

            harness.repository.rebuild();

            Assert.assertTrue(harness.repository.hasBinding(21, 5));
            Assert.assertTrue(harness.repository.hasBinding(21, 2));
            Assert.assertEquals(105,
                    harness.repository.findByBlock(21, 5).physicsShapeId);
            Assert.assertEquals(2,
                    harness.repository.findByPhysicsShapeId(102).spatialBlockId);
            Assert.assertEquals(5, harness.repository.findBlock(21, 5).id);
            Assert.assertEquals(owner.entityId,
                    harness.repository.findOwnerEntityByPhysicsShapeId(105));
            Assert.assertNull(harness.repository.findByPhysicsShapeId(999));

            Array<BlockPhysicsBindingData> out =
                    new Array<>(BlockPhysicsBindingData[]::new);
            out.add(binding(99, 999));
            harness.repository.bindingsForOwner(21, out);
            Assert.assertEquals(2, out.size);
            Assert.assertEquals(5, out.get(0).spatialBlockId);
            Assert.assertEquals(2, out.get(1).spatialBlockId);
        }
    }

    @Test
    public void mutableQueryResultsAreDefensiveCopies() {
        try (Harness harness = new Harness()) {
            validOwner(harness, 22, 7, 107);
            harness.activate();
            harness.repository.rebuild();

            BlockPhysicsBindingData byBlock = harness.repository.findByBlock(22, 7);
            BlockPhysicsBindingData byShape =
                    harness.repository.findByPhysicsShapeId(107);
            SpatialBlockData block = harness.repository.findBlock(22, 7);
            Array<BlockPhysicsBindingData> out =
                    new Array<>(BlockPhysicsBindingData[]::new);
            harness.repository.bindingsForOwner(22, out);

            byBlock.spatialBlockId = 99;
            byShape.physicsShapeId = 999;
            block.id = 88;
            out.first().spatialBlockId = 77;
            out.clear();

            Assert.assertEquals(7,
                    harness.repository.findByBlock(22, 7).spatialBlockId);
            Assert.assertEquals(107,
                    harness.repository.findByPhysicsShapeId(107).physicsShapeId);
            Assert.assertEquals(7, harness.repository.findBlock(22, 7).id);
            harness.repository.bindingsForOwner(22, out);
            Assert.assertEquals(1, out.size);
            Assert.assertEquals(7, out.first().spatialBlockId);
        }
    }

    @Test
    public void invalidBindingComponentStatesAreRejected() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 31, 1, 101);
            owner.bindings.bindings = null;
            expectInvalid(harness, "bindings collection is null");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 32, 1, 102);
            owner.bindings.bindings.clear();
            expectInvalid(harness, "component is empty");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 33, 1, 103);
            owner.bindings.bindings.set(0, null);
            expectInvalid(harness, "binding entry is null");
        }
    }

    @Test
    public void missingOrInvalidOwnerStructureIsRejected() {
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            addValidComponentsWithoutIdentity(harness, entity, 1, 101);
            expectInvalid(harness, "PixscapeIdentityComponent is missing");
        }
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            PixscapeIdentityComponent identity =
                    harness.world.getMapper(PixscapeIdentityComponent.class).create(entity);
            identity.stableId = 0;
            addValidComponentsWithoutIdentity(harness, entity, 1, 101);
            harness.identityRegistry.bind(null, null);
            expectInvalid(harness, "stableId must be positive");
        }
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            harness.identityRegistry.setIdentity(entity, 34, "no-blocks");
            addBindingsAndShapes(harness, entity, 1, 104);
            expectInvalid(harness, "SpatialBlocksComponent is missing");
        }
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            harness.identityRegistry.setIdentity(entity, 35, "no-shapes");
            addBlocks(harness, entity, 1);
            BlockPhysicsBindingsComponent bindings = harness.world
                    .getMapper(BlockPhysicsBindingsComponent.class).create(entity);
            bindings.bindings.add(binding(1, 105));
            expectInvalid(harness, "PhysicsShapesComponent is missing");
        }
    }

    @Test
    public void invalidSpatialBlockCollectionsIdsAndHighWaterAreRejected() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 41, 1, 101);
            owner.blocks.blocks = null;
            expectInvalid(harness, "blocks collection is null");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 42, 1, 102);
            owner.blocks.blocks.set(0, null);
            expectInvalid(harness, "block entry is null");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 43, 1, 103);
            owner.blocks.blocks.first().id = 0;
            expectInvalid(harness, "block ID must be positive");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 44, 1, 104);
            owner.blocks.blocks.add(block(1));
            expectInvalid(harness, "duplicate spatial block ID");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 45, 1, 105);
            owner.bindings.bindings.first().spatialBlockId = 2;
            IllegalStateException error =
                    expectInvalid(harness, "block absent from the same owner");
            assertDiagnostic(error, owner.entityId, 45, 2, 105);
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 46, 1, 106);
            owner.blocks.nextSpatialBlockId = 0;
            expectInvalid(harness, "nextSpatialBlockId must be positive");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 47, 1, 107);
            owner.blocks.nextSpatialBlockId = 1;
            expectInvalid(harness, "greater than the maximum block ID");
        }
    }

    @Test
    public void missingForeignDirectDisabledAndOrphanShapesAreRejected() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 51, 1, 101);
            owner.shapes.shapes.clear();
            expectInvalid(harness, "shape absent from the same owner");
        }
        try (Harness harness = new Harness()) {
            Owner first = validOwner(harness, 52, 1, 102);
            first.shapes.shapes.clear();
            int second = harness.world.create();
            harness.identityRegistry.setIdentity(second, 53, "foreign");
            PhysicsShapesComponent shapes =
                    harness.world.getMapper(PhysicsShapesComponent.class).create(second);
            shapes.shapes.add(linkedShape(102));
            expectInvalid(harness, "shape absent from the same owner");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 54, 1, 104);
            owner.shapes.shapes.first().directGeometry =
                    new PhysicsDirectGeometryData();
            expectInvalid(harness, "direct-geometry shape cannot be bound");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 55, 1, 105);
            owner.shapes.shapes.first().enabled = false;
            expectInvalid(harness, "linked shape must be enabled");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 56, 1, 106);
            owner.shapes.shapes.add(linkedShape(206));
            expectInvalid(harness, "linked shape has no owner-local binding");
        }
    }

    @Test
    public void linkedShapeWithoutBindingsComponentIsRejectedGlobally() {
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            harness.identityRegistry.setIdentity(entity, 57, "orphan-owner");
            addBlocks(harness, entity, 1);
            PhysicsShapesComponent shapes = harness.world
                    .getMapper(PhysicsShapesComponent.class).create(entity);
            shapes.shapes.add(linkedShape(207));

            IllegalStateException error =
                    expectInvalid(harness, "linked shape has no binding");

            Assert.assertTrue(error.getMessage().contains(
                    "ownerEntityId=" + entity));
            Assert.assertTrue(error.getMessage().contains(
                    "physicsShapeId=207"));
            Assert.assertFalse(harness.world
                    .getMapper(BlockPhysicsBindingsComponent.class).has(entity));
        }
    }

    @Test
    public void linkedShapeOnNonSpatialEntityIsRejectedGlobally() {
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            PhysicsShapesComponent shapes = harness.world
                    .getMapper(PhysicsShapesComponent.class).create(entity);
            shapes.shapes.add(linkedShape(208));

            IllegalStateException error =
                    expectInvalid(harness, "linked shape has no binding");

            Assert.assertTrue(error.getMessage().contains(
                    "ownerEntityId=" + entity));
            Assert.assertTrue(error.getMessage().contains(
                    "physicsShapeId=208"));
            Assert.assertFalse(harness.world
                    .getMapper(PixscapeIdentityComponent.class).has(entity));
            Assert.assertFalse(harness.world
                    .getMapper(SpatialBlocksComponent.class).has(entity));
        }
    }

    @Test
    public void directOnlyNonSpatialEntityIsAcceptedWithoutMutation() {
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            PhysicsShapesComponent shapes = harness.world
                    .getMapper(PhysicsShapesComponent.class).create(entity);
            PhysicsShapeData first = directShape(209);
            PhysicsShapeData second = directShape(210);
            shapes.shapes.add(first);
            shapes.shapes.add(second);
            harness.activate();
            int initialEntityCount = entityCount(harness.world);

            harness.repository.rebuild();

            assertEmptyQueries(harness.repository, 58);
            Assert.assertEquals(initialEntityCount, entityCount(harness.world));
            Assert.assertSame(first, shapes.shapes.get(0));
            Assert.assertSame(second, shapes.shapes.get(1));
            Assert.assertEquals(2, shapes.shapes.size);
            Assert.assertFalse(harness.world
                    .getMapper(BlockPhysicsBindingsComponent.class).has(entity));
            Assert.assertFalse(harness.world
                    .getMapper(SpatialBlocksComponent.class).has(entity));
        }
    }

    @Test
    public void linkedShapeCarriedByForeignEntityIsRejectedGlobally() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 59, 1, 211);
            int foreignEntity = harness.world.create();
            PhysicsShapesComponent foreignShapes = harness.world
                    .getMapper(PhysicsShapesComponent.class).create(foreignEntity);
            foreignShapes.shapes.add(linkedShape(211));

            IllegalStateException error =
                    expectInvalid(harness, "carried by another entity");

            Assert.assertTrue(error.getMessage().contains(
                    "ownerEntityId=" + foreignEntity));
            Assert.assertTrue(error.getMessage().contains(
                    "physicsShapeId=211"));
            Assert.assertTrue(error.getMessage().contains(
                    "expected ownerEntityId=" + owner.entityId));
        }
    }

    @Test
    public void failedGlobalLinkedShapePassPreservesPreviousIndexesAndHasNoSideEffects() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 60, 1, 212);
            int orphanEntity = harness.world.create();
            PhysicsShapesComponent orphanShapes = harness.world
                    .getMapper(PhysicsShapesComponent.class).create(orphanEntity);
            PhysicsShapeData orphan = directShape(213);
            orphanShapes.shapes.add(orphan);
            harness.activate();
            harness.repository.rebuild();

            int initialEntityCount = entityCount(harness.world);
            int initialNextEntityId = harness.sceneMeta.nextEntityStableId;
            int initialNextBlockId = owner.blocks.nextSpatialBlockId;
            orphan.directGeometry = null;

            IllegalStateException error = Assert.assertThrows(
                    IllegalStateException.class, harness.repository::rebuild);

            Assert.assertTrue(error.getMessage().contains(
                    "linked shape has no binding"));
            Assert.assertTrue(harness.repository.hasBinding(60, 1));
            Assert.assertEquals(212,
                    harness.repository.findByBlock(60, 1).physicsShapeId);
            Assert.assertEquals(owner.entityId,
                    harness.repository.findOwnerEntityByPhysicsShapeId(212));
            Assert.assertEquals(initialEntityCount, entityCount(harness.world));
            Assert.assertEquals(initialNextEntityId,
                    harness.sceneMeta.nextEntityStableId);
            Assert.assertEquals(initialNextBlockId,
                    owner.blocks.nextSpatialBlockId);
            Assert.assertEquals(0, harness.probe.processCalls);
            Assert.assertFalse(harness.dirty.isDirty(
                    owner.entityId, DirtyBits.EVERYTHING));
            Assert.assertFalse(harness.dirty.isDirty(
                    orphanEntity, DirtyBits.EVERYTHING));
            Assert.assertSame(orphan, orphanShapes.shapes.first());
            Assert.assertNull(orphan.directGeometry);
            Assert.assertFalse(harness.world
                    .getMapper(PhysicsBodyComponent.class).has(owner.entityId));
            Assert.assertFalse(harness.world
                    .getMapper(PhysicsBodyComponent.class).has(orphanEntity));
            Assert.assertFalse(harness.world
                    .getMapper(PhysicsCompiledFixturesComponent.class)
                    .has(owner.entityId));
            Assert.assertFalse(harness.world
                    .getMapper(PhysicsCompiledFixturesComponent.class)
                    .has(orphanEntity));
        }
    }

    @Test
    public void disabledLinkedShapeWithoutBindingIsRejectedGlobally() {
        try (Harness harness = new Harness()) {
            int entity = harness.world.create();
            PhysicsShapesComponent shapes = harness.world
                    .getMapper(PhysicsShapesComponent.class).create(entity);
            PhysicsShapeData shape = linkedShape(214);
            shape.enabled = false;
            shapes.shapes.add(shape);

            IllegalStateException error =
                    expectInvalid(harness, "linked shape has no binding");

            Assert.assertTrue(error.getMessage().contains(
                    "ownerEntityId=" + entity));
            Assert.assertTrue(error.getMessage().contains(
                    "physicsShapeId=214"));
            Assert.assertTrue(error.getMessage().contains("enabled=false"));
        }
    }

    @Test
    public void nullInvalidAndDuplicatePhysicsShapeIdentitiesAreRejected() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 61, 1, 101);
            owner.shapes.shapes.set(0, null);
            expectInvalid(harness, "physics shape entry is null");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 62, 1, 102);
            owner.shapes.shapes.first().physicsShapeId = 0;
            expectInvalid(harness, "physicsShapeId must be positive");
        }
        try (Harness harness = new Harness()) {
            Owner owner = owner(
                    harness, 63, new int[]{1, 2}, new int[]{103, 104});
            owner.bindings.bindings.get(1).physicsShapeId = 103;
            expectInvalid(harness, "bound more than once");
        }
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 64, 1, 104);
            owner.shapes.shapes.add(linkedShape(104));
            expectInvalid(harness, "duplicate physicsShapeId on the same owner");
        }
    }

    @Test
    public void oneBlockAndOnePhysicsShapeCannotHaveMultipleRelations() {
        try (Harness harness = new Harness()) {
            Owner owner = owner(
                    harness, 71, new int[]{1, 2}, new int[]{101, 102});
            owner.bindings.bindings.get(1).spatialBlockId = 1;
            expectInvalid(harness, "block has more than one binding");
        }
        try (Harness harness = new Harness()) {
            validOwner(harness, 72, 1, 201);
            validOwner(harness, 73, 1, 201);
            expectInvalid(harness, "bound more than once");
        }
    }

    @Test
    public void identicalLocalBlockIdsAcrossOwnersRemainDistinct() {
        try (Harness harness = new Harness()) {
            Owner first = validOwner(harness, 81, 1, 301);
            Owner second = validOwner(harness, 82, 1, 302);
            harness.activate();

            harness.repository.rebuild();

            Assert.assertEquals(301,
                    harness.repository.findByBlock(81, 1).physicsShapeId);
            Assert.assertEquals(302,
                    harness.repository.findByBlock(82, 1).physicsShapeId);
            Assert.assertEquals(first.entityId,
                    harness.repository.findOwnerEntityByPhysicsShapeId(301));
            Assert.assertEquals(second.entityId,
                    harness.repository.findOwnerEntityByPhysicsShapeId(302));
        }
    }

    @Test
    public void rawArtemisMutationStaysStaleUntilSuccessfulExplicitRebuild() {
        try (Harness harness = new Harness()) {
            Owner owner = owner(
                    harness, 91, new int[]{1, 2}, new int[]{401});
            harness.activate();
            harness.repository.rebuild();

            owner.bindings.bindings.first().spatialBlockId = 2;
            Assert.assertTrue(harness.repository.hasBinding(91, 1));
            Assert.assertFalse(harness.repository.hasBinding(91, 2));

            harness.repository.rebuild();
            Assert.assertFalse(harness.repository.hasBinding(91, 1));
            Assert.assertTrue(harness.repository.hasBinding(91, 2));
        }
    }

    @Test
    public void failedRebuildPreservesPreviousIndexesAndHasNoSideEffects() {
        try (Harness harness = new Harness()) {
            Owner owner = owner(
                    harness, 92, new int[]{1, 2}, new int[]{402});
            harness.activate();
            int initialEntityCount = entityCount(harness.world);
            int initialNextEntityId = harness.sceneMeta.nextEntityStableId;
            int initialNextBlockId = owner.blocks.nextSpatialBlockId;
            harness.repository.rebuild();

            owner.bindings.bindings.first().physicsShapeId = 0;
            Assert.assertThrows(
                    IllegalStateException.class, harness.repository::rebuild);

            Assert.assertTrue(harness.repository.hasBinding(92, 1));
            Assert.assertTrue(harness.repository.hasAnyBindings());
            Assert.assertEquals(402,
                    harness.repository.findByBlock(92, 1).physicsShapeId);
            Assert.assertEquals(initialEntityCount, entityCount(harness.world));
            Assert.assertEquals(initialNextEntityId,
                    harness.sceneMeta.nextEntityStableId);
            Assert.assertEquals(initialNextBlockId,
                    owner.blocks.nextSpatialBlockId);
            Assert.assertEquals(0, harness.probe.processCalls);
            Assert.assertFalse(harness.dirty.isDirty(
                    owner.entityId, DirtyBits.EVERYTHING));
            Assert.assertFalse(
                    harness.world.getMapper(PhysicsBodyComponent.class)
                            .has(owner.entityId));
            Assert.assertFalse(
                    harness.world.getMapper(PhysicsCompiledFixturesComponent.class)
                            .has(owner.entityId));
        }
    }

    @Test
    public void rebuildNeverProcessesWorldOrProducesDirtyOrHighWaterChanges() {
        try (Harness harness = new Harness()) {
            Owner owner = validOwner(harness, 93, 1, 403);
            harness.activate();
            int nextEntityStableId = harness.sceneMeta.nextEntityStableId;
            int nextSpatialBlockId = owner.blocks.nextSpatialBlockId;

            harness.repository.rebuild();

            Assert.assertEquals(0, harness.probe.processCalls);
            Assert.assertFalse(harness.dirty.isDirty(
                    owner.entityId, DirtyBits.EVERYTHING));
            Assert.assertEquals(nextEntityStableId,
                    harness.sceneMeta.nextEntityStableId);
            Assert.assertEquals(nextSpatialBlockId,
                    owner.blocks.nextSpatialBlockId);
        }
    }

    private static void assertEmptyQueries(
            BlockPhysicsBindingRepository repository, int ownerStableId) {
        Assert.assertFalse(repository.hasBinding(ownerStableId, 1));
        Assert.assertNull(repository.findByBlock(ownerStableId, 1));
        Assert.assertNull(repository.findByPhysicsShapeId(1));
        Assert.assertNull(repository.findBlock(ownerStableId, 1));
        Assert.assertEquals(-1, repository.findOwnerEntityByPhysicsShapeId(1));
        Array<BlockPhysicsBindingData> out =
                new Array<>(BlockPhysicsBindingData[]::new);
        out.add(binding(1, 1));
        repository.bindingsForOwner(ownerStableId, out);
        Assert.assertEquals(0, out.size);
        Assert.assertFalse(repository.hasBinding(0, 0));
        Assert.assertNull(repository.findByBlock(0, 0));
        Assert.assertNull(repository.findByPhysicsShapeId(0));
        Assert.assertNull(repository.findBlock(0, 0));
        Assert.assertEquals(-1, repository.findOwnerEntityByPhysicsShapeId(0));
    }

    private static Owner validOwner(
            Harness harness, int stableId, int blockId, int shapeId) {
        return owner(harness, stableId, new int[]{blockId}, new int[]{shapeId});
    }

    private static Owner owner(
            Harness harness, int stableId, int[] blockIds, int[] shapeIds) {
        int entity = harness.world.create();
        harness.identityRegistry.setIdentity(entity, stableId, "owner-" + stableId);
        Owner owner = new Owner();
        owner.entityId = entity;
        owner.blocks = harness.world.getMapper(SpatialBlocksComponent.class)
                .create(entity);
        int maxBlockId = 0;
        for (int i = 0; i < blockIds.length; i++) {
            owner.blocks.blocks.add(block(blockIds[i]));
            if (blockIds[i] > maxBlockId) maxBlockId = blockIds[i];
        }
        owner.blocks.nextSpatialBlockId = maxBlockId + 1;

        owner.shapes = harness.world.getMapper(PhysicsShapesComponent.class)
                .create(entity);
        owner.bindings = harness.world
                .getMapper(BlockPhysicsBindingsComponent.class).create(entity);
        for (int i = 0; i < shapeIds.length; i++) {
            owner.shapes.shapes.add(linkedShape(shapeIds[i]));
            owner.bindings.bindings.add(binding(blockIds[i], shapeIds[i]));
        }
        return owner;
    }

    private static void addValidComponentsWithoutIdentity(
            Harness harness, int entity, int blockId, int shapeId) {
        addBlocks(harness, entity, blockId);
        addBindingsAndShapes(harness, entity, blockId, shapeId);
    }

    private static void addBlocks(Harness harness, int entity, int blockId) {
        SpatialBlocksComponent blocks = harness.world
                .getMapper(SpatialBlocksComponent.class).create(entity);
        blocks.blocks.add(block(blockId));
        blocks.nextSpatialBlockId = blockId + 1;
    }

    private static void addBindingsAndShapes(
            Harness harness, int entity, int blockId, int shapeId) {
        PhysicsShapesComponent shapes = harness.world
                .getMapper(PhysicsShapesComponent.class).create(entity);
        shapes.shapes.add(linkedShape(shapeId));
        BlockPhysicsBindingsComponent bindings = harness.world
                .getMapper(BlockPhysicsBindingsComponent.class).create(entity);
        bindings.bindings.add(binding(blockId, shapeId));
    }

    private static SpatialBlockData block(int blockId) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = blockId;
        return block;
    }

    private static PhysicsShapeData linkedShape(int shapeId) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = shapeId;
        shape.directGeometry = null;
        shape.enabled = true;
        return shape;
    }

    private static PhysicsShapeData directShape(int shapeId) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = shapeId;
        shape.directGeometry = new PhysicsDirectGeometryData();
        return shape;
    }

    private static BlockPhysicsBindingData binding(int blockId, int shapeId) {
        BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
        binding.spatialBlockId = blockId;
        binding.physicsShapeId = shapeId;
        return binding;
    }

    private static IllegalStateException expectInvalid(
            Harness harness, String messagePart) {
        harness.activate();
        IllegalStateException error = Assert.assertThrows(
                IllegalStateException.class, harness.repository::rebuild);
        Assert.assertTrue(error.getMessage(),
                error.getMessage().contains(messagePart));
        return error;
    }

    private static void assertDiagnostic(
            IllegalStateException error,
            int ownerEntityId,
            int ownerStableId,
            int blockId,
            int physicsShapeId) {
        Assert.assertTrue(error.getMessage().contains(
                "ownerEntityId=" + ownerEntityId));
        Assert.assertTrue(error.getMessage().contains(
                "ownerStableId=" + ownerStableId));
        Assert.assertTrue(error.getMessage().contains("blockId=" + blockId));
        Assert.assertTrue(error.getMessage().contains(
                "physicsShapeId=" + physicsShapeId));
    }

    private static void assertField(
            Field[] fields, String name, Class<?> type) {
        for (int i = 0; i < fields.length; i++) {
            if (name.equals(fields[i].getName())) {
                Assert.assertEquals(type, fields[i].getType());
                return;
            }
        }
        Assert.fail("Missing field " + name);
    }

    private static int entityCount(World world) {
        return world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities().size();
    }

    private static final class Owner {
        int entityId;
        SpatialBlocksComponent blocks;
        PhysicsShapesComponent shapes;
        BlockPhysicsBindingsComponent bindings;
    }

    private static final class Harness implements AutoCloseable {
        final ProcessProbeSystem probe = new ProcessProbeSystem();
        final DirtyTrackerSystem dirty = new DirtyTrackerSystem(32);
        final World world = new World(new WorldConfigurationBuilder()
                .with(dirty, probe)
                .build());
        final SceneMetaRuntime sceneMeta = new SceneMetaRuntime();
        final IdentityRegistry identityRegistry = new IdentityRegistry();
        final BlockPhysicsBindingRepository repository =
                new BlockPhysicsBindingRepository();

        Harness() {
            sceneMeta.nextEntityStableId = 1000;
            identityRegistry.bind(world, sceneMeta);
            repository.bind(world, identityRegistry);
        }

        void activate() {
            world.process();
            dirty.clearAll();
            probe.processCalls = 0;
        }

        @Override
        public void close() {
            repository.clear();
            identityRegistry.bind(null, null);
            world.dispose();
        }
    }

    private static final class ProcessProbeSystem extends BaseSystem {
        int processCalls;

        @Override
        protected void processSystem() {
            processCalls++;
        }
    }
}
