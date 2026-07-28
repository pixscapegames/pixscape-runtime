package games.pixscape.runtime.physics;

/**
 * Pure authored-to-resolved physics shape mapping.
 */
public final class PhysicsShapeResolver {
    public ResolvedPhysicsShape resolve(PhysicsShapeData source) {
        if (source == null) {
            throw new IllegalArgumentException("PhysicsShapeData cannot be null.");
        }
        source.validateStructure();
        if (source.spatialBlockId > 0) {
            throw new IllegalArgumentException(
                    "Linked physics shape resolution is not available before "
                            + "the Spatial resolver slice.");
        }
        return ResolvedPhysicsShape.fromGeometry(source);
    }
}
