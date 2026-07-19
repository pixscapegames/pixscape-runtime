package games.pixscape.runtime.spatial;

/** Raised when exact Spatial V3 anchor constraints leave one or more actors with no valid bucket. */
public final class SpatialConstraintInvariantException extends IllegalStateException {
    private final int unresolvedConstraintCount;

    public SpatialConstraintInvariantException(int unresolvedConstraintCount, String message) {
        super(message);
        this.unresolvedConstraintCount = unresolvedConstraintCount;
    }

    public int unresolvedConstraintCount() {
        return unresolvedConstraintCount;
    }
}
