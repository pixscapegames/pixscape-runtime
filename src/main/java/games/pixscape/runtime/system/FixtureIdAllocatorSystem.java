package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.loading.SceneMetaRuntime;

/** Authoritative fixture identity allocator, explicitly bound to the active scene. */
public final class FixtureIdAllocatorSystem extends BaseSystem {
    private SceneMetaRuntime sceneMeta;

    public FixtureIdAllocatorSystem() {
    }

    public FixtureIdAllocatorSystem(SceneMetaRuntime sceneMeta) {
        bind(sceneMeta);
    }

    public void bind(SceneMetaRuntime sceneMeta) {
        if (sceneMeta == null) {
            throw new IllegalArgumentException("Cannot bind fixture ID allocator: scene metadata is null");
        }
        if (sceneMeta.nextFixtureId <= 0) {
            throw new IllegalStateException(
                    "Cannot bind fixture ID allocator to scene '" + sceneName(sceneMeta)
                            + "': nextFixtureId must be strictly positive, got " + sceneMeta.nextFixtureId);
        }
        this.sceneMeta = sceneMeta;
    }

    public void unbind() {
        sceneMeta = null;
    }

    public boolean isBound() {
        return sceneMeta != null;
    }

    public SceneMetaRuntime sceneMeta() {
        return sceneMeta;
    }

    public int allocateNewFixtureId() {
        if (sceneMeta == null) {
            throw new IllegalStateException(
                    "Cannot allocate fixture ID: no active scene metadata is bound");
        }
        int next = sceneMeta.nextFixtureId;
        if (next <= 0) {
            throw new IllegalStateException(
                    "Invalid fixture ID high-water mark for scene '" + sceneName() + "': nextFixtureId=" + next);
        }
        if (next == Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "Fixture ID space exhausted for scene '" + sceneName() + "': nextFixtureId=" + next);
        }
        sceneMeta.nextFixtureId = next + 1;
        return next;
    }

    private String sceneName() {
        return sceneName(sceneMeta);
    }

    private static String sceneName(SceneMetaRuntime sceneMeta) {
        return sceneMeta.name != null ? sceneMeta.name : "<unnamed>";
    }

    @Override
    protected void processSystem() {
        // Allocation is explicit; this system has no per-frame work.
    }
}
