package games.pixscape.runtime.render;

/**
 * Frame-local tiled render state.
 * <p>
 * Phase 4A keeps tiled quads in {@link RenderStateSOA}; this state owns only
 * the visible legacy tiled slots published by chunk visibility.
 */
public final class TiledMapRenderState {

    private int capacity;

    private int[] visibleSlots;
    private int visibleSlotCount;

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
        visibleSlots = new int[capacity];
        visibleSlotCount = 0;
    }

    public void ensureCapacity(int required) {
        if (required <= capacity) {
            return;
        }

        int next = Math.max(8, capacity);
        while (next < required) {
            next *= 2;
        }

        int[] oldVisibleSlots = visibleSlots;
        visibleSlots = new int[next];
        if (oldVisibleSlots != null && visibleSlotCount > 0) {
            System.arraycopy(oldVisibleSlots, 0, visibleSlots, 0, visibleSlotCount);
        }
        capacity = next;
    }

    public void clearVisibleSlots() {
        visibleSlotCount = 0;
    }

    public void addVisibleSlot(int slot) {
        ensureCapacity(visibleSlotCount + 1);
        visibleSlots[visibleSlotCount++] = slot;
    }

    public int[] getVisibleSlots() {
        return visibleSlots;
    }

    public int getVisibleSlotCount() {
        return visibleSlotCount;
    }

    public int getCapacity() {
        return capacity;
    }
}
