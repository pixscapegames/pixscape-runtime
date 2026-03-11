package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsWeldJointComponent extends PooledComponent {

    public float referenceAngleRad = 0f;

    public float stiffness = 0f;
    public float damping   = 0f;

    @Override
    protected void reset() {
        referenceAngleRad = 0f;
        stiffness = 0f;
        damping = 0f;
    }
}
