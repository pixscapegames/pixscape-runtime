package games.pixscape.runtime.render.lighting;

import com.artemis.*;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.system.LightingGeometrySystem;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActorVolumeCasterTest {
    private static final float[] Q={-80,-40,-80,180,80,180,80,-40};
    @Test public void boxFacesUseOneReferenceProjectionAndStayInsideImageAtAllOrientations(){
        ActorVolumeData volume=new ActorVolumeData();volume.width=70;volume.depth=45;volume.height=100;
        volume.offsetX=7;volume.offsetY=-3;volume.offsetZ=5;
        ActorVolumeCaster geometry=new ActorVolumeCaster();
        for(float yaw:new float[]{0,45,90,37.25f,179.99f,359.99f})for(float altitude:new float[]{0,40}){
            volume.orientation=yaw;geometry.update(volume,0,0,altitude,Q);
            assertTrue(geometry.vertexCount>0);assertTrue(geometry.vertexCount<=ActorVolumeCaster.MAX_VERTICES);
            for(int o=0;o<geometry.vertexCount*8;o+=8){
                float[] p=geometry.vertices;
                assertTrue(p[o+3]>=0&&p[o+3]<=1);assertTrue(p[o+4]>=0&&p[o+4]<=1);
                assertEquals(Q[0]+160*p[o+3],LightingCoordinates.screenX(p[o],p[o+1]),.0001);
                assertEquals(Q[1]+220*p[o+4],LightingCoordinates.screenY(p[o],p[o+1],p[o+2]),.0001);
                assertTrue(p[o+2]>=altitude+5-.001);assertTrue(p[o+2]<=altitude+105+.001);
            }
            int updates=geometry.updates;
            for(int i=0;i<100;i++)geometry.update(volume,0,0,altitude,Q);
            assertEquals(updates,geometry.updates);
        }
    }
    @Test public void outsideImagePublishesNoOpaqueResidualBox(){
        ActorVolumeCaster caster=new ActorVolumeCaster();caster.update(new ActorVolumeData(),10000,0,0,Q);
        assertEquals(0,caster.vertexCount);
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
            h.volumes=new ActorVolumes();h.volumes.put(7,"walk",new ActorVolumeData());
            ActorVolumeData idle=new ActorVolumeData();idle.orientation=90;idle.height=90;h.volumes.put(7,"idle",idle);
            world.getMapper(AssetRefComponent.class).create(e).assetId=7;
            AnimationComponent animation=world.getMapper(AnimationComponent.class).create(e);animation.currentClip="walk";
            world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster=true;
            geometry.prepareRuntimeAvailability();ActorVolumeCaster storage=q.lighting.entities.get(e).actorVolume;
            world.process();LightingSurface s=q.lighting.entities.get(e);assertSame(storage.vertices,s.shadowVertices);
            int identity=s.casterShadowGroup();assertTrue(identity>0);assertEquals(identity,s.receiverShadowGroup(0));
            tiled.lightingRevision++;world.process();assertEquals(identity,s.casterShadowGroup());
            int updates=storage.updates;d.u1[slot]=.4f;d.u2[slot]=.1f;animation.frame=4;world.process();
            assertEquals(updates,storage.updates);assertEquals(.4f,q.lighting.casters.first().u1,0);
            animation.currentClip="idle";h.altitude=40;world.process();assertEquals(updates+1,storage.updates);
            assertEquals(40,q.lighting.casters.first().minZ,.001);assertEquals(130,q.lighting.casters.first().maxZ,.001);
            assertEquals(Q[0],d.x1[slot],0);assertEquals(Q[5],d.y3[slot],0);assertSame(storage,s.actorVolume);
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
