package games.pixscape.runtime.physics;

/**
 * Persistent owner-local relation between one spatial block and one physics shape.
 */
public final class BlockPhysicsBindingData {
    public int spatialBlockId;
    public int physicsShapeId;

    public BlockPhysicsBindingData copy() {
        BlockPhysicsBindingData copy = new BlockPhysicsBindingData();
        copy.spatialBlockId = spatialBlockId;
        copy.physicsShapeId = physicsShapeId;
        return copy;
    }
}
