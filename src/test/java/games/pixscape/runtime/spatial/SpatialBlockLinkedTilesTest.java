package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockLinkedTilesTest {
    private final SpatialBlockLinkedTiles.Refs refs = new SpatialBlockLinkedTiles.Refs();

    @Test
    public void singleTileIntersectingFootprintIsLinked() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(0, 0, 101);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 1f, 1f), map, refs);

        assertRefs(refs, 0, 0);
        Assert.assertEquals(map.slotForTile(0, 0), refs.slot(0));
    }

    @Test
    public void footprintLinksMultipleIntersectingTiles() {
        TiledMapLayerData map = orthoMap(4, 4);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 102);
        map.setTile(0, 1, 103);
        map.setTile(2, 2, 104);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 2f, 2f), map, refs);

        assertRefs(refs, 0, 0, 1, 0, 0, 1);
    }

    @Test
    public void emptyOriginCellCanLinkNeighborTileByIntersection() {
        TiledMapLayerData map = orthoMap(4, 4);
        map.setTile(1, 0, 101);

        SpatialBlockLinkedTiles.compute(block(0.75f, 0f, 0.75f, 1f), map, refs);

        assertRefs(refs, 1, 0);
    }

    @Test
    public void isoProjectionLinksByTileCellFootprintIntersection() {
        TiledMapLayerData map = isoMap(4, 4);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 102);
        map.setTile(0, 1, 103);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 2f, 2f), map, refs);

        assertRefs(refs, 0, 0, 1, 0, 0, 1);
    }

    @Test
    public void isoCellFootprintUsesGridXAndGridY() {
        TiledMapLayerData map = isoMap(16, 16);
        float[] left = new float[8];
        float[] right = new float[8];

        map.tileToCellVertices(8, 9, left);
        map.tileToCellVertices(9, 9, right);

        Assert.assertNotEquals(left[0], right[0], 0.0001f);
        Assert.assertNotEquals(left[1], right[1], 0.0001f);
        Assert.assertEquals(45f, right[0] - left[0], 0.0001f);
        Assert.assertEquals(15f, right[1] - left[1], 0.0001f);
        for (int i = 2; i < 8; i += 2) {
            Assert.assertEquals(45f, right[i] - left[i], 0.0001f);
            Assert.assertEquals(15f, right[i + 1] - left[i + 1], 0.0001f);
        }
    }

    @Test
    public void isoBlockLinksTileOnSameCell() {
        TiledMapLayerData map = isoMap(16, 16);
        map.setTile(8, 9, 101);

        SpatialBlockLinkedTiles.compute(block(8f, 9f, 1f, 1f), map, refs);

        assertRefs(refs, 8, 9);
    }

    @Test
    public void thinBlockLinksTileWhenCellFootprintIntersects() {
        TiledMapLayerData map = isoMap(16, 16);
        map.setTile(9, 10, 101);

        SpatialBlockLinkedTiles.compute(block(9.003479f, 10.03523f, 3.035983f, 0.21065998f), map, refs);

        assertRefs(refs, 9, 10);
    }

    @Test
    public void tileBaseCanMissBlockWhileCellFootprintLinksTile() {
        TiledMapLayerData map = orthoMap(2, 2);
        map.setTile(0, 0, 101);

        SpatialBlockLinkedTiles.compute(block(0.1f, 0.1f, 0.8f, 0.2f), map, refs);

        assertRefs(refs, 0, 0);
        float[] base = new float[4];
        refs.baseSegment(0, base);
        Assert.assertEquals(0f, base[0], 0.0001f);
        Assert.assertEquals(16f, base[1], 0.0001f);
        Assert.assertEquals(16f, base[2], 0.0001f);
        Assert.assertEquals(16f, base[3], 0.0001f);
    }

    @Test
    public void emptyCellProducesNoRefs() {
        TiledMapLayerData map = orthoMap(2, 2);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 1f, 1f), map, refs);

        Assert.assertEquals(0, refs.count);
    }

    @Test
    public void tileWithoutRenderSlotProducesNoRefs() {
        TiledMapLayerData map = orthoMap(1, 1);
        map.setTile(0, 0, 101);
        map.getChunk(0, 0).soaStartIndex = -1;

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 1f, 1f), map, refs);

        Assert.assertEquals(0, refs.count);
    }

    @Test
    public void tileOutsideBlockFootprintProducesNoRefs() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(2, 2, 101);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 1f, 1f), map, refs);

        Assert.assertEquals(0, refs.count);
    }

    @Test
    public void noIntersectingTilesProducesNoRefs() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(2, 2, 101);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 1f, 1f), map, refs);

        Assert.assertEquals(0, refs.count);
    }

    @Test
    public void refsAreEmittedInStableGridOrder() {
        TiledMapLayerData map = orthoMap(4, 4);
        map.setTile(1, 1, 101);
        map.setTile(0, 0, 102);
        map.setTile(1, 0, 103);
        map.setTile(0, 1, 104);

        SpatialBlockLinkedTiles.compute(block(0f, 0f, 2f, 2f), map, refs);

        assertRefs(refs, 0, 0, 1, 0, 0, 1, 1, 1);
    }

    @Test
    public void sameTileAssetIdCanAppearInMultipleCells() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 101);
        SpatialBlockData block = block(0f, 0f, 2f, 1f);

        SpatialBlockLinkedTiles.compute(block, map, refs);

        assertRefs(refs, 0, 0, 1, 0);
        Assert.assertEquals(101, refs.tileAssetId(0));
        Assert.assertEquals(101, refs.tileAssetId(1));
        Assert.assertNotEquals(refs.slot(0), refs.slot(1));
    }

    @Test
    public void authoredRefsUseExplicitCellsWithoutIntersectionFallback() {
        TiledMapLayerData map = orthoMap(4, 4);
        map.setTile(0, 0, 101);
        map.setTile(2, 2, 202);
        SpatialBlockData block = block(0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 2, 202);

        SpatialBlockLinkedTiles.compute(block, map, refs);

        assertRefs(refs, 2, 2);
        Assert.assertEquals(202, refs.tileAssetId(0));
    }

    @Test
    public void authoredRefUsesOwningCellWhenAuthoredTileAssetIdDiffersFromCurrentTile() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(1, 1, 303);
        SpatialBlockData block = block(0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(1, 1, 202);

        SpatialBlockLinkedTiles.compute(block, map, refs);

        assertRefs(refs, 1, 1);
        Assert.assertEquals(303, refs.tileAssetId(0));
        Assert.assertEquals(map.slotForTile(1, 1), refs.slot(0));
    }

    @Test
    public void authoredEmptyRefIsIgnoredWithoutAutoFallback() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(0, 0, 101);
        SpatialBlockData block = block(0f, 0f, 1f, 1f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(1, 1, 0);

        SpatialBlockLinkedTiles.compute(block, map, refs);

        Assert.assertEquals(0, refs.count);
    }

    @Test
    public void authoredRefsKeepAuthorOrder() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(0, 0, 101);
        map.setTile(1, 1, 202);
        SpatialBlockData block = block(0f, 0f, 2f, 2f);
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(1, 1, 202);
        block.addLinkedTileRef(0, 0, 101);

        SpatialBlockLinkedTiles.compute(block, map, refs);

        assertRefs(refs, 1, 1, 0, 0);
    }

    @Test
    public void movingBlockChangesLinkedRefs() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 102);
        SpatialBlockData block = block(0f, 0f, 1f, 1f);

        SpatialBlockLinkedTiles.compute(block, map, refs);
        assertRefs(refs, 0, 0);

        block.x = 1f;
        SpatialBlockLinkedTiles.compute(block, map, refs);
        assertRefs(refs, 1, 0);
    }

    @Test
    public void resizingBlockChangesLinkedRefs() {
        TiledMapLayerData map = orthoMap(3, 3);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 102);
        SpatialBlockData block = block(0f, 0f, 1f, 1f);

        SpatialBlockLinkedTiles.compute(block, map, refs);
        assertRefs(refs, 0, 0);

        block.width = 2f;
        SpatialBlockLinkedTiles.compute(block, map, refs);
        assertRefs(refs, 0, 0, 1, 0);
    }

    private static TiledMapLayerData orthoMap(int width, int height) {
        return map(width, height, 16, 16, SceneMetaRuntime.TiledProjection.ORTHO);
    }

    private static TiledMapLayerData isoMap(int width, int height) {
        return map(width, height, 90, 30, SceneMetaRuntime.TiledProjection.ISO);
    }

    private static TiledMapLayerData map(int width,
                                         int height,
                                         int tileWidth,
                                         int tileHeight,
                                         SceneMetaRuntime.TiledProjection projection) {
        TiledMapLayerData map = new TiledMapLayerData(width, height, tileWidth, tileHeight, Math.max(width, height), projection);
        map.initSlotRange(300, 300 + width * height);
        return map;
    }

    private static SpatialBlockData block(float x, float y, float width, float depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 10;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.height = 10f;
        return block;
    }

    private static void assertRefs(SpatialBlockLinkedTiles.Refs refs, int... gxGyPairs) {
        Assert.assertEquals(gxGyPairs.length / 2, refs.count);
        for (int i = 0; i < refs.count; i++) {
            Assert.assertEquals(gxGyPairs[i * 2], refs.gx(i));
            Assert.assertEquals(gxGyPairs[i * 2 + 1], refs.gy(i));
        }
    }
}
