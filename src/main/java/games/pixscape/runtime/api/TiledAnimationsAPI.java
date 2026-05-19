package games.pixscape.runtime.api;

/**
 * Global animated tile definition registry.
 *
 * <p>Definitions are keyed by animated tile asset id and shared by all tiled layers/cells.</p>
 */
public interface TiledAnimationsAPI {
    /**
     * Returns whether an animated tile definition is registered for the asset id.
     *
     * @param animatedTileAssetId asset id used as the animated tile entry point
     * @return {@code true} when a definition exists
     */
    boolean contains(int animatedTileAssetId);

    boolean contains(String name);

    int animationId(String name);

    /**
     * Returns a read-only definition view for the asset id, or {@code null} when absent.
     *
     * <p>The returned {@link TileAnimationDefView} is ephemeral and may be reused internally.
     * Read needed values immediately and do not retain it as a stable snapshot.</p>
     */
    TileAnimationDefView get(int animatedTileAssetId);

    TileAnimationDefView get(String name);

    /**
     * Registers or replaces an animated tile definition.
     *
     * <p>The {@code animatedTileAssetId} and all frame asset ids are runtime asset ids. They should
     * be included in Runtime Availability so the tiled layer can render every frame in the current
     * scene atlas.</p>
     *
     * @param animatedTileAssetId asset id used by map cells to reference the animation
     * @param frameAssetIds ordered frame asset ids
     * @param frameDurationsMs frame durations in milliseconds, aligned with {@code frameAssetIds}
     * @return this registry facade for chaining
     */
    TiledAnimationsAPI put(int animatedTileAssetId, int[] frameAssetIds, int[] frameDurationsMs);

    /**
     * Removes the animated tile definition for an asset id.
     *
     * @param animatedTileAssetId animated tile asset id to remove
     * @return this registry facade for chaining
     */
    TiledAnimationsAPI remove(int animatedTileAssetId);

    /**
     * Removes all runtime animated tile definitions.
     */
    void clear();
}
