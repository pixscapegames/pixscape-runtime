package games.pixscape.runtime.spatial;

public final class SpatialRelationKernel {
    public static final int NO_RELATION = 0;
    public static final int ACTOR_BEHIND_BLOCK = 1;
    public static final int ACTOR_IN_FRONT_OF_BLOCK = 2;

    private static final float EPSILON = 0.0001f;

    public int relation(float actorCenterX,
                        float actorCenterY,
                        float x1,
                        float y1,
                        float x2,
                        float y2) {
        float minX = Math.min(x1, x2);
        float maxX = Math.max(x1, x2);
        boolean inside = actorCenterX >= minX && actorCenterX < maxX;
        if (!inside) return NO_RELATION;

        float dx = x2 - x1;
        if (Math.abs(dx) <= EPSILON) return NO_RELATION;

        float t = (actorCenterX - x1) / dx;
        float fy = y1 + t * (y2 - y1);
        float delta = fy - actorCenterY;
        if (delta > EPSILON) {
            return ACTOR_IN_FRONT_OF_BLOCK;
        } else if (delta < -EPSILON) {
            return ACTOR_BEHIND_BLOCK;
        }
        return ACTOR_IN_FRONT_OF_BLOCK;
    }
}
