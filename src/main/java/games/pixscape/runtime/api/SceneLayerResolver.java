package games.pixscape.runtime.api;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;

/**
 * Indexed resolver for authored scene-layer entities.
 *
 * <p>A scene layer owns {@link LayerComponent} metadata but is not itself a
 * rendered actor with {@link EntityIndexComponent}. Indexes are rebuilt at
 * scene publication and maintained for subscription changes.</p>
 */
final class SceneLayerResolver {
    private World world;
    private ComponentMapper<LayerComponent> layers;
    private EntitySubscription subscription;
    private EntitySubscription.SubscriptionListener listener;
    private final IntMap<IntArray> byLayerIndex = new IntMap<IntArray>();

    public void bind(World world) {
        if (this.world == world) return;
        if (subscription != null && listener != null) {
            subscription.removeSubscriptionListener(listener);
        }
        this.world = world;
        layers = null;
        subscription = null;
        listener = null;
        byLayerIndex.clear();
        if (world == null) return;

        layers = world.getMapper(LayerComponent.class);
        subscription = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class).exclude(EntityIndexComponent.class));
        final World boundWorld = world;
        listener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                if (SceneLayerResolver.this.world != boundWorld) return;
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) index(data[i]);
            }

            @Override
            public void removed(IntBag entities) {
                if (SceneLayerResolver.this.world != boundWorld) return;
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) unindex(data[i]);
            }
        };
        subscription.addSubscriptionListener(listener);
        rebuild();
    }

    public void rebuild() {
        byLayerIndex.clear();
        if (world == null || subscription == null) return;
        IntBag entities = subscription.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) index(data[i]);
    }

    public int requireLayerIndex(int layerIndex) {
        if (findLayerEntityId(layerIndex) < 0) {
            throw new IllegalArgumentException("No scene layer exists for layer index " + layerIndex + ".");
        }
        return layerIndex;
    }

    int findLayerEntityId(int layerIndex) {
        IntArray matches = byLayerIndex.get(layerIndex);
        if (matches == null || matches.size == 0) {
            return -1;
        }
        if (matches.size > 1) {
            throw new IllegalArgumentException("Scene layer index " + layerIndex + " is ambiguous ("
                    + matches.size + " authored scene layers match).");
        }
        return matches.get(0);
    }

    private void index(int entityId) {
        if (world == null || !world.getEntityManager().isActive(entityId)) return;
        LayerComponent layer = layers.getSafe(entityId, null);
        if (layer == null) return;
        add(byLayerIndex, layer.layerIndex, entityId);
    }

    private void unindex(int entityId) {
        removeEntity(byLayerIndex, entityId);
    }

    private static void add(IntMap<IntArray> map, int key, int entityId) {
        IntArray matches = map.get(key);
        if (matches == null) {
            matches = new IntArray();
            map.put(key, matches);
        }
        if (!matches.contains(entityId)) matches.add(entityId);
    }

    private static void removeEntity(IntMap<IntArray> map, int entityId) {
        for (IntMap.Entry<IntArray> entry : map.entries()) entry.value.removeValue(entityId);
    }

}
