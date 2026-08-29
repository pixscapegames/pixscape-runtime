package games.pixscape.runtime.render;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API.
 * <p>
 * This frame-local SOA is the pipeline workspace between render-source synchronization, sorting,
 * Spatial composition, and frame-queue extraction.
 */
public final class DrawList {

    private static final int MIN_CAPACITY = 16;

    public byte[] sourceDomain;
    public int[] sourceSlot;
    public int size = 0;
    private final RenderCompositionList composition = new RenderCompositionList();

    public DrawList() {
        // An explicit setCapacity(...) is expected later.
    }

    public DrawList(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void clear() {
        size = 0;
        composition.clear();
    }

    public RenderCompositionList composition() {
        return composition;
    }

    /** Clears only the flattened entries while preserving staged composition items. */
    public void clearEntries() {
        size = 0;
    }

    public int get(int index) {
        return sourceSlot[index];
    }

    public byte getDomain(int index) {
        return sourceDomain[index];
    }

    public int[] data() {
        return sourceSlot;
    }

    public byte[] domainData() {
        return sourceDomain;
    }

    public void addEcsSlot(int slot) {
        add(RenderSourceDomain.SOURCE_ECS, slot);
    }

    public void addTiledSlot(int slot) {
        add(RenderSourceDomain.SOURCE_TILED, slot);
    }

    public void addVfxSlot(int vfxIndex) {
        add(RenderSourceDomain.SOURCE_VFX, vfxIndex);
    }

    public void add(byte domain, int slot) {
        if (sourceSlot == null || sourceDomain == null) {
            throw new IllegalStateException(
                    "DrawList capacity not initialized. " +
                            "Call setCapacity(...) after World creation."
            );
        }
        ensureCapacity(size + 1);
        sourceDomain[size] = domain;
        sourceSlot[size] = slot;
        size++;
    }

    public void set(int index, byte domain, int slot) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("DrawList index out of bounds: " + index + ", size=" + size);
        }
        sourceDomain[index] = domain;
        sourceSlot[index] = slot;
    }

    /**
     * Sets (or resets) DrawList capacity completely.
     * <p>
     * Typically call right after World creation, with
     * world.getEntityManager().getCapacity().
     */
    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("DrawList capacity must be > 0");
        }
        sourceDomain = new byte[capacity];
        sourceSlot = new int[capacity];
        composition.setCapacity(capacity);
        size = 0;
    }

    public void ensureCapacity(int required) {
        if (sourceSlot == null || sourceDomain == null) {
            throw new IllegalStateException(
                    "DrawList capacity not initialized. " +
                            "Call setCapacity(...) after World creation."
            );
        }
        if (required <= sourceSlot.length) {
            return;
        }

        int next = Math.max(MIN_CAPACITY, sourceSlot.length);
        while (next < required) {
            next <<= 1;
        }

        int[] oldSourceSlot = sourceSlot;
        byte[] oldSourceDomain = sourceDomain;
        sourceSlot = new int[next];
        sourceDomain = new byte[next];
        if (size > 0) {
            System.arraycopy(oldSourceSlot, 0, sourceSlot, 0, size);
            System.arraycopy(oldSourceDomain, 0, sourceDomain, 0, size);
        }
    }
}
