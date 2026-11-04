package games.pixscape.runtime.hierarchy;

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
    public int[] previousSiblingEntityId;
    /** Parent-first linked traversal: unlinking an entity never shifts unrelated entities. */
    public int[] nextTraversalEntityId;
    public int[] previousTraversalEntityId;
    public int firstTraversalEntityId = -1;
    public int lastTraversalEntityId = -1;
    public int traversalSize;

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
        previousSiblingEntityId = Arrays.copyOf(previousSiblingEntityId, next);
        nextTraversalEntityId = Arrays.copyOf(nextTraversalEntityId, next);
        previousTraversalEntityId = Arrays.copyOf(previousTraversalEntityId, next);
        Arrays.fill(parentEntityId, old, next, -1);
        Arrays.fill(rootEntityId, old, next, -1);
        Arrays.fill(firstChildEntityId, old, next, -1);
        Arrays.fill(nextSiblingEntityId, old, next, -1);
        Arrays.fill(previousSiblingEntityId, old, next, -1);
        Arrays.fill(nextTraversalEntityId, old, next, -1);
        Arrays.fill(previousTraversalEntityId, old, next, -1);
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
        previousSiblingEntityId[entityId] = -1;
        nextTraversalEntityId[entityId] = -1;
        previousTraversalEntityId[entityId] = -1;
    }

    public void appendTraversal(int entityId) {
        previousTraversalEntityId[entityId] = lastTraversalEntityId;
        nextTraversalEntityId[entityId] = -1;
        if (lastTraversalEntityId >= 0) nextTraversalEntityId[lastTraversalEntityId] = entityId;
        else firstTraversalEntityId = entityId;
        lastTraversalEntityId = entityId;
        traversalSize++;
    }

    public void prependChild(int parent, int child) {
        int next = firstChildEntityId[parent];
        nextSiblingEntityId[child] = next;
        previousSiblingEntityId[child] = -1;
        if (next >= 0) previousSiblingEntityId[next] = child;
        firstChildEntityId[parent] = child;
    }

    /** O(1) per deleted entity; the caller must reject surviving orphaned children. */
    public void unlinkDeletedEntity(int entityId) {
        if (entityId < 0 || entityId >= entityCapacity) return;
        int parent = parentEntityId[entityId];
        int previous = previousSiblingEntityId[entityId];
        int next = nextSiblingEntityId[entityId];
        if (previous >= 0) nextSiblingEntityId[previous] = next;
        else if (parent >= 0) firstChildEntityId[parent] = next;
        if (next >= 0) previousSiblingEntityId[next] = previous;

        previous = previousTraversalEntityId[entityId];
        next = nextTraversalEntityId[entityId];
        if (previous >= 0 || next >= 0 || firstTraversalEntityId == entityId) {
            if (previous >= 0) nextTraversalEntityId[previous] = next;
            else firstTraversalEntityId = next;
            if (next >= 0) previousTraversalEntityId[next] = previous;
            else lastTraversalEntityId = previous;
            traversalSize--;
        }
        clearEntity(entityId);
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
        previousSiblingEntityId = new int[capacity];
        nextTraversalEntityId = new int[capacity];
        previousTraversalEntityId = new int[capacity];
        Arrays.fill(parentEntityId, -1);
        Arrays.fill(rootEntityId, -1);
        Arrays.fill(firstChildEntityId, -1);
        Arrays.fill(nextSiblingEntityId, -1);
        Arrays.fill(previousSiblingEntityId, -1);
        Arrays.fill(nextTraversalEntityId, -1);
        Arrays.fill(previousTraversalEntityId, -1);
    }
}
