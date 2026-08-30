package games.pixscape.runtime.component;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class GameObjectComponentsTest {
    @Test
    public void gameObjectResetClearsSourceAssetId() {
        GameObjectComponent component = new GameObjectComponent();
        component.sourceAssetId = "prefabs/crate";

        component.reset();

        Assert.assertEquals("", component.sourceAssetId);
    }

    @Test
    public void memberResetClearsParentStableId() {
        GameObjectMemberComponent component = new GameObjectMemberComponent();
        component.parentStableId = 42;

        component.reset();

        Assert.assertEquals(-1, component.parentStableId);
    }

    @Test
    public void componentsRoundTripThroughArtemisSceneSerialization() throws Exception {
        World source = world();
        int root = source.create();
        source.getMapper(GameObjectComponent.class).create(root).sourceAssetId = "prefabs/crate";
        int member = source.create();
        source.getMapper(GameObjectMemberComponent.class).create(member).parentStableId = 17;
        source.process();

        byte[] bytes;
        try {
            SaveFileFormat format = new SaveFileFormat();
            format.entities.add(root);
            format.entities.add(member);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            source.getSystem(WorldSerializationManager.class).save(output, format);
            bytes = output.toByteArray();
        } finally {
            source.dispose();
        }

        String serialized = new String(bytes, "UTF-8");
        Assert.assertTrue(serialized.contains("sourceAssetId"));
        Assert.assertTrue(serialized.contains("parentStableId"));
        Assert.assertFalse(serialized.contains("parentEntityId"));
        Assert.assertFalse(serialized.contains("historyId"));

        World restored = world();
        try {
            SaveFileFormat loaded = restored.getSystem(WorldSerializationManager.class)
                    .load(new ByteArrayInputStream(bytes), SaveFileFormat.class);
            int[] entities = loaded.entities.getData();
            Assert.assertEquals("prefabs/crate",
                    restored.getMapper(GameObjectComponent.class).get(entities[0]).sourceAssetId);
            Assert.assertEquals(17,
                    restored.getMapper(GameObjectMemberComponent.class).get(entities[1]).parentStableId);
        } finally {
            restored.dispose();
        }
    }

    private static World world() {
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        world.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(world));
        return world;
    }
}
