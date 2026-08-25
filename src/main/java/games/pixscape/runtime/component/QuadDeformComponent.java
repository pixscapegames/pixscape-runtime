package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

/**
 * Authored sprite geometry state containing local offsets for the normal BL,
 * BR, TR and TL corners.
 *
 * <p>The offsets are relative to the undeformed sprite corners and compose
 * with entity rotation and signed scale. All-zero values describe the normal
 * rectangle, so this component is optional.</p>
 *
 * <p>Direct ECS access is {@code SUPPORTED_EXPERT}. Ordinary gameplay
 * mutation should prefer
 * {@link games.pixscape.runtime.api.EntityRef#quadDeform()}. Expert mutation
 * must also perform the required Runtime geometry invalidation.</p>
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
