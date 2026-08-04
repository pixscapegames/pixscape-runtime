package games.pixscape.runtime.physics;

/**
 * Persistent business source for one logical physics shape.
 */
public final class PhysicsShapeData {
    public int physicsShapeId = 0;
    public int spatialBlockId = 0;
    /** Explicit owner of this entity's Spatial rendering footprint. */
    public boolean spatialFootprint = false;
    public PhysicsGeometryData geometry;

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
        copy.spatialBlockId = spatialBlockId;
        copy.spatialFootprint = spatialFootprint;
        copy.geometry = geometry != null ? geometry.copy() : null;
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
        if (spatialBlockId < 0) {
            throw invalid("spatialBlockId must be non-negative.");
        }
        if (spatialBlockId == 0) {
            if (geometry == null) {
                throw invalid("manual shape geometry is required.");
            }
            geometry.validate(physicsShapeId);
        } else if (geometry != null) {
            throw invalid("linked shape geometry must be null.");
        }
        if (spatialFootprint) {
            validateSpatialFootprint();
        }
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
                && spatialBlockId == other.spatialBlockId
                && spatialFootprint == other.spatialFootprint
                && (geometry == null
                ? other.geometry == null
                : geometry.contentEquals(other.geometry))
                && Float.compare(density, other.density) == 0
                && Float.compare(friction, other.friction) == 0
                && Float.compare(restitution, other.restitution) == 0
                && sensor == other.sensor
                && categoryBits == other.categoryBits
                && maskBits == other.maskBits
                && groupIndex == other.groupIndex
                && enabled == other.enabled;
    }

    private void validateSpatialFootprint() {
        if (spatialBlockId != 0) {
            throw invalid("spatial footprint must be a manual shape.");
        }
        if (geometry == null) {
            throw invalid("spatial footprint geometry is required.");
        }
        if (geometry.shapeType != PhysicsGeometryData.SHAPE_CIRCLE) {
            throw invalid("spatial footprint must use circle geometry.");
        }
        if (!enabled) {
            throw invalid("spatial footprint must be enabled.");
        }
        if (sensor) {
            throw invalid("spatial footprint must not be a sensor.");
        }
        validateFinite(geometry.radius, "spatial footprint radius");
        if (geometry.radius <= 0f) {
            throw invalid("spatial footprint radius must be strictly positive.");
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
