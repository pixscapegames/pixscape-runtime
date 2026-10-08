package games.pixscape.runtime.spatial;

public final class SpatialActorGeometry {
    private SpatialActorGeometry() {
    }

    /**
     * Writes the H-by-H influence square in projected world units, bottom-left first.
     * The effective circle centre already includes the actor's projected altitude.
     * Positive world Y is the existing vertical projection; height is not texture scale.
     * Degenerate dimensions have no area and never fall back to texture bounds.
     */
    public static void writeInfluenceQuad(float footX, float footY, float height,
                                         float offsetX, float offsetY, float[] out) {
        float cx = footX + offsetX;
        float bottom = footY + offsetY;
        float halfWidth = height * .5f;
        float left = cx - halfWidth;
        float right = cx + halfWidth;
        float top = bottom + height;
        out[0] = left; out[1] = bottom;
        out[2] = right; out[3] = bottom;
        out[4] = right; out[5] = top;
        out[6] = left; out[7] = top;
    }

    public static final class Footprint {
        public float footX;
        public float footY;
        public float minX;
        public float maxX;
        public float minY;
        public float maxY;
        public float bottom;
        public float top;
        public boolean pointOnly;
    }
}
