package games.pixscape.runtime.api;

/**
 * Global animated tile definition registry.
 *
 * <p>Definitions are keyed by animated tile asset id and shared by all tiled layers/cells.</p>
 */
public interface TiledAnimationsAPI {
    boolean contains(int animatedTileAssetId);

    /**
     * Returns a read-only definition view for the asset id, or {@code null} when absent.
     *
     * <p>The returned {@link TileAnimationDefView} is ephemeral and may be reused internally.
     * Read needed values immediately and do not retain it as a stable snapshot.</p>
     */
    TileAnimationDefView get(int animatedTileAssetId);

    TiledAnimationsAPI put(int animatedTileAssetId, int[] frameAssetIds, int[] frameDurationsMs);

    TiledAnimationsAPI remove(int animatedTileAssetId);

    void clear();
}
