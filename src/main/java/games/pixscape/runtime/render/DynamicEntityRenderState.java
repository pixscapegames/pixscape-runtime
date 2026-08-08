package games.pixscape.runtime.render;

/**
 * {@code SUPPORTED_EXPERT} borrowed dense render state for dynamic ECS entities.
 * <p>
 * Render slots are compact: active slots are always {@code 0..activeCount - 1}.
 * Artemis entity ids are mapped through {@link #entityIdToRenderSlot}; high
 * entity ids only grow that lightweight mapping, not the render data arrays.
 * This is derived engine-owned state, not authored ECS data. Mutation is phase-sensitive;
 * reacquire it after World rebuilds and do not retain backing arrays across capacity changes.
 */
public final class DynamicEntityRenderState {
    public static final int NO_SLOT = -1;

    private static final int MIN_RENDER_CAPACITY = 16;
    private static final int MIN_ENTITY_CAPACITY = 16;

    private int renderCapacity;
    private int entityCapacity;
    private int growthCount;

    public int activeCount;

    public int[] entityIdToRenderSlot;
    public int[] renderSlotToEntityId;
    public int[] kind;
    public boolean[] enabled;
    public boolean[] visible;

    public float[] x1, y1, x2, y2, x3, y3, x4, y4;
    public float[] offsetX, offsetY;
    public float[] u1, v1, u2, v2;
    public float[] colorPacked;
    public float[] a;

    public int[] shader;
    public int[] blend;
    public int[] textureHandle;
    public int[] layerIndex;
    public int[] z;
    public int[] paramsId;
    public int[] customParamsId;
    public byte[] repeatFlags;
    public int[] runtimeOrder;
    public long[] sortKey;

    public DynamicEntityRenderState() {
    }

    public DynamicEntityRenderState(int initialRenderCapacity) {
        setRenderCapacity(initialRenderCapacity);
    }

    public void setRenderCapacity(int initialRenderCapacity) {
        if (initialRenderCapacity <= 0) {
            throw new IllegalArgumentException("DynamicEntityRenderState render capacity must be > 0");
        }
        renderCapacity = initialRenderCapacity;
        activeCount = 0;
        growthCount = 0;

        renderSlotToEntityId = new int[renderCapacity];
        kind = new int[renderCapacity];
        enabled = new boolean[renderCapacity];
        visible = new boolean[renderCapacity];

        x1 = new float[renderCapacity];
        y1 = new float[renderCapacity];
        x2 = new float[renderCapacity];
        y2 = new float[renderCapacity];
        x3 = new float[renderCapacity];
        y3 = new float[renderCapacity];
        x4 = new float[renderCapacity];
        y4 = new float[renderCapacity];

        offsetX = new float[renderCapacity];
        offsetY = new float[renderCapacity];

        u1 = new float[renderCapacity];
        v1 = new float[renderCapacity];
        u2 = new float[renderCapacity];
        v2 = new float[renderCapacity];

        colorPacked = new float[renderCapacity];
        a = new float[renderCapacity];

        shader = new int[renderCapacity];
        blend = new int[renderCapacity];
        textureHandle = new int[renderCapacity];
        layerIndex = new int[renderCapacity];
        z = new int[renderCapacity];
        paramsId = new int[renderCapacity];
        customParamsId = new int[renderCapacity];
        repeatFlags = new byte[renderCapacity];
        runtimeOrder = new int[renderCapacity];
        sortKey = new long[renderCapacity];

        for (int i = 0; i < renderCapacity; i++) {
            renderSlotToEntityId[i] = NO_SLOT;
            layerIndex[i] = NO_SLOT;
            repeatFlags[i] = RenderRepeatFlags.NONE;
        }

        if (entityIdToRenderSlot == null || entityIdToRenderSlot.length == 0) {
            setEntityCapacity(MIN_ENTITY_CAPACITY);
        } else {
            clearEntityMappings();
        }
    }

    public void setEntityCapacity(int initialEntityCapacity) {
        if (initialEntityCapacity <= 0) {
            throw new IllegalArgumentException("DynamicEntityRenderState entity capacity must be > 0");
        }
        entityCapacity = initialEntityCapacity;
        entityIdToRenderSlot = new int[entityCapacity];
        clearEntityMappings();
    }

    public int acquireSlotForEntity(int entityId) {
        if (entityId < 0) {
            return NO_SLOT;
        }
        ensureEntityCapacity(entityId);
        int existing = entityIdToRenderSlot[entityId];
        if (existing != NO_SLOT) {
            return existing;
        }

        ensureRenderCapacity(activeCount + 1);
        int slot = activeCount++;
        clearSlot(slot);
        entityIdToRenderSlot[entityId] = slot;
        renderSlotToEntityId[slot] = entityId;
        return slot;
    }

    public void releaseSlotForEntity(int entityId) {
        int slot = renderSlotForEntity(entityId);
        if (slot == NO_SLOT) {
            return;
        }

        int last = activeCount - 1;
        entityIdToRenderSlot[entityId] = NO_SLOT;
        if (slot != last) {
            int movedEntity = renderSlotToEntityId[last];
            copySlot(last, slot);
            renderSlotToEntityId[slot] = movedEntity;
            if (movedEntity >= 0) {
                ensureEntityCapacity(movedEntity);
                entityIdToRenderSlot[movedEntity] = slot;
            }
        }

        clearSlot(last);
        activeCount = last;
    }

    public int renderSlotForEntity(int entityId) {
        if (entityId < 0 || entityId >= entityCapacity || entityIdToRenderSlot == null) {
            return NO_SLOT;
        }
        return entityIdToRenderSlot[entityId];
    }

    public int entityIdForSlot(int renderSlot) {
        if (renderSlot < 0 || renderSlot >= activeCount) {
            return NO_SLOT;
        }
        return renderSlotToEntityId[renderSlot];
    }

    public void clear() {
        for (int i = 0; i < activeCount; i++) {
            int entity = renderSlotToEntityId[i];
            if (entity >= 0 && entity < entityCapacity) {
                entityIdToRenderSlot[entity] = NO_SLOT;
            }
            clearSlot(i);
        }
        activeCount = 0;
    }

    public void ensureEntityCapacity(int entityId) {
        if (entityId < 0) return;
        int required = entityId + 1;
        if (entityIdToRenderSlot == null) {
            setEntityCapacity(Math.max(MIN_ENTITY_CAPACITY, required));
            return;
        }
        if (required <= entityCapacity) {
            return;
        }

        int next = Math.max(MIN_ENTITY_CAPACITY, entityCapacity);
        while (next < required) {
            next <<= 1;
        }

        int oldCapacity = entityCapacity;
        int[] expanded = new int[next];
        System.arraycopy(entityIdToRenderSlot, 0, expanded, 0, oldCapacity);
        entityIdToRenderSlot = expanded;
        entityCapacity = next;
        for (int i = oldCapacity; i < next; i++) {
            entityIdToRenderSlot[i] = NO_SLOT;
        }
    }

    public void ensureRenderCapacity(int required) {
        if (renderSlotToEntityId == null) {
            setRenderCapacity(Math.max(MIN_RENDER_CAPACITY, required));
            return;
        }
        if (required <= renderCapacity) {
            return;
        }

        int next = Math.max(MIN_RENDER_CAPACITY, renderCapacity);
        while (next < required) {
            next <<= 1;
        }

        renderSlotToEntityId = grow(renderSlotToEntityId, next);
        kind = grow(kind, next);
        enabled = grow(enabled, next);
        visible = grow(visible, next);

        x1 = grow(x1, next);
        y1 = grow(y1, next);
        x2 = grow(x2, next);
        y2 = grow(y2, next);
        x3 = grow(x3, next);
        y3 = grow(y3, next);
        x4 = grow(x4, next);
        y4 = grow(y4, next);

        offsetX = grow(offsetX, next);
        offsetY = grow(offsetY, next);

        u1 = grow(u1, next);
        v1 = grow(v1, next);
        u2 = grow(u2, next);
        v2 = grow(v2, next);

        colorPacked = grow(colorPacked, next);
        a = grow(a, next);

        shader = grow(shader, next);
        blend = grow(blend, next);
        textureHandle = grow(textureHandle, next);
        layerIndex = grow(layerIndex, next);
        z = grow(z, next);
        paramsId = grow(paramsId, next);
        customParamsId = grow(customParamsId, next);
        repeatFlags = grow(repeatFlags, next);
        runtimeOrder = grow(runtimeOrder, next);
        sortKey = grow(sortKey, next);

        for (int i = renderCapacity; i < next; i++) {
            renderSlotToEntityId[i] = NO_SLOT;
            layerIndex[i] = NO_SLOT;
            repeatFlags[i] = RenderRepeatFlags.NONE;
        }

        renderCapacity = next;
        growthCount++;
    }

    public int getRenderCapacity() {
        return renderCapacity;
    }

    public int getEntityMappingCapacity() {
        return entityCapacity;
    }

    public int getGrowthCount() {
        return growthCount;
    }

    private void clearEntityMappings() {
        for (int i = 0; i < entityCapacity; i++) {
            entityIdToRenderSlot[i] = NO_SLOT;
        }
    }

    private void copySlot(int from, int to) {
        kind[to] = kind[from];
        enabled[to] = enabled[from];
        visible[to] = visible[from];

        x1[to] = x1[from];
        y1[to] = y1[from];
        x2[to] = x2[from];
        y2[to] = y2[from];
        x3[to] = x3[from];
        y3[to] = y3[from];
        x4[to] = x4[from];
        y4[to] = y4[from];

        offsetX[to] = offsetX[from];
        offsetY[to] = offsetY[from];

        u1[to] = u1[from];
        v1[to] = v1[from];
        u2[to] = u2[from];
        v2[to] = v2[from];

        colorPacked[to] = colorPacked[from];
        a[to] = a[from];

        shader[to] = shader[from];
        blend[to] = blend[from];
        textureHandle[to] = textureHandle[from];
        layerIndex[to] = layerIndex[from];
        z[to] = z[from];
        paramsId[to] = paramsId[from];
        customParamsId[to] = customParamsId[from];
        repeatFlags[to] = repeatFlags[from];
        runtimeOrder[to] = runtimeOrder[from];
        sortKey[to] = sortKey[from];
    }

    private void clearSlot(int slot) {
        renderSlotToEntityId[slot] = NO_SLOT;
        kind[slot] = RenderKind.NONE;
        enabled[slot] = false;
        visible[slot] = false;

        x1[slot] = 0f;
        y1[slot] = 0f;
        x2[slot] = 0f;
        y2[slot] = 0f;
        x3[slot] = 0f;
        y3[slot] = 0f;
        x4[slot] = 0f;
        y4[slot] = 0f;

        offsetX[slot] = 0f;
        offsetY[slot] = 0f;

        u1[slot] = 0f;
        v1[slot] = 0f;
        u2[slot] = 0f;
        v2[slot] = 0f;

        colorPacked[slot] = 0f;
        a[slot] = 0f;

        shader[slot] = 0;
        blend[slot] = 0;
        textureHandle[slot] = 0;
        layerIndex[slot] = NO_SLOT;
        z[slot] = 0;
        paramsId[slot] = 0;
        customParamsId[slot] = 0;
        repeatFlags[slot] = RenderRepeatFlags.NONE;
        runtimeOrder[slot] = 0;
        sortKey[slot] = 0L;
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static boolean[] grow(boolean[] source, int next) {
        boolean[] expanded = new boolean[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static float[] grow(float[] source, int next) {
        float[] expanded = new float[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static byte[] grow(byte[] source, int next) {
        byte[] expanded = new byte[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static long[] grow(long[] source, int next) {
        long[] expanded = new long[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
