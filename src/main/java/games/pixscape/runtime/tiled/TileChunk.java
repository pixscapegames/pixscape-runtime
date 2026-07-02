package games.pixscape.runtime.tiled;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;

public final class TileChunk {

    public enum DirtyState {
        CLEAN,
        PARTIAL,
        FULL
    }

    public transient DirtyState dirtyState = DirtyState.FULL; // initial build
    public transient IntArray dirtyLocalIndices = new IntArray(false, 8);

    public int chunkX;
    public int chunkY;

    public int chunkWidth;
    public int chunkHeight;

    public int[] assetIds;
    public byte[] transformFlags;
    public float[] altitudes;
    public float[] heights;
    public int[] spatialFlags;
    public boolean[] spatialOverrides;

    // ------------------------------------------------------------
    // Animation state per tile instance (lazy)
    // ------------------------------------------------------------

    /**
     * NONE / PLAYING / PAUSED
     * Null while no cell in this chunk uses animation state.
     */
    public byte[] animPlaybackState;

    /**
     * Current frame index per cell.
     */
    public short[] animFrameIndex;

    /**
     * Elapsed time in the current frame.
     */
    public int[] animFrameElapsedMs;

    /**
     * LOOPING / PLAY_ONCE. Null while no cell in this chunk uses animation state.
     */
    public byte[] animPlaybackMode;

    /**
     * True for one-shot cells that reached their terminal state.
     */
    public boolean[] animFinished;

    /**
     * One-shot terminal behavior: hold final frame or return to normal stop semantics.
     */
    public boolean[] animHoldLastFrame;

    /**
     * Local indices of this chunk's animated cells.
     * Avoids scanning the whole chunk in the future TiledAnimationSystem.
     */
    public IntArray animatedLocalIndices;

    /**
     * Avoids duplicates in animatedLocalIndices.
     */
    private transient boolean[] animatedMembership;

    public int renderRefStartIndex = -1;
    public int renderRefCount;

    public boolean contentDirty = true;
    public boolean collisionDirty = true;
    public transient boolean visibleLastFrame = false;

    public transient Rectangle bounds;

    public TileChunk() {
        this.bounds = new Rectangle();
    }

    public TileChunk(int chunkX,
                     int chunkY,
                     int chunkWidth,
                     int chunkHeight) {

        this.chunkX = chunkX;
        this.chunkY = chunkY;

        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;

        int cellCount = chunkWidth * chunkHeight;

        this.assetIds = new int[cellCount];
        this.transformFlags = new byte[cellCount];

        this.bounds = new Rectangle();
    }

    public int get(int localX, int localY) {
        return assetIds[localY * chunkWidth + localX];
    }

    public byte getTransformFlags(int localX, int localY) {
        return transformFlags[localY * chunkWidth + localX];
    }

    public float getAltitude(int localX, int localY) {
        int index = localY * chunkWidth + localX;
        return altitudes != null ? altitudes[index] : 0f;
    }

    public float getHeight(int localX, int localY) {
        int index = localY * chunkWidth + localX;
        return heights != null ? heights[index] : 0f;
    }

    public int getSpatialFlags(int localX, int localY) {
        int index = localY * chunkWidth + localX;
        return spatialFlags != null ? spatialFlags[index] : 0;
    }

    public boolean hasSpatialOverride(int localX, int localY) {
        int index = localY * chunkWidth + localX;
        return spatialOverrides != null && spatialOverrides[index];
    }

    public boolean hasSpatialOverride(int localIndex) {
        return spatialOverrides != null
                && localIndex >= 0
                && localIndex < cellCount()
                && spatialOverrides[localIndex];
    }

    public void set(int localX, int localY, int assetId) {
        set(localX, localY, assetId, TileTransformFlags.NONE);
    }

    public void set(int localX, int localY, int assetId, byte flags) {
        if (localX < 0 || localY < 0 ||
                localX >= chunkWidth || localY >= chunkHeight) {
            return;
        }

        int index = localY * chunkWidth + localX;
        byte sanitizedFlags = TileTransformFlags.sanitize(flags);

        int previousAssetId = assetIds[index];
        byte previousFlags = transformFlags[index];

        if (previousAssetId == assetId && previousFlags == sanitizedFlags) {
            return;
        }

        assetIds[index] = assetId;
        transformFlags[index] = sanitizedFlags;

        // If the asset changes, the previous animation state is no longer valid.
        // Reset it without marking dirty again here, since set() already dirties the cell.
        if (previousAssetId != assetId) {
            clearAnimationStateInternal(index);
        }

        contentDirty = true;

        if (dirtyState != DirtyState.FULL) {
            dirtyState = DirtyState.PARTIAL;
            dirtyLocalIndices.add(index);
        }

        collisionDirty = true;
    }

    public void setSpatial(int localX, int localY, float altitude, float height, int flags) {
        setSpatialInternal(localX, localY, altitude, height, flags, false);
    }

    public void setSpatialOverride(int localX, int localY, float altitude, float height, int flags) {
        setSpatialInternal(localX, localY, altitude, height, flags, true);
    }

    public void clearSpatialOverride(int localX, int localY) {
        if (localX < 0 || localY < 0 ||
                localX >= chunkWidth || localY >= chunkHeight ||
                spatialOverrides == null) {
            return;
        }

        int index = localY * chunkWidth + localX;
        if (!spatialOverrides[index]) return;

        spatialOverrides[index] = false;
        if (altitudes != null) altitudes[index] = 0f;
        if (heights != null) heights[index] = 0f;
        if (spatialFlags != null) spatialFlags[index] = 0;
        collisionDirty = true;
    }

    private void setSpatialInternal(int localX,
                                    int localY,
                                    float altitude,
                                    float height,
                                    int flags,
                                    boolean explicitOverride) {
        if (localX < 0 || localY < 0 ||
                localX >= chunkWidth || localY >= chunkHeight) {
            return;
        }

        int index = localY * chunkWidth + localX;

        if (altitude == 0f && height == 0f && flags == 0
                && !explicitOverride
                && altitudes == null && heights == null && spatialFlags == null && spatialOverrides == null) {
            return;
        }

        ensureSpatialStorage();

        if (altitudes[index] == altitude
                && heights[index] == height
                && spatialFlags[index] == flags
                && spatialOverrides[index] == explicitOverride) {
            return;
        }

        altitudes[index] = altitude;
        heights[index] = height;
        spatialFlags[index] = flags;
        spatialOverrides[index] = explicitOverride;
        collisionDirty = true;
    }

    public int renderRefFor(int localX, int localY) {
        int localIndex = localY * chunkWidth + localX;
        if (renderRefStartIndex < 0 || localIndex < 0 || localIndex >= renderRefCount) {
            return -1;
        }
        return renderRefStartIndex + localIndex;
    }

    public int localIndexFor(int localX, int localY) {
        return localY * chunkWidth + localX;
    }

    public int cellCount() {
        return assetIds != null ? assetIds.length : chunkWidth * chunkHeight;
    }

    public void markLocalDirty(int localIndex) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        contentDirty = true;

        if (dirtyState != DirtyState.FULL) {
            dirtyState = DirtyState.PARTIAL;
            dirtyLocalIndices.add(localIndex);
        }
    }

    // ============================================================
    // Animation state
    // ============================================================

    public boolean hasAnimationState() {
        return animPlaybackState != null;
    }

    public boolean hasAnimatedTiles() {
        return animatedLocalIndices != null && animatedLocalIndices.size > 0;
    }

    public byte getAnimPlaybackState(int localIndex) {
        if (animPlaybackState == null || localIndex < 0 || localIndex >= cellCount()) {
            return TileAnimationPlayback.NONE;
        }
        return animPlaybackState[localIndex];
    }

    public short getAnimFrameIndex(int localIndex) {
        if (animFrameIndex == null || localIndex < 0 || localIndex >= cellCount()) {
            return 0;
        }
        return animFrameIndex[localIndex];
    }

    public int getAnimFrameElapsedMs(int localIndex) {
        if (animFrameElapsedMs == null || localIndex < 0 || localIndex >= cellCount()) {
            return 0;
        }
        return animFrameElapsedMs[localIndex];
    }

    public byte getAnimPlaybackMode(int localIndex) {
        if (animPlaybackMode == null || localIndex < 0 || localIndex >= cellCount()) {
            return TileAnimationPlayback.MODE_LOOPING;
        }
        return sanitizePlaybackMode(animPlaybackMode[localIndex]);
    }

    public boolean isAnimFinished(int localIndex) {
        return animFinished != null
                && localIndex >= 0
                && localIndex < cellCount()
                && animFinished[localIndex];
    }

    public boolean isAnimHoldLastFrame(int localIndex) {
        return animHoldLastFrame != null
                && localIndex >= 0
                && localIndex < cellCount()
                && animHoldLastFrame[localIndex];
    }

    /**
     * Initializes or updates a cell's animation state.
     * Does not automatically mark the cell dirty.
     * The caller decides whether to re-render based on visible frame changes.
     */
    public void setAnimationState(int localIndex,
                                  byte playbackState,
                                  int frameIndex,
                                  int frameElapsedMs) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        byte safePlayback = sanitizePlaybackState(playbackState);

        if (safePlayback == TileAnimationPlayback.NONE) {
            clearAnimationStateInternal(localIndex);
            return;
        }

        ensureAnimationStorage();

        animPlaybackState[localIndex] = safePlayback;
        animPlaybackMode[localIndex] = TileAnimationPlayback.MODE_LOOPING;
        animFrameIndex[localIndex] = (short) Math.max(0, frameIndex);
        animFrameElapsedMs[localIndex] = Math.max(0, frameElapsedMs);
        animFinished[localIndex] = false;
        animHoldLastFrame[localIndex] = false;

        if (!animatedMembership[localIndex]) {
            animatedMembership[localIndex] = true;
            animatedLocalIndices.add(localIndex);
        }
    }

    public void setAnimationState(int localIndex,
                                  byte playbackState,
                                  byte playbackMode,
                                  boolean finished,
                                  boolean holdLastFrame,
                                  int frameIndex,
                                  int frameElapsedMs) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        ensureAnimationStorage();

        byte safePlayback = sanitizePlaybackState(playbackState);
        byte safeMode = sanitizePlaybackMode(playbackMode);

        animPlaybackState[localIndex] = safePlayback;
        animPlaybackMode[localIndex] = safeMode;
        animFrameIndex[localIndex] = (short) Math.max(0, frameIndex);
        animFrameElapsedMs[localIndex] = Math.max(0, frameElapsedMs);
        animFinished[localIndex] = finished;
        animHoldLastFrame[localIndex] = safeMode == TileAnimationPlayback.MODE_PLAY_ONCE && holdLastFrame;

        if (safePlayback == TileAnimationPlayback.PLAYING) {
            ensureAnimatedMembership(localIndex);
        } else if (safePlayback == TileAnimationPlayback.PAUSED && !finished) {
            ensureAnimatedMembership(localIndex);
        } else if (safePlayback == TileAnimationPlayback.PAUSED && animHoldLastFrame[localIndex]) {
            ensureAnimatedMembership(localIndex);
        } else {
            removeAnimatedMembership(localIndex);
        }
    }

    public void clearAnimationState(int localIndex) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }
        clearAnimationStateInternal(localIndex);
    }

    public void setAnimationPlaybackState(int localIndex, byte playbackState) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        byte safePlayback = sanitizePlaybackState(playbackState);

        if (safePlayback == TileAnimationPlayback.NONE) {
            clearAnimationStateInternal(localIndex);
            return;
        }

        ensureAnimationStorage();

        animPlaybackState[localIndex] = safePlayback;
        animFinished[localIndex] = false;

        ensureAnimatedMembership(localIndex);
    }

    public void setAnimationPlaybackMode(int localIndex, byte playbackMode, boolean holdLastFrame) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        ensureAnimationStorage();
        byte safeMode = sanitizePlaybackMode(playbackMode);
        animPlaybackMode[localIndex] = safeMode;
        animHoldLastFrame[localIndex] = safeMode == TileAnimationPlayback.MODE_PLAY_ONCE && holdLastFrame;
        animFinished[localIndex] = false;
    }

    public void setAnimationFrameIndex(int localIndex, int frameIndex) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        ensureAnimationStorage();
        animFrameIndex[localIndex] = (short) Math.max(0, frameIndex);

        ensureAnimatedMembership(localIndex);
    }

    public void setAnimationFrameElapsedMs(int localIndex, int frameElapsedMs) {
        if (localIndex < 0 || localIndex >= cellCount()) {
            return;
        }

        ensureAnimationStorage();
        animFrameElapsedMs[localIndex] = Math.max(0, frameElapsedMs);

        ensureAnimatedMembership(localIndex);
    }

    public void advanceAnimationElapsedMs(int localIndex, int deltaMs) {
        if (animFrameElapsedMs == null || localIndex < 0 || localIndex >= cellCount() || deltaMs <= 0) {
            return;
        }
        animFrameElapsedMs[localIndex] += deltaMs;
    }

    private void ensureAnimationStorage() {
        if (animPlaybackState == null) {
            animPlaybackState = new byte[cellCount()];
        }
        if (animFrameIndex == null) {
            animFrameIndex = new short[cellCount()];
        }
        if (animFrameElapsedMs == null) {
            animFrameElapsedMs = new int[cellCount()];
        }
        if (animPlaybackMode == null) {
            animPlaybackMode = new byte[cellCount()];
        }
        if (animFinished == null) {
            animFinished = new boolean[cellCount()];
        }
        if (animHoldLastFrame == null) {
            animHoldLastFrame = new boolean[cellCount()];
        }
        if (animatedMembership == null) {
            animatedMembership = new boolean[cellCount()];
            if (animatedLocalIndices != null) {
                for (int i = 0; i < animatedLocalIndices.size; i++) {
                    int index = animatedLocalIndices.get(i);
                    if (index >= 0 && index < cellCount()) {
                        animatedMembership[index] = true;
                    }
                }
            }
        }
        if (animatedLocalIndices != null) {
            return;
        }

        animatedLocalIndices = new IntArray(false, 8);
    }

    private void ensureSpatialStorage() {
        if (altitudes != null && heights != null && spatialFlags != null && spatialOverrides != null) {
            return;
        }

        if (altitudes == null) altitudes = new float[cellCount()];
        if (heights == null) heights = new float[cellCount()];
        if (spatialFlags == null) spatialFlags = new int[cellCount()];
        if (spatialOverrides == null) spatialOverrides = new boolean[cellCount()];
    }

    private void clearAnimationStateInternal(int localIndex) {
        if (animPlaybackState == null || localIndex < 0 || localIndex >= cellCount()) {
            return;
        }
        ensureAnimationStorage();

        animPlaybackState[localIndex] = TileAnimationPlayback.NONE;
        animPlaybackMode[localIndex] = TileAnimationPlayback.MODE_LOOPING;
        animFrameIndex[localIndex] = 0;
        animFrameElapsedMs[localIndex] = 0;
        animFinished[localIndex] = false;
        animHoldLastFrame[localIndex] = false;

        removeAnimatedMembership(localIndex);
    }

    private void ensureAnimatedMembership(int localIndex) {
        if (!animatedMembership[localIndex]) {
            animatedMembership[localIndex] = true;
            animatedLocalIndices.add(localIndex);
        }
    }

    private void removeAnimatedMembership(int localIndex) {
        if (animatedMembership == null || animatedLocalIndices == null || !animatedMembership[localIndex]) {
            return;
        }

        animatedMembership[localIndex] = false;

        for (int i = 0; i < animatedLocalIndices.size; i++) {
            if (animatedLocalIndices.get(i) == localIndex) {
                animatedLocalIndices.removeIndex(i);
                break;
            }
        }
    }

    private static byte sanitizePlaybackState(byte playbackState) {
        switch (playbackState) {
            case TileAnimationPlayback.PLAYING:
                return TileAnimationPlayback.PLAYING;
            case TileAnimationPlayback.PAUSED:
                return TileAnimationPlayback.PAUSED;
            default:
                return TileAnimationPlayback.NONE;
        }
    }

    private static byte sanitizePlaybackMode(byte playbackMode) {
        switch (playbackMode) {
            case TileAnimationPlayback.MODE_PLAY_ONCE:
                return TileAnimationPlayback.MODE_PLAY_ONCE;
            default:
                return TileAnimationPlayback.MODE_LOOPING;
        }
    }
}
