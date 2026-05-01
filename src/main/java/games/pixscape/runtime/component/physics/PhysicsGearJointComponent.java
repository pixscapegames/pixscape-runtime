package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;

/**
 * Gear joint : references 2 existing joints.
 * You can store ECS joint EIDs.
 */
public final class PhysicsGearJointComponent extends PooledComponent {

    @EntityId
    public int joint1Eid = -1;
    @EntityId
    public int joint2Eid = -1;

    public float ratio = 1f;

    @Override
    protected void reset() {
        joint1Eid = -1;
        joint2Eid = -1;
        ratio = 1f;
    }
}
