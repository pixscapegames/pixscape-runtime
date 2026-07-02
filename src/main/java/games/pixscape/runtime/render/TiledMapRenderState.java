package games.pixscape.runtime.render;

/**
 * Frame-local tiled render state.
 * <p>
 * Phase 4C keeps tiled quads in {@link RenderStateSOA}; this state owns stable
 * logical tiled refs and resolves them to temporary legacy render slots.
 */
public final class TiledMapRenderState {

    private int capacity;

    private int[] visibleRefs;
    private int visibleRefCount;

    private int[] refToLegacySlot;
    private int refCount;

    public TiledMapRenderState() {
    }

    public TiledMapRenderState(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("TiledMapRenderState capacity must be > 0");
        }

        capacity = newCapacity;
        visibleRefs = new int[capacity];
        refToLegacySlot = new int[capacity];
        visibleRefCount = 0;
        refCount = 0;
        clearLegacySlots(refToLegacySlot, 0, refToLegacySlot.length);
    }

    public void ensureCapacity(int required) {
        if (required <= capacity) {
            return;
        }

        int next = Math.max(8, capacity);
        while (next < required) {
            next *= 2;
        }

        int[] oldVisibleRefs = visibleRefs;
        int[] oldRefToLegacySlot = refToLegacySlot;
        visibleRefs = new int[next];
        refToLegacySlot = new int[next];
        clearLegacySlots(refToLegacySlot, 0, refToLegacySlot.length);
        if (oldVisibleRefs != null && visibleRefCount > 0) {
            System.arraycopy(oldVisibleRefs, 0, visibleRefs, 0, visibleRefCount);
        }
        if (oldRefToLegacySlot != null && refCount > 0) {
            System.arraycopy(oldRefToLegacySlot, 0, refToLegacySlot, 0, refCount);
        }
        capacity = next;
    }

    public void clearVisibleSlots() {
        clearVisibleRefs();
    }

    public void clearVisibleRefs() {
        visibleRefCount = 0;
    }

    public void addVisibleRef(int tiledRenderRef) {
        if (tiledRenderRef < 0) {
            return;
        }
        ensureCapacity(visibleRefCount + 1);
        visibleRefs[visibleRefCount++] = tiledRenderRef;
    }

    public int registerLegacySlot(int legacySlot) {
        int ref = refCount;
        setLegacySlotForRef(ref, legacySlot);
        return ref;
    }

    public int registerLegacyRange(int legacyStart, int count) {
        if (count <= 0) {
            return -1;
        }
        int refStart = refCount;
        ensureCapacity(refStart + count);
        for (int i = 0; i < count; i++) {
            refToLegacySlot[refStart + i] = legacyStart + i;
        }
        refCount += count;
        return refStart;
    }

    public void setLegacySlotForRef(int tiledRenderRef, int legacySlot) {
        if (tiledRenderRef < 0) {
            throw new IllegalArgumentException("tiledRenderRef must be >= 0");
        }
        ensureCapacity(tiledRenderRef + 1);
        refToLegacySlot[tiledRenderRef] = legacySlot;
        if (tiledRenderRef >= refCount) {
            refCount = tiledRenderRef + 1;
        }
    }

    public int legacySlotForRef(int tiledRenderRef) {
        if (tiledRenderRef < 0 || tiledRenderRef >= refCount || tiledRenderRef >= refToLegacySlot.length) {
            return -1;
        }
        return refToLegacySlot[tiledRenderRef];
    }

    public int[] getVisibleRefs() {
        return visibleRefs;
    }

    public int getVisibleRefCount() {
        return visibleRefCount;
    }

    public int[] getRefToLegacySlots() {
        return refToLegacySlot;
    }

    public int getRefCount() {
        return refCount;
    }

    public int getCapacity() {
        return capacity;
    }

    private static void clearLegacySlots(int[] slots, int start, int end) {
        for (int i = start; i < end; i++) {
            slots[i] = -1;
        }
    }
}
