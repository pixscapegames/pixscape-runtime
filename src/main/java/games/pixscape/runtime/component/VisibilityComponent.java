package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public class VisibilityComponent extends PooledComponent {
    /** Masquage logique (l'utilisateur peut décocher "œil" dans l'éditeur). */
    public boolean visible = true;

    /** Si true, l'entité est actuellement considérée comme culled (hors du frustum ou du viewport). */
    public boolean culledByFrustum = true;

    /** Si true, l'entité intersecte la zone de vue, indépendamment de son flag visible. */
    public boolean inView = false;

    /** Padding pour étendre la zone de test du culling (ex: 1.1 = +10%). */
    public float padding = 1f;

    @Override
    protected void reset() {
        visible = true;
        culledByFrustum = true;
        inView = false;
        padding = 1f;
    }

    // --- Getters ---

    public boolean isVisible() {
        return visible;
    }

    public boolean isCulledByFrustum() {
        return culledByFrustum;
    }

    public boolean isInView() {
        return inView;
    }

    public float getPadding() {
        return padding;
    }

    /** Visibilité “logique + frustum”. */
    public boolean isEffectivelyVisible() {
        return visible && !culledByFrustum;
    }

}
