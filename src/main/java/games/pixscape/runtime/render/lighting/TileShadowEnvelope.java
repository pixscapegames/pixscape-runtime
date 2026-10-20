package games.pixscape.runtime.render.lighting;

/** Cold analytical extrema of the existing piecewise affine carrier inside alpha bounds.
 * Clips the UV domain only for numerical extrema; creates no rendered geometry. */
public final class TileShadowEnvelope {
    private TileShadowEnvelope() {}
    public static boolean bounds(TileShadowGeometry g,AlphaBounds alpha,float threshold,float[] out) {
        if(out==null||out.length<6)throw new IllegalArgumentException("Six envelope coordinates required");
        boolean known=alpha!=null&&alpha.usable(threshold);if(known&&alpha.empty)return false;
        // Linear sampling can retain a fragment beyond the outer retained pixel cell.
        float padU=known?.5f/alpha.sourceWidth:0,padV=known?.5f/alpha.sourceHeight:0;
        float u0=known?Math.max(0,alpha.minU-padU):0,v0=known?Math.max(0,alpha.minV-padV):0;
        float u1=known?Math.min(1,alpha.maxU+padU):1,v1=known?Math.min(1,alpha.maxV+padV):1;
        float[] a=new float[8*7],b=new float[a.length];
        for(int j=0;j<3;j++){out[j]=Float.POSITIVE_INFINITY;out[j+3]=Float.NEGATIVE_INFINITY;}
        for(int i=0;i<g.vertexCount;i+=3){
            for(int j=0;j<3;j++){int o=j*8,k=(i+j)*8;System.arraycopy(g.calibrated,k,a,o,8);a[o+5]=g.displacementWeight(i+j);}
            int n=3;
            for(int plane=0;plane<4;plane++){
                int axis=plane%2+3;float edge=plane==0?u0:plane==1?v0:plane==2?u1:v1;boolean lower=plane<2;int count=0;
                for(int j=0;j<n;j++){int prev=(j+n-1)%n;float d=a[prev*8+axis]-edge,e=a[j*8+axis]-edge;if(!lower){d=-d;e=-e;}if((d>=0)!=(e>=0)){float t=d/(d-e);for(int k=0;k<8;k++)b[count*8+k]=a[prev*8+k]+t*(a[j*8+k]-a[prev*8+k]);count++;}if(e>=0){System.arraycopy(a,j*8,b,count*8,8);count++;}}
                float[] tmp=a;a=b;b=tmp;n=count;
            }
            for(int j=0;j<n;j++)for(int axis=0;axis<3;axis++){
                float p=a[j*8+axis],normal=axis==0?g.normalX:axis==1?g.normalY:g.normalZ;
                float q=p+normal*g.halfThickness*a[j*8+5];out[axis]=Math.min(out[axis],Math.min(p,q));out[axis+3]=Math.max(out[axis+3],Math.max(p,q));
            }
        }return out[0]<=out[3];
    }
}
