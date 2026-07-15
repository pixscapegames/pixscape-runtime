package games.pixscape.runtime.spatial;

/** Shared sign convention for a projected lower edge and a witness point. */
public final class SpatialLineRelation {
    public static final float EPSILON = 0.0001f;

    private SpatialLineRelation() {
    }

    public static byte relation(float lineY, float witnessY) {
        return lineY - witnessY < -EPSILON
                ? SpatialFaceRelationSolver.ACTOR_BEHIND_FACE
                : SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE;
    }
}
