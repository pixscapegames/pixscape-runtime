package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Json;
import org.junit.Test;
import static org.junit.Assert.*;

public class EditableCornerProfileTest {
    private static final float[] Q={384,3264,384,3776,640,3776,640,3264};
    private static RoundedWallDescription explicit(){RoundedWallDescription d=new RoundedWallDescription();d.profileXY=new RoundedWallSupport.Profile(6).vertices();return d;}
    private static RoundedWallSupport compile(RoundedWallDescription d){return new RoundedWallSupport(3648,3136,128,30.07935f,0,157.34424f,new RoundedWallSupport.Profile(d),Q);}
    @Test public void legacyReadDoesNotConvertAndCopySerializationAreDetached(){
        RoundedWallDescription legacy=new Json().fromJson(RoundedWallDescription.class,"{}");
        new RoundedWallSupport.Profile(legacy).vertices();assertNull(legacy.profileXY);
        RoundedWallDescription d=explicit(),copy=d.copy();assertTrue(d.sameAs(copy));
        RoundedWallDescription loaded=new Json().fromJson(RoundedWallDescription.class,new Json().toJson(d));
        assertTrue(d.sameAs(loaded));copy.profileXY[4]+=.02f;assertFalse(d.sameAs(copy));assertTrue(d.sameAs(loaded));
    }
    @Test public void explicitInitialShapeAndThicknessMatchLegacy(){
        RoundedWallSupport old=compile(new RoundedWallDescription()),edited=compile(explicit());
        assertArrayEquals(old.outerXY,edited.outerXY,.0003f);assertArrayEquals(old.innerXY,edited.innerXY,.0003f);
        assertNotNull(edited.receiver.forwardAttributes);
    }
    @Test public void insertionKeepsOuterAndInnerSegments(){
        RoundedWallDescription d=explicit();float[] p=d.profileXY,n=new float[p.length+2];int at=6;
        System.arraycopy(p,0,n,0,at);n[at]=(p[at-2]+p[at])*.5f;n[at+1]=(p[at-1]+p[at+1])*.5f;
        System.arraycopy(p,at,n,at+2,p.length-at);d.profileXY=n;
        RoundedWallSupport s=compile(d),old=compile(explicit());
        assertEquals(7,s.segments);
        for(int i=0;i<old.outerXY.length;i++)assertEquals(old.outerXY[i],s.outerXY[i<at?i:i+2],.0003f);
        for(int i=0;i<old.innerXY.length;i++)assertEquals(old.innerXY[i],s.innerXY[i<at?i:i+2],.001f);
    }
    @Test public void nonConvexDegenerateAndBrokenTangentsAreRejected(){
        for(int kind=0;kind<4;kind++) {
            RoundedWallDescription d=explicit();
            if(kind==0)d.profileXY[0]=-.9f;
            if(kind==1)d.profileXY[2]=-.9f;
            if(kind==2){d.profileXY[4]=d.profileXY[2];d.profileXY[5]=d.profileXY[3];}
            if(kind==3){d.profileXY[6]=-.5f;d.profileXY[7]=-.5f;}
            try{compile(d);fail("Expected rejection "+kind);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void editedNormalsAreContinuousAndKeepTopAndEndOrientations(){
        RoundedWallDescription d=explicit();d.profileXY[4]+=.015f;RoundedWallSupport s=compile(d);
        float[] p=s.receiver.vertices,n=s.receiver.curveNormals;
        for(int i=0;i<s.segments*48;i+=8) {
            int k=i/8*3;assertEquals(1,n[k]*n[k]+n[k+1]*n[k+1],1e-6);
            if(p[i+1]==3136){assertEquals(-1,n[k],1e-6);assertEquals(0,n[k+1],1e-6);}
            if(p[i]==3648){assertEquals(0,n[k],1e-6);assertEquals(-1,n[k+1],1e-6);}
            for(int j=0;j<s.segments*48;j+=8)if(p[i]==p[j]&&p[i+1]==p[j+1]) {
                assertEquals(n[k],n[j/8*3],0);assertEquals(n[k+1],n[j/8*3+1],0);
            }
        }
    }
}
