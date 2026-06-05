package games.pixscape.runtime.component;

public final class SpatialBlockData {
    public static final float DEFAULT_HEIGHT = 128f;

    public int id = 0;
    public String name = null;
    public boolean enabled = true;

    /**
     * Layer/map-local footprint origin. Width and depth are measured in the
     * same local units; orientation is resolved through the tiled layer later.
     */
    public float x = 0f;
    public float y = 0f;
    public float width = 0f;
    public float depth = 0f;

    public float altitude = 0f;
    public float height = DEFAULT_HEIGHT;
    public SpatialBlockOrientation orientation = SpatialBlockOrientation.TILE_CELL;

    public boolean actorOccluder = true;
    public boolean physicsCollision = false;
    public boolean lightOccluder = false;
    public boolean shadowCaster = false;
    public boolean particleOccluder = false;

    public SpatialBlockData copy() {
        SpatialBlockData b = new SpatialBlockData();
        b.id = id;
        b.name = name;
        b.enabled = enabled;
        b.x = x;
        b.y = y;
        b.width = width;
        b.depth = depth;
        b.altitude = altitude;
        b.height = height;
        b.orientation = orientation;
        b.actorOccluder = actorOccluder;
        b.physicsCollision = physicsCollision;
        b.lightOccluder = lightOccluder;
        b.shadowCaster = shadowCaster;
        b.particleOccluder = particleOccluder;
        return b;
    }
}
