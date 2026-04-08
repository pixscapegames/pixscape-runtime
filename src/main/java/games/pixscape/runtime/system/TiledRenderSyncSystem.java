package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
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
    private final float[] tmpQuad = new float[8];

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
    }

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

            if (!chunk.visibleLastFrame) {
                chunk.dirtyState = TileChunk.DirtyState.FULL;
                chunk.dirtyLocalIndices.clear();
                chunk.contentDirty = true;
            }
            chunk.visibleLastFrame = true;

            if (chunk.dirtyState == TileChunk.DirtyState.FULL) {
                rebuildChunk(chunk, map, e, tiled.atlasTag);
            } else if (chunk.dirtyState == TileChunk.DirtyState.PARTIAL) {
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

        int layerIndex = mLayer.get(entityId).layerIndex;

        for (int i = 0; i < chunk.dirtyLocalIndices.size; i++) {

            int localIndex = chunk.dirtyLocalIndices.get(i);
            int slot = chunk.soaStartIndex + localIndex;

            int lx = localIndex % chunk.chunkWidth;
            int ly = localIndex / chunk.chunkWidth;

            int gx = chunk.chunkX * map.chunkSize + lx;
            int gy = chunk.chunkY * map.chunkSize + ly;

            writeTileSlot(slot, gx, gy, chunk.assetIds[localIndex], map, atlasTag, layerIndex);
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

    private void disableChunkSlots(TileChunk chunk) {
        for (int i = 0; i < chunk.soaCount; i++) {
            state.disable(chunk.soaStartIndex + i);
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

                writeTileSlot(slot, gx, gy, chunk.assetIds[localIndex], map, atlasTag, layerIndex);
            }
        }

        chunk.contentDirty = false;
    }

    private void writeTileSlot(int slot,
                               int gx,
                               int gy,
                               int assetId,
                               TiledMapLayerData map,
                               String atlasTag,
                               int layerIndex) {

        if (assetId <= 0) {
            state.disable(slot);
            return;
        }

        AtlasRuntimeService.CachedRegion cr =
                atlasRuntimeService.resolveCached(assetId, atlasTag);

        if (cr == null) {
            state.disable(slot);
            return;
        }

        map.tileToSpriteQuad(gx, gy, cr.pixW, cr.pixH, tmpQuad);

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
}