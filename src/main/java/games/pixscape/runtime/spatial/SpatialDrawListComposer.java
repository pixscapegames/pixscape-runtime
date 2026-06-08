package games.pixscape.runtime.spatial;

public final class SpatialDrawListComposer {
    public interface SlotClassifier {
        boolean isActorSlot(int slot);
    }

    public int composedSize;
    public int[] composedSlots = new int[0];

    private int[] bucketHead = new int[0];
    private int[] bucketTail = new int[0];
    private int[] planNext = new int[0];

    public int compose(int[] inputSlots,
                       int inputSize,
                       SpatialInsertionPlanner planner,
                       SlotClassifier classifier) {
        if (inputSlots == null) {
            throw new IllegalArgumentException("Input draw slots are required.");
        }
        if (inputSize < 0 || inputSize > inputSlots.length) {
            throw new IllegalArgumentException("Invalid input draw-list size: " + inputSize);
        }
        if (planner == null || planner.planCount == 0) {
            ensureComposedCapacity(inputSize);
            System.arraycopy(inputSlots, 0, composedSlots, 0, inputSize);
            composedSize = inputSize;
            return composedSize;
        }
        if (classifier == null) {
            throw new IllegalArgumentException("Slot classifier is required.");
        }

        validatePlans(inputSlots, inputSize, planner, classifier);
        buildBuckets(inputSize, planner);

        ensureComposedCapacity(inputSize);
        int write = 0;
        for (int originalIndex = 0; originalIndex <= inputSize; originalIndex++) {
            for (int plan = bucketHead[originalIndex]; plan >= 0; plan = planNext[plan]) {
                composedSlots[write++] = planner.planActorSlot[plan];
            }

            if (originalIndex == inputSize) break;

            int slot = inputSlots[originalIndex];
            if (isPlannedActorSlot(slot, planner)) continue;
            composedSlots[write++] = slot;
        }

        if (write != inputSize) {
            throw new IllegalStateException("Spatial draw-list composition changed draw-list size: expected="
                    + inputSize + " actual=" + write);
        }
        composedSize = write;
        return composedSize;
    }

    public void clear() {
        composedSize = 0;
    }

    private void validatePlans(int[] inputSlots,
                               int inputSize,
                               SpatialInsertionPlanner planner,
                               SlotClassifier classifier) {
        for (int plan = 0; plan < planner.planCount; plan++) {
            int slot = planner.planActorSlot[plan];
            int target = planner.planTargetDrawIndex[plan];
            if (!classifier.isActorSlot(slot)) {
                throw new IllegalStateException("Spatial insertion plan does not reference an actor slot: slot=" + slot);
            }
            if (target < 0 || target > inputSize) {
                throw new IndexOutOfBoundsException("Invalid spatial insertion target draw index: " + target);
            }
            for (int other = 0; other < plan; other++) {
                if (planner.planActorSlot[other] == slot) {
                    throw new IllegalStateException("Duplicate spatial insertion plan for actor slot: " + slot);
                }
            }

            int occurrences = 0;
            for (int i = 0; i < inputSize; i++) {
                if (inputSlots[i] == slot) occurrences++;
            }
            if (occurrences != 1) {
                throw new IllegalStateException("Planned actor slot must appear exactly once in input draw list: slot="
                        + slot + " occurrences=" + occurrences);
            }
        }
    }

    private void buildBuckets(int inputSize, SpatialInsertionPlanner planner) {
        ensureBucketCapacity(inputSize + 1);
        ensurePlanNextCapacity(planner.planCount);
        for (int i = 0; i <= inputSize; i++) {
            bucketHead[i] = -1;
            bucketTail[i] = -1;
        }
        for (int i = 0; i < planner.planCount; i++) {
            planNext[i] = -1;
        }

        for (int plan = 0; plan < planner.planCount; plan++) {
            int target = planner.planTargetDrawIndex[plan];
            if (bucketHead[target] < 0) {
                bucketHead[target] = plan;
                bucketTail[target] = plan;
            } else {
                planNext[bucketTail[target]] = plan;
                bucketTail[target] = plan;
            }
        }
    }

    private static boolean isPlannedActorSlot(int slot, SpatialInsertionPlanner planner) {
        for (int plan = 0; plan < planner.planCount; plan++) {
            if (planner.planActorSlot[plan] == slot) return true;
        }
        return false;
    }

    private void ensureComposedCapacity(int required) {
        if (required <= composedSlots.length) return;
        int next = Math.max(8, composedSlots.length);
        while (required > next) next <<= 1;
        composedSlots = grow(composedSlots, next);
    }

    private void ensureBucketCapacity(int required) {
        if (required <= bucketHead.length) return;
        int next = Math.max(8, bucketHead.length);
        while (required > next) next <<= 1;
        bucketHead = grow(bucketHead, next);
        bucketTail = grow(bucketTail, next);
    }

    private void ensurePlanNextCapacity(int required) {
        if (required <= planNext.length) return;
        int next = Math.max(8, planNext.length);
        while (required > next) next <<= 1;
        planNext = grow(planNext, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
