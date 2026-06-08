package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockAnchorResolver {
    public void resolve(SpatialBlocksComponent blocks,
                        TiledMapLayerData tiledLayer,
                        int[] slotToDrawIndex,
                        SpatialBlocksRuntimeCache cache) {
        if (cache == null) {
            throw new IllegalArgumentException("Spatial block runtime cache is required.");
        }
        cache.clear();

        if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) return;
        if (tiledLayer == null) {
            throw new IllegalArgumentException("Owning tiled layer runtime data is required.");
        }
        if (slotToDrawIndex == null) {
            throw new IllegalArgumentException("slotToDrawIndex is required.");
        }

        for (int blockIndex = 0, n = blocks.blocks.size; blockIndex < n; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null) {
                throw new IllegalStateException("Spatial block is null at index " + blockIndex);
            }
            if (!block.enabled) continue;
            resolveBlock(block, blockIndex, tiledLayer, slotToDrawIndex, cache);
        }
    }

    private void resolveBlock(SpatialBlockData block,
                              int authoredBlockIndex,
                              TiledMapLayerData tiledLayer,
                              int[] slotToDrawIndex,
                              SpatialBlocksRuntimeCache cache) {
        if (!SpatialBlockV1Rules.hasStraightContinuousAuthoredTileRefs(block)) {
            throw new IllegalStateException("Spatial block V1 anchors must be a straight continuous segment: blockIndex="
                    + authoredBlockIndex);
        }

        int anchorCount = block.linkedTileRefs.size;
        int cacheBlock = cache.addBlock(anchorCount);
        for (int anchor = 0; anchor < anchorCount; anchor++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(anchor);
            int slot = tiledLayer.slotForTile(ref.gx, ref.gy);
            if (slot < 0) {
                throw new IllegalStateException("Spatial block anchor has no draw slot: blockIndex="
                        + authoredBlockIndex + " gx=" + ref.gx + " gy=" + ref.gy);
            }
            if (slot >= slotToDrawIndex.length) {
                throw new IllegalStateException("Spatial block anchor slot has no draw-index entry: blockIndex="
                        + authoredBlockIndex + " slot=" + slot);
            }

            int drawIndex = slotToDrawIndex[slot];
            if (drawIndex < 0) {
                throw new IllegalStateException("Spatial block anchor is not present in draw list: blockIndex="
                        + authoredBlockIndex + " slot=" + slot);
            }

            cache.setAnchor(cacheBlock, anchor, slot, drawIndex);
        }
        cache.finalizeBlockRange(cacheBlock);
    }
}
