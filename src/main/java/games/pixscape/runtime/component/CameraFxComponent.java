package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class CameraFxComponent extends PooledComponent {
    public String postFxShaderName = "";

    @Override
    protected void reset() {
        postFxShaderName = "";
    }
}
