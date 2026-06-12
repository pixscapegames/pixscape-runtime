package games.pixscape.runtime.spatial;

public final class SpatialBucketDrawListComposer {
    public interface SlotClassifier {
        boolean isActorSlot(int slot);
    }

    public int composedSize;
    public int[] composedSlots = new int[0];

    public int compose(int[] inputSlots,
                       int inputSize,
                       SpatialActorCollector actors,
                       SpatialBucketPlanner planner,
                       SlotClassifier classifier) {
        if (inputSlots == null) {
            throw new IllegalArgumentException("Input draw slots are required.");
        }
        if (inputSize < 0 || inputSize > inputSlots.length) {
            throw new IllegalArgumentException("Invalid input draw-list size: " + inputSize);
        }
        if (actors == null || planner == null || actors.actorCount != planner.actorCount) {
            throw new IllegalArgumentException("Matching actor snapshot and bucket planner are required.");
        }
        if (classifier == null) {
            throw new IllegalArgumentException("Slot classifier is required.");
        }

        ensureComposedCapacity(inputSize);
        int write = 0;
        int bucket = 0;
        write = emitBucket(write, bucket, actors, planner);
        for (int i = 0; i < inputSize; i++) {
            int slot = inputSlots[i];
            if (classifier.isActorSlot(slot)) continue;
            composedSlots[write++] = slot;
            bucket++;
            write = emitBucket(write, bucket, actors, planner);
        }

        if (write != inputSize) {
            throw new IllegalStateException("Spatial bucket composition changed draw-list size: expected="
                    + inputSize + " actual=" + write);
        }
        composedSize = write;
        return composedSize;
    }

    private int emitBucket(int write,
                           int bucket,
                           SpatialActorCollector actors,
                           SpatialBucketPlanner planner) {
        if (bucket < 0 || bucket >= planner.bucketCount) return write;
        int start = planner.bucketActorStart(bucket);
        int count = planner.bucketActorCount(bucket);
        for (int i = 0; i < count; i++) {
            int actor = planner.sortedActorIndex[start + i];
            planner.finalActorDrawIndex[actor] = write;
            composedSlots[write++] = actors.actorSlot[actor];
        }
        return write;
    }

    private void ensureComposedCapacity(int required) {
        if (required <= composedSlots.length) return;
        int next = Math.max(8, composedSlots.length);
        while (required > next) next <<= 1;
        int[] expanded = new int[next];
        System.arraycopy(composedSlots, 0, expanded, 0, composedSlots.length);
        composedSlots = expanded;
    }
}
