package games.pixscape.runtime.api;

import com.artemis.Aspect;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;

/**
 * World-local generations used to reject stale runtime entity handles after
 * Artemis recycles an entity ID.
 */
final class EntityLifecycleTracker {
    private World world;
    private EntitySubscription subscription;
    private EntitySubscription.SubscriptionListener listener;
    private int[] generations = new int[16];

    public int capture(World world, int entityId) {
        bind(world);
        ensureCapacity(entityId);
        return entityId >= 0 ? generations[entityId] : -1;
    }

    public boolean matches(World world, int entityId, int generation) {
        return this.world == world
                && entityId >= 0
                && entityId < generations.length
                && generations[entityId] == generation;
    }

    public void bind(World world) {
        if (this.world == world) return;
        if (subscription != null && listener != null) {
            subscription.removeSubscriptionListener(listener);
        }
        this.world = world;
        subscription = null;
        listener = null;
        generations = new int[16];
        if (world == null) return;

        final World boundWorld = world;
        subscription = world.getAspectSubscriptionManager().get(Aspect.all());
        listener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                // A generation changes when the previous occupant is removed.
            }

            @Override
            public void removed(IntBag entities) {
                if (EntityLifecycleTracker.this.world != boundWorld) return;
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int entityId = data[i];
                    ensureCapacity(entityId);
                    generations[entityId]++;
                }
            }
        };
        subscription.addSubscriptionListener(listener);
    }

    private void ensureCapacity(int entityId) {
        if (entityId < generations.length) return;
        int next = generations.length;
        while (next <= entityId) next <<= 1;
        int[] expanded = new int[next];
        System.arraycopy(generations, 0, expanded, 0, generations.length);
        generations = expanded;
    }
}
