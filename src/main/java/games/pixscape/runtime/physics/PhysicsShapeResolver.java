package games.pixscape.runtime.physics;

/**
 * Pure authored-to-resolved physics shape mapping.
 */
public final class PhysicsShapeResolver {
    public ResolvedPhysicsShape resolve(PhysicsShapeData source) {
        if (source == null) {
            throw new IllegalArgumentException("PhysicsShapeData cannot be null.");
        }
        if (source.directGeometry == null) {
            throw new IllegalArgumentException(
                    "PhysicsShapeData " + source.physicsShapeId
                            + ": external/spatial geometry is unavailable before binding Phase D.");
        }
        source.validateStructure();
        return ResolvedPhysicsShape.fromDirect(source);
    }
}
