package games.pixscape.runtime.loading;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import org.junit.Test;

import static org.junit.Assert.*;

public class SceneNormalMapSettingsTest {
    @Test public void defaultsCopiesAndJsonPreserveOneSceneWideValue() {
        String required="{sceneSchemaVersion:3,nextEntityStableId:1,nextPhysicsShapeId:1}";
        assertTrue(SceneMetaRuntime.fromJson(new JsonReader().parse(required),"default").useNormalMaps);
        SceneMetaRuntime first=new SceneMetaRuntime(),second=new SceneMetaRuntime();
        first.useNormalMaps=false;
        assertFalse(new SceneMetaRuntime(first).useNormalMaps);
        assertTrue(second.useNormalMaps);
        Json json=new Json();json.setUsePrototypes(false);
        assertFalse(SceneMetaRuntime.fromJson(new JsonReader().parse(json.toJson(first)),"first").useNormalMaps);
        first.useNormalMaps=true;
        assertTrue(SceneMetaRuntime.fromJson(new JsonReader().parse(json.toJson(first)),"first").useNormalMaps);
    }
}
