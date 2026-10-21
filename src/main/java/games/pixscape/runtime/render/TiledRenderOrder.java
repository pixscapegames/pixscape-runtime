package games.pixscape.runtime.render;

import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;

/** Prepared unsigned-key order of published refs for one map instance, before visibility filtering. */
public final class TiledRenderOrder {
    private final TiledMapLayerData map;
    private int sourceRevision = Integer.MIN_VALUE;
    private boolean dirty = true;
    private int[] orderedRefs = new int[0];
    private int[] scratch = new int[0];
    private int[] selectedWords = new int[0];
    private final int[] buckets = new int[256];
    private int size;
    private int preparationCount;

    TiledRenderOrder(TiledMapLayerData map) { this.map = map; }

    boolean belongsTo(TiledMapLayerData source) { return map == source; }

    /** Called by the existing ref publication/key refresh boundary, never by camera changes. */
    public void invalidate() { dirty = true; }
    public boolean isPrepared() { return !dirty && sourceRevision == map.contentStateRevision(); }

    void release(TiledMapRenderState state) {
        for (int i = 0; i < size; i++) {
            int ref = orderedRefs[i];
            if (state.preparedOrderOwner[ref] == this) state.preparedOrderOwner[ref] = null;
        }
    }

    public void prepare(TiledMapRenderState state) {
        if (!dirty && sourceRevision == map.contentStateRevision()) return;
        release(state);
        int required = 0;
        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk != null) required += chunk.getRenderableRefCount();
            }
        }
        if (required > orderedRefs.length) {
            // Bounded by the map's published refs; keep storage until its owner is removed.
            orderedRefs = new int[required];
            scratch = new int[required];
            selectedWords = new int[(required + 31) >>> 5];
        }
        size = 0;
        // This is exactly the visibility publisher's traversal, including equal-key ties.
        // IntMap iteration order is deliberately not used.
        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null) continue;
                for (int i = 0; i < chunk.getRenderableRefCount(); i++) {
                    orderedRefs[size++] = chunk.renderRefStartIndex + chunk.renderableLocalIndices.get(i);
                }
            }
        }
        sortRefs(state, orderedRefs, 0, size, scratch, buckets);
        for (int i = 0; i < size; i++) {
            state.preparedOrderRank[orderedRefs[i]] = i;
            state.preparedOrderOwner[orderedRefs[i]] = this;
        }
        sourceRevision = map.contentStateRevision();
        dirty = false;
        preparationCount++;
    }

    /** O(visible refs + covered rank words), without scanning invisible quads or sorting again. */
    public void filterVisible(TiledMapRenderState state, int[] refs, int start, int count) {
        if (count <= 1) return;
        int firstWord = Integer.MAX_VALUE;
        int lastWord = 0;
        for (int i = start, end = start + count; i < end; i++) {
            int rank = state.preparedOrderRank[refs[i]];
            int word = rank >>> 5;
            selectedWords[word] |= 1 << (rank & 31);
            firstWord = Math.min(firstWord, word);
            lastWord = Math.max(lastWord, word);
        }
        int output = start;
        for (int word = firstWord; word <= lastWord; word++) {
            int bits = selectedWords[word];
            selectedWords[word] = 0;
            while (bits != 0) {
                refs[output++] = orderedRefs[(word << 5) + Integer.numberOfTrailingZeros(bits)];
                bits &= bits - 1;
            }
        }
        if (output != start + count) throw new IllegalStateException("Duplicate or stale visible Tiled refs");
    }

    /** The existing stable, unsigned 64-bit LSD radix comparator, shared by preparation and raw groups. */
    public static void sortRefs(TiledMapRenderState state, int[] refs, int start, int count,
                                int[] scratch, int[] buckets) {
        if (count <= 1) return;
        for (int pass = 0; pass < 8; pass++) {
            int shift = pass * 8;
            for (int i = 0; i < 256; i++) buckets[i] = 0;
            for (int i = 0; i < count; i++) buckets[(int) ((state.sortKey[refs[start + i]] >>> shift) & 255L)]++;
            int sum = 0;
            for (int i = 0; i < 256; i++) {
                int n = buckets[i]; buckets[i] = sum; sum += n;
            }
            for (int i = 0; i < count; i++) {
                int ref = refs[start + i];
                scratch[buckets[(int) ((state.sortKey[ref] >>> shift) & 255L)]++] = ref;
            }
            System.arraycopy(scratch, 0, refs, start, count);
        }
    }

    public int preparationCount() { return preparationCount; }
    public int refCount() { return size; }
    /** Primitive array payload only; excludes object/array headers and the shared ref-rank table. */
    public long storageBytes() { return 4L * (orderedRefs.length + scratch.length + selectedWords.length + buckets.length); }
}
