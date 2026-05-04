package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.PixscapeIdentityComponent;

/**
 * Simple runtime index for Pixscape identity lookups.
 *
 * Source of truth = {@link PixscapeIdentityComponent}.
 * This registry is only a cache / lookup index.
 */
public final class IdentityRegistry {

    public static final long UNASSIGNED_STABLE_ID = -1L;
    public static final String DEFAULT_NAME = "unnamed";

    private World world;
    private ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private EntitySubscription subscription;
    private EntitySubscription.SubscriptionListener subscriptionListener;

    /** Internal unique index: stableId -> entityId. */
    private final LongMap<Integer> byStableId = new LongMap<>();

    /** Internal non-unique index: name -> entityIds. */
    private final ObjectMap<String, IntArray> byName = new ObjectMap<>();

    /** Reverse indexes for efficient entity-local updates. */
    private final IntMap<Long> stableIdByEntity = new IntMap<>();
    private final IntMap<String> nameByEntity = new IntMap<>();

    /** Next candidate for stable id allocation. */
    private long nextStableId = 1L;

    public IdentityRegistry() {
    }

    public void bind(World world) {
        if (this.world == world) return;

        detachSubscriptionListener();

        byStableId.clear();
        byName.clear();
        stableIdByEntity.clear();
        nameByEntity.clear();
        nextStableId = 1L;

        this.world = world;
        this.mIdentity = null;
        this.subscription = null;
        this.subscriptionListener = null;

        if (world == null) return;

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

    /**
     * Fully rebuilds the index from active entities.
     * Call this after scene load or whenever a clean rebuild is needed.
     */
    public void rebuild() {
        byStableId.clear();
        byName.clear();
        stableIdByEntity.clear();
        nameByEntity.clear();
        nextStableId = 1L;

        if (world == null || subscription == null) return;

        IntBag bag = subscription.getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            indexEntityFromComponent(eid);
        }

        advanceNextStableId();
    }

    public long allocateStableId() {
        advanceNextStableId();

        long allocated = nextStableId;
        nextStableId++;

        return allocated;
    }

    public long ensureStableId(int eid) {
        if (!isEntityActive(eid)) return UNASSIGNED_STABLE_ID;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        long current = c.stableId;

        if (current != UNASSIGNED_STABLE_ID) {
            Integer existing = byStableId.get(current);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(
                        "Duplicate stableId " + current + " for entity " + eid + ", already used by entity " + existing
                );
            }
            byStableId.put(current, eid);
            stableIdByEntity.put(eid, current);
            if (current >= nextStableId) {
                nextStableId = current + 1L;
            }
            return current;
        }

        long allocated = allocateStableId();
        c.stableId = allocated;

        String normalizedName = normalizeName(c.name);
        c.name = normalizedName;

        byStableId.put(allocated, eid);
        stableIdByEntity.put(eid, allocated);

        replaceNameIndex(eid, normalizedName);

        return allocated;
    }

    public boolean hasStableId(long stableId) {
        return stableId != UNASSIGNED_STABLE_ID && byStableId.containsKey(stableId);
    }

    public int findByStableId(long stableId) {
        if (stableId == UNASSIGNED_STABLE_ID || world == null) return -1;

        Integer eid = byStableId.get(stableId);
        if (eid == null) return -1;

        return world.getEntityManager().isActive(eid) ? eid : -1;
    }

    public long getStableId(int eid) {
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

    public void setStableId(int eid, long stableId) {
        if (!isEntityActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        long oldStableId = c.stableId;

        if (oldStableId == stableId) {
            if (stableId != UNASSIGNED_STABLE_ID) {
                byStableId.put(stableId, eid);
                stableIdByEntity.put(eid, stableId);
            }
            return;
        }

        if (stableId != UNASSIGNED_STABLE_ID) {
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(
                        "Duplicate stableId " + stableId + " for entity " + eid + ", already used by entity " + existing
                );
            }
        }

        unindexStableId(eid);

        c.stableId = stableId;

        if (stableId != UNASSIGNED_STABLE_ID) {
            byStableId.put(stableId, eid);
            stableIdByEntity.put(eid, stableId);
            if (stableId >= nextStableId) {
                nextStableId = stableId + 1L;
            }
        }
    }

    public void setName(int eid, String name) {
        if (!isEntityActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        String normalized = normalizeName(name);

        c.name = normalized;
        replaceNameIndex(eid, normalized);
    }

    public void setIdentity(int eid, long stableId, String name) {
        if (!isEntityActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        String normalizedName = normalizeName(name);

        long oldStableId = c.stableId;
        if (oldStableId != stableId) {
            if (stableId != UNASSIGNED_STABLE_ID) {
                Integer existing = byStableId.get(stableId);
                if (existing != null && existing.intValue() != eid) {
                    throw new IllegalStateException(
                            "Duplicate stableId " + stableId + " for entity " + eid + ", already used by entity " + existing
                    );
                }
            }
            unindexStableId(eid);
            c.stableId = stableId;
            if (stableId != UNASSIGNED_STABLE_ID) {
                byStableId.put(stableId, eid);
                stableIdByEntity.put(eid, stableId);
                if (stableId >= nextStableId) {
                    nextStableId = stableId + 1L;
                }
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
        long stableId = c.stableId;
        String normalizedName = normalizeName(c.name);
        c.name = normalizedName;

        unindexEntity(eid);

        if (stableId != UNASSIGNED_STABLE_ID) {
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(
                        "Duplicate stableId " + stableId + " for entity " + eid + ", already used by entity " + existing
                );
            }

            byStableId.put(stableId, eid);
            stableIdByEntity.put(eid, stableId);

            if (stableId >= nextStableId) {
                nextStableId = stableId + 1L;
            }
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

    private void unindexStableId(int eid) {
        Long previousStableIdObj = stableIdByEntity.get(eid);
        if (previousStableIdObj == null) return;

        long previousStableId = previousStableIdObj;

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

    private void advanceNextStableId() {
        if (nextStableId < 1L) {
            nextStableId = 1L;
        }

        while (byStableId.containsKey(nextStableId)) {
            nextStableId++;
        }
    }

    private boolean isEntityActive(int eid) {
        return world != null && mIdentity != null && eid >= 0 && world.getEntityManager().isActive(eid);
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
