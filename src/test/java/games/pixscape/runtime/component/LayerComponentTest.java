package games.pixscape.runtime.component;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class LayerComponentTest {
    @Test
    public void pooledResetRestoresFinalLayerDefaults() {
        World world = new World(new WorldConfiguration());
        try {
            ComponentMapper<LayerComponent> layers = world.getMapper(LayerComponent.class);
            int first = world.create();
            LayerComponent layer = layers.create(first);
            layer.layerIndex = 12;
            layer.spatialEnabled = true;

            layers.remove(first);
            LayerComponent restored = layers.create(world.create());

            Assert.assertEquals(0, restored.layerIndex);
            Assert.assertFalse(restored.spatialEnabled);
        } finally {
            world.dispose();
        }
    }
}
