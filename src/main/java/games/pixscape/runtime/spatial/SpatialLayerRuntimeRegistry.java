package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Scene-owned caches shared by tiled publication and actor composition. Owner access/removal is O(1). */
public final class SpatialLayerRuntimeRegistry {
    private final IntMap<Entry> owners = new IntMap<>();

    public SpatialLayerFaceRuntime forLayer(int layerEntity, TiledMapLayerData sourceMap) {
        Entry entry = owners.get(layerEntity);
        if (entry == null || entry.source != sourceMap) {
            entry = new Entry(sourceMap, layerEntity);
            owners.put(layerEntity, entry);
        }
        return entry.runtime;
    }

    public void remove(int layerEntity) {
        owners.remove(layerEntity);
    }

    public void discardReplacedMap(int layerEntity, TiledMapLayerData map) {
        Entry entry = owners.get(layerEntity);
        if (entry != null && entry.source != map) {
            owners.remove(layerEntity);
        }
    }

    public void clear() {
        owners.clear();
    }

    private static final class Entry {
        final TiledMapLayerData source;
        final SpatialLayerFaceRuntime runtime = new SpatialLayerFaceRuntime();

        Entry(TiledMapLayerData source, int owner) {
            this.source = source;
            runtime.layerEntity = owner;
        }
    }
}
