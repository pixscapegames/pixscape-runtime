package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class CameraSettingsComponent extends PooledComponent {

    public float  zoom         = 1f;
    public boolean useOffscreen = false;

    /** Caméra active (celle utilisée par défaut dans l’éditeur / runtime). */
    public boolean active = false;

    /**
     * Masque de layers visibles :
     *  - bit i à 1 => la caméra voit le layer i
     *  - -1       => voit tous les layers
     */
    public int layerMask = -1;

    @Override
    protected void reset() {
        zoom         = 1f;
        useOffscreen = false;
        active       = false;
        layerMask    = -1;
    }
}
