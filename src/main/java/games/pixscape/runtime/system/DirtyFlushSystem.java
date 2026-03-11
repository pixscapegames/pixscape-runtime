package games.pixscape.runtime.system;

import com.artemis.BaseSystem;

/** Flush explicite des dirty en fin de frame, pour enlever la dépendance à l'ordre implicite. */
public final class DirtyFlushSystem extends BaseSystem {

    private DirtyTrackerSystem dirty;

    @Override
    protected void processSystem() {
        if (dirty != null) dirty.clearFrame();
    }
}
