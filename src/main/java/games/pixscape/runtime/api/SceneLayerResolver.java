package games.pixscape.runtime.api;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerNameComponent;
import games.pixscape.runtime.system.DirtyTrackerSystem;

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
    private final IntSet layerEntityIds = new IntSet();
    private int lastSpatialLookupVisitCount;
    private EntitySubscription members;
    private EntitySubscription namedLayers;
    private EntitySubscription.SubscriptionListener membershipListener;
    private DirtyTrackerSystem dirtyTracker;
    private long indexedLayerRevision;
    private boolean membersDirty = true;
    private final ObjectMap<String, IntArray> membersByName = new ObjectMap<String, IntArray>();
    private final IntArray empty = new IntArray(0);
    private int memberIndexBuildCount;
    private int memberIndexVisitCount;

    public void bind(World world) {
        if (this.world == world) return;
        if (subscription != null && listener != null) {
            subscription.removeSubscriptionListener(listener);
        }
        if (members != null) members.removeSubscriptionListener(membershipListener);
        if (namedLayers != null) namedLayers.removeSubscriptionListener(membershipListener);
        this.world = world;
        layers = null;
        subscription = null;
        listener = null;
        byLayerIndex.clear();
        layerEntityIds.clear();
        members = null;
        namedLayers = null;
        membershipListener = null;
        dirtyTracker = null;
        membersByName.clear();
        membersDirty = true;
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
        dirtyTracker = world.getSystem(DirtyTrackerSystem.class);
        members = world.getAspectSubscriptionManager().get(
                Aspect.all(EntityIndexComponent.class).exclude(LayerComponent.class, GameObjectMemberComponent.class));
        namedLayers = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class, LayerNameComponent.class).exclude(EntityIndexComponent.class));
        membershipListener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                membersDirty = true;
            }

            @Override
            public void removed(IntBag entities) {
                membersDirty = true;
            }
        };
        members.addSubscriptionListener(membershipListener);
        namedLayers.addSubscriptionListener(membershipListener);
        rebuild();
    }

    public void rebuild() {
        membersDirty = true;
        byLayerIndex.clear();
        layerEntityIds.clear();
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
            if (layer != null && layer.spatialEnabled) {
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

    /** Borrowed complete result; only the API snapshot may escape this resolver. */
    IntArray membersByName(String name) {
        if (world == null || name == null) return empty;
        long revision = dirtyTracker != null ? dirtyTracker.layerRevision() : 0;
        if (membersDirty || indexedLayerRevision != revision) {
            rebuildMembersByName();
            indexedLayerRevision = revision;
            membersDirty = false;
        }
        IntArray result = membersByName.get(name);
        return result != null ? result : empty;
    }

    private void rebuildMembersByName() {
        memberIndexBuildCount++;
        membersByName.clear();
        // Cold rebuild only. One content pass includes objects without rendering state.
        IntMap<ObjectSet<String>> namesByIndex = new IntMap<ObjectSet<String>>();
        ComponentMapper<LayerNameComponent> names = world.getMapper(LayerNameComponent.class);
        IntBag layerIds = namedLayers.getEntities();
        for (int i = 0; i < layerIds.size(); i++) {
            memberIndexVisitCount++;
            int entityId = layerIds.get(i);
            String name = names.get(entityId).name;
            if (name == null) continue;
            int index = layers.get(entityId).layerIndex;
            ObjectSet<String> indexNames = namesByIndex.get(index);
            if (indexNames == null) {
                indexNames = new ObjectSet<String>();
                namesByIndex.put(index, indexNames);
            }
            indexNames.add(name);
            if (!membersByName.containsKey(name)) membersByName.put(name, new IntArray(false, 0));
        }
        ComponentMapper<EntityIndexComponent> indexes = world.getMapper(EntityIndexComponent.class);
        IntBag entityIds = members.getEntities();
        for (int i = 0; i < entityIds.size(); i++) {
            memberIndexVisitCount++;
            int entityId = entityIds.get(i);
            ObjectSet<String> indexNames = namesByIndex.get(indexes.get(entityId).layerIndex);
            if (indexNames == null) continue;
            for (String name : indexNames) membersByName.get(name).add(entityId);
        }
    }

    int memberIndexBuildCount() {
        return memberIndexBuildCount;
    }

    int memberIndexVisitCount() {
        return memberIndexVisitCount;
    }

    private void index(int entityId) {
        membersDirty = true;
        if (world == null || !world.getEntityManager().isActive(entityId)) return;
        LayerComponent layer = layers.getSafe(entityId, null);
        if (layer == null) return;
        layerEntityIds.add(entityId);
        add(byLayerIndex, layer.layerIndex, entityId);
    }

    private void unindex(int entityId) {
        membersDirty = true;
        layerEntityIds.remove(entityId);
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
