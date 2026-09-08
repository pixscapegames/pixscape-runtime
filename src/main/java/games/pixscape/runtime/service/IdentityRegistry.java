package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;

/**
 * {@code SUPPORTED_EXPERT} World-scoped identity index and stable-ID allocator.
 * <p>
 * {@link PixscapeIdentityComponent} stores assigned identities, while
 * {@link SceneMetaRuntime#nextEntityStableId} stores the persistent high-water mark.
 * This registry maintains the World indexes and allocates new IDs from that mark.
 * It must be rebound after World replacement and is not thread-safe.
 *
 * <p>Single lookups are O(1) average after binding/rebuild. Collection-returning lookup methods
 * return caller-owned snapshots; overloads accepting an output collection reuse caller storage.</p>
 */
public final class IdentityRegistry {

    public static final int UNASSIGNED_STABLE_ID = -1;
    public static final String DEFAULT_NAME = "unnamed";

    private static final ObjectMap<World, IdentityRegistry> REGISTRIES_BY_WORLD = new ObjectMap<>();

    /** Runtime-internal access to the registry already bound to an ECS World. */
    public static IdentityRegistry boundTo(World world) {
        return world != null ? REGISTRIES_BY_WORLD.get(world) : null;
    }

    private World world;
    private SceneMetaRuntime sceneMeta;
    private ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private EntitySubscription subscription;
    private EntitySubscription.SubscriptionListener subscriptionListener;

    /**
     * Internal unique index: stableId -> entityId.
     */
    private final IntMap<Integer> byStableId = new IntMap<>();

    /**
     * Internal non-unique index: name -> entityIds.
     */
    private final ObjectMap<String, IntArray> byName = new ObjectMap<>();

    /**
     * Reverse indexes for efficient entity-local updates.
     */
    private final IntMap<Integer> stableIdByEntity = new IntMap<>();
    private final IntMap<String> nameByEntity = new IntMap<>();

    public IdentityRegistry() {
    }

    public void bind(World world, SceneMetaRuntime sceneMeta) {
        if (this.world == world && this.sceneMeta == sceneMeta) return;

        requireWorldAvailable(world);
        if (world == null && sceneMeta != null) {
            throw new IllegalArgumentException("Scene metadata cannot be bound without a World.");
        }
        detachSubscriptionListener();
        unregisterBoundWorld();

        byStableId.clear();
        byName.clear();
        stableIdByEntity.clear();
        nameByEntity.clear();

        this.world = world;
        this.sceneMeta = sceneMeta;
        this.mIdentity = null;
        this.subscription = null;
        this.subscriptionListener = null;

        if (world == null) return;

        registerBoundWorld(world);

        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
        this.subscription = world.getAspectSubscriptionManager().get(Aspect.all(PixscapeIdentityComponent.class));
        final World boundWorld = world;
        this.subscriptionListener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                if (IdentityRegistry.this.world != boundWorld) return;

                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    indexEntityFromComponent(data[i]);
                }
            }

            @Override
            public void removed(IntBag entities) {
                if (IdentityRegistry.this.world != boundWorld) return;

                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    unindexEntity(data[i]);
                }
            }
        };
        this.subscription.addSubscriptionListener(this.subscriptionListener);
    }

    private void detachSubscriptionListener() {
        if (subscription != null && subscriptionListener != null) {
            subscription.removeSubscriptionListener(subscriptionListener);
        }
    }

    private void registerBoundWorld(World world) {
        IdentityRegistry existing = REGISTRIES_BY_WORLD.get(world);
        if (existing != null && existing != this) {
            throw new IllegalStateException(
                    "World already has a different IdentityRegistry bound.");
        }
        REGISTRIES_BY_WORLD.put(world, this);
    }

    private void requireWorldAvailable(World world) {
        if (world == null) return;

        IdentityRegistry existing = REGISTRIES_BY_WORLD.get(world);
        if (existing != null && existing != this) {
            throw new IllegalStateException(
                    "World already has a different IdentityRegistry bound.");
        }
    }

    private void unregisterBoundWorld() {
        if (this.world == null) return;

        IdentityRegistry registered = REGISTRIES_BY_WORLD.get(this.world);
        if (registered == this) {
            REGISTRIES_BY_WORLD.remove(this.world);
        }
    }

    public static void unindexEntityImmediately(World world, int eid) {
        if (world == null || eid < 0) return;

        IdentityRegistry registry = REGISTRIES_BY_WORLD.get(world);
        if (registry != null) {
            registry.unindexEntityImmediately(eid);
        }
    }

    public void rebuild() {
        byStableId.clear();
        byName.clear();
        stableIdByEntity.clear();
        nameByEntity.clear();
        if (world == null || subscription == null) return;

        IntBag bag = subscription.getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            indexEntityFromComponent(eid);
        }

    }

    public int allocateStableId() {
        requireSceneMeta();
        int allocated = sceneMeta.nextEntityStableId;
        validateAllocatableStableId(allocated);
        sceneMeta.nextEntityStableId++;
        return allocated;
    }

    public int ensureStableId(int eid) {
        if (!isEntityActive(eid)) return UNASSIGNED_STABLE_ID;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        int current = c.stableId;

        if (current != UNASSIGNED_STABLE_ID) {
            validateRestoredStableId(current);
            Integer existing = byStableId.get(current);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(duplicateStableIdMessage(current, eid, existing));
            }
            byStableId.put(current, eid);
            stableIdByEntity.put(eid, current);
            return current;
        }

        int allocated = allocateStableId();
        c.stableId = allocated;

        String normalizedName = normalizeName(c.name);
        c.name = normalizedName;

        byStableId.put(allocated, eid);
        stableIdByEntity.put(eid, allocated);

        replaceNameIndex(eid, normalizedName);

        return allocated;
    }

    public boolean hasStableId(int stableId) {
        return stableId != UNASSIGNED_STABLE_ID && byStableId.containsKey(stableId);
    }

    public int findByStableId(int stableId) {
        if (stableId == UNASSIGNED_STABLE_ID || world == null) return -1;

        Integer eid = byStableId.get(stableId);
        if (eid == null) return -1;

        return world.getEntityManager().isActive(eid) ? eid : -1;
    }

    public int getStableId(int eid) {
        if (eid < 0 || mIdentity == null) return UNASSIGNED_STABLE_ID;

        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return UNASSIGNED_STABLE_ID;

        return c.stableId;
    }

    public String getName(int eid) {
        if (eid < 0 || mIdentity == null) return DEFAULT_NAME;

        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return DEFAULT_NAME;

        return normalizeName(c.name);
    }

    public boolean hasName(int eid, String name) {
        if (eid < 0 || mIdentity == null) return false;

        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return false;

        return normalizeName(c.name).equals(normalizeName(name));
    }

    public IntArray getByName(String name) {
        String normalized = normalizeName(name);

        IntArray bucket = byName.get(normalized);
        if (bucket == null || bucket.size == 0) return new IntArray();

        IntArray out = new IntArray(bucket.size);
        out.addAll(bucket);
        return out;
    }

    public void getByName(String name, IntArray out) {
        if (out == null) return;
        out.clear();

        String normalized = normalizeName(name);
        IntArray bucket = byName.get(normalized);
        if (bucket == null || bucket.size == 0) return;

        out.addAll(bucket);
    }

    public int firstByName(String name) {
        if (world == null) return -1;

        String normalized = normalizeName(name);

        IntArray bucket = byName.get(normalized);
        if (bucket == null || bucket.size == 0) return -1;

        for (int i = 0; i < bucket.size; i++) {
            int eid = bucket.get(i);
            if (world.getEntityManager().isActive(eid)) return eid;
        }
        return -1;
    }

    public Array<String> allNames() {
        Array<String> out = new Array<>(byName.size);
        for (ObjectMap.Entries<String, IntArray> it = byName.entries(); it.hasNext(); ) {
            ObjectMap.Entry<String, IntArray> entry = it.next();
            out.add(entry.key);
        }
        return out;
    }

    public void setStableId(int eid, int stableId) {
        if (!isEntityActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        int oldStableId = c.stableId;

        if (oldStableId == stableId) {
            if (stableId != UNASSIGNED_STABLE_ID) {
                byStableId.put(stableId, eid);
                stableIdByEntity.put(eid, stableId);
            }
            return;
        }

        if (stableId != UNASSIGNED_STABLE_ID) {
            validateRestoredStableId(stableId);
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(duplicateStableIdMessage(stableId, eid, existing));
            }
        }

        unindexStableId(eid);

        c.stableId = stableId;

        if (stableId != UNASSIGNED_STABLE_ID) {
            byStableId.put(stableId, eid);
            stableIdByEntity.put(eid, stableId);
        }
    }

    public void setName(int eid, String name) {
        if (!isEntityActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        String normalized = normalizeName(name);

        c.name = normalized;
        replaceNameIndex(eid, normalized);
    }

    public void setIdentity(int eid, int stableId, String name) {
        if (!isEntityActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        String normalizedName = normalizeName(name);

        int oldStableId = c.stableId;
        if (oldStableId != stableId) {
            if (stableId != UNASSIGNED_STABLE_ID) {
                validateRestoredStableId(stableId);
                Integer existing = byStableId.get(stableId);
                if (existing != null && existing.intValue() != eid) {
                    throw new IllegalStateException(duplicateStableIdMessage(stableId, eid, existing));
                }
            }
            unindexStableId(eid);
            c.stableId = stableId;
            if (stableId != UNASSIGNED_STABLE_ID) {
                byStableId.put(stableId, eid);
                stableIdByEntity.put(eid, stableId);
            }
        }

        c.name = normalizedName;
        replaceNameIndex(eid, normalizedName);
    }

    public void clearIdentity(int eid) {
        setIdentity(eid, UNASSIGNED_STABLE_ID, DEFAULT_NAME);
    }

    public void removeIdentity(int eid) {
        if (eid < 0 || world == null || mIdentity == null) return;

        unindexEntity(eid);

        if (world.getEntityManager().isActive(eid) && mIdentity.has(eid)) {
            mIdentity.remove(eid);
        }
    }

    private void indexEntityFromComponent(int eid) {
        if (!isEntityActive(eid) || !mIdentity.has(eid)) return;

        PixscapeIdentityComponent c = mIdentity.get(eid);
        int stableId = c.stableId;
        String normalizedName = normalizeName(c.name);
        c.name = normalizedName;

        unindexEntity(eid);

        if (stableId != UNASSIGNED_STABLE_ID) {
            validateRestoredStableId(stableId);
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(duplicateStableIdMessage(stableId, eid, existing));
            }

            byStableId.put(stableId, eid);
            stableIdByEntity.put(eid, stableId);

        }

        nameByEntity.put(eid, normalizedName);
        indexName(normalizedName, eid);
    }

    private void unindexEntity(int eid) {
        unindexStableId(eid);

        String oldName = nameByEntity.remove(eid);
        if (oldName != null) {
            unindexName(oldName, eid);
        }
    }

    private void unindexEntityImmediately(int eid) {
        if (eid < 0 || world == null || mIdentity == null) return;

        PixscapeIdentityComponent identity = mIdentity.getSafe(eid, null);
        int stableId = identity != null ? identity.stableId : UNASSIGNED_STABLE_ID;

        if (stableId != UNASSIGNED_STABLE_ID) {
            Integer mapped = byStableId.get(stableId);
            if (mapped != null && mapped.intValue() == eid) {
                byStableId.remove(stableId);
            }

            Integer reverse = stableIdByEntity.get(eid);
            if (reverse != null && reverse.intValue() == stableId) {
                stableIdByEntity.remove(eid);
            }
        }

        String oldName = nameByEntity.remove(eid);
        if (oldName != null) {
            unindexName(oldName, eid);
        }
    }

    private void unindexStableId(int eid) {
        Integer previousStableIdObj = stableIdByEntity.get(eid);
        if (previousStableIdObj == null) return;

        int previousStableId = previousStableIdObj;

        Integer mapped = byStableId.get(previousStableId);
        if (mapped != null && mapped.intValue() == eid) {
            byStableId.remove(previousStableId);
        }
        stableIdByEntity.remove(eid);
    }

    private void replaceNameIndex(int eid, String newName) {
        String oldName = nameByEntity.get(eid);
        if (newName.equals(oldName)) {
            indexName(newName, eid);
            return;
        }

        if (oldName != null) {
            unindexName(oldName, eid);
        }

        nameByEntity.put(eid, newName);
        indexName(newName, eid);
    }

    private void indexName(String name, int eid) {
        IntArray bucket = byName.get(name);
        if (bucket == null) {
            bucket = new IntArray();
            byName.put(name, bucket);
        }

        if (!containsInt(bucket, eid)) {
            bucket.add(eid);
        }
    }

    private void unindexName(String name, int eid) {
        IntArray bucket = byName.get(name);
        if (bucket == null) return;

        removeInt(bucket, eid);

        if (bucket.size == 0) {
            byName.remove(name);
        }
    }

    private void requireSceneMeta() {
        if (sceneMeta == null) {
            throw new IllegalStateException("Scene metadata is required to allocate entity stable IDs.");
        }
    }

    private void validateAllocatableStableId(int stableId) {
        if (stableId <= 0 || stableId == Integer.MAX_VALUE) {
            throw new IllegalStateException("nextEntityStableId must be positive and allocatable, got " + stableId + ".");
        }
        if (byStableId.containsKey(stableId)) {
            throw new IllegalStateException("nextEntityStableId " + stableId + " is already assigned.");
        }
    }

    private void validateRestoredStableId(int stableId) {
        requireSceneMeta();
        if (stableId <= 0 || stableId >= sceneMeta.nextEntityStableId) {
            throw new IllegalArgumentException("Restored entity stableId " + stableId
                    + " must be positive and lower than nextEntityStableId " + sceneMeta.nextEntityStableId + ".");
        }
    }

    private boolean isEntityActive(int eid) {
        return world != null && mIdentity != null && eid >= 0 && world.getEntityManager().isActive(eid);
    }

    private String duplicateStableIdMessage(int stableId, int eid, int existing) {
        boolean existingActive = world != null && existing >= 0 && world.getEntityManager().isActive(existing);
        boolean existingHasIdentity = mIdentity != null && existing >= 0 && mIdentity.getSafe(existing, null) != null;
        return "Duplicate stableId " + stableId
                + " for entity " + eid
                + ", already used by entity " + existing
                + " (existingActive=" + existingActive
                + ", existingDeleted=" + !existingActive
                + ", existingHasIdentity=" + existingHasIdentity + ")";
    }

    private static String normalizeName(String name) {
        if (name == null) return DEFAULT_NAME;

        String v = name.trim();
        return v.isEmpty() ? DEFAULT_NAME : v;
    }

    private static boolean containsInt(IntArray array, int value) {
        for (int i = 0; i < array.size; i++) {
            if (array.get(i) == value) return true;
        }
        return false;
    }

    private static void removeInt(IntArray array, int value) {
        for (int i = array.size - 1; i >= 0; i--) {
            if (array.get(i) == value) {
                array.removeIndex(i);
            }
        }
    }
}
