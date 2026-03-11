package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

/**
 * Distance joint (b2DistanceJointDef).
 *
 * Ancres locales en mètres (repère local des bodies).
 */
public final class PhysicsDistanceJointComponent extends PooledComponent {
    public float lengthM = 1f;
    public float frequencyHz = 0f;
    public float dampingRatio = 0f; // clamp [0..1]

    @Override protected void reset() {
        lengthM = 1f;
        frequencyHz = 0f;
        dampingRatio = 0f;
    }
}

