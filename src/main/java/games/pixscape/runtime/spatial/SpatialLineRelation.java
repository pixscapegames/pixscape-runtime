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

    /** A circle crossing the support line keeps the existing center-based deterministic side. */
    public static byte circleRelation(float lineYAtCenter,
                                      float centerY,
                                      float inverseNormalLength,
                                      float radius) {
        if (radius <= 0f) return relation(lineYAtCenter, centerY);
        float signedDistance = (lineYAtCenter - centerY) * inverseNormalLength;
        if (signedDistance < -radius - EPSILON) {
            return SpatialFaceRelationSolver.ACTOR_BEHIND_FACE;
        }
        if (signedDistance > radius + EPSILON) {
            return SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE;
        }
        return relation(lineYAtCenter, centerY);
    }
}
