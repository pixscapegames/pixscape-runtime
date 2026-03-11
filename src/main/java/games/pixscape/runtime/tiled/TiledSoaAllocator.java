package games.pixscape.runtime.tiled;

import com.badlogic.gdx.utils.Array;

public final class TiledSoaAllocator {

    private final int start;
    private final int end;

    // Range est immutable et léger
    public static final class Range {
        public int start;
        public int end;

        public int size() {
            return end - start;
        }

        public Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public Range() {
        }
    }

    private final Array<Range> used = new Array<>(false, 8);

    public TiledSoaAllocator(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public Range allocate(int size) {

        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }

        // --- 1) chercher trou entre ranges ---
        int cursor = start;

        for (int i = 0; i < used.size; i++) {
            Range r = used.get(i);

            int gap = r.start - cursor;
            if (gap >= size) {
                return insertRange(cursor, size, i);
            }

            cursor = r.end;
        }

        // --- 2) append à la fin ---
        if (cursor + size > end) {
            throw new IllegalStateException(
                    "Tiled SOA exhausted: required=" + size +
                            ", available=" + (end - cursor)
            );
        }

        return insertRange(cursor, size, used.size);
    }

    private Range insertRange(int start, int size, int index) {
        Range r = new Range();
        r.start = start;
        r.end = start + size;

        used.insert(index, r);
        return r;
    }

    public void free(Range range) {
        for (int i = 0; i < used.size; i++) {
            if (used.get(i) == range) {
                used.removeIndex(i);
                return;
            }
        }
    }

    public void reset() {
        used.clear();
    }
}