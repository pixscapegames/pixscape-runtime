package games.pixscape.runtime.component;

import com.artemis.Component;
import com.badlogic.gdx.utils.ObjectFloatMap;

/**
 * Paramètres de shader par entité.
 * Pour cette première version: uniquement des uniforms float (nom -> valeur).
 */
public class ShaderParamsComponent extends Component {
    /** Map nom d'uniform -> valeur float. */
    public ObjectFloatMap<String> floats = new ObjectFloatMap<>();

}
