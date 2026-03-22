package games.pixscape.runtime.render;

/**
 * Flat list of entities to draw for the current frame.
 * <p>
 * Capacity is set via the constructor or {@link #setCapacity(int)}
 * at initialization time. No dynamic growth is performed
 * beyond this capacity: overflow is considered a bug.
 */
public final class DrawList {

    private int[] entities;
    public int size = 0;

    public DrawList() {
        // An explicit setCapacity(...) is expected later.
    }

    public DrawList(int initialCapacity) {
        setCapacity(initialCapacity);
    }

    public void clear() {
        size = 0;
    }

    public int get(int index) {
        return entities[index];
    }

    public int[] data() {
        return entities;
    }

    public void add(int entity) {
        if (entities == null) {
            throw new IllegalStateException(
                    "DrawList capacity not initialized. " +
                            "Call setCapacity(...) after World creation."
            );
        }
        if (size >= entities.length) {
            throw new IllegalStateException(
                    "DrawList overflow: size=" + size +
                            ", capacity=" + entities.length
            );
        }
        entities[size++] = entity;
    }

    /**
     * Sets (or resets) DrawList capacity completely.
     * <p>
     * Typically call right after World creation, with
     * world.getEntityManager().getCapacity().
     */
    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("DrawList capacity must be > 0");
        }
        entities = new int[capacity];
        size = 0;
    }
}
