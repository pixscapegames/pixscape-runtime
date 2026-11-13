package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.physics.box2d.Body;


/**
 * Runtime-only: must NOT be exported.
 */
public final class PhysicsRuntimeBodyComponent extends PooledComponent {
    public transient Body body;
    public transient int gen; // ++ at each rebuild/recreate

    // Explicit rigid placement acknowledges its reconstructed design frame. Quantization of
    // nested TRS must not be interpreted as another editor move on the next synchronization.
    public transient boolean placedDesignPose;
    public transient float placedDesignX, placedDesignY, placedDesignAngle;

    @Override
    protected void reset() {
        body = null;
        gen = 0;
        placedDesignPose = false;
        placedDesignX = placedDesignY = placedDesignAngle = 0f;
    }
}
