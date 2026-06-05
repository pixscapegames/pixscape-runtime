package games.pixscape.runtime.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

public class TiledMapLayerDataTest {

    @Test
    public void orthoFloatTileToWorldUsesContinuousGridCoordinates() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 40, 20, 4);
        map.originX = 3f;
        map.originY = -7f;

        Assert.assertEquals(63f, map.tileToWorldX(1.5f, 2.25f), 0.0001f);
        Assert.assertEquals(38f, map.tileToWorldY(1.5f, 2.25f), 0.0001f);
    }

    @Test
    public void isoFloatTileToWorldUsesConfiguredTileRatio() {
        TiledMapLayerData map = new TiledMapLayerData(
                8,
                8,
                90,
                30,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = 10f;
        map.originY = 20f;

        Assert.assertEquals(77.5f, map.tileToWorldX(2.5f, 1f), 0.0001f);
        Assert.assertEquals(72.5f, map.tileToWorldY(2.5f, 1f), 0.0001f);
    }

    @Test
    public void floatTileProjectionRoundTripsThroughContinuousWorldProjection() {
        TiledMapLayerData map = new TiledMapLayerData(
                8,
                8,
                70,
                22,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = -13f;
        map.originY = 5f;

        float gx = 3.25f;
        float gy = 1.75f;
        float worldX = map.tileToWorldX(gx, gy);
        float worldY = map.tileToWorldY(gx, gy);

        Assert.assertEquals(gx, map.projectWorldToTileX(worldX, worldY), 0.0001f);
        Assert.assertEquals(gy, map.projectWorldToTileY(worldX, worldY), 0.0001f);
    }

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
