package games.pixscape.runtime.api;

/**
 * Handle to one tile layer entity incarnation in one Runtime World.
 * It becomes inert if that entity is removed or the World is replaced.
 */
public interface TiledLayerRef {
    int entityId();

    /**
     * Returns the entity's assigned persistent/public identity, or {@code -1} if no identity
     * has been assigned or the captured entity is stale. A live layer may have no stable identity.
     * This read does not create a component, assign an identity, or advance the allocator.
     * Use {@link EntitiesAPI#ensureStableId(int)} to assign an identity explicitly.
     *
     * <p>You should use {@link #exists() ref.exists()} to check whether the referenced entity still exists.
     * For this typed reference, {@code exists()} additionally requires valid tile layer data;
     * the stable ID alone does not indicate that capability.</p>
     */
    int stableId();

    /**
     * Returns whether the captured entity is current and still has valid tile layer data.
     */
    boolean exists();

    TiledLayerFacade layer();

    TileEditFacade tiles();

    /**
     * Spatial render-order settings for this tile layer and its cells.
     */
    TiledSpatialFacade spatial();

    /**
     * Per-cell animation playback control for this layer.
     *
     * <p>Global animated tile definitions are managed by {@link TiledAnimationsAPI}.</p>
     */
    TileAnimationControlFacade tileAnimations();
}
