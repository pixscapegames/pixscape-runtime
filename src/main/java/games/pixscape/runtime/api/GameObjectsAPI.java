package games.pixscape.runtime.api;

/**
 * Runtime API for synchronously spawning exported Game Object assets into the active scene.
 *
 * <p>Runtime Availability makes an asset definition available but does not place an instance.
 * Call only after project loading and scene {@code READY}. Accepted names are
 * {@code enemy}, {@code enemy.gameobject}, and the canonical recommended form
 * {@code gameobjects/enemy.gameobject}. The asset file is read synchronously; its visual
 * resources must already have been prepared through the scene's Runtime Availability.
 * Missing or invalid assets, or unavailable visual bindings, fail before publication.</p>
 */
public interface GameObjectsAPI {

    /**
     * Spawns an independent instance with its real root at the given world position.
     * Internal transforms remain local to the hierarchy; the asset definition is not mutated.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X coordinate of the new root
     * @param y    world-space Y coordinate of the new root
     * @return an incarnation-safe handle for the newly spawned instance
     */
    GameObjectInstance spawn(String name, float x, float y);

}
