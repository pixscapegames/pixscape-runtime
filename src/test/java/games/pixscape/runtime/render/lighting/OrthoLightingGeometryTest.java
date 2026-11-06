package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class OrthoLightingGeometryTest {
    @Test public void verticalWallCellsShareOneRoofPlaneAndInvalidateAfterHeightEdit() {
        TiledMapLayerData map=new TiledMapLayerData(8,8,32,16,8,TiledProjection.ORTHO);
        SpatialBlockData wall=new SpatialBlockData();wall.id=wall.structureId=1;
        wall.x=2;wall.y=1;wall.width=.25f;wall.depth=5;wall.altitude=7;wall.height=9;
        wall.beginAuthoredLinkedTileRefs();for(int y=1;y<=6;y++)wall.addLinkedTileRef(2,y,1);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(wall);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        for(int y=2;y<=5;y++) {
            LightingSurface surface=geometry.receiver(map,2,y,1,(byte)0,new float[]{64,y*16,64,(y+1)*16,96,(y+1)*16,96,y*16});
            int roof=0;
            for(int i=0;i<surface.vertices.length;i+=8) {
                float[] v=surface.vertices;
                if(v[i+7]==0) {assertEquals(0,v[i+5],0);assertEquals(0,v[i+6],0);continue;} // Undescribed image region, no invented side.
                roof++;assertEquals(16,v[i+2],0);assertEquals(1,v[i+7],0);
                assertEquals(64+v[i+3]*32,LightingCoordinates.screenX(v[i],v[i+1]),.0001f);
                assertEquals(y*16+v[i+4]*16,LightingCoordinates.screenY(v[i],v[i+1],v[i+2]),.0001f);
            }
            assertTrue(roof>0);
        }
        LightingSurface before=geometry.receiver(map,2,3,1,(byte)0,new float[]{64,48,64,64,96,64,96,48});
        wall.height=12;blocks.revision++;assertTrue(geometry.ensure(map,blocks));
        LightingSurface after=geometry.receiver(map,2,3,1,(byte)0,new float[]{64,48,64,64,96,64,96,48});
        assertNotSame(before,after);
        for(int i=0;i<after.vertices.length;i+=8)if(after.vertices[i+7]>0)assertEquals(19,after.vertices[i+2],0);
    }
    @Test public void rectangularMetricsKeepFacadeRoofNormalsAndAbsolutePlaneRegistration() {
        for(int[] size:new int[][]{{16,16},{32,16},{16,32}}) {
            TiledMapLayerData map=new TiledMapLayerData(8,8,size[0],size[1],8,TiledProjection.ORTHO);
            map.originX=13;map.originY=-7;map.defaultTileAltitude=11;
            SpatialBlockData wall=new SpatialBlockData();wall.id=1;wall.structureId=1;
            wall.x=1;wall.y=2;wall.width=3;wall.depth=.25f;wall.altitude=19;wall.height=8;
            wall.beginAuthoredLinkedTileRefs();wall.addLinkedTileRef(1,2,1);
            SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(wall);
            MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
            float left=map.originX+size[0],right=left+3*size[0];
            float bottom=map.originY+2*size[1]+8,top=bottom+8+.25f*size[1];
            float[] quad={left,bottom,left,top,right,top,right,bottom};
            LightingSurface surface=geometry.receiver(map,1,2,1,(byte)0,quad);
            int facade=0,roof=0;
            for(int i=0;i<surface.vertices.length;i+=8) {
                float[] v=surface.vertices;
                assertEquals(left+v[i+3]*(right-left),LightingCoordinates.screenX(v[i],v[i+1]),.0001f);
                assertEquals(bottom+v[i+4]*(top-bottom),LightingCoordinates.screenY(v[i],v[i+1],v[i+2]),.0001f);
                if(v[i+7]>.5f){roof++;assertEquals(27,v[i+2],.0001f);assertEquals(0,v[i+5],.0001f);assertEquals(0,v[i+6],.0001f);}
                else {facade++;assertEquals(-Math.sqrt(.5),v[i+5],.0001);assertEquals(-Math.sqrt(.5),v[i+6],.0001);assertEquals(0,v[i+7],0);}
            }
            assertTrue(facade>0);assertTrue(roof>0);
            assertEquals(0,geometry.casters.size);assertFalse(surface.shadowCaster);
        }
    }

    @Test public void linkedImageBeyondWallHasNoCasterContribution() {
        TiledMapLayerData map=new TiledMapLayerData(8,8,16,16,8,TiledProjection.ORTHO);
        SpatialBlockData wall=new SpatialBlockData();wall.id=1;wall.structureId=1;wall.height=8;
        wall.shadowCaster=true;wall.lightOccluder=true;wall.beginAuthoredLinkedTileRefs();wall.addLinkedTileRef(0,0,1);
        // The cell is linked but its image does not intersect any physical face.
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(wall);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        LightingSurface surface=geometry.receiver(map,0,0,1,(byte)0,new float[]{0,100,0,116,16,116,16,100});
        assertNotNull(surface.forwardTopology);assertNull(surface.casterContributions);
        assertEquals(0,geometry.casters.size);assertTrue(wall.shadowCaster);assertTrue(wall.lightOccluder);
    }

    @Test public void planeReceiverRetainsItsAbsoluteAltitude() {
        TiledMapLayerData map=new TiledMapLayerData(8,8,32,16,8,TiledProjection.ORTHO);map.defaultTileAltitude=17;
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,null);
        LightingSurface surface=geometry.receiver(map,0,0,1,(byte)0,new float[]{0,0,0,16,32,16,32,0});
        for(int i=0;i<surface.vertices.length;i+=8){assertEquals(17,surface.vertices[i+2],0);assertEquals(1,surface.vertices[i+7],0);}
    }

    @Test public void edgeOnSidesSurviveImageClippingForHardShadows() {
        TiledMapLayerData map=new TiledMapLayerData(8,8,32,16,8,TiledProjection.ORTHO);
        SpatialBlockData wall=new SpatialBlockData();
        wall.id=wall.structureId=1;wall.x=2;wall.y=1;wall.width=.25f;wall.depth=5;
        wall.altitude=7;wall.height=9;wall.shadowCaster=true;
        wall.beginAuthoredLinkedTileRefs();wall.addLinkedTileRef(2,3,1);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();blocks.blocks.add(wall);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks);
        LightingSurface tile=geometry.receiver(map,2,3,1,(byte)0,new float[]{64,48,64,64,96,64,96,48});
        float[] v=tile.casterContributions.first().vertices;
        double leftArea=0,rightArea=0;
        for(int i=0;i<v.length;i+=24){
            if(Math.abs(v[i+7])>.001)continue;
            float sx=LightingCoordinates.screenX(v[i],v[i+1]);
            for(int j=i;j<i+24;j+=8){
                assertEquals(sx,LightingCoordinates.screenX(v[j],v[j+1]),.0001);
                assertEquals((sx-64)/32,v[j+3],.0001);
                assertTrue(v[j+4]>=-.0001 && v[j+4]<=1.0001);
                assertTrue(v[j+2]>=7 && v[j+2]<=16);
            }
            double along1=(v[i+8]+v[i+9]-v[i]-v[i+1])*.5;
            double along2=(v[i+16]+v[i+17]-v[i]-v[i+1])*.5;
            double area=Math.abs(along1*(v[i+18]-v[i+2])-along2*(v[i+10]-v[i+2]))*.5;
            if(Math.abs(sx-64)<.0001)leftArea+=area;
            else {assertEquals(72,sx,.0001);rightArea+=area;}
        }
        assertEquals(16*9,leftArea,.001);assertEquals(leftArea,rightArea,.001);
    }
}
