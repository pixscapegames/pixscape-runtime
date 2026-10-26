package games.pixscape.runtime.spatial;

/** Vertical actor spatial rectangle in common XYZ native-pixel units.
 * The pivot is the lower edge centre. Offsets place the sorting envelope and caster relative to
 * the actor footprint anchor. Zero yaw aligns width with +X; positive yaw turns
 * +X towards +Y. Receiver placement and collisions remain independent.
 */
public final class ActorSpatialRectangleData {
    public float width = 32f, height = 64f;
    public float offsetX, offsetY, offsetZ, orientation;

    public void validate() {
        if (!finite(width) || !finite(height) || width <= 0 || height <= 0
                || !finite(offsetX) || !finite(offsetY) || !finite(offsetZ) || !finite(orientation))
            throw new IllegalArgumentException("Actor spatial rectangle requires positive finite dimensions and finite placement/yaw");
    }
    private static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }
    public static float normalize(float angle) { return ((angle % 360f) + 360f) % 360f; }
    public ActorSpatialRectangleData copy() {
        ActorSpatialRectangleData v = new ActorSpatialRectangleData();
        v.width=width; v.height=height;
        v.offsetX=offsetX; v.offsetY=offsetY; v.offsetZ=offsetZ; v.orientation=orientation;
        return v;
    }
    public boolean same(ActorSpatialRectangleData v) {
        return v!=null && width==v.width && height==v.height
                && offsetX==v.offsetX && offsetY==v.offsetY && offsetZ==v.offsetZ && orientation==v.orientation;
    }
    /** Four corners: bottom-left, top-left, top-right, bottom-right. Caller owns storage. */
    public void corners(float anchorX,float anchorY,float anchorZ,float[] out) {
        double radians=orientation*Math.PI/180, c=Math.cos(radians), s=Math.sin(radians);
        for(int i=0;i<4;i++) {
            float x=(i<2 ? -.5f : .5f)*width;
            out[3*i]=anchorX+offsetX+(float)(c*x);
            out[3*i+1]=anchorY+offsetY+(float)(s*x);
            out[3*i+2]=anchorZ+offsetZ+(i==1||i==2?height:0);
        }
    }
}
