package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlockRuntimeGeometryTest {

    @Test
    public void tileCellFootprintCornersProjectInOrtho() {
        TiledMapLayerData map = new TiledMapLayerData(16, 16, 32, 20, 4);
        map.originX = 5f;
        map.originY = -3f;
        SpatialBlockData block = block(7, 1.5f, 2f, 3f, 2.5f);
        float[] out = new float[8];

        Assert.assertTrue(SpatialBlockGeometry.writeTileCellFootprint(block, map, out));

        Assert.assertEquals(53f, out[0], 0.0001f);
        Assert.assertEquals(37f, out[1], 0.0001f);
        Assert.assertEquals(149f, out[2], 0.0001f);
        Assert.assertEquals(37f, out[3], 0.0001f);
        Assert.assertEquals(149f, out[4], 0.0001f);
        Assert.assertEquals(87f, out[5], 0.0001f);
        Assert.assertEquals(53f, out[6], 0.0001f);
        Assert.assertEquals(87f, out[7], 0.0001f);
    }

    @Test
    public void tileCellFootprintCornersProjectInIsoWithoutTwoToOneAssumption() {
        TiledMapLayerData map = new TiledMapLayerData(
                16,
                16,
                90,
                30,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = 10f;
        map.originY = 20f;
        SpatialBlockData block = block(7, 1f, 2f, 2f, 1.5f);
        float[] out = new float[8];

        Assert.assertTrue(SpatialBlockGeometry.writeTileCellFootprint(block, map, out));

        Assert.assertEquals(10f, out[0], 0.0001f);
        Assert.assertEquals(65f, out[1], 0.0001f);
        Assert.assertEquals(100f, out[2], 0.0001f);
        Assert.assertEquals(95f, out[3], 0.0001f);
        Assert.assertEquals(32.5f, out[4], 0.0001f);
        Assert.assertEquals(117.5f, out[5], 0.0001f);
        Assert.assertEquals(-57.5f, out[6], 0.0001f);
        Assert.assertEquals(87.5f, out[7], 0.0001f);
    }

    @Test
    public void oneByOneIsoBlockFootprintMatchesTileCellFootprint() {
        TiledMapLayerData map = new TiledMapLayerData(
                16,
                16,
                90,
                30,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        SpatialBlockData block = block(8, 8f, 9f, 1f, 1f);
        float[] blockFootprint = new float[8];
        float[] tileFootprint = new float[8];

        Assert.assertTrue(SpatialBlockGeometry.writeTileCellFootprint(block, map, blockFootprint));
        map.tileToCellVertices(8, 9, tileFootprint);

        assertPoint(blockFootprint, 0, tileFootprint, 0);
        assertPoint(blockFootprint, 2, tileFootprint, 6);
        assertPoint(blockFootprint, 4, tileFootprint, 4);
        assertPoint(blockFootprint, 6, tileFootprint, 2);
    }

    @Test
    public void thinIsoBlockFootprintUsesCellOriginOffset() {
        TiledMapLayerData map = new TiledMapLayerData(
                16,
                16,
                90,
                30,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        SpatialBlockData block = block(9, 9.003479f, 10.03523f, 3.035983f, 0.21065998f);
        float[] footprint = new float[8];

        Assert.assertTrue(SpatialBlockGeometry.writeTileCellFootprint(block, map, footprint));

        Assert.assertEquals(map.tileToWorldX(block.x, block.y) + 45f, footprint[0], 0.0001f);
        Assert.assertEquals(map.tileToWorldY(block.x, block.y), footprint[1], 0.0001f);
        Assert.assertEquals(map.tileToWorldX(block.x + block.width, block.y) + 45f, footprint[2], 0.0001f);
        Assert.assertEquals(map.tileToWorldY(block.x + block.width, block.y), footprint[3], 0.0001f);
    }

    @Test
    public void altitudeHeightAndHalfOpenCoverageRangeAreComputed() {
        SpatialBlockData block = block(9, 1.25f, 2.1f, 2.5f, 3f);
        block.altitude = 6f;
        block.height = 18f;
        SpatialBlockGeometry.CellRange range = new SpatialBlockGeometry.CellRange();

        Assert.assertEquals(6f, SpatialBlockGeometry.bottom(block), 0.0001f);
        Assert.assertEquals(24f, SpatialBlockGeometry.top(block), 0.0001f);
        Assert.assertTrue(SpatialBlockGeometry.writeCoveredCellRange(block, range));

        Assert.assertEquals(1, range.minGx);
        Assert.assertEquals(4, range.maxGxExclusive);
        Assert.assertEquals(2, range.minGy);
        Assert.assertEquals(6, range.maxGyExclusive);
        Assert.assertTrue(SpatialBlockGeometry.containsTilePoint(block, 1.25f, 2.1f));
        Assert.assertFalse(SpatialBlockGeometry.containsTilePoint(block, 3.75f, 5.1f));
    }

    @Test
    public void unsupportedOrientationDoesNotProduceFootprintOrCoverage() {
        TiledMapLayerData map = new TiledMapLayerData(16, 16, 32, 20, 4);
        SpatialBlockData block = block(7, 1f, 2f, 3f, 4f);
        block.orientation = SpatialBlockOrientation.TILE_AXIS_X;

        Assert.assertFalse(SpatialBlockGeometry.writeTileCellFootprint(block, map, new float[8]));
        Assert.assertFalse(SpatialBlockGeometry.writeCoveredCellRange(block, new SpatialBlockGeometry.CellRange()));
    }

    @Test
    public void emptyComponentProducesEmptyIndex() {
        SpatialBlockIndex index = new SpatialBlockIndex();

        index.rebuild(42, new SpatialBlocksComponent());

        Assert.assertEquals(42, index.getLayerEntity());
        Assert.assertEquals(0, index.getRefCount());
        Assert.assertEquals(0, index.getCellBucketCount());
    }

    @Test
    public void oneBlockIndexesExpectedCellsAndRefs() {
        SpatialBlockIndex index = new SpatialBlockIndex();
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        component.blocks.add(block(77, 1f, 2f, 1f, 1f));
        IntArray out = new IntArray(false, 4);

        index.rebuild(11, component);
        index.queryCell(1, 2, out);

        Assert.assertEquals(1, index.getRefCount());
        Assert.assertEquals(1, out.size);
        int ref = out.get(0);
        Assert.assertEquals(11, index.getRefOwnerLayer(ref));
        Assert.assertEquals(0, index.getRefBlockIndex(ref));
        Assert.assertEquals(77, index.getRefBlockId(ref));
        Assert.assertEquals(1, index.getRefMinGx(ref));
        Assert.assertEquals(2, index.getRefMaxGxExclusive(ref));
        Assert.assertEquals(2, index.getRefMinGy(ref));
        Assert.assertEquals(3, index.getRefMaxGyExclusive(ref));
    }

    @Test
    public void multiCellBlockIndexesAllCoveredCellsWithHalfOpenRanges() {
        SpatialBlockIndex index = new SpatialBlockIndex();
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        component.blocks.add(block(5, 1.2f, 2.2f, 2.1f, 1.1f));
        IntArray out = new IntArray(false, 4);

        index.rebuild(3, component);

        assertSingleBlockAt(index, 1, 2, out);
        assertSingleBlockAt(index, 2, 2, out);
        assertSingleBlockAt(index, 3, 2, out);
        assertSingleBlockAt(index, 1, 3, out);
        assertSingleBlockAt(index, 2, 3, out);
        assertSingleBlockAt(index, 3, 3, out);

        index.queryCell(4, 3, out);
        Assert.assertEquals(0, out.size);
    }

    @Test
    public void indexSkipsBlocksThatAreNotActorOccludingTileCells() {
        SpatialBlockIndex index = new SpatialBlockIndex();
        SpatialBlocksComponent component = new SpatialBlocksComponent();

        SpatialBlockData disabled = block(1, 0f, 0f, 1f, 1f);
        disabled.enabled = false;
        component.blocks.add(disabled);

        SpatialBlockData nonActorOccluder = block(2, 1f, 0f, 1f, 1f);
        nonActorOccluder.actorOccluder = false;
        component.blocks.add(nonActorOccluder);

        SpatialBlockData zeroHeight = block(3, 2f, 0f, 1f, 1f);
        zeroHeight.height = 0f;
        component.blocks.add(zeroHeight);

        SpatialBlockData unsupported = block(4, 3f, 0f, 1f, 1f);
        unsupported.orientation = SpatialBlockOrientation.FREE_AXIS;
        component.blocks.add(unsupported);

        SpatialBlockData invalidFootprint = block(5, 4f, 0f, 0f, 1f);
        component.blocks.add(invalidFootprint);

        index.rebuild(8, component);

        Assert.assertEquals(0, index.getRefCount());
        Assert.assertEquals(1, index.getSkippedDisabled());
        Assert.assertEquals(1, index.getSkippedNonActorOccluder());
        Assert.assertEquals(1, index.getSkippedZeroHeight());
        Assert.assertEquals(1, index.getSkippedUnsupportedOrientation());
        Assert.assertEquals(1, index.getSkippedInvalidFootprint());
    }

    @Test
    public void queryRangeDeduplicatesRefsAndUsesOnlyVisitedLocalBuckets() {
        SpatialBlockIndex index = new SpatialBlockIndex();
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        component.blocks.add(block(1, 0f, 0f, 2f, 2f));
        for (int i = 0; i < 40; i++) {
            component.blocks.add(block(100 + i, 100f + i, 100f + i, 1f, 1f));
        }
        IntArray out = new IntArray(false, 8);

        index.rebuild(4, component);
        index.queryRange(0, 2, 0, 2, out);

        Assert.assertEquals(1, out.size);
        Assert.assertEquals(1, index.getRefBlockId(out.get(0)));
        Assert.assertEquals(4, index.getLastVisitedCellCount());
        Assert.assertEquals(4, index.getLastVisitedEntryCount());
    }

    @Test
    public void farCellReturnsNoCandidates() {
        SpatialBlockIndex index = new SpatialBlockIndex();
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        component.blocks.add(block(1, 0f, 0f, 1f, 1f));
        IntArray out = new IntArray(false, 4);

        index.rebuild(4, component);
        index.queryCell(20, 20, out);

        Assert.assertEquals(0, out.size);
        Assert.assertEquals(1, index.getLastVisitedCellCount());
        Assert.assertEquals(0, index.getLastVisitedEntryCount());
    }

    private static void assertSingleBlockAt(SpatialBlockIndex index, int gx, int gy, IntArray out) {
        index.queryCell(gx, gy, out);
        Assert.assertEquals("Expected block at " + gx + "," + gy, 1, out.size);
    }

    private static void assertPoint(float[] actual, int actualOffset, float[] expected, int expectedOffset) {
        Assert.assertEquals(expected[expectedOffset], actual[actualOffset], 0.0001f);
        Assert.assertEquals(expected[expectedOffset + 1], actual[actualOffset + 1], 0.0001f);
    }

    private static SpatialBlockData block(int id, float x, float y, float width, float depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.height = 10f;
        return block;
    }
}
