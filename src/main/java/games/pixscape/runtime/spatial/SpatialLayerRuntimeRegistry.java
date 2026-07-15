package games.pixscape.runtime.spatial;

/** Stable runtime-cache ownership shared by tiled synchronization and actor composition. */
public final class SpatialLayerRuntimeRegistry {
    private SpatialLayerFaceRuntime[] layers = new SpatialLayerFaceRuntime[4];
    private int count;

    public SpatialLayerFaceRuntime forLayer(int layerEntity) {
        for (int i = 0; i < count; i++) {
            if (layers[i].layerEntity == layerEntity) return layers[i];
        }
        if (count == layers.length) {
            SpatialLayerFaceRuntime[] expanded = new SpatialLayerFaceRuntime[layers.length << 1];
            System.arraycopy(layers, 0, expanded, 0, layers.length);
            layers = expanded;
        }
        SpatialLayerFaceRuntime runtime = new SpatialLayerFaceRuntime();
        runtime.layerEntity = layerEntity;
        layers[count++] = runtime;
        return runtime;
    }
}
