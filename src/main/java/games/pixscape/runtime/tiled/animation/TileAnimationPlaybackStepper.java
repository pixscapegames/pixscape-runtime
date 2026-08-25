package games.pixscape.runtime.tiled.animation;

/**
 * Allocation-free state transition helper shared by Tiled animation playback systems.
 *
 * <p>The caller owns and reuses a {@link Result}. Chunk iteration, ECS access, visual resolution,
 * and dirty propagation deliberately remain outside this helper.</p>
 */
public final class TileAnimationPlaybackStepper {

    private TileAnimationPlaybackStepper() {
    }

    public static void advance(TileAnimationDef def,
                               byte playbackState,
                               byte playbackMode,
                               boolean finished,
                               boolean holdLastFrame,
                               int frameIndex,
                               int frameElapsedMs,
                               int deltaMs,
                               Result out) {
        if (def == null) throw new IllegalArgumentException("def must not be null");
        if (out == null) throw new IllegalArgumentException("out must not be null");

        int frameCount = def.frameCount();
        int safeFrameIndex = TileAnimationResolver.clampFrameIndex(frameIndex, frameCount);
        int safeElapsedMs = Math.max(0, frameElapsedMs);

        out.playbackState = playbackState;
        out.playbackMode = playbackMode;
        out.finished = finished;
        out.holdLastFrame = holdLastFrame;
        out.frameIndex = safeFrameIndex;
        out.frameElapsedMs = safeElapsedMs;

        if (playbackState != TileAnimationPlayback.PLAYING || deltaMs <= 0 || frameCount <= 0) {
            return;
        }

        int elapsedMs = safeElapsedMs + deltaMs;
        int newFrameIndex = safeFrameIndex;

        while (elapsedMs >= def.frameDurationMs(newFrameIndex)) {
            elapsedMs -= def.frameDurationMs(newFrameIndex);

            if (playbackMode == TileAnimationPlayback.MODE_PLAY_ONCE) {
                if (newFrameIndex >= frameCount - 1) {
                    out.playbackState = holdLastFrame
                            ? TileAnimationPlayback.PAUSED
                            : TileAnimationPlayback.NONE;
                    out.finished = true;
                    out.frameIndex = holdLastFrame ? frameCount - 1 : 0;
                    out.frameElapsedMs = 0;
                    return;
                }
                newFrameIndex++;
            } else {
                newFrameIndex = TileAnimationResolver.nextFrameIndex(newFrameIndex, frameCount);
            }
        }

        out.finished = false;
        out.frameIndex = newFrameIndex;
        out.frameElapsedMs = elapsedMs;
    }

    /** Mutable caller-owned transition result, reused to avoid hot-path allocation. */
    public static final class Result {
        public byte playbackState;
        public byte playbackMode;
        public boolean finished;
        public boolean holdLastFrame;
        public int frameIndex;
        public int frameElapsedMs;
    }
}
