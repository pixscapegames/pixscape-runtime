package games.pixscape.runtime.spatial;

/** Mutable runtime caches owned by one Tiled Map entity. */
public final class SpatialLayerFaceRuntime {
    public int layerEntity = -1;
    public final SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
    public final SpatialProjectedFaceCache projected = new SpatialProjectedFaceCache();
    public final SpatialTileOrderCache tileOrder = new SpatialTileOrderCache();
    public Object failedSource;
    public int failedSourceRevision = Integer.MIN_VALUE;
}
