package games.pixscape.runtime.render;

import games.pixscape.runtime.component.LayerComponent;

/**
 * SOA pour l'état des layers, indexé par layerIndex (0..capacity-1).
 *
 * Convention parallax :
 *  - parallaxX/Y == NaN  -> aucun parallax pour ce layer
 *  - parallaxX/Y != NaN  -> parallax actif
 */
public final class LayerStateSOA {

    private int capacity      = 0;
    private int maxLayerIndex = -1;

    public boolean[] enabled;
    public float[]   parallaxX;
    public float[]   parallaxY;
    public int[]     postFxChainId;
    public int[]     entityId;
    public int[]     type;
    public float     physicsParallaxX = Float.NaN;
    public float     physicsParallaxY = Float.NaN;

    public LayerStateSOA() {
    }

    public LayerStateSOA(int maxLayers) {
        setCapacity(maxLayers);
    }

    /**
     * Fixe la capacité max. à utiliser pour les layers.
     * À appeler une seule fois au démarrage (ou très rarement).
     */
    public void setCapacity(int maxLayers) {
        if (maxLayers <= 0) {
            throw new IllegalArgumentException("LayerStateSOA capacity must be > 0");
        }

        capacity      = maxLayers;
        maxLayerIndex = -1;

        enabled       = new boolean[capacity];
        parallaxX     = new float[capacity];
        parallaxY     = new float[capacity];
        postFxChainId = new int[capacity];
        entityId      = new int[capacity];
        type = new int[capacity];

        clear();
    }

    /** Remet tout l'état à zéro, sans changer la capacité. */
    public void clear() {
        if (capacity == 0) return;

        for (int i = 0; i < capacity; i++) {
            enabled[i]       = false;
            parallaxX[i]     = Float.NaN;
            parallaxY[i]     = Float.NaN;
            postFxChainId[i] = 0;
            entityId[i]      = -1;
            type[i]          = LayerComponent.TYPE_CLASSIC;
        }
        maxLayerIndex = -1;
    }

    /**
     * Signale qu'on utilise le layer d'index idx.
     * Ne fait qu'une vérif de bornes + maj de maxLayerIndex.
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

    /** Vrai si ce layer a un parallax actif (parallaxX/Y non-NaN). */
    public boolean hasParallax(int layerIdx) {
        if (layerIdx < 0 || layerIdx >= capacity) return false;
        if (!enabled[layerIdx]) return false;
        return !Float.isNaN(parallaxX[layerIdx]) || !Float.isNaN(parallaxY[layerIdx]);
    }
}
