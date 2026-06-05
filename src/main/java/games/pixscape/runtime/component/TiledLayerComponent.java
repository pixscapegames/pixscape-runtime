package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.ByteArray;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledSoaAllocator;

public final class TiledLayerComponent extends PooledComponent {

    // Runtime dense structure (NON serialized)
    public transient TiledMapLayerData data;
    public transient TiledSoaAllocator.Range range;

    public String atlasTag = "main";

    public int tiledStart = 0;
    public int tiledEnd = 0;

    public int mapWidthCells = 100;
    public int mapHeightCells = 100;
    public float originX = 0;
    public float originY = 0;
    public boolean spatialEnabled = false;
    public float defaultTileAltitude = 0f;
    public float defaultTileHeight = 0f;

    // -------------------------
    // Sparse persistent storage
    // -------------------------

    public IntArray tileXs = new IntArray();
    public IntArray tileYs = new IntArray();
    public IntArray tileAssetIds = new IntArray();
    public ByteArray tileTransformFlags = new ByteArray();
    public FloatArray tileAltitudes;
    public FloatArray tileHeights;
    public IntArray tileSpatialFlags;
    public ByteArray tileSpatialOverrides;

    /**
     * Ensures sparse tiled arrays stay aligned after loading older scenes
     * that may not contain transform flags yet.
     */
    public void ensureSparseTileStorageConsistency() {
        if (tileXs == null) tileXs = new IntArray();
        if (tileYs == null) tileYs = new IntArray();
        if (tileAssetIds == null) tileAssetIds = new IntArray();
        if (tileTransformFlags == null) tileTransformFlags = new ByteArray();
        while (tileTransformFlags.size < tileAssetIds.size) {
            tileTransformFlags.add(TileTransformFlags.NONE);
        }
        if (tileSpatialOverrides != null) {
            while (tileSpatialOverrides.size < tileAssetIds.size) tileSpatialOverrides.add((byte) 0);
        }
    }

    public float sparseTileAltitude(int index) {
        return tileAltitudes != null && index >= 0 && index < tileAltitudes.size
                ? tileAltitudes.get(index)
                : 0f;
    }

    public float sparseTileHeight(int index) {
        return tileHeights != null && index >= 0 && index < tileHeights.size
                ? tileHeights.get(index)
                : 0f;
    }

    public int sparseTileSpatialFlags(int index) {
        return tileSpatialFlags != null && index >= 0 && index < tileSpatialFlags.size
                ? tileSpatialFlags.get(index)
                : 0;
    }

    public boolean hasSparseSpatialOverride(int index) {
        if (index < 0) return false;
        if (tileSpatialOverrides != null && index < tileSpatialOverrides.size) {
            return tileSpatialOverrides.get(index) != 0;
        }

        // Migration path for scenes saved before explicit sparse override tracking:
        // non-default authored values still count as overrides, but allocated zero
        // arrays no longer suppress layer defaults.
        return sparseTileAltitude(index) != 0f
                || sparseTileHeight(index) != 0f
                || sparseTileSpatialFlags(index) != 0;
    }

    public void ensureSparseSpatialStorage() {
        if (tileAltitudes == null) tileAltitudes = new FloatArray();
        if (tileHeights == null) tileHeights = new FloatArray();
        if (tileSpatialFlags == null) tileSpatialFlags = new IntArray();
        if (tileSpatialOverrides == null) tileSpatialOverrides = new ByteArray();

        while (tileAltitudes.size < tileAssetIds.size) tileAltitudes.add(0f);
        while (tileHeights.size < tileAssetIds.size) tileHeights.add(0f);
        while (tileSpatialFlags.size < tileAssetIds.size) tileSpatialFlags.add(0);
        while (tileSpatialOverrides.size < tileAssetIds.size) tileSpatialOverrides.add((byte) 0);
    }

    public void setSparseSpatialOverride(int index, float altitude, float height, int flags) {
        if (index < 0 || index >= tileAssetIds.size) return;
        ensureSparseSpatialStorage();
        tileAltitudes.set(index, altitude);
        tileHeights.set(index, height);
        tileSpatialFlags.set(index, flags);
        tileSpatialOverrides.set(index, (byte) 1);
    }

    @Override
    protected void reset() {
        data = null;
        range = null;
        atlasTag = "main";
        tiledStart = 0;
        tiledEnd = 0;
        tileXs.clear();
        tileYs.clear();
        tileAssetIds.clear();
        tileTransformFlags.clear();
        if (tileAltitudes != null) tileAltitudes.clear();
        if (tileHeights != null) tileHeights.clear();
        if (tileSpatialFlags != null) tileSpatialFlags.clear();
        if (tileSpatialOverrides != null) tileSpatialOverrides.clear();
        mapWidthCells = 100;
        mapHeightCells = 100;
        originX = 0;
        originY = 0;
        spatialEnabled = false;
        defaultTileAltitude = 0f;
        defaultTileHeight = 0f;
    }
}
