package games.pixscape.runtime.api;

/**
 * High-level API for runtime tiled layers.
 */
public interface TiledAPI {
    TiledLayerRef ofEntityId(int entityId);
    TiledLayerRef ofStableId(long stableId);
    TiledLayerRef requireEntityId(int entityId);
    TiledLayerRef requireStableId(long stableId);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
