package games.pixscape.runtime.configuration;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

public class RuntimeConfigTest {
    @Test
    public void emptySceneMapIsValidAndClearsStaleCurrentScene() {
        RuntimeConfig config = config();
        config.currentSceneName = "removed-scene";

        config.applyDefaultsAndValidate("project.json");

        Assert.assertEquals(0, config.scenes.size);
        Assert.assertNull(config.currentSceneName);
        Assert.assertNull(config.getCurrentSceneMeta());
        Assert.assertNull(config.firstSceneNameSorted());
    }

    @Test
    public void validCurrentSceneIsRetained() {
        RuntimeConfig config = config();
        config.scenes.put("B", scene("B", "b.json"));
        config.scenes.put("A", scene("A", "a.json"));
        config.currentSceneName = "B";

        config.applyDefaultsAndValidate("project.json");

        Assert.assertEquals("B", config.currentSceneName);
        Assert.assertSame(config.scenes.get("B"), config.getCurrentSceneMeta());
    }

    @Test
    public void invalidCurrentSceneUsesExistingDeterministicFallbacks() {
        RuntimeConfig defaultFile = config();
        defaultFile.scenes.put("Z", scene("Z", "scene1.json"));
        defaultFile.scenes.put("A", scene("A", "a.json"));
        defaultFile.currentSceneName = "missing";

        defaultFile.applyDefaultsAndValidate("project.json");

        Assert.assertEquals("Z", defaultFile.currentSceneName);

        RuntimeConfig alphabetical = config();
        alphabetical.scenes.put("B", scene("B", "b.json"));
        alphabetical.scenes.put("A", scene("A", "a.json"));
        alphabetical.currentSceneName = "missing";

        alphabetical.applyDefaultsAndValidate("project.json");

        Assert.assertEquals("A", alphabetical.currentSceneName);
    }

    @Test
    public void existingInvalidSceneMetadataIsStillRejected() {
        RuntimeConfig config = config();
        SceneMetaRuntime invalid = scene("Invalid", "invalid.json");
        invalid.sceneSchemaVersion = 2;
        config.scenes.put("Invalid", invalid);

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> config.applyDefaultsAndValidate("project.json"));

        Assert.assertTrue(failure.getMessage(),
                failure.getMessage().contains("sceneSchemaVersion 3"));
    }

    private static RuntimeConfig config() {
        RuntimeConfig config = new RuntimeConfig();
        config.projectFileName = "test-project";
        return config;
    }

    private static SceneMetaRuntime scene(String name, String file) {
        return new SceneMetaRuntime(name, file);
    }
}
