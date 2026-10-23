package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RoundedMapSupportTest {
    private final float[] quad={384,3264,384,3776,640,3776,640,3264};
    private final TiledMapLayerData map=new TiledMapLayerData(80,80,256,128,16,TiledProjection.ISO);
    private final SpatialBlocksComponent blocks=new SpatialBlocksComponent();
    private final Array<TileSurfaceDescription> descriptions=new Array<TileSurfaceDescription>();
    private final MapLightingGeometry geometry=new MapLightingGeometry();
    public RoundedMapSupportTest(){
        pair(27,24);TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=7;d.rounded=new RoundedWallDescription();descriptions.add(d);geometry.descriptions(descriptions);
    }
    private void pair(int gx,int gy){
        map.setTile(gx,gy,7);
        for(int i=0;i<2;i++){SpatialBlockData b=new SpatialBlockData();b.id=blocks.allocateNextSpatialBlockId();b.x=gx;b.y=gy;
            b.width=i==0?3:.23499489f;b.depth=i==0?.23499489f:3;b.height=157.34424f;b.shadowCaster=true;
            SpatialBlockData.LinkedTileRef ref=new SpatialBlockData.LinkedTileRef();ref.gx=gx;ref.gy=gy;ref.tileAssetId=7;
            b.linkedTileRefs.add(ref);blocks.blocks.add(b);}
        blocks.revision++;
    }
    private LightingSurface surface(){geometry.ensure(map,blocks);return geometry.receiver(map,27,24,7,(byte)0,quad);}
    @Test public void integratedReceiverIsBitExactToValidatedSupportAndOldLIsTrimmed(){
        LightingSurface actual=surface();RoundedWallSupport expected=new RoundedWallSupport(3648,3136,128,.23499489f*128,0,157.34424f,6,quad);
        assertArrayEquals(expected.receiver.vertices,actual.vertices,0);assertArrayEquals(expected.receiver.receiverVertices,actual.receiverVertices,0);
        assertArrayEquals(expected.receiver.curveNormals,actual.curveNormals,0);assertArrayEquals(expected.texturedCaster.vertices,actual.casterSurface.vertices,0);
        assertEquals(234,actual.forwardTopology.vertexCount);
        for(LightingSurface s:geometry.casters)for(int i=0;i<s.vertices.length;i+=8){
            if(s.vertices[i+7]==-1&&s.vertices.length==expected.bottomCaster.vertices.length)continue;
            assertTrue("Old opaque L overlaps rounded footprint",s.vertices[i]>=3648||s.vertices[i+1]>=3136);
        }
    }
    @Test public void repeatedFramesAndAtlasRebindDoNotCompile(){
        LightingSurface s=surface();for(int i=0;i<1000;i++){assertFalse(geometry.ensure(map,blocks));assertSame(s,surface());}
        map.markAllChunksContentDirty();assertSame(s,surface());
        assertEquals(1,geometry.roundedProfileCompilationCount());assertEquals(1,geometry.roundedGeometryCompilationCount());
    }
    @Test public void duplicateOccurrenceSharesProfileButNeverWorldGeometry(){
        LightingSurface first=surface();pair(31,26);geometry.ensure(map,blocks);
        float[] q=quad.clone();for(int i=0;i<8;i+=2){q[i]+=256;q[i+1]+=384;}
        LightingSurface second=geometry.receiver(map,31,26,7,(byte)0,q);
        assertSame(first,surface());assertNotSame(first,second);
        for(int i=0;i<first.vertices.length;i+=8)if(first.vertices[i+5]!=0||first.vertices[i+6]!=0||first.vertices[i+7]!=0){
            assertEquals(first.vertices[i]+512,second.vertices[i],.001);assertEquals(first.vertices[i+1]+256,second.vertices[i+1],.001);
            assertEquals(first.vertices[i+3],second.vertices[i+3],.000001);assertEquals(first.vertices[i+4],second.vertices[i+4],.000001);}
        assertEquals(1,geometry.roundedProfileCompilationCount());assertEquals(2,geometry.roundedGeometryCompilationCount());
    }
    @Test public void wallEditRebuildsOccurrenceWithoutRecompilingIntrinsicProfile(){
        LightingSurface s=surface();for(SpatialBlockData b:blocks.blocks)b.height+=10;blocks.revision++;
        assertNotSame(s,surface());assertEquals(1,geometry.roundedProfileCompilationCount());assertEquals(2,geometry.roundedGeometryCompilationCount());
    }
    @Test public void movingOccurrenceReusesIntrinsicButDropsItsOldWorldSupport(){
        LightingSurface first=surface();map.setTile(27,24,0);map.setTile(31,26,7);
        for(SpatialBlockData b:blocks.blocks){b.x+=4;b.y+=2;for(SpatialBlockData.LinkedTileRef r:b.linkedTileRefs){r.gx+=4;r.gy+=2;}}
        blocks.revision++;geometry.ensure(map,blocks);
        float[] movedQuad=quad.clone();for(int i=0;i<8;i+=2){movedQuad[i]+=256;movedQuad[i+1]+=384;}
        LightingSurface moved=geometry.receiver(map,31,26,7,(byte)0,movedQuad);
        assertNotSame(first,moved);assertNotNull(moved.curveNormals);
        assertNull(geometry.receiver(map,27,24,0,(byte)0,quad).curveNormals);
        assertEquals(1,geometry.roundedProfileCompilationCount());assertEquals(2,geometry.roundedGeometryCompilationCount());
    }
    @Test public void incompatibleProfileReplacementIsAtomicAndUndeclaredMapsKeepAutomaticSupport(){
        LightingSurface s=surface();Array<TileSurfaceDescription> next=new Array<TileSurfaceDescription>();TileSurfaceDescription d=descriptions.first().copy();
        d.rounded.radiusCells=.9f;d.rounded.centerGX=d.rounded.centerGY=.9f;next.add(d);geometry.descriptions(next);
        try{prepare();fail();}catch(LightingAdmissionException expected){}
        assertSame(s,geometry.receiver(map,27,24,7,(byte)0,quad));
        geometry.descriptions(new Array<TileSurfaceDescription>());assertNull(surface().curveNormals);
    }
    private boolean prepare(){return geometry.ensure(map,blocks,(m,c,i,q,required)->{System.arraycopy(quad,0,q,0,8);return true;},"scene",1);}
    @Test public void fullAdmissionPreparesOffscreenSupportAndAtomicallyReplacesBothCasterKinds(){
        assertTrue(prepare());assertEquals(1,geometry.roundedGeometryCompilationCount());
        LightingSurface old=surface();int count=geometry.casters.size;
        for(SpatialBlockData b:blocks.blocks)b.height+=20;blocks.revision++;
        assertTrue(prepare());LightingSurface updated=surface();assertNotSame(old,updated);
        assertEquals(count,geometry.casters.size);assertEquals(2,geometry.roundedGeometryCompilationCount());
        float max=0;for(int i=2;i<updated.casterSurface.vertices.length;i+=8)max=Math.max(max,updated.casterSurface.vertices[i]);
        assertEquals(177.34424f,max,.0001);
        for(int i=0;i<100;i++)assertFalse(prepare());
        assertEquals(2,geometry.roundedGeometryCompilationCount());
    }
    @Test public void localRegistrationAndUnavailableResourcesRejectBeforePublication(){
        prepare();LightingSurface old=surface();LightingSurface oldOpaque=geometry.casters.first();
        TileSurfaceDescription d=new TileSurfaceDescription();d.assetId=7;d.geometry=new LocalSurfaceDescription();
        d.geometry.xyz=new float[]{0,0,0,1,0,0,0,1,0};d.geometry.uv=new float[]{0,0,1,0,0,1};
        d.geometry.normals=new float[]{0,0,1,0,0,1,0,0,1};
        d.geometry.triangles=new int[]{0,1,2};
        Array<TileSurfaceDescription> next=new Array<>();next.add(d);geometry.descriptions(next);
        try{prepare();fail();}catch(LightingAdmissionException expected){assertTrue(expected.getMessage().contains("registration"));}
        assertSame(oldOpaque,geometry.casters.first());assertSame(old,geometry.receiver(map,27,24,7,(byte)0,quad));
        geometry.descriptions(descriptions);
        try{geometry.ensure(map,blocks,(m,c,i,q,r)->false,"scene",2);fail();}catch(LightingAdmissionException expected){assertTrue(expected.getMessage().contains("unavailable"));}
        assertSame(oldOpaque,geometry.casters.first());
    }
    @Test public void structuralAdmissionSubtractsInLocalCoordinatesBeforeTranslation(){
        for(int position:new int[]{1,2,27,63}){
            TiledMapLayerData m=new TiledMapLayerData(80,80,256,128,16,TiledProjection.ISO);m.setTile(position,position,7);
            SpatialBlocksComponent bs=new SpatialBlocksComponent();
            for(SpatialBlockData b:blocks.blocks){SpatialBlockData copy=b.copy();copy.x=copy.y=position;
                copy.linkedTileRefs.first().gx=copy.linkedTileRefs.first().gy=position;bs.blocks.add(copy);}
            TileSurfaceDescription d=descriptions.first().copy();d.rounded.radiusCells=d.rounded.centerGX=d.rounded.centerGY=1.4f;
            MapLightingGeometry g=new MapLightingGeometry();Array<TileSurfaceDescription> ds=new Array<>();ds.add(d);g.descriptions(ds);
            assertTrue("Structural admission at "+position,g.ensure(m,bs));
            // Separately admit the valid profile against its translated native quad.
            d=descriptions.first().copy();ds=new Array<>();ds.add(d);g.descriptions(ds);
            final float sx=(position-27-(position-24))*128,sy=(position-27+position-24)*64;
            assertTrue(g.ensure(m,bs,(mm,c,i,q,r)->{for(int k=0;k<8;k++)q[k]=quad[k]+(k%2==0?sx:sy);return true;},"scene",1));
        }
    }
    @Test public void invalidCandidateDoesNotReplacePublishedGeometry(){
        LightingSurface s=surface();Array<TileSurfaceDescription> invalid=new Array<TileSurfaceDescription>();invalid.add(descriptions.first().copy());invalid.add(descriptions.first().copy());
        geometry.descriptions(invalid);try{geometry.ensure(map,blocks);fail();}catch(IllegalArgumentException expected){}
        assertSame(s,geometry.receiver(map,27,24,7,(byte)0,quad));geometry.descriptions(descriptions);assertSame(s,surface());
    }
}
