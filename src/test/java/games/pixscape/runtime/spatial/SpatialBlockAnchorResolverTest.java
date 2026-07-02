package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialBlockAnchorResolverTest {
    private final SpatialBlockAnchorResolver resolver = new SpatialBlockAnchorResolver();
    private final SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();

    @Test
    public void resolvesHorizontalSegmentAnchorsToDrawSlotsAndIndices() {
        TiledMapLayerData map = map(8, 8, 300);
        map.setTile(1, 2, 101);
        map.setTile(2, 2, 102);
        map.setTile(3, 2, 103);
        int slot0 = map.slotForTile(1, 2);
        int slot1 = map.slotForTile(2, 2);
        int slot2 = map.slotForTile(3, 2);

        resolver.resolve(blocks(block(1, 2, 101, 2, 2, 102, 3, 2, 103)),
                map,
                slotToDrawIndex(512, slot0, 14, slot1, 10, slot2, 12),
                cache);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(3, cache.anchorCount);
        Assert.assertEquals(3, cache.blockAnchorCount[0]);
        Assert.assertEquals(slot0, cache.anchorDrawSlot[0]);
        Assert.assertEquals(slot1, cache.anchorDrawSlot[1]);
        Assert.assertEquals(slot2, cache.anchorDrawSlot[2]);
        Assert.assertEquals(14, cache.anchorDrawIndex[0]);
        Assert.assertEquals(10, cache.anchorDrawIndex[1]);
        Assert.assertEquals(12, cache.anchorDrawIndex[2]);
        Assert.assertEquals(10, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(14, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void resolvesVerticalSegmentAnchors() {
        TiledMapLayerData map = map(8, 8, 300);
        map.setTile(4, 1, 201);
        map.setTile(4, 2, 202);
        map.setTile(4, 3, 203);
        int slot0 = map.slotForTile(4, 1);
        int slot1 = map.slotForTile(4, 2);
        int slot2 = map.slotForTile(4, 3);

        resolver.resolve(blocks(block(4, 1, 201, 4, 2, 202, 4, 3, 203)),
                map,
                slotToDrawIndex(512, slot0, 20, slot1, 21, slot2, 22),
                cache);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(3, cache.anchorCount);
        Assert.assertEquals(slot0, cache.anchorDrawSlot[0]);
        Assert.assertEquals(slot1, cache.anchorDrawSlot[1]);
        Assert.assertEquals(slot2, cache.anchorDrawSlot[2]);
        Assert.assertEquals(20, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(22, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void resolvesSingleCellSegmentAnchor() {
        TiledMapLayerData map = map(8, 8, 300);
        map.setTile(5, 5, 301);
        int slot = map.slotForTile(5, 5);

        resolver.resolve(blocks(block(5, 5, 301)), map, slotToDrawIndex(512, slot, 7), cache);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(1, cache.anchorCount);
        Assert.assertEquals(slot, cache.anchorDrawSlot[0]);
        Assert.assertEquals(7, cache.anchorDrawIndex[0]);
        Assert.assertEquals(7, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(7, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void resolvesAnchorThroughTiledRenderRefNotLegacySlot() {
        TiledMapLayerData map = map(4, 4, 300);
        assignRenderRefs(map, 20);
        map.setTile(1, 1, 301);
        int legacySlot = map.slotForTile(1, 1);
        int tiledRenderRef = map.tiledRenderRefForTile(1, 1);

        resolver.resolve(blocks(block(1, 1, 301)), map,
                refToDrawIndex(tiledRenderRef + 1, tiledRenderRef, 7), cache);

        Assert.assertNotEquals(legacySlot, tiledRenderRef);
        Assert.assertTrue(cache.hasResolvedBlock(0));
        Assert.assertEquals(tiledRenderRef, cache.anchorDrawSlot[0]);
        Assert.assertEquals(7, cache.anchorDrawIndex[0]);
    }

    @Test
    public void legacySlotMutationDoesNotAffectTiledRefAnchorResolution() {
        TiledMapLayerData map = map(4, 4, 300);
        assignRenderRefs(map, 40);
        map.setTile(2, 1, 301);
        int legacySlot = map.slotForTile(2, 1);
        int tiledRenderRef = map.tiledRenderRefForTile(2, 1);
        int[] tiledRefToDrawIndex = refToDrawIndex(tiledRenderRef + 1, tiledRenderRef, 9);
        if (legacySlot < tiledRefToDrawIndex.length) {
            tiledRefToDrawIndex[legacySlot] = -1;
        }

        resolver.resolve(blocks(block(2, 1, 301)), map, tiledRefToDrawIndex, cache);

        Assert.assertNotEquals(legacySlot, tiledRenderRef);
        Assert.assertTrue(cache.hasResolvedBlock(0));
        Assert.assertEquals(tiledRenderRef, cache.anchorDrawSlot[0]);
        Assert.assertEquals(9, cache.anchorDrawIndex[0]);
    }

    @Test
    public void resolvesRectangularAnchors() {
        TiledMapLayerData map = map(8, 8, 300);
        map.setTile(1, 1, 101);
        map.setTile(2, 1, 102);
        map.setTile(1, 2, 103);
        map.setTile(2, 2, 104);
        int slot0 = map.slotForTile(1, 1);
        int slot1 = map.slotForTile(2, 1);
        int slot2 = map.slotForTile(1, 2);
        int slot3 = map.slotForTile(2, 2);

        resolver.resolve(blocks(block(1, 1, 101, 2, 1, 102, 1, 2, 103, 2, 2, 104)),
                map,
                slotToDrawIndex(512, slot0, 1, slot1, 2, slot2, 3, slot3, 4),
                cache);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(4, cache.anchorCount);
        Assert.assertEquals(4, cache.blockAnchorCount[0]);
        Assert.assertEquals(slot0, cache.anchorDrawSlot[0]);
        Assert.assertEquals(slot1, cache.anchorDrawSlot[1]);
        Assert.assertEquals(slot2, cache.anchorDrawSlot[2]);
        Assert.assertEquals(slot3, cache.anchorDrawSlot[3]);
        Assert.assertEquals(1, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(4, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void unresolvedCellsInsideRectangularAnchorsAreSkippedForCurrentFrame() {
        TiledMapLayerData map = map(8, 8, 300);
        map.setTile(1, 1, 101);
        map.setTile(2, 2, 104);
        int slot0 = map.slotForTile(1, 1);
        int slot3 = map.slotForTile(2, 2);

        resolver.resolve(blocks(block(1, 1, 101, 2, 1, 102, 1, 2, 103, 2, 2, 104)),
                map,
                slotToDrawIndex(512, slot0, 1, slot3, 4),
                cache);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(4, cache.anchorCount);
        Assert.assertEquals(slot0, cache.anchorDrawSlot[0]);
        Assert.assertEquals(-1, cache.anchorDrawSlot[1]);
        Assert.assertEquals(-1, cache.anchorDrawSlot[2]);
        Assert.assertEquals(slot3, cache.anchorDrawSlot[3]);
        Assert.assertEquals(1, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(4, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void resolvesOnlyAgainstOwningLayerData() {
        TiledMapLayerData owningMap = map(4, 4, 300);
        TiledMapLayerData otherMap = map(4, 4, 600);
        owningMap.setTile(1, 1, 101);
        otherMap.setTile(1, 1, 101);
        int owningSlot = owningMap.slotForTile(1, 1);
        int otherSlot = otherMap.slotForTile(1, 1);

        resolver.resolve(blocks(block(1, 1, 101)), owningMap, slotToDrawIndex(700, owningSlot, 5), cache);

        Assert.assertEquals(owningSlot, cache.anchorDrawSlot[0]);
        Assert.assertNotEquals(otherSlot, cache.anchorDrawSlot[0]);
    }

    @Test
    public void sharedTileAssetIdDoesNotMergeAnchors() {
        TiledMapLayerData map = map(4, 4, 300);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 101);
        int slot0 = map.slotForTile(0, 0);
        int slot1 = map.slotForTile(1, 0);

        resolver.resolve(blocks(block(0, 0, 101, 1, 0, 101)),
                map,
                slotToDrawIndex(512, slot0, 3, slot1, 4),
                cache);

        Assert.assertEquals(2, cache.anchorCount);
        Assert.assertEquals(slot0, cache.anchorDrawSlot[0]);
        Assert.assertEquals(slot1, cache.anchorDrawSlot[1]);
        Assert.assertNotEquals(cache.anchorDrawSlot[0], cache.anchorDrawSlot[1]);
    }

    @Test
    public void distinctBlocksMayShareVisualAnchorCells() {
        TiledMapLayerData map = map(6, 4, 300);
        map.setTile(0, 1, 101);
        map.setTile(1, 1, 102);
        map.setTile(2, 1, 103);
        map.setTile(3, 1, 104);
        map.setTile(4, 1, 105);
        int slot0 = map.slotForTile(0, 1);
        int slot1 = map.slotForTile(1, 1);
        int sharedSlot = map.slotForTile(2, 1);
        int slot3 = map.slotForTile(3, 1);
        int slot4 = map.slotForTile(4, 1);

        resolver.resolve(blocks(
                        block(0, 1, 101, 1, 1, 102, 2, 1, 103),
                        block(2, 1, 103, 3, 1, 104, 4, 1, 105)),
                map,
                slotToDrawIndex(512, slot0, 10, slot1, 11, sharedSlot, 12, slot3, 13, slot4, 14),
                cache);

        Assert.assertEquals(2, cache.blockCount);
        Assert.assertEquals(6, cache.anchorCount);
        Assert.assertEquals(3, cache.blockAnchorCount[0]);
        Assert.assertEquals(3, cache.blockAnchorCount[1]);
        Assert.assertEquals(sharedSlot, cache.anchorDrawSlot[2]);
        Assert.assertEquals(sharedSlot, cache.anchorDrawSlot[3]);
        Assert.assertEquals(10, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(12, cache.blockAnchorEndDrawIndex[0]);
        Assert.assertEquals(12, cache.blockAnchorStartDrawIndex[1]);
        Assert.assertEquals(14, cache.blockAnchorEndDrawIndex[1]);
    }

    @Test
    public void missingDrawIndexSkipsAnchorForCurrentFrame() {
        TiledMapLayerData map = map(4, 4, 300);
        map.setTile(0, 0, 101);
        SpatialBlocksComponent blocks = blocks(block(0, 0, 101));

        resolver.resolve(blocks, map, slotToDrawIndex(512), cache);

        Assert.assertEquals(1, blocks.blocks.get(0).linkedTileRefs.size);
        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(1, cache.anchorCount);
        Assert.assertFalse(cache.hasResolvedBlock(0));
        Assert.assertEquals(-1, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(-1, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void repaintingSameCellReactivatesExistingLinkedRef() {
        TiledMapLayerData map = map(4, 4, 300);
        SpatialBlocksComponent blocks = blocks(block(0, 0, 101));

        map.setTile(0, 0, 0);
        resolver.resolve(blocks, map, slotToDrawIndex(512), cache);
        Assert.assertEquals(1, blocks.blocks.get(0).linkedTileRefs.size);
        Assert.assertFalse(cache.hasResolvedBlock(0));

        map.setTile(0, 0, 202);
        int slot = map.slotForTile(0, 0);
        resolver.resolve(blocks, map, slotToDrawIndex(512, slot, 7), cache);

        Assert.assertEquals(1, blocks.blocks.get(0).linkedTileRefs.size);
        Assert.assertEquals(101, blocks.blocks.get(0).linkedTileRefs.get(0).tileAssetId);
        Assert.assertTrue(cache.hasResolvedBlock(0));
        Assert.assertEquals(slot, cache.anchorDrawSlot[0]);
        Assert.assertEquals(7, cache.anchorDrawIndex[0]);
    }

    @Test
    public void staleTileAssetIdDoesNotPreventCellResolution() {
        TiledMapLayerData map = map(4, 4, 300);
        map.setTile(0, 0, 202);
        int slot = map.slotForTile(0, 0);

        resolver.resolve(blocks(block(0, 0, 101)), map, slotToDrawIndex(512, slot, 7), cache);

        Assert.assertTrue(cache.hasResolvedBlock(0));
        Assert.assertEquals(slot, cache.anchorDrawSlot[0]);
        Assert.assertEquals(7, cache.anchorDrawIndex[0]);
    }

    @Test
    public void resolveClearsCacheForReuse() {
        TiledMapLayerData map = map(4, 4, 300);
        map.setTile(0, 0, 101);
        map.setTile(1, 0, 102);
        map.setTile(2, 0, 103);
        int slot0 = map.slotForTile(0, 0);
        int slot1 = map.slotForTile(1, 0);
        int slot2 = map.slotForTile(2, 0);

        resolver.resolve(blocks(block(0, 0, 101, 1, 0, 102)),
                map,
                slotToDrawIndex(512, slot0, 1, slot1, 2),
                cache);
        resolver.resolve(blocks(block(2, 0, 103)), map, slotToDrawIndex(512, slot2, 9), cache);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(1, cache.anchorCount);
        Assert.assertEquals(0, cache.blockAnchorOffset[0]);
        Assert.assertEquals(1, cache.blockAnchorCount[0]);
        Assert.assertEquals(slot2, cache.anchorDrawSlot[0]);
        Assert.assertEquals(9, cache.anchorDrawIndex[0]);
    }

    private static TiledMapLayerData map(int width, int height, int startSlot) {
        TiledMapLayerData map = new TiledMapLayerData(width, height, 16, 16, Math.max(width, height));
        map.initSlotRange(startSlot, startSlot + width * height);
        assignRenderRefs(map, startSlot);
        return map;
    }

    private static void assignRenderRefs(TiledMapLayerData map, int startRef) {
        int nextRef = startRef;
        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null) continue;
                chunk.renderRefStartIndex = nextRef;
                chunk.renderRefCount = chunk.soaCount;
                nextRef += chunk.soaCount;
            }
        }
    }

    private static SpatialBlocksComponent blocks(SpatialBlockData... blocks) {
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        for (SpatialBlockData block : blocks) {
            component.blocks.add(block);
        }
        return component;
    }

    private static SpatialBlockData block(int... gxGyTileAssetIdTriples) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 10;
        block.enabled = true;
        block.beginAuthoredLinkedTileRefs();
        for (int i = 0; i < gxGyTileAssetIdTriples.length; i += 3) {
            block.addLinkedTileRef(gxGyTileAssetIdTriples[i],
                    gxGyTileAssetIdTriples[i + 1],
                    gxGyTileAssetIdTriples[i + 2]);
        }
        return block;
    }

    private static int[] slotToDrawIndex(int size, int... slotDrawPairs) {
        int[] slotToDrawIndex = new int[size];
        Arrays.fill(slotToDrawIndex, -1);
        for (int i = 0; i < slotDrawPairs.length; i += 2) {
            slotToDrawIndex[slotDrawPairs[i]] = slotDrawPairs[i + 1];
        }
        return slotToDrawIndex;
    }

    private static int[] refToDrawIndex(int size, int... refDrawPairs) {
        return slotToDrawIndex(size, refDrawPairs);
    }
}
