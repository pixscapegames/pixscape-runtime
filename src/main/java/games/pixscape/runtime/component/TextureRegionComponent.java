package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/** Runtime uniquement : UV/tailles résolues (non sérialisé). */
public final class TextureRegionComponent extends PooledComponent {
    public transient float u1, v1, u2, v2;  // UV normalisés
    public transient int pixW, pixH;        // taille packée (facultatif)
    public transient boolean valid;         // true si résolu

    @Override
    protected void reset() {
        u1 = v1 = u2 = v2 = 0f;
        pixW = pixH = 0;
        valid = false;
    }
}
