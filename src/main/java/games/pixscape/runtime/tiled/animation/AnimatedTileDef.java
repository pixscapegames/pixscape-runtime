package games.pixscape.runtime.tiled.animation;

import java.util.Arrays;

public final class AnimatedTileDef {

    private final int ownerAssetId;
    private final int[] frameAssetIds;
    private final int[] frameDurationsMs;
    private final int totalDurationMs;

    public AnimatedTileDef(int ownerAssetId, int[] frameAssetIds, int[] frameDurationsMs) {
        if (ownerAssetId <= 0) {
            throw new IllegalArgumentException("ownerAssetId must be > 0");
        }
        if (frameAssetIds == null || frameDurationsMs == null) {
            throw new IllegalArgumentException("frameAssetIds and durationsMs must not be null");
        }
        if (frameAssetIds.length == 0) {
            throw new IllegalArgumentException("Animated tile must contain at least one frame");
        }
        if (frameAssetIds.length != frameDurationsMs.length) {
            throw new IllegalArgumentException("frameAssetIds and durationsMs must have the same length");
        }

        int total = 0;

        for (int i = 0; i < frameAssetIds.length; i++) {
            int frameAssetId = frameAssetIds[i];
            int durationMs = frameDurationsMs[i];

            if (frameAssetId <= 0) {
                throw new IllegalArgumentException("frameAssetIds[" + i + "] must be > 0");
            }
            if (durationMs <= 0) {
                throw new IllegalArgumentException("durationsMs[" + i + "] must be > 0");
            }

            total += durationMs;
            if (total < 0) {
                throw new IllegalArgumentException("total animation duration overflow");
            }
        }

        this.ownerAssetId = ownerAssetId;
        this.frameAssetIds = Arrays.copyOf(frameAssetIds, frameAssetIds.length);
        this.frameDurationsMs = Arrays.copyOf(frameDurationsMs, frameDurationsMs.length);
        this.totalDurationMs = total;
    }

    public int ownerAssetId() {
        return ownerAssetId;
    }

    public int frameCount() {
        return frameAssetIds.length;
    }

    public int frameAssetId(int index) {
        return frameAssetIds[index];
    }

    public int frameDurationMs(int index) {
        return frameDurationsMs[index];
    }

    public int[] frameAssetIds() {
        return Arrays.copyOf(frameAssetIds, frameAssetIds.length);
    }

    public int[] durationsMs() {
        return Arrays.copyOf(frameDurationsMs, frameDurationsMs.length);
    }

    public int totalDurationMs() {
        return totalDurationMs;
    }

    public boolean isSingleFrame() {
        return frameAssetIds.length == 1;
    }
}