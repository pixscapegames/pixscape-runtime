package games.pixscape.runtime.physics;

import java.util.Arrays;

/**
 * Transient, fully resolved input to {@link PhysicsShapeCompiler}.
 */
public final class ResolvedPhysicsShape {
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
    public String diagnosticSource;

    public static ResolvedPhysicsShape fromDirect(PhysicsShapeData source) {
        if (source == null) {
            throw new IllegalArgumentException("PhysicsShapeData cannot be null.");
        }
        source.validateStructure();

        ResolvedPhysicsShape resolved = new ResolvedPhysicsShape();
        resolved.physicsShapeId = source.physicsShapeId;
        resolved.shapeType = source.shapeType;
        resolved.halfWidth = source.halfWidth;
        resolved.halfHeight = source.halfHeight;
        resolved.radius = source.radius;
        resolved.polygonVertices = source.polygonVertices != null
                ? Arrays.copyOf(source.polygonVertices, source.polygonVertices.length)
                : new float[0];
        resolved.polygonVertexCount = source.polygonVertexCount;
        resolved.offsetX = source.offsetX;
        resolved.offsetY = source.offsetY;
        resolved.angleDegrees = source.angleDegrees;
        resolved.density = source.density;
        resolved.friction = source.friction;
        resolved.restitution = source.restitution;
        resolved.sensor = source.sensor;
        resolved.categoryBits = source.categoryBits;
        resolved.maskBits = source.maskBits;
        resolved.groupIndex = source.groupIndex;
        resolved.enabled = source.enabled;
        resolved.diagnosticSource = "direct";
        return resolved;
    }

    public ResolvedPhysicsShape copy() {
        ResolvedPhysicsShape copy = new ResolvedPhysicsShape();
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
        copy.diagnosticSource = diagnosticSource;
        return copy;
    }
}
