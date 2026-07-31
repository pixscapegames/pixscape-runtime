package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;

/**
 * Explicit dirty flush at end of frame, to remove dependency on implicit ordering.
 */
public final class DirtyFlushSystem extends BaseSystem implements ProfiledSystem {

    private DirtyTrackerSystem dirty;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.DIRTY_FLUSH);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.DIRTY_FLUSH, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        if (dirty != null) dirty.clearFrame();
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
