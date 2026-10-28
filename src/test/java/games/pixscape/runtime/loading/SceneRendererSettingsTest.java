package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.JsonReader;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.lighting.ActiveLights;
import games.pixscape.runtime.render.lighting.LightingAdmissionException;
import org.junit.Test;
import static org.junit.Assert.*;

public class SceneRendererSettingsTest {
    @Test public void sceneSelectionDefaultsLegacyAndExplicitOverrideWins()throws Exception {
        String base="{sceneSchemaVersion:3,nextEntityStableId:1,nextPhysicsShapeId:1";
        SceneMetaRuntime legacy=SceneMetaRuntime.fromJson(new JsonReader().parse(base+"}"),"legacy");
        SceneMetaRuntime forward=SceneMetaRuntime.fromJson(new JsonReader().parse(base+",forwardLighting:true}"),"forward");
        assertFalse(legacy.forwardLighting);assertTrue(new SceneMetaRuntime(forward).forwardLighting);
        PixscapeEngine engine=new PixscapeEngine();
        var apply=PixscapeEngine.class.getDeclaredMethod("applyAmbientFromMeta",SceneMetaRuntime.class);apply.setAccessible(true);
        var choice=PixscapeEngine.class.getDeclaredField("forwardLighting");choice.setAccessible(true);
        apply.invoke(engine,forward);assertTrue(choice.getBoolean(engine));
        apply.invoke(engine,legacy);assertFalse(choice.getBoolean(engine));
        engine.setForwardLighting(false);apply.invoke(engine,forward);assertFalse(choice.getBoolean(engine));
    }
    @Test public void pointAndConeAdmissionUsesVisibilityAndEnabledTogether(){
        World world=new World(new WorldConfigurationBuilder().build());
        try {
            assertEquals(0,ActiveLights.count(world));
            int p=world.create(),c=world.create();world.getMapper(PointLightComponent.class).create(p);
            ConeLightComponent cone=world.getMapper(ConeLightComponent.class).create(c);cone.enabled=false;
            world.process();assertEquals(1,ActiveLights.count(world));ActiveLights.validateForward(world);
            cone.enabled=true;assertEquals(2,ActiveLights.count(world));
            assertThrows(LightingAdmissionException.class,()->ActiveLights.validateForward(world));
            world.getMapper(VisibilityComponent.class).create(c).visible=false;
            assertEquals(1,ActiveLights.count(world));ActiveLights.validateForward(world);
        } finally {world.dispose();}
    }
}
