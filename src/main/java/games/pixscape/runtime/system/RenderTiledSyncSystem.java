package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.spatial.SpatialLayerFaceRuntime;
import games.pixscape.runtime.spatial.SpatialLayerRuntimeRegistry;
import games.pixscape.runtime.spatial.SpatialTileOrderCache;
import games.pixscape.runtime.spatial.SpatialTileSyncInvariantException;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TileQuadTransforms;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationResolver;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.runtime.tiled.profile.TileProfilePlacement;


@All({LayerComponent.class, TiledLayerComponent.class})
public final class RenderTiledSyncSystem extends IteratingSystem implements ProfiledSystem {

    private static final int CHUNK_OUTSIDE = 0;
    private static final int CHUNK_FULLY_INSIDE = 1;
    private static final int CHUNK_PARTIAL = 2;

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TiledLayerComponent> mTiled;
    private ComponentMapper<SpatialBlocksComponent> mSpatialBlocks;
    private ComponentMapper<PixscapeIdentityComponent> mIdentity;

    private final OrthographicCamera camera;
    private final TiledMapRenderState tiledState;
    private final AtlasRuntimeService atlasRuntimeService;
    private final int defaultShaderIdx;
    private final RuntimeTilesetProfiles tilesetProfiles;
    private final SpatialLayerRuntimeRegistry spatialRuntimeRegistry;
    private SpatialTileOrderCache currentTileOrder;
    private SpatialBlocksComponent currentSpatialBlocks;
    private SpatialLayerFaceRuntime currentSpatialRuntime;
    private int currentLayerEntity = -1;
    private boolean refreshingTileKeys;
    private TileAnimationLookup tileAnimationLookup;

    private final Rectangle viewBounds = new Rectangle();
    private final float[] tmpQuad = new float[8];
    private final float[] tmpSpriteBounds = new float[4];
    private final int[] tmpWindow = new int[4];
    private final IntSet reportedMissingProfileTileAssetIds = new IntSet();
    private int testedChunkCount;
    private int visibleChunkCount;
    private int shownChunkCount;
    private int hiddenChunkCount;
    private int dirtyFullChunkCount;
    private int dirtyPartialChunkCount;
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public RenderTiledSyncSystem(OrthographicCamera camera,
                                 TiledMapRenderState tiledState,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx) {
        this(
                camera,
                tiledState,
                atlasRuntimeService,
                defaultShaderIdx,
                null,
                null,
                null
        );
    }

    public RenderTiledSyncSystem(OrthographicCamera camera,
                                 TiledMapRenderState tiledState,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx,
                                 TileAnimationLookup tileAnimationLookup) {
        this(
                camera,
                tiledState,
                atlasRuntimeService,
                defaultShaderIdx,
                tileAnimationLookup,
                null,
                null
        );
    }

    public RenderTiledSyncSystem(OrthographicCamera camera,
                                 TiledMapRenderState tiledState,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx,
                                 TileAnimationLookup tileAnimationLookup,
                                 RuntimeTilesetProfiles tilesetProfiles) {
        this(camera, tiledState, atlasRuntimeService, defaultShaderIdx, tileAnimationLookup,
                tilesetProfiles, null);
    }

    public RenderTiledSyncSystem(OrthographicCamera camera,
                                 TiledMapRenderState tiledState,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx,
                                 TileAnimationLookup tileAnimationLookup,
                                 RuntimeTilesetProfiles tilesetProfiles,
                                 SpatialLayerRuntimeRegistry spatialRuntimeRegistry) {

        this.camera = camera;
        this.tiledState = tiledState;
        this.atlasRuntimeService = atlasRuntimeService;
        this.defaultShaderIdx = defaultShaderIdx;
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : assetId -> null;
        this.tilesetProfiles = tilesetProfiles != null ? tilesetProfiles : RuntimeTilesetProfiles.empty();
        this.spatialRuntimeRegistry = spatialRuntimeRegistry != null
                ? spatialRuntimeRegistry : new SpatialLayerRuntimeRegistry();
    }

    @Override
    protected void begin() {
        profiling = profiler.enabled();
        if (profiling) {
            profileStartNs = profiler.begin(SystemProfilePhases.RENDER_TILED_SYNC);
        }
        computeViewBounds();
        tiledState.clearVisibleSlots();
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
        currentTileOrder = null;
        currentSpatialBlocks = null;
        currentSpatialRuntime = null;
        currentLayerEntity = e;
        if (map.projection == SceneMetaRuntime.TiledProjection.ISO
                && (layer.spatialEnabled || tiled.spatialEnabled || map.spatialEnabled)) {
            ensureAllChunkRenderRefs(map);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(e, null);
            SpatialLayerFaceRuntime runtime = spatialRuntimeRegistry.forLayer(e);
            runtime.compiled.ensure(blocks);
            runtime.projected.ensure(runtime.compiled, map);
            runtime.tileOrder.ensure(e, map, blocks, runtime.compiled);
            currentTileOrder = runtime.tileOrder;
            currentSpatialBlocks = blocks;
            currentSpatialRuntime = runtime;
            if (currentTileOrder.needsKeyRefresh()) refreshTileKeys(map, layer.layerIndex, currentTileOrder);
        }
        if (!map.visible) return;
        refreshVisualPaddingIfDirty(map, tiled.atlasTag);
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

                ensureChunkRenderRefs(chunk);
                if (chunk.dirtyState == TileChunk.DirtyState.FULL) {
                    dirtyFullChunkCount++;
                    rebuildChunk(chunk, map, e, tiled.atlasTag);
                } else if (chunk.dirtyState == TileChunk.DirtyState.PARTIAL) {
                    dirtyPartialChunkCount++;
                    updatePartialChunk(chunk, map, e, tiled.atlasTag);
                }

                if (chunk.renderMetadataDirty) {
                    recomputeChunkVisualBounds(chunk);
                }

                int classification = classifyChunk(chunk);
                tiledState.cullingChunksTested++;

                if (classification == CHUNK_OUTSIDE) {
                    tiledState.cullingChunksOutside++;
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
                }
                chunk.visibleLastFrame = true;

                if (classification == CHUNK_FULLY_INSIDE) {
                    tiledState.cullingChunksFullyInside++;
                    publishFullyInsideChunk(chunk);
                } else {
                    tiledState.cullingChunksPartial++;
                    publishPartialChunk(chunk);
                }
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
            int tiledRenderRef = chunk.renderRefStartIndex + localIndex;

            int lx = localIndex % chunk.chunkWidth;
            int ly = localIndex / chunk.chunkWidth;

            int gx = chunk.chunkX * map.chunkSize + lx;
            int gy = chunk.chunkY * map.chunkSize + ly;

            writeTileSlot(
                    chunk,
                    localIndex,
                    tiledRenderRef,
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
        recomputeChunkVisualBounds(chunk);
    }

    private void ensureChunkRenderRefs(TileChunk chunk) {
        int cellCount = chunk.cellCount();
        if (chunk.renderRefStartIndex < 0 || chunk.renderRefCount != cellCount) {
            chunk.renderRefStartIndex = tiledState.registerRefs(cellCount);
            chunk.renderRefCount = cellCount;
        }
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

        float queryMinX = viewBounds.x - map.visualPaddingRight;
        float queryMaxX = viewBounds.x + viewBounds.width + map.visualPaddingLeft;
        float queryMinY = viewBounds.y - map.visualPaddingTop;
        float queryMaxY = viewBounds.y + viewBounds.height + map.visualPaddingBottom;

        int tx = map.worldToTileX(queryMinX, queryMinY);
        int ty = map.worldToTileY(queryMinX, queryMinY);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        tx = map.worldToTileX(queryMaxX, queryMinY);
        ty = map.worldToTileY(queryMaxX, queryMinY);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        tx = map.worldToTileX(queryMinX, queryMaxY);
        ty = map.worldToTileY(queryMinX, queryMaxY);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        tx = map.worldToTileX(queryMaxX, queryMaxY);
        ty = map.worldToTileY(queryMaxX, queryMaxY);
        minTx = Math.min(minTx, tx);
        maxTx = Math.max(maxTx, tx);
        minTy = Math.min(minTy, ty);
        maxTy = Math.max(maxTy, ty);

        // Conservative expansion avoids edge misses; overlap test filters extras.
        int tilePadding = 1;
        minTx -= tilePadding;
        minTy -= tilePadding;
        maxTx += tilePadding;
        maxTy += tilePadding;

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

    private void refreshVisualPaddingIfDirty(TiledMapLayerData map, String atlasTag) {
        if (!map.visualBoundsDirty) return;

        map.updateAllChunkBounds();

        float left = 0f;
        float right = 0f;
        float top = 0f;
        float bottom = 0f;

        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null) continue;

                for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                    for (int lx = 0; lx < chunk.chunkWidth; lx++) {
                        int localIndex = ly * chunk.chunkWidth + lx;
                        int assetId = chunk.assetIds[localIndex];
                        if (assetId <= 0) continue;

                        int frameIndex = chunk.getAnimFrameIndex(localIndex);
                        int visualAssetId = TileAnimationResolver.resolveVisualAssetId(
                                assetId,
                                frameIndex,
                                tileAnimationLookup
                        );

                        AtlasRuntimeService.CachedRegion cr =
                                atlasRuntimeService.resolveCached(visualAssetId, atlasTag);
                        if (cr == null) continue;

                        int gx = chunk.chunkX * map.chunkSize + lx;
                        int gy = chunk.chunkY * map.chunkSize + ly;
                        RuntimeTilesetProfile profile = tilesetProfiles.profileForTileAsset(visualAssetId);
                        if (profile == null) {
                            reportMissingProfileOnce(visualAssetId, assetId, atlasTag);
                            continue;
                        }

                        TileQuadTransforms.buildSpriteQuad(
                                map,
                                gx,
                                gy,
                                cr.pixW,
                                cr.pixH,
                                profile,
                                chunk.transformFlags[localIndex],
                                tmpQuad
                        );
                        TileProfilePlacement.computeSpriteBounds(tmpQuad, tmpSpriteBounds);

                        float cellX = map.tileToWorldX(gx, gy);
                        float cellY = map.tileToWorldY(gx, gy);
                        float cellRight = cellX + map.tileWidth;
                        float cellTop = cellY + map.tileHeight;

                        left = Math.max(left, cellX - tmpSpriteBounds[0]);
                        right = Math.max(right, tmpSpriteBounds[2] - cellRight);
                        bottom = Math.max(bottom, cellY - tmpSpriteBounds[1]);
                        top = Math.max(top, tmpSpriteBounds[3] - cellTop);
                    }
                }
            }
        }

        map.setVisualPadding(left, right, top, bottom);
    }

    private void hideChunkSlots(TileChunk chunk) {
        // Visibility is frame-local through TiledMapRenderState.visibleRefs.
        // Keep persistent ref render data intact so clean chunks can reappear
        // without a rebuild.
    }

    private int classifyChunk(TileChunk chunk) {
        if (chunk == null || !chunk.hasVisualBounds || chunk.getRenderableRefCount() == 0) {
            return CHUNK_OUTSIDE;
        }

        float viewMinX = viewBounds.x;
        float viewMinY = viewBounds.y;
        float viewMaxX = viewBounds.x + viewBounds.width;
        float viewMaxY = viewBounds.y + viewBounds.height;

        if (!boundsOverlap(
                chunk.visualMinX,
                chunk.visualMinY,
                chunk.visualMaxX,
                chunk.visualMaxY,
                viewMinX,
                viewMinY,
                viewMaxX,
                viewMaxY)) {
            return CHUNK_OUTSIDE;
        }

        if (viewMinX <= chunk.visualMinX
                && viewMaxX >= chunk.visualMaxX
                && viewMinY <= chunk.visualMinY
                && viewMaxY >= chunk.visualMaxY) {
            return CHUNK_FULLY_INSIDE;
        }

        return CHUNK_PARTIAL;
    }

    private void publishFullyInsideChunk(TileChunk chunk) {
        int count = chunk.getRenderableRefCount();
        tiledState.cullingRenderableRefsConsidered += count;
        tiledState.cullingRenderableRefsVisible += count;

        for (int i = 0; i < count; i++) {
            tiledState.addVisibleRef(chunk.renderRefStartIndex + chunk.renderableLocalIndices.get(i));
        }
    }

    private void publishPartialChunk(TileChunk chunk) {
        int count = chunk.getRenderableRefCount();
        tiledState.cullingRenderableRefsConsidered += count;

        float viewMinX = viewBounds.x;
        float viewMinY = viewBounds.y;
        float viewMaxX = viewBounds.x + viewBounds.width;
        float viewMaxY = viewBounds.y + viewBounds.height;

        for (int i = 0; i < count; i++) {
            int ref = chunk.renderRefStartIndex + chunk.renderableLocalIndices.get(i);
            float minX = min4(tiledState.x1[ref], tiledState.x2[ref], tiledState.x3[ref], tiledState.x4[ref]);
            float maxX = max4(tiledState.x1[ref], tiledState.x2[ref], tiledState.x3[ref], tiledState.x4[ref]);
            float minY = min4(tiledState.y1[ref], tiledState.y2[ref], tiledState.y3[ref], tiledState.y4[ref]);
            float maxY = max4(tiledState.y1[ref], tiledState.y2[ref], tiledState.y3[ref], tiledState.y4[ref]);

            if (boundsOverlap(minX, minY, maxX, maxY, viewMinX, viewMinY, viewMaxX, viewMaxY)) {
                tiledState.addVisibleRef(ref);
                tiledState.cullingRenderableRefsVisible++;
            } else {
                tiledState.cullingRenderableRefsCulled++;
            }
        }
    }

    private void recomputeChunkVisualBounds(TileChunk chunk) {
        int count = chunk.getRenderableRefCount();
        if (count == 0 || chunk.renderRefStartIndex < 0) {
            chunk.hasVisualBounds = false;
            chunk.renderMetadataDirty = false;
            return;
        }

        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < count; i++) {
            int ref = chunk.renderRefStartIndex + chunk.renderableLocalIndices.get(i);

            float refMinX = min4(tiledState.x1[ref], tiledState.x2[ref], tiledState.x3[ref], tiledState.x4[ref]);
            float refMaxX = max4(tiledState.x1[ref], tiledState.x2[ref], tiledState.x3[ref], tiledState.x4[ref]);
            float refMinY = min4(tiledState.y1[ref], tiledState.y2[ref], tiledState.y3[ref], tiledState.y4[ref]);
            float refMaxY = max4(tiledState.y1[ref], tiledState.y2[ref], tiledState.y3[ref], tiledState.y4[ref]);

            if (refMinX < minX) minX = refMinX;
            if (refMaxX > maxX) maxX = refMaxX;
            if (refMinY < minY) minY = refMinY;
            if (refMaxY > maxY) maxY = refMaxY;
        }

        chunk.visualMinX = minX;
        chunk.visualMinY = minY;
        chunk.visualMaxX = maxX;
        chunk.visualMaxY = maxY;
        chunk.hasVisualBounds = true;
        chunk.renderMetadataDirty = false;
    }

    private static boolean boundsOverlap(float minX,
                                         float minY,
                                         float maxX,
                                         float maxY,
                                         float otherMinX,
                                         float otherMinY,
                                         float otherMaxX,
                                         float otherMaxY) {
        return minX < otherMaxX
                && maxX > otherMinX
                && minY < otherMaxY
                && maxY > otherMinY;
    }

    private static float min4(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max4(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private void rebuildChunk(TileChunk chunk,
                              TiledMapLayerData map,
                              int entityId,
                              String atlasTag) {

        int layerIndex = mLayer.get(entityId).layerIndex;

        chunk.clearRenderableRefs();

        // Slot storage remains row-major.
        // The final visual order is ensured by the sortKey:
        // - ORTHO: stable order of slots
        // - ISO: z = gx + gy, tie = gx
        for (int ly = 0; ly < chunk.chunkHeight; ly++) {
            for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                int localIndex = ly * chunk.chunkWidth + lx;
                int tiledRenderRef = chunk.renderRefStartIndex + localIndex;

                int gx = chunk.chunkX * map.chunkSize + lx;
                int gy = chunk.chunkY * map.chunkSize + ly;

                writeTileSlot(
                        chunk,
                        localIndex,
                        tiledRenderRef,
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
        chunk.dirtyLocalIndices.clear();
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        recomputeChunkVisualBounds(chunk);
    }

    private void writeTileSlot(TileChunk chunk,
                               int localIndex,
                               int tiledRenderRef,
                               int gx,
                               int gy,
                               int assetId,
                               byte transformFlags,
                               TiledMapLayerData map,
                               String atlasTag,
                               int layerIndex) {

        if (assetId <= 0) {
            tiledState.disableRef(tiledRenderRef);
            chunk.setRenderableLocalIndex(localIndex, false);
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
            tiledState.disableRef(tiledRenderRef);
            chunk.setRenderableLocalIndex(localIndex, false);
            return;
        }

        RuntimeTilesetProfile profile = tilesetProfiles.profileForTileAsset(visualAssetId);
        if (profile == null) {
            reportMissingProfileOnce(visualAssetId, assetId, atlasTag);
            tiledState.disableRef(tiledRenderRef);
            chunk.setRenderableLocalIndex(localIndex, false);
            return;
        }

        TileQuadTransforms.buildSpriteQuad(
                map,
                gx,
                gy,
                cr.pixW,
                cr.pixH,
                profile,
                transformFlags,
                tmpQuad
        );

        int z = 0;
        int tie = 0;

        // Tiled-compatible isometric depth: farther cells must render first,
        // so depth is the negative diagonal index.
        if (map.projection == SceneMetaRuntime.TiledProjection.ISO) {
            z = clampSortZ(-(gx + gy));
            tie = clampSortTie(gx);
        }

        long sortKey;
        if (currentTileOrder != null) {
            int rank = currentTileOrder.rank(gx, gy);
            if (rank < 0 && currentTileOrder.requiresCanonicalRank(map, gx, gy)) {
                rank = recoverRequiredRank(map, gx, gy, assetId, layerIndex);
            }
            if (rank >= 0) {
                sortKey = SortKey64.packForBlendOrder30(defaultShaderIdx, BlendMode.ALPHA.id,
                        cr.textureHandle, layerIndex, rank);
            } else {
                sortKey = SortKey64.packForBlend(defaultShaderIdx, BlendMode.ALPHA.id,
                        cr.textureHandle, layerIndex, z, tie);
            }
        } else {
            sortKey = SortKey64.packForBlend(defaultShaderIdx, BlendMode.ALPHA.id,
                    cr.textureHandle, layerIndex, z, tie);
        }

        tiledState.setRenderDataForRef(
                tiledRenderRef,
                cr.textureHandle,
                defaultShaderIdx,
                BlendMode.ALPHA.id,
                layerIndex,
                0,
                0,
                sortKey,
                tmpQuad[0],
                tmpQuad[1],
                tmpQuad[2],
                tmpQuad[3],
                tmpQuad[4],
                tmpQuad[5],
                tmpQuad[6],
                tmpQuad[7],
                cr.u1,
                cr.v1,
                cr.u2,
                cr.v2,
                Color.WHITE.toFloatBits(),
                1f,
                RenderRepeatFlags.NONE
        );
        chunk.setRenderableLocalIndex(localIndex, true);
        chunk.markRenderMetadataDirty();
    }

    private void reportMissingProfileOnce(int visualAssetId, int logicalAssetId, String atlasTag) {
        if (visualAssetId <= 0 || reportedMissingProfileTileAssetIds.contains(visualAssetId)) {
            return;
        }
        reportedMissingProfileTileAssetIds.add(visualAssetId);

        String message = "Missing tileset profile for tile asset " + visualAssetId
                + " (logical tile asset " + logicalAssetId
                + ", atlasTag " + (atlasTag != null ? atlasTag : "<none>") + ")";
        if (Gdx.app != null) {
            Gdx.app.error("RenderTiledSyncSystem", message);
        } else {
            System.err.println("[RenderTiledSyncSystem] " + message);
        }
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

    private void refreshTileKeys(TiledMapLayerData map, int layerIndex, SpatialTileOrderCache order) {
        refreshingTileKeys = true;
        try {
            for (com.badlogic.gdx.utils.IntMap.Values<TileChunk> values = map.getChunks(); values.hasNext(); ) {
                TileChunk chunk = values.next();
                if (chunk.renderRefStartIndex < 0) continue;
                for (int local = 0; local < chunk.cellCount(); local++) {
                    if (chunk.assetIds[local] <= 0) continue;
                    int ref = chunk.renderRefStartIndex + local;
                    if (ref < 0 || ref >= tiledState.getRefCount() || !tiledState.enabled[ref]) continue;
                    int gx = chunk.chunkX * map.chunkSize + local % chunk.chunkWidth;
                    int gy = chunk.chunkY * map.chunkSize + local / chunk.chunkWidth;
                    int rank = order.rank(gx, gy);
                    if (rank < 0 && order.requiresCanonicalRank(map, gx, gy)) {
                        rank = recoverRequiredRank(map, gx, gy, chunk.assetIds[local], layerIndex);
                    }
                    if (rank >= 0) {
                        tiledState.sortKey[ref] = SortKey64.packForBlendOrder30(
                                tiledState.shader[ref], tiledState.blend[ref], tiledState.textureHandle[ref],
                                layerIndex, rank);
                    } else if (order.requiresCanonicalRank(map, gx, gy)) {
                        throw missingRequiredRank(map, gx, gy, chunk.assetIds[local], layerIndex,
                                "missing-after-canonical-key-refresh");
                    } else {
                        int z = clampSortZ(-(gx + gy));
                        int tie = clampSortTie(gx);
                        tiledState.sortKey[ref] = SortKey64.packForBlend(
                                tiledState.shader[ref], tiledState.blend[ref], tiledState.textureHandle[ref],
                                layerIndex, z, tie);
                    }
                }
            }
            order.markKeysApplied();
        } finally {
            refreshingTileKeys = false;
        }
    }

    private int recoverRequiredRank(TiledMapLayerData map,
                                    int gx,
                                    int gy,
                                    int assetId,
                                    int layerIndex) {
        if (currentSpatialRuntime != null) {
            currentSpatialRuntime.compiled.ensure(currentSpatialBlocks);
            currentSpatialRuntime.projected.ensure(currentSpatialRuntime.compiled, map);
            currentTileOrder.forceRebuild(currentLayerEntity, map, currentSpatialBlocks,
                    currentSpatialRuntime.compiled);
            int recovered = currentTileOrder.rank(gx, gy);
            if (recovered >= 0) {
                if (!refreshingTileKeys) refreshTileKeys(map, layerIndex, currentTileOrder);
                return recovered;
            }
        }
        throw missingRequiredRank(map, gx, gy, assetId, layerIndex, "missing-after-forced-rebuild");
    }

    private SpatialTileSyncInvariantException missingRequiredRank(TiledMapLayerData map,
                                                                  int gx,
                                                                  int gy,
                                                                  int assetId,
                                                                  int layerIndex,
                                                                  String lookupState) {
        int chunkX = map.chunkSize > 0 ? gx / map.chunkSize : -1;
        int chunkY = map.chunkSize > 0 ? gy / map.chunkSize : -1;
        PixscapeIdentityComponent identity = mIdentity.getSafe(currentLayerEntity, null);
        String layerName = identity != null && identity.name != null ? identity.name : "<unnamed>";
        int owner = currentTileOrder != null ? currentTileOrder.ownerBlockId(gx, gy) : 0;
        int anchor = currentTileOrder != null ? currentTileOrder.anchorStructureId(gx, gy) : 0;
        boolean spatialLayer = mLayer.get(currentLayerEntity).spatialEnabled
                || mTiled.get(currentLayerEntity).spatialEnabled || map.spatialEnabled;
        return new SpatialTileSyncInvariantException("Spatial tiled sync could not resolve canonical rank: cell=("
                + gx + "," + gy + "), layerEntity=" + currentLayerEntity + ", layerName=" + layerName
                + ", layerIndex=" + layerIndex + ", chunk=(" + chunkX + "," + chunkY + ")"
                + ", tileAssetId=" + assetId + ", spatialLayer=" + spatialLayer
                + ", ownerBlockId=" + (owner != 0 ? String.valueOf(owner) : "<none>")
                + ", anchorStructureId=" + (anchor != 0 ? String.valueOf(anchor) : "<none>")
                + ", explicitSpatialMetadata=" + map.hasTileSpatialOverride(gx, gy)
                + ", canonicalRankState=" + lookupState + ".");
    }

    private void ensureAllChunkRenderRefs(TiledMapLayerData map) {
        for (com.badlogic.gdx.utils.IntMap.Values<TileChunk> values = map.getChunks(); values.hasNext(); ) {
            ensureChunkRenderRefs(values.next());
        }
    }

    public void setAnimatedTileLookup(TileAnimationLookup tileAnimationLookup) {
        this.tileAnimationLookup = tileAnimationLookup != null ? tileAnimationLookup : assetId -> null;
    }

    @Override
    protected void end() {
        if (profiling) {
            profiler.end(SystemProfilePhases.RENDER_TILED_SYNC, profileStartNs);
            profiling = false;
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
