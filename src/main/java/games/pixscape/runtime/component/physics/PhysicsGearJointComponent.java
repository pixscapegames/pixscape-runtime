package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

/**
 * Gear joint : référence 2 joints existants.
 * Tu peux stocker des EIDs de joints ECS.
 */
public final class PhysicsGearJointComponent extends PooledComponent {

    public int joint1Eid = -1;
    public int joint2Eid = -1;

    public float ratio = 1f;

    @Override
    protected void reset() {
        joint1Eid = -1;
        joint2Eid = -1;
        ratio = 1f;
    }
}
