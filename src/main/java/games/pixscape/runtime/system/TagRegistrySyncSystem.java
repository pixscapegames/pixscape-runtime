package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.service.TagRegistry;

public final class TagRegistrySyncSystem extends BaseSystem {

    private TagRegistry registry;
    private EntitySubscription subscription;

    @Override
    protected void initialize() {
        registry = new TagRegistry(world);
        subscription = world.getAspectSubscriptionManager().get(Aspect.all(PixscapeTagComponent.class));
        subscription.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    registry.syncEntity(data[i]);
                }
            }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    registry.removeEntity(data[i]);
                }
            }
        });
        registry.rebuild();
    }

    @Override
    protected void processSystem() {
        IntBag entities = subscription.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            registry.syncEntity(data[i]);
        }
    }

    public TagRegistry getRegistry() {
        return registry;
    }
}
