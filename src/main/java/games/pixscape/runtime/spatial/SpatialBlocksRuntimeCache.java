package games.pixscape.runtime.spatial;

public final class SpatialBlocksRuntimeCache {
    int blockCount;
    int anchorCount;

    int[] blockAnchorOffset = new int[0];
    int[] blockAnchorCount = new int[0];
    int[] blockAnchorStartDrawIndex = new int[0];
    int[] blockAnchorEndDrawIndex = new int[0];

    int[] anchorDrawSlot = new int[0];
    int[] anchorDrawIndex = new int[0];

    public void clear() {
        blockCount = 0;
        anchorCount = 0;
    }

    public int blockCount() {
        return blockCount;
    }

    int addBlock(int anchors) {
        if (anchors <= 0) {
            throw new IllegalArgumentException("Spatial block anchor count must be positive.");
        }

        ensureBlockCapacity(blockCount + 1);
        ensureAnchorCapacity(anchorCount + anchors);

        int block = blockCount++;
        blockAnchorOffset[block] = anchorCount;
        blockAnchorCount[block] = anchors;
        blockAnchorStartDrawIndex[block] = -1;
        blockAnchorEndDrawIndex[block] = -1;

        for (int i = 0; i < anchors; i++) {
            int anchor = anchorCount + i;
            anchorDrawSlot[anchor] = -1;
            anchorDrawIndex[anchor] = -1;
        }

        anchorCount += anchors;
        return block;
    }

    void setAnchor(int block, int localAnchor, int drawSlot, int drawIndex) {
        if (block < 0 || block >= blockCount) {
            throw new IndexOutOfBoundsException("Invalid spatial block cache index: " + block);
        }
        int count = blockAnchorCount[block];
        if (localAnchor < 0 || localAnchor >= count) {
            throw new IndexOutOfBoundsException("Invalid spatial block anchor index: " + localAnchor);
        }
        if (drawSlot < 0 || drawIndex < 0) {
            throw new IllegalArgumentException("Spatial block anchor must have valid draw slot and draw index.");
        }

        int anchor = blockAnchorOffset[block] + localAnchor;
        anchorDrawSlot[anchor] = drawSlot;
        anchorDrawIndex[anchor] = drawIndex;
    }

    void finalizeBlockRange(int block) {
        if (block < 0 || block >= blockCount) {
            throw new IndexOutOfBoundsException("Invalid spatial block cache index: " + block);
        }

        int offset = blockAnchorOffset[block];
        int count = blockAnchorCount[block];
        int minDrawIndex = Integer.MAX_VALUE;
        int maxDrawIndex = Integer.MIN_VALUE;

        for (int i = 0; i < count; i++) {
            int anchor = offset + i;
            if (anchorDrawSlot[anchor] < 0 || anchorDrawIndex[anchor] < 0) continue;
            int drawIndex = anchorDrawIndex[anchor];
            if (drawIndex < minDrawIndex) minDrawIndex = drawIndex;
            if (drawIndex > maxDrawIndex) maxDrawIndex = drawIndex;
        }

        if (minDrawIndex == Integer.MAX_VALUE) {
            blockAnchorStartDrawIndex[block] = -1;
            blockAnchorEndDrawIndex[block] = -1;
        } else {
            blockAnchorStartDrawIndex[block] = minDrawIndex;
            blockAnchorEndDrawIndex[block] = maxDrawIndex;
        }
    }

    void finalizeRanges() {
        for (int block = 0; block < blockCount; block++) {
            finalizeBlockRange(block);
        }
    }

    public void convertDrawIndexRangesToBuckets(int[] drawIndexToBucketBefore,
                                                int[] drawIndexToBucketAfter,
                                                int drawListSize) {
        if (drawIndexToBucketBefore == null || drawIndexToBucketAfter == null) {
            throw new IllegalArgumentException("Stable draw-index bucket maps are required.");
        }
        for (int block = 0; block < blockCount; block++) {
            int startDrawIndex = blockAnchorStartDrawIndex[block];
            int endDrawIndex = blockAnchorEndDrawIndex[block];
            if (startDrawIndex < 0 || endDrawIndex < 0) continue;
            if (startDrawIndex < 0
                    || startDrawIndex >= drawListSize
                    || endDrawIndex < 0
                    || endDrawIndex >= drawListSize
                    || startDrawIndex >= drawIndexToBucketBefore.length
                    || endDrawIndex >= drawIndexToBucketAfter.length) {
                throw new IllegalStateException("Spatial block anchor draw range is outside stable snapshot: block=" + block);
            }

            int startBucket = drawIndexToBucketBefore[startDrawIndex];
            int endBucketInclusive = drawIndexToBucketAfter[endDrawIndex] - 1;
            if (endBucketInclusive < startBucket) endBucketInclusive = startBucket;
            blockAnchorStartDrawIndex[block] = startBucket;
            blockAnchorEndDrawIndex[block] = endBucketInclusive;
        }
    }

    public boolean hasResolvedBlock(int block) {
        return block >= 0
                && block < blockCount
                && blockAnchorStartDrawIndex[block] >= 0
                && blockAnchorEndDrawIndex[block] >= 0;
    }

    private void ensureBlockCapacity(int required) {
        if (required <= blockAnchorOffset.length) return;
        int next = Math.max(4, blockAnchorOffset.length);
        while (required > next) next <<= 1;
        blockAnchorOffset = grow(blockAnchorOffset, next);
        blockAnchorCount = grow(blockAnchorCount, next);
        blockAnchorStartDrawIndex = grow(blockAnchorStartDrawIndex, next);
        blockAnchorEndDrawIndex = grow(blockAnchorEndDrawIndex, next);
    }

    private void ensureAnchorCapacity(int required) {
        if (required <= anchorDrawSlot.length) return;
        int next = Math.max(8, anchorDrawSlot.length);
        while (required > next) next <<= 1;
        anchorDrawSlot = grow(anchorDrawSlot, next);
        anchorDrawIndex = grow(anchorDrawIndex, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] out = new int[next];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }
}
