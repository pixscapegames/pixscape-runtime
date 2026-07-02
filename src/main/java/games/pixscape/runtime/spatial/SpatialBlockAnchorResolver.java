package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialBlockAnchorResolver {
    public void resolve(SpatialBlocksComponent blocks,
                        TiledMapLayerData tiledLayer,
                        int[] tiledRefToDrawIndex,
                        SpatialBlocksRuntimeCache cache) {
        resolve(blocks, tiledLayer, tiledRefToDrawIndex, cache, null);
    }

    public void resolve(SpatialBlocksComponent blocks,
                        TiledMapLayerData tiledLayer,
                        int[] tiledRefToDrawIndex,
                        SpatialBlocksRuntimeCache cache,
                        SpatialTiledSort.Context spatialSort) {
        if (cache == null) {
            throw new IllegalArgumentException("Spatial block runtime cache is required.");
        }
        cache.clear();

        if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) return;
        if (tiledLayer == null) {
            throw new IllegalArgumentException("Owning tiled layer runtime data is required.");
        }
        if (tiledRefToDrawIndex == null) {
            throw new IllegalArgumentException("tiledRefToDrawIndex is required.");
        }

        for (int blockIndex = 0, n = blocks.blocks.size; blockIndex < n; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null) {
                throw new IllegalStateException("Spatial block is null at index " + blockIndex);
            }
            if (!block.enabled) continue;
            resolveBlock(block, blockIndex, tiledLayer, tiledRefToDrawIndex, cache, spatialSort);
        }
    }

    private void resolveBlock(SpatialBlockData block,
                              int authoredBlockIndex,
                              TiledMapLayerData tiledLayer,
                              int[] tiledRefToDrawIndex,
                              SpatialBlocksRuntimeCache cache,
                              SpatialTiledSort.Context spatialSort) {
        if (!SpatialV2Rule.hasValidAuthoredTileRefs(block)) {
            throw new IllegalStateException("Spatial block V2 anchors require valid authored linked tile refs: blockIndex="
                    + authoredBlockIndex);
        }

        int anchorCount = block.linkedTileRefs.size;
        int cacheBlock = cache.addBlock(anchorCount);
        for (int anchor = 0; anchor < anchorCount; anchor++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(anchor);
            if (ref == null) continue;
            if (spatialSort != null && spatialSort.applies() && spatialSort.isShared(ref.gx, ref.gy)) {
                continue;
            }
            int tiledRenderRef = tiledLayer.tiledRenderRefForTile(ref.gx, ref.gy);
            if (tiledRenderRef < 0 || tiledRenderRef >= tiledRefToDrawIndex.length) continue;

            int drawIndex = tiledRefToDrawIndex[tiledRenderRef];
            if (drawIndex < 0) continue;

            cache.setAnchor(cacheBlock, anchor, tiledRenderRef, drawIndex);
        }
        cache.finalizeBlockRange(cacheBlock);
    }

}
