package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.TagRegistry;

/**
 * Expert ECS access escape hatch.
 *
 * <p>This low-level path coexists with the high-level API and does not replace
 * regular high-level runtime usage.</p>
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
