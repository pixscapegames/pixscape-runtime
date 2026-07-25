package games.pixscape.runtime.component;

import com.badlogic.gdx.utils.Json;
import org.junit.Assert;
import org.junit.Test;

public class TransformComponentPersistenceTest {
    @Test
    public void derivedCachesAreNotSerializedAndAreRebuiltFromSources() {
        TransformComponent source = new TransformComponent();
        source.rotationRad = 0.5f;
        source.scaleX = 2f;
        source.scaleY = 4f;
        source.cos = 99f;
        source.sin = 98f;
        source.absCos = 97f;
        source.absSin = 96f;
        source.invScaleX = 95f;
        source.invScaleY = 94f;

        Json json = new Json();
        String serialized = json.toJson(source);
        Assert.assertFalse(serialized.contains("\"cos\""));
        Assert.assertFalse(serialized.contains("\"sin\""));
        Assert.assertFalse(serialized.contains("\"absCos\""));
        Assert.assertFalse(serialized.contains("\"absSin\""));
        Assert.assertFalse(serialized.contains("\"invScaleX\""));
        Assert.assertFalse(serialized.contains("\"invScaleY\""));

        TransformComponent restored =
                json.fromJson(TransformComponent.class, serialized);
        restored.refreshCaches();
        Assert.assertEquals((float) Math.cos(0.5f), restored.cos, 0.0001f);
        Assert.assertEquals((float) Math.sin(0.5f), restored.sin, 0.0002f);
        Assert.assertEquals(0.5f, restored.invScaleX, 0f);
        Assert.assertEquals(0.25f, restored.invScaleY, 0f);
    }
}
