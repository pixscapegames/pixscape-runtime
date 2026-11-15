package games.pixscape.runtime.tiled.animation;

import games.pixscape.runtime.tiled.TileChunk;

public final class TileAnimationStateSupport {

    private TileAnimationStateSupport() {
    }

    /** Cold materialization of existing authored cells after loading or restoration. */
    public static void syncLayer(games.pixscape.runtime.component.TiledLayerComponent layer,
                                  TileAnimationLookup lookup) {
        if (layer == null || layer.data == null || lookup == null) {
            return;
        }
        layer.ensureSparseTileStorageConsistency();
        for (int i = 0; i < layer.tileAssetIds.size; i++) {
            int x = layer.tileXs.get(i), y = layer.tileYs.get(i);
            int cx = x / layer.data.chunkSize, cy = y / layer.data.chunkSize;
            TileChunk chunk = layer.data.getChunk(cx, cy);
            if (chunk != null) {
                syncWorldCell(chunk, x - cx * layer.data.chunkSize, y - cy * layer.data.chunkSize, lookup);
            }
        }
    }

    /**
     * Synchronizes the per-cell animation state with the logical asset stored in the map.
     * <p>
     * Rules:
     * - no asset            -> clear state
     * - unknown animation   -> clear state
     * - single-frame anim   -> clear state
     * - animated asset      -> ensure PLAYING state exists
     */
    public static void syncCell(TileChunk chunk,
                                int localIndex,
                                int assetId,
                                TileAnimationLookup lookup) {
        syncCell(chunk, localIndex, assetId, lookup, TileAnimationPlayback.PLAYING);
    }

    /**
     * Same as syncCell(...) but allows choosing the initial playback state.
     * Useful if one day you want freshly restored cells to start paused.
     */
    public static void syncCell(TileChunk chunk,
                                int localIndex,
                                int assetId,
                                TileAnimationLookup lookup,
                                byte initialPlaybackState) {

        if (chunk == null) {
            return;
        }

        if (localIndex < 0 || localIndex >= chunk.cellCount()) {
            return;
        }

        if (assetId <= 0 || lookup == null) {
            chunk.clearAnimationState(localIndex);
            return;
        }

        TileAnimationDef def = lookup.get(assetId);
        if (def == null || def.frameCount() <= 1) {
            chunk.clearAnimationState(localIndex);
            return;
        }

        byte playbackState = sanitizeInitialPlayback(initialPlaybackState);

        int frameCount = def.frameCount();
        int currentFrameIndex = TileAnimationResolver.clampFrameIndex(
                chunk.getAnimFrameIndex(localIndex),
                frameCount
        );
        int currentElapsedMs = Math.max(0, chunk.getAnimFrameElapsedMs(localIndex));

        if (!TileAnimationPlayback.isAnimated(chunk.getAnimPlaybackState(localIndex))) {
            currentFrameIndex = 0;
            currentElapsedMs = 0;
        }

        chunk.setAnimationState(
                localIndex,
                playbackState,
                currentFrameIndex,
                currentElapsedMs
        );
    }

    /**
     * Clears animation state for every cell in the chunk that no longer matches
     * an animated tile definition, and ensures valid state for cells that do.
     */
    public static void syncChunk(TileChunk chunk,
                                 TileAnimationLookup lookup) {
        if (chunk == null || chunk.assetIds == null) {
            return;
        }

        for (int localIndex = 0, n = chunk.cellCount(); localIndex < n; localIndex++) {
            syncCell(chunk, localIndex, chunk.assetIds[localIndex], lookup);
        }
    }

    private static byte sanitizeInitialPlayback(byte playbackState) {
        switch (playbackState) {
            case TileAnimationPlayback.PLAYING:
                return TileAnimationPlayback.PLAYING;
            case TileAnimationPlayback.PAUSED:
                return TileAnimationPlayback.PAUSED;
            default:
                return TileAnimationPlayback.PLAYING;
        }
    }

    public static void syncWorldCell(TileChunk chunk,
                                     int localX,
                                     int localY,
                                     TileAnimationLookup lookup) {
        if (chunk == null) return;
        if (localX < 0 || localY < 0 || localX >= chunk.chunkWidth || localY >= chunk.chunkHeight) return;

        int localIndex = chunk.localIndexFor(localX, localY);
        syncCell(chunk, localIndex, chunk.assetIds[localIndex], lookup);
    }
}
