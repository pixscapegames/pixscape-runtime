package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsMotorJointComponent extends PooledComponent {

    /** Local offsets (bodyA frame) — in meters. */
    public float linearOffsetX = 0f;
    public float linearOffsetY = 0f;

    public float angularOffsetRad = 0f;

    public float maxForce  = 0f;
    public float maxTorque = 0f;

    /** [0..1] */
    public float correctionFactor = 0.3f;

    @Override
    protected void reset() {
        linearOffsetX = 0f;
        linearOffsetY = 0f;
        angularOffsetRad = 0f;
        maxForce = 0f;
        maxTorque = 0f;
        correctionFactor = 0.3f;
    }
}
