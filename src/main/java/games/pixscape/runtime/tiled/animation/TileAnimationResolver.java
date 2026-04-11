package games.pixscape.runtime.tiled.animation;

public final class TileAnimationResolver {

    private TileAnimationResolver() {
    }

    /**
     *
     * Resolves the visual asset to display for a cell
     *
     * @param assetId    logical asset stored in the map
     * @param frameIndex current frame index for this instance
     * @param lookup     animation definitions lookup
     * @return
     * the visual asset to be rendered
     */
    public static int resolveVisualAssetId(int assetId,
                                           int frameIndex,
                                           TileAnimationLookup lookup) {
        if (assetId <= 0 || lookup == null) {
            return assetId;
        }

        TileAnimationDef def = lookup.get(assetId);
        if (def == null) {
            return assetId;
        }

        int frameCount = def.frameCount();
        if (frameCount <= 0) {
            return assetId;
        }

        int safeFrameIndex = clampFrameIndex(frameIndex, frameCount);
        return def.frameAssetId(safeFrameIndex);
    }

    /**
     * Returns true if the asset matches a known animated tile.
     */
    public static boolean isAnimated(int assetId, TileAnimationLookup lookup) {
        return assetId > 0 && lookup != null && lookup.get(assetId) != null;
    }

    /**
     * Number of frames for this animated asset, 0 if not animated.
     */
    public static int frameCount(int assetId, TileAnimationLookup lookup) {
        if (assetId <= 0 || lookup == null) {
            return 0;
        }

        TileAnimationDef def = lookup.get(assetId);
        return def != null ? def.frameCount() : 0;
    }

    /**
     * Duration of the current frame, or 0 if asset is not animated / invalid index.
     */
    public static int frameDurationMs(int assetId,
                                      int frameIndex,
                                      TileAnimationLookup lookup) {
        if (assetId <= 0 || lookup == null) {
            return 0;
        }

        TileAnimationDef def = lookup.get(assetId);
        if (def == null || def.frameCount() <= 0) {
            return 0;
        }

        int safeFrameIndex = clampFrameIndex(frameIndex, def.frameCount());
        return def.frameDurationMs(safeFrameIndex);
    }

    /**
     * Bounds a frameIndex to the valid interval.
     */
    public static int clampFrameIndex(int frameIndex, int frameCount) {
        if (frameCount <= 0) {
            return 0;
        }
        if (frameIndex < 0) {
            return 0;
        }
        if (frameIndex >= frameCount) {
            return frameCount - 1;
        }
        return frameIndex;
    }

    /**
     * Move to the next frame with a loop.
     */
    public static int nextFrameIndex(int frameIndex, int frameCount) {
        if (frameCount <= 1) {
            return 0;
        }

        int safeFrameIndex = clampFrameIndex(frameIndex, frameCount);
        int next = safeFrameIndex + 1;
        return next >= frameCount ? 0 : next;
    }

    /**
     * Goes to the previous frame with a loop.
     */
    public static int previousFrameIndex(int frameIndex, int frameCount) {
        if (frameCount <= 1) {
            return 0;
        }

        int safeFrameIndex = clampFrameIndex(frameIndex, frameCount);
        return safeFrameIndex == 0 ? frameCount - 1 : safeFrameIndex - 1;
    }
}