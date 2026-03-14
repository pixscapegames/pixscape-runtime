package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.LongArray;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.PixscapeIdentityComponent;

/**
 * Simple runtime index for Pixscape identity lookups.
 *
 * Source of truth = {@link PixscapeIdentityComponent}.
 * This registry is only a cache / lookup index.
 *
 * V1 rules:
 * - stableId is unique when assigned
 * - {@link #UNASSIGNED_STABLE_ID} means "not assigned"
 * - name is normalized and indexed as a multi-hit lookup
 * - name defaults to "unnamed" when null or blank
 */
public final class IdentityRegistry {

    public static final long UNASSIGNED_STABLE_ID = -1L;
    public static final String DEFAULT_NAME = "unnamed";

    private final World world;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;

    /** Internal unique index: stableId -> entityId. */
    private final LongMap<Integer> byStableId = new LongMap<>();

    /** Internal non-unique index: name -> entityIds. */
    private final ObjectMap<String, IntArray> byName = new ObjectMap<>();

    /** Next candidate for stable id allocation. */
    private long nextStableId = 1L;

    public IdentityRegistry(World world) {
        if (world == null) throw new IllegalArgumentException("world is null");
        this.world = world;
        this.mIdentity = world.getMapper(PixscapeIdentityComponent.class);
    }

    /**
     * Fully rebuilds the index from active entities.
     * Call this after scene load or whenever a clean rebuild is needed.
     */
    public void rebuild() {
        byStableId.clear();
        byName.clear();
        nextStableId = 1L;

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeIdentityComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            indexEntityFromComponent(eid);
        }

        advanceNextStableId();
    }

    /**
     * Re-synchronizes one entity from its component data.
     * Useful as a safety net during migration if legacy code still modifies
     * {@link PixscapeIdentityComponent} directly.
     */
    public void syncEntity(int eid) {
        unindexEverywhere(eid);

        if (eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;
        if (!mIdentity.has(eid)) return;

        indexEntityFromComponent(eid);
        advanceNextStableId();
    }

    /**
     * Removes the entity from the index without modifying the component.
     * Useful before delete / purge / deactivation.
     */
    public void removeEntity(int eid) {
        unindexEverywhere(eid);
    }

    /**
     * Allocates a new unique stable id without assigning it to any entity.
     */
    public long allocateStableId() {
        advanceNextStableId();

        long allocated = nextStableId;
        nextStableId++;

        return allocated;
    }

    /**
     * Returns the existing stable id of the entity, or allocates and assigns one
     * if the entity does not have one yet.
     *
     * This method is convenient for ad-hoc creation or repair paths.
     * Do not rely on it inside redo paths that must remain deterministic unless
     * the allocated value is persisted in the command/initializer state.
     */
    public long ensureStableId(int eid) {
        if (eid < 0) return UNASSIGNED_STABLE_ID;
        if (!world.getEntityManager().isActive(eid)) return UNASSIGNED_STABLE_ID;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        long current = c.stableId;

        if (current != UNASSIGNED_STABLE_ID) {
            if (!byStableId.containsKey(current)) {
                byStableId.put(current, eid);
            }
            if (current >= nextStableId) {
                nextStableId = current + 1L;
            }
            return current;
        }

        long allocated = allocateStableId();
        c.stableId = allocated;
        c.name = normalizeName(c.name);
        byStableId.put(allocated, eid);
        indexName(c.name, eid);

        return allocated;
    }

    /**
     * Returns true if an assigned stable id is already indexed.
     */
    public boolean hasStableId(long stableId) {
        return stableId != UNASSIGNED_STABLE_ID && byStableId.containsKey(stableId);
    }

    /**
     * Returns the active entity matching the given stable id, or -1.
     */
    public int findByStableId(long stableId) {
        if (stableId == UNASSIGNED_STABLE_ID) return -1;

        Integer eid = byStableId.get(stableId);
        if (eid == null) return -1;

        return world.getEntityManager().isActive(eid) ? eid : -1;
    }

    /**
     * Returns the stable id of the entity, or {@link #UNASSIGNED_STABLE_ID}.
     */
    public long getStableId(int eid) {
        if (eid < 0) return UNASSIGNED_STABLE_ID;

        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return UNASSIGNED_STABLE_ID;

        return c.stableId;
    }

    /**
     * Returns the normalized name of the entity, or {@link #DEFAULT_NAME}
     * when missing.
     */
    public String getName(int eid) {
        if (eid < 0) return DEFAULT_NAME;

        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return DEFAULT_NAME;

        return normalizeName(c.name);
    }

    /**
     * Returns true if the entity currently has the given name after normalization.
     */
    public boolean hasName(int eid, String name) {
        if (eid < 0) return false;

        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return false;

        return normalizeName(c.name).equals(normalizeName(name));
    }

    /**
     * Returns a copy of the entity ids matching the given name.
     * No ordering is guaranteed.
     */
    public IntArray getByName(String name) {
        String normalized = normalizeName(name);

        IntArray bucket = byName.get(normalized);
        if (bucket == null || bucket.size == 0) return new IntArray();

        IntArray out = new IntArray(bucket.size);
        out.addAll(bucket);
        return out;
    }

    /**
     * Non-allocating variant that fills the provided output array.
     */
    public void getByName(String name, IntArray out) {
        if (out == null) return;
        out.clear();

        String normalized = normalizeName(name);
        IntArray bucket = byName.get(normalized);
        if (bucket == null || bucket.size == 0) return;

        out.addAll(bucket);
    }

    /**
     * Returns the first active entity matching the given name, or -1.
     */
    public int firstByName(String name) {
        String normalized = normalizeName(name);

        IntArray bucket = byName.get(normalized);
        if (bucket == null || bucket.size == 0) return -1;

        for (int i = 0; i < bucket.size; i++) {
            int eid = bucket.get(i);
            if (world.getEntityManager().isActive(eid)) return eid;
        }
        return -1;
    }

    /**
     * Returns a defensive copy of all currently indexed names.
     */
    public Array<String> allNames() {
        Array<String> out = new Array<>(byName.size);
        for (ObjectMap.Entry<String, IntArray> entry : byName.entries()) {
            out.add(entry.key);
        }
        return out;
    }

    /**
     * Sets the stable id of an entity.
     *
     * An assigned stable id must be unique across the registry.
     * {@link #UNASSIGNED_STABLE_ID} clears the assigned id.
     */
    public void setStableId(int eid, long stableId) {
        if (eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        long oldStableId = c.stableId;

        if (oldStableId == stableId) {
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

        unindexStableIdIfOwned(oldStableId, eid);

        c.stableId = stableId;

        if (stableId != UNASSIGNED_STABLE_ID) {
            byStableId.put(stableId, eid);
            if (stableId >= nextStableId) {
                nextStableId = stableId + 1L;
            }
        }
    }

    /**
     * Sets the name of an entity.
     *
     * Null or blank names are normalized to {@link #DEFAULT_NAME}.
     * Names are not required to be unique.
     */
    public void setName(int eid, String name) {
        if (eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);

        String oldName = normalizeName(c.name);
        String newName = normalizeName(name);

        if (oldName.equals(newName)) {
            if (!newName.equals(c.name)) {
                c.name = newName;
            }
            return;
        }

        unindexName(oldName, eid);

        c.name = newName;
        indexName(newName, eid);
    }

    /**
     * Sets both stable id and name.
     */
    public void setIdentity(int eid, long stableId, String name) {
        setStableId(eid, stableId);
        setName(eid, name);
    }

    private void indexEntityFromComponent(int eid) {
        PixscapeIdentityComponent c = mIdentity.getSafe(eid, null);
        if (c == null) return;

        long stableId = c.stableId;
        String normalizedName = normalizeName(c.name);

        c.name = normalizedName;

        if (stableId != UNASSIGNED_STABLE_ID) {
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(
                        "Duplicate stableId " + stableId + " for entity " + eid + ", already used by entity " + existing
                );
            }
            byStableId.put(stableId, eid);

            if (stableId >= nextStableId) {
                nextStableId = stableId + 1L;
            }
        }

        indexName(normalizedName, eid);
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

    private void unindexStableIdIfOwned(long stableId, int eid) {
        if (stableId == UNASSIGNED_STABLE_ID) return;

        Integer mapped = byStableId.get(stableId);
        if (mapped != null && mapped.intValue() == eid) {
            byStableId.remove(stableId);
        }
    }

    /**
     * Removes an entity from all indexes.
     * This is intentionally simple and robust for V1.
     */
    private void unindexEverywhere(int eid) {
        if (eid < 0) return;

        LongArray stableIdsToRemove = null;
        for (LongMap.Entry<Integer> entry : byStableId.entries()) {
            Integer mapped = entry.value;
            if (mapped != null && mapped.intValue() == eid) {
                if (stableIdsToRemove == null) stableIdsToRemove = new LongArray();
                stableIdsToRemove.add(entry.key);
            }
        }

        if (stableIdsToRemove != null) {
            for (int i = 0; i < stableIdsToRemove.size; i++) {
                byStableId.remove(stableIdsToRemove.get(i));
            }
        }

        Array<String> emptyKeys = null;
        for (ObjectMap.Entry<String, IntArray> entry : byName.entries()) {
            IntArray bucket = entry.value;
            removeInt(bucket, eid);

            if (bucket.size == 0) {
                if (emptyKeys == null) emptyKeys = new Array<>();
                emptyKeys.add(entry.key);
            }
        }

        if (emptyKeys != null) {
            for (int i = 0; i < emptyKeys.size; i++) {
                byName.remove(emptyKeys.get(i));
            }
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
