package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsMouseJointComponent extends PooledComponent {

    /**
     * Target world (meters).
     */
    public float targetX = 0f;
    public float targetY = 0f;

    public float maxForce = 0f;

    public float stiffness = 0f;
    public float damping = 0f;

    @Override
    protected void reset() {
        targetX = targetY = 0f;
        maxForce = 0f;
        stiffness = 0f;
        damping = 0f;
    }
}
