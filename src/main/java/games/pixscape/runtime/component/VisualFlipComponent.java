package games.pixscape.runtime.component;

import com.artemis.PooledComponent;
import com.artemis.annotations.Transient;

/** Per-instance visual orientation, deliberately excluded from scene/asset persistence. */
@Transient
public final class VisualFlipComponent extends PooledComponent {
    public transient boolean flipX;
    public transient boolean flipY;

    @Override
    protected void reset() {
        flipX = false;
        flipY = false;
    }
}
