package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class GameObjectSceneLoadingTest {
    @Test
    public void validHierarchyLoadsThroughCurrentSceneMachinery() throws Exception {
        World authored = world();
        int root = entity(authored, 1);
        authored.getMapper(GameObjectComponent.class).create(root).sourceAssetId = "gameobjects/crate.gameobject";
        int child = entity(authored, 2);
        authored.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 1;
        FileHandle file = save(authored, root, child);
        SceneMetaRuntime meta = meta(3);

        World loaded = world();
        try {
            SaveFileFormat format = SceneLoader.loadScene(loaded, file, false, meta);
            int[] entities = format.entities.getData();
            Assert.assertEquals("gameobjects/crate.gameobject",
                    loaded.getMapper(GameObjectComponent.class).get(entities[0]).sourceAssetId);
            Assert.assertEquals(1,
                    loaded.getMapper(GameObjectMemberComponent.class).get(entities[1]).parentStableId);
        } finally {
            loaded.dispose();
        }
    }

    @Test
    public void flatLegacyStyleEntitiesAreNotConvertedAutomatically() throws Exception {
        World authored = world();
        int first = entity(authored, 1);
        int second = entity(authored, 2);
        FileHandle file = save(authored, first, second);

        World loaded = world();
        try {
            SaveFileFormat format = SceneLoader.loadScene(loaded, file, false, meta(3));
            int[] entities = format.entities.getData();
            Assert.assertFalse(loaded.getMapper(GameObjectComponent.class).has(entities[0]));
            Assert.assertFalse(loaded.getMapper(GameObjectComponent.class).has(entities[1]));
            Assert.assertFalse(loaded.getMapper(GameObjectMemberComponent.class).has(entities[0]));
            Assert.assertFalse(loaded.getMapper(GameObjectMemberComponent.class).has(entities[1]));
        } finally {
            loaded.dispose();
        }
    }

    @Test
    public void malformedHierarchyIsRejectedBeforeScenePublication() throws Exception {
        World authored = world();
        int child = entity(authored, 1);
        authored.getMapper(GameObjectMemberComponent.class).create(child).parentStableId = 99;
        FileHandle file = save(authored, child);

        World loaded = world();
        try {
            RuntimeException failure = Assert.assertThrows(
                    RuntimeException.class,
                    () -> SceneLoader.loadScene(loaded, file, false, meta(2)));
            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains("invalid Game Object hierarchy"));
        } finally {
            loaded.dispose();
        }
    }

    @Test
    public void sceneSchemaRemainsThree() {
        Assert.assertEquals(3, SceneMetaRuntime.CURRENT_SCENE_SCHEMA_VERSION);
    }

    private static int entity(World world, int stableId) {
        int entity = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        return entity;
    }

    private static SceneMetaRuntime meta(int nextStableId) {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = nextStableId;
        return meta;
    }

    private static FileHandle save(World world, int... entities) throws Exception {
        world.process();
        SaveFileFormat format = new SaveFileFormat();
        for (int entity : entities) format.entities.add(entity);
        FileHandle file = new FileHandle(File.createTempFile("pixscape-game-object", ".json"));
        try (OutputStream output = file.write(false)) {
            world.getSystem(WorldSerializationManager.class).save(output, format);
        } finally {
            world.dispose();
        }
        return file;
    }

    private static World world() {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        world.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(world));
        return world;
    }
}
