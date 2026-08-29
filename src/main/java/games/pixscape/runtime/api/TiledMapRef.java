package games.pixscape.runtime.api;

/**
 * Handle to one Tiled Map entity incarnation in one Runtime World.
 * It becomes inert if that entity is removed or the World is replaced.
 */
public interface TiledMapRef {
    int entityId();

    int stableId();

    /**
     * Returns whether the captured entity is current and still has valid tiled map data.
     */
    boolean exists();

    TiledMapFacade map();

    TileEditFacade tiles();

    /**
     * Spatial render-order settings for this Tiled Map and its cells.
     */
    TiledSpatialFacade spatial();

    /**
     * Per-cell animation playback control for this Map.
     *
     * <p>Global animated tile definitions are managed by {@link TiledAnimationsAPI}.</p>
     */
    TileAnimationControlFacade tileAnimations();
}
