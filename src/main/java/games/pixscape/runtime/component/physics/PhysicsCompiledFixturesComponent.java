package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.CompiledFixtureData;

/**
 * Disposable live-world cache compiled from {@link PhysicsShapesComponent}.
 */
public final class PhysicsCompiledFixturesComponent extends PooledComponent {
    public transient Array<CompiledFixtureData> fixtures =
            new Array<>(true, 4, CompiledFixtureData.class);
    public transient int generation;
    public transient boolean valid;

    @Override
    protected void reset() {
        ensureStorage();
        fixtures.clear();
        generation = 0;
        valid = false;
    }

    public void replaceWith(Array<CompiledFixtureData> candidate) {
        ensureStorage();
        fixtures.clear();
        if (candidate != null) {
            for (int i = 0; i < candidate.size; i++) {
                CompiledFixtureData fixture = candidate.get(i);
                if (fixture == null) {
                    throw new IllegalArgumentException(
                            "Compiled fixture candidate contains a null entry at index " + i + ".");
                }
                fixtures.add(fixture.copy());
            }
        }
        generation++;
        valid = true;
    }

    public void invalidate() {
        ensureStorage();
        fixtures.clear();
        valid = false;
    }

    private void ensureStorage() {
        if (fixtures == null) {
            fixtures = new Array<>(true, 4, CompiledFixtureData.class);
        }
    }
}
