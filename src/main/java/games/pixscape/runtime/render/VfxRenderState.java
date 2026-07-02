package games.pixscape.runtime.render;

/**
 * Frame-local SOA for particle/VFX draw data.
 * <p>
 * The state owns VFX render data independently. Clearing a frame only resets
 * the active count; arrays are reused until capacity needs to grow.
 */
public final class VfxRenderState {
    private static final int MIN_CAPACITY = 16;

    private int capacity;
    private int growthCount;
    public int activeCount;

    public int[] textureHandle;
    public int[] shader;
    public int[] blend;
    public int[] layerIndex;
    public int[] z;
    public int[] paramsId;
    public int[] customParamsId;
    public long[] sortKey;

    public float[] x1, y1, x2, y2, x3, y3, x4, y4;
    public float[] u1, v1, u2, v2;
    public float[] colorPacked;
    public byte[] repeatFlags;

    public int[] sourceEmitter;

    public VfxRenderState() {
    }

    public VfxRenderState(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void clearFrame() {
        activeCount = 0;
    }

    public void reset() {
        clearFrame();
    }

    public int getCapacity() {
        return capacity;
    }

    public int getGrowthCount() {
        return growthCount;
    }

    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("VfxRenderState capacity must be > 0");
        }
        allocate(newCapacity, false);
        activeCount = 0;
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

    public int addParticleQuad(
            int textureHandle,
            int shader,
            int blend,
            int layerIndex,
            int z,
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
            int sourceEmitter) {
        ensureCapacity(activeCount + 1);

        int index = activeCount++;
        this.textureHandle[index] = textureHandle;
        this.shader[index] = shader;
        this.blend[index] = blend;
        this.layerIndex[index] = layerIndex;
        this.z[index] = z;
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
        this.sourceEmitter[index] = sourceEmitter;
        return index;
    }

    private void allocate(int newCapacity, boolean copyExisting) {
        int[] oldTextureHandle = textureHandle;
        int[] oldShader = shader;
        int[] oldBlend = blend;
        int[] oldLayerIndex = layerIndex;
        int[] oldZ = z;
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
        int[] oldSourceEmitter = sourceEmitter;

        textureHandle = new int[newCapacity];
        shader = new int[newCapacity];
        blend = new int[newCapacity];
        layerIndex = new int[newCapacity];
        z = new int[newCapacity];
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
        sourceEmitter = new int[newCapacity];

        if (copyExisting && capacity > 0) {
            int count = Math.min(activeCount, capacity);
            copy(oldTextureHandle, textureHandle, count);
            copy(oldShader, shader, count);
            copy(oldBlend, blend, count);
            copy(oldLayerIndex, layerIndex, count);
            copy(oldZ, z, count);
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
            copy(oldSourceEmitter, sourceEmitter, count);
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
}
