package games.pixscape.runtime.render.lighting;

import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedWallSupportTest {
    private static final float[] Q={384,3264,384,3776,640,3776,640,3264};
    private RoundedWallSupport support(){return new RoundedWallSupport(3648,3136,128,30.07935f,0,157.34424f,6,Q);}
    @Test public void contactsAndTerminalOrientationsMatchBothStraightWalls(){
        RoundedWallSupport s=support();
        assertEquals(3520,s.outerXY[0],0);assertEquals(3136,s.outerXY[1],0);
        assertEquals(3648,s.outerXY[12],0);assertEquals(3008,s.outerXY[13],0);
        assertEquals(3550.07935f,s.innerXY[0],.001f);assertEquals(3038.07935f,s.innerXY[13],.001f);
        float[] v=s.texturedCaster.vertices;
        assertEquals(-1,v[5],.00001f);assertEquals(0,v[6],.00001f);
        assertEquals(0,v[5*48+5],.00001f);assertEquals(-1,v[5*48+6],.00001f);
    }
    @Test public void visiblePhysicalVerticesRemainRegisteredToTheNativeImage(){
        RoundedWallSupport s=support();float[] v=s.receiver.vertices;
        for(int o=0;o<v.length;o+=8){
            if(v[o+5]==0&&v[o+6]==0&&v[o+7]==0)continue;
            assertEquals(Q[0]+v[o+3]*256,LightingCoordinates.screenX(v[o],v[o+1]),.002f);
            assertEquals(Q[1]+v[o+4]*512,LightingCoordinates.screenY(v[o],v[o+1],v[o+2]),.002f);
        }
        // This opaque face pixel must lie on the curved wall, rather than the old X/Y corner.
        float[] p=at(s.receiver.vertices,.5f,(3395-3264)/512f);
        assertNotNull(p);assertTrue(p[0]>3550);assertTrue(p[1]>3040);assertTrue(p[2]>90&&p[2]<102);
    }
    @Test public void backOpacityIsTransportedThroughThicknessAndBottomIsSeparate(){
        RoundedWallSupport s=support();float[] v=s.texturedCaster.vertices;int back=6*48;
        for(int i=0;i<6*48;i+=8){assertEquals(v[i+3],v[back+i+3],0);assertEquals(v[i+4],v[back+i+4],0);}
        assertTrue(Math.abs(v[0]-v[back])>20);
        assertEquals(6*6*3,v.length/8);assertEquals(6*6,s.bottomCaster.vertices.length/8);
        assertTrue(s.receiver.shadowCaster);assertSame(s.texturedCaster,s.receiver.casterSurface);assertTrue(s.texturedCaster.shadowCaster);
        for(int o=0;o<s.bottomCaster.vertices.length;o+=8){assertEquals(0,s.bottomCaster.vertices[o+2],0);assertEquals(-1,s.bottomCaster.vertices[o+7],0);}
    }
    @Test public void receiverPartitionCoversTheImageExactlyOnce(){
        float[] v=support().receiver.vertices;double area=0;
        for(int o=0;o<v.length;o+=24)area+=Math.abs((v[o+11]-v[o+3])*(v[o+20]-v[o+4])-(v[o+12]-v[o+4])*(v[o+19]-v[o+3]))*.5;
        assertEquals(1,area,.0001);
        // Independent geometric check: pairwise triangle intersection, not area cancellation.
        for(int a=0;a<v.length;a+=24)for(int b=a+24;b<v.length;b+=24){
            java.util.List<double[]> polygon=new java.util.ArrayList<>();
            for(int k=0;k<3;k++)polygon.add(new double[]{v[a+k*8+3],v[a+k*8+4]});
            double orientation=(v[b+11]-v[b+3])*(v[b+20]-v[b+4])-(v[b+12]-v[b+4])*(v[b+19]-v[b+3]);
            if(Math.abs(orientation)<1e-12)continue;
            for(int edge=0;edge<3&&!polygon.isEmpty();edge++){
                int p=b+edge*8,q=b+(edge+1)%3*8;
                java.util.List<double[]> clipped=new java.util.ArrayList<>();
                for(int k=0;k<polygon.size();k++){
                    double[] x=polygon.get((k+polygon.size()-1)%polygon.size()),y=polygon.get(k);
                    double dx=((v[q+3]-v[p+3])*(x[1]-v[p+4])-(v[q+4]-v[p+4])*(x[0]-v[p+3]))*Math.signum(orientation);
                    double dy=((v[q+3]-v[p+3])*(y[1]-v[p+4])-(v[q+4]-v[p+4])*(y[0]-v[p+3]))*Math.signum(orientation);
                    if((dx>=0)!=(dy>=0)){double t=dx/(dx-dy);clipped.add(new double[]{x[0]+t*(y[0]-x[0]),x[1]+t*(y[1]-x[1])});}
                    if(dy>=0)clipped.add(y);
                }
                polygon=clipped;
            }
            double overlap=0;for(int k=0;k<polygon.size();k++){double[] p=polygon.get(k),q=polygon.get((k+1)%polygon.size());overlap+=p[0]*q[1]-q[0]*p[1];}
            assertEquals("Triangle overlap "+a/24+" / "+b/24,0,Math.abs(overlap)*.5,1e-9);
        }
    }
    @Test public void immutableOverlayKeepsExactGeometricAndLightingInterpolants(){
        RoundedWallSupport support=support();LightingSurface s=support.receiver;ForwardTopology t=s.forwardTopology;
        float[] p=s.receiverVertices==null?s.vertices:s.receiverVertices;
        for(int i=0;i<t.vertexCount;i++){
            int source=t.source[i];if(source<0)continue;
            float a=t.weights[i*5+1],b=t.weights[i*5+2],c=1-a-b;
            for(int j=0;j<3;j++){
                assertEquals(a*p[source+j]+b*p[source+8+j]+c*p[source+16+j],s.forwardAttributes[i*11+j],0);
                assertEquals(a*p[source+5+j]+b*p[source+13+j]+c*p[source+21+j],s.forwardAttributes[i*11+3+j],0);
                int ns=source/8*3;
                assertEquals(a*s.curveNormals[ns+j]+b*s.curveNormals[ns+3+j]+c*s.curveNormals[ns+6+j],s.forwardAttributes[i*11+8+j],0);
            }
        }
    }
    @Test public void invalidAndUnboundedProfilesAreRejected(){
        try{new RoundedWallSupport(3699.2f,3187.2f,179.2f,30,0,158,6,Q);fail();}catch(IllegalArgumentException expected){}
        try{new RoundedWallSupport(3648,3136,128,30,0,450,6,Q);fail();}catch(IllegalArgumentException expected){}
        for(int n:new int[]{0,2,9,100})try{new RoundedWallSupport(0,0,128,30,0,158,n,Q);fail();}catch(IllegalArgumentException expected){}
        try{new RoundedWallSupport(0,0,30,30,0,158,6,Q);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void sharedFacadeNormalsAndTerminalEdgesAreExact(){
        RoundedWallSupport s=support();float[] n=s.receiver.curveNormals,v=s.receiver.vertices;
        assertEquals(52,v.length/24);assertEquals(24,6*4);
        for(int a=0;a<6*48;a+=8){
            int na=a/8*3;assertEquals(1,n[na]*n[na]+n[na+1]*n[na+1],.000001);
            if(v[a+1]==3136){assertEquals(-1,n[na],0);assertEquals(0,n[na+1],0);}
            if(v[a]==3648){assertEquals(0,n[na],0);assertEquals(-1,n[na+1],0);}
            for(int b=0;b<6*48;b+=8)if(v[a]==v[b]&&v[a+1]==v[b+1]){
                assertEquals(n[na],n[b/8*3],0);assertEquals(n[na+1],n[b/8*3+1],0);
            }
        }
        for(int a=6*48;a<12*48;a+=8){assertEquals(0,n[a/8*3],0);assertEquals(0,n[a/8*3+1],0);assertEquals(1,n[a/8*3+2],0);}
    }
    @Test public void altitudeTranslationMovesAllPhysicalContactsButNotUvOrOrientations(){
        RoundedWallSupport a=support();float[] translated=Q.clone();for(int i=1;i<8;i+=2)translated[i]+=80;
        RoundedWallSupport b=new RoundedWallSupport(3648,3136,128,30.07935f,80,157.34424f,6,translated);
        for(float[][] pair:new float[][][]{ {a.texturedCaster.vertices,b.texturedCaster.vertices},{a.bottomCaster.vertices,b.bottomCaster.vertices} }){
            for(int i=0;i<pair[0].length;i+=8)for(int c=0;c<8;c++)assertEquals(pair[0][i+c]+(c==2?80:0),pair[1][i+c],.0001);
        }
        assertArrayEquals(a.receiver.curveNormals,b.receiver.curveNormals,.0001f);
    }
    @Test public void terminalResponseMatchesWholeWallEdgeAndDarkSideIsExactlyZero(){
        RoundedWallSupport s=support();float[] v=s.receiver.vertices,n=s.receiver.curveNormals;
        for(int facet:new int[]{0,5})for(int step=0;step<=20;step++){
            int at=facet==0?0:5*48+8,normalAt=at/8*3;
            float z=step*157.34424f/20,px=v[at],py=v[at+1];
            for(int angle=0;angle<360;angle++){
                float lx=px+(float)Math.cos(Math.toRadians(angle))*200,ly=py+(float)Math.sin(Math.toRadians(angle))*200,lz=80;
                float dx=lx-px,dy=ly-py,dz=lz-z,len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
                float continuous=(n[normalAt]*dx+n[normalAt+1]*dy)/len;
                float wall=(facet==0?-dx:-dy)/len;
                assertEquals(response(wall),response(continuous),0);
                if(continuous<=0)assertEquals(0,response(continuous),0);
            }
        }
    }
    private static float response(float cosine){float t=Math.max(0,Math.min(1,(cosine-.052335956f)/(.087155743f-.052335956f)));return Math.max(.1f,cosine)*t*t*(3-2*t);}
    private static float[] at(float[] v,float u,float w){
        for(int o=0;o<v.length;o+=24){if(v[o+5]==0&&v[o+6]==0&&v[o+7]==0)continue;
            double ax=v[o+11]-v[o+3],ay=v[o+12]-v[o+4],bx=v[o+19]-v[o+3],by=v[o+20]-v[o+4],d=ax*by-ay*bx;
            if(Math.abs(d)<1e-10)continue;double x=u-v[o+3],y=w-v[o+4],a=(x*by-y*bx)/d,b=(ax*y-ay*x)/d;
            if(a<0||b<0||a+b>1)continue;
            return new float[]{(float)(v[o]+a*(v[o+8]-v[o])+b*(v[o+16]-v[o])),(float)(v[o+1]+a*(v[o+9]-v[o+1])+b*(v[o+17]-v[o+1])),(float)(v[o+2]+a*(v[o+10]-v[o+2])+b*(v[o+18]-v[o+2]))};
        }return null;
    }
}
