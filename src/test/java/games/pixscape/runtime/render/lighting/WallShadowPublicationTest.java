package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.LightingGeometrySystem;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.*;

/** Exercises the ECS publication path, not the frozen-replay group reconstruction. */
public class WallShadowPublicationTest {
    @Test public void unrelatedDeletionKeepsPublishedStaticCastersAndIndex() throws Exception {
        Fixture fixture=fixture(false);
        try {
            int owner=fixture.world.create();publishOwner(fixture,owner,0,1);
            LightingFrame.Caster caster=fixture.queue.lighting.casters.first();
            int[] groups=fixture.tiled.lightingSurfaces.get(0).receiverShadowGroups;
            int[] indexSeen=indexSeen(fixture.queue.lighting);
            int unrelated=fixture.world.create();fixture.world.getMapper(SurfaceLightingComponent.class).create(unrelated);
            fixture.world.process();fixture.world.delete(unrelated);fixture.world.process();fixture.world.process();
            assertSame(caster,fixture.queue.lighting.casters.first());
            assertSame(groups,fixture.tiled.lightingSurfaces.get(0).receiverShadowGroups);
            assertSame(indexSeen,indexSeen(fixture.queue.lighting));
            assertPublishedCaster(fixture.queue.lighting,fixture.tiled.lightingSurfaces.get(0));
        } finally {fixture.world.dispose();}
    }

    @Test public void actorCasterDeletionKeepsStaticPublicationAndIndex() throws Exception {
        Fixture fixture=fixture(false);
        try {
            int owner=fixture.world.create();publishOwner(fixture,owner,0,1);
            LightingFrame.Caster staticCaster=fixture.queue.lighting.casters.first();
            int[] indexSeen=indexSeen(fixture.queue.lighting);
            int actor=fixture.world.create();SurfaceLightingComponent authored=fixture.world.getMapper(SurfaceLightingComponent.class).create(actor);
            authored.shadowCaster=true;int slot=fixture.dynamic.acquireSlotForEntity(actor);fixture.dynamic.enabled[slot]=true;fixture.dynamic.textureHandle[slot]=1;
            fixture.dynamic.x2[slot]=fixture.dynamic.x3[slot]=fixture.dynamic.y3[slot]=16;fixture.dynamic.x4[slot]=fixture.dynamic.y4[slot]=16;
            fixture.world.process();assertEquals(2,fixture.queue.lighting.casters.size);
            fixture.dynamic.releaseSlotForEntity(actor);fixture.world.delete(actor);fixture.world.process();fixture.world.process();
            assertEquals(1,fixture.queue.lighting.casters.size);assertSame(staticCaster,fixture.queue.lighting.casters.first());
            assertSame(indexSeen,indexSeen(fixture.queue.lighting));
            assertPublishedCaster(fixture.queue.lighting,fixture.tiled.lightingSurfaces.get(0));
        } finally {fixture.world.dispose();}
    }

    @Test public void deletedGroupOnlyOwnerRemovesPublishedSurface() {
        Fixture fixture=fixture(false);
        try {
            int owner=fixture.world.create();LightingSurface groupOnly=surface(owner,1);groupOnly.shadowCaster=false;
            fixture.tiled.lightingSurfaces.put(0,groupOnly);fixture.tiled.enabled[0]=true;fixture.tiled.lightingRevision++;
            fixture.world.process();assertEquals(fixture.tiled.lightingRevision,fixture.queue.lighting.staticCasterRevision);
            fixture.world.delete(owner);fixture.world.process();fixture.world.process();fixture.world.process();
            assertNull(fixture.tiled.lightingSurfaces.get(0));
            assertEquals(fixture.tiled.lightingRevision,fixture.queue.lighting.staticCasterRevision);
        } finally {fixture.world.dispose();}
    }

    @Test public void deletedStaticOwnerRepublishesRemainingGroupsAndCasterIndex() {
        Fixture fixture=fixture(true);
        try {
            int removed=createTiledOwner(fixture.world),survivor=createTiledOwner(fixture.world);fixture.world.process();
            publishOwner(fixture,removed,0,1);publishOwner(fixture,survivor,1,2);
            fixture.world.delete(removed);fixture.world.process();
            fixture.world.process();
            fixture.world.process();
            assertNull(fixture.tiled.lightingMaps.get(removed));
            assertNull(fixture.tiled.lightingSurfaces.get(0));
            assertEquals(1,fixture.queue.lighting.staticCasterCount);
            LightingSurface remaining=fixture.tiled.lightingSurfaces.get(1);
            assertPublishedCaster(fixture.queue.lighting,remaining);
            assertEquals(remaining.selfShadowExclusionId,remaining.receiverShadowGroup(0));
        } finally {fixture.world.dispose();}
    }

    @Test public void mixedDeletionAndEntityReuseKeepsOnlyCurrentCasterIdentities() {
        Fixture fixture=fixture(true);
        try {
            int removed=createTiledOwner(fixture.world),survivor=createTiledOwner(fixture.world);fixture.world.process();
            publishOwner(fixture,removed,0,1);publishOwner(fixture,survivor,1,2);
            int actor=fixture.world.create();SurfaceLightingComponent authored=fixture.world.getMapper(SurfaceLightingComponent.class).create(actor);
            authored.shadowCaster=true;int slot=fixture.dynamic.acquireSlotForEntity(actor);fixture.dynamic.enabled[slot]=true;fixture.dynamic.textureHandle[slot]=1;
            fixture.dynamic.x2[slot]=fixture.dynamic.x3[slot]=fixture.dynamic.y3[slot]=16;fixture.dynamic.x4[slot]=fixture.dynamic.y4[slot]=16;
            fixture.world.process();int formerIdentity=fixture.queue.lighting.casters.peek().surface.selfShadowExclusionId;
            fixture.dynamic.releaseSlotForEntity(actor);fixture.world.delete(actor);fixture.world.delete(removed);fixture.world.process();
            int replacement=fixture.world.create();assertTrue(replacement==actor||replacement==removed);authored=fixture.world.getMapper(SurfaceLightingComponent.class).create(replacement);
            authored.shadowCaster=true;slot=fixture.dynamic.acquireSlotForEntity(replacement);fixture.dynamic.enabled[slot]=true;fixture.dynamic.textureHandle[slot]=1;
            fixture.dynamic.x2[slot]=fixture.dynamic.x3[slot]=fixture.dynamic.y3[slot]=16;fixture.dynamic.x4[slot]=fixture.dynamic.y4[slot]=16;
            fixture.world.process();fixture.world.process();
            assertNull(fixture.tiled.lightingSurfaces.get(0));assertEquals(1,fixture.queue.lighting.staticCasterCount);assertPublishedCaster(fixture.queue.lighting,fixture.tiled.lightingSurfaces.get(1));
            LightingFrame.Caster replacementCaster=fixture.queue.lighting.casters.peek();
            assertNotEquals(formerIdentity,replacementCaster.surface.selfShadowExclusionId);
        } finally {fixture.world.dispose();}
    }

    @Test public void constructionEditRepublishesGroupsWhileUnchangedFramesReuseThem() {
        TiledMapRenderState tiled = new TiledMapRenderState(4);
        FrameRenderQueue queue = new FrameRenderQueue(4);
        LayerStateSOA layers = new LayerStateSOA(1);
        World world = new World(new WorldConfigurationBuilder().with(new LightingGeometrySystem(
                new DynamicEntityRenderState(4), tiled, queue, layers)).build());
        try {
            int owner = world.create();
            world.process();
            TiledMapLayerData map = new TiledMapLayerData(16,16,256,128,16,TiledProjection.ISO);
            SpatialBlocksComponent blocks = new SpatialBlocksComponent();
            SpatialBlockData first = wall(1,0), second = wall(2,2);
            blocks.blocks.add(first); blocks.blocks.add(second);
            MapLightingGeometry geometry = new MapLightingGeometry();
            assertTrue(geometry.ensure(map, blocks));
            tiled.lightingMaps.put(owner, geometry);
            LightingSurface a = surface(owner,1), b = surface(owner,2);
            tiled.lightingSurfaces.put(0,a); tiled.lightingSurfaces.put(1,b);
            tiled.enabled[0]=tiled.enabled[1]=true;
            tiled.lightingRevision++;
            world.process();
            assertNotEquals(a.selfShadowExclusionId,b.selfShadowExclusionId);
            assertEquals(a.casterShadowGroup(),b.casterShadowGroup());
            assertEquals(2,queue.lighting.staticCasterCount);
            int[] originalGroups = b.receiverShadowGroups;
            LightingFrame.Caster originalCaster = queue.lighting.casters.first();
            for(int frame=0;frame<100;frame++)world.process();
            assertSame(originalGroups,b.receiverShadowGroups);
            assertSame(originalCaster,queue.lighting.casters.first());
            second.x=8; blocks.revision++;
            assertTrue(geometry.ensure(map,blocks));
            tiled.lightingRevision++;
            world.process();
            assertNotSame(originalGroups,b.receiverShadowGroups);
            assertNotEquals(a.casterShadowGroup(),b.casterShadowGroup());
            assertEquals(b.selfShadowExclusionId,b.receiverShadowGroup(0));
            assertEquals(tiled.lightingRevision,queue.lighting.staticCasterRevision);
        } finally { world.dispose(); }
    }
    private static SpatialBlockData wall(int id,float x) {
        SpatialBlockData block=new SpatialBlockData();
        block.id=id;block.structureId=1;block.x=x;block.width=2;block.depth=.25f;block.height=128;
        block.addLinkedTileRef((int)x,0,1);
        return block;
    }
    private static LightingSurface surface(int owner,int block) {
        LightingSurface surface=new LightingSurface(new float[48]);
        for(int i=0;i<6;i++){int offset=i*8;surface.vertices[offset]=block*16+i%3;surface.vertices[offset+1]=i/3;surface.vertices[offset+7]=1;}
        surface.ownerEntity=owner;surface.pocSourceBlockId=block;surface.shadowCaster=true;
        surface.receiverBlockIds=new int[]{block,block};
        return surface;
    }

    private static Fixture fixture(boolean includeTiledSync) {
        TiledMapRenderState tiled=new TiledMapRenderState(4);FrameRenderQueue queue=new FrameRenderQueue(4);
        DynamicEntityRenderState dynamic=new DynamicEntityRenderState(4);LayerStateSOA layers=new LayerStateSOA(1);
        LightingGeometrySystem lighting=new LightingGeometrySystem(dynamic,tiled,queue,layers);
        WorldConfigurationBuilder builder=new WorldConfigurationBuilder();
        if(includeTiledSync)builder.with(new RenderTiledSyncSystem(new OrthographicCamera(),tiled,null,0));
        World world=new World(builder.with(lighting).build());return new Fixture(world,tiled,queue,dynamic);
    }

    private static void publishOwner(Fixture fixture,int owner,int ref,int block) {
        TiledMapLayerData map=new TiledMapLayerData(16,16,256,128,16,TiledProjection.ISO);SpatialBlocksComponent blocks=new SpatialBlocksComponent();
        blocks.blocks.add(wall(block,block*2));MapLightingGeometry geometry=new MapLightingGeometry();assertTrue(geometry.ensure(map,blocks));
        fixture.tiled.lightingMaps.put(owner,geometry);LightingSurface surface=surface(owner,block);fixture.tiled.lightingSurfaces.put(ref,surface);fixture.tiled.enabled[ref]=true;
        fixture.tiled.lightingRevision++;fixture.world.process();
    }

    private static int createTiledOwner(World world) {
        int entity=world.create();world.getMapper(EntityIndexComponent.class).create(entity);world.getMapper(TiledLayerComponent.class).create(entity);return entity;
    }

    private static void assertPublishedCaster(LightingFrame frame,LightingSurface surface) {
        LightingFrame.Light light=new LightingFrame.Light();light.radius=10_000;frame.casterIndex.query(frame,light,0,frame.staticCasterCount);
        boolean found=false;for(int i=0;i<frame.casterIndex.candidates.size;i++)
            if(frame.casters.get(frame.casterIndex.candidates.get(i)).surface==surface)found=true;
        assertTrue(found);
    }

    private static int[] indexSeen(LightingFrame frame) throws Exception {
        java.lang.reflect.Field field=CasterIndex.class.getDeclaredField("seen");field.setAccessible(true);return (int[])field.get(frame.casterIndex);
    }

    private static final class Fixture {
        final World world;final TiledMapRenderState tiled;final FrameRenderQueue queue;final DynamicEntityRenderState dynamic;
        Fixture(World world,TiledMapRenderState tiled,FrameRenderQueue queue,DynamicEntityRenderState dynamic){
            this.world=world;this.tiled=tiled;this.queue=queue;this.dynamic=dynamic;
        }
    }
}
