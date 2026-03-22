package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;

@All({LayerComponent.class, TiledLayerComponent.class})
public final class TiledRenderSyncSystem extends IteratingSystem {

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TiledLayerComponent> mTiled;

    private final OrthographicCamera camera;
    private final RenderStateSOA state;
    private final AtlasRuntimeService atlasRuntimeService;
    private final int defaultShaderIdx;

    private final Rectangle viewBounds = new Rectangle();

    private final int globalTiledStart;
    private final int globalTiledEnd;

    private int nextFreeTiledSlot;

    public record SlotRange(int start, int end) {}

    public TiledRenderSyncSystem(OrthographicCamera camera,
                                 RenderStateSOA state,
                                 AtlasRuntimeService atlasRuntimeService,
                                 int defaultShaderIdx,
                                 int tiledStart,
                                 int tiledEnd) {

        this.camera = camera;
        this.state = state;
        this.atlasRuntimeService = atlasRuntimeService;
        this.defaultShaderIdx = defaultShaderIdx;

        this.globalTiledStart = tiledStart;
        this.globalTiledEnd = tiledEnd;
        this.nextFreeTiledSlot = tiledStart;
    }

    // --------------------------------------

    @Override
    protected void process(int e) {
        LayerComponent layer = mLayer.get(e);
        if (layer.type != LayerComponent.TYPE_TILED) return;

        TiledLayerComponent tiled = mTiled.get(e);
        if (tiled == null || tiled.data == null) return;

        TiledMapLayerData map = tiled.data;
        if (!map.visible) return;

        computeViewBounds();

        IntMap.Values<TileChunk> values = map.getChunks();

        while (values.hasNext()) {
            TileChunk chunk = values.next();

            boolean inView = viewBounds.overlaps(chunk.bounds);

            if (!inView) {
                if (chunk.visibleLastFrame) {
                    disableChunkSlots(chunk);
                    chunk.visibleLastFrame = false;
                }
                continue;
            }

            // If the chunk has just become visible again
            if (!chunk.visibleLastFrame) {
                chunk.dirtyState = TileChunk.DirtyState.FULL;
                chunk.dirtyLocalIndices.clear();
                chunk.contentDirty = true;
            }
            chunk.visibleLastFrame = true;

            if (chunk.dirtyState == TileChunk.DirtyState.FULL) {
                rebuildChunk(chunk, map, e, tiled.atlasTag);
            }
            else if (chunk.dirtyState == TileChunk.DirtyState.PARTIAL) {
                updatePartialChunk(chunk, map, e, tiled.atlasTag);
            }
            chunk.dirtyState = TileChunk.DirtyState.CLEAN;
            chunk.dirtyLocalIndices.clear();
        }

    }

    private void updatePartialChunk(TileChunk chunk,
                                    TiledMapLayerData map,
                                    int entityId,
                                    String atlasTag) {

        float tileWidth  = map.tileWidth;
        float tileHeight = map.tileHeight;
        int layerIndex = mLayer.get(entityId).layerIndex;

        for (int i = 0; i < chunk.dirtyLocalIndices.size; i++) {

            int localIndex = chunk.dirtyLocalIndices.get(i);
            int slot = chunk.soaStartIndex + localIndex;

            int assetId = chunk.assetIds[localIndex];

            if (assetId <= 0) {
                state.disable(slot);
                continue;
            }

            AtlasRuntimeService.CachedRegion cr =
                    atlasRuntimeService.resolveCached(assetId, atlasTag);

            if (cr == null) {
                state.disable(slot);
                continue;
            }

            int lx = localIndex % chunk.chunkWidth;
            int ly = localIndex / chunk.chunkWidth;

            float x = chunk.bounds.x + lx * tileWidth;
            float y = chunk.bounds.y + ly * tileHeight;
            float x2 = x + tileWidth;
            float y2 = y + tileHeight;

            state.kind[slot] = RenderStateSOA.KIND_SPRITE;
            state.enabled[slot] = true;
            state.visible[slot] = true;

            state.x1[slot] = x;  state.y1[slot] = y;
            state.x2[slot] = x;  state.y2[slot] = y2;
            state.x3[slot] = x2; state.y3[slot] = y2;
            state.x4[slot] = x2; state.y4[slot] = y;

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

            state.sortKey[slot] = SortKey64.packForBlend(
                    state.shader[slot],
                    state.blend[slot],
                    state.textureHandle[slot],
                    state.layerIndex[slot],
                    0,          // z
                    0     // tie
            );

            state.entityId[slot] = -1;
        }

        chunk.dirtyLocalIndices.clear();
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;
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

    private void disableChunkSlots(TileChunk chunk) {
        for (int i = 0; i < chunk.soaCount; i++) {
            state.disable(chunk.soaStartIndex + i);
        }
    }

    private void rebuildChunk(TileChunk chunk,
                              TiledMapLayerData map,
                              int entityId,
                              String atlasTag) {

        float tileWidth  = map.tileWidth;
        float tileHeight = map.tileHeight;

        for (int ly = 0; ly < chunk.chunkHeight; ly++) {
            for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                int localIndex = ly * chunk.chunkWidth + lx;
                int slot = chunk.soaStartIndex + localIndex;

                int assetId = chunk.assetIds[localIndex];

                if (assetId <= 0) {
                    state.disable(slot);
                    continue;
                }

                AtlasRuntimeService.CachedRegion cr =
                        atlasRuntimeService.resolveCached(assetId, atlasTag);

                if (cr == null) {
                    state.disable(slot);
                    continue;
                }

                float x = chunk.bounds.x + lx * tileWidth;
                float y = chunk.bounds.y + ly * tileHeight;
                float x2 = x + tileWidth;
                float y2 = y + tileHeight;

                state.kind[slot] = RenderStateSOA.KIND_SPRITE;
                state.enabled[slot] = true;
                state.visible[slot] = true;

                state.x1[slot] = x;  state.y1[slot] = y;
                state.x2[slot] = x;  state.y2[slot] = y2;
                state.x3[slot] = x2; state.y3[slot] = y2;
                state.x4[slot] = x2; state.y4[slot] = y;

                state.u1[slot] = cr.u1;
                state.v1[slot] = cr.v1;
                state.u2[slot] = cr.u2;
                state.v2[slot] = cr.v2;

                state.textureHandle[slot] = cr.textureHandle;
                state.shader[slot] = defaultShaderIdx;
                state.blend[slot] = BlendMode.ALPHA.id;

                state.layerIndex[slot] = mLayer.get(entityId).layerIndex;

                state.colorPacked[slot] = Color.WHITE.toFloatBits();
                state.a[slot] = 1f;

                state.touch(slot);

                state.sortKey[slot] = SortKey64.packForBlend(
                        state.shader[slot],
                        state.blend[slot],
                        state.textureHandle[slot],
                        state.layerIndex[slot],
                        0,          // z
                        0        // tie
                );

                state.entityId[slot] = -1;
            }
        }

        chunk.contentDirty = false;
    }
}
