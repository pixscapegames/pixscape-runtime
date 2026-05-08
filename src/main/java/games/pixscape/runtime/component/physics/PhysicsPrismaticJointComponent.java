package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsPrismaticJointComponent extends PooledComponent {

    /**
     * Local axis A (ideally normalized).
     */
    public float axisX = 1f;
    public float axisY = 0f;

    public boolean enableLimit = false;
    public float lowerTranslationM = 0f;
    public float upperTranslationM = 0f;

    public boolean enableMotor = false;
    public float motorSpeedMps = 0f;
    public float maxMotorForce = 0f;

    @Override
    protected void reset() {
        axisX = 1f;
        axisY = 0f;
        enableLimit = false;
        lowerTranslationM = 0f;
        upperTranslationM = 0f;
        enableMotor = false;
        motorSpeedMps = 0f;
        maxMotorForce = 0f;
    }
}
