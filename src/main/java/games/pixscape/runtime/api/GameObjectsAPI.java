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
     * Spawns an exported Game Object by name at the given world offset.
     *
     * @param name Game Object name without the {@code .pixfragment.json} extension
     * @param x    world-space X offset applied to every spawned transform
     * @param y    world-space Y offset applied to every spawned transform
     * @return result containing all created entity IDs
     */
    SpawnResult spawn(String name, float x, float y);

    /**
     * Spawns an already loaded Game Object fragment at the given world offset.
     *
     * @param fragment runtime Game Object fragment to instantiate
     * @param x        world-space X offset applied to every spawned transform
     * @param y        world-space Y offset applied to every spawned transform
     * @return result containing all created entity IDs
     */
    SpawnResult spawnFragment(GameObjectRuntimeFragment fragment, float x, float y);

    /**
     * Spawns a Game Object and returns a reference to the first created entity.
     *
     * <p>If the Game Object creates no entity, the returned reference targets entity {@code -1}.</p>
     *
     * @param name Game Object name without the {@code .pixfragment.json} extension
     * @param x    world-space X offset
     * @param y    world-space Y offset
     * @return reference to the first created entity, or an invalid reference
     */
    EntityRef first(String name, float x, float y);

    /**
     * Spawns a Game Object and returns a reference to the first created entity.
     *
     * @param name Game Object name without the {@code .pixfragment.json} extension
     * @param x    world-space X offset
     * @param y    world-space Y offset
     * @return reference to the first created entity
     * @throws IllegalStateException if the Game Object creates no entity
     */
    EntityRef requireFirst(String name, float x, float y);
}
