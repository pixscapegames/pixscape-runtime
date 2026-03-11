package games.pixscape.runtime.render;

/**
 * SOA pour l'état des caméras runtime.
 *
 * Indexation par cameraIndex (0..capacity-1).
 *
 * Pour chaque caméra :
 *  - enabled         : active ou non
 *  - x,y,width,height: viewport logique (optionnel pour l'instant)
 *  - layerMask       : quels layers sont visibles (bitfield, -1 = voit tout)
 *  - postFxChainId   : identifiant de chaîne de PostFX (0 = aucun)
 *  - fboHandle       : FBO où la caméra rend (0 = backbuffer pour l'instant)
 *  - colorTextureHandle : texture couleur attachée au FBO (si != 0)
 *  - depthHandle     : buffer de profondeur (RBO/texture) attaché au FBO (optionnel)
 */
public final class CameraStateSOA {

    private static int capacity;
    public int maxIndex = -1;

    public boolean[] enabled;
    public float[]   zoom;
    public boolean[] useOffscreen;
    public float[] x;
    public float[] y;
    public float[] width;
    public float[] height;
    public int[] layerMask;
    public int[] postFxChainId;
    public int[] fboHandle;
    public float[] ambientMulR, ambientMulG, ambientMulB;
    public int[] colorTextureHandle;
    public int[] depthHandle;
    public int[] entityId;

    public CameraStateSOA() {
        capacity = 0;
    }

    // ------------------------------------------------------------------------
    // Capacity / init
    // ------------------------------------------------------------------------

    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException("CameraStateSOA capacity must be > 0");
        }

        capacity = newCapacity;
        this.enabled            = new boolean[newCapacity];
        this.zoom               = new float[newCapacity];
        this.useOffscreen       = new boolean[newCapacity];
        this.x                  = new float[newCapacity];
        this.y                  = new float[newCapacity];
        this.width              = new float[newCapacity];
        this.height             = new float[newCapacity];
        this.layerMask          = new int[newCapacity];
        this.postFxChainId      = new int[newCapacity];
        this.fboHandle          = new int[newCapacity];
        this.ambientMulR        = new float[newCapacity];
        this.ambientMulG        = new float[newCapacity];
        this.ambientMulB        = new float[newCapacity];
        this.colorTextureHandle = new int[newCapacity];
        this.depthHandle        = new int[newCapacity];
        this.entityId           = new int[capacity];

        clear();
    }

    public static int capacity() {
        return capacity;
    }

    /**
     * Remise à zéro logique (sans réallocation).
     */
    public void clear() {
        if (capacity == 0) return;

        for (int i = 0; i < capacity; i++) {
            enabled[i]            = false;
            zoom[i]               = 1f;
            useOffscreen[i]       = false;
            x[i]                  = 0f;
            y[i]                  = 0f;
            width[i]              = 0f;
            height[i]             = 0f;
            layerMask[i]          = -1; // "voit tout" par défaut
            postFxChainId[i]      = 0;
            fboHandle[i]          = 0;
            ambientMulR[i]        = 1f;
            ambientMulG[i]        = 1f;
            ambientMulB[i]        = 1f;
            colorTextureHandle[i] = 0;
            depthHandle[i]        = 0;
            entityId[i]           = -1;
        }
        maxIndex = -1;
    }

    // ------------------------------------------------------------------------
    // Gestion des caméras actives
    // ------------------------------------------------------------------------

    public void enableCamera(int index) {
        checkIndex(index);
        enabled[index] = true;
        if (index > maxIndex) {
            maxIndex = index;
        }
    }

    public void disableCamera(int index) {
        checkIndex(index);
        enabled[index] = false;
        if (index == maxIndex) {
            recomputeMaxIndex();
        }
    }

    private void recomputeMaxIndex() {
        int max = -1;
        for (int i = 0; i < capacity; i++) {
            if (enabled[i]) {
                max = i;
            }
        }
        maxIndex = max;
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= capacity) {
            throw new IndexOutOfBoundsException(
                    "cameraIndex " + index + " out of bounds (capacity=" + capacity + ')'
            );
        }
    }
}
