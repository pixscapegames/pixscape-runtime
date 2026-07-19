package games.pixscape.runtime.spatial;

/** Resolves each canonical layer-local tile anchor once against one immutable frame snapshot. */
public final class SpatialFaceAnchorResolver {
    public void resolve(SpatialProjectedFaceCache faces,
                        int[] tiledRefToDrawIndex,
                        int[] drawIndexToBucketBefore,
                        int[] drawIndexToBucketAfter,
                        int drawListSize) {
        if (faces == null || tiledRefToDrawIndex == null) return;
        for (int anchor = 0; anchor < faces.anchorCount; anchor++) {
            int tiledRef = faces.anchorTiledRef[anchor];
            int drawIndex = tiledRef >= 0 && tiledRef < tiledRefToDrawIndex.length
                    ? tiledRefToDrawIndex[tiledRef] : -1;
            boolean resolved = drawIndex >= 0 && drawIndex < drawListSize
                    && drawIndex < drawIndexToBucketBefore.length
                    && drawIndex < drawIndexToBucketAfter.length;
            faces.anchorResolved[anchor] = resolved;
            faces.anchorBeforeBucket[anchor] = resolved ? drawIndexToBucketBefore[drawIndex] : -1;
            faces.anchorAfterBucket[anchor] = resolved ? drawIndexToBucketAfter[drawIndex] : -1;
        }
    }
}
