package games.pixscape.runtime.api;

/**
 * Handle to one tiled layer entity incarnation in one Runtime World.
 * It becomes inert if that entity is removed or the World is replaced.
 */
public interface TiledLayerRef {
    int entityId();

    int stableId();

    /**
     * Returns whether the captured entity is current and still has valid tiled map data.
     */
    boolean exists();

    TiledMapFacade map();

    TileEditFacade tiles();

    /**
     * Spatial render-order settings for this tiled layer and its cells.
     */
    TiledSpatialFacade spatial();

    /**
     * Per-cell animation playback control for this layer.
     *
     * <p>Global animated tile definitions are managed by {@link TiledAnimationsAPI}.</p>
     */
    TileAnimationControlFacade tileAnimations();
}
