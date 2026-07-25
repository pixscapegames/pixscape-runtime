package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;

/**
 * Atomically compiles every enabled logical shape of one body.
 */
public final class PhysicsBodyCompiler {
    private final PhysicsShapeCompiler shapeCompiler;

    public PhysicsBodyCompiler() {
        this(new PhysicsShapeCompiler());
    }

    public PhysicsBodyCompiler(PhysicsShapeCompiler shapeCompiler) {
        if (shapeCompiler == null) {
            throw new IllegalArgumentException("PhysicsShapeCompiler cannot be null.");
        }
        this.shapeCompiler = shapeCompiler;
    }

    public PreparedCompiledFixtures compile(Array<ResolvedPhysicsShape> sources) {
        if (sources == null) {
            throw new IllegalArgumentException("Resolved physics shapes cannot be null.");
        }

        Array<CompiledFixtureData> candidate =
                new Array<>(true, Math.max(1, sources.size), CompiledFixtureData.class);
        IntSet seen = new IntSet(Math.max(1, sources.size));
        for (int sourceIndex = 0; sourceIndex < sources.size; sourceIndex++) {
            ResolvedPhysicsShape source = sources.get(sourceIndex);
            if (source == null) {
                throw new PhysicsShapeCompilationException(
                        0, -1, -1, "Body source at index " + sourceIndex + " is null.");
            }
            if (!seen.add(source.physicsShapeId)) {
                throw new PhysicsShapeCompilationException(
                        source.physicsShapeId, -1, -1,
                        "Duplicate physicsShapeId in one body.");
            }

            CompiledFixtureData[] compiled = shapeCompiler.compile(source);
            for (int partIndex = 0; partIndex < compiled.length; partIndex++) {
                CompiledFixtureData fixture = compiled[partIndex];
                if (fixture == null) {
                    throw new PhysicsShapeCompilationException(
                            source.physicsShapeId, partIndex, -1,
                            "Compiler produced a null fixture.");
                }
                fixture.validate();
                candidate.add(fixture);
            }
        }
        Array<CompiledFixtureData> prepared =
                new Array<>(true, candidate.size, CompiledFixtureData.class);
        for (int i = 0; i < candidate.size; i++) {
            CompiledFixtureData fixture = candidate.get(i);
            if (fixture == null) {
                throw new IllegalArgumentException(
                        "Compiled fixture candidate contains a null entry at index " + i + ".");
            }
            fixture.validate();
            prepared.add(fixture.copy());
        }
        return new PreparedCompiledFixtures(prepared);
    }
}
