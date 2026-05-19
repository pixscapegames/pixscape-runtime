package games.pixscape.runtime.api;

/**
 * Factory API for runtime sprite entities.
 *
 * <p>Spawned sprites use assets from the current scene atlas. The asset must be included in
 * Runtime Availability before export, otherwise spawning fails with an
 * {@link IllegalArgumentException}.</p>
 */
public interface SpritesAPI {
    /**
     * Creates a sprite entity from an asset id at world position {@code (x, y)}.
     *
     * @param assetId exported asset id to use for the sprite region
     * @param x initial world x position
     * @param y initial world y position
     * @return fluent reference to the created sprite entity
     * @throws IllegalArgumentException when the asset is not available in the current scene atlas
     */
    SpriteRef spawn(int assetId, float x, float y);

    /**
     * Creates a sprite entity from an asset name at world position {@code (x, y)}.
     *
     * @param name exported asset name to use for the sprite region
     * @param x initial world x position
     * @param y initial world y position
     * @return fluent reference to the created sprite entity
     * @throws IllegalArgumentException when the asset is not available in the current scene atlas
     */
    SpriteRef spawn(String name, float x, float y);
}
