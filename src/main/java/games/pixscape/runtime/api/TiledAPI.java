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

    /**
     * Returns a tolerant tiled view for a transitional TYPE_TILED host layer index.
     * Ordinary layers, including layers containing one or more Maps, produce an inert reference;
     * callers must use Map entity/stable identity for those Maps. Missing and non-tiled layers produce an inert reference whose
     * {@link TiledLayerRef#exists()} returns {@code false}.
     */
    TiledLayerRef ofLayerIndex(int layerIndex);

    /**
     * Strict compatibility lookup for a transitional TYPE_TILED host layer index.
     * Prefer {@link #requireLayerIndex(int)} when strict acquisition is intended.
     *
     * @throws IllegalArgumentException when the layer is missing, ambiguous, or not tiled
     * @throws IllegalStateException when no Runtime World is loaded
     */
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
     * Strictly resolves a tiled layer index at acquisition time.
     * The returned reference can later become stale and inert.
     *
     * @throws IllegalStateException when the index does not identify a valid tiled capability
     */
    TiledLayerRef requireLayerIndex(int layerIndex);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
