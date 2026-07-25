package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Array;

/**
 * Pure physics shape compiler independent of ECS and native Box2D.
 */
public final class PhysicsShapeCompiler {
    public static final int COMPILER_VERSION = PolygonDecomposer.ALGORITHM_VERSION;

    private static final CompiledFixtureData[] NO_FIXTURES = new CompiledFixtureData[0];

    public CompiledFixtureData[] compile(ResolvedPhysicsShape source) {
        validateResolvedSource(source);
        if (!source.enabled) {
            return NO_FIXTURES;
        }

        switch (source.shapeType) {
            case PhysicsDirectGeometryData.SHAPE_BOX:
                return new CompiledFixtureData[]{compileBox(source)};
            case PhysicsDirectGeometryData.SHAPE_CIRCLE:
                return new CompiledFixtureData[]{compileCircle(source)};
            case PhysicsDirectGeometryData.SHAPE_POLYGON:
                return compilePolygon(source);
            default:
                throw failure(source, -1, -1, "unsupported shapeType " + source.shapeType + ".");
        }
    }

    private static CompiledFixtureData compileBox(ResolvedPhysicsShape source) {
        validatePositiveFinite(source, source.halfWidth, "halfWidth");
        validatePositiveFinite(source, source.halfHeight, "halfHeight");
        CompiledFixtureData compiled = base(source, 0);
        compiled.shapeType = PhysicsDirectGeometryData.SHAPE_BOX;
        compiled.halfWidth = source.halfWidth;
        compiled.halfHeight = source.halfHeight;
        validateCompiled(source, compiled);
        return compiled;
    }

    private static CompiledFixtureData compileCircle(ResolvedPhysicsShape source) {
        validatePositiveFinite(source, source.radius, "radius");
        CompiledFixtureData compiled = base(source, 0);
        compiled.shapeType = PhysicsDirectGeometryData.SHAPE_CIRCLE;
        compiled.radius = source.radius;
        validateCompiled(source, compiled);
        return compiled;
    }

    private static CompiledFixtureData[] compilePolygon(ResolvedPhysicsShape source) {
        PolygonBuildResult build =
                PolygonDecomposer.build(source.polygonVertices, source.polygonVertexCount);
        if (!build.isValid()) {
            PolygonValidationResult validation = build.validation();
            throw failure(
                    source,
                    -1,
                    validation != null ? validation.code() : -1,
                    build.message());
        }

        Array<PolygonPartData> parts = build.parts();
        CompiledFixtureData[] compiled = new CompiledFixtureData[parts.size];
        for (int i = 0; i < parts.size; i++) {
            PolygonPartData part = parts.get(i);
            if (part == null) {
                throw failure(source, i, -1, "polygon decomposition produced a null part.");
            }
            CompiledFixtureData fixture = base(source, i);
            fixture.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
            fixture.polygonVertexCount = part.vertexCount;
            fixture.polygonVertices = copyVertices(part.vertices, part.vertexCount);
            validateCompiled(source, fixture);
            compiled[i] = fixture;
        }
        return compiled;
    }

    private static CompiledFixtureData base(ResolvedPhysicsShape source, int partIndex) {
        CompiledFixtureData compiled = new CompiledFixtureData();
        compiled.physicsShapeId = source.physicsShapeId;
        compiled.partIndex = partIndex;
        compiled.offsetX = source.offsetX;
        compiled.offsetY = source.offsetY;
        compiled.angleDegrees = source.angleDegrees;
        compiled.density = source.density;
        compiled.friction = source.friction;
        compiled.restitution = source.restitution;
        compiled.sensor = source.sensor;
        compiled.categoryBits = source.categoryBits;
        compiled.maskBits = source.maskBits;
        compiled.groupIndex = source.groupIndex;
        return compiled;
    }

    private static void validateResolvedSource(ResolvedPhysicsShape source) {
        if (source == null) {
            throw new PhysicsShapeCompilationException(
                    0, -1, -1, "ResolvedPhysicsShape cannot be null.");
        }
        try {
            PhysicsShapeIdAllocator.validatePhysicsShapeId(source.physicsShapeId);
        } catch (IllegalArgumentException ex) {
            throw failure(source, -1, -1, ex.getMessage());
        }
        validateFinite(source, source.offsetX, "offsetX");
        validateFinite(source, source.offsetY, "offsetY");
        validateFinite(source, source.angleDegrees, "angleDegrees");
        validateFinite(source, source.density, "density");
        validateFinite(source, source.friction, "friction");
        validateFinite(source, source.restitution, "restitution");
        if (source.density < 0f || source.friction < 0f || source.restitution < 0f) {
            throw failure(source, -1, -1, "material values must be non-negative.");
        }
    }

    private static void validateCompiled(
            ResolvedPhysicsShape source, CompiledFixtureData compiled) {
        try {
            compiled.validate();
        } catch (IllegalArgumentException ex) {
            throw failure(source, compiled.partIndex, -1, ex.getMessage());
        }
    }

    private static void validatePositiveFinite(
            ResolvedPhysicsShape source, float value, String field) {
        validateFinite(source, value, field);
        if (value <= 0f) {
            throw failure(source, -1, -1, field + " must be strictly positive.");
        }
    }

    private static void validateFinite(
            ResolvedPhysicsShape source, float value, String field) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw failure(source, -1, -1, field + " must be finite.");
        }
    }

    private static float[] copyVertices(float[] source, int count) {
        if (source == null || count < 0 || count > source.length / 2) {
            return new float[0];
        }
        float[] copy = new float[count * 2];
        System.arraycopy(source, 0, copy, 0, copy.length);
        return copy;
    }

    private static PhysicsShapeCompilationException failure(
            ResolvedPhysicsShape source, int partIndex, int reasonCode, String detail) {
        return new PhysicsShapeCompilationException(
                source != null ? source.physicsShapeId : 0,
                partIndex,
                reasonCode,
                detail);
    }
}
