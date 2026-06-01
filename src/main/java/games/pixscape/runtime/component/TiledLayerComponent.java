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

    // -------------------------
    // Sparse persistent storage
    // -------------------------

    public IntArray tileXs = new IntArray();
    public IntArray tileYs = new IntArray();
    public IntArray tileAssetIds = new IntArray();
    public ByteArray tileTransformFlags = new ByteArray();
    public FloatArray tileElevations;
    public FloatArray tileHeights;
    public IntArray tileSpatialFlags;

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
    }

    public float sparseTileElevation(int index) {
        return tileElevations != null && index >= 0 && index < tileElevations.size
                ? tileElevations.get(index)
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

    public void ensureSparseSpatialStorage() {
        if (tileElevations == null) tileElevations = new FloatArray();
        if (tileHeights == null) tileHeights = new FloatArray();
        if (tileSpatialFlags == null) tileSpatialFlags = new IntArray();

        while (tileElevations.size < tileAssetIds.size) tileElevations.add(0f);
        while (tileHeights.size < tileAssetIds.size) tileHeights.add(0f);
        while (tileSpatialFlags.size < tileAssetIds.size) tileSpatialFlags.add(0);
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
        if (tileElevations != null) tileElevations.clear();
        if (tileHeights != null) tileHeights.clear();
        if (tileSpatialFlags != null) tileSpatialFlags.clear();
        mapWidthCells = 100;
        mapHeightCells = 100;
        originX = 0;
        originY = 0;
    }
}
