package games.pixscape.runtime.tiled;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.loading.SceneMetaRuntime;

public final class TiledMapLayerData {

    private static final float EPSILON = 0.0001f;

    // =========================
    // CONFIG MAP
    // =========================

    public int mapWidth;
    public int mapHeight;

    public int tileWidth;
    public int tileHeight;

    public float originX;
    public float originY;
    public boolean spatialEnabled;
    public float defaultTileAltitude;
    public float defaultTileHeight;

    public int chunkSize;

    public SceneMetaRuntime.TiledProjection projection =
            SceneMetaRuntime.TiledProjection.ORTHO;

    // =========================
    // SOA RANGE (injected by allocator)
    // =========================

    public int layerTiledStart;
    public int layerTiledEnd;

    // =========================
    // CHUNK GRID
    // =========================

    private int chunksX;
    private int chunksY;

    // =========================
    // CHUNKS
    // =========================

    private final IntMap<TileChunk> chunks = new IntMap<>();

    public boolean visible = true;
    public boolean collisionEnabled = true;
    public boolean hasPreviousChunkWindow = false;
    public int previousChunkMinX = 0;
    public int previousChunkMaxX = -1;
    public int previousChunkMinY = 0;
    public int previousChunkMaxY = -1;
    public boolean visualBoundsDirty = true;
    public float visualPaddingLeft = 0f;
    public float visualPaddingRight = 0f;
    public float visualPaddingTop = 0f;
    public float visualPaddingBottom = 0f;

    public TiledMapLayerData(int mapWidth,
                             int mapHeight,
                             int tileWidth,
                             int tileHeight,
                             int chunkSize) {
        this(mapWidth, mapHeight, tileWidth, tileHeight, chunkSize,
                SceneMetaRuntime.TiledProjection.ORTHO);
    }

    public TiledMapLayerData(int mapWidth,
                             int mapHeight,
                             int tileWidth,
                             int tileHeight,
                             int chunkSize,
                             SceneMetaRuntime.TiledProjection projection) {

        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.chunkSize = chunkSize;
        this.projection = projection != null
                ? projection
                : SceneMetaRuntime.TiledProjection.ORTHO;
    }

    public TiledMapLayerData() {
    }

    // ============================================================
    // INITIALISATION RANGE
    // ============================================================

    public void initSlotRange(int layerStart, int layerEnd) {
        this.layerTiledStart = layerStart;
        this.layerTiledEnd = layerEnd;
        allocateChunksStrict();
    }

    private void allocateChunksStrict() {

        chunks.clear();

        int cursor = layerTiledStart;

        chunksX = Math.max(1, (mapWidth + chunkSize - 1) / chunkSize);
        chunksY = Math.max(1, (mapHeight + chunkSize - 1) / chunkSize);

        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {

                int worldTileX = cx * chunkSize;
                int worldTileY = cy * chunkSize;

                int chunkWidth = Math.min(chunkSize, mapWidth - worldTileX);
                int chunkHeight = Math.min(chunkSize, mapHeight - worldTileY);

                if (chunkWidth <= 0 || chunkHeight <= 0) continue;

                int requiredSlots = chunkWidth * chunkHeight;

                if (cursor + requiredSlots > layerTiledEnd) {
                    throw new IllegalStateException(
                            "TILED range overflow. Required=" +
                                    (cursor + requiredSlots) +
                                    " end=" + layerTiledEnd
                    );
                }

                TileChunk chunk = new TileChunk(
                        cx,
                        cy,
                        chunkWidth,
                        chunkHeight,
                        cursor
                );

                updateChunkBounds(chunk);

                chunks.put(packChunk(cx, cy), chunk);

                cursor += requiredSlots;
            }
        }

        if (cursor > layerTiledEnd) {
            throw new IllegalStateException("Tiled allocation overflow");
        }

        hasPreviousChunkWindow = false;
        previousChunkMinX = 0;
        previousChunkMaxX = -1;
        previousChunkMinY = 0;
        previousChunkMaxY = -1;
        markVisualBoundsDirty();
    }

    public void updateChunkBounds(TileChunk chunk) {
        if (chunk == null) return;

        if (chunk.bounds == null) {
            chunk.bounds = new Rectangle();
        }

        int gx0 = chunk.chunkX * chunkSize;
        int gy0 = chunk.chunkY * chunkSize;
        int gx1 = gx0 + chunk.chunkWidth - 1;
        int gy1 = gy0 + chunk.chunkHeight - 1;

        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            float worldX = originX + gx0 * tileWidth;
            float worldY = originY + gy0 * tileHeight;
            float worldW = chunk.chunkWidth * tileWidth;
            float worldH = chunk.chunkHeight * tileHeight;
            chunk.bounds.set(worldX, worldY, worldW, worldH);
            return;
        }

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;

        float minX = originX + (gx0 - gy1) * halfW;
        float maxX = originX + (gx1 - gy0) * halfW + tileWidth;

        float minY = originY + (gx0 + gy0) * halfH;
        float maxY = originY + (gx1 + gy1) * halfH + tileHeight;

        chunk.bounds.set(minX, minY, maxX - minX, maxY - minY);
    }

    // ============================================================
    // TILE ACCESS
    // ============================================================

    public int getTile(int gx, int gy) {

        if (!isInside(gx, gy)) return 0;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(packChunk(cx, cy));
        if (chunk == null) return 0;

        int lx = gx - (cx * chunkSize);
        int ly = gy - (cy * chunkSize);

        return chunk.get(lx, ly);
    }

    public void setTile(int gx, int gy, int assetId) {
        setTile(gx, gy, assetId, TileTransformFlags.NONE);
    }

    public void setTile(int gx, int gy, int assetId, byte flags) {
        if (!isInside(gx, gy)) return;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(packChunk(cx, cy));
        if (chunk == null) return;

        int lx = gx - (cx * chunkSize);
        int ly = gy - (cy * chunkSize);

        chunk.set(lx, ly, assetId, flags);
        markVisualBoundsDirty();
    }

    public float getTileAltitude(int gx, int gy) {
        if (!isInside(gx, gy)) return 0f;

        TileChunk chunk = chunkForTile(gx, gy);
        if (chunk == null) return 0f;

        int lx = gx - (gx / chunkSize) * chunkSize;
        int ly = gy - (gy / chunkSize) * chunkSize;
        return chunk.hasSpatialOverride(lx, ly) ? chunk.getAltitude(lx, ly) : defaultTileAltitude;
    }

    public float getTileHeight(int gx, int gy) {
        if (!isInside(gx, gy)) return 0f;

        TileChunk chunk = chunkForTile(gx, gy);
        if (chunk == null) return 0f;

        int lx = gx - (gx / chunkSize) * chunkSize;
        int ly = gy - (gy / chunkSize) * chunkSize;
        return chunk.hasSpatialOverride(lx, ly) ? chunk.getHeight(lx, ly) : defaultTileHeight;
    }

    public int getTileSpatialFlags(int gx, int gy) {
        if (!isInside(gx, gy)) return 0;

        TileChunk chunk = chunkForTile(gx, gy);
        if (chunk == null) return 0;

        int lx = gx - (gx / chunkSize) * chunkSize;
        int ly = gy - (gy / chunkSize) * chunkSize;
        return chunk.hasSpatialOverride(lx, ly) ? chunk.getSpatialFlags(lx, ly) : 0;
    }

    public boolean hasTileSpatialOverride(int gx, int gy) {
        if (!isInside(gx, gy)) return false;

        TileChunk chunk = chunkForTile(gx, gy);
        if (chunk == null) return false;

        int lx = gx - (gx / chunkSize) * chunkSize;
        int ly = gy - (gy / chunkSize) * chunkSize;
        return chunk.hasSpatialOverride(lx, ly);
    }

    public void setTileSpatial(int gx, int gy, float altitude, float height, int flags) {
        setTileSpatial(gx, gy, altitude, height, flags, true);
    }

    public void setTileSpatialOverride(int gx, int gy, float altitude, float height, int flags) {
        setTileSpatial(gx, gy, altitude, height, flags, true);
    }

    public void clearTileSpatialOverride(int gx, int gy) {
        if (!isInside(gx, gy)) return;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(packChunk(cx, cy));
        if (chunk == null) return;

        int lx = gx - (cx * chunkSize);
        int ly = gy - (cy * chunkSize);
        chunk.clearSpatialOverride(lx, ly);
    }

    private void setTileSpatial(int gx, int gy, float altitude, float height, int flags, boolean explicitOverride) {
        if (!isInside(gx, gy)) return;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(packChunk(cx, cy));
        if (chunk == null) return;

        int lx = gx - (cx * chunkSize);
        int ly = gy - (cy * chunkSize);

        if (explicitOverride) {
            chunk.setSpatialOverride(lx, ly, altitude, height, flags);
        } else {
            chunk.setSpatial(lx, ly, altitude, height, flags);
        }
    }

    // ============================================================
    // DIRTY MANAGEMENT
    // ============================================================

    public void markAllChunksContentDirty() {

        IntMap.Values<TileChunk> values = chunks.values();

        while (values.hasNext()) {
            TileChunk chunk = values.next();
            chunk.dirtyState = TileChunk.DirtyState.FULL;
            chunk.dirtyLocalIndices.clear();
            chunk.contentDirty = true;
            chunk.collisionDirty = true;
            chunk.visibleLastFrame = false;
        }
        markVisualBoundsDirty();
    }

    public void markVisualBoundsDirty() {
        visualBoundsDirty = true;
        hasPreviousChunkWindow = false;
    }

    public void setVisualPadding(float left, float right, float top, float bottom) {
        visualPaddingLeft = Math.max(0f, left);
        visualPaddingRight = Math.max(0f, right);
        visualPaddingTop = Math.max(0f, top);
        visualPaddingBottom = Math.max(0f, bottom);
        visualBoundsDirty = false;
    }

    public void rebuildWithNewSize(int newWidth, int newHeight) {

        final class SavedTile {
            final int assetId;
            final byte flags;
            final float altitude;
            final float height;
            final int spatialFlags;
            final boolean spatialOverride;

            SavedTile(int assetId,
                      byte flags,
                      float altitude,
                      float height,
                      int spatialFlags,
                      boolean spatialOverride) {
                this.assetId = assetId;
                this.flags = flags;
                this.altitude = altitude;
                this.height = height;
                this.spatialFlags = spatialFlags;
                this.spatialOverride = spatialOverride;
            }
        }

        IntMap<IntMap<SavedTile>> saved = new IntMap<>();

        IntMap.Values<TileChunk> values = chunks.values();
        while (values.hasNext()) {
            TileChunk chunk = values.next();

            for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                    int asset = chunk.get(lx, ly);
                    byte flags = chunk.getTransformFlags(lx, ly);
                    float altitude = chunk.getAltitude(lx, ly);
                    float height = chunk.getHeight(lx, ly);
                    int spatialFlags = chunk.getSpatialFlags(lx, ly);
                    boolean spatialOverride = chunk.hasSpatialOverride(lx, ly);

                    if (asset == 0 && !spatialOverride) continue;

                    int gx = chunk.chunkX * chunkSize + lx;
                    int gy = chunk.chunkY * chunkSize + ly;

                    IntMap<SavedTile> col = saved.get(gx);
                    if (col == null) {
                        col = new IntMap<>();
                        saved.put(gx, col);
                    }
                    col.put(gy, new SavedTile(asset, flags, altitude, height, spatialFlags, spatialOverride));
                }
            }
        }

        this.mapWidth = newWidth;
        this.mapHeight = newHeight;

        allocateChunksStrict();

        IntMap.Keys xs = saved.keys();
        while (xs.hasNext) {
            int gx = xs.next();
            IntMap<SavedTile> ys = saved.get(gx);

            IntMap.Keys yKeys = ys.keys();
            while (yKeys.hasNext) {
                int gy = yKeys.next();
                SavedTile savedTile = ys.get(gy);

                if (isInside(gx, gy)) {
                    setTile(gx, gy, savedTile.assetId, savedTile.flags);
                    if (savedTile.spatialOverride) {
                        setTileSpatialOverride(gx, gy, savedTile.altitude, savedTile.height, savedTile.spatialFlags);
                    }
                }
            }
        }

        markAllChunksContentDirty();
    }

    // ============================================================
    // COORD CONVERSION
    // ============================================================

    /**
     * Legacy ortho helper.
     * For ISO, callers should use worldToTileX(worldX, worldY).
     */
    public int worldToTileX(float worldX) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return (int) Math.floor((worldX - originX) / tileWidth);
        }
        return worldToTileX(worldX, 0f);
    }

    /**
     * Legacy ortho helper.
     * For ISO, callers should use worldToTileY(worldX, worldY).
     */
    public int worldToTileY(float worldY) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return (int) Math.floor((worldY - originY) / tileHeight);
        }
        return worldToTileY(0f, worldY);
    }

    public int worldToTileX(float worldX, float worldY) {
        return unpackTileX(worldToTilePacked(worldX, worldY));
    }

    public int worldToTileY(float worldX, float worldY) {
        return unpackTileY(worldToTilePacked(worldX, worldY));
    }

    /**
     * Continuous tile-space projection using the exact inverse of tileToWorldX/Y.
     * For ISO this inverts the logical top-left tile transform, not cell containment.
     */
    public float projectWorldToTileX(float worldX, float worldY) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return (worldX - originX) / Math.max(tileWidth, EPSILON);
        }

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;
        if (halfW <= EPSILON || halfH <= EPSILON) return 0f;

        float localX = (worldX - originX) / halfW;
        float localY = (worldY - originY) / halfH;
        return (localY + localX) * 0.5f;
    }

    /**
     * Continuous tile-space projection using the exact inverse of tileToWorldX/Y.
     * For ISO this inverts the logical top-left tile transform, not cell containment.
     */
    public float projectWorldToTileY(float worldX, float worldY) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return (worldY - originY) / Math.max(tileHeight, EPSILON);
        }

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;
        if (halfW <= EPSILON || halfH <= EPSILON) return 0f;

        float localX = (worldX - originX) / halfW;
        float localY = (worldY - originY) / halfH;
        return (localY - localX) * 0.5f;
    }

    private long worldToTilePacked(float worldX, float worldY) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            int gx = (int) Math.floor((worldX - originX) / tileWidth);
            int gy = (int) Math.floor((worldY - originY) / tileHeight);
            return packTile(gx, gy);
        }

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;

        if (halfW <= EPSILON || halfH <= EPSILON) {
            return packTile(0, 0);
        }

        float localX = worldX - originX - halfW;
        float localY = worldY - originY - halfH;

        int approxGX = (int) Math.floor(((localY / halfH) + (localX / halfW)) * 0.5f);
        int approxGY = (int) Math.floor(((localY / halfH) - (localX / halfW)) * 0.5f);

        if (isPointInsideCell(approxGX, approxGY, worldX, worldY)) {
            return packTile(approxGX, approxGY);
        }

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = approxGX + dx;
                int gy = approxGY + dy;
                if (isPointInsideCell(gx, gy, worldX, worldY)) {
                    return packTile(gx, gy);
                }
            }
        }

        return packTile(approxGX, approxGY);
    }

    public void tileToSpriteQuad(int gx, int gy, int spriteW, int spriteH, float[] out8) {
        float logicalX = tileToWorldX(gx, gy);
        float logicalY = tileToWorldY(gx, gy);

        float x = logicalX + (tileWidth - spriteW) * 0.5f;
        float y = logicalY + tileHeight - spriteH;

        float x2 = x + spriteW;
        float y2 = y + spriteH;

        out8[0] = x;
        out8[1] = y;
        out8[2] = x;
        out8[3] = y2;
        out8[4] = x2;
        out8[5] = y2;
        out8[6] = x2;
        out8[7] = y;
    }

    /**
     * Top-left of render rect.
     */
    public float tileToWorldX(int gx) {
        return tileToWorldX(gx, 0);
    }

    /**
     * Top-left of render rect.
     */
    public float tileToWorldY(int gy) {
        return tileToWorldY(0, gy);
    }

    /**
     * Top-left of render rect.
     */
    public float tileToWorldX(int gx, int gy) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return originX + gx * tileWidth;
        }

        float halfW = tileWidth * 0.5f;
        return originX + (gx - gy) * halfW;
    }

    /**
     * Continuous tile-space variant of {@link #tileToWorldX(int, int)}.
     */
    public float tileToWorldX(float gx, float gy) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return originX + gx * tileWidth;
        }

        float halfW = tileWidth * 0.5f;
        return originX + (gx - gy) * halfW;
    }

    /**
     * Top-left of render rect.
     */
    public float tileToWorldY(int gx, int gy) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return originY + gy * tileHeight;
        }

        float halfH = tileHeight * 0.5f;
        return originY + (gx + gy) * halfH;
    }

    /**
     * Continuous tile-space variant of {@link #tileToWorldY(int, int)}.
     */
    public float tileToWorldY(float gx, float gy) {
        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            return originY + gy * tileHeight;
        }

        float halfH = tileHeight * 0.5f;
        return originY + (gx + gy) * halfH;
    }

    /**
     * Axis-aligned render quad for the tile texture.
     * Useful for runtime rendering.
     */
    public void tileToRenderQuad(int gx, int gy, float[] out8) {
        float x = tileToWorldX(gx, gy);
        float y = tileToWorldY(gx, gy);
        float x2 = x + tileWidth;
        float y2 = y + tileHeight;

        out8[0] = x;
        out8[1] = y;
        out8[2] = x;
        out8[3] = y2;
        out8[4] = x2;
        out8[5] = y2;
        out8[6] = x2;
        out8[7] = y;
    }

    /**
     * Logical cell shape.
     * Ortho => rectangle
     * Iso   => diamond
     */
    public void tileToCellVertices(int gx, int gy, float[] out8) {
        float x = tileToWorldX(gx, gy);
        float y = tileToWorldY(gx, gy);

        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            float x2 = x + tileWidth;
            float y2 = y + tileHeight;

            out8[0] = x;
            out8[1] = y;
            out8[2] = x;
            out8[3] = y2;
            out8[4] = x2;
            out8[5] = y2;
            out8[6] = x2;
            out8[7] = y;
            return;
        }

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;

        out8[0] = x + halfW;
        out8[1] = y;
        out8[2] = x;
        out8[3] = y + halfH;
        out8[4] = x + halfW;
        out8[5] = y + tileHeight;
        out8[6] = x + tileWidth;
        out8[7] = y + halfH;
    }

    public boolean isPointInsideCell(int gx, int gy, float worldX, float worldY) {
        if (!isInside(gx, gy)) return false;

        if (projection == SceneMetaRuntime.TiledProjection.ORTHO) {
            float x = tileToWorldX(gx, gy);
            float y = tileToWorldY(gx, gy);
            return worldX >= x
                    && worldX < x + tileWidth
                    && worldY >= y
                    && worldY < y + tileHeight;
        }

        float x = tileToWorldX(gx, gy);
        float y = tileToWorldY(gx, gy);

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;

        float cx = x + halfW;
        float cy = y + halfH;

        float dx = Math.abs(worldX - cx) / Math.max(halfW, EPSILON);
        float dy = Math.abs(worldY - cy) / Math.max(halfH, EPSILON);

        return dx + dy <= 1.0f + EPSILON;
    }

    public boolean isInside(int gx, int gy) {
        return gx >= 0 && gy >= 0 && gx < mapWidth && gy < mapHeight;
    }

    // ============================================================
    // ACCESSORS
    // ============================================================

    public byte getTileTransformFlags(int gx, int gy) {
        if (!isInside(gx, gy)) return TileTransformFlags.NONE;

        TileChunk chunk = chunkForTile(gx, gy);
        if (chunk == null) return TileTransformFlags.NONE;

        int lx = gx - (gx / chunkSize) * chunkSize;
        int ly = gy - (gy / chunkSize) * chunkSize;

        return chunk.getTransformFlags(lx, ly);
    }

    public int slotForTile(int gx, int gy) {
        if (!isInside(gx, gy)) return -1;

        TileChunk chunk = chunkForTile(gx, gy);
        if (chunk == null) return -1;

        int lx = gx - (gx / chunkSize) * chunkSize;
        int ly = gy - (gy / chunkSize) * chunkSize;
        return chunk.slotFor(lx, ly);
    }

    private TileChunk chunkForTile(int gx, int gy) {
        int cx = gx / chunkSize;
        int cy = gy / chunkSize;
        return chunks.get(packChunk(cx, cy));
    }

    public IntMap.Values<TileChunk> getChunks() {
        return chunks.values();
    }

    public TileChunk getChunk(int cx, int cy) {
        return chunks.get(packChunk(cx, cy));
    }

    public int getChunksX() {
        return chunksX;
    }

    public int getChunksY() {
        return chunksY;
    }

    private int packChunk(int cx, int cy) {
        return (cx << 16) ^ (cy & 0xFFFF);
    }

    private long packTile(int gx, int gy) {
        return (((long) gx) << 32) ^ (gy & 0xFFFFFFFFL);
    }

    private int unpackTileX(long packed) {
        return (int) (packed >> 32);
    }

    private int unpackTileY(long packed) {
        return (int) packed;
    }
}
