package games.pixscape.runtime.api;

import com.artemis.World;
import com.artemis.EntityEdit;

/**
 * Runtime hook similar to a HyperLap2D {@code TagTransmuter}.
 * Applied to tagged entities while a scene is being loaded.
 */
@FunctionalInterface
public interface TagInjector {

    /**
     * Applies components or data to the tagged entity.
     *
     * @param world ECS world currently loading the scene
     * @param entityId Artemis entity id receiving injected components
     * @param edit mutable editor for attaching components to the entity
     */
    void apply(World world, int entityId, EntityEdit edit);
}
