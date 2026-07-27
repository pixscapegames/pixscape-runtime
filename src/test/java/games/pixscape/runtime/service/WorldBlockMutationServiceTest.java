package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class WorldBlockMutationServiceTest {
    @BeforeClass
    public static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    public void bindsFirstBlockWithACompleteReservedAggregate() {
        Fixture fixture = new Fixture();
        try {
            fixture.service.bindBlockCollision(1, 10);
            Assert.assertEquals(2, fixture.meta.nextPhysicsShapeId);
            assertFirstBinding(fixture, 1);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void bindsDistinctBlocksInAuthoredOrderAndUpdatesRepositoryIncrementally() {
        Fixture fixture = new Fixture();
        try {
            int first = fixture.service.bindBlockCollision(1, 10);
            int second = fixture.service.bindBlockCollision(1, 11);
            PhysicsShapesComponent shapes = fixture.world.getMapper(PhysicsShapesComponent.class).get(fixture.owner);
            BlockPhysicsBindingsComponent bindings = fixture.world.getMapper(BlockPhysicsBindingsComponent.class).get(fixture.owner);
            PhysicsCompiledFixturesComponent compiled = fixture.world.getMapper(PhysicsCompiledFixturesComponent.class).get(fixture.owner);
            Assert.assertEquals(2, bindings.bindings.size);
            Assert.assertEquals(10, bindings.bindings.get(0).spatialBlockId);
            Assert.assertEquals(first, bindings.bindings.get(0).physicsShapeId);
            Assert.assertEquals(11, bindings.bindings.get(1).spatialBlockId);
            Assert.assertEquals(second, bindings.bindings.get(1).physicsShapeId);
            Assert.assertEquals(first, shapes.shapes.get(0).physicsShapeId);
            Assert.assertEquals(second, shapes.shapes.get(1).physicsShapeId);
            Assert.assertNull(shapes.shapes.get(0).directGeometry);
            Assert.assertNull(shapes.shapes.get(1).directGeometry);
            Assert.assertEquals(2, compiled.fixtures.size);
            Assert.assertEquals(first, compiled.fixtures.get(0).physicsShapeId);
            Assert.assertEquals(second, compiled.fixtures.get(1).physicsShapeId);
            Assert.assertEquals(second, fixture.repository.findByBlock(1, 11).physicsShapeId);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void rejectsDoubleBindBeforeAllocatingAnotherShape() {
        Fixture fixture = new Fixture();
        try {
            int first = fixture.service.bindBlockCollision(1, 10);
            int highWater = fixture.meta.nextPhysicsShapeId;
            try {
                fixture.service.bindBlockCollision(1, 10);
                Assert.fail("A block may only own one binding.");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage().contains("already bound"));
            }
            Assert.assertEquals(highWater, fixture.meta.nextPhysicsShapeId);
            assertFirstBinding(fixture, first);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void rejectsInvalidRequestBeforeAllocationAndLeavesWorldUntouched() {
        Fixture fixture = new Fixture();
        try {
            try {
                fixture.service.bindBlockCollision(0, 10);
                Assert.fail("Invalid owner IDs must be rejected.");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage().contains("owner and block IDs"));
            }
            Assert.assertEquals(1, fixture.meta.nextPhysicsShapeId);
            Assert.assertFalse(fixture.repository.hasAnyBindings());
            Assert.assertFalse(fixture.world.getMapper(PhysicsShapesComponent.class).has(fixture.owner));
            Assert.assertFalse(fixture.world.getMapper(PhysicsBodyComponent.class).has(fixture.owner));
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void consumesHighWaterButPublishesNothingWhenResolutionFails() {
        Fixture fixture = new Fixture();
        try {
            fixture.world.getMapper(SpatialBlocksComponent.class).get(fixture.owner)
                    .blocks.first().width = 0f;
            try {
                fixture.service.bindBlockCollision(1, 10);
                Assert.fail("Invalid spatial geometry must reject the candidate.");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage().contains("width"));
            }
            Assert.assertEquals(2, fixture.meta.nextPhysicsShapeId);
            Assert.assertFalse(fixture.repository.hasAnyBindings());
            Assert.assertFalse(fixture.world.getMapper(BlockPhysicsBindingsComponent.class).has(fixture.owner));
            Assert.assertFalse(fixture.world.getMapper(PhysicsShapesComponent.class).has(fixture.owner));
            Assert.assertFalse(fixture.world.getMapper(PhysicsCompiledFixturesComponent.class).has(fixture.owner));
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void preparedMutationAndRepositoryDeltaAreSingleUse() {
        Fixture fixture = new Fixture();
        try {
            PreparedWorldBlockMutation prepared = fixture.service.prepareBind(1, 10);
            Assert.assertFalse(fixture.repository.hasAnyBindings());
            Assert.assertFalse(fixture.world.getMapper(PhysicsShapesComponent.class).has(fixture.owner));
            PreparedWorldBlockMutation.Publication publication = prepared.takePublication();
            try {
                prepared.takePublication();
                Assert.fail("Prepared mutation must reject a second transfer.");
            } catch (IllegalStateException expected) {
                // expected
            }
            publication.repositorySnapshot.applyTo(fixture.repository);
            try {
                publication.repositorySnapshot.applyTo(fixture.repository);
                Assert.fail("Repository delta must reject a second application.");
            } catch (IllegalStateException expected) {
                // expected
            }
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void rejectsStalePublishedBindingBeforeAllocation() {
        Fixture fixture = new Fixture();
        try {
            fixture.service.bindBlockCollision(1, 10);
            int highWater = fixture.meta.nextPhysicsShapeId;
            fixture.world.getMapper(BlockPhysicsBindingsComponent.class).get(fixture.owner)
                    .bindings.first().spatialBlockId = 11;
            try {
                fixture.service.bindBlockCollision(1, 11);
                Assert.fail("A stale repository must be rejected.");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("stale"));
            }
            Assert.assertEquals(highWater, fixture.meta.nextPhysicsShapeId);
            Assert.assertEquals(1, fixture.repository.findByBlock(1, 10).physicsShapeId);
            Assert.assertFalse(fixture.repository.hasBinding(1, 11));
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void detachedServiceRejectsBeforeWorldAccess() {
        Fixture fixture = new Fixture();
        try {
            fixture.service.detach();
            try {
                fixture.service.bindBlockCollision(1, 10);
                Assert.fail("Detached services must reject all public mutations.");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("detached"));
            }
            Assert.assertEquals(1, fixture.meta.nextPhysicsShapeId);
            Assert.assertFalse(fixture.repository.hasAnyBindings());
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void rejectsStaleNextSpatialBlockIdBeforeAllocation() {
        Fixture fixture = new Fixture();
        try {
            fixture.service.bindBlockCollision(1, 10);
            int highWater = fixture.meta.nextPhysicsShapeId;
            fixture.world.getMapper(SpatialBlocksComponent.class).get(fixture.owner)
                    .nextSpatialBlockId = 11;
            try {
                fixture.service.bindBlockCollision(1, 11);
                Assert.fail("A stale nextSpatialBlockId must be rejected.");
            } catch (IllegalStateException expected) {
                Assert.assertTrue(expected.getMessage().contains("nextSpatialBlockId"));
            }
            Assert.assertEquals(highWater, fixture.meta.nextPhysicsShapeId);
        } finally {
            fixture.dispose();
        }
    }

    @Test
    public void rejectsStaleShapeAndBlockCollectionsBeforeAllocation() {
        assertStaleRejected(fixture -> fixture.world.getMapper(PhysicsShapesComponent.class)
                .get(fixture.owner).shapes.first().enabled = false);
        assertStaleRejected(fixture -> fixture.world.getMapper(PhysicsShapesComponent.class)
                .get(fixture.owner).shapes.clear());
        assertStaleRejected(fixture -> fixture.world.getMapper(SpatialBlocksComponent.class)
                .get(fixture.owner).blocks.first().width = 2f);
    }

    @Test
    public void preparedMutationOwnsDeepCopiesOfThePublishedOwnerState() {
        Fixture fixture = new Fixture();
        try {
            fixture.service.bindBlockCollision(1, 10);
            PreparedWorldBlockMutation prepared = fixture.service.prepareBind(1, 11);
            SpatialBlocksComponent blocks = fixture.world.getMapper(SpatialBlocksComponent.class)
                    .get(fixture.owner);
            BlockPhysicsBindingsComponent bindings = fixture.world
                    .getMapper(BlockPhysicsBindingsComponent.class).get(fixture.owner);
            PhysicsShapesComponent shapes = fixture.world.getMapper(PhysicsShapesComponent.class)
                    .get(fixture.owner);
            blocks.blocks.first().width = 9f;
            bindings.bindings.first().physicsShapeId = 99;
            shapes.shapes.first().density = 9f;

            PreparedWorldBlockMutation.Publication publication = prepared.takePublication();
            Assert.assertEquals(1, publication.bindings.first().physicsShapeId);
            Assert.assertEquals(1f, publication.shapes.first().density, 0f);
            Assert.assertEquals(1f, publication.repositorySnapshot.findBlock(1, 10).width, 0f);
            Assert.assertNotSame(publication.fixtures.get(0).polygonVertices,
                    publication.fixtures.get(1).polygonVertices);

            SpatialBlockData snapshotBlock = publication.repositorySnapshot.findBlock(1, 10);
            snapshotBlock.width = 17f;
            Assert.assertEquals(1f, publication.repositorySnapshot.findBlock(1, 10).width, 0f);
        } finally {
            fixture.dispose();
        }
    }

    private static void assertStaleRejected(StaleMutation mutation) {
        Fixture fixture = new Fixture();
        try {
            fixture.service.bindBlockCollision(1, 10);
            int highWater = fixture.meta.nextPhysicsShapeId;
            mutation.apply(fixture);
            try {
                fixture.service.bindBlockCollision(1, 11);
                Assert.fail("Stale owner state must be rejected.");
            } catch (RuntimeException expected) {
                Assert.assertEquals(highWater, fixture.meta.nextPhysicsShapeId);
                Assert.assertFalse(fixture.repository.hasBinding(1, 11));
            }
        } finally {
            fixture.dispose();
        }
    }

    private interface StaleMutation {
        void apply(Fixture fixture);
    }

    private static void assertFirstBinding(Fixture fixture, int shapeId) {
        PhysicsShapesComponent shapes = fixture.world.getMapper(PhysicsShapesComponent.class).get(fixture.owner);
        BlockPhysicsBindingsComponent bindings = fixture.world.getMapper(BlockPhysicsBindingsComponent.class).get(fixture.owner);
        PhysicsBodyComponent body = fixture.world.getMapper(PhysicsBodyComponent.class).get(fixture.owner);
        TransformComponent transform = fixture.world.getMapper(TransformComponent.class).get(fixture.owner);
        Assert.assertEquals(1, bindings.bindings.size);
        Assert.assertEquals(10, bindings.bindings.first().spatialBlockId);
        Assert.assertEquals(shapeId, bindings.bindings.first().physicsShapeId);
        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertEquals(shapeId, shapes.shapes.first().physicsShapeId);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, body.type);
        Assert.assertTrue(body.fixedRotation);
        Assert.assertEquals(0f, transform.x, 0f);
        Assert.assertTrue(fixture.repository.hasBinding(1, 10));
    }

    private static final class Fixture {
        final SceneMetaRuntime meta = new SceneMetaRuntime();
        final World world;
        final IdentityRegistry identities = new IdentityRegistry();
        final BlockPhysicsBindingRepository repository = new BlockPhysicsBindingRepository();
        final int owner;
        final WorldBlockMutationService service;

        Fixture() {
            meta.physicsEnabled = true;
            meta.pixelsPerMeter = 32f;
            meta.nextEntityStableId = 2;
            meta.nextPhysicsShapeId = 1;
            world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(16)).build());
            owner = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
            SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(owner);
            blocks.blocks.add(block(10, 0));
            blocks.blocks.add(block(11, 2));
            blocks.nextSpatialBlockId = 12;
            world.getMapper(TiledLayerComponent.class).create(owner).data =
                    new TiledMapLayerData(8, 8, 32, 32, 2, SceneMetaRuntime.TiledProjection.ORTHO);
            world.process();
            identities.bind(world, meta);
            identities.rebuild();
            repository.bind(world, identities);
            repository.rebuild();
            service = new WorldBlockMutationService(world, meta, identities, repository,
                    new PhysicsService(world, null, meta));
        }

        void dispose() {
            repository.clear();
            identities.bind(null, null);
            world.dispose();
        }

        private static SpatialBlockData block(int id, int x) {
            SpatialBlockData result = new SpatialBlockData();
            result.id = id;
            result.structureId = 1;
            result.x = x;
            result.y = 0;
            result.width = 1f;
            result.depth = 1f;
            return result;
        }
    }
}
