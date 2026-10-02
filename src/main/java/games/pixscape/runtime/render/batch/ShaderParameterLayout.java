package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import games.pixscape.runtime.component.ShaderFloatParam;

/** Ordered numeric parameter slots for one material shader. */
public final class ShaderParameterLayout {
    public static final int MAX_FLOATS = 16;
    public static final ShaderParameterLayout EMPTY = new ShaderParameterLayout("<none>", null);

    private final String shaderName;
    private final String[] names;
    private final float[] defaults;
    private final ObjectIntMap<String> slots = new ObjectIntMap<>();

    public ShaderParameterLayout(String shaderName, Array<ShaderFloatParam> parameters) {
        if (shaderName == null || shaderName.length() == 0) {
            throw new IllegalArgumentException("Shader parameter layout needs a shader name");
        }
        this.shaderName = shaderName;
        int count = parameters == null ? 0 : parameters.size;
        if (count > MAX_FLOATS) {
            throw new IllegalArgumentException("Shader '" + shaderName + "' has " + count
                    + " floats; maximum is " + MAX_FLOATS);
        }
        names = new String[count];
        defaults = new float[count];
        for (int i = 0; i < count; i++) {
            ShaderFloatParam param = parameters.get(i);
            if (param == null || param.name == null || param.name.length() == 0
                    || !param.name.matches("[A-Za-z_][A-Za-z0-9_]*") || param.name.startsWith("gl_")
                    || slots.containsKey(param.name) || Float.isNaN(param.value) || Float.isInfinite(param.value)) {
                throw new IllegalArgumentException("Invalid or duplicate float parameter at slot " + i
                        + " for shader '" + shaderName + "'");
            }
            names[i] = param.name;
            defaults[i] = param.value;
            slots.put(param.name, i);
        }
    }

    public String shaderName() { return shaderName; }
    public int size() { return names.length; }
    public String name(int slot) { return names[slot]; }
    public float defaultValue(int slot) { return defaults[slot]; }

    public int slot(String name) {
        return slots.get(name, -1);
    }

    /** Stable GLSL names keep slot changes from reassigning another parameter's meaning. */
    public String glslDefines() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            result.append("#define PIXSCAPE_PARAM_").append(names[i]).append(' ').append(i).append('\n');
        }
        return result.toString();
    }
}
