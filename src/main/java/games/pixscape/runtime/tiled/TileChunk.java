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

    public DirtyState dirtyState = DirtyState.FULL; // initial build
    public IntArray dirtyLocalIndices = new IntArray(false, 8);

    public int chunkX;
    public int chunkY;

    public int chunkWidth;
    public int chunkHeight;

    public int[] assetIds;
    public byte[] transformFlags;

    // ------------------------------------------------------------
    // Animation state per tile instance (lazy)
    // ------------------------------------------------------------

    /**
     * NONE / PLAYING / PAUSED
     * Null tant qu'aucune cellule de ce chunk n'utilise d'état d'animation.
     */
    public byte[] animPlaybackState;

    /**
     * Index de frame courant par cellule.
     */
    public short[] animFrameIndex;

    /**
     * Temps écoulé dans la frame courante.
     */
    public int[] animFrameElapsedMs;

    /**
     * Indices locaux des cellules animées de ce chunk.
     * Sert à éviter de scanner tout le chunk dans le futur TiledAnimationSystem.
     */
    public IntArray animatedLocalIndices;

    /**
     * Évite les doublons dans animatedLocalIndices.
     */
    private boolean[] animatedMembership;

    public int soaStartIndex;
    public int soaCount;

    public boolean contentDirty = true;
    public boolean collisionDirty = true;
    public boolean visibleLastFrame = false;

    public Rectangle bounds;

    public TileChunk() {
        this.bounds = new Rectangle();
    }

    public TileChunk(int chunkX,
                     int chunkY,
                     int chunkWidth,
                     int chunkHeight,
                     int soaStartIndex) {

        this.chunkX = chunkX;
        this.chunkY = chunkY;

        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;

        int cellCount = chunkWidth * chunkHeight;

        this.assetIds = new int[cellCount];
        this.transformFlags = new byte[cellCount];

        this.soaStartIndex = soaStartIndex;
        this.soaCount = cellCount;

        this.bounds = new Rectangle();
    }

    public int get(int localX, int localY) {
        return assetIds[localY * chunkWidth + localX];
    }

    public byte getTransformFlags(int localX, int localY) {
        return transformFlags[localY * chunkWidth + localX];
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

        // Si l'asset change, l'état d'animation précédent n'a plus de sens.
        // On le remet à zéro sans re-dirty ici, car le set() dirty déjà la cellule.
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

    public int slotFor(int localX, int localY) {
        return soaStartIndex + (localY * chunkWidth + localX);
    }

    public int localIndexFor(int localX, int localY) {
        return localY * chunkWidth + localX;
    }

    public void markLocalDirty(int localIndex) {
        if (localIndex < 0 || localIndex >= soaCount) {
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
        if (animPlaybackState == null || localIndex < 0 || localIndex >= soaCount) {
            return TileAnimationPlayback.NONE;
        }
        return animPlaybackState[localIndex];
    }

    public short getAnimFrameIndex(int localIndex) {
        if (animFrameIndex == null || localIndex < 0 || localIndex >= soaCount) {
            return 0;
        }
        return animFrameIndex[localIndex];
    }

    public int getAnimFrameElapsedMs(int localIndex) {
        if (animFrameElapsedMs == null || localIndex < 0 || localIndex >= soaCount) {
            return 0;
        }
        return animFrameElapsedMs[localIndex];
    }

    /**
     * Initialise ou met à jour l'état d'animation d'une cellule.
     * Ne dirty pas automatiquement la cellule.
     * Le caller décide s'il faut re-rendre selon changement de frame visible.
     */
    public void setAnimationState(int localIndex,
                                  byte playbackState,
                                  int frameIndex,
                                  int frameElapsedMs) {
        if (localIndex < 0 || localIndex >= soaCount) {
            return;
        }

        byte safePlayback = sanitizePlaybackState(playbackState);

        if (safePlayback == TileAnimationPlayback.NONE) {
            clearAnimationStateInternal(localIndex);
            return;
        }

        ensureAnimationStorage();

        animPlaybackState[localIndex] = safePlayback;
        animFrameIndex[localIndex] = (short) Math.max(0, frameIndex);
        animFrameElapsedMs[localIndex] = Math.max(0, frameElapsedMs);

        if (!animatedMembership[localIndex]) {
            animatedMembership[localIndex] = true;
            animatedLocalIndices.add(localIndex);
        }
    }

    public void clearAnimationState(int localIndex) {
        if (localIndex < 0 || localIndex >= soaCount) {
            return;
        }
        clearAnimationStateInternal(localIndex);
    }

    public void setAnimationPlaybackState(int localIndex, byte playbackState) {
        if (localIndex < 0 || localIndex >= soaCount) {
            return;
        }

        byte safePlayback = sanitizePlaybackState(playbackState);

        if (safePlayback == TileAnimationPlayback.NONE) {
            clearAnimationStateInternal(localIndex);
            return;
        }

        ensureAnimationStorage();

        animPlaybackState[localIndex] = safePlayback;

        if (!animatedMembership[localIndex]) {
            animatedMembership[localIndex] = true;
            animatedLocalIndices.add(localIndex);
        }
    }

    public void setAnimationFrameIndex(int localIndex, int frameIndex) {
        if (localIndex < 0 || localIndex >= soaCount) {
            return;
        }

        ensureAnimationStorage();
        animFrameIndex[localIndex] = (short) Math.max(0, frameIndex);

        if (!animatedMembership[localIndex]) {
            animatedMembership[localIndex] = true;
            animatedLocalIndices.add(localIndex);
        }
    }

    public void setAnimationFrameElapsedMs(int localIndex, int frameElapsedMs) {
        if (localIndex < 0 || localIndex >= soaCount) {
            return;
        }

        ensureAnimationStorage();
        animFrameElapsedMs[localIndex] = Math.max(0, frameElapsedMs);

        if (!animatedMembership[localIndex]) {
            animatedMembership[localIndex] = true;
            animatedLocalIndices.add(localIndex);
        }
    }

    public void advanceAnimationElapsedMs(int localIndex, int deltaMs) {
        if (animFrameElapsedMs == null || localIndex < 0 || localIndex >= soaCount || deltaMs <= 0) {
            return;
        }
        animFrameElapsedMs[localIndex] += deltaMs;
    }

    private void ensureAnimationStorage() {
        if (animPlaybackState != null) {
            return;
        }

        animPlaybackState = new byte[soaCount];
        animFrameIndex = new short[soaCount];
        animFrameElapsedMs = new int[soaCount];
        animatedMembership = new boolean[soaCount];
        animatedLocalIndices = new IntArray(false, 8);
    }

    private void clearAnimationStateInternal(int localIndex) {
        if (animPlaybackState == null || localIndex < 0 || localIndex >= soaCount) {
            return;
        }

        animPlaybackState[localIndex] = TileAnimationPlayback.NONE;
        animFrameIndex[localIndex] = 0;
        animFrameElapsedMs[localIndex] = 0;

        if (animatedMembership[localIndex]) {
            animatedMembership[localIndex] = false;

            for (int i = 0; i < animatedLocalIndices.size; i++) {
                if (animatedLocalIndices.get(i) == localIndex) {
                    animatedLocalIndices.removeIndex(i);
                    break;
                }
            }
        }
    }

    private static byte sanitizePlaybackState(byte playbackState) {
        return switch (playbackState) {
            case TileAnimationPlayback.PLAYING -> TileAnimationPlayback.PLAYING;
            case TileAnimationPlayback.PAUSED -> TileAnimationPlayback.PAUSED;
            default -> TileAnimationPlayback.NONE;
        };
    }
}