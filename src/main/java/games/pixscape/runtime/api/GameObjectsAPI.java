package games.pixscape.runtime.api;

/**
 * Runtime API for synchronously spawning exported Game Object assets into the active scene.
 *
 * <p>Call only after project loading and scene {@code READY}. Accepted names are
 * {@code enemy}, {@code enemy.gameobject}, and the canonical recommended form
 * {@code gameobjects/enemy.gameobject}. The asset file is read synchronously; its visual
 * resources must already have been prepared through the scene's Runtime Availability.
 * Missing or invalid assets, or unavailable visual bindings, fail before publication.</p>
 */
public interface GameObjectsAPI {

    /**
     * Spawns a Game Object asset at the given root offset.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X offset applied to the real root
     * @param y    world-space Y offset applied to the real root
     * @return an incarnation-safe handle for the newly spawned instance
     */
    GameObjectInstance spawn(String name, float x, float y);

    /**
     * Spawns a new Game Object instance and returns its real root.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X offset
     * @param y    world-space Y offset
     * @return reference to the real root, or an invalid reference
     */
    EntityRef root(String name, float x, float y);

    /**
     * Spawns a new Game Object instance and returns its real root.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X offset
     * @param y    world-space Y offset
     * @return reference to the real root
     * @throws IllegalStateException if the Game Object creates no entity
     */
    EntityRef requireRoot(String name, float x, float y);
}
