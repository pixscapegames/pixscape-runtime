package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.SurfaceLightingComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.ActorSpatialRectangleData;
import games.pixscape.runtime.spatial.ActorSpatialRectangles;
import games.pixscape.runtime.system.LightingGeometrySystem;
import org.junit.Test;

import static org.junit.Assert.*;

public class ActorShadowRectangleCasterTest {
    private static final float[] Q={-80,-40,-80,180,80,180,80,-40};
    @Test public void lightFacingTrianglesPreserveMappingContactAndReference(){
        ActorSpatialRectangleData r=new ActorSpatialRectangleData();r.x=-35;r.y=-3;r.width=70;r.height=100;r.pivotX=7;r.pivotY=-3;
        ActorShadowRectangleCaster c=new ActorShadowRectangleCaster();
        LightingFrame.Caster bounds=new LightingFrame.Caster();
        for(float altitude:new float[]{0,40}){
            c.update(r,0,0,altitude,Q);assertEquals(6,c.vertexCount);
            float[] reference=c.vertices.clone();c.bounds(bounds);int updates=c.updates;
            for(int angle=0;angle<360;angle++){
                c.faceLight(200*(float)Math.cos(Math.toRadians(angle)),200*(float)Math.sin(Math.toRadians(angle)));
                assertArrayEquals(reference,c.vertices,0);
                float u=(r.pivotX-r.x)/r.width;
                assertEquals(LightingCoordinates.worldX(r.pivotX,r.pivotY,0),c.lightVertices[0]+u*(c.lightVertices[40]-c.lightVertices[0]),.0001);
                assertEquals(LightingCoordinates.worldY(r.pivotX,r.pivotY,0),c.lightVertices[1]+u*(c.lightVertices[41]-c.lightVertices[1]),.0001);
                assertEquals(altitude,c.lightVertices[2],0);
                for(int i=0;i<6;i++){
                    int o=i*8;float x=c.lightVertices[o],y=c.lightVertices[o+1];
                    assertTrue(x>=bounds.minX-.0001 && x<=bounds.maxX+.0001);
                    assertTrue(y>=bounds.minY-.0001 && y<=bounds.maxY+.0001);
                    assertEquals(reference[o+3],c.lightVertices[o+3],0);assertEquals(reference[o+4],c.lightVertices[o+4],0);
                    float sx=Q[0]+reference[o+3]*(Q[6]-Q[0]);
                    float sy=Q[1]+reference[o+4]*(Q[3]-Q[1]);
                    assertEquals(sx,LightingCoordinates.screenX(reference[o],reference[o+1]),.0001);
                    assertEquals(sy,LightingCoordinates.screenY(reference[o],reference[o+1],reference[o+2]),.0001);
                }
                c.update(r,0,0,altitude,Q);assertEquals(updates,c.updates);
            }
            c.faceLight(LightingCoordinates.worldX(r.pivotX,r.pivotY,0),LightingCoordinates.worldY(r.pivotX,r.pivotY,0));
            for(float value:c.lightVertices)assertTrue(Float.isFinite(value));
        }
    }
    @Test public void imageExtentChangesRebuildUvWithoutClampingOrRecentring(){
        ActorShadowRectangleCaster c=new ActorShadowRectangleCaster();ActorSpatialRectangleData r=new ActorSpatialRectangleData();
        r.x=-200;r.width=400;c.update(r,0,0,0,Q);assertTrue(c.vertices[3]<0);
        float[] before=c.vertices.clone();int updates=c.updates;
        c.update(r,0,0,0,new float[]{-90,-40,-90,180,90,180,90,-40});assertEquals(updates+1,c.updates);
        for(int o=0;o<48;o+=8)for(int k=0;k<3;k++)assertEquals(before[o+k],c.vertices[o+k],0);
        assertNotEquals(before[3],c.vertices[3],0);
    }
    @Test public void translatedActorAndLightKeepTheSameLocalSilhouette(){
        ActorShadowRectangleCaster c=new ActorShadowRectangleCaster();ActorSpatialRectangleData r=new ActorSpatialRectangleData();
        r.pivotX=3;r.pivotY=-4;c.update(r,0,0,0,Q);c.faceLight(100,70);
        float[] before=c.lightVertices.clone(),moved=Q.clone();
        for(int i=0;i<8;i+=2){moved[i]+=20;moved[i+1]+=20;}
        c.update(r,30,10,0,moved);c.faceLight(130,80);
        for(int o=0;o<48;o+=8){
            assertEquals(before[o]+30,c.lightVertices[o],.0001);
            assertEquals(before[o+1]+10,c.lightVertices[o+1],.0001);
            for(int k=2;k<8;k++)assertEquals(before[o+k],c.lightVertices[o+k],.0001);
        }
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
            ActorSpatialRectangleData idle=new ActorSpatialRectangleData();idle.pivotX=9;idle.height=90;h.rectangles.put(7,"idle",idle);
            world.getMapper(AssetRefComponent.class).create(e).assetId=7;
            AnimationComponent animation=world.getMapper(AnimationComponent.class).create(e);animation.currentClip="walk";
            world.getMapper(SurfaceLightingComponent.class).create(e).shadowCaster=true;
            geometry.prepareRuntimeAvailability();ActorShadowRectangleCaster storage=q.lighting.entities.get(e).actorShadowRectangle;
            world.process();LightingSurface s=q.lighting.entities.get(e);assertSame(storage.vertices,s.shadowVertices);
            int identity=s.casterShadowGroup();assertTrue(identity>0);assertEquals(identity,s.receiverShadowGroup(0));
            tiled.lightingRevision++;world.process();assertEquals(identity,s.casterShadowGroup());
            float[] receiver=s.vertices.clone();
            ActorSpatialRectangleData active=h.rectangles.get(7,"walk");
            active.x=15;active.y=-8;active.pivotX=9;world.process();
            assertArrayEquals("Rectangle and pivot edits must not move the receiver",receiver,s.vertices,0);
            int updates=storage.updates;d.u1[slot]=.4f;d.u2[slot]=.1f;animation.frame=4;world.process();
            assertEquals(updates,storage.updates);assertEquals(.4f,q.lighting.casters.first().u1,0);
            animation.currentClip="idle";h.altitude=40;world.process();assertEquals(updates+1,storage.updates);
            assertEquals(40,q.lighting.casters.first().minZ,.001);assertEquals(130,q.lighting.casters.first().maxZ,.001);
            assertEquals(Q[0],d.x1[slot],0);assertEquals(Q[5],d.y3[slot],0);assertSame(storage,s.actorShadowRectangle);
            assertTrue(s.twoSided);assertTrue(s.actor);assertEquals(1,q.lighting.casters.size);
            assertEquals(identity,s.casterShadowGroup());assertEquals(identity,s.receiverShadowGroup(24));
            world.getMapper(SpatialHeightComponent.class).remove(e);world.process();
            assertNull("Disabling spatial must stop using the cached actor caster",s.shadowVertices);
            LightingFrame.Caster actual=q.lighting.casters.first();
            LightingFrame.Caster expected=new LightingFrame.Caster();expected.surface=new LightingSurface(s.vertices);expected.bounds();
            assertEquals(expected.minX,actual.minX,0);assertEquals(expected.maxY,actual.maxY,0);
            d.enabled[slot]=false;world.delete(e);world.process();
            int recreated=world.create(),newSlot=d.acquireSlotForEntity(recreated);d.enabled[newSlot]=true;
            d.x1[newSlot]=d.x2[newSlot]=-32;d.x3[newSlot]=d.x4[newSlot]=32;d.y2[newSlot]=d.y3[newSlot]=64;
            world.process();
            assertTrue(q.lighting.entities.get(recreated).casterShadowGroup()>identity);
        }finally{world.dispose();}
    }
}
