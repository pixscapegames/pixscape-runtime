package games.pixscape.runtime.render.lighting;

import org.junit.Assert;
import org.junit.Test;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;

public class TileShadowGeometryTest {
    private static final float[] Q={-50,-20,-50,180,50,180,50,-20};
    private static LightingFrame.Light light(float x,float y,float z){LightingFrame.Light l=new LightingFrame.Light();l.x=x;l.y=y;l.z=z;return l;}
    private static float area(float[] v){float a=0;for(int i=0;i<v.length;i+=24)a+=Math.abs((v[i+11]-v[i+3])*(v[i+20]-v[i+4])-(v[i+19]-v[i+3])*(v[i+12]-v[i+4]))*.5f;return a;}
    @Test public void oneSheetRetainsWholeImageContactAndDecorBounds(){
        TileShadowGeometry g=new TileShadowGeometry(Q,0,0,0,1,1,0,20,60);
        Assert.assertEquals(1,area(g.calibrated),.00001f);Assert.assertTrue(g.vertexCount<=192);
        float[] moved=g.forLight(light(200,-200,30));Assert.assertEquals(1,g.lastWeight,0);boolean contact=false,displaced=false;
        for(int i=0;i<g.calibrated.length;i+=8){
            float[] v=g.calibrated;Assert.assertEquals(ActorShadowImageMapping.x(Q,v[i+3],v[i+4]),LightingCoordinates.screenX(v[i],v[i+1]),.0001f);
            Assert.assertEquals(ActorShadowImageMapping.y(Q,v[i+3],v[i+4]),LightingCoordinates.screenY(v[i],v[i+1],v[i+2]),.0001f);
            if(v[i+2]==0){contact=true;Assert.assertEquals(v[i],moved[i],0);Assert.assertEquals(v[i+1],moved[i+1],0);}
            float d=(float)Math.hypot(moved[i]-v[i],moved[i+1]-v[i+1]);Assert.assertTrue(d<=20.00001f);displaced|=d>19;
        }Assert.assertTrue(contact&&displaced);
        LightingSurface s=new LightingSurface(g.calibrated);s.tileShadow=g;LightingFrame.Caster c=new LightingFrame.Caster();c.surface=s;c.bounds();Assert.assertFalse(s.actor);
        for(int i=0;i<moved.length;i+=8){Assert.assertTrue(moved[i]>=c.minX&&moved[i]<=c.maxX&&moved[i+1]>=c.minY&&moved[i+1]<=c.maxY);}
    }
    @Test public void compensationFadesContinuouslyAndDisabledIsExactlyCalibrated(){
        TileShadowGeometry g=new TileShadowGeometry(Q,0,0,0,1,1,0,20,60);
        Assert.assertSame(g.calibrated,g.forLight(light(200,200,30)));
        for(int i=-200;i<=200;i++){double angle=Math.toRadians(i*.1);float[] p=g.forLight(light((float)(200*Math.cos(angle)),(float)(-200*Math.cos(angle)+400*Math.sin(angle)),30));for(float v:p)Assert.assertTrue(Float.isFinite(v));}
        g.compensationEnabled=false;Assert.assertSame(g.calibrated,g.forLight(light(200,-200,30)));
    }
    @Test public void horizontalSupportIsNotMadeVerticalOrMovedOffItsContactPlane(){
        TileShadowGeometry g=new TileShadowGeometry(Q,0,0,12,0,0,1,7,0);
        Assert.assertEquals(6,g.vertexCount);Assert.assertEquals(1,area(g.calibrated),0);
        float[] v=g.forLight(light(200,200,12));for(int i=2;i<v.length;i+=8)Assert.assertEquals(12,v[i],0);
    }
    @Test public void affineReflectionsAndPlacementKeepOriginalUvMapping(){
        for(int flags=0;flags<8;flags++){
            float[] q=Q.clone();for(int i=0;i<4;i++){float x=q[i*2],y=q[i*2+1]-80;if((flags&1)!=0)x=-x;if((flags&2)!=0)y=-y;if((flags&4)!=0){float t=x;x=y;y=t;}q[i*2]=x+700;q[i*2+1]=y+80+900;}
            TileShadowGeometry g=new TileShadowGeometry(q,1330,630,0,1,1,0,20,60);Assert.assertEquals(1,area(g.calibrated),.00002f);
            for(int i=0;i<g.calibrated.length;i+=8){float[] v=g.calibrated;Assert.assertEquals(ActorShadowImageMapping.x(q,v[i+3],v[i+4]),LightingCoordinates.screenX(v[i],v[i+1]),.001f);}
        }
    }
    @Test public void incompatibleOwnersHaveTheSameRejectionInEitherOrder(){
        TiledMapLayerData map=new TiledMapLayerData();map.projection=TiledProjection.ISO;map.tileWidth=256;map.tileHeight=128;
        SpatialBlockData x=new SpatialBlockData();x.x=x.y=0;x.width=3;x.depth=.2f;x.height=60;x.shadowCaster=true;
        SpatialBlockData y=new SpatialBlockData();y.x=y.y=0;y.width=.2f;y.depth=3;y.height=60;y.shadowCaster=true;
        Array<SpatialBlockData> a=new Array<SpatialBlockData>();a.add(x);a.add(y);TileShadowSupport first=new TileShadowSupport(map,0,0,Q,a,null);a.reverse();TileShadowSupport second=new TileShadowSupport(map,0,0,Q,a,null);
        Assert.assertNull(first.geometry);Assert.assertEquals(first.rejection,second.rejection);
    }
}
