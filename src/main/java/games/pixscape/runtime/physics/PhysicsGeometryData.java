package games.pixscape.runtime.physics;

import java.util.Arrays;

/**
 * Persistent authored geometry for one manual physics shape.
 */
public final class PhysicsGeometryData {
    public static final int SHAPE_BOX = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_POLYGON = 2;

    public int shapeType = SHAPE_BOX;
    public float offsetX;
    public float offsetY;
    public float angleDegrees;
    public float radius = 0.5f;
    public float halfWidth = 0.5f;
    public float halfHeight = 0.5f;
    public float[] polygonVertices = new float[0];
    public int polygonVertexCount;

    public PhysicsGeometryData copy() {
        PhysicsGeometryData copy = new PhysicsGeometryData();
        copy.shapeType = shapeType;
        copy.offsetX = offsetX;
        copy.offsetY = offsetY;
        copy.angleDegrees = angleDegrees;
        copy.radius = radius;
        copy.halfWidth = halfWidth;
        copy.halfHeight = halfHeight;
        copy.polygonVertices = polygonVertices != null
                ? Arrays.copyOf(polygonVertices, polygonVertices.length)
                : new float[0];
        copy.polygonVertexCount = polygonVertexCount;
        return copy;
    }

    public void validate(int physicsShapeId) {
        validateFinite(physicsShapeId, offsetX, "offsetX");
        validateFinite(physicsShapeId, offsetY, "offsetY");
        validateFinite(physicsShapeId, angleDegrees, "angleDegrees");
        switch (shapeType) {
            case SHAPE_BOX:
                validatePositiveFinite(physicsShapeId, halfWidth, "halfWidth");
                validatePositiveFinite(physicsShapeId, halfHeight, "halfHeight");
                break;
            case SHAPE_CIRCLE:
                validatePositiveFinite(physicsShapeId, radius, "radius");
                break;
            case SHAPE_POLYGON:
                if (polygonVertices == null) {
                    throw invalid(physicsShapeId, "polygonVertices cannot be null.");
                }
                if (polygonVertexCount < 3) {
                    throw invalid(physicsShapeId, "polygonVertexCount must be at least 3.");
                }
                if (polygonVertexCount > polygonVertices.length / 2) {
                    throw invalid(
                            physicsShapeId,
                            "polygonVertices is smaller than polygonVertexCount.");
                }
                for (int i = 0; i < polygonVertexCount * 2; i++) {
                    validateFinite(physicsShapeId, polygonVertices[i], "polygonVertices[" + i + "]");
                }
                break;
            default:
                throw invalid(physicsShapeId, "unsupported shapeType " + shapeType + ".");
        }
    }

    public boolean contentEquals(PhysicsGeometryData other) {
        return other != null
                && shapeType == other.shapeType
                && Float.compare(offsetX, other.offsetX) == 0
                && Float.compare(offsetY, other.offsetY) == 0
                && Float.compare(angleDegrees, other.angleDegrees) == 0
                && Float.compare(radius, other.radius) == 0
                && Float.compare(halfWidth, other.halfWidth) == 0
                && Float.compare(halfHeight, other.halfHeight) == 0
                && Arrays.equals(polygonVertices, other.polygonVertices)
                && polygonVertexCount == other.polygonVertexCount;
    }

    private static void validatePositiveFinite(
            int physicsShapeId, float value, String field) {
        validateFinite(physicsShapeId, value, field);
        if (value <= 0f) {
            throw invalid(physicsShapeId, field + " must be strictly positive.");
        }
    }

    private static void validateFinite(int physicsShapeId, float value, String field) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw invalid(physicsShapeId, field + " must be finite.");
        }
    }

    private static IllegalArgumentException invalid(int physicsShapeId, String detail) {
        return new IllegalArgumentException(
                "Invalid PhysicsShapeData " + physicsShapeId + ": " + detail);
    }
}
