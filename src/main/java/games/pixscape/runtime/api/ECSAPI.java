package games.pixscape.runtime.api;

import com.artemis.BaseSystem;
import com.artemis.Component;
import com.artemis.ComponentMapper;
import com.artemis.World;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.TagRegistry;

/**
 * Expert escape hatch to raw ECS/runtime services.
 */
public interface ECSAPI {
    World world();
    <T extends Component> ComponentMapper<T> mapper(Class<T> componentType);
    <T extends BaseSystem> T system(Class<T> systemType);

    IdentityRegistry identityRegistry();
    TagRegistry tagRegistry();
}
