package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.CompiledFixtureData;

/**
 * Passive, disposable live-world cache compiled from {@link PhysicsShapesComponent}.
 */
public final class PhysicsCompiledFixturesComponent extends PooledComponent {
    public transient Array<CompiledFixtureData> fixtures =
            new Array<>(true, 4, CompiledFixtureData.class);
    public transient int generation;
    public transient boolean valid;

    @Override
    protected void reset() {
        if (fixtures == null) {
            fixtures = new Array<>(true, 4, CompiledFixtureData.class);
        }
        fixtures.clear();
        generation = 0;
        valid = false;
    }
}
