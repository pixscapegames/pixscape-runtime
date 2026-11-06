package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.LightingGeometrySystem;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class UnlitLightPublicationTest {
    @Test public void publicationTracksLifecycleAndLogicalVisibilityNotTheCulledIcon() throws Exception {
        Field field=ShaderRegistry.class.getDeclaredField("originsByIdx");field.setAccessible(true);
        Object previous=field.get(null);Array<ShaderOrigin> origins=new Array<>();origins.add(ShaderOrigin.CORE);field.set(null,origins);
        DynamicEntityRenderState dynamic=new DynamicEntityRenderState(4);FrameRenderQueue queue=new FrameRenderQueue(4);
        LayerStateSOA layers=new LayerStateSOA(1);layers.touchLayerIndex(0);layers.enabled[0]=true;
        World world=new World(new WorldConfigurationBuilder().with(new LightingGeometrySystem(dynamic,new TiledMapRenderState(4),queue,layers)).build());
        try {
            world.process();assertEquals(0,queue.lighting.lights.size);
            int entity=world.create(),slot=dynamic.acquireSlotForEntity(entity);
            dynamic.enabled[slot]=true;dynamic.light[slot]=1;dynamic.shader[slot]=0;dynamic.layerIndex[slot]=0;
            // Icon outside a 64x32 camera, but a large light still reaches receivers inside it.
            dynamic.x1[slot]=dynamic.x3[slot]=100;dynamic.y1[slot]=dynamic.y3[slot]=16;
            dynamic.visible[slot]=false;
            VisibilityComponent visibility=world.getMapper(VisibilityComponent.class).create(entity);
            visibility.inView=false;visibility.culledByFrustum=true;
            PointLightComponent light=world.getMapper(PointLightComponent.class).create(entity);light.radius=1000;light.enabled=false;
            world.process();assertEquals(0,queue.lighting.lights.size);
            LightingFrame.Light published=null;
            for(int i=0;i<12;i++){
                light.enabled=true;world.process();assertEquals(1,queue.lighting.lights.size);
                if(published==null)published=queue.lighting.lights.first();else assertSame(published,queue.lighting.lights.first());
                light.enabled=false;world.process();assertEquals(0,queue.lighting.lights.size);
            }
            light.enabled=true;light.intensity=0;world.process();assertEquals("No new zero-intensity culling rule",1,queue.lighting.lights.size);
            visibility.visible=false;world.process();assertEquals(0,queue.lighting.lights.size);
            visibility.visible=true;layers.enabled[0]=false;world.process();assertEquals(0,queue.lighting.lights.size);
            layers.enabled[0]=true;world.process();assertEquals(1,queue.lighting.lights.size);
            dynamic.releaseSlotForEntity(entity);world.delete(entity);world.process();assertEquals(0,queue.lighting.lights.size);
            int next=world.create(),nextSlot=dynamic.acquireSlotForEntity(next);
            dynamic.enabled[nextSlot]=true;dynamic.light[nextSlot]=1;dynamic.shader[nextSlot]=0;dynamic.layerIndex[nextSlot]=0;
            world.getMapper(PointLightComponent.class).create(next);world.process();assertEquals(1,queue.lighting.lights.size);
        } finally {world.dispose();field.set(null,previous);}
    }
}
