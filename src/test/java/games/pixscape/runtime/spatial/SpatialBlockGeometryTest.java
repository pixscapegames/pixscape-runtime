package games.pixscape.runtime.spatial;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockGeometryTest {
    @Test
    public void projectBaseFootprintUsesOrderedOrthoCornersAndAltitude() {
        TiledMapLayerData map = new TiledMapLayerData();
        map.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        map.tileWidth = 10; map.tileHeight = 20; map.originX = 3f; map.originY = 5f;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1; block.x = 1.5f; block.y = 2.25f; block.width = 2f; block.depth = .5f; block.altitude = 4f;
        float[] out = new float[8];
        SpatialBlockGeometry.projectBaseFootprint(map, block, out);
        Assert.assertArrayEquals(new float[]{18f, 54f, 38f, 54f, 38f, 64f, 18f, 64f}, out, .0001f);
    }

    @Test
    public void projectBaseFootprintUsesCanonicalIsoCornersAndIgnoresHeight() {
        TiledMapLayerData map = new TiledMapLayerData();
        map.projection = SceneMetaRuntime.TiledProjection.ISO;
        map.tileWidth = 30; map.tileHeight = 18; map.originX = 7f; map.originY = 11f;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 2; block.x = 1.25f; block.y = 2.5f; block.width = 3.5f; block.depth = .75f; block.altitude = 6f;
        float[] first = new float[8];
        SpatialBlockGeometry.projectBaseFootprint(map, block, first);
        Assert.assertArrayEquals(new float[]{3.25f, 50.75f, 55.75f, 82.25f, 44.5f, 89f, -8f, 57.5f}, first, .0001f);
        block.height = 999f;
        float[] second = new float[8];
        SpatialBlockGeometry.projectBaseFootprint(map, block, second);
        Assert.assertArrayEquals(first, second, 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void projectBaseFootprintRejectsDegenerateBlock() {
        TiledMapLayerData map = new TiledMapLayerData(); map.tileWidth = 1; map.tileHeight = 1;
        SpatialBlockData block = new SpatialBlockData(); block.width = 0; block.depth = 1;
        SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]);
    }

    @Test
    public void projectBaseFootprintRejectsInvalidInputs() {
        TiledMapLayerData map = new TiledMapLayerData(); map.tileWidth = 1; map.tileHeight = 1;
        SpatialBlockData block = new SpatialBlockData(); block.id = 4; block.width = 1; block.depth = 1;
        reject(() -> SpatialBlockGeometry.projectBaseFootprint(null, block, new float[8]), "map");
        reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, null, new float[8]), "block");
        reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, null), "out8");
        reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[7]), "out8");
        map.projection = null; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "projection");
        map.projection = SceneMetaRuntime.TiledProjection.ORTHO; map.tileWidth = 0; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "tile");
        map.tileWidth = 1; map.originX = Float.NaN; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "origin");
        map.originX = 0; block.altitude = Float.NaN; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "geometry");
        block.altitude = 0; map.tileHeight = 0; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "tile");
        map.tileHeight = 1; map.originY = Float.POSITIVE_INFINITY; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "origin");
        map.originY = 0; block.x = Float.NaN; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "geometry");
        block.x = 0; block.y = Float.POSITIVE_INFINITY; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "geometry");
        block.y = 0; block.width = -1; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "width");
        block.width = 1; block.depth = 0; reject(() -> SpatialBlockGeometry.projectBaseFootprint(map, block, new float[8]), "depth");
    }

    private static void reject(Runnable action, String fragment) {
        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class, action::run);
        Assert.assertTrue(failure.getMessage().contains(fragment));
    }
}
