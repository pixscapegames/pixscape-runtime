package games.pixscape.runtime.component.physics;

import com.artemis.Component;
import com.artemis.PooledComponent;

public final class PhysicsWheelJointComponent extends PooledComponent {

    // Suspension
    public float frequencyHz = 4f;
    public float dampingRatio = 0.7f;

    // Motor
    public boolean enableMotor = false;
    public float motorSpeedRad = 0f;
    public float maxMotorTorque = 0f;

    // Local axis (Body A space)
    public float axisX = 0f;
    public float axisY = 1f;

    @Override
    protected void reset() {
        frequencyHz = 4f;
        dampingRatio = 0.7f;
        enableMotor = false;
        motorSpeedRad = 0f;
        maxMotorTorque = 0f;
        axisX = 0f;
        axisY = 1f;
    }
}
