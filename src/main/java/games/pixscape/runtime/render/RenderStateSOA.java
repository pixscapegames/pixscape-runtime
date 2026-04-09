package games.pixscape.runtime.render;

/**
 * Structure-of-arrays containing prepared render state for each entity.
 * <p>
 * Les indices correspondent directement aux entityId Artemis.
 * <p>
 * Capacity is set once via the constructor or {@link #setCapacity(int)}.
 * No dynamic growth is performed beyond this capacity:
 * overflow is considered a bug.
 */
public final class RenderStateSOA {

    public static final int KIND_NONE   = 0;
    public static final int KIND_SPRITE = 1;
    public static final int KIND_LAYER  = 2;

    private int capacity   = 0;
    private int maxEntityId = -1;

    public int[]     kind;
    public boolean[] enabled;
    public boolean[] visible;

    // Geometry ...
    public float[] x1, y1, x2, y2, x3, y3, x4, y4;

    // Offsets
    public float[] offsetX;
    public float[] offsetY;

    // UV
    public float[] u1, v1, u2, v2;

    // Couleur (canonical draw path = packed float bits)
    public float[] colorPacked;
    // Alpha remains available for systems that test CPU opacity (e.g., occlusion).
    public float[] a;

    public int[] shader;
    public int[] blend;
    public int[] textureHandle;
    public int[] layerIndex;
    public int[] z;
    public int[] paramsId;
    public int[] customParamsId;

    /** Runtime-specific internal order (Talos, Spine, Pixscape...). */
    public int[] runtimeOrder;

    public long[] sortKey;

    /** Mapping direct entityId -> entityId... */
    public int[] entityId;
    /** Frame-local list of tiled slots belonging to currently visible chunks. */
    public int[] tiledVisibleSlots;
    public int tiledVisibleSlotCount;

    public RenderStateSOA() {
    }

    public RenderStateSOA(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("RenderStateSOA capacity must be > 0");
        }

        capacity    = newCapacity;
        maxEntityId = -1;

        kind    = new int[capacity];
        enabled = new boolean[capacity];
        visible = new boolean[capacity];

        x1 = new float[capacity]; y1 = new float[capacity];
        x2 = new float[capacity]; y2 = new float[capacity];
        x3 = new float[capacity]; y3 = new float[capacity];
        x4 = new float[capacity]; y4 = new float[capacity];

        offsetX = new float[capacity];
        offsetY = new float[capacity];

        u1 = new float[capacity]; v1 = new float[capacity];
        u2 = new float[capacity]; v2 = new float[capacity];

        colorPacked = new float[capacity];
        a = new float[capacity];

        shader        = new int[capacity];
        blend         = new int[capacity];
        textureHandle = new int[capacity];
        layerIndex    = new int[capacity];
        z             = new int[capacity];
        paramsId      = new int[capacity];
        customParamsId= new int[capacity];

        runtimeOrder  = new int[capacity];

        sortKey  = new long[capacity];
        entityId = new int[capacity];
        tiledVisibleSlots = new int[capacity];
        tiledVisibleSlotCount = 0;

        clearAll();
    }

    public void touch(int slot) {
        ensureCapacity(slot + 1);
        if (slot > maxEntityId) {
            maxEntityId = slot;
        }
    }

    public void disable(int entity) {
        if (entity < 0 || entity >= capacity) {
            return;
        }
        enabled[entity] = false;
        visible[entity] = false;
        kind[entity]    = KIND_NONE;
        sortKey[entity] = 0L;
        textureHandle[entity] = 0;

        offsetX[entity] = 0f;
        offsetY[entity] = 0f;

        runtimeOrder[entity] = 0;
    }

    public int maxEntityId() {
        return maxEntityId;
    }

    public void clearTiledVisibleSlots() {
        tiledVisibleSlotCount = 0;
    }

    public void appendTiledVisibleRange(int startInclusive, int count) {
        if (count <= 0) return;
        ensureCapacity(startInclusive + count);
        int writeEnd = tiledVisibleSlotCount + count;
        if (writeEnd > tiledVisibleSlots.length) {
            throw new IllegalStateException(
                    "RenderStateSOA tiled visible list overflow: required=" + writeEnd
                            + ", capacity=" + tiledVisibleSlots.length
            );
        }
        for (int i = 0; i < count; i++) {
            tiledVisibleSlots[tiledVisibleSlotCount++] = startInclusive + i;
        }
    }

    private void ensureCapacity(int required) {
        if (capacity == 0) {
            throw new IllegalStateException(
                    "RenderStateSOA capacity not initialized. " +
                            "Call setCapacity(...) after World creation."
            );
        }
        if (required <= capacity) {
            return;
        }
        throw new IllegalStateException(
                "RenderStateSOA overflow: required=" + required + ", capacity=" + capacity
        );
    }

    public int getCapacity() {
        return capacity;
    }

    public void clearAll() {
        if (capacity == 0) return;
        for (int i = 0; i < capacity; i++) {
            kind[i]    = KIND_NONE;
            enabled[i] = false;
            visible[i] = false;
            sortKey[i] = 0L;
            entityId[i] = 0;
            offsetX[i]  = 0f;
            offsetY[i]  = 0f;
            runtimeOrder[i] = 0;
        }
        maxEntityId = -1;
        tiledVisibleSlotCount = 0;
    }
}
