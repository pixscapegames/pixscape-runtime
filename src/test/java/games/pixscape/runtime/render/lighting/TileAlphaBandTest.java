package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class TileAlphaBandTest {
    private static final float[] Q={-512,-256,-512,512,512,512,512,-256};
    private static TiledMapLayerData map(){TiledMapLayerData m=new TiledMapLayerData();m.projection=TiledProjection.ISO;m.tileWidth=64;m.tileHeight=32;return m;}
    private static SpatialBlockData wall(int id,float x,float y,float width,float depth,float z,float height){
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.x=x;b.y=y;b.width=width;b.depth=depth;b.altitude=z;b.height=height;b.shadowCaster=true;
        for(int gy=(int)Math.floor(y);gy<Math.ceil(y+depth);gy++)for(int gx=(int)Math.floor(x);gx<Math.ceil(x+width);gx++)b.addLinkedTileRef(gx,gy,7);
        return b;
    }
    private static SpatialBlocksComponent blocks(SpatialBlockData... values){SpatialBlocksComponent b=new SpatialBlocksComponent();for(SpatialBlockData v:values)b.blocks.add(v);return b;}
    private static TileAlphaContactIndex index(TiledMapLayerData a,SpatialBlocksComponent low,TiledMapLayerData b,SpatialBlocksComponent high){
        TileAlphaContactIndex i=new TileAlphaContactIndex();i.add(7,a,low);i.add(8,b,high);i.compile();return i;
    }
    private static MapLightingGeometry geometry(TiledMapLayerData map,SpatialBlocksComponent blocks,TileAlphaContactIndex.MapContacts contacts,TileShadowMode mode){
        MapLightingGeometry g=new MapLightingGeometry();g.contacts(contacts);g.mode(mode);assertTrue(g.ensure(map,blocks));return g;
    }
    @Test public void calibrationUsesTheWholeVolumeWhileReplacementPortionsStayCellLocal(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(1,0,.9f,4,.4f,0,32);Array<SpatialBlockData> owners=new Array<SpatialBlockData>();owners.add(b);
        TileShadowSupport a=new TileShadowSupport(m,0,0,Q,owners,null,false),c=new TileShadowSupport(m,0,1,Q,owners,null,false);
        assertEquals(a.contactY,c.contactY,0);assertEquals(a.thickness,c.thickness,0);assertEquals(19.2,a.contactY,.00001);assertEquals(12.8,a.thickness,.00001);
        assertTrue(a.portions.first()[4]<c.portions.first()[4]);assertTrue(a.portions.first()[1]<c.portions.first()[1]);
        SpatialBlocksComponent data=blocks(b);MapLightingGeometry g=geometry(m,data,null,TileShadowMode.V3_PLANE);
        LightingSurface lower=g.receiver(m,0,0,7,(byte)0,Q),upper=g.receiver(m,0,1,7,(byte)0,Q);
        for(int i=0;i<lower.vertices.length;i+=8){assertEquals(19.2,lower.vertices[i+1],.00005);assertEquals(lower.vertices[i+1],upper.vertices[i+1],0);}
        assertTrue(g.finishAlphaSheets(m));assertEquals(2,g.coverage().tiles);
    }
    @Test public void exactStackAcrossMapsSharesOneReferenceWithLocalIdsAndFixedGeometry(){
        TiledMapLayerData a=map(),b=map();SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32)),high=blocks(wall(1,0,0,4,.2f,32,40));
        TileAlphaContactIndex i=index(a,low,b,high);TileAlphaContactIndex.Band band=i.contacts(7).band(1);
        assertNotNull(band);assertSame(band,i.contacts(8).band(1));assertEquals(72,band.height,0);assertEquals(3.2,band.halfThickness,.00001);assertNull(i.contacts(7).rejection(1));assertNull(i.contacts(7).band(999));assertNull(i.contacts(7).rejection(999));
        MapLightingGeometry gl=geometry(a,low,i.contacts(7),TileShadowMode.V3_FIXED),gh=geometry(b,high,i.contacts(8),TileShadowMode.V3_FIXED);
        LightingSurface l=gl.receiver(a,0,0,7,(byte)0,Q),h=gh.receiver(b,0,0,7,(byte)0,Q);assertNotNull(h.alphaSheet);
        TileAlphaSheet.Part p=l.alphaSheet.parts[0],q=h.alphaSheet.parts[0];assertEquals(p.x,q.x,0);assertEquals(p.y,q.y,0);assertEquals(p.z,q.z,0);assertEquals(p.height,q.height,0);
        float k=band.halfThickness/band.height;
        for(LightingSurface s:new LightingSurface[]{l,h})for(int at=0;at<s.vertices.length;at+=8)assertEquals(band.y+k*(s.vertices[at+2]-band.z),s.vertices[at+1],.0001);
        LightingFrame.Light light=new LightingFrame.Light();float[] saved=h.vertices.clone();
        for(int n=0;n<=720;n++){light.x=(float)Math.cos(n*.02)*500;light.y=(float)Math.sin(n*.02)*500;light.z=n%3*100;assertSame(h.vertices,h.alphaSheet.forLight(light));assertArrayEquals(saved,h.vertices,0);}
    }
    @Test public void progressiveUsesTheSameLightReferenceForEveryStorey(){
        TiledMapLayerData a=map(),b=map();SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32)),high=blocks(wall(2,0,0,4,.2f,32,40));TileAlphaContactIndex i=index(a,low,b,high);
        LightingSurface l=geometry(a,low,i.contacts(7),TileShadowMode.V3_PROGRESSIVE).receiver(a,0,0,7,(byte)0,Q),h=geometry(b,high,i.contacts(8),TileShadowMode.V3_PROGRESSIVE).receiver(b,0,0,7,(byte)0,Q);
        LightingFrame.Light light=new LightingFrame.Light();
        for(int n=0;n<=720;n++){
            light.x=(float)Math.cos(n*.02)*500;light.y=(float)Math.sin(n*.02)*500;light.z=n%3*100;
            float[] x=l.alphaSheet.forLight(light),y=h.alphaSheet.forLight(light);
            for(int k=5;k<8;k++)assertEquals(x[k],y[k],0);
            // The physical-face UV partitions differ by storey; the common affine plane must not.
            for(float[] data:new float[][]{x,y})for(int at=0;at<data.length;at+=8){
                float k=x[6]==0?0:-x[7]/x[6];TileAlphaSheet.Part p=l.alphaSheet.parts[0];
                assertEquals(p.y+k*(data[at+2]-p.z),data[at+1],.0001);
            }
            for(float[] uv:new float[][]{{0,0},{.2f,.5f},{.8f,.75f},{1,1}})assertArrayEquals(point(x,uv[0],uv[1]),point(y,uv[0],uv[1]),.0001f);
        }
    }
    private static float[] point(float[] data,float u,float v){
        for(int at=0;at<data.length;at+=24){
            float ax=data[at+11]-data[at+3],ay=data[at+12]-data[at+4],bx=data[at+19]-data[at+3],by=data[at+20]-data[at+4],det=ax*by-ay*bx;
            if(Math.abs(det)<1e-9)continue;float a=((u-data[at+3])*by-(v-data[at+4])*bx)/det,b=(ax*(v-data[at+4])-ay*(u-data[at+3]))/det;
            if(a<-.00001||b<-.00001||a+b>1.00001)continue;
            float[] p=new float[3];for(int k=0;k<3;k++)p[k]=data[at+k]+a*(data[at+8+k]-data[at+k])+b*(data[at+16+k]-data[at+k]);return p;
        }
        throw new AssertionError("Image UV left the published receiver partition");
    }
    @Test public void authoredGapsEvenBelowOldToleranceAndShiftedSectionsNeverBecomeBands(){
        TiledMapLayerData m=map();SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32));
        for(float gap:new float[]{.0005f,.5f,-.5f}){
            SpatialBlocksComponent high=blocks(wall(2,0,0,4,.2f,32+gap,32));TileAlphaContactIndex i=index(m,low,map(),high);
            assertNull(i.contacts(7).band(1));assertNotNull(i.contacts(7).rejection(1,0,0));assertNotNull(i.contacts(8).rejection(2,0,0));assertEquals(32+gap,high.blocks.first().altitude,0);
        }
        SpatialBlocksComponent shifted=blocks(wall(2,0,.01f,4,.2f,32,32));TileAlphaContactIndex i=index(m,low,map(),shifted);
        assertNull(i.contacts(7).band(1));assertNotNull(i.contacts(8).rejection(2,0,0));
    }
    @Test public void anOrthogonalContactRefusesOnlyItsCellsAndKeepsTheStraightBand(){
        TiledMapLayerData m=map();SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32),wall(3,0,0,.2f,4,0,32)),high=blocks(wall(2,0,0,4,.2f,32,32));
        TileAlphaContactIndex i=index(m,low,map(),high);assertNotNull(i.contacts(7).band(1));assertSame(i.contacts(7).band(1),i.contacts(8).band(2));
        assertNull(i.contacts(7).rejection(1));assertNotNull(i.contacts(8).rejection(2));
        assertNotNull(i.contacts(7).rejection(3,0,0));assertNotNull(i.contacts(8).rejection(2,0,0));assertNull(i.contacts(8).rejection(2,1,0));assertNull(i.contacts(8).rejection(999,0,0));
        MapLightingGeometry g=geometry(m,high,i.contacts(8),TileShadowMode.V3_FIXED);
        assertNull(g.receiver(m,0,0,7,(byte)0,Q).alphaSheet);assertNotNull(g.receiver(m,1,0,7,(byte)0,Q).alphaSheet);
    }
    @Test public void exactEndContactsJoinButProximityAndInteriorOverlapDoNot(){
        TiledMapLayerData m=map();SpatialBlocksComponent low=blocks(wall(1,0,0,7.5f,.2f,0,32)); // End lies on a world bucket boundary.
        SpatialBlocksComponent high=blocks(wall(2,7.5f,0,2,.2f,0,40));TileAlphaContactIndex i=index(m,low,map(),high);
        assertSame(i.contacts(7).band(1),i.contacts(8).band(2));assertNotNull(i.contacts(7).band(1));
        high.blocks.first().x+=.001f;high.revision++;i=index(m,low,map(),high);assertNull(i.contacts(7).band(1));assertNull(i.contacts(8).band(2));
        high.blocks.first().x=1;i=index(m,low,map(),high);assertNull(i.contacts(7).band(1));
    }
    @Test public void localOrthogonalGuardsUseEachMapsWorldCellsAndIgnoreEnumerationOrder(){
        TiledMapLayerData a=map(),b=map();b.originX=64;b.originY=32;
        SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32),wall(3,1,0,.2f,4,0,32)),high=blocks(wall(2,-2,0,4,.2f,32,32));
        TileAlphaContactIndex i=index(a,low,b,high),j=new TileAlphaContactIndex();low.blocks.reverse();j.add(8,b,high);j.add(7,a,low);j.compile();
        for(int gx=-3;gx<6;gx++)for(int gy=-1;gy<5;gy++)for(int id:new int[]{1,2,3,999}){
            assertEquals(i.contacts(7).rejection(id,gx,gy),j.contacts(7).rejection(id,gx,gy));assertEquals(i.contacts(8).rejection(id,gx,gy),j.contacts(8).rejection(id,gx,gy));
        }
        assertNotNull(i.contacts(8).rejection(2,-1,0));assertNull(i.contacts(8).rejection(2,0,0));
    }
    @Test public void movingTheOrthogonalContactRebuildsAndClearsItsNegativeCellDecisions(){
        TiledMapLayerData m=map();SpatialBlockData crossing=wall(3,0,0,.2f,4,0,32);SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32),crossing),high=blocks(wall(2,0,0,4,.2f,32,32));
        TileAlphaContactIndex i=index(m,low,map(),high);assertNotNull(i.contacts(8).rejection(2,0,0));assertNotNull(i.contacts(8).band(2));
        crossing.x=10;low.revision++;i.begin();i.check(7,m,low);assertTrue(i.needsRebuild());i.clear();i.add(7,m,low);i.add(8,map(),high);i.compile();
        assertNull(i.contacts(8).rejection(2,0,0));assertNotNull(i.contacts(8).band(2));assertNull(i.contacts(8).rejection(2,99,99));
    }
    @Test public void topologyRebuildsOnSourceChangesAndPublishesNewMapContactsOnlyAtColdBoundary(){
        TiledMapLayerData a=map(),b=map();SpatialBlocksComponent low=blocks(wall(1,0,0,4,.2f,0,32)),high=blocks(wall(2,0,0,4,.2f,32,40));TileAlphaContactIndex i=index(a,low,b,high);
        MapLightingGeometry g=geometry(a,low,i.contacts(7),TileShadowMode.V3_FIXED);LightingSurface first=g.receiver(a,0,0,7,(byte)0,Q);
        for(int n=0;n<100;n++){i.begin();i.check(7,a,low);i.check(8,b,high);assertFalse(i.needsRebuild());assertFalse(g.ensure(a,low));assertSame(first,g.receiver(a,0,0,7,(byte)0,Q));}
        high.blocks.first().altitude+=1;high.revision++;i.begin();i.check(7,a,low);i.check(8,b,high);assertTrue(i.needsRebuild());
        i.clear();i.add(7,a,low);i.add(8,b,high);i.compile();g.contacts(i.contacts(7));assertTrue(g.ensure(a,low));assertNull(g.receiver(a,0,0,7,(byte)0,Q).alphaSheet);
        b.originX++;i.begin();i.check(7,a,low);i.check(8,b,high);assertTrue(i.needsRebuild());
        i.begin();i.check(7,a,low);assertTrue(i.needsRebuild());
    }
    @Test public void bandCoefficientsAreIndependentOfVolumeAndMapInsertionOrder(){
        TiledMapLayerData a=map(),b=map();SpatialBlockData lower=wall(1,0,0,4,.2f,0,32),left=wall(1,0,0,2,.2f,32,40),right=wall(2,2,0,2,.2f,32,38);
        SpatialBlocksComponent low=blocks(lower),high=blocks(left,right);TileAlphaContactIndex i=index(a,low,b,high);
        TileAlphaContactIndex.Band first=i.contacts(7).band(1);assertNotNull(first);assertSame(first,i.contacts(8).band(2));
        high.blocks.reverse();TileAlphaContactIndex j=new TileAlphaContactIndex();j.add(8,b,high);j.add(7,a,low);j.compile();TileAlphaContactIndex.Band second=j.contacts(7).band(1);
        assertEquals(first.x,second.x,0);assertEquals(first.y,second.y,0);assertEquals(first.z,second.z,0);assertEquals(first.height,second.height,0);assertEquals(first.halfThickness,second.halfThickness,0);
    }
    @Test public void duplicateLocalVolumeIdsAreExplicitAndDifferentMapIdsRemainIndependent(){
        try{index(map(),blocks(wall(1,0,0,4,.2f,0,32),wall(1,5,0,4,.2f,0,32)),map(),blocks());fail("Duplicate local identity");}catch(IllegalArgumentException expected){}
        TileAlphaContactIndex i=index(map(),blocks(wall(1,0,0,4,.2f,0,32)),map(),blocks(wall(1,0,0,4,.2f,32,32)));assertNotNull(i.contacts(7).band(1));assertSame(i.contacts(7).band(1),i.contacts(8).band(1));
    }
}
