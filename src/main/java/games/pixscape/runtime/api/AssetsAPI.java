package games.pixscape.runtime.api;

/**
 * Lookup API for assets exported into the current scene atlas.
 *
 * <p>The runtime only knows about assets included in the scene's Runtime Availability
 * set during export. Methods that resolve an asset throw when the requested asset is not
 * present in the current scene atlas; use {@code contains(...)} to probe first.</p>
 */
public interface AssetsAPI {
    /**
     * Resolves an exported atlas region by its logical name.
     *
     * <p>Names are matched against the normalized atlas region name, without directory and
     * extension suffixes.</p>
     *
     * @param name asset name to resolve
     * @return resolved region metadata and texture region
     * @throws IllegalArgumentException when the asset is not available in the current scene atlas
     */
    AssetRegionRef region(String name);

    /**
     * Resolves an exported atlas region by asset id.
     *
     * @param assetId asset id to resolve
     * @return resolved region metadata and texture region
     * @throws IllegalArgumentException when the asset is not available in the current scene atlas
     */
    AssetRegionRef region(int assetId);

    /**
     * Returns whether an asset name can be resolved in the current scene atlas.
     *
     * @param name asset name to test
     * @return {@code true} when the asset is available for this scene
     */
    boolean contains(String name);

    /**
     * Returns whether an asset id can be resolved in the current scene atlas.
     *
     * @param assetId asset id to test
     * @return {@code true} when the asset is available for this scene
     */
    boolean contains(int assetId);
}
