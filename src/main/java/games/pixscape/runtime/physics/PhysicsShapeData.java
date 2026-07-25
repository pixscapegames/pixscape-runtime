package games.pixscape.runtime.physics;

/**
 * Persistent business source for one logical physics shape.
 */
public final class PhysicsShapeData {
    public int physicsShapeId = 0;
    public PhysicsDirectGeometryData directGeometry;

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
        copy.directGeometry = directGeometry != null ? directGeometry.copy() : null;
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
        if (directGeometry == null) {
            throw invalid(
                    "directGeometry is missing; external/spatial geometry is unavailable "
                            + "before binding Phase D.");
        }
        directGeometry.validate(physicsShapeId);
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
    }

    public boolean contentEquals(PhysicsShapeData other) {
        return other != null
                && physicsShapeId == other.physicsShapeId
                && (directGeometry == null
                ? other.directGeometry == null
                : directGeometry.contentEquals(other.directGeometry))
                && Float.compare(density, other.density) == 0
                && Float.compare(friction, other.friction) == 0
                && Float.compare(restitution, other.restitution) == 0
                && sensor == other.sensor
                && categoryBits == other.categoryBits
                && maskBits == other.maskBits
                && groupIndex == other.groupIndex
                && enabled == other.enabled;
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
