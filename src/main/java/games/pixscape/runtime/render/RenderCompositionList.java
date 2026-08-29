package games.pixscape.runtime.render;

/**
 * Frame-local primitive staging list for global render composition.
 * Tiled entries address one visible-map group rather than one tiled render ref.
 */
public final class RenderCompositionList {
    private static final int MIN_CAPACITY = 16;

    public byte[] sourceDomain;
    public int[] sourceIndex;
    public long[] sortKey;
    public int size;

    public RenderCompositionList() {
    }

    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("RenderCompositionList capacity must be > 0");
        }
        sourceDomain = new byte[capacity];
        sourceIndex = new int[capacity];
        sortKey = new long[capacity];
        size = 0;
    }

    public void clear() {
        size = 0;
    }

    public void add(byte domain, int index, long key) {
        ensureCapacity(size + 1);
        sourceDomain[size] = domain;
        sourceIndex[size] = index;
        sortKey[size] = key;
        size++;
    }

    public void ensureCapacity(int required) {
        if (sourceIndex == null || sourceDomain == null || sortKey == null) {
            throw new IllegalStateException(
                    "RenderCompositionList capacity not initialized. Call setCapacity(...)."
            );
        }
        if (required <= sourceIndex.length) return;

        int next = Math.max(MIN_CAPACITY, sourceIndex.length);
        while (next < required) next <<= 1;

        byte[] oldDomains = sourceDomain;
        int[] oldIndices = sourceIndex;
        long[] oldKeys = sortKey;
        sourceDomain = new byte[next];
        sourceIndex = new int[next];
        sortKey = new long[next];
        if (size > 0) {
            System.arraycopy(oldDomains, 0, sourceDomain, 0, size);
            System.arraycopy(oldIndices, 0, sourceIndex, 0, size);
            System.arraycopy(oldKeys, 0, sortKey, 0, size);
        }
    }
}
