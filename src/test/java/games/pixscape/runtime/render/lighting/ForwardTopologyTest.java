package games.pixscape.runtime.render.lighting;

import org.junit.Test;

import static org.junit.Assert.*;

public class ForwardTopologyTest {
    @Test public void deformedNativeDiagonalPreservesColorAndPhysicalDomainsIndependently() {
        LightingSurface surface=partition();
        float[] quad={0,0,0,1,1.4f,1,1,0};
        ForwardTopology t=ForwardTopology.compile(surface,quad);
        double total=0;boolean independent=false,ambientCompletion=false;
        for(int i=0;i<t.vertexCount;i+=3){
            float[] xy=new float[6];
            for(int j=0;j<3;j++){
                int k=i+j,w=k*5,id=(int)t.weights[w];
                assertEquals((int)t.weights[i*5],id);
                float a=t.weights[w+3],b=t.weights[w+4],c=1-a-b;
                assertTrue(a>=-1e-6f&&b>=-1e-6f&&c>=-1e-6f);
                int c0=ForwardTopology.CORNERS[id*3],c1=ForwardTopology.CORNERS[id*3+1],c2=ForwardTopology.CORNERS[id*3+2];
                xy[j*2]=a*quad[c0*2]+b*quad[c1*2]+c*quad[c2*2];
                xy[j*2+1]=a*quad[c0*2+1]+b*quad[c1*2+1]+c*quad[c2*2+1];
                if(t.source[k]<0){ambientCompletion=true;continue;}
                float pa=t.weights[w+1],pb=t.weights[w+2],pc=1-pa-pb;int o=t.source[k];
                float pu=pa*surface.vertices[o+3]+pb*surface.vertices[o+11]+pc*surface.vertices[o+19];
                float pv=pa*surface.vertices[o+4]+pb*surface.vertices[o+12]+pc*surface.vertices[o+20];
                assertEquals(xy[j*2],pu,1e-6f);assertEquals(xy[j*2+1],pv,1e-6f);
                float colorU=a*(c0>=2?1:0)+b*(c1>=2?1:0)+c*(c2>=2?1:0);
                if(Math.abs(colorU-pu)>.02f)independent=true;
            }
            total+=Math.abs((xy[2]-xy[0])*(xy[5]-xy[1])-(xy[4]-xy[0])*(xy[3]-xy[1]))*.5;
        }
        assertEquals("Complete native coverage, including the extra deformed wedge",1.2,total,1e-6);
        assertTrue("Color UVs must not follow the reception mesh",independent);
        assertTrue("Missing physical coverage must remain ambient color",ambientCompletion);
    }
    @Test public void physicalCompletionIsRetainedAndNativeSheetReusesOriginalTriangles() {
        LightingSurface s=partition();s.receiverVertices=s.vertices.clone();
        s.vertices[5]=s.vertices[6]=s.vertices[7]=0;
        ForwardTopology t=ForwardTopology.compile(s);
        for(int source:t.source)assertTrue("Completed regions still reference the prepared receiver",source>=0);
        s.imageQuad=true;assertSame(ForwardTopology.NATIVE,ForwardTopology.compile(s));
        assertEquals(6,ForwardTopology.NATIVE.vertexCount);
    }
    private static LightingSurface partition(){
        float[] uv={0,0,0,1,.4f,1,0,0,.4f,1,.4f,0,.4f,0,.4f,1,1,1,.4f,0,1,1,1,0};
        float[] v=new float[uv.length/2*8];
        for(int i=0;i<uv.length/2;i++){v[i*8]=uv[i*2];v[i*8+1]=uv[i*2+1];v[i*8+3]=uv[i*2];v[i*8+4]=uv[i*2+1];v[i*8+7]=1;}
        return new LightingSurface(v);
    }
}
