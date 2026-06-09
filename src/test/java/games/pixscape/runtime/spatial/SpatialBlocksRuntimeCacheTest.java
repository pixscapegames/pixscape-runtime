package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialBlocksRuntimeCacheTest {
    @Test
    public void storesFlatBlockAnchorSpans() {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();

        int block0 = cache.addBlock(3);
        cache.setAnchor(block0, 0, 300, 10);
        cache.setAnchor(block0, 1, 301, 11);
        cache.setAnchor(block0, 2, 302, 12);

        int block1 = cache.addBlock(2);
        cache.setAnchor(block1, 0, 400, 20);
        cache.setAnchor(block1, 1, 401, 21);

        Assert.assertEquals(2, cache.blockCount);
        Assert.assertEquals(5, cache.anchorCount);
        Assert.assertEquals(0, cache.blockAnchorOffset[block0]);
        Assert.assertEquals(3, cache.blockAnchorCount[block0]);
        Assert.assertEquals(3, cache.blockAnchorOffset[block1]);
        Assert.assertEquals(2, cache.blockAnchorCount[block1]);
        Assert.assertEquals(300, cache.anchorDrawSlot[0]);
        Assert.assertEquals(302, cache.anchorDrawSlot[2]);
        Assert.assertEquals(400, cache.anchorDrawSlot[3]);
        Assert.assertEquals(401, cache.anchorDrawSlot[4]);
        Assert.assertEquals(10, cache.anchorDrawIndex[0]);
        Assert.assertEquals(12, cache.anchorDrawIndex[2]);
        Assert.assertEquals(20, cache.anchorDrawIndex[3]);
        Assert.assertEquals(21, cache.anchorDrawIndex[4]);
    }

    @Test
    public void computesBlockDrawIndexRangeFromAnchors() {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();

        int block = cache.addBlock(3);
        cache.setAnchor(block, 0, 300, 10);
        cache.setAnchor(block, 1, 301, 14);
        cache.setAnchor(block, 2, 302, 12);
        cache.finalizeBlockRange(block);

        Assert.assertEquals(10, cache.blockAnchorStartDrawIndex[block]);
        Assert.assertEquals(14, cache.blockAnchorEndDrawIndex[block]);
    }

    @Test
    public void clearAllowsReuseWithoutLeakingCounts() {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();

        int first = cache.addBlock(2);
        cache.setAnchor(first, 0, 300, 10);
        cache.setAnchor(first, 1, 301, 11);
        cache.finalizeRanges();
        int blockCapacity = cache.blockAnchorOffset.length;
        int anchorCapacity = cache.anchorDrawSlot.length;

        cache.clear();
        int second = cache.addBlock(1);
        cache.setAnchor(second, 0, 400, 7);
        cache.finalizeRanges();

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(1, cache.anchorCount);
        Assert.assertEquals(blockCapacity, cache.blockAnchorOffset.length);
        Assert.assertEquals(anchorCapacity, cache.anchorDrawSlot.length);
        Assert.assertEquals(0, cache.blockAnchorOffset[second]);
        Assert.assertEquals(1, cache.blockAnchorCount[second]);
        Assert.assertEquals(400, cache.anchorDrawSlot[0]);
        Assert.assertEquals(7, cache.blockAnchorStartDrawIndex[second]);
        Assert.assertEquals(7, cache.blockAnchorEndDrawIndex[second]);
    }

    @Test
    public void unresolvedAnchorsLeaveBlockFrameUnresolved() {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        int block = cache.addBlock(2);
        cache.setAnchor(block, 0, 300, 10);

        cache.finalizeBlockRange(block);

        Assert.assertTrue(cache.hasResolvedBlock(block));
        Assert.assertEquals(10, cache.blockAnchorStartDrawIndex[block]);
        Assert.assertEquals(10, cache.blockAnchorEndDrawIndex[block]);
    }

    @Test
    public void fullyUnresolvedBlockIsKeptButInactive() {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        int block = cache.addBlock(2);

        cache.finalizeBlockRange(block);

        Assert.assertFalse(cache.hasResolvedBlock(block));
        Assert.assertEquals(-1, cache.blockAnchorStartDrawIndex[block]);
        Assert.assertEquals(-1, cache.blockAnchorEndDrawIndex[block]);
    }

    @Test
    public void invalidDrawSlotOrIndexFailsVisibly() {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        int block = cache.addBlock(1);

        try {
            cache.setAnchor(block, 0, -1, 10);
            Assert.fail("Expected invalid draw slot to fail.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("valid draw slot"));
        }

        try {
            cache.setAnchor(block, 0, 300, -1);
            Assert.fail("Expected invalid draw index to fail.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("valid draw slot"));
        }
    }

    @Test
    public void slotForTileResolvesDistinctCellsWithSameTileAssetId() {
        TiledMapLayerData map = new TiledMapLayerData(3, 3, 16, 16, 3);
        map.initSlotRange(300, 309);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 101);

        Assert.assertEquals(101, map.getTile(0, 0));
        Assert.assertEquals(101, map.getTile(1, 0));
        Assert.assertEquals(300, map.slotForTile(0, 0));
        Assert.assertEquals(301, map.slotForTile(1, 0));
        Assert.assertNotEquals(map.slotForTile(0, 0), map.slotForTile(1, 0));
    }
}
