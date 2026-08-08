package games.pixscape.runtime.render;

/**
 * {@code SUPPORTED_EXPERT} engine-owned frame-local queue containing draw-ready render data.
 * <p>
 * This queue is intentionally a structure-of-arrays. It stores copied render
 * data for the current frame so submit code can render without reading
 * domain-specific source state. {@link games.pixscape.runtime.system.RenderExtractFrameQueueSystem}
 * populates it after draw-list build, sorting, and Spatial composition; the configured
 * submit system consumes it next.
 *
 * <p>The contents are derived data, not persistent authored scene state. Entries and backing
 * arrays may be reset, grown, or replaced across frames and scene/World rebuilds. Expert
 * mutation is phase-sensitive and must preserve the queue's SOA invariants. Callers borrowing
 * the queue from the engine must not dispose it or retain entry assumptions across rebuilds.</p>
 */
public final class FrameRenderQueue {
    public static final byte SOURCE_NONE = RenderSourceDomain.SOURCE_NONE;
    public static final byte SOURCE_ECS = RenderSourceDomain.SOURCE_ECS;
    public static final byte SOURCE_TILED = RenderSourceDomain.SOURCE_TILED;
    public static final byte SOURCE_VFX = RenderSourceDomain.SOURCE_VFX;

    private static final int MIN_CAPACITY = 16;

    private int capacity;
    private int growthCount;
    public int size;

    public int[] textureHandle;
    public int[] shader;
    public int[] blend;
    public int[] layerIndex;
    public int[] paramsId;
    public int[] customParamsId;
    public long[] sortKey;

    public float[] x1, y1, x2, y2, x3, y3, x4, y4;
    public float[] u1, v1, u2, v2;
    public float[] colorPacked;
    public byte[] repeatFlags;

    public byte[] sourceDomain;
    public int[] sourceSlot;
    public int[] sourceEntity;

    public FrameRenderQueue() {
    }

    public FrameRenderQueue(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void clear() {
        size = 0;
    }

    public void reset() {
        clear();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getGrowthCount() {
        return growthCount;
    }

    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("FrameRenderQueue capacity must be > 0");
        }
        allocate(newCapacity, false);
        size = 0;
        growthCount = 0;
    }

    public void ensureCapacity(int required) {
        if (required <= capacity) {
            return;
        }

        int next = Math.max(MIN_CAPACITY, capacity);
        while (next < required) {
            next <<= 1;
        }
        allocate(next, true);
        growthCount++;
    }

    public void addQuad(
            int textureHandle,
            int shader,
            int blend,
            int layerIndex,
            int paramsId,
            int customParamsId,
            long sortKey,
            float x1,
            float y1,
            float x2,
            float y2,
            float x3,
            float y3,
            float x4,
            float y4,
            float u1,
            float v1,
            float u2,
            float v2,
            float colorPacked,
            byte repeatFlags,
            byte sourceDomain,
            int sourceSlot,
            int sourceEntity) {
        ensureCapacity(size + 1);

        int index = size++;
        this.textureHandle[index] = textureHandle;
        this.shader[index] = shader;
        this.blend[index] = blend;
        this.layerIndex[index] = layerIndex;
        this.paramsId[index] = paramsId;
        this.customParamsId[index] = customParamsId;
        this.sortKey[index] = sortKey;

        this.x1[index] = x1;
        this.y1[index] = y1;
        this.x2[index] = x2;
        this.y2[index] = y2;
        this.x3[index] = x3;
        this.y3[index] = y3;
        this.x4[index] = x4;
        this.y4[index] = y4;

        this.u1[index] = u1;
        this.v1[index] = v1;
        this.u2[index] = u2;
        this.v2[index] = v2;

        this.colorPacked[index] = colorPacked;
        this.repeatFlags[index] = RenderRepeatFlags.sanitize(repeatFlags);
        this.sourceDomain[index] = sourceDomain;
        this.sourceSlot[index] = sourceSlot;
        this.sourceEntity[index] = sourceEntity;
    }

    public void swap(int a, int b) {
        if (a == b) {
            return;
        }
        if (a < 0 || b < 0 || a >= size || b >= size) {
            throw new IndexOutOfBoundsException(
                    "FrameRenderQueue swap indices out of bounds: a=" + a
                            + ", b=" + b
                            + ", size=" + size
            );
        }

        swap(textureHandle, a, b);
        swap(shader, a, b);
        swap(blend, a, b);
        swap(layerIndex, a, b);
        swap(paramsId, a, b);
        swap(customParamsId, a, b);
        swap(sortKey, a, b);

        swap(x1, a, b);
        swap(y1, a, b);
        swap(x2, a, b);
        swap(y2, a, b);
        swap(x3, a, b);
        swap(y3, a, b);
        swap(x4, a, b);
        swap(y4, a, b);
        swap(u1, a, b);
        swap(v1, a, b);
        swap(u2, a, b);
        swap(v2, a, b);
        swap(colorPacked, a, b);
        swap(repeatFlags, a, b);

        swap(sourceDomain, a, b);
        swap(sourceSlot, a, b);
        swap(sourceEntity, a, b);
    }

    private void allocate(int newCapacity, boolean copyExisting) {
        int[] oldTextureHandle = textureHandle;
        int[] oldShader = shader;
        int[] oldBlend = blend;
        int[] oldLayerIndex = layerIndex;
        int[] oldParamsId = paramsId;
        int[] oldCustomParamsId = customParamsId;
        long[] oldSortKey = sortKey;

        float[] oldX1 = x1;
        float[] oldY1 = y1;
        float[] oldX2 = x2;
        float[] oldY2 = y2;
        float[] oldX3 = x3;
        float[] oldY3 = y3;
        float[] oldX4 = x4;
        float[] oldY4 = y4;
        float[] oldU1 = u1;
        float[] oldV1 = v1;
        float[] oldU2 = u2;
        float[] oldV2 = v2;
        float[] oldColorPacked = colorPacked;
        byte[] oldRepeatFlags = repeatFlags;

        byte[] oldSourceDomain = sourceDomain;
        int[] oldSourceSlot = sourceSlot;
        int[] oldSourceEntity = sourceEntity;

        textureHandle = new int[newCapacity];
        shader = new int[newCapacity];
        blend = new int[newCapacity];
        layerIndex = new int[newCapacity];
        paramsId = new int[newCapacity];
        customParamsId = new int[newCapacity];
        sortKey = new long[newCapacity];

        x1 = new float[newCapacity];
        y1 = new float[newCapacity];
        x2 = new float[newCapacity];
        y2 = new float[newCapacity];
        x3 = new float[newCapacity];
        y3 = new float[newCapacity];
        x4 = new float[newCapacity];
        y4 = new float[newCapacity];
        u1 = new float[newCapacity];
        v1 = new float[newCapacity];
        u2 = new float[newCapacity];
        v2 = new float[newCapacity];
        colorPacked = new float[newCapacity];
        repeatFlags = new byte[newCapacity];

        sourceDomain = new byte[newCapacity];
        sourceSlot = new int[newCapacity];
        sourceEntity = new int[newCapacity];

        if (copyExisting && capacity > 0) {
            int count = Math.min(size, capacity);
            copy(oldTextureHandle, textureHandle, count);
            copy(oldShader, shader, count);
            copy(oldBlend, blend, count);
            copy(oldLayerIndex, layerIndex, count);
            copy(oldParamsId, paramsId, count);
            copy(oldCustomParamsId, customParamsId, count);
            copy(oldSortKey, sortKey, count);

            copy(oldX1, x1, count);
            copy(oldY1, y1, count);
            copy(oldX2, x2, count);
            copy(oldY2, y2, count);
            copy(oldX3, x3, count);
            copy(oldY3, y3, count);
            copy(oldX4, x4, count);
            copy(oldY4, y4, count);
            copy(oldU1, u1, count);
            copy(oldV1, v1, count);
            copy(oldU2, u2, count);
            copy(oldV2, v2, count);
            copy(oldColorPacked, colorPacked, count);
            copy(oldRepeatFlags, repeatFlags, count);

            copy(oldSourceDomain, sourceDomain, count);
            copy(oldSourceSlot, sourceSlot, count);
            copy(oldSourceEntity, sourceEntity, count);
        }

        capacity = newCapacity;
    }

    private static void copy(int[] source, int[] target, int count) {
        if (source != null && count > 0) {
            System.arraycopy(source, 0, target, 0, count);
        }
    }

    private static void copy(long[] source, long[] target, int count) {
        if (source != null && count > 0) {
            System.arraycopy(source, 0, target, 0, count);
        }
    }

    private static void copy(float[] source, float[] target, int count) {
        if (source != null && count > 0) {
            System.arraycopy(source, 0, target, 0, count);
        }
    }

    private static void copy(byte[] source, byte[] target, int count) {
        if (source != null && count > 0) {
            System.arraycopy(source, 0, target, 0, count);
        }
    }

    private static void swap(int[] values, int a, int b) {
        int tmp = values[a];
        values[a] = values[b];
        values[b] = tmp;
    }

    private static void swap(long[] values, int a, int b) {
        long tmp = values[a];
        values[a] = values[b];
        values[b] = tmp;
    }

    private static void swap(float[] values, int a, int b) {
        float tmp = values[a];
        values[a] = values[b];
        values[b] = tmp;
    }

    private static void swap(byte[] values, int a, int b) {
        byte tmp = values[a];
        values[a] = values[b];
        values[b] = tmp;
    }
}
