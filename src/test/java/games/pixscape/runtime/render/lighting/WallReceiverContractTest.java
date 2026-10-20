package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WallReceiverContractTest {
    private static final float[] Q={-64,-32,-64,96,64,96,64,-32};
    private static TiledMapLayerData map(){TiledMapLayerData m=new TiledMapLayerData();m.projection=TiledProjection.ISO;m.tileWidth=64;m.tileHeight=32;return m;}
    private static SpatialBlockData wall(int id,boolean x){SpatialBlockData b=new SpatialBlockData();b.id=id;b.width=x?.2f:4;b.depth=x?4:.2f;b.height=32;b.shadowCaster=true;b.addLinkedTileRef(0,0,7);return b;}
    private static LightingSurface surface(TiledMapLayerData m,TileShadowMode mode,SpatialBlockData... owners){SpatialBlocksComponent b=new SpatialBlocksComponent();for(SpatialBlockData v:owners)b.blocks.add(v);MapLightingGeometry g=new MapLightingGeometry();g.mode(mode);g.ensure(m,b);return g.receiver(m,0,0,7,(byte)0,Q);}
    @Test public void aStraightSheetRetainsTheV2FacePartitionAndItsPhysicalPlanes(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(1,true);
        LightingSurface v2=surface(m,TileShadowMode.V2,b),fixed=surface(m,TileShadowMode.V3_FIXED,b);
        assertNotNull(fixed.alphaSheet);assertEquals(v2.vertices.length,fixed.vertices.length);int walls=0,other=0;
        for(int i=0;i<v2.vertices.length;i+=8){assertEquals(v2.vertices[i+3],fixed.vertices[i+3],0);assertEquals(v2.vertices[i+4],fixed.vertices[i+4],0);}
        for(int at=0;at<v2.vertices.length;at+=24){float nx=v2.vertices[at+5],ny=v2.vertices[at+6];float side=Math.abs(nx)>.999f?(nx<0?1:2):Math.abs(ny)>.999f?(ny<0?3:4):0;
            assertEquals(side,fixed.alphaSheet.receiverSide(at),0);if(side!=0){assertEquals(v2.vertices[at+(side<=2?0:1)],fixed.alphaSheet.receiverPlane(at),0);walls++;}else other++;
        }
        assertTrue(walls>0);assertTrue(other>0);
    }
    @Test public void aCornerKeepsEachPhysicalFaceIndependentOfItsInclinedNormal(){
        TiledMapLayerData m=map();SpatialBlockData x=wall(1,true),y=wall(2,false);LightingSurface v2=surface(m,TileShadowMode.V2,x,y),fixed=surface(m,TileShadowMode.V3_FIXED,x,y);boolean foundX=false,foundY=false;
        for(int at=0;at<v2.vertices.length;at+=24){float side=fixed.alphaSheet.receiverSide(at);foundX|=side==1||side==2;foundY|=side==3||side==4;
            if(side!=0)assertEquals(v2.vertices[at+(side<=2?0:1)],fixed.alphaSheet.receiverPlane(at),0);
        }
        assertTrue(foundX);assertTrue(foundY);LightingFrame.Light l=new LightingFrame.Light();float plane=fixed.alphaSheet.receiverPlane(0);
        for(int n=-100;n<=100;n++){l.x=n;l.y=-n;l.z=n*2;assertSame(fixed.alphaSheet.calibrated,fixed.alphaSheet.forLight(l));assertEquals(plane,fixed.alphaSheet.receiverPlane(0),0);}
    }
    @Test public void physicalPlanesDoNotFollowTheMedianOrTheAffineShear(){
        TiledMapLayerData m=map();LightingSurface fixed=surface(m,TileShadowMode.V3_FIXED,wall(1,true));boolean different=false;
        for(int at=0;at<fixed.vertices.length;at+=24)if(fixed.alphaSheet.receiverSide(at)!=0){int axis=fixed.alphaSheet.receiverSide(at)<=2?0:1;different|=Math.abs(fixed.vertices[at+axis]-fixed.alphaSheet.receiverPlane(at))>.1f;}
        assertTrue(different);
    }
    @Test public void mixedCasterParticipationDoesNotAcquireTheOpaqueWallContract(){
        SpatialBlockData x=wall(1,true),y=wall(2,false);y.shadowCaster=false;LightingSurface s=surface(map(),TileShadowMode.V3_PLANE,x,y);
        if(s.alphaSheet!=null)for(int at=0;at<s.vertices.length;at+=24)assertEquals(0,s.alphaSheet.receiverSide(at),0);
    }
}
