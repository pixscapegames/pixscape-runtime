package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class LayerPostFXComponent extends PooledComponent {
    public String[] passes = new String[0];

    @Override
    protected void reset(){
        passes = new String[0];
    }
}
