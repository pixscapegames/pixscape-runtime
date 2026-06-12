package games.pixscape.runtime.spatial;

public final class SpatialBucketPlanner {
    public int actorCount;
    public int bucketCount;
    public int[] actorBucket = new int[0];

    int[] actorOriginalBucket = new int[0];
    int[] actorLowerBound = new int[0];
    int[] actorUpperBound = new int[0];
    int[] actorLowerSourceBlockIndex = new int[0];
    int[] actorLowerSourceBlockId = new int[0];
    String[] actorLowerSourceBlockName = new String[0];
    int[] actorUpperSourceBlockIndex = new int[0];
    int[] actorUpperSourceBlockId = new int[0];
    String[] actorUpperSourceBlockName = new String[0];
    int[] finalActorDrawIndex = new int[0];
    private boolean[] actorHasRelation = new boolean[0];
    private int[] bucketActorCount = new int[0];
    private int[] bucketActorOffset = new int[0];
    private int[] bucketWrite = new int[0];
    int[] sortedActorIndex = new int[0];
    private final SpatialActorBucketSorter sorter = new SpatialActorBucketSorter();

    public void begin(SpatialActorCollector actors, int[] actorOriginalBucket, int buckets) {
        clear();
        if (actors == null || actors.actorCount == 0) return;
        if (actorOriginalBucket == null || actorOriginalBucket.length < actors.actorCount) {
            throw new IllegalArgumentException("Actor original bucket array is required.");
        }
        if (buckets <= 0) {
            throw new IllegalArgumentException("Spatial bucket count must be positive.");
        }

        actorCount = actors.actorCount;
        bucketCount = buckets;
        ensureActorCapacity(actorCount);
        ensureBucketCapacity(bucketCount);
        for (int actor = 0; actor < actorCount; actor++) {
            int bucket = clampBucket(actorOriginalBucket[actor], bucketCount);
            actorBucket[actor] = bucket;
            this.actorOriginalBucket[actor] = bucket;
            actorLowerBound[actor] = Integer.MIN_VALUE;
            actorUpperBound[actor] = Integer.MAX_VALUE;
            actorLowerSourceBlockIndex[actor] = -1;
            actorLowerSourceBlockId[actor] = 0;
            actorLowerSourceBlockName[actor] = null;
            actorUpperSourceBlockIndex[actor] = -1;
            actorUpperSourceBlockId[actor] = 0;
            actorUpperSourceBlockName[actor] = null;
            finalActorDrawIndex[actor] = -1;
            actorHasRelation[actor] = false;
        }
    }

    public void addRelations(SpatialActorCollector actors,
                             SpatialBlocksRuntimeCache blockCache,
                             SpatialRelationSolver relations) {
        if (actors == null || actors.actorCount == 0 || relations == null || relations.relationCount == 0) return;
        if (blockCache == null) {
            throw new IllegalArgumentException("Spatial block runtime cache is required.");
        }
        if (actors.actorCount != actorCount) {
            throw new IllegalStateException("Actor snapshot changed while planning spatial buckets.");
        }

        for (int relation = 0; relation < relations.relationCount; relation++) {
            int actor = relations.relationActorIndex[relation];
            int block = relations.relationBlockIndex[relation];
            int type = relations.relationType[relation];
            validateRelation(actor, block, type, blockCache);

            actorHasRelation[actor] = true;
            if (type == SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK) {
                int lower = blockCache.blockAnchorEndDrawIndex[block] + 1;
                if (lower > actorLowerBound[actor]) {
                    actorLowerBound[actor] = lower;
                    actorLowerSourceBlockIndex[actor] = relationAuthoredBlockIndex(relations, relation);
                    actorLowerSourceBlockId[actor] = relationBlockId(relations, relation);
                    actorLowerSourceBlockName[actor] = relationBlockName(relations, relation);
                }
            } else {
                int upper = blockCache.blockAnchorStartDrawIndex[block];
                if (upper < actorUpperBound[actor]) {
                    actorUpperBound[actor] = upper;
                    actorUpperSourceBlockIndex[actor] = relationAuthoredBlockIndex(relations, relation);
                    actorUpperSourceBlockId[actor] = relationBlockId(relations, relation);
                    actorUpperSourceBlockName[actor] = relationBlockName(relations, relation);
                }
            }
        }
    }

    public void finish(SpatialActorCollector actors) {
        if (actors == null || actorCount == 0) return;
        for (int actor = 0; actor < actorCount; actor++) {
            if (!actorHasRelation[actor]) continue;
            int lower = actorLowerBound[actor];
            int upper = actorUpperBound[actor];
            if (lower == Integer.MIN_VALUE) lower = 0;
            if (upper == Integer.MAX_VALUE) upper = bucketCount - 1;

            int target;
            if (lower > upper) {
                // Adjacent split blocks can disagree at a seam; prefer the front-most bucket deterministically.
                target = clampBucket(lower, bucketCount);
            } else {
                target = chooseTarget(actorBucket[actor], lower, upper);
            }
            actorBucket[actor] = clampBucket(target, bucketCount);
        }
        sortActorsWithinBuckets(actors);
    }

    public int bucketActorStart(int bucket) {
        if (bucket < 0 || bucket >= bucketCount) {
            throw new IndexOutOfBoundsException("Invalid spatial actor bucket: " + bucket);
        }
        return bucketActorOffset[bucket];
    }

    public int bucketActorCount(int bucket) {
        if (bucket < 0 || bucket >= bucketCount) {
            throw new IndexOutOfBoundsException("Invalid spatial actor bucket: " + bucket);
        }
        return bucketActorCount[bucket];
    }

    public void clear() {
        actorCount = 0;
        bucketCount = 0;
    }

    private void sortActorsWithinBuckets(SpatialActorCollector actors) {
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            bucketActorCount[bucket] = 0;
        }
        for (int actor = 0; actor < actorCount; actor++) {
            bucketActorCount[actorBucket[actor]]++;
        }

        int offset = 0;
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            bucketActorOffset[bucket] = offset;
            bucketWrite[bucket] = offset;
            offset += bucketActorCount[bucket];
        }
        ensureSortedCapacity(actorCount);
        for (int actor = 0; actor < actorCount; actor++) {
            int bucket = actorBucket[actor];
            sortedActorIndex[bucketWrite[bucket]++] = actor;
        }

        sorter.sort(actors, sortedActorIndex, bucketActorOffset, bucketActorCount, bucketCount);
    }

    private static int chooseTarget(int originalBucket, int lower, int upper) {
        if (originalBucket < lower) return lower;
        if (originalBucket > upper) return upper;
        return originalBucket;
    }

    private void validateRelation(int actor, int block, int type, SpatialBlocksRuntimeCache blockCache) {
        if (actor < 0 || actor >= actorCount) {
            throw new IndexOutOfBoundsException("Invalid spatial relation actor index: " + actor);
        }
        if (block < 0 || block >= blockCache.blockCount) {
            throw new IndexOutOfBoundsException("Invalid spatial relation block index: " + block);
        }
        if (type != SpatialRelationSolver.ACTOR_BEHIND_BLOCK
                && type != SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK) {
            throw new IllegalStateException("Unsupported spatial relation type for bucket planning: " + type);
        }
        if (blockCache.blockAnchorStartDrawIndex[block] < 0 || blockCache.blockAnchorEndDrawIndex[block] < 0) {
            throw new IllegalStateException("Spatial block cache entry is unresolved: block=" + block);
        }
    }

    private static int clampBucket(int bucket, int count) {
        if (bucket < 0) return 0;
        if (bucket >= count) return count - 1;
        return bucket;
    }

    private static int relationAuthoredBlockIndex(SpatialRelationSolver relations, int relation) {
        return relations.relationAuthoredBlockIndex != null && relation < relations.relationAuthoredBlockIndex.length
                ? relations.relationAuthoredBlockIndex[relation]
                : -1;
    }

    private static int relationBlockId(SpatialRelationSolver relations, int relation) {
        return relations.relationBlockId != null && relation < relations.relationBlockId.length
                ? relations.relationBlockId[relation]
                : 0;
    }

    private static String relationBlockName(SpatialRelationSolver relations, int relation) {
        return relations.relationBlockName != null && relation < relations.relationBlockName.length
                ? relations.relationBlockName[relation]
                : null;
    }

    private void ensureActorCapacity(int required) {
        if (required <= actorBucket.length) return;
        int next = Math.max(8, actorBucket.length);
        while (required > next) next <<= 1;
        actorBucket = grow(actorBucket, next);
        actorOriginalBucket = grow(actorOriginalBucket, next);
        actorLowerBound = grow(actorLowerBound, next);
        actorUpperBound = grow(actorUpperBound, next);
        actorLowerSourceBlockIndex = grow(actorLowerSourceBlockIndex, next);
        actorLowerSourceBlockId = grow(actorLowerSourceBlockId, next);
        actorLowerSourceBlockName = grow(actorLowerSourceBlockName, next);
        actorUpperSourceBlockIndex = grow(actorUpperSourceBlockIndex, next);
        actorUpperSourceBlockId = grow(actorUpperSourceBlockId, next);
        actorUpperSourceBlockName = grow(actorUpperSourceBlockName, next);
        finalActorDrawIndex = grow(finalActorDrawIndex, next);
        boolean[] expanded = new boolean[next];
        System.arraycopy(actorHasRelation, 0, expanded, 0, actorHasRelation.length);
        actorHasRelation = expanded;
    }

    private void ensureBucketCapacity(int required) {
        if (required <= bucketActorCount.length) return;
        int next = Math.max(8, bucketActorCount.length);
        while (required > next) next <<= 1;
        bucketActorCount = grow(bucketActorCount, next);
        bucketActorOffset = grow(bucketActorOffset, next);
        bucketWrite = grow(bucketWrite, next);
    }

    private void ensureSortedCapacity(int required) {
        if (required <= sortedActorIndex.length) return;
        int next = Math.max(8, sortedActorIndex.length);
        while (required > next) next <<= 1;
        sortedActorIndex = grow(sortedActorIndex, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static String[] grow(String[] source, int next) {
        String[] expanded = new String[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
