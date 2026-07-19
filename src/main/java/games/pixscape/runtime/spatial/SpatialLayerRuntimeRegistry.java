package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Stable runtime-cache ownership shared by tiled synchronization and actor composition. */
public final class SpatialLayerRuntimeRegistry {
    private SpatialLayerFaceRuntime[] layers = new SpatialLayerFaceRuntime[4];
    private TiledMapLayerData[] sourceMaps = new TiledMapLayerData[4];
    private int count;

    public SpatialLayerFaceRuntime forLayer(int layerEntity, TiledMapLayerData sourceMap) {
        for (int i = 0; i < count; i++) {
            if (layers[i].layerEntity != layerEntity) continue;
            if (sourceMaps[i] == sourceMap) return layers[i];

            SpatialLayerFaceRuntime replacement = newRuntime(layerEntity);
            layers[i] = replacement;
            sourceMaps[i] = sourceMap;
            return replacement;
        }
        if (count == layers.length) {
            SpatialLayerFaceRuntime[] expanded = new SpatialLayerFaceRuntime[layers.length << 1];
            System.arraycopy(layers, 0, expanded, 0, layers.length);
            layers = expanded;
            TiledMapLayerData[] expandedSources = new TiledMapLayerData[sourceMaps.length << 1];
            System.arraycopy(sourceMaps, 0, expandedSources, 0, sourceMaps.length);
            sourceMaps = expandedSources;
        }
        SpatialLayerFaceRuntime runtime = newRuntime(layerEntity);
        sourceMaps[count] = sourceMap;
        layers[count++] = runtime;
        return runtime;
    }

    private static SpatialLayerFaceRuntime newRuntime(int layerEntity) {
        SpatialLayerFaceRuntime runtime = new SpatialLayerFaceRuntime();
        runtime.layerEntity = layerEntity;
        return runtime;
    }
}
