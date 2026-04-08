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

    public int chunkSize;

    public SceneMetaRuntime.TiledProjection projection =
            SceneMetaRuntime.TiledProjection.ORTHO;

    // =========================
    // SOA RANGE (injected by allocator)
    // =========================

    public int layerTiledStart;
    public int layerTiledEnd;

    // =========================
    // CHUNKS
    // =========================

    private final IntMap<TileChunk> chunks = new IntMap<>();

    public boolean visible = true;
    public boolean collisionEnabled = true;

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

        int chunksX = Math.max(1, (mapWidth + chunkSize - 1) / chunkSize);
        int chunksY = Math.max(1, (mapHeight + chunkSize - 1) / chunkSize);

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

        if (!isInside(gx, gy)) return;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(packChunk(cx, cy));
        if (chunk == null) return;

        int lx = gx - (cx * chunkSize);
        int ly = gy - (cy * chunkSize);

        chunk.set(lx, ly, assetId);
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
    }

    public void rebuildWithNewSize(int newWidth, int newHeight) {

        IntMap<IntMap<Integer>> saved = new IntMap<>();

        IntMap.Values<TileChunk> values = chunks.values();
        while (values.hasNext()) {
            TileChunk chunk = values.next();

            for (int ly = 0; ly < chunk.chunkHeight; ly++) {
                for (int lx = 0; lx < chunk.chunkWidth; lx++) {

                    int asset = chunk.get(lx, ly);
                    if (asset == 0) continue;

                    int gx = chunk.chunkX * chunkSize + lx;
                    int gy = chunk.chunkY * chunkSize + ly;

                    IntMap<Integer> col = saved.get(gx);
                    if (col == null) {
                        col = new IntMap<>();
                        saved.put(gx, col);
                    }
                    col.put(gy, asset);
                }
            }
        }

        this.mapWidth = newWidth;
        this.mapHeight = newHeight;

        allocateChunksStrict();

        IntMap.Keys xs = saved.keys();
        while (xs.hasNext) {
            int gx = xs.next();
            IntMap<Integer> ys = saved.get(gx);

            IntMap.Keys yKeys = ys.keys();
            while (yKeys.hasNext) {
                int gy = yKeys.next();
                int asset = ys.get(gy);

                if (isInside(gx, gy)) {
                    setTile(gx, gy, asset);
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
        float localY = worldY - originY;

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
     * Axis-aligned render quad for the tile texture.
     * Useful for runtime rendering.
     */
    public void tileToRenderQuad(int gx, int gy, float[] out8) {
        float x = tileToWorldX(gx, gy);
        float y = tileToWorldY(gx, gy);
        float x2 = x + tileWidth;
        float y2 = y + tileHeight;

        out8[0] = x;  out8[1] = y;
        out8[2] = x;  out8[3] = y2;
        out8[4] = x2; out8[5] = y2;
        out8[6] = x2; out8[7] = y;
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

            out8[0] = x;  out8[1] = y;
            out8[2] = x;  out8[3] = y2;
            out8[4] = x2; out8[5] = y2;
            out8[6] = x2; out8[7] = y;
            return;
        }

        float halfW = tileWidth * 0.5f;
        float halfH = tileHeight * 0.5f;

        out8[0] = x + halfW;    out8[1] = y;
        out8[2] = x;            out8[3] = y + halfH;
        out8[4] = x + halfW;    out8[5] = y + tileHeight;
        out8[6] = x + tileWidth;out8[7] = y + halfH;
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

    public IntMap.Values<TileChunk> getChunks() {
        return chunks.values();
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