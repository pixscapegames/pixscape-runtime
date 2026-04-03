package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsWeldJointComponent extends PooledComponent {

    public float referenceAngleRad = 0f;

    public float frequencyHz = 0f;
    public float dampingRatio = 0f;

    @Override
    protected void reset() {
        referenceAngleRad = 0f;
        frequencyHz = 0f;
        dampingRatio = 0f;
    }
}
