package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.ByteArray;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.tiled.TileTransformFlags;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.Arrays;

public final class TiledLayerComponent extends PooledComponent {

    // Runtime dense structure (NON serialized)
    public transient TiledMapLayerData data;

    public String atlasTag = "main";

    public TiledProjection projection;
    public int tileWidth;
    public int tileHeight;
    public int mapWidthCells;
    public int mapHeightCells;
    public int chunkSize;
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
    public float[] tileAltitudes;
    public float[] tileHeights;
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
        return tileAltitudes != null && index >= 0 && index < tileAltitudes.length
                ? tileAltitudes[index]
                : 0f;
    }

    public float sparseTileHeight(int index) {
        return tileHeights != null && index >= 0 && index < tileHeights.length
                ? tileHeights[index]
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
        return false;
    }

    /** Builds transient map data exclusively from this map's authored configuration. */
    public TiledMapLayerData createMapData() {
        validateMapConfiguration();
        TiledMapLayerData result = new TiledMapLayerData(
                mapWidthCells, mapHeightCells, tileWidth, tileHeight, chunkSize, projection);
        result.originX = originX;
        result.originY = originY;
        result.spatialEnabled = spatialEnabled;
        result.defaultTileAltitude = defaultTileAltitude;
        result.defaultTileHeight = defaultTileHeight;
        return result;
    }

    public void validateMapConfiguration() {
        if (projection == null || tileWidth <= 0 || tileHeight <= 0
                || mapWidthCells <= 0 || mapHeightCells <= 0 || chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "Tiled map configuration requires a projection and positive tile, map, and chunk dimensions.");
        }
    }

    public void ensureSparseSpatialStorage() {
        if (tileAltitudes == null) tileAltitudes = new float[0];
        if (tileHeights == null) tileHeights = new float[0];
        if (tileSpatialFlags == null) tileSpatialFlags = new IntArray();
        if (tileSpatialOverrides == null) tileSpatialOverrides = new ByteArray();

        if (tileAltitudes.length < tileAssetIds.size) tileAltitudes = Arrays.copyOf(tileAltitudes, tileAssetIds.size);
        if (tileHeights.length < tileAssetIds.size) tileHeights = Arrays.copyOf(tileHeights, tileAssetIds.size);
        while (tileSpatialFlags.size < tileAssetIds.size) tileSpatialFlags.add(0);
        while (tileSpatialOverrides.size < tileAssetIds.size) tileSpatialOverrides.add((byte) 0);
    }

    public void setSparseSpatialOverride(int index, float altitude, float height, int flags) {
        if (index < 0 || index >= tileAssetIds.size) return;
        ensureSparseSpatialStorage();
        tileAltitudes[index] = altitude;
        tileHeights[index] = height;
        tileSpatialFlags.set(index, flags);
        tileSpatialOverrides.set(index, (byte) 1);
    }

    @Override
    protected void reset() {
        data = null;
        atlasTag = "main";
        tileXs.clear();
        tileYs.clear();
        tileAssetIds.clear();
        tileTransformFlags.clear();
        tileAltitudes = null;
        tileHeights = null;
        if (tileSpatialFlags != null) tileSpatialFlags.clear();
        if (tileSpatialOverrides != null) tileSpatialOverrides.clear();
        projection = null;
        tileWidth = 0;
        tileHeight = 0;
        mapWidthCells = 0;
        mapHeightCells = 0;
        chunkSize = 0;
        originX = 0;
        originY = 0;
        spatialEnabled = false;
        defaultTileAltitude = 0f;
        defaultTileHeight = 0f;
    }
}
