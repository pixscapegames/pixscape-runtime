package games.pixscape.runtime.component;

import com.artemis.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Shader parameters per entity.
 * For this first version: only float uniforms (name -> value).
 */
public class ShaderParamsComponent extends Component {
    public static Array<ShaderFloatParam> newShaderFloatArray() {
        return new Array<>(ShaderFloatParam[]::new);
    }

    /**
     * Uniform name -> float value map.
     */
    public Array<ShaderFloatParam> floats = newShaderFloatArray();

}
