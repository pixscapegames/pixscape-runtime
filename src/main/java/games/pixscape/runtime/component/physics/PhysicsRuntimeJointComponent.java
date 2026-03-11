package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.physics.box2d.Joint;

/**
 * Runtime cache: joint Box2D créé par le sync system.
 */
public final class PhysicsRuntimeJointComponent extends PooledComponent {
    public transient Joint joint;
    public transient int type = -1;

    // Guards: si les bodies ont été rebuild, on force recreate du joint.
    public transient int aGen = -1;
    public transient int bGen = -1;

    @Override protected void reset() {
        joint = null;
        aGen = bGen = -1;
    }
}

