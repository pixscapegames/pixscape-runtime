package games.pixscape.runtime.render.lighting;

/** Explicit negative-X/negative-Y quarter wall, in map-cell units. No image detection.
 * Height, base and thickness come from the two explicitly tile-linked Spatial walls. */
public final class RoundedWallDescription {
    public float centerGX = 1;
    public float centerGY = 1;
    public float radiusCells = 1;
    /** Optional normalized outer XY, relative to the center/radius. Null preserves the legacy compiler. */
    public float[] profileXY;

    public void validate() {
        if (!finite(centerGX) || !finite(centerGY) || !finite(radiusCells) || radiusCells <= 0)
            throw new IllegalArgumentException("Rounded wall requires a finite center and positive cell radius");
        if(profileXY!=null) validateProfile(profileXY);
    }
    public static void validateProfile(float[] p) {
        if(p==null || p.length<8 || p.length>18 || (p.length&1)!=0)
            throw new IllegalArgumentException("Corner profile requires 3..8 segments");
        int last=p.length-2;
        if(p[0]!=-1 || p[1]!=0 || p[last]!=0 || p[last+1]!=-1
                || p[2]!=-1 || p[last-1]!=-1)
            throw new IllegalArgumentException("Endpoints are fixed; adjacent vertices must retain wall tangents");
        for(int i=0;i<p.length;i++) if(!finite(p[i]) || p[i]<-1 || p[i]>0)
            throw new IllegalArgumentException("Corner vertices must stay inside the wall joins");
        for(int i=2;i<p.length;i+=2) {
            float dx=p[i]-p[i-2],dy=p[i+1]-p[i-1];
            if(dx<0 || dy>0 || dx*dx+dy*dy<1e-10f)
                throw new IllegalArgumentException("Corner segments must be nondegenerate and ordered between the joins");
            if(i<last) {
                float nx=p[i+2]-p[i],ny=p[i+3]-p[i+1];
                if(dx*ny-dy*nx < -1e-7f)
                    throw new IllegalArgumentException("Corner profile must remain convex");
            }
        }
    }
    private static boolean finite(float f){return !Float.isNaN(f)&&!Float.isInfinite(f);}
    public RoundedWallDescription copy(){RoundedWallDescription d=new RoundedWallDescription();d.centerGX=centerGX;d.centerGY=centerGY;d.radiusCells=radiusCells;d.profileXY=profileXY==null?null:LocalSurfaceDescription.copy(profileXY);return d;}
    public boolean sameAs(RoundedWallDescription d){
        if(d==null||centerGX!=d.centerGX||centerGY!=d.centerGY||radiusCells!=d.radiusCells)return false;
        if(profileXY==null||d.profileXY==null)return profileXY==d.profileXY;
        if(profileXY.length!=d.profileXY.length)return false;
        for(int i=0;i<profileXY.length;i++)if(profileXY[i]!=d.profileXY[i])return false;
        return true;
    }
}
