package games.pixscape.runtime.api;

/**
 * Runtime spatial render-order API.
 *
 * <p>This high-level API exposes spatial participation and vertical volume only.
 * Authored blocks and compiler/query types remain available separately through the
 * {@code SUPPORTED_EXPERT} Spatial surface; they are not exposed by this facade.</p>
 */
public interface SpatialAPI {
    /**
     * Returns whether at least one runtime layer with the given visual layer index
     * currently participates in spatial render ordering.
     */
    boolean isLayerEnabled(int layerIndex);

    /**
     * Enables or disables spatial render ordering for every runtime layer matching
     * the given visual layer index.
     *
     * <p>This controls layer participation only. Entity volumes and tiled cell
     * volumes are configured through {@link SpatialEntityFacade} and
     * {@link TiledSpatialFacade}.</p>
     */
    SpatialAPI setLayerEnabled(int layerIndex, boolean enabled);
}
