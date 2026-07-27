package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;

/** Detached, single-use publication data for one spatial block bind. */
final class PreparedWorldBlockMutation {
    private int ownerEntityId;
    private int physicsShapeId;
    private Array<BlockPhysicsBindingData> bindings;
    private PreparedPhysicsBodyCandidate physics;
    private BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot;
    private final boolean createTransform;
    private final boolean createBody;

    PreparedWorldBlockMutation(int ownerEntityId, int physicsShapeId,
                               Array<BlockPhysicsBindingData> bindings,
                               PreparedPhysicsBodyCandidate physics,
                               BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot,
                               boolean createTransform, boolean createBody) {
        this.ownerEntityId = ownerEntityId;
        this.physicsShapeId = physicsShapeId;
        this.bindings = bindings;
        this.physics = physics;
        this.repositorySnapshot = repositorySnapshot;
        this.createTransform = createTransform;
        this.createBody = createBody;
    }

    Publication takePublication() {
        if (bindings == null) {
            throw new IllegalStateException("Prepared world block mutation was already consumed.");
        }
        Publication publication = new Publication(ownerEntityId, physicsShapeId,
                bindings, physics, repositorySnapshot, createTransform, createBody);
        bindings = null;
        physics = null;
        repositorySnapshot = null;
        return publication;
    }

    static final class Publication {
        final int ownerEntityId;
        final int physicsShapeId;
        final Array<BlockPhysicsBindingData> bindings;
        final PreparedPhysicsBodyCandidate physics;
        final BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot;
        final boolean createTransform;
        final boolean createBody;

        Publication(int ownerEntityId, int physicsShapeId,
                    Array<BlockPhysicsBindingData> bindings,
                    PreparedPhysicsBodyCandidate physics,
                    BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot,
                    boolean createTransform, boolean createBody) {
            this.ownerEntityId = ownerEntityId;
            this.physicsShapeId = physicsShapeId;
            this.bindings = bindings;
            this.physics = physics;
            this.repositorySnapshot = repositorySnapshot;
            this.createTransform = createTransform;
            this.createBody = createBody;
        }
    }
}
