package games.pixscape.runtime.api;

/**
 * Factory and facade access API for sprite animations.
 *
 * <p>Animation spawning requires a registered animation definition whose backing visual asset
 * is present in the current scene atlas through Runtime Availability.</p>
 */
public interface AnimationsAPI {
    /**
     * Creates an animated entity from a registered animation asset id.
     *
     * @param assetId registered animation asset id
     * @param x initial world x position
     * @param y initial world y position
     * @return fluent reference to the created animation entity
     * @throws IllegalArgumentException when the animation is unknown or unavailable
     */
    AnimationRef spawn(int assetId, float x, float y);

    /**
     * Creates an animated entity from a registered animation name.
     *
     * @param name registered animation name
     * @param x initial world x position
     * @param y initial world y position
     * @return fluent reference to the created animation entity
     * @throws IllegalArgumentException when the animation is unknown or unavailable
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
