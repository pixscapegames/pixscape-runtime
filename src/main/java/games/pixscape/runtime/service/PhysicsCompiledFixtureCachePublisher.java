package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.physics.CompiledFixtureData;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API. Publishes prepared physics results into the passive ECS cache.
 */
public final class PhysicsCompiledFixtureCachePublisher {
    public int publish(
            PhysicsCompiledFixturesComponent target,
            Array<CompiledFixtureData> fixtures) {
        if (target == null || fixtures == null) {
            throw new IllegalArgumentException(
                    "Compiled cache target and transferred fixtures are required.");
        }
        target.fixtures = fixtures;
        target.generation++;
        target.valid = true;
        return target.generation;
    }

    public int invalidate(PhysicsCompiledFixturesComponent target) {
        if (target == null) {
            throw new IllegalArgumentException("Compiled cache target is required.");
        }
        if (target.fixtures == null) {
            target.fixtures = new Array<>(true, 4, CompiledFixtureData.class);
        } else {
            target.fixtures.clear();
        }
        target.generation++;
        target.valid = false;
        return target.generation;
    }
}
