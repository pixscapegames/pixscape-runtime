package games.pixscape.runtime.render.lighting;

import com.artemis.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.system.LightingGeometrySystem;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActorShadowRectangleCasterTest {
    private static final float[] Q={-80,-40,-80,180,80,180,80,-40};
    @Test public void twoTrianglesFollowAuthoredRectangleAndMapBackToSprite(){
        ActorSpatialRectangleData r=new ActorSpatialRectangleData();r.width=70;r.height=100;r.offsetX=7;r.offsetY=-3;r.offsetZ=5;
        ActorShadowRectangleCaster c=new ActorShadowRectangleCaster();float[] rectangle=new float[12];
        for(float yaw:new float[]{0,45,90,37.25f,179.99f,359.99f})for(float altitude:new float[]{0,40}){
            r.orientation=yaw;r.corners(0,0,altitude,rectangle);c.update(r,0,0,altitude,Q);assertEquals(6,c.vertexCount);
            for(int i=0;i<6;i++){
                int o=i*8,k=SpriteSurfaceSupport.corner(i)*3;
                for(int j=0;j<3;j++)assertEquals(rectangle[k+j],c.vertices[o+j],0);
                float x=Q[0]+c.vertices[o+3]*(Q[6]-Q[0])+c.vertices[o+4]*(Q[2]-Q[0]);
                float y=Q[1]+c.vertices[o+3]*(Q[7]-Q[1])+c.vertices[o+4]*(Q[3]-Q[1]);
                assertEquals(x,LightingCoordinates.screenX(rectangle[k],rectangle[k+1]),.0001);
                assertEquals(y,LightingCoordinates.screenY(rectangle[k],rectangle[k+1],rectangle[k+2]),.0001);
            }
            int updates=c.updates;for(int i=0;i<100;i++)c.update(r,0,0,altitude,Q);assertEquals(updates,c.updates);
        }
    }
    @Test public void imageExtentChangesRebuildUvWithoutClampingOrRecentring(){
        ActorShadowRectangleCaster c=new ActorShadowRectangleCaster();ActorSpatialRectangleData r=new ActorSpatialRectangleData();
        r.width=400;c.update(r,0,0,0,Q);assertTrue(c.vertices[3]<0);
        float[] before=c.vertices.clone();int updates=c.updates;
        c.update(r,0,0,0,new float[]{-90,-40,-90,180,90,180,90,-40});assertEquals(updates+1,c.updates);
        for(int o=0;o<48;o+=8)for(int k=0;k<3;k++)assertEquals(before[o+k],c.vertices[o+k],0);
        assertNotEquals(before[3],c.vertices[3],0);
    }
    @Test public void ecsPublicationTracksClipsFramesAndAltitudeWithoutChangingTheSprite(){
        DynamicEntityRenderState d=new DynamicEntityRenderState(4);FrameRenderQueue q=new FrameRenderQueue(4);
        TiledMapRenderState tiled=new TiledMapRenderState(4);
        LightingGeometrySystem geometry=new LightingGeometrySystem(d,tiled,q,new LayerStateSOA(1));
        World world=new World(new WorldConfigurationBuilder().with(geometry).build());
        try{
            int e=world.create(),slot=d.acquireSlotForEntity(e);d.enabled[slot]=true;
            d.x1[slot]=Q[0];d.y1[slot]=Q[1];d.x2[slot]=Q[2];d.y2[slot]=Q[3];
            d.x3[slot]=Q[4];d.y3[slot]=Q[5];d.x4[slot]=Q[6];d.y4[slot]=Q[7];
            d.u1[slot]=.1f;d.u2[slot]=.4f;d.v1[slot]=.2f;d.v2[slot]=.8f;
            world.getMapper(TransformComponent.class).create(e);
            SpatialPhysicsFootprintComponent foot=world.getMapper(SpatialPhysicsFootprintComponent.class).create(e);foot.valid=true;
            SpatialHeightComponent h=world.getMapper(SpatialHeightComponent.class).create(e);
            h.rectangles=new ActorSpatialRectangles();h.rectangles.put(7,"walk",new ActorSpatialRectangleData());
            ActorSpatialRectangleData idle=new ActorSpatialRectangleData();idle.orientation=90;idle.height=90;h.rectangles.put(7,"idle",idle);
            world.getMapper(AssetRefComponent.class).create(e).assetId=7;
            AnimationComponent animation=world.getMapper(AnimationComponent.class).create(e);animation.currentClip="walk";
            world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster=true;
            geometry.prepareRuntimeAvailability();ActorShadowRectangleCaster storage=q.lighting.entities.get(e).actorShadowRectangle;
            world.process();LightingSurface s=q.lighting.entities.get(e);assertSame(storage.vertices,s.shadowVertices);
            int identity=s.casterShadowGroup();assertTrue(identity>0);assertEquals(identity,s.receiverShadowGroup(0));
            tiled.lightingRevision++;world.process();assertEquals(identity,s.casterShadowGroup());
            float[] receiver=s.vertices.clone();
            ActorSpatialRectangleData active=h.rectangles.get(7,"walk");
            active.offsetX=15;active.offsetY=-8;active.offsetZ=9;world.process();
            assertArrayEquals("Caster offsets must not move the receiver",receiver,s.vertices,0);
            int updates=storage.updates;d.u1[slot]=.4f;d.u2[slot]=.1f;animation.frame=4;world.process();
            assertEquals(updates,storage.updates);assertEquals(.4f,q.lighting.casters.first().u1,0);
            animation.currentClip="idle";h.altitude=40;world.process();assertEquals(updates+1,storage.updates);
            assertEquals(40,q.lighting.casters.first().minZ,.001);assertEquals(130,q.lighting.casters.first().maxZ,.001);
            assertEquals(Q[0],d.x1[slot],0);assertEquals(Q[5],d.y3[slot],0);assertSame(storage,s.actorShadowRectangle);
            assertTrue(s.twoSided);assertTrue(s.actor);assertEquals(1,q.lighting.casters.size);
            assertEquals(identity,s.casterShadowGroup());assertEquals(identity,s.receiverShadowGroup(24));
            d.enabled[slot]=false;world.delete(e);world.process();
            int recreated=world.create(),newSlot=d.acquireSlotForEntity(recreated);d.enabled[newSlot]=true;
            d.x1[newSlot]=d.x2[newSlot]=-32;d.x3[newSlot]=d.x4[newSlot]=32;d.y2[newSlot]=d.y3[newSlot]=64;
            world.process();
            assertTrue(q.lighting.entities.get(recreated).casterShadowGroup()>identity);
        }finally{world.dispose();}
    }
}
