package games.pixscape.runtime.spatial;

/** Reduces exact-anchor intents and resolves stable actor insertion intervals jointly. */
public final class SpatialBucketPlanner {
    private static final byte FRONT = 1;
    private static final byte BEHIND = 2; // max() makes BEHIND dominate FRONT at a shared anchor.

    public int actorCount;
    public int bucketCount;
    public int[] actorBucket = new int[0];
    int[] actorOriginalBucket = new int[0];
    int[] actorLowerBound = new int[0];
    int[] actorUpperBound = new int[0];
    int[] finalActorDrawIndex = new int[0];
    int[] sortedActorIndex = new int[0];
    private int[] orderedActorIndex = new int[0];
    private int[] actorsByLowerBound = new int[0];
    private int[] lowerSortScratch = new int[0];
    private int[] upperBoundHeap = new int[0];
    private int[] readyActorHeap = new int[0];
    private int[] baselineActorBucket = new int[0];
    private int[] candidateActorBucket = new int[0];
    private int[] prefixMaximumLowerBound = new int[0];
    private boolean[] actorRemaining = new boolean[0];
    private int[] actorLowerSourceAnchorGx = new int[0];
    private int[] actorLowerSourceAnchorGy = new int[0];
    private int[] actorLowerSourceFaceIndex = new int[0];
    private int[] actorLowerSourceStructureId = new int[0];
    private float[] actorLowerSourceMinX = new float[0];
    private float[] actorLowerSourceMaxX = new float[0];
    private int[] actorUpperSourceAnchorGx = new int[0];
    private int[] actorUpperSourceAnchorGy = new int[0];
    private int[] actorUpperSourceFaceIndex = new int[0];
    private int[] actorUpperSourceStructureId = new int[0];
    private float[] actorUpperSourceMinX = new float[0];
    private float[] actorUpperSourceMaxX = new float[0];
    boolean[] actorHasConstraint = new boolean[0];
    private int[] anchorVisitStamp = new int[0];
    private byte[] anchorIntent = new byte[0];
    private int[] anchorSourceFace = new int[0];
    private int[] anchorSourceMembership = new int[0];
    private int[] touchedAnchorIndices = new int[0];
    private int currentStamp;
    private int[] bucketActorCount = new int[0];
    private int[] bucketActorOffset = new int[0];
    private int[] bucketWrite = new int[0];
    private int unresolvedConstraintCount;
    private int actorOrderingFallbackCount;
    public int testedMembershipCount;
    public int acceptedLocalMembershipCount;
    public int rejectedNonlocalMembershipCount;
    private final SpatialActorBucketSorter sorter = new SpatialActorBucketSorter();

    public void begin(SpatialActorCollector actors, int[] originals, int buckets) {
        clear();
        if (actors == null || actors.actorCount == 0) return;
        if (originals == null || originals.length < actors.actorCount || buckets <= 0) {
            throw new IllegalArgumentException("Valid actor buckets are required.");
        }
        actorCount = actors.actorCount;
        bucketCount = buckets;
        ensureActorCapacity(actorCount);
        ensureBucketCapacity(bucketCount);
        for (int actor = 0; actor < actorCount; actor++) {
            int original = clamp(originals[actor]);
            actorBucket[actor] = actorOriginalBucket[actor] = original;
            actorLowerBound[actor] = Integer.MIN_VALUE;
            actorUpperBound[actor] = Integer.MAX_VALUE;
            actorLowerSourceFaceIndex[actor] = actorUpperSourceFaceIndex[actor] = -1;
            actorLowerSourceStructureId[actor] = actorUpperSourceStructureId[actor] = 0;
            actorHasConstraint[actor] = false;
            finalActorDrawIndex[actor] = -1;
        }
    }

    public void addRelations(SpatialActorCollector actors,
                             SpatialProjectedFaceCache faces,
                             SpatialFaceRelationSolver relations) {
        if (actors == null || faces == null || relations == null || relations.relationCount == 0) return;
        if (actors.actorCount != actorCount) throw new IllegalStateException("Actor snapshot changed while planning.");
        ensureAnchorScratchCapacity(faces.anchorCount);
        for (int actor = 0; actor < actorCount; actor++) {
            int touchedCount = 0;
            int stamp = nextStamp();
            int relationStart = relations.actorRelationStart[actor];
            int relationEnd = relationStart + relations.actorRelationCount[actor];
            float actorX = actors.actorCircleX[actor];
            float radius = actors.circleRadius(actor);
            float circleMinX = actorX - radius;
            float circleMaxX = actorX + radius;
            for (int relation = relationStart; relation < relationEnd; relation++) {
                int face = relations.relationFaceIndex[relation];
                if (face < 0 || face >= faces.faceCount) continue;
                byte requested = relations.relationType[relation] == SpatialFaceRelationSolver.ACTOR_BEHIND_FACE
                        ? BEHIND : FRONT;
                int membershipStart = faces.faceAnchorIndexStart[face];
                int membershipEnd = membershipStart + faces.faceAnchorIndexCount[face];
                for (int membership = membershipStart; membership < membershipEnd; membership++) {
                    testedMembershipCount++;
                    if (circleMaxX < faces.faceAnchorScreenMinX[membership]
                            || circleMinX >= faces.faceAnchorScreenMaxX[membership]) {
                        rejectedNonlocalMembershipCount++;
                        continue;
                    }
                    acceptedLocalMembershipCount++;
                    int anchor = faces.faceAnchorIndices[membership];
                    if (!faces.anchorResolved[anchor]) continue;
                    if (anchorVisitStamp[anchor] != stamp) {
                        anchorVisitStamp[anchor] = stamp;
                        anchorIntent[anchor] = requested;
                        anchorSourceFace[anchor] = face;
                        anchorSourceMembership[anchor] = membership;
                        touchedAnchorIndices[touchedCount++] = anchor;
                    } else if (requested > anchorIntent[anchor]
                            || requested == anchorIntent[anchor]
                            && preferFace(faces, face, anchorSourceFace[anchor])) {
                        anchorIntent[anchor] = requested;
                        anchorSourceFace[anchor] = face;
                        anchorSourceMembership[anchor] = membership;
                    }
                }
            }
            for (int touched = 0; touched < touchedCount; touched++) {
                int anchor = touchedAnchorIndices[touched];
                int face = anchorSourceFace[anchor];
                int membership = anchorSourceMembership[anchor];
                actorHasConstraint[actor] = true;
                if (anchorIntent[anchor] == FRONT) {
                    int candidate = faces.anchorAfterBucket[anchor];
                    if (candidate > actorLowerBound[actor]
                            || candidate == actorLowerBound[actor]
                            && preferLowerSource(actor, faces, anchor, face)) {
                        actorLowerBound[actor] = candidate;
                        actorLowerSourceAnchorGx[actor] = faces.anchorGx[anchor];
                        actorLowerSourceAnchorGy[actor] = faces.anchorGy[anchor];
                        actorLowerSourceFaceIndex[actor] = faces.faceCompiledIndex[face];
                        actorLowerSourceStructureId[actor] = faces.faceStructureId[face];
                        actorLowerSourceMinX[actor] = faces.faceAnchorScreenMinX[membership];
                        actorLowerSourceMaxX[actor] = faces.faceAnchorScreenMaxX[membership];
                    }
                } else {
                    int candidate = faces.anchorBeforeBucket[anchor];
                    if (candidate < actorUpperBound[actor]
                            || candidate == actorUpperBound[actor]
                            && preferUpperSource(actor, faces, anchor, face)) {
                        actorUpperBound[actor] = candidate;
                        actorUpperSourceAnchorGx[actor] = faces.anchorGx[anchor];
                        actorUpperSourceAnchorGy[actor] = faces.anchorGy[anchor];
                        actorUpperSourceFaceIndex[actor] = faces.faceCompiledIndex[face];
                        actorUpperSourceStructureId[actor] = faces.faceStructureId[face];
                        actorUpperSourceMinX[actor] = faces.faceAnchorScreenMinX[membership];
                        actorUpperSourceMaxX[actor] = faces.faceAnchorScreenMaxX[membership];
                    }
                }
            }
        }
    }

    public void finish(SpatialActorCollector actors) {
        if (actors == null || actorCount == 0) return;
        unresolvedConstraintCount = 0;
        for (int actor = 0; actor < actorCount; actor++) {
            if (actorHasConstraint[actor] && actorLowerBound[actor] > actorUpperBound[actor]) {
                unresolvedConstraintCount++;
            }
        }

        assignActorBuckets(actors);
        sortActorsWithinBuckets(actors);
    }

    public int unresolvedConstraintCount() { return unresolvedConstraintCount; }
    public int actorOrderingFallbackCount() { return actorOrderingFallbackCount; }
    boolean actorHasConstraint(int actor) { return actorHasConstraint[actor]; }
    int lowerSourceAnchorGx(int actor) { return actorLowerSourceAnchorGx[actor]; }
    int lowerSourceAnchorGy(int actor) { return actorLowerSourceAnchorGy[actor]; }
    float lowerSourceMinX(int actor) { return actorLowerSourceMinX[actor]; }
    float lowerSourceMaxX(int actor) { return actorLowerSourceMaxX[actor]; }
    float upperSourceMinX(int actor) { return actorUpperSourceMinX[actor]; }
    float upperSourceMaxX(int actor) { return actorUpperSourceMaxX[actor]; }
    public int bucketActorStart(int bucket) { checkBucket(bucket); return bucketActorOffset[bucket]; }
    public int bucketActorCount(int bucket) { checkBucket(bucket); return bucketActorCount[bucket]; }
    public void clear() {
        actorCount = 0; bucketCount = 0; unresolvedConstraintCount = 0; actorOrderingFallbackCount = 0;
        testedMembershipCount = 0; acceptedLocalMembershipCount = 0; rejectedNonlocalMembershipCount = 0;
    }

    private int nextStamp() {
        if (currentStamp == Integer.MAX_VALUE) {
            for (int i = 0; i < anchorVisitStamp.length; i++) anchorVisitStamp[i] = 0;
            currentStamp = 0;
        }
        return ++currentStamp;
    }

    private static boolean preferFace(SpatialProjectedFaceCache faces, int candidate, int current) {
        int candidateStructure = faces.faceStructureId[candidate];
        int currentStructure = faces.faceStructureId[current];
        return candidateStructure < currentStructure || candidateStructure == currentStructure
                && faces.faceCompiledIndex[candidate] < faces.faceCompiledIndex[current];
    }

    private boolean preferLowerSource(int actor, SpatialProjectedFaceCache faces, int anchor, int face) {
        return preferSource(faces.anchorGx[anchor], faces.anchorGy[anchor],
                faces.faceStructureId[face], faces.faceCompiledIndex[face],
                actorLowerSourceAnchorGx[actor], actorLowerSourceAnchorGy[actor],
                actorLowerSourceStructureId[actor], actorLowerSourceFaceIndex[actor]);
    }

    private boolean preferUpperSource(int actor, SpatialProjectedFaceCache faces, int anchor, int face) {
        return preferSource(faces.anchorGx[anchor], faces.anchorGy[anchor],
                faces.faceStructureId[face], faces.faceCompiledIndex[face],
                actorUpperSourceAnchorGx[actor], actorUpperSourceAnchorGy[actor],
                actorUpperSourceStructureId[actor], actorUpperSourceFaceIndex[actor]);
    }

    private static boolean preferSource(int gx, int gy, int structure, int face,
                                        int currentGx, int currentGy, int currentStructure, int currentFace) {
        if (currentFace < 0) return true;
        if (gx != currentGx) return gx < currentGx;
        if (gy != currentGy) return gy < currentGy;
        if (structure != currentStructure) return structure < currentStructure;
        return face < currentFace;
    }

    private void sortActorsWithinBuckets(SpatialActorCollector actors) {
        for (int bucket = 0; bucket < bucketCount; bucket++) bucketActorCount[bucket] = 0;
        for (int actor = 0; actor < actorCount; actor++) bucketActorCount[actorBucket[actor]]++;
        int offset = 0;
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            bucketActorOffset[bucket] = offset; bucketWrite[bucket] = offset; offset += bucketActorCount[bucket];
        }
        ensureSortedCapacity(actorCount);
        for (int actor = 0; actor < actorCount; actor++) sortedActorIndex[bucketWrite[actorBucket[actor]]++] = actor;
        sorter.sort(actors, sortedActorIndex, bucketActorOffset, bucketActorCount, bucketCount);
    }

    private void assignActorBuckets(SpatialActorCollector actors) {
        ensureActorOrderCapacity(actorCount);
        for (int actor = 0; actor < actorCount; actor++) {
            baselineActorBucket[actor] = actorOriginalBucket[actor];
        }

        int orderedCount = buildActorOrder(actors);
        if (orderedCount == actorCount) assignCandidateBuckets();
        validateAndPublishActorBuckets(orderedCount);
    }

    /** Package-private so the atomic fallback path can be exercised without production fault injection. */
    boolean validateAndPublishActorBuckets(int orderedCount) {
        boolean valid = orderedCount == actorCount;
        int previousBucket = 0;
        for (int actor = 0; actor < actorCount; actor++) actorRemaining[actor] = true;
        for (int position = 0; valid && position < actorCount; position++) {
            int actor = orderedActorIndex[position];
            valid = actor >= 0 && actor < actorCount && actorRemaining[actor];
            if (!valid) break;
            actorRemaining[actor] = false;
            int bucket = candidateActorBucket[actor];
            valid = bucket >= resolvedLowerBound(actor)
                    && bucket <= resolvedUpperBound(actor)
                    && (position == 0 || previousBucket <= bucket);
            previousBucket = bucket;
        }
        int[] published = valid ? candidateActorBucket : baselineActorBucket;
        System.arraycopy(published, 0, actorBucket, 0, actorCount);
        if (!valid) actorOrderingFallbackCount++;
        return valid;
    }

    private int buildActorOrder(SpatialActorCollector actors) {
        for (int actor = 0; actor < actorCount; actor++) {
            actorsByLowerBound[actor] = actor;
            actorRemaining[actor] = true;
        }
        sortActorsByLowerBound();
        int upperHeapSize = 0;
        for (int actor = 0; actor < actorCount; actor++) {
            upperHeapSize = pushUpperBound(actor, upperHeapSize);
        }

        int lowerPosition = 0;
        int readyHeapSize = 0;
        int orderedCount = 0;
        while (orderedCount < actorCount) {
            while (upperHeapSize > 0 && !actorRemaining[upperBoundHeap[0]]) {
                upperHeapSize = popUpperBound(upperHeapSize);
            }
            if (upperHeapSize == 0) break;
            int minimumRemainingUpper = resolvedUpperBound(upperBoundHeap[0]);
            while (lowerPosition < actorCount
                    && resolvedLowerBound(actorsByLowerBound[lowerPosition]) <= minimumRemainingUpper) {
                readyHeapSize = pushReadyActor(actors,
                        actorsByLowerBound[lowerPosition++], readyHeapSize);
            }
            if (readyHeapSize == 0) break;
            int actor = readyActorHeap[0];
            readyHeapSize = popReadyActor(actors, readyHeapSize);
            actorRemaining[actor] = false;
            orderedActorIndex[orderedCount++] = actor;
        }
        return orderedCount;
    }

    private void assignCandidateBuckets() {
        int maximumLowerBound = 0;
        for (int position = 0; position < actorCount; position++) {
            maximumLowerBound = Math.max(maximumLowerBound,
                    resolvedLowerBound(orderedActorIndex[position]));
            prefixMaximumLowerBound[position] = maximumLowerBound;
        }

        int nextBucket = bucketCount - 1;
        for (int position = actorCount - 1; position >= 0; position--) {
            int actor = orderedActorIndex[position];
            int lower = prefixMaximumLowerBound[position];
            int upper = Math.min(resolvedUpperBound(actor), nextBucket);
            candidateActorBucket[actor] = clampToInterval(actorOriginalBucket[actor], lower, upper);
            nextBucket = candidateActorBucket[actor];
        }
    }

    private int resolvedLowerBound(int actor) {
        if (hasContradictoryInterval(actor)) return actorOriginalBucket[actor];
        return actorLowerBound[actor] == Integer.MIN_VALUE ? 0 : actorLowerBound[actor];
    }

    private int resolvedUpperBound(int actor) {
        if (hasContradictoryInterval(actor)) return actorOriginalBucket[actor];
        return actorUpperBound[actor] == Integer.MAX_VALUE ? bucketCount - 1 : actorUpperBound[actor];
    }

    private boolean hasContradictoryInterval(int actor) {
        return actorHasConstraint[actor] && actorLowerBound[actor] > actorUpperBound[actor];
    }

    private void sortActorsByLowerBound() {
        int[] source = actorsByLowerBound;
        int[] target = lowerSortScratch;
        for (int width = 1; width < actorCount; width <<= 1) {
            for (int start = 0; start < actorCount; start += width << 1) {
                int middle = Math.min(start + width, actorCount);
                int end = Math.min(start + (width << 1), actorCount);
                int left = start;
                int right = middle;
                for (int write = start; write < end; write++) {
                    if (left < middle && (right >= end
                            || compareLowerBound(source[left], source[right]) <= 0)) {
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
        if (source != actorsByLowerBound) {
            System.arraycopy(source, 0, actorsByLowerBound, 0, actorCount);
        }
    }

    private int compareLowerBound(int left, int right) {
        int leftLower = resolvedLowerBound(left);
        int rightLower = resolvedLowerBound(right);
        if (leftLower != rightLower) return leftLower < rightLower ? -1 : 1;
        int leftUpper = resolvedUpperBound(left);
        int rightUpper = resolvedUpperBound(right);
        if (leftUpper != rightUpper) return leftUpper < rightUpper ? -1 : 1;
        return left < right ? -1 : left == right ? 0 : 1;
    }

    private int pushUpperBound(int actor, int size) {
        int index = size++;
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (compareUpperBound(upperBoundHeap[parent], actor) <= 0) break;
            upperBoundHeap[index] = upperBoundHeap[parent];
            index = parent;
        }
        upperBoundHeap[index] = actor;
        return size;
    }

    private int popUpperBound(int size) {
        int nextSize = size - 1;
        if (nextSize == 0) return 0;
        int actor = upperBoundHeap[nextSize];
        int index = 0;
        while (true) {
            int left = (index << 1) + 1;
            if (left >= nextSize) break;
            int right = left + 1;
            int child = right < nextSize
                    && compareUpperBound(upperBoundHeap[right], upperBoundHeap[left]) < 0 ? right : left;
            if (compareUpperBound(actor, upperBoundHeap[child]) <= 0) break;
            upperBoundHeap[index] = upperBoundHeap[child];
            index = child;
        }
        upperBoundHeap[index] = actor;
        return nextSize;
    }

    private int compareUpperBound(int left, int right) {
        int leftUpper = resolvedUpperBound(left);
        int rightUpper = resolvedUpperBound(right);
        if (leftUpper != rightUpper) return leftUpper < rightUpper ? -1 : 1;
        return left < right ? -1 : left == right ? 0 : 1;
    }

    private int pushReadyActor(SpatialActorCollector actors, int actor, int size) {
        int index = size++;
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (SpatialActorBucketSorter.compareActors(actors, readyActorHeap[parent], actor) <= 0) break;
            readyActorHeap[index] = readyActorHeap[parent];
            index = parent;
        }
        readyActorHeap[index] = actor;
        return size;
    }

    private int popReadyActor(SpatialActorCollector actors, int size) {
        int nextSize = size - 1;
        if (nextSize == 0) return 0;
        int actor = readyActorHeap[nextSize];
        int index = 0;
        while (true) {
            int left = (index << 1) + 1;
            if (left >= nextSize) break;
            int right = left + 1;
            int child = right < nextSize && SpatialActorBucketSorter.compareActors(actors,
                    readyActorHeap[right], readyActorHeap[left]) < 0 ? right : left;
            if (SpatialActorBucketSorter.compareActors(actors, actor, readyActorHeap[child]) <= 0) break;
            readyActorHeap[index] = readyActorHeap[child];
            index = child;
        }
        readyActorHeap[index] = actor;
        return nextSize;
    }

    private static int clampToInterval(int bucket, int lower, int upper) {
        return bucket < lower ? lower : bucket > upper ? upper : bucket;
    }

    private int clamp(int bucket) { return bucket < 0 ? 0 : bucket >= bucketCount ? bucketCount - 1 : bucket; }
    private void checkBucket(int bucket) { if (bucket < 0 || bucket >= bucketCount) throw new IndexOutOfBoundsException("Invalid bucket " + bucket); }
    private void ensureActorCapacity(int required) {
        if (required <= actorBucket.length) return; int n=capacity(actorBucket.length,required);
        actorBucket=grow(actorBucket,n); actorOriginalBucket=grow(actorOriginalBucket,n); actorLowerBound=grow(actorLowerBound,n); actorUpperBound=grow(actorUpperBound,n);
        actorLowerSourceAnchorGx=grow(actorLowerSourceAnchorGx,n); actorLowerSourceAnchorGy=grow(actorLowerSourceAnchorGy,n); actorLowerSourceFaceIndex=grow(actorLowerSourceFaceIndex,n); actorLowerSourceStructureId=grow(actorLowerSourceStructureId,n);
        actorLowerSourceMinX=grow(actorLowerSourceMinX,n); actorLowerSourceMaxX=grow(actorLowerSourceMaxX,n);
        actorUpperSourceAnchorGx=grow(actorUpperSourceAnchorGx,n); actorUpperSourceAnchorGy=grow(actorUpperSourceAnchorGy,n); actorUpperSourceFaceIndex=grow(actorUpperSourceFaceIndex,n); actorUpperSourceStructureId=grow(actorUpperSourceStructureId,n);
        actorUpperSourceMinX=grow(actorUpperSourceMinX,n); actorUpperSourceMaxX=grow(actorUpperSourceMaxX,n);
        finalActorDrawIndex=grow(finalActorDrawIndex,n); actorHasConstraint=grow(actorHasConstraint,n);
    }
    private void ensureAnchorScratchCapacity(int required){if(required<=anchorVisitStamp.length)return;int n=capacity(anchorVisitStamp.length,required);anchorVisitStamp=grow(anchorVisitStamp,n);anchorIntent=grow(anchorIntent,n);anchorSourceFace=grow(anchorSourceFace,n);anchorSourceMembership=grow(anchorSourceMembership,n);touchedAnchorIndices=grow(touchedAnchorIndices,n);}
    private void ensureBucketCapacity(int required){if(required<=bucketActorCount.length)return;int n=capacity(bucketActorCount.length,required);bucketActorCount=grow(bucketActorCount,n);bucketActorOffset=grow(bucketActorOffset,n);bucketWrite=grow(bucketWrite,n);}
    private void ensureSortedCapacity(int required){if(required>sortedActorIndex.length)sortedActorIndex=grow(sortedActorIndex,capacity(sortedActorIndex.length,required));}
    private void ensureActorOrderCapacity(int required){
        if(required<=orderedActorIndex.length)return;int n=capacity(orderedActorIndex.length,required);
        orderedActorIndex=grow(orderedActorIndex,n);actorsByLowerBound=grow(actorsByLowerBound,n);
        lowerSortScratch=grow(lowerSortScratch,n);upperBoundHeap=grow(upperBoundHeap,n);readyActorHeap=grow(readyActorHeap,n);
        baselineActorBucket=grow(baselineActorBucket,n);candidateActorBucket=grow(candidateActorBucket,n);
        prefixMaximumLowerBound=grow(prefixMaximumLowerBound,n);actorRemaining=grow(actorRemaining,n);
    }
    private static int capacity(int current,int required){int n=Math.max(8,current);while(n<required)n<<=1;return n;}
    private static int[] grow(int[] a,int n){int[] b=new int[n];System.arraycopy(a,0,b,0,a.length);return b;}
    private static float[] grow(float[] a,int n){float[] b=new float[n];System.arraycopy(a,0,b,0,a.length);return b;}
    private static byte[] grow(byte[] a,int n){byte[] b=new byte[n];System.arraycopy(a,0,b,0,a.length);return b;}
    private static boolean[] grow(boolean[] a,int n){boolean[] b=new boolean[n];System.arraycopy(a,0,b,0,a.length);return b;}
}
