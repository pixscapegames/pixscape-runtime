package games.pixscape.runtime.render.lighting;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.system.LightingGeometrySystem;
import org.junit.*;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class ConeInclinationTest {
    private World world;
    private DynamicEntityRenderState dynamic;
    private FrameRenderQueue queue;
    private ConeLightComponent cone;
    private int slot;
    private Field originsField;
    private Object previousOrigins;

    @Before public void setup() throws Exception {
        // Publication only needs shader provenance; no GL program is created by this test.
        originsField=ShaderRegistry.class.getDeclaredField("originsByIdx");originsField.setAccessible(true);
        previousOrigins=originsField.get(null);Array<ShaderOrigin> origins=new Array<>();origins.add(ShaderOrigin.CORE);originsField.set(null,origins);
        dynamic=new DynamicEntityRenderState(4);queue=new FrameRenderQueue(4);
        LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
        world=new World(new WorldConfigurationBuilder().with(new LightingGeometrySystem(dynamic,new TiledMapRenderState(4),queue,layers)).build());
        int entity=world.create();cone=world.getMapper(ConeLightComponent.class).create(entity);
        cone.height=128;cone.radius=400;cone.coneAngleDeg=60;
        slot=dynamic.acquireSlotForEntity(entity);dynamic.enabled[slot]=true;dynamic.light[slot]=1;dynamic.shader[slot]=0;
        orient(.37f);
    }
    @After public void cleanup() throws Exception {if(world!=null)world.dispose();originsField.set(null,previousOrigins);}
    private void orient(float angle){
        float x=MathUtils.cos(angle)*16,y=MathUtils.sin(angle)*16;
        dynamic.x1[slot]=100;dynamic.y1[slot]=200;
        dynamic.x2[slot]=100-y;dynamic.y2[slot]=200+x;
        dynamic.x3[slot]=100+x-y;dynamic.y3[slot]=200+x+y;
        dynamic.x4[slot]=100+x;dynamic.y4[slot]=200+y;
    }
    @Test public void zeroKeepsTheExistingDirectionAndInclinationDoesNotMoveTheSourceOrRange(){
        world.process();LightingFrame.Light reused=queue.lighting.lights.first();
        float x=reused.x,y=reused.y,z=reused.z,dx=reused.dx,dy=reused.dy,outer=reused.cosOuter;
        for(float angle:new float[]{-90,-45,-17.25f,0,23.5f,45,90,0}){
            cone.inclinationDeg=angle;world.process();LightingFrame.Light light=queue.lighting.lights.first();
            assertSame(reused,light);assertEquals(x,light.x,0);assertEquals(y,light.y,0);assertEquals(z,light.z,0);
            assertEquals(400,light.radius,0);assertEquals(outer,light.cosOuter,0);
            assertEquals(Math.sin(Math.toRadians(angle)),light.dz,.001);
            assertEquals(1,light.dx*light.dx+light.dy*light.dy+light.dz*light.dz,.001);
            if(angle==0){assertEquals(dx,light.dx,0);assertEquals(dy,light.dy,0);assertEquals(0,light.dz,0);}
            if(Math.abs(angle)==90){assertEquals(0,light.dx,0);assertEquals(0,light.dy,0);}
            assertEquals(0,queue.lighting.casters.size);
        }
    }
    @Test public void resolvedAzimuthAndInclinationRemainIndependent(){
        cone.inclinationDeg=-35.5f;
        for(float rotation:new float[]{-2f,0f,.4f,MathUtils.HALF_PI,2.7f}){
            orient(rotation);world.process();LightingFrame.Light l=queue.lighting.lights.first();
            assertEquals(Math.sin(Math.toRadians(-35.5)),l.dz,.001);
            assertEquals(rotation,(float)Math.atan2((l.dx+l.dy)*.5f,l.dx-l.dy),.001);
        }
    }
    @Test public void invalidInclinationsAreRejected(){
        for(float invalid:new float[]{-91,91,Float.NaN,Float.POSITIVE_INFINITY}){
            cone.inclinationDeg=invalid;
            try{world.process();fail("Expected invalid inclination rejection");}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void pooledComponentsResetToHorizontal(){
        int entity=world.create();ConeLightComponent value=world.getMapper(ConeLightComponent.class).create(entity);value.inclinationDeg=-47;
        world.process();world.delete(entity);world.process();int another=world.create();
        assertEquals(0,world.getMapper(ConeLightComponent.class).create(another).inclinationDeg,0);
    }
}
