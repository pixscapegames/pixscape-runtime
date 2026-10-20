package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class TileAlphaReplacementTest {
    private static TiledMapLayerData map(){TiledMapLayerData m=new TiledMapLayerData();m.projection=TiledProjection.ISO;m.tileWidth=64;m.tileHeight=32;return m;}
    private static SpatialBlockData wall(boolean xNormal){SpatialBlockData b=new SpatialBlockData();b.id=1;b.shadowCaster=true;b.height=32;b.x=xNormal?.9f:0;b.y=xNormal?0:.9f;b.width=xNormal?.4f:4;b.depth=xNormal?4:.4f;return b;}
    private static float[] quad(boolean xNormal,float plane,float lo,float hi,float bottom,float top){
        float[] q=new float[8];float[][] p={{lo,bottom},{lo,top},{hi,top},{hi,bottom}};
        for(int i=0;i<4;i++){float x=xNormal?plane:p[i][0],y=xNormal?p[i][0]:plane;q[i*2]=x-y;q[i*2+1]=(x+y)*.5f+p[i][1];}return q;
    }
    private static MapLightingGeometry geometry(TiledMapLayerData m,SpatialBlockData b,TileShadowMode mode){SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(b);MapLightingGeometry g=new MapLightingGeometry();g.mode(mode);g.ensure(m,blocks);return g;}
    private static double area(Array<LightingSurface> surfaces,int axis,int sign){
        double result=0;for(LightingSurface s:surfaces)for(int i=0;i<s.vertices.length;i+=24){float[] p=s.vertices;if(axis>=0&&p[i+5+axis]!=sign)continue;
            double ax=p[i+8]-p[i],ay=p[i+9]-p[i+1],az=p[i+10]-p[i+2],bx=p[i+16]-p[i],by=p[i+17]-p[i+1],bz=p[i+18]-p[i+2];
            double x=ay*bz-az*by,y=az*bx-ax*bz,z=ax*by-ay*bx;result+=Math.sqrt(x*x+y*y+z*z)*.5;}
        return result;
    }
    @Test public void rearAndFrontCoverageSpanTheWholeThicknessBeyondTheLinkedCell(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(false);b.addLinkedTileRef(0,1,7);MapLightingGeometry g=geometry(m,b,TileShadowMode.V3_PLANE);
        LightingSurface s=g.receiver(m,0,1,7,(byte)0,quad(false,19.2f,16,48,0,32));assertNotNull(s.alphaSheet);g.finishAlphaSheets(m);
        assertEquals(3072,area(g.casters,1,-1),.01);assertEquals(3072,area(g.casters,1,1),.01);
        assertEquals(96*12.8,area(g.casters,2,1),.01);assertEquals(96*12.8,area(g.casters,2,-1),.01);
        assertEquals(0,area(g.casters,0,-1),.01);assertEquals(32*12.8,area(g.casters,0,1),.01);
        assertTrue(g.casters.size<=6);
    }
    @Test public void uvLimitsKeepTheUnrepresentedUpperStripAndLongitudinalRemainder(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(false);b.addLinkedTileRef(0,1,7);MapLightingGeometry g=geometry(m,b,TileShadowMode.V3_FIXED);
        assertNotNull(g.receiver(m,0,1,7,(byte)0,quad(false,19.2f,16,32,0,16)).alphaSheet);g.finishAlphaSheets(m);
        assertEquals(4096-16*16,area(g.casters,1,-1),.01);assertEquals(4096-16*16,area(g.casters,1,1),.01);
        assertEquals(128*12.8,area(g.casters,2,1),.01);assertEquals((128-16)*12.8,area(g.casters,2,-1),.01);
        assertEquals(16*12.8,area(g.casters,0,-1),.01);
    }
    @Test public void aLargeImageCanvasNeverErasesAColumnWithoutAnAssociatedTile(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(false);b.addLinkedTileRef(0,1,7);b.addLinkedTileRef(2,1,7);MapLightingGeometry g=geometry(m,b,TileShadowMode.V3_FIXED);
        float[] large=quad(false,19.2f,-100,300,-100,200);g.receiver(m,0,1,7,(byte)0,large);g.receiver(m,2,1,7,(byte)0,large);g.finishAlphaSheets(m);
        assertEquals(64*32,area(g.casters,1,-1),.01);assertEquals(64*32,area(g.casters,1,1),.01);
        assertEquals(64*12.8,area(g.casters,2,1),.01);assertEquals(32*12.8,area(g.casters,0,1),.01);
    }
    @Test public void duplicateCoverageAcrossTransverseRowsIsSubtractedAsOneUnion(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(false);b.depth=1.2f;b.addLinkedTileRef(0,0,7);b.addLinkedTileRef(0,1,7);
        MapLightingGeometry one=geometry(m,b,TileShadowMode.V3_PLANE),two=geometry(m,b,TileShadowMode.V3_PLANE);float[] q=quad(false,32,16,48,0,32);
        one.receiver(m,0,0,7,(byte)0,q);one.finishAlphaSheets(m);
        two.receiver(m,0,0,7,(byte)0,q);two.receiver(m,0,1,7,(byte)0,q);two.finishAlphaSheets(m);
        assertEquals(area(one.casters,-1,0),area(two.casters,-1,0),.01);assertEquals(3072,area(two.casters,1,-1),.01);
    }
    @Test public void overlappingImagesPreserveTheUncoveredTriangleInsteadOfDuplicatingIt(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(false);b.depth=1.2f;b.addLinkedTileRef(0,0,7);b.addLinkedTileRef(0,1,7);
        MapLightingGeometry g=geometry(m,b,TileShadowMode.V3_PLANE);
        g.receiver(m,0,0,7,(byte)0,quad(false,32,16,40,0,24));g.receiver(m,0,1,7,(byte)0,quad(false,32,24,48,0,32));g.finishAlphaSheets(m);
        assertEquals(4096-960,area(g.casters,1,-1),.02);assertEquals(4096-960,area(g.casters,1,1),.02);
    }
    @Test public void theSameProofWorksForTheOtherWallOrientation(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(true);b.addLinkedTileRef(1,0,7);MapLightingGeometry g=geometry(m,b,TileShadowMode.V3_FIXED);
        assertNotNull(g.receiver(m,1,0,7,(byte)0,quad(true,51.2f,-16,16,0,32)).alphaSheet);g.finishAlphaSheets(m);
        assertEquals(3072,area(g.casters,0,-1),.01);assertEquals(3072,area(g.casters,0,1),.01);assertEquals(96*12.8,area(g.casters,2,1),.01);
    }
    @Test public void flipsAndInclinationModesDoNotChangeColdOpaqueCoverage(){
        TiledMapLayerData m=map();SpatialBlockData b=wall(false);b.addLinkedTileRef(0,1,7);double expected=-1;
        for(TileShadowMode mode:new TileShadowMode[]{TileShadowMode.V3_PLANE,TileShadowMode.V3_FIXED,TileShadowMode.V3_PROGRESSIVE})for(int flip=0;flip<2;flip++){
            float[] q=quad(false,19.2f,16,40,0,24);if(flip!=0){float[] other=q.clone();for(int i=0;i<4;i++){q[i*2]=other[(3-i)*2];q[i*2+1]=other[(3-i)*2+1];}}
            MapLightingGeometry g=geometry(m,b,mode);g.receiver(m,0,1,7,(byte)flip,q);g.finishAlphaSheets(m);double actual=area(g.casters,-1,0);
            if(expected<0)expected=actual;else assertEquals(expected,actual,.01);
            for(LightingSurface face:g.casters)for(int at=0;at<face.vertices.length;at+=8){assertEquals(0,face.vertices[at+3],0);assertEquals(0,face.vertices[at+4],0);}
        }
    }
    @Test public void mixedLegacyAndFullMasksInOneColumnPreserveOnlyTheirActualUnion(){
        float y=25.6f;float[] f={16,y,0,0,0,0,1,0,16,y,32,0,0,0,1,0,48,y,32,0,0,0,1,0,16,y,0,0,0,0,1,0,48,y,32,0,0,0,1,0,48,y,0,0,0,0,1,0};
        LightingSurface face=new LightingSurface(f);face.sourceBlockId=1;float[] dummy=new float[24];dummy[6]=dummy[14]=dummy[22]=1;
        LightingSurface carrier=new LightingSurface(dummy);carrier.alphaSheet=new TileAlphaSheet(dummy,new TileAlphaSheet.Part[]{new TileAlphaSheet.Part(0,24,32,19.2f,0,0,1,0,32,6.4f)},TileShadowMode.V3_PLANE);
        TileAlphaCompiler.Cell full=new TileAlphaCompiler.Cell(0,0,quad(false,19.2f,16,48,0,16),null,carrier,true,null,0,1,0);
        float[] t={16,y,16,0,0,0,1,0,16,y,32,0,1,0,1,0,48,y,32,1,1,0,1,0};LightingSurface partial=new LightingSurface(t);
        partial.alphaSheet=new TileAlphaSheet(t,new TileAlphaSheet.Part[]{new TileAlphaSheet.Part(0,24,16,y,16,0,1,0,0,0)},TileShadowMode.V3_PLANE);
        TileAlphaCompiler.Cell legacy=new TileAlphaCompiler.Cell(0,1,quad(false,y,16,48,0,32),null,partial,false,"mixed",0,1,0);
        Array<TileAlphaCompiler.Cell> cells=new Array<TileAlphaCompiler.Cell>();cells.add(full);cells.add(legacy);
        for(int order=0;order<2;order++){Array<LightingSurface> out=new Array<LightingSurface>();TileAlphaCompiler.replace(map(),face,cells,out);assertEquals(256,area(out,-1,0),.01);cells.reverse();}
    }
}
