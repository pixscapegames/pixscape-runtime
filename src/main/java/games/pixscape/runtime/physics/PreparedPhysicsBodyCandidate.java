package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

/**
 * Runtime implementation detail. Public Java visibility does not make this type part of the
 * supported compatibility API. This single-use transfer object holds detached authored sources
 * and a compiled cache for failure-atomic publication.
 */
public final class PreparedPhysicsBodyCandidate {
    private Array<PhysicsShapeData> shapes;
    private PreparedCompiledFixtures compiledFixtures;

    public PreparedPhysicsBodyCandidate(
            Array<PhysicsShapeData> shapes,
            PreparedCompiledFixtures compiledFixtures) {
        if (shapes == null || compiledFixtures == null) {
            throw new IllegalArgumentException(
                    "Prepared physics shapes and compiled fixtures are required.");
        }
        this.shapes = shapes;
        this.compiledFixtures = compiledFixtures;
    }

    public Array<PhysicsShapeData> takeShapes() {
        if (shapes == null) {
            throw new IllegalStateException("Prepared physics shapes were already consumed.");
        }
        Array<PhysicsShapeData> result = shapes;
        shapes = null;
        return result;
    }

    public PreparedCompiledFixtures takeCompiledFixtures() {
        if (compiledFixtures == null) {
            throw new IllegalStateException("Prepared compiled fixtures were already consumed.");
        }
        PreparedCompiledFixtures result = compiledFixtures;
        compiledFixtures = null;
        return result;
    }
}
