package games.pixscape.runtime.component.physics;

import com.artemis.PooledComponent;
import com.badlogic.gdx.utils.Array;

public final class PhysicsFixturesComponent extends PooledComponent {
    public final Array<FixtureDefData> fixtures = new Array<>();

    @Override
    protected void reset() {
        fixtures.clear();
    }

    public boolean hasFixtures() {
        return fixtures.size > 0;
    }
}
