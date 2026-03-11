package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

/**
 * Composant commun à tous les joints (équivalent ECS des champs communs des b2JointDef).
 *
 * Invariant recommandé :
 * - Une entité "joint" doit avoir EXACTEMENT :
 *   - PhysicsJointComponent (base)
 *   - + 1 composant de type (Distance/Revolute/Prismatic/etc.)
 */
public final class PhysicsJointComponent extends PooledComponent {

    // Box2D joint types (alignés sur tes composants spécifiques)
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

    /** Type logique (pour debug/UI/serial). */
    public int type = TYPE_DISTANCE;

    /** Bodies référencés (ECS entity ids). */
    public int aEid = -1;
    public int bEid = -1;

    /** b2JointDef.collideConnected */
    public boolean collideConnected = false;

    /**
     * Ancres locales en mètres.
     * - localAnchorA : dans le repère local du body A
     * - localAnchorB : dans le repère local du body B
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
