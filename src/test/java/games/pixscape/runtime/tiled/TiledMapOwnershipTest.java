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
    public void layerMayOwnMultipleIndependentMaps() {
        World world = new World(new WorldConfiguration());
        int layerEntity = layer(world, 2, LayerComponent.TYPE_CLASSIC);
        int first = tiledMap(world, 2);
        int second = tiledMap(world, 2);
        world.process();

        TiledMapOwnership.validateWorld(world);

        Assert.assertNotEquals(first, second);
        Assert.assertTrue(world.getMapper(LayerComponent.class).has(layerEntity));
    }

    @Test
    public void mapMustResolveToARealPixscapeLayer() {
        World world = new World(new WorldConfiguration());
        tiledMap(world, 9);
        world.process();

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> TiledMapOwnership.validateWorld(world));
        Assert.assertTrue(failure.getMessage().contains("Pixscape layerIndex=9"));
    }

    @Test
    public void mapMustHaveEntityIndex() {
        World world = new World(new WorldConfiguration());
        layer(world, 2, LayerComponent.TYPE_CLASSIC);
        world.getMapper(TiledLayerComponent.class).create(world.create());
        world.process();

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> TiledMapOwnership.validateWorld(world));
        Assert.assertTrue(failure.getMessage().contains("EntityIndexComponent"));
    }

    @Test
    public void mapMustNotAlsoBeALayer() {
        World world = new World(new WorldConfiguration());
        int entity = layer(world, 2, LayerComponent.TYPE_CLASSIC);
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = 2;
        world.getMapper(TiledLayerComponent.class).create(entity);
        world.process();

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> TiledMapOwnership.validateWorld(world));
        Assert.assertTrue(failure.getMessage().contains("must not also be a Pixscape layer"));
    }

    @Test
    public void duplicateLayerIndexIsRejected() {
        World world = new World(new WorldConfiguration());
        layer(world, 2, LayerComponent.TYPE_CLASSIC);
        layer(world, 2, LayerComponent.TYPE_CLASSIC);
        world.process();

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> TiledMapOwnership.validateWorld(world));
        Assert.assertTrue(failure.getMessage().contains("Multiple Pixscape layers"));
    }

    private static int layer(World world, int layerIndex, int type) {
        int entity = world.create();
        LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
        layer.type = type;
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
