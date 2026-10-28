package games.pixscape.runtime.loading;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import org.junit.Assert;
import org.junit.Test;

public class SceneMetaRuntimeHudTest {
    @Test
    public void sceneWithoutHudRoundTripsAsNoDefaultHud() {
        SceneMetaRuntime source = new SceneMetaRuntime("scene", "scene.json");

        SceneMetaRuntime restored = roundTrip(source);

        Assert.assertNull(restored.defaultHudScreenId);
    }

    @Test
    public void sceneDefaultHudLogicalIdSurvivesRoundTrip() {
        SceneMetaRuntime source = new SceneMetaRuntime("scene", "scene.json");
        source.defaultHudScreenId = "hud/game";

        SceneMetaRuntime restored = roundTrip(source);

        Assert.assertEquals("hud/game", restored.defaultHudScreenId);
    }

    @Test
    public void legacySceneMetadataWithoutHudLoadsWithNoDefaultHud() {
        SceneMetaRuntime restored = SceneMetaRuntime.fromJson(
                new JsonReader().parse("{\"sceneSchemaVersion\":3,"
                        + "\"nextEntityStableId\":1,\"nextPhysicsShapeId\":1}"),
                "legacy");

        Assert.assertNull(restored.defaultHudScreenId);
    }

    @Test
    public void emptyHudReferencesNormalizeToNoDefaultHud() {
        Assert.assertNull(parseHudReference("null"));
        Assert.assertNull(parseHudReference("\"\""));
        Assert.assertNull(parseHudReference("\"   \""));
    }

    @Test
    public void copiedScenePreservesCanonicalDefaultHudReference() {
        SceneMetaRuntime source = new SceneMetaRuntime();
        source.defaultHudScreenId = "game";

        SceneMetaRuntime copied = new SceneMetaRuntime(source);

        Assert.assertEquals("hud/game", copied.defaultHudScreenId);
    }

    private static SceneMetaRuntime roundTrip(SceneMetaRuntime source) {
        Json json = new Json();
        json.setUsePrototypes(false);
        String serialized = json.toJson(source);
        return SceneMetaRuntime.fromJson(new JsonReader().parse(serialized), "fallback");
    }

    private static String parseHudReference(String value) {
        String serialized = "{\"sceneSchemaVersion\":3,"
                + "\"nextEntityStableId\":1,\"nextPhysicsShapeId\":1,"
                + "\"defaultHudScreenId\":" + value + "}";
        return SceneMetaRuntime.fromJson(
                new JsonReader().parse(serialized), "scene").defaultHudScreenId;
    }
}
