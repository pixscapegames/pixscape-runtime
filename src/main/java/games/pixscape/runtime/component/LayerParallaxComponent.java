package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class LayerParallaxComponent extends PooledComponent {
    public float factorX = 1f, factorY = 1f;

    @Override
    protected void reset(){
        factorX=1f;
        factorY=1f;
    }
}
