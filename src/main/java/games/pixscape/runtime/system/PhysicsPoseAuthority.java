package games.pixscape.runtime.system;

import com.artemis.BaseSystem;

/**
 * Transient execution ownership for physical Body poses.
 *
 * <p>This is deliberately independent of Box2D stepping: a paused Runtime scene remains
 * {@link Mode#RUNTIME_PHYSICS}, while Studio authoring uses {@link Mode#AUTHORING} without
 * stepping. The state is World-owned, non-serialized, and defaults to authored authority.</p>
 */
public final class PhysicsPoseAuthority extends BaseSystem {
    public enum Mode {
        AUTHORING,
        RUNTIME_PHYSICS
    }

    private Mode mode = Mode.AUTHORING;

    @Override
    protected void processSystem() {
        // Ownership is configured by the Runtime lifecycle; no per-frame work is required.
    }

    public Mode mode() {
        return mode;
    }

    public boolean isRuntimePhysics() {
        return mode == Mode.RUNTIME_PHYSICS;
    }

    public void setMode(Mode mode) {
        if (mode == null) throw new IllegalArgumentException("Physics pose authority mode is required.");
        this.mode = mode;
    }
}
