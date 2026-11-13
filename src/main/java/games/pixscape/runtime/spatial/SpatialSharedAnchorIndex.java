package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.LongMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.Arrays;

/**
 * Scene-owned derived cell memberships for maps with identical drawn search windows.
 * Publish after face/quad publication and anchor resolution, then search an immutable frame.
 * Owner and cohort lookups are average O(1); a changed context rebuilds only its own O(K) anchors.
 * Search costs one cell window per cohort/actor plus the necessary context memberships.
 */
public final class SpatialSharedAnchorIndex {
    private final IntMap<Context> byOwner = new IntMap<>();
    private final ObjectMap<Key, Cohort> byKey = new ObjectMap<>();
    private final Array<Context> contexts = new Array<>(false, 8, Context[]::new);
    private final Array<Cohort> cohorts = new Array<>(false, 8, Cohort[]::new);
    private final Key lookup = new Key();
    private final SpatialVisualAnchorQuery.Workspace query = new SpatialVisualAnchorQuery.Workspace();
    private final ReplaySelector replay = new ReplaySelector();
    private int sharedContexts;
    private final Pool<Context> contextPool = new Pool<Context>() {
        @Override
        protected Context newObject() {
            return new Context();
        }
    };
    private final Pool<Cohort> cohortPool = new Pool<Cohort>() {
        @Override
        protected Cohort newObject() {
            return new Cohort();
        }
    };
    private final Pool<Entry> entryPool = new Pool<Entry>() {
        @Override
        protected Entry newObject() {
            return new Entry();
        }
    };
    private final Pool<Bucket> bucketPool = new Pool<Bucket>() {
        @Override
        protected Bucket newObject() {
            return new Bucket();
        }
    };
    long reindexCount;

    /** Borrowed frame handle. Invalid after removal; no authored or persistent identity. */
    public static final class Context implements Pool.Poolable {
        int owner, slot, memberSlot, revision;
        TiledMapLayerData map;
        SpatialProjectedFaceCache faces;
        float offsetX, offsetY;
        Cohort cohort;
        final Array<Entry> owned = new Array<>(Entry[]::new);
        Candidates[] candidates = new Candidates[0];

        void reserve(int actors) {
            if (actors <= candidates.length) {
                return;
            }
            int old = candidates.length;
            candidates = Arrays.copyOf(candidates, Math.max(actors, Math.max(8, old * 2)));
            for (int i = old; i < candidates.length; i++) {
                candidates[i] = new Candidates();
            }
        }

        @Override
        public void reset() {
            map = null;
            faces = null;
            cohort = null;
            owned.clear();
        }
    }

    private static final class Candidates {
        int[] values = new int[0];
        int size;

        void add(int anchor) {
            if (size == values.length) {
                values = Arrays.copyOf(values, Math.max(8, size * 2));
            }
            values[size++] = anchor;
        }
    }

    private static final class Key {
        TiledProjection projection;
        int width, height, tw, th;
        int ox, oy, dx, dy, left, right, top, bottom;

        void set(TiledMapLayerData map, float offsetX, float offsetY) {
            projection = map.projection;
            width = map.mapWidth;
            height = map.mapHeight;
            tw = map.tileWidth;
            th = map.tileHeight;
            ox = bits(map.originX);
            oy = bits(map.originY);
            dx = bits(offsetX);
            dy = bits(offsetY);
            left = bits(map.visualPaddingLeft);
            right = bits(map.visualPaddingRight);
            top = bits(map.visualPaddingTop);
            bottom = bits(map.visualPaddingBottom);
        }

        private static int bits(float value) {
            return Float.floatToIntBits(value == 0f ? 0f : value);
        }

        @Override
        public int hashCode() {
            int h = projection == null ? 0 : projection.ordinal();
            h = 31 * h + width;
            h = 31 * h + height;
            h = 31 * h + tw;
            h = 31 * h + th;
            h = 31 * h + ox;
            h = 31 * h + oy;
            h = 31 * h + dx;
            h = 31 * h + dy;
            h = 31 * h + left;
            h = 31 * h + right;
            h = 31 * h + top;
            return 31 * h + bottom;
        }

        @Override
        public boolean equals(Object value) {
            if (!(value instanceof Key)) {
                return false;
            }
            Key k = (Key) value;
            return projection == k.projection && width == k.width && height == k.height
                    && tw == k.tw && th == k.th && ox == k.ox && oy == k.oy
                    && dx == k.dx && dy == k.dy && left == k.left && right == k.right
                    && top == k.top && bottom == k.bottom;
        }
    }

    private static final class Cohort {
        final Key key = new Key();
        final Array<Context> members = new Array<>(false, 8, Context[]::new);
        final LongMap<Bucket> cells = new LongMap<>();
        int slot;
    }

    private static final class Bucket implements Pool.Poolable {
        long key;
        Entry head;

        @Override
        public void reset() {
            head = null;
        }
    }

    private static final class Entry implements Pool.Poolable {
        Context context;
        int anchor;
        Entry previous, next;
        Bucket bucket;

        @Override
        public void reset() {
            context = null;
            bucket = null;
            previous = null;
            next = null;
        }
    }

    /** No scan/allocation on an unchanged publication; numerical revisions are scoped to cache identity. */
    public Context publish(int owner, TiledMapLayerData map, SpatialProjectedFaceCache faces, float dx, float dy) {
        Context c = byOwner.get(owner);
        boolean fresh = c == null;
        if (fresh) {
            c = contextPool.obtain();
            c.owner = owner;
            c.slot = contexts.size;
            contexts.add(c);
            byOwner.put(owner, c);
        }
        boolean replaced = c.faces != faces;
        c.map = map;
        c.faces = faces;
        c.offsetX = dx;
        c.offsetY = dy;
        lookup.set(map, dx, dy);
        if (fresh || !lookup.equals(c.cohort.key)) {
            if (!fresh) {
                detach(c);
            }
            attach(c);
        } else if (replaced || c.revision != faces.revision()) {
            if (c.cohort.members.size > 1) {
                erase(c);
                insert(c);
            }
            c.revision = faces.revision();
        }
        return c;
    }

    private void attach(Context c) {
        Cohort group = byKey.get(lookup);
        if (group == null) {
            group = cohortPool.obtain();
            group.key.set(c.map, c.offsetX, c.offsetY);
            group.slot = cohorts.size;
            cohorts.add(group);
            byKey.put(group.key, group);
        }
        c.cohort = group;
        c.memberSlot = group.members.size;
        group.members.add(c);
        c.revision = c.faces.revision();
        if (group.members.size == 2) {
            insert(group.members.first());
            sharedContexts++;
        }
        if (group.members.size > 1) {
            insert(c);
            sharedContexts++;
        }
    }

    private void insert(Context c) {
        reindexCount++;
        for (int a = 0; a < c.faces.anchorCount; a++) {
            long key = cell(c.faces.anchorGx[a], c.faces.anchorGy[a]);
            Bucket b = c.cohort.cells.get(key);
            if (b == null) {
                b = bucketPool.obtain();
                b.key = key;
                c.cohort.cells.put(key, b);
            }
            Entry e = entryPool.obtain();
            e.context = c;
            e.anchor = a;
            e.bucket = b;
            e.next = b.head;
            if (e.next != null) {
                e.next.previous = e;
            }
            b.head = e;
            c.owned.add(e);
        }
    }

    private void erase(Context c) {
        for (int i = 0; i < c.owned.size; i++) {
            Entry e = c.owned.get(i);
            Bucket b = e.bucket;
            if (e.previous == null) {
                b.head = e.next;
            } else {
                e.previous.next = e.next;
            }
            if (e.next != null) {
                e.next.previous = e.previous;
            }
            if (b.head == null) {
                c.cohort.cells.remove(b.key);
                bucketPool.free(b);
            }
            entryPool.free(e);
        }
        c.owned.clear();
    }

    private void detach(Context c) {
        Cohort old = c.cohort;
        erase(c);
        if (old.members.size > 1) {
            sharedContexts--;
        }
        old.members.removeIndex(c.memberSlot);
        if (c.memberSlot < old.members.size) {
            old.members.get(c.memberSlot).memberSlot = c.memberSlot;
        }
        if (old.members.size == 1) {
            erase(old.members.first());
            sharedContexts--;
        }
        if (old.members.size == 0) {
            byKey.remove(old.key);
            cohorts.removeIndex(old.slot);
            if (old.slot < cohorts.size) {
                cohorts.get(old.slot).slot = old.slot;
            }
            cohortPool.free(old);
        }
        c.cohort = null;
    }

    /** Explicit owner removal, including component removal and recycled Artemis entity IDs. */
    public boolean remove(int owner) {
        Context c = byOwner.remove(owner);
        if (c == null) {
            return false;
        }
        detach(c);
        contexts.removeIndex(c.slot);
        if (c.slot < contexts.size) {
            contexts.get(c.slot).slot = c.slot;
        }
        replay.context = null;
        contextPool.free(c);
        return true;
    }

    /** A replaced source must not remain borrowed, even in a frame with no visible actors. */
    public boolean discardReplacedMap(int owner, TiledMapLayerData map) {
        Context c = byOwner.get(owner);
        return c != null && c.map != map && remove(owner);
    }

    public void clear() {
        while (contexts.size > 0) {
            remove(contexts.peek().owner);
        }
        contextPool.clear();
        cohortPool.clear();
        entryPool.clear();
        bucketPool.clear();
        replay.candidateAnchors = new int[0];
        replay.candidateCount = 0;
    }

    public int contextCount() {
        return contexts.size;
    }

    public int sharedContextCount() {
        return sharedContexts;
    }

    public void search(SpatialActorCollector actors, DynamicEntityRenderState ecs, TiledMapRenderState tiled) {
        if (sharedContexts == 0) {
            return;
        }
        for (int i = 0; i < contexts.size; i++) {
            Context c = contexts.get(i);
            if (c.cohort.members.size < 2) {
                continue;
            }
            c.reserve(actors.actorCount);
            for (int a = 0; a < actors.actorCount; a++) {
                c.candidates[a].size = 0;
            }
        }
        if (ecs == null || tiled == null) {
            return;
        }
        for (int g = 0; g < cohorts.size; g++) {
            Cohort group = cohorts.get(g);
            if (group.members.size < 2) {
                continue;
            }
            Context first = group.members.first();
            for (int actor = 0; actor < actors.actorCount; actor++) {
                if (!SpatialVisualAnchorQuery.window(actor, actors, ecs, first.map, first.offsetX, first.offsetY, query)) {
                    continue;
                }
                for (int gy = query.fromGy; gy <= query.toGy; gy++) {
                    for (int gx = query.fromGx; gx <= query.toGx; gx++) {
                        Bucket b = group.cells.get(cell(gx, gy));
                        if (b == null) {
                            continue;
                        }
                        for (Entry e = b.head; e != null; e = e.next) {
                            Context c = e.context;
                            if (SpatialVisualAnchorQuery.accept(e.anchor, c.faces, tiled, c.offsetX, c.offsetY, query)) {
                                c.candidates[actor].add(e.anchor);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Select immediately before solveVisual; the adapter borrows this frame's candidates. */
    public SpatialVisualAnchorSelector selector(Context context, SpatialVisualAnchorSelector nativeSelector) {
        if (context.cohort.members.size < 2) {
            return nativeSelector;
        }
        replay.context = context;
        return replay;
    }

    private static long cell(int gx, int gy) {
        return ((long) gx << 32) | (gy & 0xffffffffL);
    }

    private static final class ReplaySelector extends SpatialVisualAnchorSelector {
        Context context;

        @Override
        public void select(int actor, SpatialActorCollector actors, DynamicEntityRenderState ecs,
                           TiledMapRenderState tiled, TiledMapLayerData map,
                           SpatialProjectedFaceCache faces, float dx, float dy) {
            Candidates list = context.candidates[actor];
            candidateCount = list.size;
            candidateAnchors = list.values;
        }
    }
}
