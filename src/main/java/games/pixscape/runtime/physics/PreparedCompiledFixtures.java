package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

/**
 * Complete, validated fixture-cache candidate produced by {@link PhysicsBodyCompiler}.
 */
public final class PreparedCompiledFixtures {
    private Array<CompiledFixtureData> fixtures;

    PreparedCompiledFixtures(Array<CompiledFixtureData> fixtures) {
        this.fixtures = fixtures;
    }

    public Array<CompiledFixtureData> takeFixtures() {
        if (fixtures == null) {
            throw new IllegalStateException("Prepared compiled fixtures were already consumed.");
        }
        Array<CompiledFixtureData> result = fixtures;
        fixtures = null;
        return result;
    }
}
