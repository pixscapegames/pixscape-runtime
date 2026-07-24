package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

/**
 * Complete, validated fixture-cache candidate produced by {@link PhysicsBodyCompiler}.
 */
public final class PreparedCompiledFixtures {
    private final Array<CompiledFixtureData> fixtures;

    PreparedCompiledFixtures(Array<CompiledFixtureData> fixtures) {
        this.fixtures = fixtures;
    }

    public Array<CompiledFixtureData> fixtures() {
        return fixtures;
    }
}
