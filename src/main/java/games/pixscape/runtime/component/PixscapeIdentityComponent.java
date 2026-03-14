package games.pixscape.runtime.component;

import com.artemis.PooledComponent;

public final class PixscapeIdentityComponent extends PooledComponent {
    public long stableId = -1;
    public String name = "unnamed";

    @Override
    protected void reset() {
        stableId = -1;
        name = "unnamed";
    }
}
