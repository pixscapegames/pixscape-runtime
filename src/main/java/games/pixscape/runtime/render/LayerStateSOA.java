package games.pixscape.runtime.render;

import games.pixscape.runtime.component.LayerComponent;

/**
 * {@code SUPPORTED_EXPERT} borrowed derived layer state indexed by layer index.
 * <p>
 * A {@code NaN} parallax value means that parallax is disabled for that axis. The engine owns and
 * rebuilds this state; mutation is phase-sensitive, and callers must reacquire it after World
 * replacement rather than retaining backing arrays.
 */
public final class LayerStateSOA {

    private int capacity = 0;
    private int maxLayerIndex = -1;

    public boolean[] enabled;
    public float[] parallaxX;
    public float[] parallaxY;
    public int[] entityId;
    public int[] type;
    public float physicsParallaxX = Float.NaN;
    public float physicsParallaxY = Float.NaN;

    public LayerStateSOA() {
    }

    public LayerStateSOA(int maxLayers) {
        setCapacity(maxLayers);
    }

    /**
     * Sets the layer capacity, replacing all backing arrays and clearing their contents.
     */
    public void setCapacity(int maxLayers) {
        if (maxLayers <= 0) {
            throw new IllegalArgumentException("LayerStateSOA capacity must be > 0");
        }

        capacity = maxLayers;
        maxLayerIndex = -1;

        enabled = new boolean[capacity];
        parallaxX = new float[capacity];
        parallaxY = new float[capacity];
        entityId = new int[capacity];
        type = new int[capacity];

        clear();
    }

    /**
     * Resets all state to zero, without changing capacity.
     */
    public void clear() {
        if (capacity == 0) return;

        for (int i = 0; i < capacity; i++) {
            enabled[i] = false;
            parallaxX[i] = Float.NaN;
            parallaxY[i] = Float.NaN;
            entityId[i] = -1;
            type[i] = LayerComponent.TYPE_CLASSIC;
        }
        maxLayerIndex = -1;
    }

    /**
     * Marks that layer index idx is used.
     * Only does bounds check + maxLayerIndex update.
     */
    public void touchLayerIndex(int idx) {
        if (capacity == 0) {
            throw new IllegalStateException(
                    "LayerStateSOA capacity not initialized. " +
                            "Call setCapacity(...) at startup."
            );
        }
        if (idx < 0 || idx >= capacity) {
            throw new IllegalStateException(
                    "LayerStateSOA.touchLayerIndex: idx=" + idx +
                            " out of bounds (capacity=" + capacity + ")"
            );
        }
        if (idx > maxLayerIndex) {
            maxLayerIndex = idx;
        }
    }

    public int maxLayerIndex() {
        return maxLayerIndex;
    }

    public int capacity() {
        return capacity;
    }

    /**
     * True if this layer has active parallax (parallaxX/Y non-NaN).
     */
    public boolean hasParallax(int layerIdx) {
        if (layerIdx < 0 || layerIdx >= capacity) return false;
        if (!enabled[layerIdx]) return false;
        return !Float.isNaN(parallaxX[layerIdx]) || !Float.isNaN(parallaxY[layerIdx]);
    }
}
