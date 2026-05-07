package games.pixscape.runtime.system;

import com.artemis.BaseSystem;

/** Explicit dirty flush at end of frame, to remove dependency on implicit ordering. */
public final class DirtyFlushSystem extends BaseSystem {

    private final DirtyTrackerSystem dirty;

    public DirtyFlushSystem(DirtyTrackerSystem dirty) {
        this.dirty = dirty;
    }

    @Override
    protected void processSystem() {
        if (dirty != null) dirty.clearFrame();
    }
}
