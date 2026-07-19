package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderSourceDomain;

public final class SpatialBucketDrawListComposer {
    public int composedSize;
    public int[] composedSlots = new int[0];
    public byte[] composedDomains = new byte[0];

    public int compose(DrawList input,
                       SpatialActorCollector actors,
                       SpatialBucketPlanner planner,
                       SpatialFrameSnapshotBuilder snapshot) {
        if (input == null) {
            throw new IllegalArgumentException("Input draw list is required.");
        }
        int inputSize = input.size;
        int[] inputSlots = input.data();
        byte[] inputDomains = input.domainData();
        if (inputSize < 0 || inputSize > inputSlots.length || inputSize > inputDomains.length) {
            throw new IllegalArgumentException("Invalid input draw-list size: " + inputSize);
        }
        if (actors == null || planner == null || actors.actorCount != planner.actorCount) {
            throw new IllegalArgumentException("Matching actor snapshot and bucket planner are required.");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("Spatial frame snapshot is required.");
        }

        ensureComposedCapacity(inputSize);
        int write = 0;
        int bucket = 0;
        write = emitBucket(write, bucket, actors, planner);
        for (int i = 0; i < inputSize; i++) {
            int slot = inputSlots[i];
            byte domain = inputDomains[i];
            if (snapshot.isActorEntry(domain, slot)) continue;
            composedDomains[write] = domain;
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
            composedDomains[write] = RenderSourceDomain.SOURCE_ECS;
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
        byte[] expandedDomains = new byte[next];
        System.arraycopy(composedDomains, 0, expandedDomains, 0, composedDomains.length);
        composedDomains = expandedDomains;
    }
}
