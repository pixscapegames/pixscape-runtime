package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.IntSet;

/**
 * The single allocation authority for scene-scoped {@code physicsShapeId} values.
 */
public final class PhysicsShapeIdAllocator {
    private final PhysicsShapeIdState state;

    public PhysicsShapeIdAllocator(PhysicsShapeIdState state) {
        if (state == null) {
            throw new IllegalArgumentException("Physics shape ID state cannot be null.");
        }
        this.state = state;
        validateNextPhysicsShapeId(state.getNextPhysicsShapeId());
    }

    public int allocateNewPhysicsShapeId() {
        int next = state.getNextPhysicsShapeId();
        validateNextPhysicsShapeId(next);
        if (next == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Physics shape ID space is exhausted: nextPhysicsShapeId is Integer.MAX_VALUE.");
        }
        state.setNextPhysicsShapeId(next + 1);
        return next;
    }

    public int nextPhysicsShapeId() {
        int next = state.getNextPhysicsShapeId();
        validateNextPhysicsShapeId(next);
        return next;
    }

    /**
     * Validates a restored identity without allocating or advancing the high-water mark.
     */
    public void validateRestoredPhysicsShapeId(int physicsShapeId) {
        validatePhysicsShapeId(physicsShapeId);
        int next = nextPhysicsShapeId();
        if (physicsShapeId >= next) {
            throw new IllegalArgumentException(
                    "Restored physicsShapeId " + physicsShapeId
                            + " must be lower than nextPhysicsShapeId " + next + ".");
        }
    }

    /**
     * Validates the complete persisted identity set during scene loading.
     */
    public void validatePersistedPhysicsShapeIds(int[] physicsShapeIds) {
        int next = nextPhysicsShapeId();
        if (physicsShapeIds == null || physicsShapeIds.length == 0) {
            return;
        }

        IntSet seen = new IntSet(physicsShapeIds.length);
        for (int i = 0; i < physicsShapeIds.length; i++) {
            int physicsShapeId = physicsShapeIds[i];
            validatePhysicsShapeId(physicsShapeId);
            if (!seen.add(physicsShapeId)) {
                throw new IllegalArgumentException(
                        "Duplicate persisted physicsShapeId " + physicsShapeId + ".");
            }
            if (physicsShapeId >= next) {
                throw new IllegalArgumentException(
                        "Persisted physicsShapeId " + physicsShapeId
                                + " must be lower than nextPhysicsShapeId " + next + ".");
            }
        }
    }

    public static void validatePhysicsShapeId(int physicsShapeId) {
        if (physicsShapeId <= 0) {
            throw new IllegalArgumentException(
                    "physicsShapeId must be strictly positive: " + physicsShapeId + ".");
        }
    }

    public static void validateNextPhysicsShapeId(int nextPhysicsShapeId) {
        if (nextPhysicsShapeId <= 0) {
            throw new IllegalArgumentException(
                    "nextPhysicsShapeId must be strictly positive: "
                            + nextPhysicsShapeId + ".");
        }
    }
}
