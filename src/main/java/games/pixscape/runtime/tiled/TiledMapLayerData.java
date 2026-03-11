package games.pixscape.runtime.tiled;

import com.badlogic.gdx.utils.IntMap;

public final class TiledMapLayerData {

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

    // =========================
    // SOA RANGE (injecté par allocator)
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

        this.mapWidth  = mapWidth;
        this.mapHeight = mapHeight;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.chunkSize = chunkSize;
    }

    public TiledMapLayerData() { }

    // ============================================================
    // INITIALISATION RANGE
    // ============================================================

    public void initSlotRange(int layerStart, int layerEnd) {
        this.layerTiledStart = layerStart;
        this.layerTiledEnd   = layerEnd;
        allocateChunksStrict();
    }

    private void allocateChunksStrict() {

        chunks.clear();

        int cursor = layerTiledStart;

        int chunksX = Math.max(1, (mapWidth  + chunkSize - 1) / chunkSize);
        int chunksY = Math.max(1, (mapHeight + chunkSize - 1) / chunkSize);

        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {

                int worldTileX = cx * chunkSize;
                int worldTileY = cy * chunkSize;

                int chunkWidth  = Math.min(chunkSize, mapWidth  - worldTileX);
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
                        worldTileX,
                        worldTileY,
                        originX,
                        originY,
                        tileWidth,
                        tileHeight,
                        cursor
                );

                chunks.put(pack(cx, cy), chunk);

                cursor += requiredSlots;
            }
        }

        // Sécurité stricte
        if (cursor > layerTiledEnd) {
            throw new IllegalStateException("Tiled allocation overflow");
        }
    }

    // ============================================================
    // TILE ACCESS
    // ============================================================

    public int getTile(int gx, int gy) {

        if (!isInside(gx, gy)) return 0;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(pack(cx, cy));
        if (chunk == null) return 0;

        int lx = gx - (cx * chunkSize);
        int ly = gy - (cy * chunkSize);

        return chunk.get(lx, ly);
    }

    public void setTile(int gx, int gy, int assetId) {

        if (!isInside(gx, gy)) return;

        int cx = gx / chunkSize;
        int cy = gy / chunkSize;

        TileChunk chunk = chunks.get(pack(cx, cy));
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

        // 1) Sauvegarde sparse des tiles existantes
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

        // 2) Mettre à jour dimensions
        this.mapWidth  = newWidth;
        this.mapHeight = newHeight;

        // 3) Recréer les chunks (réutilise le même range SOA)
        allocateChunksStrict();

        // 4) Réinjecter tiles valides
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
        // 5) Dirty complet
        markAllChunksContentDirty();
    }

    // ============================================================
    // COORD CONVERSION
    // ============================================================

    public int worldToTileX(float worldX) {
        return (int)Math.floor((worldX - originX) / tileWidth);
    }

    public int worldToTileY(float worldY) {
        return (int)Math.floor((worldY - originY) / tileHeight);
    }

    public float tileToWorldX(int gx) {
        return originX + gx * tileWidth;
    }

    public float tileToWorldY(int gy) {
        return originY + gy * tileHeight;
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

    private int pack(int cx, int cy) {
        return (cx << 16) ^ (cy & 0xFFFF);
    }
}
