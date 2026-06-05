package games.pixscape.runtime.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

public class TiledMapLayerDataTest {

    @Test
    public void isoWorldToTileHandlesOriginAndNegativeWorldX() {
        TiledMapLayerData map = new TiledMapLayerData(
                8,
                8,
                64,
                32,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = -128f;
        map.originY = 256f;

        assertTileCenterMapsBackToCell(map, 0, 0);
        assertTileCenterMapsBackToCell(map, 0, 2);
        assertTileCenterMapsBackToCell(map, 3, 1);
    }

    private static void assertTileCenterMapsBackToCell(TiledMapLayerData map, int gx, int gy) {
        float worldX = map.tileToWorldX(gx, gy) + map.tileWidth * 0.5f;
        float worldY = map.tileToWorldY(gx, gy) + map.tileHeight * 0.5f;

        Assert.assertEquals(gx, map.worldToTileX(worldX, worldY));
        Assert.assertEquals(gy, map.worldToTileY(worldX, worldY));
    }
}
