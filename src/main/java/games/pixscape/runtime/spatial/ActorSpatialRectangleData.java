package games.pixscape.runtime.spatial;

/**
 * Reference-image zone in normalized local coordinates, before texture flips and deformation.
 * The default covers the full image. Shadow pivotX/pivotY are independent projected-pixel
 * offsets from the resolved footprint. Sorting height is derived from transformed geometry.
 */
public final class ActorSpatialRectangleData {
    public float width = 1f, height = 1f;
    public float x, y;
    public float pivotX, pivotY;

    public void validate() {
        if (!finite(width) || !finite(height) || width <= 0 || height <= 0
                || !finite(x) || !finite(y) || !finite(pivotX) || !finite(pivotY)) {
            throw new IllegalArgumentException("Actor spatial rectangle requires positive finite dimensions and finite local placement/pivot");
        }
    }

    private static boolean finite(float v) {
        return !Float.isNaN(v) && !Float.isInfinite(v);
    }

    public ActorSpatialRectangleData copy() {
        ActorSpatialRectangleData v = new ActorSpatialRectangleData();
        v.width = width;
        v.height = height;
        v.x = x;
        v.y = y;
        v.pivotX = pivotX;
        v.pivotY = pivotY;
        return v;
    }

    public boolean same(ActorSpatialRectangleData v) {
        return v != null && width == v.width && height == v.height
                && x == v.x && y == v.y && pivotX == v.pivotX && pivotY == v.pivotY;
    }
}
