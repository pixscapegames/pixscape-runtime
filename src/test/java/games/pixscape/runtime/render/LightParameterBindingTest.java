package games.pixscape.runtime.render;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;
import games.pixscape.runtime.render.batch.ShaderParameterRows;
import org.junit.Test;
import static org.junit.Assert.*;

public class LightParameterBindingTest {
    @Test public void componentIntensityOverridesStaleParameterWithoutMutatingAuthoredValues() {
        World world = new World();
        try {
            int id = world.create();
            PointLightComponent light = world.getMapper(PointLightComponent.class).create(id);
            Array<ShaderFloatParam> parameters = new Array<>();
            ShaderFloatParam gain = new ShaderFloatParam("u_gain", .25f);
            ShaderFloatParam stale = new ShaderFloatParam(LightParameterBinding.INTENSITY, .5f);
            parameters.add(gain); parameters.add(stale);
            ShaderParameterLayout layout = new ShaderParameterLayout("light", parameters);
            ShaderParameterRows rows = new ShaderParameterRows(16); rows.setLayout(layout);
            LightParameterBinding binding = new LightParameterBinding(world);
            for (float intensity : new float[]{0f, .25f, 1f, 3f, 10f}) {
                light.intensity = intensity;
                int row = rows.setEntityParameters(binding.bind(id, layout, parameters));
                assertEquals(.25f, rows.data()[row * 16], 0f);
                assertEquals(intensity, rows.data()[row * 16 + 1], 0f);
                assertEquals(.5f, stale.value, 0f);
                assertSame(gain, parameters.get(0));
                assertEquals(2, parameters.size);
            }
        } finally { world.dispose(); }
    }

    @Test public void coneUsesItsComponentAndOrdinaryMaterialIsUnchanged() {
        World world = new World();
        try {
            Array<ShaderFloatParam> defaults = new Array<>();
            defaults.add(new ShaderFloatParam(LightParameterBinding.INTENSITY, 1f));
            ShaderParameterLayout layout = new ShaderParameterLayout("cone", defaults);
            int id = world.create();
            world.getMapper(ConeLightComponent.class).create(id).intensity = 4f;
            LightParameterBinding binding = new LightParameterBinding(world);
            assertEquals(4f, binding.bind(id, layout, null).get(0).value, 0f);
            assertSame(defaults, binding.bind(world.create(), layout, defaults));
        } finally { world.dispose(); }
    }
}
