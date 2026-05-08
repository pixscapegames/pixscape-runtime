package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.ByteArray;
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

    /**
     * Ensures sparse tiled arrays stay aligned after loading older scenes
     * that may not contain transform flags yet.
     */
    public void ensureSparseTileStorageConsistency() {
        while (tileTransformFlags.size < tileAssetIds.size) {
            tileTransformFlags.add(TileTransformFlags.NONE);
        }
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
        mapWidthCells = 100;
        mapHeightCells = 100;
        originX = 0;
        originY = 0;
    }
}