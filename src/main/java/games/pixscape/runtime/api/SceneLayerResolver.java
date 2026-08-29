package games.pixscape.runtime.api;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TiledLayerComponent;

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
    private EntitySubscription tiledMapsSubscription;
    private EntitySubscription.SubscriptionListener tiledMapsListener;
    private final IntMap<IntArray> byLayerIndex = new IntMap<IntArray>();
    private final IntMap<IntArray> tiledMapsByLayerIndex = new IntMap<IntArray>();
    private final IntSet layerEntityIds = new IntSet();
    private int lastSpatialLookupVisitCount;

    public void bind(World world) {
        if (this.world == world) return;
        if (subscription != null && listener != null) {
            subscription.removeSubscriptionListener(listener);
        }
        if (tiledMapsSubscription != null && tiledMapsListener != null) {
            tiledMapsSubscription.removeSubscriptionListener(tiledMapsListener);
        }
        this.world = world;
        layers = null;
        subscription = null;
        listener = null;
        tiledMapsSubscription = null;
        tiledMapsListener = null;
        byLayerIndex.clear();
        tiledMapsByLayerIndex.clear();
        layerEntityIds.clear();
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
        tiledMapsSubscription = world.getAspectSubscriptionManager().get(
                Aspect.all(TiledLayerComponent.class, EntityIndexComponent.class)
                        .exclude(LayerComponent.class));
        tiledMapsListener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                if (SceneLayerResolver.this.world != boundWorld) return;
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) indexTiledMap(data[i]);
            }

            @Override
            public void removed(IntBag entities) {
                if (SceneLayerResolver.this.world != boundWorld) return;
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) unindexTiledMap(data[i]);
            }
        };
        tiledMapsSubscription.addSubscriptionListener(tiledMapsListener);
        rebuild();
    }

    public void rebuild() {
        byLayerIndex.clear();
        tiledMapsByLayerIndex.clear();
        layerEntityIds.clear();
        if (world == null || subscription == null) return;
        IntBag entities = subscription.getEntities();
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) index(data[i]);
        IntBag tiledMaps = tiledMapsSubscription.getEntities();
        int[] tiledData = tiledMaps.getData();
        for (int i = 0, n = tiledMaps.size(); i < n; i++) indexTiledMap(tiledData[i]);
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

    int findTiledMapEntityId(int layerIndex) {
        IntArray matches = tiledMapsByLayerIndex.get(layerIndex);
        if (matches == null || matches.size == 0) return -1;
        if (matches.size > 1) {
            throw new IllegalArgumentException("Tiled host layer index " + layerIndex
                    + " is ambiguous (" + matches.size + " Tiled maps match).");
        }
        return matches.get(0);
    }

    boolean isLayerEntityId(int entityId) {
        return entityId >= 0 && layerEntityIds.contains(entityId);
    }

    boolean isLayerSpatialEnabled(int layerIndex) {
        IntArray matches = byLayerIndex.get(layerIndex);
        lastSpatialLookupVisitCount = 0;
        if (matches == null) return false;
        for (int i = 0, n = matches.size; i < n; i++) {
            lastSpatialLookupVisitCount++;
            LayerComponent layer = layers.getSafe(matches.get(i), null);
            if (layer != null && layer.spatialEnabled) return true;
        }
        return false;
    }

    boolean isActorSpatialLayerEnabled(int layerIndex) {
        IntArray matches = byLayerIndex.get(layerIndex);
        lastSpatialLookupVisitCount = 0;
        if (matches == null) return false;
        for (int i = 0, n = matches.size; i < n; i++) {
            lastSpatialLookupVisitCount++;
            LayerComponent layer = layers.getSafe(matches.get(i), null);
            if (layer != null
                    && layer.type == LayerComponent.TYPE_CLASSIC
                    && layer.spatialEnabled) {
                return true;
            }
        }
        return false;
    }

    void setLayerSpatialEnabled(int layerIndex, boolean enabled) {
        IntArray matches = byLayerIndex.get(layerIndex);
        if (matches == null) return;
        for (int i = 0, n = matches.size; i < n; i++) {
            LayerComponent layer = layers.getSafe(matches.get(i), null);
            if (layer != null) layer.spatialEnabled = enabled;
        }
    }

    int matchingLayerCount(int layerIndex) {
        IntArray matches = byLayerIndex.get(layerIndex);
        return matches != null ? matches.size : 0;
    }

    int lastSpatialLookupVisitCount() {
        return lastSpatialLookupVisitCount;
    }

    private void index(int entityId) {
        if (world == null || !world.getEntityManager().isActive(entityId)) return;
        LayerComponent layer = layers.getSafe(entityId, null);
        if (layer == null) return;
        layerEntityIds.add(entityId);
        add(byLayerIndex, layer.layerIndex, entityId);
    }

    private void unindex(int entityId) {
        layerEntityIds.remove(entityId);
        removeEntity(byLayerIndex, entityId);
    }

    private void indexTiledMap(int entityId) {
        if (world == null || !world.getEntityManager().isActive(entityId)) return;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).getSafe(entityId, null);
        if (index != null) add(tiledMapsByLayerIndex, index.layerIndex, entityId);
    }

    private void unindexTiledMap(int entityId) {
        removeEntity(tiledMapsByLayerIndex, entityId);
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
