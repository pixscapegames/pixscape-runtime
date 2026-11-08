package games.pixscape.runtime.api;

/**
 * Handle to one Tiled Map entity incarnation in one Runtime World.
 * It becomes inert if that entity is removed or the World is replaced.
 */
public interface TiledMapRef {
    int entityId();

    /**
     * Returns the entity's assigned persistent/public identity, or {@code -1} if no identity
     * has been assigned or the captured entity is stale. A live map may have no stable identity.
     * This read does not create a component, assign an identity, or advance the allocator.
     * Use {@link EntitiesAPI#ensureStableId(int)} to assign an identity explicitly.
     *
     * <p>You should use {@link #exists() ref.exists()} to check whether the referenced entity still exists.
     * For this typed reference, {@code exists()} additionally requires valid tiled map data;
     * the stable ID alone does not indicate that capability.</p>
     */
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
