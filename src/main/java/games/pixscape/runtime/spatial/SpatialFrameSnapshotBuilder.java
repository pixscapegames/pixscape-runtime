package games.pixscape.runtime.spatial;

import java.util.Arrays;

public final class SpatialFrameSnapshotBuilder {
    public int drawSize;
    public int actorCount;
    public int nonActorCount;
    public int bucketCount;
    public int[] actorOriginalBucket = new int[0];
    public int[] nonActorSlots = new int[0];
    public int[] drawIndexToBucketBefore = new int[0];
    public int[] drawIndexToBucketAfter = new int[0];
    public boolean[] actorSlotMask = new boolean[0];

    public void build(int[] sourceSlots,
                      int sourceSize,
                      int slotCapacity,
                      SpatialActorCollector actors) {
        if (sourceSlots == null) {
            throw new IllegalArgumentException("Source draw slots are required.");
        }
        if (sourceSize < 0 || sourceSize > sourceSlots.length) {
            throw new IllegalArgumentException("Invalid source draw-list size: " + sourceSize);
        }
        if (actors == null) {
            throw new IllegalArgumentException("Spatial actor snapshot is required.");
        }

        drawSize = sourceSize;
        actorCount = actors.actorCount;
        ensureCapacity(Math.max(slotCapacity, 0), sourceSize, actorCount);
        Arrays.fill(actorSlotMask, 0, Math.min(actorSlotMask.length, Math.max(slotCapacity, 0)), false);
        for (int actor = 0; actor < actors.actorCount; actor++) {
            int slot = actors.actorSlot[actor];
            if (slot >= 0 && slot < actorSlotMask.length) actorSlotMask[slot] = true;
        }

        nonActorCount = 0;
        for (int drawIndex = 0; drawIndex < sourceSize; drawIndex++) {
            int slot = sourceSlots[drawIndex];
            drawIndexToBucketBefore[drawIndex] = nonActorCount;
            if (isActorSlot(slot)) {
                int actor = actorIndexForSlot(actors, slot);
                if (actor >= 0) actorOriginalBucket[actor] = nonActorCount;
            } else {
                nonActorSlots[nonActorCount++] = slot;
            }
            drawIndexToBucketAfter[drawIndex] = nonActorCount;
        }
        bucketCount = nonActorCount + 1;
    }

    public boolean isActorSlot(int slot) {
        return slot >= 0 && slot < actorSlotMask.length && actorSlotMask[slot];
    }

    private static int actorIndexForSlot(SpatialActorCollector actors, int slot) {
        for (int actor = 0; actor < actors.actorCount; actor++) {
            if (actors.actorSlot[actor] == slot) return actor;
        }
        return -1;
    }

    private void ensureCapacity(int slotCapacity, int drawCapacity, int actorCapacity) {
        if (slotCapacity > actorSlotMask.length) {
            int next = Math.max(8, actorSlotMask.length);
            while (slotCapacity > next) next <<= 1;
            boolean[] expanded = new boolean[next];
            System.arraycopy(actorSlotMask, 0, expanded, 0, actorSlotMask.length);
            actorSlotMask = expanded;
        }
        if (drawCapacity > drawIndexToBucketBefore.length) {
            int next = Math.max(8, drawIndexToBucketBefore.length);
            while (drawCapacity > next) next <<= 1;
            drawIndexToBucketBefore = grow(drawIndexToBucketBefore, next);
            drawIndexToBucketAfter = grow(drawIndexToBucketAfter, next);
            nonActorSlots = grow(nonActorSlots, next);
        }
        if (actorCapacity > actorOriginalBucket.length) {
            int next = Math.max(8, actorOriginalBucket.length);
            while (actorCapacity > next) next <<= 1;
            actorOriginalBucket = grow(actorOriginalBucket, next);
        }
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
