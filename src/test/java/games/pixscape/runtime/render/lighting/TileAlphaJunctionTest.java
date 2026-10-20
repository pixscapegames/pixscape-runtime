package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class TileAlphaJunctionTest {
    private static final float[] Q={-64,-32,-64,96,64,96,64,-32};
    private static TiledMapLayerData map(){TiledMapLayerData m=new TiledMapLayerData();m.projection=TiledProjection.ISO;m.tileWidth=64;m.tileHeight=32;return m;}
    private static SpatialBlockData wall(int id,boolean x){SpatialBlockData b=new SpatialBlockData();b.id=id;b.shadowCaster=true;b.width=x?.2f:4;b.depth=x?4:.2f;b.height=32;b.addLinkedTileRef(0,0,7);return b;}
    private static SpatialBlocksComponent blocks(SpatialBlockData... values){SpatialBlocksComponent b=new SpatialBlocksComponent();for(SpatialBlockData v:values)b.blocks.add(v);return b;}
    private static MapLightingGeometry geometry(TiledMapLayerData m,SpatialBlocksComponent b,TileShadowMode mode){MapLightingGeometry g=new MapLightingGeometry();g.mode(mode);g.ensure(m,b);return g;}
    @Test public void fixedMiterPreservesEveryNativeV2UvAndTriangleIncludingUndescribedProvenance(){
        TiledMapLayerData m=map();SpatialBlocksComponent b=blocks(wall(1,true),wall(2,false));LightingSurface v2=geometry(m,b,TileShadowMode.V2).receiver(m,0,0,7,(byte)0,Q),fixed=geometry(m,b,TileShadowMode.V3_FIXED).receiver(m,0,0,7,(byte)0,Q);
        assertNotNull(fixed.alphaSheet);assertEquals(v2.vertices.length,fixed.vertices.length);assertEquals(v2.vertices.length,fixed.alphaSheet.calibrated.length);int unknown=0,known=0;
        for(int i=0;i<v2.vertices.length;i+=8){assertEquals(v2.vertices[i+3],fixed.vertices[i+3],0);assertEquals(v2.vertices[i+4],fixed.vertices[i+4],0);assertEquals(v2.vertices[i+3],fixed.alphaSheet.calibrated[i+3],0);assertEquals(v2.vertices[i+4],fixed.alphaSheet.calibrated[i+4],0);}
        for(int i=0;i<v2.vertices.length;i+=24){boolean valid=v2.vertices[i+5]!=0||v2.vertices[i+6]!=0||v2.vertices[i+7]!=0;assertEquals(valid,fixed.alphaSheet.admits(i));if(valid)known++;else unknown++;}
        assertTrue(known>0);assertTrue(unknown>0);
    }
    @Test public void genuineV2SharedVerticesRemainCoincidentAfterInclination(){
        TiledMapLayerData m=map();SpatialBlocksComponent b=blocks(wall(1,true),wall(2,false));LightingSurface v2=geometry(m,b,TileShadowMode.V2).receiver(m,0,0,7,(byte)0,Q),fixed=geometry(m,b,TileShadowMode.V3_FIXED).receiver(m,0,0,7,(byte)0,Q);int pairs=0;
        float[] original=v2.receiverVertices==null?v2.vertices:v2.receiverVertices,actual=fixed.alphaSheet.calibrated;
        for(int a=0;a<original.length;a+=8)for(int c=a+8;c<original.length;c+=8){
            if(Math.abs(original[a+3]-original[c+3])+Math.abs(original[a+4]-original[c+4])>.00001||Math.abs(original[a]-original[c])+Math.abs(original[a+1]-original[c+1])+Math.abs(original[a+2]-original[c+2])>.001)continue;
            for(int axis=0;axis<3;axis++)assertEquals(actual[a+axis],actual[c+axis],.0001);pairs++;
        }
        assertTrue(pairs>0);LightingFrame.Light light=new LightingFrame.Light();float[] frozen=actual.clone();for(int f=0;f<730;f++){light.x=f;light.y=-f;light.z=f%300;assertSame(actual,fixed.alphaSheet.forLight(light));assertArrayEquals(frozen,actual,0);}
    }
    @Test public void contactAndAbsoluteAltitudeAreIdenticalBetweenPlaneAndFixed(){
        TiledMapLayerData m=map();SpatialBlocksComponent b=blocks(wall(1,true),wall(2,false));LightingSurface plane=geometry(m,b,TileShadowMode.V3_PLANE).receiver(m,0,0,7,(byte)0,Q),fixed=geometry(m,b,TileShadowMode.V3_FIXED).receiver(m,0,0,7,(byte)0,Q);
        for(int i=0;i<plane.vertices.length;i+=8){assertEquals(plane.vertices[i+2],fixed.vertices[i+2],0);if(plane.vertices[i+2]==0){assertEquals(plane.vertices[i],fixed.vertices[i],0);assertEquals(plane.vertices[i+1],fixed.vertices[i+1],0);}}
    }
    @Test public void volumeAndMapEnumerationCannotSelectTheUvPartitionOrCornerGeometry(){
        TiledMapLayerData m=map();SpatialBlocksComponent b=blocks(wall(1,true),wall(2,false));LightingSurface first=geometry(m,b,TileShadowMode.V3_FIXED).receiver(m,0,0,7,(byte)0,Q);b.blocks.reverse();LightingSurface second=geometry(m,b,TileShadowMode.V3_FIXED).receiver(m,0,0,7,(byte)0,Q);assertArrayEquals(first.vertices,second.vertices,0);
        TiledMapLayerData shifted=map();shifted.originX=100;shifted.originY=200;float[] q=Q.clone();for(int i=0;i<q.length;i+=2){q[i]+=100;q[i+1]+=200;}
        MapLightingGeometry other=geometry(shifted,b,TileShadowMode.V3_FIXED);LightingSurface s=other.receiver(shifted,0,0,7,(byte)0,q);assertNotSame(first,s);assertNotEquals(first.vertices[0],s.vertices[0],0);
    }
    @Test public void replacementRetainsUnrepresentedEndsAndUsesTheSameFootprintForBothSides(){
        TiledMapLayerData m=map();SpatialBlocksComponent b=blocks(wall(1,true),wall(2,false));MapLightingGeometry g=geometry(m,b,TileShadowMode.V3_FIXED);g.receiver(m,0,0,7,(byte)0,Q);g.finishAlphaSheets(m);assertTrue(g.casters.size>0);assertTrue(g.casters.size<=12);
        boolean farEnd=false;for(LightingSurface s:g.casters)for(int i=0;i<s.vertices.length;i+=8){if(s.vertices[i]>100||s.vertices[i+1]>100)farEnd=true;assertEquals(0,s.vertices[i+3],0);assertEquals(0,s.vertices[i+4],0);}assertTrue(farEnd);
    }
    @Test public void progressiveAndDifferentAltitudesKeepTheirExistingFallback(){
        SpatialBlockData x=wall(1,true),y=wall(2,false);TiledMapLayerData m=map();SpatialBlocksComponent b=blocks(x,y);LightingSurface moving=geometry(m,b,TileShadowMode.V3_PROGRESSIVE).receiver(m,0,0,7,(byte)0,Q);if(moving.alphaSheet!=null)for(TileAlphaSheet.Part p:moving.alphaSheet.parts)assertEquals(0,p.height,0);
        y.altitude=1;LightingSurface rejected=geometry(m,b,TileShadowMode.V3_FIXED).receiver(m,0,0,7,(byte)0,Q);if(rejected.alphaSheet!=null)for(TileAlphaSheet.Part p:rejected.alphaSheet.parts)assertEquals(0,p.height,0);
    }
    @Test public void replacementKeepsEveryProjectedV2TriangleWhenItsSharedUvEdgesDifferByAnUlp(){
        TiledMapLayerData m=map();m.tileWidth=256;m.tileHeight=128;float h=157.34424f,y=2014.0793f;
        float[] face={2368,y,0,0,0,0,1,0,2368,y,h,0,0,0,1,0,2624,y,h,0,0,0,1,0,2368,y,0,0,0,0,1,0,2624,y,h,0,0,0,1,0,2624,y,0,0,0,0,1,0};
        float[] q={256,2176,256,2688,512,2688,512,2176};
        float[] raw={2368,1984,0,.5f,0,0,-1,0,2398.0793f,1984,0,.61749744f,.029374123f,0,-1,0,2398.0793f,1984,h,.61749744f,.3366871f,0,-1,0,
                2368,1984,0,.5f,0,0,-1,0,2398.0793f,1984,h,.61749744f,.3366871f,0,-1,0,2368,1984,h,.5f,.30731297f,0,-1,0,
                2398.0793f,1984,0,.61749744f,.029374367f,0,-1,0,2496,1984,0,1,.125f,0,-1,0,2496,1984,h,1,.43231297f,0,-1,0,
                2398.0793f,1984,0,.61749744f,.029374367f,0,-1,0,2496,1984,h,1,.43231297f,0,-1,0,2398.0793f,1984,h,.61749744f,.33668733f,0,-1,0};
        LightingSurface carrier=new LightingSurface(raw);carrier.alphaSheet=new TileAlphaSheet(raw,new TileAlphaSheet.Part[]{new TileAlphaSheet.Part(0,raw.length,2368,1984,0,0,-1,0,0,0)},TileShadowMode.V3_PLANE);
        SpatialBlockData owner=wall(21,false);owner.x=18;owner.y=16;owner.width=2;owner.depth=.235f;
        com.badlogic.gdx.utils.Array<SpatialBlockData> owners=new com.badlogic.gdx.utils.Array<SpatialBlockData>();owners.add(owner);
        TileAlphaCompiler.Cell cell=new TileAlphaCompiler.Cell(18,16,q,owners,carrier,false,"V2 mask",0,4,0,raw);LightingSurface source=new LightingSurface(face);source.sourceBlockId=21;
        com.badlogic.gdx.utils.Array<TileAlphaCompiler.Cell> cells=new com.badlogic.gdx.utils.Array<TileAlphaCompiler.Cell>();cells.add(cell);com.badlogic.gdx.utils.Array<LightingSurface> out=new com.badlogic.gdx.utils.Array<LightingSurface>();TileAlphaCompiler.replace(m,source,cells,out);
        double area=0;for(LightingSurface s:out)for(int i=0;i<s.vertices.length;i+=24){float[] v=s.vertices;area+=Math.abs((v[i+8]-v[i])*(v[i+18]-v[i+2])-(v[i+16]-v[i])*(v[i+10]-v[i+2]))*.5;}
        assertEquals(128*h,area,.1);
    }
}
