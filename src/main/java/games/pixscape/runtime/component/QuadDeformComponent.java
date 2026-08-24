package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Authored local-space offsets for the normal BL, BR, TR and TL sprite corners.
 */
public final class QuadDeformComponent extends PooledComponent {
    public float blX, blY;
    public float brX, brY;
    public float trX, trY;
    public float tlX, tlY;

    @Override
    protected void reset() {
        blX = blY = 0f;
        brX = brY = 0f;
        trX = trY = 0f;
        tlX = tlY = 0f;
    }
}
