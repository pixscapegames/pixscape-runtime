package games.pixscape.runtime.spatial;

/**
 * {@code SUPPORTED_EXPERT} caller-owned mutable result of one Spatial occlusion query.
 * Reuse through query output parameters when allocation matters; this is not a live Runtime view.
 */
public final class SpatialOcclusionResult {
    public boolean occluded;
    public boolean partiallyOccluded;
    public float occluderBottom;
    public float occluderTop;
    public float actorBottom;
    public float actorTop;

    public SpatialOcclusionResult reset() {
        occluded = false;
        partiallyOccluded = false;
        occluderBottom = 0f;
        occluderTop = 0f;
        actorBottom = 0f;
        actorTop = 0f;
        return this;
    }
}
