package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.TagRegistry;

/**
 * {@code SUPPORTED_EXPERT} ECS access escape hatch.
 *
 * <p>This low-level path coexists with the high-level API and does not replace
 * regular high-level runtime usage. Returned Artemis objects are borrowed from the current
 * Runtime World, are not thread-safe, and must be reacquired after scene/World replacement.
 * Authored component mutations must follow the component's validation and dirty/invalidation
 * contract; direct access does not bypass Runtime synchronization requirements.</p>
 */
public interface ECSAPI {
    /**
     * Underlying Artemis world.
     */
    World world();

    /**
     * Direct component mapper access for expert ECS operations.
     */
    <T extends Component> ComponentMapper<T> mapper(Class<T> componentType);

    /**
     * Direct system lookup for expert ECS operations.
     */
    <T extends BaseSystem> T system(Class<T> systemType);

    /**
     * Runtime stableId/entityId registry access.
     */
    IdentityRegistry identityRegistry();

    /**
     * Runtime tag registry access.
     */
    TagRegistry tagRegistry();
}
