package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;
import org.junit.*;

public class LightingGeometryTest {
    @Test public void mapPlaneIsExplicitAndImageProjectionIsUnchanged(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        map.originX=17;map.originY=-36;map.defaultTileAltitude=128;
        float[] xyz=new float[3],image=new float[2];
        for(float plane:new float[]{0,64,128})for(float altitude:new float[]{-20,128,300}){
            LightingCoordinates.mapPoint(map,plane,5.25f,8.5f,altitude,xyz,0);
            map.projectSpatialPoint(5.25f,8.5f,altitude,image,0);
            Assert.assertEquals(image[0],LightingCoordinates.screenX(xyz[0],xyz[1]),.001f);
            Assert.assertEquals(image[1],LightingCoordinates.screenY(xyz[0],xyz[1],xyz[2]),.001f);
            Assert.assertEquals(plane+altitude-128,xyz[2],0);
        }
    }
    @Test public void staticReceiversAreReusedAndRelevantChangesInvalidate(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        MapLightingGeometry geometry=new MapLightingGeometry();
        Assert.assertTrue(geometry.ensure(map,null,0));float[] quad={0,0,0,128,256,128,256,0};
        LightingSurface first=geometry.receiver(map,0,0,0,7,(byte)0,quad);
        Assert.assertFalse(geometry.ensure(map,null,0));
        Assert.assertSame(first,geometry.receiver(map,0,0,0,7,(byte)0,quad));
        Assert.assertTrue(geometry.ensure(map,null,128));
        LightingSurface raised=geometry.receiver(map,128,0,0,7,(byte)0,quad);
        Assert.assertNotSame(first,raised);
        for(int i=0;i<raised.vertices.length;i+=8)Assert.assertEquals(128,raised.vertices[i+2],0);
    }
    @Test public void casterFlagsAreAuthoredAndAllMembershipsSurvive(){
        TiledMapLayerData map=new TiledMapLayerData(40,40,256,128,16,TiledProjection.ISO);
        SpatialBlocksComponent blocks=new SpatialBlocksComponent();
        SpatialBlockData a=block(1,0,0,1,1,128),b=block(2,1,0,1,1,160);
        a.addLinkedTileRef(0,0,7);b.addLinkedTileRef(0,0,7);blocks.blocks.add(a);blocks.blocks.add(b);
        MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(map,blocks,0);
        Assert.assertEquals(0,geometry.casters.size);
        float[] quad={-256,-128,-256,512,512,512,512,-128};
        LightingSurface receiver=geometry.receiver(map,0,0,0,7,(byte)0,quad);
        boolean first=false,second=false;
        for(int i=0;i<receiver.vertices.length;i+=8){float z=receiver.vertices[i+2];first|=Math.abs(z-128)<.01f;second|=Math.abs(z-160)<.01f;}
        Assert.assertTrue(first);Assert.assertTrue(second);
        a.shadowCaster=true;blocks.revision++;geometry.ensure(map,blocks,0);
        Assert.assertEquals(6,geometry.casters.size);
        b.lightOccluder=true;blocks.revision++;geometry.ensure(map,blocks,0);
        Assert.assertEquals(12,geometry.casters.size);
    }
    @Test public void casterIndexDoesNotDependOnVisibleImagesAndMarginDoesNotChangeRadius(){
        LightingFrame frame=new LightingFrame();LightingFrame.Light light=new LightingFrame.Light();light.radius=128;
        LightingFrame.Caster caster=new LightingFrame.Caster();caster.minX=200;caster.maxX=210;caster.minY=-10;caster.maxY=10;caster.minZ=0;caster.maxZ=100;
        frame.casters.add(caster);frame.staticCasterCount=1;frame.casterIndex.rebuild(frame,1);
        frame.casterIndex.query(frame,light,256,1);Assert.assertEquals(1,frame.casterIndex.candidates.size);
        Assert.assertEquals(128,light.radius,0);
        frame.casterIndex.query(frame,light,0,1);Assert.assertEquals(0,frame.casterIndex.candidates.size);
    }
    @Test public void localDescriptionRejectsInvalidTopologyAndCopiesAuthoredValues(){
        LocalSurfaceDescription d=new LocalSurfaceDescription();d.xyz=new float[]{0,0,0,1,0,0,0,1,0};
        d.uv=new float[]{0,0,1,0,0,1};d.normals=new float[]{0,0,1,0,0,1,0,0,1};d.triangles=new int[]{0,1,2};
        d.validate();LocalSurfaceDescription copy=d.copy();d.xyz[0]=42;Assert.assertEquals(0,copy.xyz[0],0);
        d.triangles[2]=3;try{d.validate();Assert.fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void partialLocalMeshRetainsUnlitCoverageAndRejectsOverlaps(){
        LocalSurfaceDescription d=new LocalSurfaceDescription();d.xyz=new float[]{0,0,0,1,0,0,0,1,0};
        d.uv=new float[]{0,0,1,0,0,1};d.normals=new float[]{0,0,1,0,0,1,0,0,1};d.triangles=new int[]{0,1,2};
        LightingSurface surface=LocalSurfaceCompiler.compile(d,0,0,0);float area=0;boolean missing=false;
        for(int i=0;i<surface.vertices.length;i+=24){float[] v=surface.vertices;
            area+=Math.abs((v[i+11]-v[i+3])*(v[i+20]-v[i+4])-(v[i+19]-v[i+3])*(v[i+12]-v[i+4]))*.5f;
            missing|=v[i+5]==0&&v[i+6]==0&&v[i+7]==0;
        }
        Assert.assertEquals(1,area,.0001f);Assert.assertTrue(missing);
        d.triangles=new int[]{0,1,2,0,1,2};
        try{LocalSurfaceCompiler.compile(d,0,0,0);Assert.fail();}catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("overlap"));}
    }
    private static SpatialBlockData block(int id,float x,float y,float w,float depth,float height){
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=id;b.x=x;b.y=y;b.width=w;b.depth=depth;b.height=height;return b;
    }
}
