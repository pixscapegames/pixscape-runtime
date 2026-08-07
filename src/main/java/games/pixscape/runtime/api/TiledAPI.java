package games.pixscape.runtime.api;

/**
 * High-level API for runtime tiled layers.
 *
 * <p>Layer indices and stable IDs are exported Runtime data. Studio display
 * names are not part of the Runtime layer contract.</p>
 */
public interface TiledAPI {
    /**
     * Returns a tolerant tiled view bound to the current entity incarnation and World.
     */
    TiledLayerRef ofEntityId(int entityId);

    /**
     * Returns a tolerant tiled view bound to the entity currently resolved by stable ID.
     */
    TiledLayerRef ofStableId(int stableId);

    TiledLayerRef ofLayerIndex(int layerIndex);

    TiledLayerRef layer(int layerIndex);

    /**
     * Strictly resolves a tiled entity at acquisition time.
     * The returned reference can later become stale and inert.
     */
    TiledLayerRef requireEntityId(int entityId);

    /**
     * Strictly resolves a tiled stable ID at acquisition time.
     * The returned reference can later become stale and inert.
     */
    TiledLayerRef requireStableId(int stableId);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
