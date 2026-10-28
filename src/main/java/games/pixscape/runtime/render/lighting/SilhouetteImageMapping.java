package games.pixscape.runtime.render.lighting;

/** One reference projection for all support faces; never a per-face sprite repetition. */
public final class SilhouetteImageMapping {
    private SilhouetteImageMapping() {}
    public static void imageUV(float[] q,float x,float y,float[] out,int at) {
        float ax=q[6]-q[0],ay=q[7]-q[1],bx=q[2]-q[0],by=q[3]-q[1];
        float d=ax*by-ay*bx;
        if(Math.abs(d)<.000001f)throw new IllegalArgumentException("Degenerate receiver image quad");
        x-=q[0];y-=q[1];out[at]=(x*by-y*bx)/d;out[at+1]=(ax*y-ay*x)/d;
    }
}
