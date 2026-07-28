package games.pixscape.runtime.physics;

import java.util.Arrays;

/**
 * Compiled Box2D-compatible product with no persistent fixture identity.
 */
public final class CompiledFixtureData {
    public int shapeType;

    public float halfWidth;
    public float halfHeight;
    public float radius;
    public float[] polygonVertices = new float[0];
    public int polygonVertexCount;

    public float offsetX;
    public float offsetY;
    public float angleDegrees;

    public float density;
    public float friction;
    public float restitution;
    public boolean sensor;

    public short categoryBits;
    public short maskBits;
    public short groupIndex;

    /** Transient source provenance, not fixture identity. */
    public int physicsShapeId;
    /** Transient index within one compilation result. */
    public int partIndex;

    public CompiledFixtureData copy() {
        CompiledFixtureData copy = new CompiledFixtureData();
        copy.shapeType = shapeType;
        copy.halfWidth = halfWidth;
        copy.halfHeight = halfHeight;
        copy.radius = radius;
        copy.polygonVertices = polygonVertices != null
                ? Arrays.copyOf(polygonVertices, polygonVertices.length)
                : new float[0];
        copy.polygonVertexCount = polygonVertexCount;
        copy.offsetX = offsetX;
        copy.offsetY = offsetY;
        copy.angleDegrees = angleDegrees;
        copy.density = density;
        copy.friction = friction;
        copy.restitution = restitution;
        copy.sensor = sensor;
        copy.categoryBits = categoryBits;
        copy.maskBits = maskBits;
        copy.groupIndex = groupIndex;
        copy.physicsShapeId = physicsShapeId;
        copy.partIndex = partIndex;
        return copy;
    }

    public void validate() {
        PhysicsShapeIdAllocator.validatePhysicsShapeId(physicsShapeId);
        if (partIndex < 0) {
            throw invalid("partIndex must be non-negative.");
        }
        validateFinite(offsetX, "offsetX");
        validateFinite(offsetY, "offsetY");
        validateFinite(angleDegrees, "angleDegrees");
        validateFinite(density, "density");
        validateFinite(friction, "friction");
        validateFinite(restitution, "restitution");
        if (density < 0f || friction < 0f || restitution < 0f) {
            throw invalid("material values must be non-negative.");
        }

        switch (shapeType) {
            case PhysicsGeometryData.SHAPE_BOX:
                validatePositiveFinite(halfWidth, "halfWidth");
                validatePositiveFinite(halfHeight, "halfHeight");
                break;
            case PhysicsGeometryData.SHAPE_CIRCLE:
                validatePositiveFinite(radius, "radius");
                break;
            case PhysicsGeometryData.SHAPE_POLYGON:
                PolygonValidationResult validation =
                        PolygonValidator.validate(polygonVertices, polygonVertexCount);
                if (!validation.isValid()) {
                    throw invalid(validation.message());
                }
                if (polygonVertexCount > PolygonDecomposer.BOX2D_MAX_POLYGON_VERTICES) {
                    throw invalid("compiled polygon exceeds the Box2D vertex limit.");
                }
                if (!PolygonValidator.isConvex(polygonVertices, polygonVertexCount)) {
                    throw invalid("compiled polygon must be convex.");
                }
                break;
            default:
                throw invalid("unsupported shapeType " + shapeType + ".");
        }
    }

    private void validatePositiveFinite(float value, String field) {
        validateFinite(value, field);
        if (value <= 0f) {
            throw invalid(field + " must be strictly positive.");
        }
    }

    private void validateFinite(float value, String field) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw invalid(field + " must be finite.");
        }
    }

    private IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(
                "Invalid compiled fixture for physicsShapeId " + physicsShapeId
                        + ", partIndex " + partIndex + ": " + detail);
    }
}
