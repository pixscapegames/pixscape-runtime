package games.pixscape.runtime.physics;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeIdentityValidatorTest {

    @Test
    public void duplicateManualPhysicsShapeIdIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner();
        fixture.manual(owner, 21);
        fixture.manual(owner, 21);

        fixture.assertRejected("Duplicate scene-wide physicsShapeId");
    }

    @Test
    public void duplicateManualLinkedPhysicsShapeIdIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(7);
        fixture.manual(owner, 21);
        fixture.linked(owner, 21, 7);

        fixture.assertRejected("Duplicate scene-wide physicsShapeId");
    }

    @Test
    public void duplicateLinkedPhysicsShapeIdIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(7, 8);
        fixture.linked(owner, 21, 7);
        fixture.linked(owner, 21, 8);

        fixture.assertRejected("Duplicate scene-wide physicsShapeId");
    }

    @Test
    public void samePhysicsShapeIdOnDifferentOwnersIsRejected() {
        Fixture fixture = new Fixture();
        fixture.manual(fixture.owner(), 21);
        fixture.manual(fixture.owner(), 21);

        fixture.assertRejected("Duplicate scene-wide physicsShapeId");
    }

    @Test
    public void linkedShapeReferencingMissingBlockIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(7);
        fixture.linked(owner, 21, 8);

        fixture.assertRejected("referenced spatial block is missing");
    }

    @Test
    public void linkedShapeRequiresSpatialBlocksComponentOnOwner() {
        Fixture fixture = new Fixture();
        fixture.linked(fixture.owner(), 21, 7);

        fixture.assertRejected("owner has no SpatialBlocksComponent");
    }

    @Test
    public void duplicateLinkedRelationOnSameOwnerIsRejected() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(7);
        fixture.linked(owner, 21, 7);
        fixture.linked(owner, 22, 7);

        fixture.assertRejected("another linked shape already references");
    }

    @Test
    public void sameSpatialBlockIdOnDifferentOwnersIsAllowed() {
        Fixture fixture = new Fixture();
        fixture.linked(fixture.owner(7), 21, 7);
        fixture.linked(fixture.owner(7), 22, 7);

        fixture.validate();
    }

    @Test
    public void multipleManualShapesOnSameOwnerAreAllowed() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner();
        fixture.manual(owner, 21);
        fixture.manual(owner, 22);

        fixture.validate();
    }

    @Test
    public void manualAndLinkedShapesOnSameOwnerAreAllowed() {
        Fixture fixture = new Fixture();
        int owner = fixture.owner(7);
        fixture.manual(owner, 21);
        fixture.linked(owner, 22, 7);

        fixture.validate();
    }

    @Test
    public void linkedShapeWithGeometryIsRejected() {
        Fixture fixture = new Fixture();
        PhysicsShapeData shape =
                fixture.linked(fixture.owner(7), 21, 7);
        shape.geometry = new PhysicsGeometryData();

        fixture.assertRejected("linked shape geometry must be null");
    }

    @Test
    public void manualShapeWithoutGeometryIsRejected() {
        Fixture fixture = new Fixture();
        PhysicsShapeData shape = fixture.manual(fixture.owner(), 21);
        shape.geometry = null;

        fixture.assertRejected("manual shape geometry is required");
    }

    @Test
    public void negativeSpatialBlockIdIsRejected() {
        Fixture fixture = new Fixture();
        PhysicsShapeData shape = fixture.manual(fixture.owner(), 21);
        shape.spatialBlockId = -1;

        fixture.assertRejected("spatialBlockId must be non-negative");
    }

    @Test
    public void nextPhysicsShapeIdAtMaximumIsRejected() {
        Fixture fixture = new Fixture();
        fixture.meta.nextPhysicsShapeId = 21;
        fixture.manual(fixture.owner(), 21);

        fixture.assertRejected("must be greater than maximum");
    }

    @Test
    public void nextPhysicsShapeIdAboveMaximumIsAllowed() {
        Fixture fixture = new Fixture();
        fixture.meta.nextPhysicsShapeId = 22;
        fixture.manual(fixture.owner(), 21);

        fixture.validate();
    }

    private static final class Fixture {
        final World world = new World(new WorldConfiguration());
        final SceneMetaRuntime meta = new SceneMetaRuntime();

        Fixture() {
            meta.nextPhysicsShapeId = 100;
        }

        int owner(int... blockIds) {
            int owner = world.create();
            for (int i = 0; i < blockIds.length; i++) {
                block(owner, blockIds[i]);
            }
            return owner;
        }

        void block(int owner, int blockId) {
            SpatialBlocksComponent blocks =
                    world.getMapper(SpatialBlocksComponent.class)
                            .getSafe(owner, null);
            if (blocks == null) {
                blocks = world.getMapper(SpatialBlocksComponent.class)
                        .create(owner);
            }
            SpatialBlockData block = new SpatialBlockData();
            block.id = blockId;
            blocks.blocks.add(block);
            if (blocks.nextSpatialBlockId <= blockId) {
                blocks.nextSpatialBlockId = blockId + 1;
            }
        }

        PhysicsShapeData manual(int owner, int physicsShapeId) {
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.geometry = new PhysicsGeometryData();
            shapes(owner).shapes.add(shape);
            return shape;
        }

        PhysicsShapeData linked(
                int owner, int physicsShapeId, int spatialBlockId) {
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = physicsShapeId;
            shape.spatialBlockId = spatialBlockId;
            shapes(owner).shapes.add(shape);
            return shape;
        }

        PhysicsShapesComponent shapes(int owner) {
            PhysicsShapesComponent shapes =
                    world.getMapper(PhysicsShapesComponent.class)
                            .getSafe(owner, null);
            return shapes != null
                    ? shapes
                    : world.getMapper(PhysicsShapesComponent.class)
                            .create(owner);
        }

        void validate() {
            world.process();
            IntBag entities = world.getAspectSubscriptionManager()
                    .get(Aspect.all())
                    .getEntities();
            PhysicsShapeIdentityValidator.validateEntities(
                    world, entities, meta);
        }

        void assertRejected(String expectedMessage) {
            try {
                validate();
                Assert.fail("Invalid scene physics identities must be rejected.");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(
                        expected.getMessage(),
                        expected.getMessage().contains(expectedMessage));
            }
        }
    }
}
