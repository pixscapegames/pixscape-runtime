package games.pixscape.runtime.render.lighting;

/** Shared reference projection, image clipping and fan triangulation for a convex quad.
 * Reusable scratch; no allocations during compilation. Receiver partitioning is independent.
 */
public final class SilhouetteFaceCompiler {
    private final float[] a=new float[80],b=new float[80];
    /** Writes interleaved XYZ/UV/normal triangles; returns their vertex count. */
    public int compile(float[] face,float[] quad,float[] output,int outputOffset){
        System.arraycopy(face,0,a,0,32);
        for(int o=0;o<32;o+=8)
            SilhouetteImageMapping.imageUV(quad,LightingCoordinates.screenX(a[o],a[o+1]),
                    LightingCoordinates.screenY(a[o],a[o+1],a[o+2]),a,o+3);
        int count=cut(a,4,b,1,0,0,true);count=cut(b,count,a,1,0,-1,false);
        count=cut(a,count,b,0,1,0,true);count=cut(b,count,a,0,1,-1,false);
        float area=0;
        for(int i=0;i<count;i++){int j=(i+1)%count;area+=a[i*8+3]*a[j*8+4]-a[j*8+3]*a[i*8+4];}
        if(count<3 || Math.abs(area)*.5f<=.000001f)return 0;
        int at=outputOffset;
        for(int i=1;i<count-1;i++)for(int k=0;k<3;k++){
            int v=k==0?0:k==1?i:i+1;System.arraycopy(a,v*8,output,at,8);at+=8;
        }
        return (at-outputOffset)/8;
    }
    private static int cut(float[] input,int count,float[] output,float a,float b,float c,boolean positive){
        if(count<3)return 0;
        int at=0;
        for(int i=0;i<count;i++){
            int j=(i+count-1)%count;
            float d0=a*input[j*8+3]+b*input[j*8+4]+c,d1=a*input[i*8+3]+b*input[i*8+4]+c;
            if(!positive){d0=-d0;d1=-d1;}boolean in0=d0>=-.000001f,in1=d1>=-.000001f;
            if(in0!=in1){float t=d0/(d0-d1);for(int k=0;k<8;k++)output[at++]=input[j*8+k]+t*(input[i*8+k]-input[j*8+k]);}
            if(in1){System.arraycopy(input,i*8,output,at,8);at+=8;}
        }
        return at/8;
    }
}
