package games.pixscape.runtime.hierarchy;

import com.badlogic.gdx.utils.IntArray;

import java.util.Arrays;

/** Derived, non-serialized Game Object topology indexed by Artemis entity ID. */
public final class GameObjectTopologyState {
    private static final int MIN_CAPACITY = 16;

    public int[] parentEntityId;
    public int[] rootEntityId;
    public int[] depth;
    public boolean[] parented;
    public int[] firstChildEntityId;
    public int[] nextSiblingEntityId;
    public final IntArray traversal = new IntArray(false, MIN_CAPACITY);

    private int entityCapacity;

    public GameObjectTopologyState() {
        setEntityCapacity(MIN_CAPACITY);
    }

    public GameObjectTopologyState(int initialEntityCapacity) {
        setEntityCapacity(Math.max(MIN_CAPACITY, initialEntityCapacity));
    }

    public void ensureEntityCapacity(int entityId) {
        if (entityId < entityCapacity) return;
        int next = entityCapacity;
        while (next <= entityId) next <<= 1;
        int old = entityCapacity;
        parentEntityId = Arrays.copyOf(parentEntityId, next);
        rootEntityId = Arrays.copyOf(rootEntityId, next);
        depth = Arrays.copyOf(depth, next);
        parented = Arrays.copyOf(parented, next);
        firstChildEntityId = Arrays.copyOf(firstChildEntityId, next);
        nextSiblingEntityId = Arrays.copyOf(nextSiblingEntityId, next);
        Arrays.fill(parentEntityId, old, next, -1);
        Arrays.fill(rootEntityId, old, next, -1);
        Arrays.fill(firstChildEntityId, old, next, -1);
        Arrays.fill(nextSiblingEntityId, old, next, -1);
        entityCapacity = next;
    }

    public void clearEntity(int entityId) {
        if (entityId < 0 || entityId >= entityCapacity) return;
        parentEntityId[entityId] = -1;
        rootEntityId[entityId] = -1;
        depth[entityId] = 0;
        parented[entityId] = false;
        firstChildEntityId[entityId] = -1;
        nextSiblingEntityId[entityId] = -1;
    }

    public int getEntityCapacity() {
        return entityCapacity;
    }

    private void setEntityCapacity(int capacity) {
        entityCapacity = capacity;
        parentEntityId = new int[capacity];
        rootEntityId = new int[capacity];
        depth = new int[capacity];
        parented = new boolean[capacity];
        firstChildEntityId = new int[capacity];
        nextSiblingEntityId = new int[capacity];
        Arrays.fill(parentEntityId, -1);
        Arrays.fill(rootEntityId, -1);
        Arrays.fill(firstChildEntityId, -1);
        Arrays.fill(nextSiblingEntityId, -1);
    }
}
