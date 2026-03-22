package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public class VisibilityComponent extends PooledComponent {
    /** Logical masking (the user can uncheck "eye" in the editor). */
    public boolean visible = true;

    /** If true, the entity is currently considered culled (outside frustum or viewport). */
    public boolean culledByFrustum = true;

    /** If true, the entity intersects the view area, regardless of its visible flag. */
    public boolean inView = false;

    /** Padding to extend the culling test area (e.g.: 1.1 = +10%). */
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

    /** "Logical + frustum" visibility. */
    public boolean isEffectivelyVisible() {
        return visible && !culledByFrustum;
    }

}
