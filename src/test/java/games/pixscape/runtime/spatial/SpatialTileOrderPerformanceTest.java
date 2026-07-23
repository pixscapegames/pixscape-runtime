package games.pixscape.runtime.spatial;

import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialTileOrderPerformanceTest {
    @Test
    public void staticCompileScalesAndWarmReadsDoNotRecompile() {
        SpatialTileOrderCache large = compile(filledMap(256, 256), new SpatialBlocksComponent());

        TiledMapLayerData anchoredMap = filledMap(512, 2);
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 1; wall.structureId = 1; wall.x = 0; wall.y = 0; wall.width = 512; wall.depth = 1;
        wall.height = 32; wall.beginAuthoredLinkedTileRefs();
        for (int gx = 0; gx < 512; gx++) wall.addLinkedTileRef(gx, 0, 1);
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        blocks.blocks.add(wall); blocks.revision = 1;
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(blocks);
        SpatialTileOrderCache anchored = new SpatialTileOrderCache();
        anchored.ensure(3, anchoredMap, blocks, compiled);

        for (int i = 0; i < 10000; i++) Assert.assertFalse(anchored.ensure(3, anchoredMap, blocks, compiled));

        Assert.assertEquals(65536, large.tileOrderNodeCount);
        Assert.assertTrue(anchored.tileOrderSegmentCount >= 514);
        Assert.assertEquals(1, anchored.tileOrderCompileCount);
    }

    private static SpatialTileOrderCache compile(TiledMapLayerData map, SpatialBlocksComponent blocks) {
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(blocks);
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        order.ensure(1, map, blocks, compiled);
        return order;
    }

    private static TiledMapLayerData filledMap(int width, int height) {
        TiledMapLayerData map = new TiledMapLayerData(width, height, 32, 16, 32,
                SceneMetaRuntime.TiledProjection.ISO);
        map.beginContentMutation();
        for (int gy = 0; gy < height; gy++) for (int gx = 0; gx < width; gx++) map.setTile(gx, gy, 1);
        map.endContentMutation();
        return map;
    }
}
