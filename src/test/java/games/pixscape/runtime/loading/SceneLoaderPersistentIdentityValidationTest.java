package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class SceneLoaderPersistentIdentityValidationTest {

    @Test
    public void realSceneLoadRejectsDuplicateEntityStableIds() throws Exception {
        World authored = world();
        int first = identityEntity(authored, 4);
        int second = identityEntity(authored, 4);
        FileHandle file = save(authored, first, second);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 5;

        IllegalArgumentException cause = loadFailure(file, meta);
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("duplicate ID"));
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("entityStableId"));
    }

    @Test
    public void realSceneLoadRejectsEntityHighWaterNotGreaterThanMax() throws Exception {
        World authored = world();
        int entity = identityEntity(authored, 8);
        FileHandle file = save(authored, entity);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 8;

        IllegalArgumentException cause = loadFailure(file, meta);
        Assert.assertTrue(cause.getMessage(),
                cause.getMessage().contains("high-water must be greater than max ID"));
    }

    @Test
    public void realSceneLoadRejectsDuplicateSpatialBlockIds() throws Exception {
        World authored = world();
        int owner = identityEntity(authored, 1);
        SpatialBlocksComponent blocks =
                authored.getMapper(SpatialBlocksComponent.class).create(owner);
        blocks.nextSpatialBlockId = 2;
        blocks.blocks.add(block(1));
        blocks.blocks.add(block(1));
        FileHandle file = save(authored, owner);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 2;

        IllegalArgumentException cause = loadFailure(file, meta);
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("spatialBlockId"));
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("duplicate ID"));
    }

    @Test
    public void realSceneLoadRejectsSpatialHighWaterNotGreaterThanMax() throws Exception {
        World authored = world();
        int owner = identityEntity(authored, 1);
        SpatialBlocksComponent blocks =
                authored.getMapper(SpatialBlocksComponent.class).create(owner);
        blocks.nextSpatialBlockId = 3;
        blocks.blocks.add(block(3));
        FileHandle file = save(authored, owner);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 2;

        IllegalArgumentException cause = loadFailure(file, meta);
        Assert.assertTrue(cause.getMessage(),
                cause.getMessage().contains("high-water must be greater than max ID"));
    }

    @Test
    public void realSceneLoadRejectsSpatialOwnerWithoutIdentity() throws Exception {
        World authored = world();
        int owner = authored.create();
        SpatialBlocksComponent blocks = authored.getMapper(SpatialBlocksComponent.class).create(owner);
        blocks.nextSpatialBlockId = 2;
        blocks.blocks.add(block(1));

        IllegalArgumentException cause = loadFailure(save(authored, owner), new SceneMetaRuntime());
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("spatialOwnerStableId"));
    }

    @Test
    public void realSceneLoadRejectsNonPositiveEntityStableId() throws Exception {
        World authored = world();
        int entity = identityEntity(authored, 0);

        IllegalArgumentException cause = loadFailure(save(authored, entity), new SceneMetaRuntime());
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("entityStableId"));
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("ID must be positive"));
    }

    @Test
    public void realSceneLoadRejectsNonPositiveSpatialHighWater() throws Exception {
        World authored = world();
        int owner = identityEntity(authored, 1);
        authored.getMapper(SpatialBlocksComponent.class).create(owner).nextSpatialBlockId = 0;
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 2;

        IllegalArgumentException cause = loadFailure(save(authored, owner), meta);
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("spatialBlockId"));
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("high-water must be positive"));
    }

    @Test
    public void realSceneLoadRejectsDuplicatePhysicsShapeIds() throws Exception {
        World authored = world();
        int entity = identityEntity(authored, 1);
        PhysicsShapesComponent shapes = authored.getMapper(PhysicsShapesComponent.class).create(entity);
        shapes.shapes.add(shape(1));
        shapes.shapes.add(shape(1));
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 2;
        meta.nextPhysicsShapeId = 2;

        IllegalArgumentException cause = loadFailure(save(authored, entity), meta);
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("physicsShapeId"));
        Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("Duplicate"));
    }

    @Test
    public void validRealSceneLoadsWithoutIntermediateProcess() throws Exception {
        World authored = world();
        int owner = identityEntity(authored, 1);
        SpatialBlocksComponent blocks = authored.getMapper(SpatialBlocksComponent.class).create(owner);
        blocks.nextSpatialBlockId = 2;
        blocks.blocks.add(block(1));
        FileHandle file = save(authored, owner);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 2;
        World loaded = world();
        try {
            SceneLoader.loadScene(loaded, file, false, meta);
        } finally {
            loaded.dispose();
        }
    }

    private static IllegalArgumentException loadFailure(
            FileHandle file, SceneMetaRuntime meta) {
        World loaded = world();
        try {
            RuntimeException failure = Assert.assertThrows(
                    RuntimeException.class,
                    () -> SceneLoader.loadScene(loaded, file, false, meta));
            Throwable cause = failure.getCause();
            Assert.assertTrue(cause instanceof IllegalArgumentException);
            return (IllegalArgumentException) cause;
        } finally {
            loaded.dispose();
        }
    }

    private static int identityEntity(World world, int stableId) {
        int entity = world.create();
        PixscapeIdentityComponent identity =
                world.getMapper(PixscapeIdentityComponent.class).create(entity);
        identity.stableId = stableId;
        return entity;
    }

    private static SpatialBlockData block(int id) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.structureId = 1;
        block.width = 1;
        block.depth = 1;
        return block;
    }

    private static PhysicsShapeData shape(int id) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        shape.physicsShapeId = id;
        return shape;
    }

    private static FileHandle save(World world, int... entities) throws Exception {
        world.process();
        WorldSerializationManager serialization =
                world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat format = new SaveFileFormat();
        for (int entity : entities) format.entities.add(entity);
        FileHandle file = new FileHandle(
                File.createTempFile("pixscape-identity-validation", ".json"));
        try (OutputStream out = file.write(false)) {
            serialization.save(out, format);
        } finally {
            world.dispose();
        }
        return file;
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }
}
