package games.pixscape.runtime.api;

/**
 * High-level API for runtime tiled layers.
 *
 * <p>Layer indices and stable IDs are exported Runtime data. Studio display
 * names are not part of the Runtime layer contract.</p>
 */
public interface TiledAPI {
    TiledLayerRef ofEntityId(int entityId);

    TiledLayerRef ofStableId(int stableId);

    TiledLayerRef ofLayerIndex(int layerIndex);

    TiledLayerRef layer(int layerIndex);

    TiledLayerRef requireEntityId(int entityId);

    TiledLayerRef requireStableId(int stableId);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
