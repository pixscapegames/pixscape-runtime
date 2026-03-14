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
 * - stableId is unique when non-zero
 * - name is normalized and indexed as a multi-hit lookup
 * - name defaults to "unnamed" when null or blank
 */
public final class IdentityRegistry {

    public static final String DEFAULT_NAME = "unnamed";

    private final World world;
    private final ComponentMapper<PixscapeIdentityComponent> mIdentity;

    /** Internal unique index: stableId -> entityId. */
    private final LongMap<Integer> byStableId = new LongMap<>();

    /** Internal non-unique index: name -> entityIds. */
    private final ObjectMap<String, IntArray> byName = new ObjectMap<>();

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

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeIdentityComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            indexEntityFromComponent(eid);
        }
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
    }

    /**
     * Removes the entity from the index without modifying the component.
     * Useful before delete / purge / deactivation.
     */
    public void removeEntity(int eid) {
        unindexEverywhere(eid);
    }

    /**
     * Returns true if a non-zero stableId is already indexed.
     */
    public boolean hasStableId(long stableId) {
        return stableId != 0L && byStableId.containsKey(stableId);
    }

    /**
     * Returns the active entity matching the given stableId, or -1.
     */
    public int findByStableId(long stableId) {
        if (stableId == 0L) return -1;

        Integer eid = byStableId.get(stableId);
        if (eid == null) return -1;

        return world.getEntityManager().isActive(eid) ? eid : -1;
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
     * Returns a COPY of the entity ids matching the given name.
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
     * Sets the stableId of an entity.
     *
     * A non-zero stableId must be unique across the registry.
     */
    public void setStableId(int eid, long stableId) {
        if (eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;

        PixscapeIdentityComponent c = mIdentity.has(eid) ? mIdentity.get(eid) : mIdentity.create(eid);
        long oldStableId = c.stableId;

        if (oldStableId == stableId) {
            return;
        }

        if (stableId != 0L) {
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(
                        "Duplicate stableId " + stableId + " for entity " + eid + ", already used by entity " + existing
                );
            }
        }

        unindexStableIdIfOwned(oldStableId, eid);

        c.stableId = stableId;

        if (stableId != 0L) {
            byStableId.put(stableId, eid);
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
     * Sets both stableId and name.
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

        /*
         * Keep the component normalized as well,
         * so runtime data stays consistent.
         */
        c.name = normalizedName;

        if (stableId != 0L) {
            Integer existing = byStableId.get(stableId);
            if (existing != null && existing.intValue() != eid) {
                throw new IllegalStateException(
                        "Duplicate stableId " + stableId + " for entity " + eid + ", already used by entity " + existing
                );
            }
            byStableId.put(stableId, eid);
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
        if (stableId == 0L) return;

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