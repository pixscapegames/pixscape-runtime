package games.pixscape.runtime.render.lighting;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.TileChunk;
import org.junit.Test;
import static org.junit.Assert.*;

/** No GL: exercise the same detached native-image boundary as RenderTiledSyncSystem. */
public class SilhouettePreparationTest {
    private final TiledMapLayerData map=new TiledMapLayerData(8,8,64,32,4,TiledProjection.ISO);
    private final SpatialBlocksComponent blocks=new SpatialBlocksComponent();
    private final MapLightingGeometry geometry=new MapLightingGeometry();
    private final float[] quad=new float[8];
    private final SpatialBlockData block=new SpatialBlockData();
    private final Quads quads=new Quads();

    public SilhouettePreparationTest(){
        map.setTile(2,3,7);map.tileToRenderQuad(2,3,quad);
        block.id=44;block.x=2.1f;block.y=3.2f;block.width=.75f;block.depth=.63f;
        block.height=54;block.shadowCaster=true;block.addLinkedTileRef(2,3,7);
        block.linkedTileRefsAuthored=false; // Opaque reference: no authored tile contribution yet.
        blocks.blocks.add(block);
    }
    private final class Quads implements MapLightingGeometry.NativeQuads {
        boolean available=true,required;int calls;
        public boolean resolve(TiledMapLayerData m,TileChunk chunk,int cell,float[] out,boolean mustExist){
            calls++;required|=mustExist;System.arraycopy(quad,0,out,0,8);return available;
        }
    }
    private boolean prepare(){return geometry.ensure(map,blocks,quads,"atlas",1);}
    private LightingSurface receiver(int asset){return geometry.receiver(map,2,3,asset,(byte)0,quad);}
    private void alpha(){block.linkedTileRefsAuthored=true;blocks.revision++;}
    private void reject(){try{prepare();fail("Candidate should be rejected");}catch(LightingAdmissionException expected){}}

    @Test public void completeGraphAdmitsDistinctContributionsInBothBlockOrders(){
        alpha();SpatialBlockData other=block.copy();other.id=45;other.x+=.1f;
        blocks.blocks.add(other);
        prepare();LightingSurface before=receiver(7);
        assertEquals(2,before.casterContributions.size);
        blocks.blocks.reverse();blocks.revision++;prepare();LightingSurface after=receiver(7);
        assertArrayEquals(before.vertices,after.vertices,0);
        assertArrayEquals(before.receiverBlockIds,after.receiverBlockIds);
        assertEquals(2,after.casterContributions.size);assertEquals(0,geometry.casters.size);
    }
    @Test public void missingNativeImageKeepsPublishedVolumeAndReceiver(){
        prepare();LightingSurface before=receiver(7),caster=geometry.casters.first();int revision=geometry.publicationRevision();
        alpha();quads.available=false;reject();
        assertTrue("The alpha image must be mandatory",quads.required);
        assertEquals(revision,geometry.publicationRevision());assertEquals(6,geometry.casters.size);
        assertSame(caster,geometry.casters.first());assertSame(before,receiver(7));
    }
    @Test public void duplicateBlockIdentitiesCannotSilentlyMergeExclusions(){
        alpha();prepare();LightingSurface old=receiver(7);int revision=geometry.publicationRevision();
        blocks.blocks.add(block.copy());blocks.revision++;reject();
        assertSame(old,receiver(7));assertEquals(revision,geometry.publicationRevision());
    }
    @Test public void emptyLinkedCellCannotSilentlyRemoveTheCaster(){
        prepare();LightingSurface caster=geometry.casters.first();alpha();map.setTile(2,3,0);
        reject();assertSame(caster,geometry.casters.first());assertEquals(6,geometry.casters.size);
    }
    @Test public void contentEditInvalidatesAlphaEvenWithoutExplicitLightingDescription(){
        alpha();prepare();int revision=geometry.publicationRevision();LightingSurface before=receiver(7);
        map.setTile(2,3,8);assertTrue(prepare());
        assertEquals(revision+1,geometry.publicationRevision());assertNotSame(before,receiver(8));
        assertNotNull(receiver(8).casterSurface);
        assertEquals("Authored snapshot is not silently rewritten",7,block.linkedTileRefs.first().tileAssetId);
    }
    @Test public void repeatedPublicationDoesNotResolveResourcesAgain(){
        alpha();prepare();LightingSurface before=receiver(7);int calls=quads.calls;
        for(int i=0;i<1000;i++){assertFalse(prepare());assertSame(before,receiver(7));}
        assertEquals(calls,quads.calls);
    }
    @Test public void failedRepackRetainsPublishedAlphaAndCanRecover(){
        alpha();prepare();LightingSurface before=receiver(7);int revision=geometry.publicationRevision();
        quads.available=false;
        try{geometry.ensure(map,blocks,quads,"atlas",2);fail();}catch(LightingAdmissionException expected){}
        assertEquals(revision,geometry.publicationRevision());assertSame(before,receiver(7));
        quads.available=true;assertTrue(geometry.ensure(map,blocks,quads,"atlas",2));
        assertNotNull(receiver(7).casterSurface);
    }
    @Test public void lastMissingContributionRejectsTheEntireVolumeReplacement(){
        prepare();LightingSurface old=geometry.casters.first();alpha();block.addLinkedTileRef(3,3,8);
        reject();assertSame(old,geometry.casters.first());assertEquals(6,geometry.casters.size);
        map.setTile(3,3,8);assertTrue(prepare());
        assertEquals(0,geometry.casters.size);
        assertEquals(44,geometry.receiver(map,3,3,8,(byte)0,quad).casterSurface.pocSourceBlockId);
        assertEquals(44,receiver(7).casterSurface.pocSourceBlockId);
    }
    @Test public void preparedAnimationVariantsReuseBothReceiverAndCasterGeometry(){
        alpha();final float[] taller=quad.clone();taller[3]+=10;taller[5]+=10;
        MapLightingGeometry.NativeQuads animation=new MapLightingGeometry.NativeQuads(){
            public int frameCount(int asset){return 2;}
            public boolean resolveFrame(TiledMapLayerData m,TileChunk c,int cell,int frame,float[] out){
                System.arraycopy(frame==0?quad:taller,0,out,0,8);return true;
            }
            public boolean resolve(TiledMapLayerData m,TileChunk c,int cell,float[] out,boolean required){System.arraycopy(quad,0,out,0,8);return true;}
        };
        geometry.ensure(map,blocks,animation,"atlas",1);
        LightingSurface first=receiver(7),second=geometry.receiver(map,2,3,7,(byte)0,taller);
        assertNotSame(first,second);
        for(int i=0;i<1000;i++){
            assertSame(first,receiver(7));assertSame(second,geometry.receiver(map,2,3,7,(byte)0,taller));
        }
    }
}
