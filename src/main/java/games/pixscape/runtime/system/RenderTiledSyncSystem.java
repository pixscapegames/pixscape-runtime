package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;


public final class RenderTiledSyncSystem extends IteratingSystem {

    private ComponentMapper<LayerComponent>      mLayer;
    private ComponentMapper<TiledLayerComponent> mTiled;

    private final OrthographicCamera  camera;
    private final RenderStateSOA      state;
    private final AtlasRuntimeService atlasRuntimeService;
    private final int                 defaultShaderIdx;
    private TileAnimationLookup       tileAnimationLookup;

    private final Rectangle viewBounds = new Rectangle();
    private final float[]   tmpQuad = new float[8];
    private final int[]     tmpWindow = new int[4];
    private int             testedChunkCount;
    private int             visibleChunkCount;
    private int             shownChunkCount;
    private int             hiddenChunkCount;
    private int             dirtyFullChunkCount;
    private int             dirtyPartialChunkCount;

    public RenderTiledSyncSystem(OrthographicCamera camera,
                                 RenderStateSOA state,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx,
                                 int tiledStart,
                                 int tiledEnd) {
        this(
                camera,
                state,
                atlasRuntimeService,
                defaultShaderIdx,
                tiledStart,
                tiledEnd,
                null
        );
    }

    public RenderTiledSyncSystem(OrthographicCamera camera,
                                 RenderStateSOA state,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx,
                                 int tiledStart,
                                 int tiledEnd,
                                 TileAnimationLookup tileAnimationLookup) {
        super(Aspect.all(LayerComponent.class, TiledLayerComponent.class));
        this.camera = camera;
        this.state = state;
        this.atlasRuntimeService = atlasRuntimeService;
        this.defaultShaderIdx = defaultShaderIdx;
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : assetId -> null;
    }

    @Override
    protected void initialize() {
        mLayer = world.getMapper(LayerComponent.class);
        mTiled = world.getMapper(TiledLayerComponent.class);
    }

    @Override
    protected void begin() {
        computeViewBounds();
        state.clearTiledVisibleSlots();
        testedChunkCount = 0;
        visibleChunkCount = 0;
        shownChunkCount = 0;
        hiddenChunkCount = 0;
        dirtyFullChunkCount = 0;
        dirtyPartialChunkCount = 0;
    }

    @Override
    protected void process(int e) {
        LayerComponent layer = mLayer.get(e);
        if (layer.type != LayerComponent.TYPE_TILED) return;

        TiledLayerComponent tiled = mTiled.get(e);
        if (tiled == null || tiled.data == null) return;

        TiledMapLayerData map = tiled.data;
        if (!map.visible) return;
        computeChunkWindow(map, tmpWindow);
        int currentMinCx = tmpWindow[0];
        int currentMaxCx = tmpWindow[1];
        int currentMinCy = tmpWindow[2];
        int currentMaxCy = tmpWindow[3];

        hideChunksOutsideCurrentWindow(map, currentMinCx, currentMaxCx, currentMinCy, currentMaxCy);

        for (int cy = currentMinCy; cy <= currentMaxCy; cy++) {
            for (int cx = currentMinCx; cx <= currentMaxCx; cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null) continue;

                testedChunkCount++;

                // Final safety overlap check remains required (especially for ISO).
                boolean inView = viewBounds.overlaps(chunk.bounds);
                if (!inView) {
                    if (chunk.visibleLastFrame) {
                        hideChunkSlots(chunk);
                        chunk.visibleLastFrame = false;
                        hiddenChunkCount++;
                    }
                    continue;
                }

                visibleChunkCount++;

                if (!chunk.visibleLastFrame) {
                    shownChunkCount++;
                    if (chunk.dirtyState != TileChunk.DirtyState.FULL) {
                        reactivateChunkSlots(chunk);
                    }
                }
                chunk.visibleLastFrame = true;
                state.appendTiledVisibleRange(chunk.soaStartIndex, chunk.soaCount);

                if (chunk.dirtyState == TileChunk.DirtyState.FULL) {
                    dirtyFullChunkCount++;
                    rebuildChunk(chunk, map, e, tiled.atlasTag);
                } else if (chunk.dirtyState == TileChunk.DirtyState.PARTIAL) {
                    dirtyPartialChunkCount++;
                    updatePartialChunk(chunk, map, e, tiled.atlasTag);
                }

                chunk.dirtyState = TileChunk.DirtyState.CLEAN;
                chunk.dirtyLocalIndices.clear();
            }
        }

        map.hasPreviousChunkWindow = true;
        map.previousChunkMinX = currentMinCx;
        map.previousChunkMaxX = currentMaxCx;
        map.previousChunkMinY = currentMinCy;
        map.previousChunkMaxY = currentMaxCy;
    }

    int getTestedChunkCount() {
        return testedChunkCount;
    }

    int getVisibleChunkCount() {
        return visibleChunkCount;
    }

    int getShownChunkCount() {
        return shownChunkCount;
    }

    int getHiddenChunkCount() {
        return hiddenChunkCount;
    }

    int getDirtyFullChunkCount() {
        return dirtyFullChunkCount;
    }

    int getDirtyPartialChunkCount() {
        return dirtyPartialChunkCount;
    }

    private void updatePartialChunk(TileChunk chunk,
                                    TiledMapLayerData map,
                                    int entityId,
                                    String atlasTag) {

        int layerIndex = mLayer.get(entityId).layerIndex;

        for (int i = 0; i < chunk.dirtyLocalIndices.size; i++) {

            int localIndex = chunk.dirtyLocalIndices.get(i);
            int slot = chunk.soaStartIndex + localIndex;

            int lx = localIndex % chunk.chunkWidth;
            int ly = localIndex / chunk.chunkWidth;

            int gx = chunk.chunkX * map.chunkSize + lx;
            int gy = chunk.chunkY * map.chunkSize + ly;

            writeTileSlot(
                    chunk,
                    localIndex,
                    slot,
                    gx,
                    gy,
                    chunk.assetIds[localIndex],
                    chunk.transformFlags[localIndex],
                    map,
                    atlasTag,
                    layerIndex
            );
        }

        chunk.dirtyLocalIndices.clear();
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        chunk.contentDirty = false;
    }

    private void computeViewBounds() {
        float w = camera.viewportWidth * camera.zoom;
        float h = camera.viewportHeight * camera.zoom;

        viewBounds.set(
                camera.position.x - w * 0.5f,
                camera.position.y - h * 0.5f,
                w,
                h
        );
    }

    private void hideChunksOutsideCurrentWindow(TiledMapLayerData map,
                                                int currentMinCx,
                                                int currentMaxCx,
                                                int currentMinCy,
                                                int currentMaxCy) {
        if (!map.hasPreviousChunkWindow) return;

        int prevMinCx = map.previousChunkMinX;
        int prevMaxCx = map.previousChunkMaxX;
        int prevMinCy = map.previousChunkMinY;
        int prevMaxCy = map.previousChunkMaxY;

        for (int cy = prevMinCy; cy <= prevMaxCy; cy++) {
            for (int cx = prevMinCx; cx <= prevMaxCx; cx++) {
                if (cx >= currentMinCx && cx <= currentMaxCx
                        && cy >= currentMinCy && cy <= currentMaxCy) {
                    continue;
                }

                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null || !chunk.visibleLastFrame) continue;

                hideChunkSlots(chunk);
                chunk.visibleLastFrame = false;
                hiddenChunkCount++;
            }
        }
    }

    private void computeChunkWindow(TiledMapLayerData map, int[] outWindow) {
        int minTx = Integer.MAX_VALUE;
        int maxTx = Integer.MIN_VALUE;
        int minTy = Integer.MAX_VALUE;
        int maxTy = Integer.MIN_VALUE;

        int tx = map.worldToTileX(viewBounds.x, viewBounds.y);
        int ty = map.worldToTileY(viewBounds.x, viewBounds.y);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        tx = map.worldToTileX(viewBounds.x + viewBounds.width, viewBounds.y);
        ty = map.worldToTileY(viewBounds.x + viewBounds.width, viewBounds.y);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        tx = map.worldToTileX(viewBounds.x, viewBounds.y + viewBounds.height);
        ty = map.worldToTileY(viewBounds.x, viewBounds.y + viewBounds.height);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        tx = map.worldToTileX(viewBounds.x + viewBounds.width, viewBounds.y + viewBounds.height);
        ty = map.worldToTileY(viewBounds.x + viewBounds.width, viewBounds.y + viewBounds.height);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        // Conservative expansion avoids edge misses; overlap test filters extras.
        minTx -= 1;
        minTy -= 1;
        maxTx += 1;
        maxTy += 1;

        int clampedMinTx = clamp(minTx, 0, map.mapWidth - 1);
        int clampedMaxTx = clamp(maxTx, 0, map.mapWidth - 1);
        int clampedMinTy = clamp(minTy, 0, map.mapHeight - 1);
        int clampedMaxTy = clamp(maxTy, 0, map.mapHeight - 1);

        int minCx = clampedMinTx / map.chunkSize;
        int maxCx = clampedMaxTx / map.chunkSize;
        int minCy = clampedMinTy / map.chunkSize;
        int maxCy = clampedMaxTy / map.chunkSize;

        minCx = clamp(minCx, 0, map.getChunksX() - 1);
        maxCx = clamp(maxCx, 0, map.getChunksX() - 1);
        minCy = clamp(minCy, 0, map.getChunksY() - 1);
        maxCy = clamp(maxCy, 0, map.getChunksY() - 1);

        outWindow[0] = Math.min(minCx, maxCx);
        outWindow[1] = Math.max(minCx, maxCx);
        outWindow[2] = Math.min(minCy, maxCy);
        outWindow[3] = Math.max(minCy, maxCy);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void hideChunkSlots(TileChunk chunk) {
        for (int i = 0; i < chunk.soaCount; i++) {
            int slot = chunk.soaStartIndex + i;
            state.enabled[slot] = false;
            state.visible[slot] = false;
        }
    }

    private void reactivateChunkSlots(TileChunk chunk) {
        for (int i = 0; i < chunk.soaCount; i++) {
            int slot = chunk.soaStartIndex + i;
            boolean renderable =
                    state.kind[slot] == RenderStateSOA.KIND_SPRITE &&
                            state.textureHandle[slot] != 0;
            state.enabled[slot] = renderable;
            state.visible[slot] = renderable;
        }
    }

    private void rebuildChunk(TileChunk chunk,
                              TiledMapLayerData map,
                              int entityId,
                              String atlasTag) {

        int layerIndex = mLayer.get(entityId).layerIndex;


        // Slot storage remains row-major.
        // The final visual order is ensured by the sortKey:
        // - ORTHO: stable order of slots
        // - ISO: z = gx + gy, tie = gx
        for (int ly = 0; ly < chunk.chunkHeight; ly++) {
            for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                int localIndex = ly * chunk.chunkWidth + lx;
                int slot = chunk.soaStartIndex + localIndex;

                int gx = chunk.chunkX * map.chunkSize + lx;
                int gy = chunk.chunkY * map.chunkSize + ly;

                writeTileSlot(
                        chunk,
                        localIndex,
                        slot,
                        gx,
                        gy,
                        chunk.assetIds[localIndex],
                        chunk.transformFlags[localIndex],
                        map,
                        atlasTag,
                        layerIndex
                );
            }
        }

        chunk.contentDirty = false;
    }

    private void writeTileSlot(TileChunk chunk,
                               int localIndex,
                               int slot,
                               int gx,
                               int gy,
                               int assetId,
                               byte transformFlags,
                               TiledMapLayerData map,
                               String atlasTag,
                               int layerIndex) {

        if (assetId <= 0) {
            state.disable(slot);
            return;
        }

        int frameIndex = chunk.getAnimFrameIndex(localIndex);

        int visualAssetId = TileAnimationResolver.resolveVisualAssetId(
                assetId,
                frameIndex,
                tileAnimationLookup
        );

        AtlasRuntimeService.CachedRegion cr =
                atlasRuntimeService.resolveCached(visualAssetId, atlasTag);

        if (cr == null) {
            state.disable(slot);
            return;
        }

        TileQuadTransforms.buildSpriteQuad(
                map,
                gx,
                gy,
                cr.pixW,
                cr.pixH,
                transformFlags,
                tmpQuad
        );

        state.kind[slot] = RenderStateSOA.KIND_SPRITE;
        state.enabled[slot] = true;
        state.visible[slot] = true;

        state.x1[slot] = tmpQuad[0];
        state.y1[slot] = tmpQuad[1];
        state.x2[slot] = tmpQuad[2];
        state.y2[slot] = tmpQuad[3];
        state.x3[slot] = tmpQuad[4];
        state.y3[slot] = tmpQuad[5];
        state.x4[slot] = tmpQuad[6];
        state.y4[slot] = tmpQuad[7];

        state.u1[slot] = cr.u1;
        state.v1[slot] = cr.v1;
        state.u2[slot] = cr.u2;
        state.v2[slot] = cr.v2;

        state.textureHandle[slot] = cr.textureHandle;
        state.shader[slot] = defaultShaderIdx;
        state.blend[slot] = BlendMode.ALPHA.id;
        state.layerIndex[slot] = layerIndex;

        state.colorPacked[slot] = Color.WHITE.toFloatBits();
        state.a[slot] = 1f;

        state.touch(slot);

        int z = 0;
        int tie = 0;

        // Tiled-compatible isometric depth: farther cells must render first,
        // so depth is the negative diagonal index.
        if (map.projection == SceneMetaRuntime.TiledProjection.ISO) {
            z = clampSortZ(-(gx + gy));
            tie = clampSortTie(gx);
        }

        state.sortKey[slot] = SortKey64.packForBlend(
                state.shader[slot],
                state.blend[slot],
                state.textureHandle[slot],
                state.layerIndex[slot],
                z,
                tie
        );

        state.entityId[slot] = -1;
    }

    private static int clampSortZ(int value) {
        if (value < -32768) return -32768;
        if (value > 32767) return 32767;
        return value;
    }

    private static int clampSortTie(int value) {
        if (value < 0) return 0;
        return Math.min(value, SortKey64.MAX_TIE);
    }

    public void setAnimatedTileLookup(TileAnimationLookup tileAnimationLookup) {
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : assetId -> null;
    }
}
