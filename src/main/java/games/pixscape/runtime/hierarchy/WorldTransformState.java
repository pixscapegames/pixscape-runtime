package games.pixscape.runtime.hierarchy;

import games.pixscape.runtime.component.TransformComponent;

/**
 * Derived, non-serialized world-transform storage indexed by Artemis entity ID.
 *
 * <p>This Stage-1 type defines the state contract only. A later hierarchy-resolution system will
 * own publication and invalidation. The primitive arrays allow allocation-free indexed reads after
 * capacity preparation.</p>
 */
public final class WorldTransformState {
    private static final int MIN_CAPACITY = 16;

    private int entityCapacity;

    public boolean[] resolved;
    public float[] x, y;
    public float[] rotationRad;
    public float[] scaleX, scaleY;
    public float[] m00, m01, m02;
    public float[] m10, m11, m12;

    public WorldTransformState() {
    }

    public WorldTransformState(int initialEntityCapacity) {
        setEntityCapacity(initialEntityCapacity);
    }

    public void setEntityCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("WorldTransformState entity capacity must be > 0.");
        }
        entityCapacity = capacity;
        resolved = new boolean[capacity];
        x = new float[capacity];
        y = new float[capacity];
        rotationRad = new float[capacity];
        scaleX = new float[capacity];
        scaleY = new float[capacity];
        m00 = new float[capacity];
        m01 = new float[capacity];
        m02 = new float[capacity];
        m10 = new float[capacity];
        m11 = new float[capacity];
        m12 = new float[capacity];
    }

    public void setResolved(int entityId, TransformComponent worldTransform) {
        if (entityId < 0 || worldTransform == null) {
            throw new IllegalArgumentException("Entity ID and resolved world transform are required.");
        }
        ensureEntityCapacity(entityId);
        float cos = com.badlogic.gdx.math.MathUtils.cos(worldTransform.rotationRad);
        float sin = com.badlogic.gdx.math.MathUtils.sin(worldTransform.rotationRad);
        resolved[entityId] = true;
        x[entityId] = worldTransform.x;
        y[entityId] = worldTransform.y;
        rotationRad[entityId] = worldTransform.rotationRad;
        scaleX[entityId] = worldTransform.scaleX;
        scaleY[entityId] = worldTransform.scaleY;
        m00[entityId] = cos * worldTransform.scaleX;
        m01[entityId] = -sin * worldTransform.scaleY;
        m02[entityId] = worldTransform.x;
        m10[entityId] = sin * worldTransform.scaleX;
        m11[entityId] = cos * worldTransform.scaleY;
        m12[entityId] = worldTransform.y;
    }

    public void setResolved(int entityId, float worldX, float worldY, float worldRotationRad,
                            float worldScaleX, float worldScaleY) {
        if (entityId < 0) {
            throw new IllegalArgumentException("Entity ID must be non-negative.");
        }
        ensureEntityCapacity(entityId);
        float cos = com.badlogic.gdx.math.MathUtils.cos(worldRotationRad);
        float sin = com.badlogic.gdx.math.MathUtils.sin(worldRotationRad);
        resolved[entityId] = true;
        x[entityId] = worldX;
        y[entityId] = worldY;
        rotationRad[entityId] = worldRotationRad;
        scaleX[entityId] = worldScaleX;
        scaleY[entityId] = worldScaleY;
        m00[entityId] = cos * worldScaleX;
        m01[entityId] = -sin * worldScaleY;
        m02[entityId] = worldX;
        m10[entityId] = sin * worldScaleX;
        m11[entityId] = cos * worldScaleY;
        m12[entityId] = worldY;
    }

    /** Publishes a resolved authored pose together with its exact hierarchy frame. */
    public void setResolvedFrame(
            int entityId,
            float worldX, float worldY, float worldRotationRad,
            float worldScaleX, float worldScaleY,
            float frameM00, float frameM01, float frameM02,
            float frameM10, float frameM11, float frameM12) {
        if (entityId < 0) {
            throw new IllegalArgumentException("Entity ID must be non-negative.");
        }
        ensureEntityCapacity(entityId);
        resolved[entityId] = true;
        x[entityId] = worldX;
        y[entityId] = worldY;
        rotationRad[entityId] = worldRotationRad;
        scaleX[entityId] = worldScaleX;
        scaleY[entityId] = worldScaleY;
        m00[entityId] = frameM00;
        m01[entityId] = frameM01;
        m02[entityId] = frameM02;
        m10[entityId] = frameM10;
        m11[entityId] = frameM11;
        m12[entityId] = frameM12;
    }

    public boolean isResolved(int entityId) {
        return entityId >= 0 && entityId < entityCapacity && resolved[entityId];
    }

    public void clear(int entityId) {
        if (entityId < 0 || entityId >= entityCapacity) return;
        resolved[entityId] = false;
    }

    public void ensureEntityCapacity(int entityId) {
        if (entityId < 0) return;
        int required = entityId + 1;
        if (resolved == null) {
            setEntityCapacity(Math.max(MIN_CAPACITY, required));
            return;
        }
        if (required <= entityCapacity) return;
        int next = Math.max(MIN_CAPACITY, entityCapacity);
        while (next < required) next <<= 1;
        resolved = grow(resolved, next);
        x = grow(x, next);
        y = grow(y, next);
        rotationRad = grow(rotationRad, next);
        scaleX = grow(scaleX, next);
        scaleY = grow(scaleY, next);
        m00 = grow(m00, next);
        m01 = grow(m01, next);
        m02 = grow(m02, next);
        m10 = grow(m10, next);
        m11 = grow(m11, next);
        m12 = grow(m12, next);
        entityCapacity = next;
    }

    public int getEntityCapacity() {
        return entityCapacity;
    }

    private static float[] grow(float[] source, int capacity) {
        float[] expanded = new float[capacity];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static boolean[] grow(boolean[] source, int capacity) {
        boolean[] expanded = new boolean[capacity];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
