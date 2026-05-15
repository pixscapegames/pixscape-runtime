package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;

public final class PhysicsFixturesComponent extends PooledComponent {
    public Array<FixtureDefData> fixtures = new Array<>(FixtureDefData[]::new);

    @Override
    protected void reset() {
        if (fixtures == null) {
            fixtures = new Array<>(FixtureDefData[]::new);
        } else {
            fixtures.clear();
        }
    }

    public boolean hasFixtures() {
        return fixtures.size > 0;
    }
}
