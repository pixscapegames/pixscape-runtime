package games.pixscape.runtime.physics;

/**
 * Persistent scene-scoped storage used by the physics shape ID allocator.
 */
public interface PhysicsShapeIdState {
    int getNextPhysicsShapeId();

    void setNextPhysicsShapeId(int nextPhysicsShapeId);
}
