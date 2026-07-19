package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class RenderRepeatComponent extends PooledComponent {
    public boolean repeatX;
    public boolean repeatY;

    @Override
    protected void reset() {
        repeatX = false;
        repeatY = false;
    }
}
