package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

/** Cold overlay of the physical triangles and the two native color triangles.
 * Each output vertex borrows physical attributes through barycentric weights;
 * color coordinates retain their independent native interpolation. */
public final class ForwardTopology {
    private static final float[] UNIT_QUAD = {0,0,0,1,1,1,1,0};
    public static final ForwardTopology NATIVE = nativeTopology();
    /** Three source corners per native triangle. */
    public static final int[] CORNERS = {0,1,2,0,2,3};
    /** Source triangle's first float offset, or -1 for ambient-only completion. */
    public final int[] source;
    /** Native triangle id, physical weights A/B, and native weights A/B. */
    public final float[] weights;
    public final int vertexCount;

    private ForwardTopology(int[] source, float[] weights) {
        this.source=source;this.weights=weights;vertexCount=source.length;
    }
    private static ForwardTopology nativeTopology() {
        int[] sources=new int[6];float[] weights=new float[30];
        for(int i=0;i<6;i++) { sources[i]=i<3?0:24;int o=i*5;
            weights[o]=i/3;weights[o+1]=weights[o+3]=i%3==0?1:0;
            weights[o+2]=weights[o+4]=i%3==1?1:0; }
        return new ForwardTopology(sources,weights);
    }
    public static ForwardTopology compile(LightingSurface surface) { return compile(surface,UNIT_QUAD); }

    /** Cold composition for immutable supports only. Atlas/material/screen values remain dynamic. */
    public static void prepareImmutableAttributes(LightingSurface surface){
        ForwardTopology t=surface.forwardTopology;
        float[] p=surface.receiverVertices==null?surface.vertices:surface.receiverVertices,n=surface.curveNormals;
        float[] out=new float[t.vertexCount*11];
        for(int k=0;k<t.vertexCount;k++){
            int source=t.source[k];if(source<0)continue;
            int w=k*5,o=k*11;float a=t.weights[w+1],b=t.weights[w+2],c=1-a-b;
            for(int j=0;j<3;j++){
                out[o+j]=a*p[source+j]+b*p[source+8+j]+c*p[source+16+j];
                out[o+3+j]=a*p[source+5+j]+b*p[source+13+j]+c*p[source+21+j];
                int ns=source/8*3;
                out[o+8+j]=n==null?0:a*n[ns+j]+b*n[ns+3+j]+c*n[ns+6+j];
            }
            out[o+6]=a*p[source+3]+b*p[source+11]+c*p[source+19];
            out[o+7]=a*p[source+4]+b*p[source+12]+c*p[source+20];
        }
        surface.forwardAttributes=out;
    }

    /** Runs at support construction/invalidation, never from a submit or camera/light update. */
    public static ForwardTopology compile(LightingSurface surface,float[] quad) {
        if(surface.imageQuad)return NATIVE;
        float[] data=surface.receiverVertices==null?surface.vertices:surface.receiverVertices;
        double ax=quad[6]-quad[0],ay=quad[7]-quad[1],bx=quad[2]-quad[0],by=quad[3]-quad[1];
        double det=ax*by-ay*bx;
        if(Math.abs(det)<1e-12)throw new IllegalArgumentException("Forward native quad has a singular image basis");
        double dx=quad[4]-quad[0],dy=quad[5]-quad[1];
        double tx=(dx*by-dy*bx)/det,ty=(ax*dy-ay*dx)/det;
        double[][] corners={{0,0},{0,1},{tx,ty},{1,0}};
        IntArray sources=new IntArray();FloatArray weights=new FloatArray();
        for(int nativeId=0;nativeId<2;nativeId++) {
            double[] nativeTriangle=triangle(corners[CORNERS[nativeId*3]],corners[CORNERS[nativeId*3+1]],corners[CORNERS[nativeId*3+2]]);
            Array<double[]> remaining=new Array<double[]>();remaining.add(nativeTriangle);
            for(int offset=0;offset<data.length;offset+=24) {
                double[] physical={data[offset+3],data[offset+4],data[offset+11],data[offset+12],data[offset+19],data[offset+20]};
                if(Math.abs(area(physical))<1e-12)continue;
                double[] clipped=intersect(physical,nativeTriangle);
                emit(clipped,physical,nativeTriangle,offset,nativeId,sources,weights);
                Array<double[]> next=new Array<double[]>();
                for(int i=0;i<remaining.size;i++)subtract(remaining.get(i),physical,next);
                remaining=next;
            }
            // Color coverage is authoritative, including parts with no physical receiver.
            for(int i=0;i<remaining.size;i++)emit(remaining.get(i),nativeTriangle,nativeTriangle,-1,nativeId,sources,weights);
        }
        return new ForwardTopology(sources.toArray(),weights.toArray());
    }
    private static double[] triangle(double[] a,double[] b,double[] c) {return new double[]{a[0],a[1],b[0],b[1],c[0],c[1]};}
    private static double area(double[] p) {double sum=0;for(int i=0,j=p.length-2;i<p.length;j=i,i+=2)sum+=p[j]*p[i+1]-p[i]*p[j+1];return sum;}
    private static double cross(double ax,double ay,double bx,double by,double x,double y) {return (bx-ax)*(y-ay)-(by-ay)*(x-ax);}
    private static double[] clip(double[] p,double ax,double ay,double bx,double by,double sign) {
        if(p.length==0)return p;
        double[] out=new double[p.length+4];int size=0;
        for(int i=0,j=p.length-2;i<p.length;j=i,i+=2) {
            double a=sign*cross(ax,ay,bx,by,p[j],p[j+1]),b=sign*cross(ax,ay,bx,by,p[i],p[i+1]);
            boolean inA=a>=0,inB=b>=0;
            if(inA!=inB){double t=a/(a-b);out[size++]=p[j]+t*(p[i]-p[j]);out[size++]=p[j+1]+t*(p[i+1]-p[j+1]);}
            if(inB){out[size++]=p[i];out[size++]=p[i+1];}
        }
        double[] result=new double[size];System.arraycopy(out,0,result,0,size);return result;
    }
    private static double[] intersect(double[] p,double[] boundary) {
        double sign=area(boundary)>0?1:-1;
        for(int i=0,j=boundary.length-2;i<boundary.length && p.length>=6;j=i,i+=2)
            p=clip(p,boundary[j],boundary[j+1],boundary[i],boundary[i+1],sign);
        return p;
    }
    private static void subtract(double[] p,double[] boundary,Array<double[]> out) {
        double sign=area(boundary)>0?1:-1;
        for(int i=0,j=boundary.length-2;i<boundary.length && p.length>=6;j=i,i+=2) {
            double[] outside=clip(p,boundary[j],boundary[j+1],boundary[i],boundary[i+1],-sign);
            if(outside.length>=6 && Math.abs(area(outside))>1e-12)out.add(outside);
            p=clip(p,boundary[j],boundary[j+1],boundary[i],boundary[i+1],sign);
        }
    }
    private static void emit(double[] polygon,double[] physical,double[] nativeTriangle,int offset,int nativeId,IntArray sources,FloatArray weights) {
        for(int i=2;i+3<polygon.length;i+=2) {
            double[] tri={polygon[0],polygon[1],polygon[i],polygon[i+1],polygon[i+2],polygon[i+3]};
            if(Math.abs(area(tri))<1e-12)continue;
            for(int j=0;j<6;j+=2){sources.add(offset);weights.add(nativeId);
                barycentric(tri[j],tri[j+1],physical,weights);barycentric(tri[j],tri[j+1],nativeTriangle,weights);}
        }
    }
    private static void barycentric(double x,double y,double[] p,FloatArray out) {
        double d=(p[3]-p[5])*(p[0]-p[4])+(p[4]-p[2])*(p[1]-p[5]);
        out.add((float)(((p[3]-p[5])*(x-p[4])+(p[4]-p[2])*(y-p[5]))/d));
        out.add((float)(((p[5]-p[1])*(x-p[4])+(p[0]-p[4])*(y-p[5]))/d));
    }
}
