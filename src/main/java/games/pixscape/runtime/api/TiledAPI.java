package games.pixscape.runtime.api;

/**
 * High-level API for runtime tiled layers.
 */
public interface TiledAPI {
    TiledLayerRef ofEntityId(int entityId);

    TiledLayerRef ofStableId(int stableId);

    TiledLayerRef ofLayerIndex(int layerIndex);

    TiledLayerRef ofLayerName(String name);

    TiledLayerRef layer(int layerIndex);

    TiledLayerRef layer(String name);

    TiledLayerRef requireEntityId(int entityId);

    TiledLayerRef requireStableId(int stableId);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
