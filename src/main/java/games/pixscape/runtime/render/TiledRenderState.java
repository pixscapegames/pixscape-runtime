package games.pixscape.runtime.render;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API.
 * <p>
 * Owns stable logical tiled refs and draw-ready tiled quad data for the internal render pipeline.
 */
public final class TiledRenderState {
    /**
     * Derived scene gate; authored scene metadata remains authoritative.
     */
    public boolean lightingEnabled = true;
    private final com.badlogic.gdx.utils.IntMap<TiledRenderOrder> preparedOrders =
            new com.badlogic.gdx.utils.IntMap<TiledRenderOrder>();
    int[] preparedOrderRank;
    TiledRenderOrder[] preparedOrderOwner;

    public TiledRenderOrder prepareOrderForLayer(int entityId, games.pixscape.runtime.tiled.TiledLayerData layer) {
        TiledRenderOrder order = preparedOrders.get(entityId);
        if (order == null || !order.belongsTo(layer)) {
            if (order != null) order.release(this);
            order = new TiledRenderOrder(layer);
            preparedOrders.put(entityId, order);
        }
        return order;
    }

    public TiledRenderOrder orderForLayer(int entityId) {
        return preparedOrders.get(entityId);
    }

    public void removeOrderForLayer(int entityId) {
        TiledRenderOrder old = preparedOrders.remove(entityId);
        if (old != null) old.release(this);
    }

    public long orderRankStorageBytes() {
        return preparedOrderRank != null ? 4L * preparedOrderRank.length : 0L;
    }

    public int lightingRevision;
    public final com.badlogic.gdx.utils.IntMap<TileCornerSplit> cornerSplits = new com.badlogic.gdx.utils.IntMap<>();
    public final com.badlogic.gdx.utils.IntMap<games.pixscape.runtime.render.lighting.LightingSurface> lightingSurfaces =
            new com.badlogic.gdx.utils.IntMap<games.pixscape.runtime.render.lighting.LightingSurface>();
    public final com.badlogic.gdx.utils.IntMap<games.pixscape.runtime.render.lighting.TiledLayerLightingGeometry> lightingMaps =
            new com.badlogic.gdx.utils.IntMap<games.pixscape.runtime.render.lighting.TiledLayerLightingGeometry>();

    public void replacePreviewReceiver(int ref, float[] quad, games.pixscape.runtime.render.lighting.LightingSurface receiver) {
        TileCornerSplit split = cornerSplits.get(ref);
        if (split != null) cornerSplits.put(ref, split.withReceiver(quad, receiver));
        lightingSurfaces.put(ref, receiver);
        lightingRevision++;
    }

    private static final int MIN_CAPACITY = 8;

    private int capacity;
    private int growthCount;

    private int[] visibleRefs;
    private int visibleRefCount;

    private int[] visibleTileLayerEntityId;
    private int[] visibleTileLayerLayerIndex;
    private int[] visibleTileLayerZIndex;
    private long[] visibleTileLayerCompositionKey;
    private int[] visibleTileLayerRefStart;
    private int[] visibleTileLayerRefCount;
    private TiledRenderOrder[] visibleTileLayerPreparedOrder;
    private int visibleTileLayerCount;

    private int refCount;

    public int cullingChunksTested;
    public int cullingChunksOutside;
    public int cullingChunksFullyInside;
    public int cullingChunksPartial;
    public int cullingRenderableRefsConsidered;
    public int cullingRenderableRefsVisible;
    public int cullingRenderableRefsCulled;

    public boolean[] enabled;
    public boolean[] visible;

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
    public float[] alpha;
    public byte[] repeatFlags;

    public TiledRenderState() {
    }

    public TiledRenderState(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void setCapacity(int newCapacity) {
        clearVisibleRefs();
        preparedOrders.clear();
        cornerSplits.clear();
        lightingSurfaces.clear();
        lightingMaps.clear();
        lightingRevision++;
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("TiledRenderState capacity must be > 0");
        }

        allocate(newCapacity, false);
        visibleRefCount = 0;
        visibleTileLayerCount = 0;
        refCount = 0;
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

    public void clearVisibleSlots() {
        clearVisibleRefs();
        clearCullingStats();
    }

    public void clearVisibleRefs() {
        for (int i = 0; i < visibleTileLayerCount; i++) visibleTileLayerPreparedOrder[i] = null;
        visibleRefCount = 0;
        visibleTileLayerCount = 0;
    }

    public void addVisibleRef(int tiledRenderRef) {
        if (tiledRenderRef < 0) {
            return;
        }
        ensureCapacity(visibleRefCount + 1);
        visibleRefs[visibleRefCount++] = tiledRenderRef;
    }

    public int registerRef() {
        int ref = refCount;
        ensureCapacity(ref + 1);
        refCount = ref + 1;
        return ref;
    }

    public int registerRefs(int count) {
        if (count <= 0) {
            return -1;
        }
        int refStart = refCount;
        ensureCapacity(refStart + count);
        refCount += count;
        return refStart;
    }

    public void addVisibleTileLayer(int tileLayerEntityId,
                              int layerIndex,
                              int zIndex,
                              long compositionKey,
                              int visibleRefStart,
                              int visibleRefCount) {
        // A separate publisher may have added refs after Runtime preparation (Studio standalone textures).
        TiledRenderOrder order = preparedOrders.get(tileLayerEntityId);
        if (order != null) order.invalidate();
        addPreparedVisibleTileLayer(tileLayerEntityId, layerIndex, zIndex, compositionKey, visibleRefStart, visibleRefCount, null);
    }

    /**
     * Only a publisher whose traversal matches the prepared layer order may attach that order.
     */
    public void addPreparedVisibleTileLayer(int tileLayerEntityId, int layerIndex, int zIndex, long compositionKey,
                                      int visibleRefStart, int visibleRefCount, TiledRenderOrder order) {
        if (tileLayerEntityId < 0 || visibleRefStart < 0 || visibleRefCount <= 0
                || visibleRefStart + visibleRefCount > this.visibleRefCount) {
            throw new IllegalArgumentException("Visible Tiled Map group must reference a valid non-empty ref range.");
        }
        ensureVisibleTileLayerCapacity(visibleTileLayerCount + 1);
        visibleTileLayerEntityId[visibleTileLayerCount] = tileLayerEntityId;
        visibleTileLayerLayerIndex[visibleTileLayerCount] = layerIndex;
        visibleTileLayerZIndex[visibleTileLayerCount] = zIndex;
        visibleTileLayerCompositionKey[visibleTileLayerCount] = compositionKey;
        visibleTileLayerRefStart[visibleTileLayerCount] = visibleRefStart;
        visibleTileLayerRefCount[visibleTileLayerCount] = visibleRefCount;
        visibleTileLayerPreparedOrder[visibleTileLayerCount] = order;
        visibleTileLayerCount++;
    }

    public void setRenderDataForRef(int tiledRenderRef,
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
                                    float alpha,
                                    byte repeatFlags) {
        if (tiledRenderRef < 0) {
            return;
        }
        ensureCapacity(tiledRenderRef + 1);

        TiledRenderOrder order = preparedOrderOwner[tiledRenderRef];
        if (order != null && (!this.enabled[tiledRenderRef] || this.sortKey[tiledRenderRef] != sortKey)) {
            order.invalidate();
        }

        this.enabled[tiledRenderRef] = true;
        this.visible[tiledRenderRef] = true;
        this.textureHandle[tiledRenderRef] = textureHandle;
        this.shader[tiledRenderRef] = shader;
        this.blend[tiledRenderRef] = blend;
        this.layerIndex[tiledRenderRef] = layerIndex;
        this.paramsId[tiledRenderRef] = paramsId;
        this.customParamsId[tiledRenderRef] = customParamsId;
        this.sortKey[tiledRenderRef] = sortKey;

        this.x1[tiledRenderRef] = x1;
        this.y1[tiledRenderRef] = y1;
        this.x2[tiledRenderRef] = x2;
        this.y2[tiledRenderRef] = y2;
        this.x3[tiledRenderRef] = x3;
        this.y3[tiledRenderRef] = y3;
        this.x4[tiledRenderRef] = x4;
        this.y4[tiledRenderRef] = y4;

        this.u1[tiledRenderRef] = u1;
        this.v1[tiledRenderRef] = v1;
        this.u2[tiledRenderRef] = u2;
        this.v2[tiledRenderRef] = v2;

        this.colorPacked[tiledRenderRef] = colorPacked;
        this.alpha[tiledRenderRef] = alpha;
        this.repeatFlags[tiledRenderRef] = RenderRepeatFlags.sanitize(repeatFlags);
    }

    public void disableRef(int tiledRenderRef) {
        cornerSplits.remove(tiledRenderRef);
        if (lightingSurfaces.remove(tiledRenderRef) != null) lightingRevision++;
        if (tiledRenderRef < 0 || tiledRenderRef >= capacity) {
            return;
        }
        enabled[tiledRenderRef] = false;
        TiledRenderOrder order = preparedOrderOwner[tiledRenderRef];
        if (order != null) order.invalidate();
        visible[tiledRenderRef] = false;
        textureHandle[tiledRenderRef] = 0;
    }

    public boolean isRenderableRef(int tiledRenderRef) {
        return tiledRenderRef >= 0
                && tiledRenderRef < capacity
                && enabled[tiledRenderRef]
                && visible[tiledRenderRef]
                && textureHandle[tiledRenderRef] != 0;
    }

    public int[] getVisibleRefs() {
        return visibleRefs;
    }

    public int getVisibleRefCount() {
        return visibleRefCount;
    }

    public int getVisibleTileLayerCount() {
        return visibleTileLayerCount;
    }

    public int visibleTileLayerEntityId(int groupIndex) {
        return visibleTileLayerEntityId[groupIndex];
    }

    public int visibleTileLayerLayerIndex(int groupIndex) {
        return visibleTileLayerLayerIndex[groupIndex];
    }

    public int visibleTileLayerZIndex(int groupIndex) {
        return visibleTileLayerZIndex[groupIndex];
    }

    public long visibleTileLayerCompositionKey(int groupIndex) {
        return visibleTileLayerCompositionKey[groupIndex];
    }

    public int visibleTileLayerRefStart(int groupIndex) {
        return visibleTileLayerRefStart[groupIndex];
    }

    public int visibleTileLayerRefCount(int groupIndex) {
        return visibleTileLayerRefCount[groupIndex];
    }

    public TiledRenderOrder visibleTileLayerPreparedOrder(int groupIndex) {
        return visibleTileLayerPreparedOrder[groupIndex];
    }

    public int getRefCount() {
        return refCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getGrowthCount() {
        return growthCount;
    }

    public void clearCullingStats() {
        cullingChunksTested = 0;
        cullingChunksOutside = 0;
        cullingChunksFullyInside = 0;
        cullingChunksPartial = 0;
        cullingRenderableRefsConsidered = 0;
        cullingRenderableRefsVisible = 0;
        cullingRenderableRefsCulled = 0;
    }

    private void allocate(int newCapacity, boolean copyExisting) {
        int[] oldOrderRank = preparedOrderRank;
        TiledRenderOrder[] oldOrderOwner = preparedOrderOwner;
        int[] oldVisibleRefs = visibleRefs;
        boolean[] oldEnabled = enabled;
        boolean[] oldVisible = visible;
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
        float[] oldAlpha = alpha;
        byte[] oldRepeatFlags = repeatFlags;

        visibleRefs = new int[newCapacity];
        preparedOrderRank = new int[newCapacity];
        preparedOrderOwner = new TiledRenderOrder[newCapacity];
        enabled = new boolean[newCapacity];
        visible = new boolean[newCapacity];
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
        alpha = new float[newCapacity];
        repeatFlags = new byte[newCapacity];

        if (copyExisting && capacity > 0) {
            copy(oldOrderRank, preparedOrderRank, refCount);
            System.arraycopy(oldOrderOwner, 0, preparedOrderOwner, 0, refCount);
            copy(oldVisibleRefs, visibleRefs, visibleRefCount);

            copy(oldEnabled, enabled, refCount);
            copy(oldVisible, visible, refCount);
            copy(oldTextureHandle, textureHandle, refCount);
            copy(oldShader, shader, refCount);
            copy(oldBlend, blend, refCount);
            copy(oldLayerIndex, layerIndex, refCount);
            copy(oldParamsId, paramsId, refCount);
            copy(oldCustomParamsId, customParamsId, refCount);
            copy(oldSortKey, sortKey, refCount);

            copy(oldX1, x1, refCount);
            copy(oldY1, y1, refCount);
            copy(oldX2, x2, refCount);
            copy(oldY2, y2, refCount);
            copy(oldX3, x3, refCount);
            copy(oldY3, y3, refCount);
            copy(oldX4, x4, refCount);
            copy(oldY4, y4, refCount);
            copy(oldU1, u1, refCount);
            copy(oldV1, v1, refCount);
            copy(oldU2, u2, refCount);
            copy(oldV2, v2, refCount);
            copy(oldColorPacked, colorPacked, refCount);
            copy(oldAlpha, alpha, refCount);
            copy(oldRepeatFlags, repeatFlags, refCount);
        }

        capacity = newCapacity;
    }

    private void ensureVisibleTileLayerCapacity(int required) {
        if (visibleTileLayerEntityId != null && required <= visibleTileLayerEntityId.length) return;
        int current = visibleTileLayerEntityId != null ? visibleTileLayerEntityId.length : 0;
        int next = Math.max(4, current);
        while (next < required) next <<= 1;

        visibleTileLayerEntityId = grow(visibleTileLayerEntityId, next, visibleTileLayerCount);
        visibleTileLayerLayerIndex = grow(visibleTileLayerLayerIndex, next, visibleTileLayerCount);
        visibleTileLayerZIndex = grow(visibleTileLayerZIndex, next, visibleTileLayerCount);
        visibleTileLayerCompositionKey = grow(visibleTileLayerCompositionKey, next, visibleTileLayerCount);
        visibleTileLayerRefStart = grow(visibleTileLayerRefStart, next, visibleTileLayerCount);
        visibleTileLayerRefCount = grow(visibleTileLayerRefCount, next, visibleTileLayerCount);
        TiledRenderOrder[] orders = new TiledRenderOrder[next];
        if (visibleTileLayerPreparedOrder != null) System.arraycopy(visibleTileLayerPreparedOrder, 0, orders, 0, visibleTileLayerCount);
        visibleTileLayerPreparedOrder = orders;
    }

    private static int[] grow(int[] source, int capacity, int count) {
        int[] target = new int[capacity];
        if (source != null && count > 0) System.arraycopy(source, 0, target, 0, count);
        return target;
    }

    private static long[] grow(long[] source, int capacity, int count) {
        long[] target = new long[capacity];
        if (source != null && count > 0) System.arraycopy(source, 0, target, 0, count);
        return target;
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

    private static void copy(boolean[] source, boolean[] target, int count) {
        if (source != null && count > 0) {
            System.arraycopy(source, 0, target, 0, count);
        }
    }
}
