package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.physics.box2d.Body;


/**
 * Runtime-only: must NOT be exported.
 */
public final class PhysicsRuntimeBodyComponent extends PooledComponent {
    public transient Body body;
    public transient int gen; // ++ at each rebuild/recreate

    @Override
    protected void reset() {
        body = null;
        gen = 0;
    }
}
