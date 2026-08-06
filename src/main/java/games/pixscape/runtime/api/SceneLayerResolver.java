package games.pixscape.runtime.api;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;

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
    private ComponentMapper<PixscapeIdentityComponent> identities;
    private EntitySubscription subscription;
    private EntitySubscription.SubscriptionListener listener;
    private final IntMap<IntArray> byLayerIndex = new IntMap<IntArray>();
    private final ObjectMap<String, IntArray> byName = new ObjectMap<String, IntArray>();

    public void bind(World world) {
        if (this.world == world) return;
        if (subscription != null && listener != null) {
            subscription.removeSubscriptionListener(listener);
        }
        this.world = world;
        layers = null;
        identities = null;
        subscription = null;
        listener = null;
        byLayerIndex.clear();
        byName.clear();
        if (world == null) return;

        layers = world.getMapper(LayerComponent.class);
        identities = world.getMapper(PixscapeIdentityComponent.class);
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
        byName.clear();
        if (world == null || subscription == null) return;
        IntBag entities = subscription.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) index(data[i]);
    }

    public int requireLayerIndex(int layerIndex) {
        IntArray matches = byLayerIndex.get(layerIndex);
        if (matches == null || matches.size == 0) {
            throw new IllegalArgumentException("No scene layer exists for layer index " + layerIndex + ".");
        }
        if (matches.size > 1) {
            throw new IllegalArgumentException("Scene layer index " + layerIndex + " is ambiguous ("
                    + matches.size + " scene layers match).");
        }
        return layerIndex;
    }

    public int requireLayerName(String layerName) {
        String normalized = normalizeLookupName(layerName);
        if (isBlank(normalized)) {
            throw new IllegalArgumentException("Scene layer name must not be blank.");
        }
        IntArray matches = byName.get(normalized);
        if (matches == null || matches.size == 0) {
            throw new IllegalArgumentException("No scene layer exists for name '" + layerName + "'.");
        }
        if (matches.size > 1) {
            throw new IllegalArgumentException("Scene layer name '" + layerName + "' is ambiguous ("
                    + matches.size + " scene layers match). Use layerIndex(int) instead.");
        }
        LayerComponent layer = layers.getSafe(matches.get(0), null);
        if (layer == null) {
            throw new IllegalStateException("Resolved scene layer no longer has LayerComponent: entityId="
                    + matches.get(0) + ".");
        }
        return layer.layerIndex;
    }

    private void index(int entityId) {
        if (world == null || !world.getEntityManager().isActive(entityId)) return;
        LayerComponent layer = layers.getSafe(entityId, null);
        if (layer == null) return;
        add(byLayerIndex, layer.layerIndex, entityId);
        PixscapeIdentityComponent identity = identities.getSafe(entityId, null);
        String name = identity != null ? normalizeLookupName(identity.name) : null;
        if (!isBlank(name)) add(byName, name, entityId);
    }

    private void unindex(int entityId) {
        removeEntity(byLayerIndex, entityId);
        removeEntity(byName, entityId);
    }

    private static void add(IntMap<IntArray> map, int key, int entityId) {
        IntArray matches = map.get(key);
        if (matches == null) {
            matches = new IntArray();
            map.put(key, matches);
        }
        if (!matches.contains(entityId)) matches.add(entityId);
    }

    private static void add(ObjectMap<String, IntArray> map, String key, int entityId) {
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

    private static void removeEntity(ObjectMap<String, IntArray> map, int entityId) {
        for (ObjectMap.Entry<String, IntArray> entry : map.entries()) entry.value.removeValue(entityId);
    }

    private static String normalizeLookupName(String name) {
        if (name == null) return null;
        String normalized = name.trim();
        if (normalized.length() == 0) return null;
        normalized = normalized.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) normalized = normalized.substring(slash + 1);
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) normalized = normalized.substring(0, dot);
        return normalized;
    }

    private static boolean isBlank(String value) {
        if (value == null || value.length() == 0) return true;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isWhitespace(value.charAt(i))) return false;
        }
        return true;
    }
}
