package games.pixscape.runtime.physics;

import java.util.Arrays;

/**
 * Persistent business source for one logical physics shape.
 */
public final class PhysicsShapeData {
    public static final int SHAPE_BOX = 0;
    public static final int SHAPE_CIRCLE = 1;
    public static final int SHAPE_POLYGON = 2;

    public int physicsShapeId = 0;
    public int shapeType = SHAPE_BOX;

    public float halfWidth = 0.5f;
    public float halfHeight = 0.5f;
    public float radius = 0.5f;
    public float[] polygonVertices = new float[0];
    public int polygonVertexCount = 0;

    public float offsetX = 0f;
    public float offsetY = 0f;
    public float angleDegrees = 0f;

    public float density = 1f;
    public float friction = 0.2f;
    public float restitution = 0f;
    public boolean sensor = false;

    public short categoryBits = 0x0001;
    public short maskBits = (short) 0xFFFF;
    public short groupIndex = 0;

    public boolean enabled = true;

    public PhysicsShapeData copy() {
        PhysicsShapeData copy = new PhysicsShapeData();
        copy.physicsShapeId = physicsShapeId;
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
        copy.enabled = enabled;
        return copy;
    }

    /**
     * Checks the serialized union layout without performing polygon decomposition.
     */
    public void validateStructure() {
        PhysicsShapeIdAllocator.validatePhysicsShapeId(physicsShapeId);
        validateFinite(offsetX, "offsetX");
        validateFinite(offsetY, "offsetY");
        validateFinite(angleDegrees, "angleDegrees");
        validateFinite(density, "density");
        validateFinite(friction, "friction");
        validateFinite(restitution, "restitution");
        if (density < 0f) {
            throw invalid("density must be non-negative.");
        }
        if (friction < 0f) {
            throw invalid("friction must be non-negative.");
        }
        if (restitution < 0f) {
            throw invalid("restitution must be non-negative.");
        }

        switch (shapeType) {
            case SHAPE_BOX:
                validatePositiveFinite(halfWidth, "halfWidth");
                validatePositiveFinite(halfHeight, "halfHeight");
                break;
            case SHAPE_CIRCLE:
                validatePositiveFinite(radius, "radius");
                break;
            case SHAPE_POLYGON:
                if (polygonVertices == null) {
                    throw invalid("polygonVertices cannot be null.");
                }
                if (polygonVertexCount < 3) {
                    throw invalid("polygonVertexCount must be at least 3.");
                }
                if (polygonVertexCount > polygonVertices.length / 2) {
                    throw invalid("polygonVertices is smaller than polygonVertexCount.");
                }
                break;
            default:
                throw invalid("unsupported shapeType " + shapeType + ".");
        }
    }

    public boolean contentEquals(PhysicsShapeData other) {
        return other != null
                && physicsShapeId == other.physicsShapeId
                && shapeType == other.shapeType
                && Float.compare(halfWidth, other.halfWidth) == 0
                && Float.compare(halfHeight, other.halfHeight) == 0
                && Float.compare(radius, other.radius) == 0
                && Arrays.equals(polygonVertices, other.polygonVertices)
                && polygonVertexCount == other.polygonVertexCount
                && Float.compare(offsetX, other.offsetX) == 0
                && Float.compare(offsetY, other.offsetY) == 0
                && Float.compare(angleDegrees, other.angleDegrees) == 0
                && Float.compare(density, other.density) == 0
                && Float.compare(friction, other.friction) == 0
                && Float.compare(restitution, other.restitution) == 0
                && sensor == other.sensor
                && categoryBits == other.categoryBits
                && maskBits == other.maskBits
                && groupIndex == other.groupIndex
                && enabled == other.enabled;
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
                "Invalid PhysicsShapeData " + physicsShapeId + ": " + detail);
    }
}
