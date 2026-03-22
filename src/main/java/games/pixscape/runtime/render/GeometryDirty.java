// ------------------------------------------------------------
// GeometryDirty.java
// ------------------------------------------------------------
package games.pixscape.runtime.render;

/** Submask GEOMETRY (logical granularity). */
public final class GeometryDirty {

    private GeometryDirty() {}

    public static final int NONE     = 0;

    public static final int POSITION = 1 << 0; // x/y
    public static final int ORIGIN   = 1 << 1; // originX/originY
    public static final int ROTATION = 1 << 2; // rotationRad (cos/sin + axes)
    public static final int SCALE    = 1 << 3; // scaleX/scaleY (half extents + AABB)
    public static final int SIZE     = 1 << 4; // Dimensions (width/height)

    public static final int ALL = POSITION | ORIGIN | ROTATION | SCALE | SIZE;

    /** Recalc trig/axes/half-extents. */
    public static final int AXES_MASK = ROTATION | SCALE | SIZE;

    /** Recalc center + AABB corners. */
    public static final int AABB_MASK = POSITION | ORIGIN | ROTATION | SCALE | SIZE;
}
