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
import games.pixscape.runtime.component.PixscapeTagComponent;

/**
 * Simple runtime index: tag -> entityIds.
 * <p>
 * Source of truth = {@link PixscapeTagComponent}.
 * This registry is only a cache / lookup index.
 */
public final class TagRegistry {

    private World world;
    private ComponentMapper<PixscapeTagComponent> mTags;
    private EntitySubscription subscription;
    private EntitySubscription.SubscriptionListener subscriptionListener;

    /**
     * Internal index: tag -> entities.
     */
    private final ObjectMap<String, IntArray> byTag = new ObjectMap<>();

    /**
     * Reverse index for efficient entity-local updates.
     */
    private final IntMap<Array<String>> tagsByEntity = new IntMap<>();

    public TagRegistry() {
    }

    public void bind(World world) {
        if (this.world == world) return;

        detachSubscriptionListener();

        byTag.clear();
        tagsByEntity.clear();

        this.world = world;
        this.mTags = null;
        this.subscription = null;
        this.subscriptionListener = null;

        if (world == null) return;

        this.mTags = world.getMapper(PixscapeTagComponent.class);
        this.subscription = world.getAspectSubscriptionManager().get(Aspect.all(PixscapeTagComponent.class));
        final World boundWorld = world;
        this.subscriptionListener = new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                if (TagRegistry.this.world != boundWorld) return;

                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    indexEntityFromComponent(data[i]);
                }
            }

            @Override
            public void removed(IntBag entities) {
                if (TagRegistry.this.world != boundWorld) return;

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
        byTag.clear();
        tagsByEntity.clear();

        if (world == null || subscription == null) return;

        IntBag bag = subscription.getEntities();

        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int eid = data[i];
            if (!world.getEntityManager().isActive(eid)) continue;
            indexEntityFromComponent(eid);
        }
    }

    public boolean hasTag(int eid, String tag) {
        String normalized = normalize(tag);
        if (normalized == null || eid < 0 || mTags == null) return false;

        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c == null || c.tags == null || c.tags.size == 0) return false;

        return containsString(c.tags, normalized);
    }

    public IntArray get(String tag) {
        String normalized = normalize(tag);
        if (normalized == null) return new IntArray();

        IntArray bucket = byTag.get(normalized);
        if (bucket == null || bucket.size == 0) return new IntArray();

        IntArray out = new IntArray(bucket.size);
        out.addAll(bucket);
        return out;
    }

    public void get(String tag, IntArray out) {
        if (out == null) return;
        out.clear();

        String normalized = normalize(tag);
        if (normalized == null) return;

        IntArray bucket = byTag.get(normalized);
        if (bucket == null || bucket.size == 0) return;

        out.addAll(bucket);
    }

    public int first(String tag) {
        if (world == null) return -1;

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

    public Array<String> allTags() {
        Array<String> out = new Array<>(byTag.size);
        for (ObjectMap.Entries<String, IntArray> it = byTag.entries(); it.hasNext(); ) {
            ObjectMap.Entry<String, IntArray> entry = it.next();
            out.add(entry.key);
        }
        return out;
    }

    public void addTag(int eid, String tag) {
        String normalized = normalize(tag);
        if (normalized == null || !isEntityActive(eid)) return;

        PixscapeTagComponent c = mTags.has(eid) ? mTags.get(eid) : mTags.create(eid);
        if (c.tags == null) c.tags = new Array<>();

        if (!containsString(c.tags, normalized)) {
            c.tags.add(normalized);
        }

        Array<String> current = tagsByEntity.get(eid);
        if (current == null) {
            current = new Array<>();
            tagsByEntity.put(eid, current);
        }

        if (!containsString(current, normalized)) {
            current.add(normalized);
            index(normalized, eid);
        }
    }

    public void removeTag(int eid, String tag) {
        String normalized = normalize(tag);
        if (normalized == null || eid < 0 || mTags == null) return;

        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c != null && c.tags != null && c.tags.size > 0) {
            removeString(c.tags, normalized);
        }

        Array<String> current = tagsByEntity.get(eid);
        if (current != null) {
            removeString(current, normalized);
            if (current.size == 0) {
                tagsByEntity.remove(eid);
            }
        }

        unindex(normalized, eid);
    }

    public void clearTags(int eid) {
        if (eid < 0 || mTags == null) return;

        PixscapeTagComponent c = mTags.getSafe(eid, null);
        if (c != null && c.tags != null) {
            c.tags.clear();
        }

        unindexEntity(eid);
    }

    public void setTags(int eid, String... tags) {
        if (!isEntityActive(eid)) return;

        unindexEntity(eid);

        PixscapeTagComponent c = mTags.has(eid) ? mTags.get(eid) : mTags.create(eid);
        if (c.tags == null) c.tags = new Array<>();
        c.tags.clear();

        if (tags == null || tags.length == 0) return;

        Array<String> normalizedUnique = new Array<>(tags.length);
        for (int i = 0; i < tags.length; i++) {
            String normalized = normalize(tags[i]);
            if (normalized == null || containsString(normalizedUnique, normalized)) continue;

            normalizedUnique.add(normalized);
            c.tags.add(normalized);
            index(normalized, eid);
        }

        if (normalizedUnique.size > 0) {
            tagsByEntity.put(eid, normalizedUnique);
        }
    }

    public void setTags(int eid, Array<String> tags) {
        if (tags == null) {
            setTags(eid, (String[]) null);
            return;
        }
        setTags(eid, tags.toArray());
    }

    private void indexEntityFromComponent(int eid) {
        if (!isEntityActive(eid) || !mTags.has(eid)) return;

        PixscapeTagComponent c = mTags.get(eid);
        if (c.tags == null || c.tags.size == 0) {
            unindexEntity(eid);
            return;
        }

        unindexEntity(eid);

        Array<String> normalizedUnique = new Array<>(c.tags.size);

        for (int i = 0; i < c.tags.size; i++) {
            String normalized = normalize(c.tags.get(i));
            if (normalized == null || containsString(normalizedUnique, normalized)) continue;

            normalizedUnique.add(normalized);
            index(normalized, eid);
        }

        c.tags.clear();
        c.tags.addAll(normalizedUnique);

        if (normalizedUnique.size > 0) {
            tagsByEntity.put(eid, new Array<>(normalizedUnique));
        }
    }

    private void unindexEntity(int eid) {
        Array<String> known = tagsByEntity.remove(eid);
        if (known == null || known.size == 0) return;

        for (int i = 0; i < known.size; i++) {
            unindex(known.get(i), eid);
        }
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

    private boolean isEntityActive(int eid) {
        return world != null && mTags != null && eid >= 0 && world.getEntityManager().isActive(eid);
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
