package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsFrictionJointComponent extends PooledComponent {

    public float maxForce  = 0f;
    public float maxTorque = 0f;

    @Override
    protected void reset() {

        maxForce = 0f;
        maxTorque = 0f;
    }
}
