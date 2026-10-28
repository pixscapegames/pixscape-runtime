package games.pixscape.runtime.engine;

import com.badlogic.gdx.files.FileHandle;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PixscapeEngineSceneLessProjectTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadingSceneLessProjectCreatesNoWorldAndExplicitSceneLoadsStayStrict()
            throws Exception {
        FileHandle userRoot = new FileHandle(temporaryFolder.newFolder("user-root"));
        FileHandle project = userRoot.child("pixscape-project");
        project.mkdirs();
        project.child("project.json").writeString(
                "{"
                        + "\"projectFileName\":\"ui-only\","
                        + "\"version\":\"1\","
                        + "\"currentSceneName\":\"removed-scene\","
                        + "\"scenes\":{}"
                        + "}",
                false, "UTF-8");
        PixscapeEngine engine = new PixscapeEngine();
        try {
            engine.loadProject(userRoot);

            Assert.assertTrue(engine.isLoaded());
            Assert.assertEquals(0, engine.config().scenes.size);
            Assert.assertNull(engine.config().currentSceneName);
            Assert.assertNull(engine.getActiveSceneMeta());
            Assert.assertNull(engine.getWorld());
            engine.update(1f / 60f);
            engine.render();

            IllegalStateException absent = Assert.assertThrows(
                    IllegalStateException.class, () -> engine.loadScene(null));
            Assert.assertEquals("RuntimeConfig has no scenes.", absent.getMessage());
            IllegalArgumentException unknown = Assert.assertThrows(
                    IllegalArgumentException.class, () -> engine.loadScene("missing"));
            Assert.assertEquals("Unknown scene: missing", unknown.getMessage());
        } finally {
            engine.dispose();
        }
    }
}
