package games.pixscape.runtime.tiled.animation;

import java.util.Arrays;

public final class TileAnimationDef {

    private final TileAnimationDefData data;
    private final int totalDurationMs;

    public TileAnimationDef(TileAnimationDefData source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (source.id <= 0) {
            throw new IllegalArgumentException("id must be > 0");
        }
        if (source.frameAssetIds == null || source.frameDurationsMs == null) {
            throw new IllegalArgumentException("frameAssetIds and frameDurationsMs must not be null");
        }
        if (source.frameAssetIds.length == 0) {
            throw new IllegalArgumentException("Animated tile must contain at least one frame");
        }
        if (source.frameAssetIds.length != source.frameDurationsMs.length) {
            throw new IllegalArgumentException("frameAssetIds and frameDurationsMs must have the same length");
        }

        int total = 0;
        for (int i = 0; i < source.frameAssetIds.length; i++) {
            int frameAssetId = source.frameAssetIds[i];
            int durationMs = source.frameDurationsMs[i];

            if (frameAssetId <= 0) {
                throw new IllegalArgumentException("frameAssetIds[" + i + "] must be > 0");
            }
            if (durationMs <= 0) {
                throw new IllegalArgumentException("frameDurationsMs[" + i + "] must be > 0");
            }

            total += durationMs;
            if (total < 0) {
                throw new IllegalArgumentException("total animation duration overflow");
            }
        }

        TileAnimationDefData copy = new TileAnimationDefData();
        copy.id = source.id;
        copy.frameAssetIds = Arrays.copyOf(source.frameAssetIds, source.frameAssetIds.length);
        copy.frameDurationsMs = Arrays.copyOf(source.frameDurationsMs, source.frameDurationsMs.length);

        this.data = copy;
        this.totalDurationMs = total;
    }

    public int id() {
        return data.id;
    }

    public int frameCount() {
        return data.frameAssetIds.length;
    }

    public int frameAssetId(int index) {
        return data.frameAssetIds[index];
    }

    public int frameDurationMs(int index) {
        return data.frameDurationsMs[index];
    }

    public int[] frameAssetIds() {
        return Arrays.copyOf(data.frameAssetIds, data.frameAssetIds.length);
    }

    public int[] frameDurationsMs() {
        return Arrays.copyOf(data.frameDurationsMs, data.frameDurationsMs.length);
    }

    public int totalDurationMs() {
        return totalDurationMs;
    }

    public boolean isSingleFrame() {
        return data.frameAssetIds.length == 1;
    }

    public TileAnimationDefData toDataCopy() {
        TileAnimationDefData out = new TileAnimationDefData();
        out.id = data.id;
        out.frameAssetIds = Arrays.copyOf(data.frameAssetIds, data.frameAssetIds.length);
        out.frameDurationsMs = Arrays.copyOf(data.frameDurationsMs, data.frameDurationsMs.length);
        return out;
    }
}