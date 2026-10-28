package games.pixscape.runtime.component;

import com.artemis.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Per-entity float overrides for a registered shader parameter table layout.
 * Draw-wide uniforms are configured separately by the submit system.
 */
public class ShaderParamsComponent extends Component {
    public static Array<ShaderFloatParam> newShaderFloatArray() {
        return new Array<>(ShaderFloatParam[]::new);
    }

    /**
     * Declared parameter name -> finite float override.
     */
    public Array<ShaderFloatParam> floats = newShaderFloatArray();

}
