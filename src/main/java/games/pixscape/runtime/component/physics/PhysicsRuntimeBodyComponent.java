package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.physics.box2d.Body;


/** Runtime-only: ne doit PAS être exporté. */
public final class PhysicsRuntimeBodyComponent extends PooledComponent {
    public transient Body body;
    public transient int gen; // ++ à chaque rebuild/recreate

    @Override
    protected void reset() {
        body = null;
        gen = 0;
    }
}
