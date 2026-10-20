package games.pixscape.runtime.render.lighting;

import org.junit.Assert;
import org.junit.Test;

public class TileCommonReceiverTest {
    private static TileShadowGeometry geometry() {
        return new TileShadowGeometry(new float[]{-50,-20,-50,180,50,180,50,-20},0,0,0,1,1,0,20,60);
    }
    private static LightingFrame.Light light(float x,float y,float z) {
        LightingFrame.Light light=new LightingFrame.Light();light.x=x;light.y=y;light.z=z;return light;
    }
    @Test public void bothLightsKeepTheirOwnExactTrianglesAndReuseWorldData() {
        TileShadowGeometry g=geometry();
        LightingFrame.Light a=light(200,-200,30),b=light(200,-180,90);
        g.prepareLight(a);g.prepareLight(b);
        float[] av=g.forLight(a),bv=g.forLight(b);
        Assert.assertNotSame(av,bv);
        int prepared=g.transformations;
        for(int frame=0;frame<10;frame++)for(LightingFrame.Light l:new LightingFrame.Light[]{a,b}) {
            float[] caster=g.forLight(l),receiver=g.receiverForLight(l);
            for(int i=0;i<caster.length;i+=8)for(int k=0;k<5;k++)
                Assert.assertEquals(Float.floatToIntBits(caster[i+k]),Float.floatToIntBits(receiver[i+k]));
        }
        a.radius=100;a.cone=true;a.dx=1;a.quality=2;a.intensity=4;
        g.receiverForLight(a);
        Assert.assertEquals(prepared,g.transformations);
        b.x+=15;g.forLight(b);g.receiverForLight(b);
        Assert.assertEquals(prepared+1,g.transformations);
        Assert.assertSame(av,g.forLight(a));
    }
    @Test public void normalsFollowEachFacetAndPlaneProjectionNeverFolds() {
        TileShadowGeometry g=geometry();
        for(int angle=0;angle<360;angle++) {
            double r=Math.toRadians(angle);
            LightingFrame.Light l=light((float)Math.cos(r)*300,(float)Math.sin(r)*300,30);
            float[] v=g.receiverForLight(l);
            for(int o=0;o<v.length;o+=24) {
                float ax=v[o+8]-v[o],ay=v[o+9]-v[o+1],az=v[o+10]-v[o+2];
                float bx=v[o+16]-v[o],by=v[o+17]-v[o+1],bz=v[o+18]-v[o+2];
                float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                Assert.assertTrue(length>.001f);
                float dot=nx*v[o+5]+ny*v[o+6]+nz*v[o+7];
                Assert.assertEquals(length,Math.abs(dot),length*.0001f);
                Assert.assertTrue(v[o+5]*g.normalX+v[o+6]*g.normalY+v[o+7]*g.normalZ>0);
            }
        }
    }
}
