package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

/**
 * Revolute joint (b2RevoluteJointDef).
 *
 * Angles in radians.
 */
public final class PhysicsRevoluteJointComponent extends PooledComponent {
    public boolean enableLimit = false;
    public float lowerAngleRad = 0f;
    public float upperAngleRad = 0f;

    public boolean enableMotor = false;
    public float motorSpeedRad = 0f;
    public float maxMotorTorque = 0f;

    @Override
    protected void reset() {
        enableLimit = false;
        lowerAngleRad = 0f;
        upperAngleRad = 0f;
        enableMotor = false;
        motorSpeedRad = 0f;
        maxMotorTorque = 0f;
    }
}
