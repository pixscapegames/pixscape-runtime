package games.pixscape.runtime.api;

import com.artemis.io.SaveFileFormat;
import games.pixscape.runtime.prefab.SpawnResult;

/**
 * Runtime API for spawning exported prefab fragments into the currently loaded scene.
 *
 * <p>Prefab spawning requires a loaded project and an initialized scene. Spawned
 * entities receive fresh stable IDs and their asset references are resolved against
 * the currently loaded runtime atlases.</p>
 */
public interface PrefabsAPI {

    /**
     * Spawns an exported prefab by name at the given world offset.
     *
     * @param name prefab name without the {@code .pixfragment.json} extension
     * @param x world-space X offset applied to every spawned transform
     * @param y world-space Y offset applied to every spawned transform
     * @return result containing all created entity IDs
     */
    SpawnResult spawn(String name, float x, float y);

    /**
     * Spawns an already loaded prefab fragment at the given world offset.
     *
     * @param fragment Artemis save fragment to instantiate
     * @param x world-space X offset applied to every spawned transform
     * @param y world-space Y offset applied to every spawned transform
     * @return result containing all created entity IDs
     */
    SpawnResult spawnFragment(SaveFileFormat fragment, float x, float y);

    /**
     * Spawns a prefab and returns a reference to the first created entity.
     *
     * <p>If the prefab creates no entity, the returned reference targets entity {@code -1}.</p>
     *
     * @param name prefab name without the {@code .pixfragment.json} extension
     * @param x world-space X offset
     * @param y world-space Y offset
     * @return reference to the first created entity, or an invalid reference
     */
    EntityRef first(String name, float x, float y);

    /**
     * Spawns a prefab and returns a reference to the first created entity.
     *
     * @param name prefab name without the {@code .pixfragment.json} extension
     * @param x world-space X offset
     * @param y world-space Y offset
     * @return reference to the first created entity
     * @throws IllegalStateException if the prefab creates no entity
     */
    EntityRef requireFirst(String name, float x, float y);
}
