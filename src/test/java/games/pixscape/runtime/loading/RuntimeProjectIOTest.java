package games.pixscape.runtime.loading;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.runtime.service.TileAnimationRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;

public class RuntimeProjectIOTest {

    @Test
    public void sceneSchemaVersionThreeIsAccepted() throws Exception {
        FileHandle projectDir = projectDirectory(projectJson(
                "\"sceneSchemaVersion\":3,"));

        RuntimeConfig config = RuntimeProjectIO.loadProject(projectDir);

        SceneMetaRuntime scene = config.getCurrentSceneMeta();
        Assert.assertEquals(3, scene.sceneSchemaVersion);
        Assert.assertTrue(scene.physicsEnabled);
    }

    @Test
    public void removedMainCameraOffscreenMetadataIsIgnored() throws Exception {
        FileHandle projectDir = projectDirectory(projectJson(
                "\"sceneSchemaVersion\":3,\"mainCameraOffscreen\":true,"));

        RuntimeConfig config = RuntimeProjectIO.loadProject(projectDir);

        Assert.assertNotNull(config.getCurrentSceneMeta());
        try {
            SceneMetaRuntime.class.getDeclaredField("mainCameraOffscreen");
            Assert.fail("Removed runtime metadata must not remain declared.");
        } catch (NoSuchFieldException expected) {
            // Expected: stale exported JSON remains forward-compatible input only.
        }
    }

    @Test
    public void missingSceneSchemaVersionIsRejected() throws Exception {
        assertRejected("");
    }

    @Test
    public void sceneSchemaVersionZeroIsRejected() throws Exception {
        assertRejected("\"sceneSchemaVersion\":0,");
    }

    @Test
    public void sceneSchemaVersionOneIsRejected() throws Exception {
        assertRejected("\"sceneSchemaVersion\":1,");
    }

    @Test
    public void sceneSchemaVersionTwoIsRejected() throws Exception {
        assertRejected("\"sceneSchemaVersion\":2,");
    }

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
        Assert.assertEquals(2, registry.getByAssetId(123).clipCount());
        Assert.assertTrue(registry.getByAssetId(123).clip("attack").flipX());
    }

    @Test
    public void tileAnimationsJsonLoadsNames() throws Exception {
        File dir = makeTempDir("pixscape-runtime-tiled-animations");
        File file = new File(dir, "tiled-animations.json");
        FileWriter writer = new FileWriter(file);
        writer.write("{\"animations\":[{\"id\":100,\"name\":\"test\",\"frameAssetIds\":[101,102],\"frameDurationsMs\":[100,100]}]}");
        writer.close();

        TileAnimationRegistry registry = new TileAnimationRegistry();
        RuntimeProjectIO.loadTileAnimations(new FileHandle(dir), registry);

        Assert.assertEquals(1, registry.size());
        Assert.assertTrue(registry.containsName("test"));
        Assert.assertEquals(100, registry.idByName("test"));
    }

    private static void assertRejected(String versionField) throws Exception {
        try {
            RuntimeProjectIO.loadProject(
                    projectDirectory(projectJson(versionField)));
            Assert.fail("Scene schema version must be rejected.");
        } catch (RuntimeException expected) {
            Throwable cause = expected;
            while (cause != null
                    && (cause.getMessage() == null
                    || !cause.getMessage().contains("sceneSchemaVersion"))) {
                cause = cause.getCause();
            }
            Assert.assertNotNull(cause);
        }
    }

    private static FileHandle projectDirectory(String json) {
        File directory = makeTempDir("pixscape-runtime-scene-schema");
        new FileHandle(new File(directory, RuntimeProjectIO.PROJECT_JSON))
                .writeString(json, false, "UTF-8");
        return new FileHandle(directory);
    }

    private static String projectJson(String versionField) {
        return "{"
                + "\"projectFileName\":\"schema-test\","
                + "\"version\":\"1\","
                + "\"currentSceneName\":\"Main\","
                + "\"scenes\":{\"Main\":{"
                + versionField
                + "\"name\":\"Main\","
                + "\"file\":\"scene1.json\","
                + "\"physicsEnabled\":true,"
                + "\"nextEntityStableId\":1,"
                + "\"nextPhysicsShapeId\":1"
                + "}}"
                + "}";
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
