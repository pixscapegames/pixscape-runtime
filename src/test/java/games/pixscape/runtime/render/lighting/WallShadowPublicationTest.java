package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.LightingGeometrySystem;
import games.pixscape.runtime.tiled.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the ECS publication path, not the frozen-replay group reconstruction. */
public class WallShadowPublicationTest {
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
        surface.ownerEntity=owner;surface.pocSourceBlockId=block;surface.shadowCaster=true;
        surface.receiverBlockIds=new int[]{block,block};
        return surface;
    }
}
