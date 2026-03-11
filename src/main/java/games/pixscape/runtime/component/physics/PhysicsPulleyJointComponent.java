package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

public final class PhysicsPulleyJointComponent extends PooledComponent {

    /** Ground anchors (monde) en mètres (ou WU si tu choisis, mais sois cohérent). */
    public float groundAx = 0f;
    public float groundAy = 0f;
    public float groundBx = 0f;
    public float groundBy = 0f;

    public float lengthAM = 0f;
    public float lengthBM = 0f;

    public float ratio = 1f;

    @Override
    protected void reset() {
        groundAx = groundAy = groundBx = groundBy = 0f;
        lengthAM = 0f;
        lengthBM = 0f;
        ratio = 1f;
    }
}
