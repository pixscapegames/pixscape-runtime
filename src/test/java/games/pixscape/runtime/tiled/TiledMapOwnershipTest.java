package games.pixscape.runtime.tiled;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import org.junit.Assert;
import org.junit.Test;

public class TiledMapOwnershipTest {
    @Test
    public void transitionalWorldRequiresDistinctUniqueMapForTiledHost() {
        World world = new World(new WorldConfiguration());
        int host = tiledHost(world, 4);
        int map = tiledMap(world, 4);
        world.process();

        TiledMapOwnership.validateTransitionalWorld(world);
        Assert.assertNotEquals(host, map);
        Assert.assertFalse(world.getMapper(LayerComponent.class).has(map));
        Assert.assertTrue(world.getMapper(EntityIndexComponent.class).has(map));
    }

    @Test
    public void transitionalWorldRejectsSecondMapInSameHost() {
        World world = new World(new WorldConfiguration());
        tiledHost(world, 1);
        tiledMap(world, 1);
        tiledMap(world, 1);
        world.process();

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> TiledMapOwnership.validateTransitionalWorld(world));
        Assert.assertTrue(failure.getMessage().contains("exactly one"));
    }

    private static int tiledHost(World world, int layerIndex) {
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = layerIndex;
        return entity;
    }

    private static int tiledMap(World world, int layerIndex) {
        int entity = world.create();
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layerIndex;
        index.zIndex = 0;
        world.getMapper(TiledLayerComponent.class).create(entity);
        return entity;
    }
}
