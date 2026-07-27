package games.pixscape.runtime.service;

import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.spatial.SpatialBlockData;

/** Read-only owner-local binding queries used during linked physics preparation. */
interface BlockPhysicsBindingLookup {
    BlockPhysicsBindingData findByPhysicsShapeId(int physicsShapeId);

    SpatialBlockData findBlock(int ownerStableId, int spatialBlockId);

    int findOwnerEntityByPhysicsShapeId(int physicsShapeId);
}
