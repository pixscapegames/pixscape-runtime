package games.pixscape.runtime.api;

import games.pixscape.runtime.gameobject.GameObjectRuntimeFragment;
import games.pixscape.runtime.gameobject.SpawnResult;

/**
 * Runtime API for spawning exported Game Object fragments into the currently loaded scene.
 *
 * <p>GameObject spawning requires a loaded project and an initialized scene. Spawned
 * entities receive fresh stable IDs and their asset references are resolved against
 * the currently loaded runtime atlases.</p>
 */
public interface GameObjectsAPI {

    /**
     * Spawns a Game Object asset at the given root offset.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X offset applied to the real root
     * @param y    world-space Y offset applied to the real root
     * @return result containing all created entity IDs
     */
    SpawnResult spawn(String name, float x, float y);

    /**
     * Spawns an already loaded real-hierarchy fragment at the given root offset.
     *
     * @param fragment runtime Game Object fragment to instantiate
     * @param x        world-space X offset applied to the real root
     * @param y        world-space Y offset applied to the real root
     * @return result containing all created entity IDs
     */
    SpawnResult spawnFragment(GameObjectRuntimeFragment fragment, float x, float y);

    /**
     * Spawns a Game Object and returns a reference to its real root.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X offset
     * @param y    world-space Y offset
     * @return reference to the real root, or an invalid reference
     */
    EntityRef root(String name, float x, float y);

    /**
     * Spawns a Game Object and returns a reference to its real root.
     *
     * @param name Game Object name or canonical logical asset ID
     * @param x    world-space X offset
     * @param y    world-space Y offset
     * @return reference to the real root
     * @throws IllegalStateException if the Game Object creates no entity
     */
    EntityRef requireRoot(String name, float x, float y);
}
