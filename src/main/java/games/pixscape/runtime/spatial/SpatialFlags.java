package games.pixscape.runtime.spatial;

public final class SpatialFlags {
    private SpatialFlags() {
    }

    public static final int NONE = 0;
    public static final int ACTOR_OCCLUDER = 1 << 0;
    public static final int LIGHT_OCCLUDER = 1 << 1;
    public static final int PARTICLE_OCCLUDER = 1 << 2;
    public static final int SPATIAL_DEPTH = 1 << 3;
}
