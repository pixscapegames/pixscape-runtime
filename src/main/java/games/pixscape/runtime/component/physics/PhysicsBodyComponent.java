package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;

/** Définition sérialisable du body Box2D. */
public final class PhysicsBodyComponent extends PooledComponent {
    public static final int STATIC = 0;
    public static final int KINEMATIC = 1;
    public static final int DYNAMIC = 2;

    public int type = DYNAMIC;

    public boolean fixedRotation = false;
    public boolean bullet = false;
    public boolean allowSleep = true;
    public boolean awake = true;

    public float gravityScale = 1f;
    public float linearDamping = 0f;
    public float angularDamping = 0f;

    public boolean enabled = true;

    @Override protected void reset() {
        type = DYNAMIC;
        fixedRotation = false;
        bullet = false;
        allowSleep = true;
        awake = true;
        gravityScale = 1f;
        linearDamping = 0f;
        angularDamping = 0f;
        enabled = true;
    }
}

