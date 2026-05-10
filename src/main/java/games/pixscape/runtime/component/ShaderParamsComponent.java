package games.pixscape.runtime.component;

import com.artemis.Component;
import com.badlogic.gdx.utils.Array;

/**
 * Parameters de shader par entity.
 * For this first version: only float uniforms (name -> value).
 */
public class ShaderParamsComponent extends Component {
    /**
     * Map nom d'uniform -> valeur float.
     */
    public Array<ShaderFloatParam> floats = new Array<>(ShaderFloatParam[]::new);

}
