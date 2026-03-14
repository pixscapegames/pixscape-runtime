package games.pixscape.runtime.service;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.PixscapeTagComponent;

/**
 * Simple runtime index: tag -> entityIds.
 *
 * Source of truth = {@link PixscapeTagComponent}.
 * This registry is only a cache / lookup index.
 *
 * V1 rules:
 * - tags are trimmed
 * - empty tags are ignored
 * - exact duplicates are not allowed inside a component
 * - case is preserved (exact match)
 */
public final class TagRegistry {

    private final World world;
    private final ComponentMapper<PixscapeTagComponent> mTags;

    /** Internal index: tag -> entities. */
    private final ObjectMap<String, IntArray> byTag = new ObjectMap<>();

    public TagRegistry(World world) {
        if (world == null) throw new IllegalArgumentException("world is null");
        this.world = world;
        this.mTags = world.getMapper(PixscapeTagComponent.class);
    }

    /**
     * Fully rebuilds the index from active entities.
     * Call this after scene load or whenever a clean rebuild is needed.
     */
    public void rebuild() {
        byTag.clear();

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeTagComponent.class))
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
     * {@link PixscapeTagComponent} directly.
     */
    public void syncEntity(int eid) {
        unindexEverywhere(eid);

        if (eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;
        if (!mTags.has(eid)) return;

        indexEntityFromComponent(eid);
    }

    /**
     * Removes the entity from the index without modifying the component.
     * Useful before delete / purge / deactivation.
     */
    public void removeEntity(int eid) {
        unindexEverywhere(eid);
    }

    public boolean hasTag(int eid, String tag) {
        String normalized = normalize(tag);
        if (normalized == null || eid < 0) return false;

        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c == null || c.tags == null || c.tags.size == 0) return false;

        return containsString(c.tags, normalized);
    }

    /**
     * Returns a COPY of the entity ids matching the given tag.
     * No ordering is guaranteed.
     */
    public IntArray get(String tag) {
        String normalized = normalize(tag);
        if (normalized == null) return new IntArray();

        IntArray bucket = byTag.get(normalized);
        if (bucket == null || bucket.size == 0) return new IntArray();

        IntArray out = new IntArray(bucket.size);
        out.addAll(bucket);
        return out;
    }

    /**
     * Non-allocating variant that fills the provided output array.
     */
    public void get(String tag, IntArray out) {
        if (out == null) return;
        out.clear();

        String normalized = normalize(tag);
        if (normalized == null) return;

        IntArray bucket = byTag.get(normalized);
        if (bucket == null || bucket.size == 0) return;

        out.addAll(bucket);
    }

    /**
     * Returns the first active entity matching the given tag, or -1.
     */
    public int first(String tag) {
        String normalized = normalize(tag);
        if (normalized == null) return -1;

        IntArray bucket = byTag.get(normalized);
        if (bucket == null || bucket.size == 0) return -1;

        for (int i = 0; i < bucket.size; i++) {
            int eid = bucket.get(i);
            if (world.getEntityManager().isActive(eid)) return eid;
        }
        return -1;
    }

    /**
     * Returns a defensive copy of all currently indexed tags.
     */
    public Array<String> allTags() {
        Array<String> out = new Array<>(byTag.size);
        for (ObjectMap.Entry<String, IntArray> entry : byTag.entries()) {
            out.add(entry.key);
        }
        return out;
    }

    /**
     * Adds a tag to an entity, creating the component if needed.
     */
    public void addTag(int eid, String tag) {
        String normalized = normalize(tag);
        if (normalized == null || eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;

        PixscapeTagComponent c = mTags.has(eid) ? mTags.get(eid) : mTags.create(eid);
        if (c.tags == null) c.tags = new Array<>();

        if (!containsString(c.tags, normalized)) {
            c.tags.add(normalized);
        }

        index(normalized, eid);
    }

    /**
     * Removes a tag from an entity.
     */
    public void removeTag(int eid, String tag) {
        String normalized = normalize(tag);
        if (normalized == null || eid < 0) return;

        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c != null && c.tags != null && c.tags.size > 0) {
            removeString(c.tags, normalized);
        }

        unindex(normalized, eid);
    }

    /**
     * Removes all tags from an entity.
     */
    public void clearTags(int eid) {
        if (eid < 0) return;

        unindexEverywhere(eid);

        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c != null && c.tags != null) {
            c.tags.clear();
        }
    }

    /**
     * Replaces the full tag set of an entity.
     * The component content is normalized and deduplicated.
     */
    public void setTags(int eid, Array<String> tags) {
        if (eid < 0) return;
        if (!world.getEntityManager().isActive(eid)) return;

        unindexEverywhere(eid);

        PixscapeTagComponent c = mTags.has(eid) ? mTags.get(eid) : mTags.create(eid);
        if (c.tags == null) c.tags = new Array<>();
        c.tags.clear();

        if (tags == null || tags.size == 0) return;

        for (int i = 0; i < tags.size; i++) {
            String normalized = normalize(tags.get(i));
            if (normalized == null) continue;
            if (containsString(c.tags, normalized)) continue;

            c.tags.add(normalized);
            index(normalized, eid);
        }
    }

    private void indexEntityFromComponent(int eid) {
        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c == null || c.tags == null || c.tags.size == 0) return;

        /*
         * Normalize and deduplicate component data while indexing,
         * so the component stays clean as well.
         */
        Array<String> normalizedUnique = new Array<>(c.tags.size);

        for (int i = 0; i < c.tags.size; i++) {
            String normalized = normalize(c.tags.get(i));
            if (normalized == null) continue;
            if (containsString(normalizedUnique, normalized)) continue;

            normalizedUnique.add(normalized);
            index(normalized, eid);
        }

        c.tags.clear();
        c.tags.addAll(normalizedUnique);
    }

    private void index(String tag, int eid) {
        IntArray bucket = byTag.get(tag);
        if (bucket == null) {
            bucket = new IntArray();
            byTag.put(tag, bucket);
        }

        if (!containsInt(bucket, eid)) {
            bucket.add(eid);
        }
    }

    private void unindex(String tag, int eid) {
        IntArray bucket = byTag.get(tag);
        if (bucket == null) return;

        removeInt(bucket, eid);

        if (bucket.size == 0) {
            byTag.remove(tag);
        }
    }

    /**
     * Removes an entity from all buckets.
     * This is intentionally simple and robust for V1.
     */
    private void unindexEverywhere(int eid) {
        if (byTag.size == 0) return;

        Array<String> emptyKeys = null;

        for (ObjectMap.Entry<String, IntArray> entry : byTag.entries()) {
            IntArray bucket = entry.value;
            removeInt(bucket, eid);

            if (bucket.size == 0) {
                if (emptyKeys == null) emptyKeys = new Array<>();
                emptyKeys.add(entry.key);
            }
        }

        if (emptyKeys != null) {
            for (int i = 0; i < emptyKeys.size; i++) {
                byTag.remove(emptyKeys.get(i));
            }
        }
    }

    private static String normalize(String tag) {
        if (tag == null) return null;
        String v = tag.trim();
        return v.isEmpty() ? null : v;
    }

    private static boolean containsString(Array<String> array, String value) {
        for (int i = 0; i < array.size; i++) {
            if (value.equals(array.get(i))) return true;
        }
        return false;
    }

    private static void removeString(Array<String> array, String value) {
        for (int i = array.size - 1; i >= 0; i--) {
            if (value.equals(array.get(i))) {
                array.removeIndex(i);
            }
        }
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