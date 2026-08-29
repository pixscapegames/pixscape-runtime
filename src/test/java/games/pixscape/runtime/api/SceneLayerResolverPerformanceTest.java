package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.LayerComponent;
import org.junit.Assert;
import org.junit.Test;

public class SceneLayerResolverPerformanceTest {

    @Test
    public void spatialLookupVisitsOnlyLayersWithRequestedIndex() {
        World world = new World(new WorldConfigurationBuilder().build());
        for (int i = 0; i < 5000; i++) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = i + 100;
        }
        int targetEntity = world.create();
        LayerComponent target = world.getMapper(LayerComponent.class).create(targetEntity);
        target.layerIndex = 7;
        target.spatialEnabled = true;
        world.process();

        SceneLayerResolver resolver = new SceneLayerResolver();
        resolver.bind(world);

        Assert.assertEquals(1, resolver.matchingLayerCount(7));
        Assert.assertTrue(resolver.isActorSpatialLayerEnabled(7));
        Assert.assertEquals(1, resolver.lastSpatialLookupVisitCount());
        Assert.assertFalse(resolver.isActorSpatialLayerEnabled(8));
        Assert.assertEquals(0, resolver.lastSpatialLookupVisitCount());

        target.spatialEnabled = false;
        Assert.assertFalse(resolver.isActorSpatialLayerEnabled(7));
        Assert.assertEquals(1, resolver.lastSpatialLookupVisitCount());
    }

    @Test
    public void bindingReplacementWorldDropsPreviousLayerState() {
        World first = worldWithSpatialLayer(3, true);
        World replacement = worldWithSpatialLayer(4, true);
        SceneLayerResolver resolver = new SceneLayerResolver();

        resolver.bind(first);
        Assert.assertTrue(resolver.isActorSpatialLayerEnabled(3));

        resolver.bind(replacement);
        Assert.assertFalse(resolver.isActorSpatialLayerEnabled(3));
        Assert.assertTrue(resolver.isActorSpatialLayerEnabled(4));

        resolver.bind(null);
        Assert.assertFalse(resolver.isActorSpatialLayerEnabled(4));
    }

    private static World worldWithSpatialLayer(int layerIndex, boolean enabled) {
        World world = new World(new WorldConfigurationBuilder().build());
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.layerIndex = layerIndex;
        layer.spatialEnabled = enabled;
        world.process();
        return world;
    }
}
