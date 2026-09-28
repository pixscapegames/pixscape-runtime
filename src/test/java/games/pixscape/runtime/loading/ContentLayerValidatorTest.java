package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import org.junit.Assert;
import org.junit.Test;

public class ContentLayerValidatorTest {
    @Test
    public void indexedContentCannotAlsoOwnLayerMetadata() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 2;
        int content = world.create();
        world.getMapper(EntityIndexComponent.class).create(content).layerIndex = 2;
        world.process();
        ContentLayerValidator.validateWorld(world, "Scene 'valid'");

        world.getMapper(LayerComponent.class).create(content).layerIndex = 2;
        world.process();
        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                () -> ContentLayerValidator.validateWorld(world, "Scene 'invalid'"));
        Assert.assertTrue(failure.getMessage().contains("Scene 'invalid' entityId=" + content));
    }

    @Test
    public void serializedSceneReportsSourceAndEntityBeforeLoading() {
        String scene = "{\"entities\":{\"42\":{\"components\":{"
                + "\"LayerComponent\":{},\"EntityIndexComponent\":{}}}}}";
        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
                () -> ContentLayerValidator.validateSerializedScene(scene, "Scene 'scene.json'"));
        Assert.assertTrue(failure.getMessage().contains("Scene 'scene.json' entity=42"));
    }
}
