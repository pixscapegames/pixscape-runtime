package games.pixscape.runtime.api;

/**
 * Handle to one tiled layer entity.
 */
public interface TiledLayerRef {
    int entityId();

    int stableId();

    boolean exists();

    TiledMapFacade map();

    TileEditFacade tiles();

    /**
     * Per-cell animation playback control for this layer.
     *
     * <p>Global animated tile definitions are managed by {@link TiledAnimationsAPI}.</p>
     */
    TileAnimationControlFacade tileAnimations();
}
