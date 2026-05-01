package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.artemis.annotations.EntityId;

/**
 * Common component for all joints (ECS equivalent of common b2JointDef fields).
 *
 * Recommended invariant:
 * - Une entity "joint" doit avoir EXACTEMENT :
 *   - PhysicsJointComponent (base)
 *   - + 1 composant de type (Distance/Revolute/Prismatic/etc.)
 */
public final class PhysicsJointComponent extends PooledComponent {

    // Box2D joint types (aligned with your specific components)
    public static final int TYPE_DISTANCE  = 0;
    public static final int TYPE_REVOLUTE  = 1;
    public static final int TYPE_PRISMATIC = 2;
    public static final int TYPE_PULLEY    = 3;
    public static final int TYPE_MOUSE     = 4;
    public static final int TYPE_GEAR      = 5;
    public static final int TYPE_WHEEL     = 6;
    public static final int TYPE_WELD      = 7;
    public static final int TYPE_FRICTION  = 8;
    public static final int TYPE_MOTOR     = 9;

    /** Logical type (for debug/UI/serialization). */
    public int type = TYPE_DISTANCE;

    /** Referenced bodies (ECS entity ids). */
    @EntityId
    public int aEid = -1;
    @EntityId
    public int bEid = -1;

    /** b2JointDef.collideConnected */
    public boolean collideConnected = false;

    /**
     * Local anchors in meters.
     * - localAnchorA : in body A local frame
     * - localAnchorB : in body B local frame
     *
     * Convention : (0,0) = centre du body.
     */
    public float anchorAx = 0f;
    public float anchorAy = 0f;
    public float anchorBx = 0f;
    public float anchorBy = 0f;

    /** Optionnel : tag/debug/serial. */
    public long userData = 0L;

    @Override
    protected void reset() {
        type = TYPE_DISTANCE;
        aEid = -1;
        bEid = -1;
        collideConnected = false;

        anchorAx = 0f;
        anchorAy = 0f;
        anchorBx = 0f;
        anchorBy = 0f;

        userData = 0L;
    }
}
