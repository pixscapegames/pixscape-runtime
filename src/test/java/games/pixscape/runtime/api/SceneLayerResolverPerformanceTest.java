package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.LayerComponent;
import org.junit.Assert;
import org.junit.Test;

public class SceneLayerResolverPerformanceTest {

    @Test
    public void layerLookupRetainsOnlyMatchingIndices() {
        World world = new World(new WorldConfigurationBuilder().build());
        for (int i = 0; i < 5000; i++) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = i + 100;
        }
        int targetEntity = world.create();
        LayerComponent target = world.getMapper(LayerComponent.class).create(targetEntity);
        target.layerIndex = 7;
        world.process();

        SceneLayerResolver resolver = new SceneLayerResolver();
        resolver.bind(world);

        Assert.assertEquals(1, resolver.matchingLayerCount(7));
        Assert.assertEquals(targetEntity, resolver.findLayerEntityId(7));
        Assert.assertEquals(0, resolver.matchingLayerCount(8));

        Assert.assertEquals(targetEntity, resolver.findLayerEntityId(7));
    }

    @Test
    public void bindingReplacementWorldDropsPreviousLayerState() {
        World first = worldWithSpatialLayer(3, true);
        World replacement = worldWithSpatialLayer(4, true);
        SceneLayerResolver resolver = new SceneLayerResolver();

        resolver.bind(first);
        Assert.assertEquals(1, resolver.matchingLayerCount(3));

        resolver.bind(replacement);
        Assert.assertEquals(0, resolver.matchingLayerCount(3));
        Assert.assertEquals(1, resolver.matchingLayerCount(4));

        resolver.bind(null);
        Assert.assertEquals(0, resolver.matchingLayerCount(4));
    }

    private static World worldWithSpatialLayer(int layerIndex, boolean enabled) {
        World world = new World(new WorldConfigurationBuilder().build());
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.layerIndex = layerIndex;
        world.process();
        return world;
    }
}
