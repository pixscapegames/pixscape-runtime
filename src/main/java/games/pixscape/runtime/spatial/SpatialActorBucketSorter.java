package games.pixscape.runtime.spatial;

public final class SpatialActorBucketSorter {
    private int[] mergeScratch = new int[0];

    public void sort(SpatialActorCollector actors,
                     int[] actorOrder,
                     int[] bucketStarts,
                     int[] bucketCounts,
                     int bucketCount) {
        if (actors == null || actorOrder == null || bucketStarts == null || bucketCounts == null) return;
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            insertionSortBucket(actors, actorOrder, bucketStarts[bucket], bucketCounts[bucket]);
        }
    }

    void sortAll(SpatialActorCollector actors, int[] actorOrder, int actorCount) {
        if (actors == null || actorOrder == null) return;
        ensureMergeCapacity(actorCount);
        int[] source = actorOrder;
        int[] target = mergeScratch;
        for (int width = 1; width < actorCount; width <<= 1) {
            for (int start = 0; start < actorCount; start += width << 1) {
                int middle = Math.min(start + width, actorCount);
                int end = Math.min(start + (width << 1), actorCount);
                merge(actors, source, target, start, middle, end);
            }
            int[] swap = source;
            source = target;
            target = swap;
        }
        if (source != actorOrder) System.arraycopy(source, 0, actorOrder, 0, actorCount);
    }

    private static void merge(SpatialActorCollector actors,
                              int[] source,
                              int[] target,
                              int start,
                              int middle,
                              int end) {
        int left = start;
        int right = middle;
        for (int write = start; write < end; write++) {
            if (left < middle && (right >= end
                    || compareActors(actors, source[left], source[right]) <= 0)) {
                target[write] = source[left++];
            } else {
                target[write] = source[right++];
            }
        }
    }

    private void ensureMergeCapacity(int required) {
        if (required <= mergeScratch.length) return;
        int next = Math.max(8, mergeScratch.length);
        while (next < required) next <<= 1;
        mergeScratch = new int[next];
    }

    private static void insertionSortBucket(SpatialActorCollector actors,
                                            int[] actorOrder,
                                            int start,
                                            int count) {
        for (int i = 1; i < count; i++) {
            int actor = actorOrder[start + i];
            int j = start + i - 1;
            while (j >= start && compareActors(actors, actor, actorOrder[j]) < 0) {
                actorOrder[j + 1] = actorOrder[j];
                j--;
            }
            actorOrder[j + 1] = actor;
        }
    }

    static int compareActors(SpatialActorCollector actors, int left, int right) {
        int depthCompare = compareDepthY(actors.actorCircleY[left], actors.actorCircleY[right]);
        if (depthCompare != 0) return depthCompare;
        if (actors.actorDrawIndex[left] != actors.actorDrawIndex[right]) {
            return actors.actorDrawIndex[left] < actors.actorDrawIndex[right] ? -1 : 1;
        }
        if (actors.actorStableOrder[left] != actors.actorStableOrder[right]) {
            return actors.actorStableOrder[left] < actors.actorStableOrder[right] ? -1 : 1;
        }
        if (actors.actorSlot[left] != actors.actorSlot[right]) {
            return actors.actorSlot[left] < actors.actorSlot[right] ? -1 : 1;
        }
        if (left != right) return left < right ? -1 : 1;
        return 0;
    }

    private static int compareDepthY(float left, float right) {
        if (left == right) return 0;
        if (Float.isNaN(left)) return Float.isNaN(right) ? 0 : 1;
        if (Float.isNaN(right)) return -1;
        return left > right ? -1 : 1;
    }
}
