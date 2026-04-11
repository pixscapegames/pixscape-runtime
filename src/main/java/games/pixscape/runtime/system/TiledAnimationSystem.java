package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;

@All({LayerComponent.class, TiledLayerComponent.class})
public final class TiledAnimationSystem extends IteratingSystem {

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TiledLayerComponent> mTiled;

    private TileAnimationLookup tileAnimationLookup;

    /**
     * If true, only tiles in chunks visible during the previous frame are advanced.
     * This is usually the best trade-off for runtime performance.
     */
    private boolean advanceOnlyVisibleChunks = true;

    /**
     * Fractional milliseconds carried across frames to avoid time drift.
     */
    private float deltaRemainderMs = 0f;

    /**
     * Whole milliseconds to advance during the current world step.
     */
    private int frameDeltaMs = 0;

    public TiledAnimationSystem() {
        this(null);
    }

    public TiledAnimationSystem(TileAnimationLookup tileAnimationLookup) {
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : assetId -> null;
    }

    public void setAnimatedTileLookup(TileAnimationLookup tileAnimationLookup) {
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : assetId -> null;
    }

    public void setAdvanceOnlyVisibleChunks(boolean advanceOnlyVisibleChunks) {
        this.advanceOnlyVisibleChunks = advanceOnlyVisibleChunks;
    }

    public boolean isAdvanceOnlyVisibleChunks() {
        return advanceOnlyVisibleChunks;
    }

    @Override
    protected void begin() {
        float deltaMs = world.getDelta() * 1000f + deltaRemainderMs;
        frameDeltaMs = (int) deltaMs;
        deltaRemainderMs = deltaMs - frameDeltaMs;
    }

    @Override
    protected void process(int e) {
        LayerComponent layer = mLayer.get(e);
        if (layer.type != LayerComponent.TYPE_TILED) return;

        TiledLayerComponent tiled = mTiled.get(e);
        if (tiled == null || tiled.data == null) return;

        TiledMapLayerData map = tiled.data;
        if (!map.visible) return;
        if (frameDeltaMs <= 0) return;

        IntMap.Values<TileChunk> values = map.getChunks();
        while (values.hasNext()) {
            TileChunk chunk = values.next();
            if (chunk == null || !chunk.hasAnimatedTiles()) {
                continue;
            }

            if (advanceOnlyVisibleChunks && !chunk.visibleLastFrame) {
                continue;
            }

            advanceChunkAnimations(chunk);
        }
    }

    private void advanceChunkAnimations(TileChunk chunk) {
        if (chunk.animatedLocalIndices == null || chunk.animatedLocalIndices.size == 0) {
            return;
        }

        /*
         * Iterate backwards because a tile may be removed from animatedLocalIndices
         * while processing.
         */
        for (int i = chunk.animatedLocalIndices.size - 1; i >= 0; i--) {
            int localIndex = chunk.animatedLocalIndices.get(i);
            advanceTileAnimation(chunk, localIndex);
        }
    }

    private void advanceTileAnimation(TileChunk chunk, int localIndex) {
        int assetId = chunk.assetIds[localIndex];
        if (assetId <= 0) {
            chunk.clearAnimationState(localIndex);
            return;
        }

        TileAnimationDef def = tileAnimationLookup.get(assetId);
        if (def == null || def.frameCount() <= 1) {
            chunk.clearAnimationState(localIndex);
            return;
        }

        byte playbackState = chunk.getAnimPlaybackState(localIndex);
        if (playbackState == TileAnimationPlayback.NONE) {
            chunk.clearAnimationState(localIndex);
            return;
        }

        int frameCount = def.frameCount();

        int currentFrameIndex = TileAnimationResolver.clampFrameIndex(
                chunk.getAnimFrameIndex(localIndex),
                frameCount
        );

        int currentVisualAssetId = TileAnimationResolver.resolveVisualAssetId(
                assetId,
                currentFrameIndex,
                tileAnimationLookup
        );

        /*
         * PAUSED instances keep their current frame and do not consume elapsed time.
         * We still normalize the frame index so the instance remains valid if the
         * animation definition changed.
         */
        if (playbackState == TileAnimationPlayback.PAUSED) {
            if (chunk.getAnimFrameIndex(localIndex) != currentFrameIndex) {
                chunk.animFrameIndex[localIndex] = (short) currentFrameIndex;
                int normalizedVisualAssetId = TileAnimationResolver.resolveVisualAssetId(
                        assetId,
                        currentFrameIndex,
                        tileAnimationLookup
                );
                if (normalizedVisualAssetId != currentVisualAssetId) {
                    chunk.markLocalDirty(localIndex);
                }
            }
            return;
        }

        if (playbackState != TileAnimationPlayback.PLAYING) {
            return;
        }

        int elapsedMs = chunk.getAnimFrameElapsedMs(localIndex) + frameDeltaMs;
        int newFrameIndex = currentFrameIndex;

        while (true) {
            int frameDurationMs = def.frameDurationMs(newFrameIndex);
            if (frameDurationMs <= 0 || elapsedMs < frameDurationMs) {
                break;
            }

            elapsedMs -= frameDurationMs;
            newFrameIndex = TileAnimationResolver.nextFrameIndex(newFrameIndex, frameCount);
        }

        if (newFrameIndex != currentFrameIndex) {
            int newVisualAssetId = TileAnimationResolver.resolveVisualAssetId(
                    assetId,
                    newFrameIndex,
                    tileAnimationLookup
            );

            if (newVisualAssetId != currentVisualAssetId) {
                chunk.markLocalDirty(localIndex);
            }
        }

        chunk.animFrameIndex[localIndex] = (short) newFrameIndex;
        chunk.animFrameElapsedMs[localIndex] = elapsedMs;
    }
}