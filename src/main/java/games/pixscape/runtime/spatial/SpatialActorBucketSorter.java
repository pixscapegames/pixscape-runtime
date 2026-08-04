package games.pixscape.runtime.spatial;

public final class SpatialActorBucketSorter {
    private int[] mergeScratch = new int[0];

    public void sort(SpatialActorCollector actors,
                     int[] actorOrder,
                     int[] bucketStarts,
                     int[] bucketCounts,
                     int bucketCount) {
        if (actors == null || actorOrder == null || bucketStarts == null || bucketCounts == null) return;
        ensureMergeCapacity(actorOrder.length);
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            mergeSortBucket(actors, actorOrder, bucketStarts[bucket], bucketCounts[bucket]);
        }
    }

    private void mergeSortBucket(SpatialActorCollector actors,
                                 int[] actorOrder,
                                 int start,
                                 int count) {
        int end = start + count;
        int[] source = actorOrder;
        int[] target = mergeScratch;
        for (int width = 1; width < count; width <<= 1) {
            for (int run = start; run < end; run += width << 1) {
                int middle = Math.min(run + width, end);
                int runEnd = Math.min(run + (width << 1), end);
                int left = run;
                int right = middle;
                for (int write = run; write < runEnd; write++) {
                    if (left < middle && (right >= runEnd
                            || compareActors(actors, source[left], source[right]) <= 0)) {
                        target[write] = source[left++];
                    } else {
                        target[write] = source[right++];
                    }
                }
            }
            int[] swap = source;
            source = target;
            target = swap;
        }
        if (source != actorOrder) System.arraycopy(source, start, actorOrder, start, count);
    }

    private void ensureMergeCapacity(int required) {
        if (required <= mergeScratch.length) return;
        int next = Math.max(8, mergeScratch.length);
        while (next < required) next <<= 1;
        mergeScratch = new int[next];
    }

    static int compareActors(SpatialActorCollector actors, int left, int right) {
        int leftLayer = actorLayer(actors, left);
        int rightLayer = actorLayer(actors, right);
        if (leftLayer != rightLayer) return leftLayer < rightLayer ? -1 : 1;
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

    static int actorLayer(SpatialActorCollector actors, int actor) {
        return actor >= 0 && actor < actors.actorLayerIndex.length
                ? actors.actorLayerIndex[actor] : 0;
    }

    private static int compareDepthY(float left, float right) {
        if (left == right) return 0;
        if (Float.isNaN(left)) return Float.isNaN(right) ? 0 : 1;
        if (Float.isNaN(right)) return -1;
        return left > right ? -1 : 1;
    }
}
