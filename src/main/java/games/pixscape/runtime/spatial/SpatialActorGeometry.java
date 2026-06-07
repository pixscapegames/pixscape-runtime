package games.pixscape.runtime.spatial;

public final class SpatialActorGeometry {
    private SpatialActorGeometry() {
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
