package games.pixscape.runtime.physics;

/**
 * Transient, fully resolved input to {@link PhysicsShapeCompiler}.
 */
public final class ResolvedPhysicsShape {
    public static final int SOURCE_DIRECT = 1;
    public static final int SOURCE_SPATIAL_BLOCK = 2;
    public int physicsShapeId;
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

    public boolean enabled;
    public int sourceKind;
    public int spatialOwnerStableId;
    public int spatialBlockId;

    public static ResolvedPhysicsShape fromDirect(PhysicsShapeData source) {
        if (source == null) {
            throw new IllegalArgumentException("PhysicsShapeData cannot be null.");
        }
        PhysicsDirectGeometryData geometry = source.directGeometry;
        if (geometry == null) {
            throw new IllegalArgumentException(
                    "PhysicsShapeData " + source.physicsShapeId + " has no directGeometry.");
        }

        ResolvedPhysicsShape resolved = new ResolvedPhysicsShape();
        resolved.physicsShapeId = source.physicsShapeId;
        PhysicsDirectGeometryData detached = geometry.copy();
        resolved.shapeType = detached.shapeType;
        resolved.halfWidth = detached.halfWidth;
        resolved.halfHeight = detached.halfHeight;
        resolved.radius = detached.radius;
        resolved.polygonVertices = detached.polygonVertices;
        resolved.polygonVertexCount = detached.polygonVertexCount;
        resolved.offsetX = detached.offsetX;
        resolved.offsetY = detached.offsetY;
        resolved.angleDegrees = detached.angleDegrees;
        resolved.density = source.density;
        resolved.friction = source.friction;
        resolved.restitution = source.restitution;
        resolved.sensor = source.sensor;
        resolved.categoryBits = source.categoryBits;
        resolved.maskBits = source.maskBits;
        resolved.groupIndex = source.groupIndex;
        resolved.enabled = source.enabled;
        resolved.sourceKind = SOURCE_DIRECT;
        return resolved;
    }

    public ResolvedPhysicsShape copy() {
        ResolvedPhysicsShape copy = new ResolvedPhysicsShape();
        copy.physicsShapeId = physicsShapeId;
        copy.shapeType = shapeType;
        copy.halfWidth = halfWidth;
        copy.halfHeight = halfHeight;
        copy.radius = radius;
        copy.polygonVertices = copyVertices(polygonVertices);
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
        copy.sourceKind = sourceKind;
        copy.spatialOwnerStableId = spatialOwnerStableId;
        copy.spatialBlockId = spatialBlockId;
        return copy;
    }

    private static float[] copyVertices(float[] source) {
        if (source == null) return new float[0];
        float[] copy = new float[source.length];
        System.arraycopy(source, 0, copy, 0, source.length);
        return copy;
    }
}
