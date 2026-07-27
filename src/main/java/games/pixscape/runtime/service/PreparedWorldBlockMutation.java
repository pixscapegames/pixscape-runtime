package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsShapeData;
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
        Array<PhysicsShapeData> shapes = physics.takeShapes();
        Array<CompiledFixtureData> fixtures = physics.takeCompiledFixtures().takeFixtures();
        Publication publication = new Publication(ownerEntityId, physicsShapeId,
                bindings, shapes, fixtures, repositorySnapshot, createTransform, createBody);
        bindings = null;
        physics = null;
        repositorySnapshot = null;
        return publication;
    }

    static final class Publication {
        final int ownerEntityId;
        final int physicsShapeId;
        final Array<BlockPhysicsBindingData> bindings;
        final Array<PhysicsShapeData> shapes;
        final Array<CompiledFixtureData> fixtures;
        final BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot;
        final boolean createTransform;
        final boolean createBody;

        Publication(int ownerEntityId, int physicsShapeId,
                    Array<BlockPhysicsBindingData> bindings,
                    Array<PhysicsShapeData> shapes, Array<CompiledFixtureData> fixtures,
                    BlockPhysicsBindingRepository.PreparedOwnerSnapshot repositorySnapshot,
                    boolean createTransform, boolean createBody) {
            this.ownerEntityId = ownerEntityId;
            this.physicsShapeId = physicsShapeId;
            this.bindings = bindings;
            this.shapes = shapes;
            this.fixtures = fixtures;
            this.repositorySnapshot = repositorySnapshot;
            this.createTransform = createTransform;
            this.createBody = createBody;
        }
    }
}
