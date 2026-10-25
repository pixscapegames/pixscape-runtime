package games.pixscape.runtime.spatial;

/** Authored actor shadow envelope in common XYZ native-pixel units. Independent of collisions.
 * The pivot is the base centre; offsets are in world axes relative to the actor anchor.
 * Zero aligns width with +X and depth with +Y; positive yaw turns +X towards +Y.
 */
public final class ActorVolumeData {
    public float width = 32f, depth = 32f, height = 64f;
    public float offsetX, offsetY, offsetZ, orientation;

    public void validate() {
        if (!finite(width) || !finite(depth) || !finite(height) || width <= 0 || depth <= 0 || height <= 0
                || !finite(offsetX) || !finite(offsetY) || !finite(offsetZ) || !finite(orientation))
            throw new IllegalArgumentException("Actor volume requires positive finite dimensions and finite placement/yaw");
    }
    private static boolean finite(float v) { return !Float.isNaN(v) && !Float.isInfinite(v); }
    public static float normalize(float angle) { return ((angle % 360f) + 360f) % 360f; }
    public ActorVolumeData copy() {
        ActorVolumeData v = new ActorVolumeData();
        v.width=width; v.depth=depth; v.height=height;
        v.offsetX=offsetX; v.offsetY=offsetY; v.offsetZ=offsetZ; v.orientation=orientation;
        return v;
    }
    public boolean same(ActorVolumeData v) {
        return v!=null && width==v.width && depth==v.depth && height==v.height
                && offsetX==v.offsetX && offsetY==v.offsetY && offsetZ==v.offsetZ && orientation==v.orientation;
    }
    /** Eight corners, base then top, counterclockwise viewed from +Z. Caller owns storage. */
    public void corners(float anchorX,float anchorY,float anchorZ,float[] out) {
        double radians=orientation*Math.PI/180, c=Math.cos(radians), s=Math.sin(radians);
        for(int i=0;i<8;i++) {
            float x=(i%4==0 || i%4==3 ? -.5f : .5f)*width;
            float y=(i%4<2 ? -.5f : .5f)*depth;
            out[3*i]=anchorX+offsetX+(float)(c*x-s*y);
            out[3*i+1]=anchorY+offsetY+(float)(s*x+c*y);
            out[3*i+2]=anchorZ+offsetZ+(i<4?0:height);
        }
    }
}
