package games.pixscape.runtime.spatial;

/** Fixed projected-pixel rectangle relative to the actor's resolved footprint centre.
 * Height is also the actor's spatial height. The independent shadow pivot uses the same
 * local coordinates; neither it nor the light changes the sorting rectangle or altitude.
 */
public final class ActorSpatialRectangleData {
    public float width = 32f, height = 64f;
    public float x = -16f, y;
    public float pivotX, pivotY;

    public void validate() {
        if (!finite(width) || !finite(height) || width <= 0 || height <= 0
                || !finite(x) || !finite(y) || !finite(pivotX) || !finite(pivotY))
            throw new IllegalArgumentException("Actor spatial rectangle requires positive finite dimensions and finite local placement/pivot");
    }
    private static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }
    public ActorSpatialRectangleData copy() {
        ActorSpatialRectangleData v = new ActorSpatialRectangleData();
        v.width=width; v.height=height;
        v.x=x; v.y=y; v.pivotX=pivotX; v.pivotY=pivotY;
        return v;
    }
    public boolean same(ActorSpatialRectangleData v) {
        return v!=null && width==v.width && height==v.height
                && x==v.x && y==v.y && pivotX==v.pivotX && pivotY==v.pivotY;
    }
}
