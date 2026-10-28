package games.pixscape.runtime.render;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.ShaderParamsComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;

/** Reusable submission values; the authored light component owns intensity. */
public final class LightParameterBinding {
    public static final String INTENSITY = "u_lightIntensity";
    private final ComponentMapper<PointLightComponent> points;
    private final ComponentMapper<ConeLightComponent> cones;
    private final Array<ShaderFloatParam> values = ShaderParamsComponent.newShaderFloatArray();
    private final ShaderFloatParam intensity = new ShaderFloatParam(INTENSITY, 1f);

    public LightParameterBinding(World world) {
        points = world.getMapper(PointLightComponent.class);
        cones = world.getMapper(ConeLightComponent.class);
    }

    public Array<ShaderFloatParam> bind(int entityId, ShaderParameterLayout layout, Array<ShaderFloatParam> authored) {
        if (entityId < 0) return authored;
        PointLightComponent point = points.getSafe(entityId, null);
        ConeLightComponent cone = cones.getSafe(entityId, null);
        if (point == null && cone == null) return authored;
        float value = point != null ? point.intensity : cone.intensity;
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f)
            throw new IllegalArgumentException("Light intensity must be finite and nonnegative");
        if (layout == null || layout.slot(INTENSITY) < 0)
            throw new IllegalArgumentException("Light material must declare per-entity " + INTENSITY);
        values.clear();
        if (authored != null) {
            for (int i = 0; i < authored.size; i++) {
                ShaderFloatParam parameter = authored.get(i);
                if (parameter == null || !INTENSITY.equals(parameter.name)) values.add(parameter);
            }
        }
        intensity.value = value;
        values.add(intensity);
        return values;
    }
}
