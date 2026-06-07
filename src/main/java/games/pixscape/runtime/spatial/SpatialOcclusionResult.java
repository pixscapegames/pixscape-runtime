package games.pixscape.runtime.spatial;

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
