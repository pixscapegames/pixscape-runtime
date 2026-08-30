package games.pixscape.runtime.hierarchy;

import java.util.Arrays;

/** Derived, non-serialized hierarchy composition data indexed by Artemis entity ID. */
public final class GameObjectCompositionState {
    private static final int MIN_CAPACITY = 16;

    public int[] effectiveLayer;
    public boolean[] hierarchyVisible;
    public int[] orderedFirstChildEntityId;
    public int[] orderedNextSiblingEntityId;
    public boolean[] boundsResolved;
    public float[] minX, minY, maxX, maxY;

    private int entityCapacity;

    public GameObjectCompositionState() {
        this(MIN_CAPACITY);
    }

    public GameObjectCompositionState(int initialCapacity) {
        setCapacity(Math.max(MIN_CAPACITY, initialCapacity));
    }

    public void ensureEntityCapacity(int entityId) {
        if (entityId < entityCapacity) return;
        int old = entityCapacity;
        int next = old;
        while (next <= entityId) next <<= 1;
        effectiveLayer = Arrays.copyOf(effectiveLayer, next);
        hierarchyVisible = Arrays.copyOf(hierarchyVisible, next);
        orderedFirstChildEntityId = Arrays.copyOf(orderedFirstChildEntityId, next);
        orderedNextSiblingEntityId = Arrays.copyOf(orderedNextSiblingEntityId, next);
        boundsResolved = Arrays.copyOf(boundsResolved, next);
        minX = Arrays.copyOf(minX, next);
        minY = Arrays.copyOf(minY, next);
        maxX = Arrays.copyOf(maxX, next);
        maxY = Arrays.copyOf(maxY, next);
        Arrays.fill(effectiveLayer, old, next, -1);
        Arrays.fill(orderedFirstChildEntityId, old, next, -1);
        Arrays.fill(orderedNextSiblingEntityId, old, next, -1);
        entityCapacity = next;
    }

    public int getEntityCapacity() {
        return entityCapacity;
    }

    private void setCapacity(int capacity) {
        entityCapacity = capacity;
        effectiveLayer = new int[capacity];
        hierarchyVisible = new boolean[capacity];
        orderedFirstChildEntityId = new int[capacity];
        orderedNextSiblingEntityId = new int[capacity];
        boundsResolved = new boolean[capacity];
        minX = new float[capacity];
        minY = new float[capacity];
        maxX = new float[capacity];
        maxY = new float[capacity];
        Arrays.fill(effectiveLayer, -1);
        Arrays.fill(orderedFirstChildEntityId, -1);
        Arrays.fill(orderedNextSiblingEntityId, -1);
    }
}
