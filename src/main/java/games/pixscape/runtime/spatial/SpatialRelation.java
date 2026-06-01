package games.pixscape.runtime.spatial;

public final class SpatialRelation {
    private SpatialRelation() {
    }

    public static final int NONE = 0;
    public static final int IN_FRONT_OF = 1;
    public static final int BEHIND = 2;
    public static final int OVERLAPPING = 3;
    public static final int ABOVE = 4;
    public static final int BELOW = 5;
    public static final int OCCLUDED_BY = 6;
}
