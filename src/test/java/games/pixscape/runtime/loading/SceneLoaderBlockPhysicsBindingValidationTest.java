package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class SceneLoaderBlockPhysicsBindingValidationTest {

    @Test
    public void directShapeIsValidatedAndRoundTripsWithoutBinding() throws Exception {
        World source = world();
        int entity = source.create();
        PhysicsShapeData direct = directShape(1);
        source.getMapper(PhysicsShapesComponent.class)
                .create(entity).shapes.add(direct);
        FileHandle file = save(source);
        SceneMetaRuntime meta = enabledMeta(1, 2);
        World target = world();
        try {
            SaveFileFormat loaded =
                    SceneLoader.loadScene(target, file, false, meta);
            int loadedEntity = loaded.entities.get(0);
            PhysicsShapesComponent shapes = target
                    .getMapper(PhysicsShapesComponent.class).get(loadedEntity);

            Assert.assertEquals(1, shapes.shapes.size);
            Assert.assertEquals(1, shapes.shapes.first().physicsShapeId);
            Assert.assertNotNull(shapes.shapes.first().directGeometry);
            Assert.assertFalse(target.getMapper(
                    BlockPhysicsBindingsComponent.class).has(loadedEntity));
        } finally {
            target.dispose();
        }
    }

    @Test
    public void invalidDirectShapeAndDirectHighWaterRemainRejected() throws Exception {
        World invalidGeometry = world();
        int geometryEntity = invalidGeometry.create();
        PhysicsShapeData invalid = directShape(1);
        invalid.directGeometry.halfWidth = 0f;
        invalidGeometry.getMapper(PhysicsShapesComponent.class)
                .create(geometryEntity).shapes.add(invalid);
        RuntimeException geometryFailure = loadFailure(
                save(invalidGeometry), enabledMeta(1, 2), false);
        Assert.assertTrue(geometryFailure.getMessage(),
                geometryFailure.getMessage().contains("halfWidth"));

        World invalidHighWater = world();
        int highWaterEntity = invalidHighWater.create();
        invalidHighWater.getMapper(PhysicsShapesComponent.class)
                .create(highWaterEntity).shapes.add(directShape(2));
        RuntimeException highWaterFailure = loadFailure(
                save(invalidHighWater), enabledMeta(1, 2), false);
        Assert.assertTrue(highWaterFailure.getMessage(),
                highWaterFailure.getMessage().contains("high-water"));
    }

    @Test
    public void structurallyValidLinkedOwnerRoundTripsWithoutCompiledState()
            throws Exception {
        World source = validLinkedWorld(1, 1, 10);
        FileHandle file = save(source);
        SceneMetaRuntime meta = enabledMeta(2, 11);
        World target = world();
        try {
            SaveFileFormat loaded =
                    SceneLoader.loadScene(target, file, false, meta);
            int owner = loaded.entities.get(0);
            BlockPhysicsBindingsComponent bindings = target
                    .getMapper(BlockPhysicsBindingsComponent.class).get(owner);
            PhysicsShapesComponent shapes = target
                    .getMapper(PhysicsShapesComponent.class).get(owner);

            Assert.assertEquals(1, bindings.bindings.size);
            Assert.assertEquals(1,
                    bindings.bindings.first().spatialBlockId);
            Assert.assertEquals(10,
                    bindings.bindings.first().physicsShapeId);
            Assert.assertEquals(10, shapes.shapes.first().physicsShapeId);
            Assert.assertNull(shapes.shapes.first().directGeometry);
            Assert.assertFalse(target.getMapper(
                    PhysicsBodyComponent.class).has(owner));
            Assert.assertFalse(target.getMapper(
                    PhysicsCompiledFixturesComponent.class).has(owner));
        } finally {
            target.dispose();
        }
    }

    @Test
    public void orphanAndEmptyBindingStatesAreRejected() throws Exception {
        World orphan = validLinkedWorld(1, 1, 10);
        orphan.getMapper(BlockPhysicsBindingsComponent.class).remove(0);
        assertFailureContains(save(orphan), enabledMeta(2, 11),
                "linked shape", "binding", "physicsShapeId=10");

        World empty = validLinkedWorld(1, 1, 10);
        empty.getMapper(BlockPhysicsBindingsComponent.class)
                .get(0).bindings.clear();
        assertFailureContains(save(empty), enabledMeta(2, 11),
                "component is empty", "ownerEntityId=0");
    }

    @Test
    public void danglingBlockAndShapeBindingsAreRejected() throws Exception {
        World absentBlock = validLinkedWorld(1, 1, 10);
        absentBlock.getMapper(BlockPhysicsBindingsComponent.class)
                .get(0).bindings.first().spatialBlockId = 2;
        assertFailureContains(save(absentBlock), enabledMeta(2, 11),
                "block absent", "blockId=2", "physicsShapeId=10");

        World absentShape = validLinkedWorld(1, 1, 10);
        absentShape.getMapper(BlockPhysicsBindingsComponent.class)
                .get(0).bindings.first().physicsShapeId = 11;
        assertFailureContains(save(absentShape), enabledMeta(2, 12),
                "shape absent", "blockId=1", "physicsShapeId=11");
    }

    @Test
    public void directBoundDisabledAndForeignLinkedShapesAreRejected()
            throws Exception {
        World directBound = validLinkedWorld(1, 1, 10);
        directBound.getMapper(PhysicsShapesComponent.class)
                .get(0).shapes.first().directGeometry =
                new PhysicsDirectGeometryData();
        assertFailureContains(save(directBound), enabledMeta(2, 11),
                "direct-geometry shape cannot be bound", "physicsShapeId=10");

        World disabled = validLinkedWorld(1, 1, 10);
        disabled.getMapper(PhysicsShapesComponent.class)
                .get(0).shapes.first().enabled = false;
        assertFailureContains(save(disabled), enabledMeta(2, 11),
                "linked shape must be enabled", "physicsShapeId=10");

        World foreign = validLinkedWorld(1, 1, 10);
        foreign.getMapper(PhysicsShapesComponent.class)
                .get(0).shapes.clear();
        int other = foreign.create();
        PixscapeIdentityComponent otherIdentity = foreign
                .getMapper(PixscapeIdentityComponent.class).create(other);
        otherIdentity.stableId = 2;
        foreign.getMapper(PhysicsShapesComponent.class)
                .create(other).shapes.add(linkedShape(10));
        assertFailureContains(save(foreign), enabledMeta(3, 11),
                "shape absent from the same owner", "physicsShapeId=10");
    }

    @Test
    public void duplicateLinkedShapeIdAndInvalidHighWaterAreRejected()
            throws Exception {
        World duplicate = validLinkedWorld(1, 1, 10);
        addLinkedOwner(duplicate, 2, 1, 10);
        assertFailureContains(save(duplicate), enabledMeta(3, 11),
                "physicsShapeId", "Duplicate");

        World highWater = validLinkedWorld(1, 1, 10);
        assertFailureContains(save(highWater), enabledMeta(2, 10),
                "physicsShapeId", "high-water");
    }

    @Test
    public void physicsDisabledRejectsBindingsAndLinkedShapes() throws Exception {
        World bindingOnly = world();
        int bindingEntity = bindingOnly.create();
        bindingOnly.getMapper(BlockPhysicsBindingsComponent.class)
                .create(bindingEntity);
        SceneMetaRuntime disabled = enabledMeta(1, 1);
        disabled.physicsEnabled = false;
        assertFailureContains(save(bindingOnly), disabled,
                "physicsEnabled=false", "BlockPhysicsBindingsComponent");

        World linkedOnly = world();
        int linkedEntity = linkedOnly.create();
        linkedOnly.getMapper(PhysicsShapesComponent.class)
                .create(linkedEntity).shapes.add(linkedShape(1));
        SceneMetaRuntime linkedDisabled = enabledMeta(1, 2);
        linkedDisabled.physicsEnabled = false;
        assertFailureContains(save(linkedOnly), linkedDisabled,
                "physicsEnabled=false", "PhysicsShapesComponent");
    }

    @Test
    public void invalidLinkedPreflightPreservesTargetForBothClearModes()
            throws Exception {
        World source = validLinkedWorld(1, 1, 10);
        source.getMapper(BlockPhysicsBindingsComponent.class).remove(0);
        FileHandle file = save(source);
        assertTargetPreserved(file, true);
        assertTargetPreserved(file, false);
    }

    private static void assertTargetPreserved(
            FileHandle file, boolean clearContentFirst) {
        World target = world();
        int existing = target.create();
        PixscapeIdentityComponent identity = target
                .getMapper(PixscapeIdentityComponent.class).create(existing);
        identity.stableId = 99;
        target.process();
        try {
            RuntimeException failure = Assert.assertThrows(
                    RuntimeException.class,
                    () -> SceneLoader.loadScene(
                            target, file, clearContentFirst,
                            enabledMeta(2, 11)));
            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains(file.path()));
            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains("physicsShapeId=10"));
            Assert.assertTrue(target.getEntityManager().isActive(existing));
            Assert.assertEquals(1, target.getAspectSubscriptionManager()
                    .get(Aspect.all()).getEntities().size());
            Assert.assertEquals(99, target
                    .getMapper(PixscapeIdentityComponent.class)
                    .get(existing).stableId);
        } finally {
            target.dispose();
        }
    }

    private static void assertFailureContains(
            FileHandle file, SceneMetaRuntime meta, String... parts) {
        RuntimeException failure = loadFailure(file, meta, false);
        for (String part : parts) {
            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains(part));
        }
    }

    private static RuntimeException loadFailure(
            FileHandle file, SceneMetaRuntime meta, boolean clearContentFirst) {
        World target = world();
        try {
            return Assert.assertThrows(
                    RuntimeException.class,
                    () -> SceneLoader.loadScene(
                            target, file, clearContentFirst, meta));
        } finally {
            target.dispose();
        }
    }

    private static World validLinkedWorld(
            int stableId, int blockId, int shapeId) {
        World world = world();
        addLinkedOwner(world, stableId, blockId, shapeId);
        return world;
    }

    private static int addLinkedOwner(
            World world, int stableId, int blockId, int shapeId) {
        int owner = world.create();
        PixscapeIdentityComponent identity = world
                .getMapper(PixscapeIdentityComponent.class).create(owner);
        identity.stableId = stableId;
        SpatialBlocksComponent blocks = world
                .getMapper(SpatialBlocksComponent.class).create(owner);
        blocks.blocks.add(block(blockId));
        blocks.nextSpatialBlockId = blockId + 1;
        world.getMapper(PhysicsShapesComponent.class)
                .create(owner).shapes.add(linkedShape(shapeId));
        BlockPhysicsBindingsComponent bindings = world
                .getMapper(BlockPhysicsBindingsComponent.class).create(owner);
        bindings.bindings.add(binding(blockId, shapeId));
        return owner;
    }

    private static SpatialBlockData block(int id) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.structureId = 1;
        block.width = 1;
        block.depth = 1;
        return block;
    }

    private static PhysicsShapeData linkedShape(int id) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = id;
        shape.directGeometry = null;
        shape.enabled = true;
        return shape;
    }

    private static PhysicsShapeData directShape(int id) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = id;
        shape.directGeometry = new PhysicsDirectGeometryData();
        shape.enabled = true;
        return shape;
    }

    private static BlockPhysicsBindingData binding(
            int blockId, int shapeId) {
        BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
        binding.spatialBlockId = blockId;
        binding.physicsShapeId = shapeId;
        return binding;
    }

    private static SceneMetaRuntime enabledMeta(
            int nextEntityId, int nextShapeId) {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.sceneSchemaVersion = SceneMetaRuntime.CURRENT_SCENE_SCHEMA_VERSION;
        meta.physicsEnabled = true;
        meta.nextEntityStableId = nextEntityId;
        meta.nextPhysicsShapeId = nextShapeId;
        return meta;
    }

    private static FileHandle save(World source) throws Exception {
        try {
            source.process();
            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(
                    source.getAspectSubscriptionManager()
                            .get(Aspect.all()).getEntities());
            FileHandle file = new FileHandle(
                    File.createTempFile(
                            "pixscape-binding-validation-", ".json"));
            try (OutputStream output = file.write(false)) {
                serialization.save(output, format);
            }
            return file;
        } finally {
            source.dispose();
        }
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
