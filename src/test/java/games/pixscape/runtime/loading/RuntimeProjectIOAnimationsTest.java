package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.service.AnimationRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

public class RuntimeProjectIOAnimationsTest {

    @Test
    public void missingAnimationsJsonDoesNotCrash() {
        File dir = makeTempDir("pixscape-runtime-no-animations");
        AnimationRegistry registry = new AnimationRegistry();

        RuntimeProjectIO.loadAnimations(new FileHandle(dir), registry);

        Assert.assertEquals(0, registry.size());
    }

    @Test
    public void animationsJsonLoadsClips() throws Exception {
        File dir = makeTempDir("pixscape-runtime-animations");
        File file = new File(dir, "animations.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"animations\":[{\"assetId\":123,\"name\":\"hero\",\"fps\":12,\"currentClip\":\"idle\",\"frameCount\":8,\"clips\":[{\"name\":\"idle\",\"start\":0,\"end\":3,\"flipX\":false},{\"name\":\"attack\",\"start\":4,\"end\":7,\"flipX\":true}]}]}");
        writer.close();

        AnimationRegistry registry = new AnimationRegistry();
        RuntimeProjectIO.loadAnimations(new FileHandle(dir), registry);

        Assert.assertEquals(1, registry.size());
        Assert.assertNotNull(registry.getByName("hero"));
        Assert.assertEquals(2, registry.getByAssetId(123).clips().size);
        Assert.assertTrue(registry.getByAssetId(123).clips().get(1).flipX);
    }

    private static File makeTempDir(String prefix) {
        File root = new File(System.getProperty("java.io.tmpdir"));
        File dir = new File(root, prefix + "-" + System.nanoTime());
        if (!dir.mkdirs()) {
            throw new IllegalStateException("Could not create temp dir: " + dir.getAbsolutePath());
        }
        dir.deleteOnExit();
        return dir;
    }
}
