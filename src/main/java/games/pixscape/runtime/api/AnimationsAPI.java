package games.pixscape.runtime.api;

/**
 * Factory and facade access API for sprite animations.
 *
 * <p>Animation spawning resolves either a registered animation definition or a direct atlas
 * asset. In both cases, the backing asset must be present in the current scene atlas through
 * Runtime Availability.</p>
 */
public interface AnimationsAPI {
    /**
     * Creates an animated entity from a registered animation asset id, or from a direct atlas asset.
     *
     * @param assetId registered animation asset id or atlas asset id
     * @param x initial world x position
     * @param y initial world y position
     * @return fluent reference to the created animation entity
     * @throws IllegalArgumentException when the backing asset is not available in the current scene atlas
     */
    AnimationRef spawn(int assetId, float x, float y);

    /**
     * Creates an animated entity from a registered animation name, or from a direct atlas asset name.
     *
     * @param name registered animation name or atlas asset name
     * @param x initial world x position
     * @param y initial world y position
     * @return fluent reference to the created animation entity
     * @throws IllegalArgumentException when the backing asset is not available in the current scene atlas
     */
    AnimationRef spawn(String name, float x, float y);

    /**
     * Returns animation controls for an existing entity.
     *
     * @param entity entity reference to control
     * @return animation facade bound to the entity
     * @throws IllegalArgumentException when {@code entity} is {@code null}
     */
    AnimationFacade get(EntityRef entity);
}
