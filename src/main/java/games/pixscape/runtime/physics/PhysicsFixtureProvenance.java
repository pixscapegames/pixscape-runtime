package games.pixscape.runtime.physics;

/**
 * Transient logical provenance attached to a native Box2D fixture.
 */
public final class PhysicsFixtureProvenance {
    public final int bodyEntityId;
    public final int physicsShapeId;
    public final int partIndex;

    public PhysicsFixtureProvenance(int bodyEntityId, int physicsShapeId, int partIndex) {
        this.bodyEntityId = bodyEntityId;
        this.physicsShapeId = physicsShapeId;
        this.partIndex = partIndex;
    }
}
