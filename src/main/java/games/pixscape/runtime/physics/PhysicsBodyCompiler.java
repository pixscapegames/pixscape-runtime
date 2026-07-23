package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;

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

    public Array<CompiledFixtureData> compile(PhysicsShapesComponent sources) {
        if (sources == null || sources.shapes == null) {
            throw new IllegalArgumentException("PhysicsShapesComponent cannot be null.");
        }

        Array<CompiledFixtureData> candidate =
                new Array<>(true, Math.max(1, sources.shapes.size), CompiledFixtureData.class);
        IntSet seen = new IntSet(Math.max(1, sources.shapes.size));
        for (int sourceIndex = 0; sourceIndex < sources.shapes.size; sourceIndex++) {
            PhysicsShapeData source = sources.shapes.get(sourceIndex);
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
        return candidate;
    }
}
