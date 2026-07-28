package games.pixscape.runtime.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockPhysicsRegistryTest {

    @Test
    public void emptyWorldPublishesEmptyIndexes() {
        Fixture fixture = new Fixture();

        fixture.rebuild();

        Assert.assertNull(fixture.registry.findByPhysicsShapeId(1));
        Assert.assertEquals(-1, fixture.registry.findPhysicsShapeId(1, 1));
        Assert.assertFalse(fixture.registry.hasLinkedShape(1, 1));
    }

    @Test
    public void validRelationSupportsBothLookups() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.linked(owner, 21, 7);

        fixture.rebuild();

        SpatialBlockPhysicsRegistry.LinkedShapeRef ref =
                fixture.registry.findByPhysicsShapeId(21);
        Assert.assertNotNull(ref);
        Assert.assertEquals(21, ref.physicsShapeId());
        Assert.assertEquals(owner, ref.ownerEntityId());
        Assert.assertEquals(11, ref.ownerStableId());
        Assert.assertEquals(7, ref.spatialBlockId());
        Assert.assertEquals(21, fixture.registry.findPhysicsShapeId(11, 7));
    }

    @Test
    public void sameBlockIdOnDifferentOwnersDoesNotCollide() {
        Fixture fixture = new Fixture();
        int firstOwner = fixture.owner(11, 7);
        int secondOwner = fixture.owner(12, 7);
        fixture.linked(firstOwner, 21, 7);
        fixture.linked(secondOwner, 22, 7);

        fixture.rebuild();

        Assert.assertEquals(21, fixture.registry.findPhysicsShapeId(11, 7));
        Assert.assertEquals(22, fixture.registry.findPhysicsShapeId(12, 7));
    }

    @Test
    public void duplicateOwnerBlockRelationIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.linked(owner, 21, 7);
        fixture.linked(owner, 22, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void duplicateLinkedPhysicsShapeIdIsRejected() {
        Fixture fixture = new Fixture();
        int firstOwner = fixture.owner(11, 7);
        int secondOwner = fixture.owner(12, 8);
        fixture.linked(firstOwner, 21, 7);
        fixture.linked(secondOwner, 21, 8);

        fixture.assertRebuildRejected();
    }

    @Test
    public void duplicateManualLinkedPhysicsShapeIdIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.manual(owner, 21);
        fixture.linked(owner, 21, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void duplicateManualPhysicsShapeIdIsRejected() {
        Fixture fixture = new Fixture();
        int firstOwner = fixture.owner(11, 7);
        int secondOwner = fixture.owner(12, 8);
        fixture.manual(firstOwner, 21);
        fixture.manual(secondOwner, 21);

        fixture.assertRebuildRejected();
    }

    @Test
    public void physicsShapeIdAtHighWaterIsRejected() {
        Fixture fixture = new Fixture();
        fixture.meta.nextPhysicsShapeId = 21;
        int owner = fixture.owner(11, 7);
        fixture.linked(owner, 21, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void missingReferencedBlockIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.linked(owner, 21, 8);

        fixture.assertRebuildRejected();
    }

    @Test
    public void ownerWithoutIdentityIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.world.create();
        fixture.block(owner, 7);
        fixture.linked(owner, 21, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void inconsistentIdentityRegistryIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.linked(owner, 21, 7);
        fixture.world.process();
        fixture.identities.bind(fixture.world, fixture.meta);
        fixture.identities.rebuild();
        fixture.world.getMapper(PixscapeIdentityComponent.class)
                .get(owner).stableId = 12;
        fixture.registry.bind(
                fixture.world, fixture.identities, fixture.meta);

        assertRejected(new Runnable() {
            @Override
            public void run() {
                fixture.registry.rebuild();
            }
        });
    }

    @Test
    public void ownerWithoutSpatialBlocksComponentIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11);
        fixture.linked(owner, 21, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void linkedShapeWithGeometryIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        PhysicsShapeData linked = fixture.linked(owner, 21, 7);
        linked.geometry = new PhysicsGeometryData();

        fixture.assertRebuildRejected();
    }

    @Test
    public void manualShapeWithoutGeometryIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        PhysicsShapeData manual = fixture.manual(owner, 21);
        manual.geometry = null;

        fixture.assertRebuildRejected();
    }

    @Test
    public void invalidOwnerBlockHighWaterIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.world.getMapper(SpatialBlocksComponent.class)
                .get(owner).nextSpatialBlockId = 7;
        fixture.linked(owner, 21, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void duplicateOwnerBlockIdentityIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.block(owner, 7);
        fixture.linked(owner, 21, 7);

        fixture.assertRebuildRejected();
    }

    @Test
    public void failedRebuildPreservesPublishedStateAndGeneration() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        PhysicsShapeData linked = fixture.linked(owner, 21, 7);
        fixture.rebuild();
        int publishedGeneration = fixture.registry.generation();

        linked.spatialBlockId = 999;
        fixture.assertBoundRebuildRejected();

        Assert.assertEquals(
                publishedGeneration, fixture.registry.generation());
        Assert.assertEquals(21, fixture.registry.findPhysicsShapeId(11, 7));
        Assert.assertNotNull(fixture.registry.findByPhysicsShapeId(21));
    }

    @Test
    public void bindingNewWorldImmediatelyInvalidatesOldResults() {
        Fixture first = new Fixture();
        int firstOwner = first.owner(11, 7);
        first.linked(firstOwner, 21, 7);
        first.rebuild();
        int generation = first.registry.generation();

        World secondWorld = new World(new WorldConfiguration());
        SceneMetaRuntime secondMeta = new SceneMetaRuntime();
        IdentityRegistry secondIdentities = new IdentityRegistry();
        secondIdentities.bind(secondWorld, secondMeta);
        secondIdentities.rebuild();
        first.registry.bind(
                secondWorld, secondIdentities, secondMeta);

        Assert.assertTrue(first.registry.generation() > generation);
        Assert.assertFalse(first.registry.isBoundTo(first.world));
        Assert.assertTrue(first.registry.isBoundTo(secondWorld));
        Assert.assertNull(first.registry.findByPhysicsShapeId(21));
        Assert.assertEquals(-1, first.registry.findPhysicsShapeId(11, 7));

        first.registry.rebuild();
        Assert.assertNull(first.registry.findByPhysicsShapeId(21));
    }

    @Test
    public void detachClearsQueriesAndDetachedRebuildFailsClosed() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(11, 7);
        fixture.linked(owner, 21, 7);
        fixture.rebuild();
        int generation = fixture.registry.generation();

        fixture.registry.detach();

        Assert.assertTrue(fixture.registry.generation() > generation);
        Assert.assertFalse(fixture.registry.isBoundTo(fixture.world));
        Assert.assertNull(fixture.registry.findByPhysicsShapeId(21));
        Assert.assertEquals(-1, fixture.registry.findPhysicsShapeId(11, 7));
        try {
            fixture.registry.rebuild();
            Assert.fail("Detached rebuild must fail closed.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("detached"));
        }
    }

    private static void assertRejected(Runnable action) {
        try {
            action.run();
            Assert.fail("Invalid registry state must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertNotNull(expected.getMessage());
        }
    }

    private static final class Fixture {
        final World world = new World(new WorldConfiguration());
        final SceneMetaRuntime meta = new SceneMetaRuntime();
        final IdentityRegistry identities = new IdentityRegistry();
        final SpatialBlockPhysicsRegistry registry =
                new SpatialBlockPhysicsRegistry();

        Fixture() {
            meta.nextEntityStableId = 100;
            meta.nextPhysicsShapeId = 100;
        }

        int owner(int stableId, int... blockIds) {
            int owner = world.create();
            PixscapeIdentityComponent identity =
                    world.getMapper(PixscapeIdentityComponent.class)
                            .create(owner);
            identity.stableId = stableId;
            for (int i = 0; i < blockIds.length; i++) {
                block(owner, blockIds[i]);
            }
            return owner;
        }

        void block(int owner, int blockId) {
            SpatialBlocksComponent component =
                    world.getMapper(SpatialBlocksComponent.class)
                            .getSafe(owner, null);
            if (component == null) {
                component = world.getMapper(SpatialBlocksComponent.class)
                        .create(owner);
            }
            SpatialBlockData block = new SpatialBlockData();
            block.id = blockId;
            component.blocks.add(block);
            if (component.nextSpatialBlockId <= blockId) {
                component.nextSpatialBlockId = blockId + 1;
            }
        }

        PhysicsShapeData linked(
                int owner, int physicsShapeId, int spatialBlockId) {
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.spatialBlockId = spatialBlockId;
            shapes(owner).shapes.add(shape);
            return shape;
        }

        PhysicsShapeData manual(int owner, int physicsShapeId) {
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.geometry = new PhysicsGeometryData();
            shapes(owner).shapes.add(shape);
            return shape;
        }

        PhysicsShapesComponent shapes(int owner) {
            PhysicsShapesComponent component =
                    world.getMapper(PhysicsShapesComponent.class)
                            .getSafe(owner, null);
            return component != null
                    ? component
                    : world.getMapper(PhysicsShapesComponent.class)
                            .create(owner);
        }

        void rebuild() {
            world.process();
            identities.bind(world, meta);
            identities.rebuild();
            registry.bind(world, identities, meta);
            registry.rebuild();
        }

        void assertRebuildRejected() {
            world.process();
            identities.bind(world, meta);
            try {
                identities.rebuild();
            } catch (IllegalArgumentException identityFailure) {
                throw identityFailure;
            }
            registry.bind(world, identities, meta);
            assertBoundRebuildRejected();
        }

        void assertBoundRebuildRejected() {
            assertRejected(new Runnable() {
                @Override
                public void run() {
                    registry.rebuild();
                }
            });
        }
    }
}
