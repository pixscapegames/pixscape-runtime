package games.pixscape.runtime.system;

import com.artemis.BaseSystem;

/** Explicit dirty flush at end of frame, to remove dependency on implicit ordering. */
public final class DirtyFlushSystem extends BaseSystem {

    private DirtyTrackerSystem dirty;

    @Override
    protected void processSystem() {
        if (dirty != null) dirty.clearFrame();
    }
}
