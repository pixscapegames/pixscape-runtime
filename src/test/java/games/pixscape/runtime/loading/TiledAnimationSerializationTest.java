package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledAnimationComponent;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;

public class TiledAnimationSerializationTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void sceneRoundTripPreservesAnimationIdAndResetsTransientPlayback() throws Exception {
        World source = serializationWorld();
        int entity = source.create();
        TiledAnimationComponent before = source.getMapper(TiledAnimationComponent.class)
                .create(entity);
        before.animationId = 42;
        before.frameIndex = 3;
        before.frameElapsedMs = 117;
        before.appliedFrameAssetId = 123;
        source.process();

        byte[] serialized = save(source, entity);
        String json = new String(serialized, "UTF-8");
        Assert.assertTrue(json.contains("animationId"));
        Assert.assertFalse(json.contains("frameIndex"));
        Assert.assertFalse(json.contains("frameElapsedMs"));
        Assert.assertFalse(json.contains("appliedFrameAssetId"));

        FileHandle sceneFile = new FileHandle(temporaryFolder.newFile("tiled-animation.json"));
        sceneFile.writeBytes(serialized, false);
        World target = serializationWorld();
        SaveFileFormat loaded = SceneLoader.loadScene(
                target,
                sceneFile,
                true,
                new SceneMetaRuntime("test", sceneFile.path()));
        TiledAnimationComponent after = target.getMapper(TiledAnimationComponent.class)
                .get(loaded.entities.get(0));

        Assert.assertEquals(42, after.animationId);
        Assert.assertEquals(0, after.frameIndex);
        Assert.assertEquals(0, after.frameElapsedMs);
        Assert.assertEquals(-1, after.appliedFrameAssetId);
        source.dispose();
        target.dispose();
    }

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
    }

    private static byte[] save(World world, int entity) throws Exception {
        WorldSerializationManager serialization = world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(entity);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialization.save(out, request);
        return out.toByteArray();
    }
}
