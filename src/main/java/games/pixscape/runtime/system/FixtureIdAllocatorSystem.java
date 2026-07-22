package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.loading.SceneMetaRuntime;

/** Scene-bound authoritative allocator for fixture identities. */
public final class FixtureIdAllocatorSystem extends BaseSystem {
    private SceneMetaRuntime sceneMeta;

    public FixtureIdAllocatorSystem(SceneMetaRuntime sceneMeta) {
        bindScene(sceneMeta);
    }

    public void bindScene(SceneMetaRuntime sceneMeta) {
        if (sceneMeta == null) {
            throw new IllegalArgumentException("Fixture ID allocator requires scene metadata.");
        }
        this.sceneMeta = sceneMeta;
    }

    public SceneMetaRuntime sceneMeta() {
        return sceneMeta;
    }

    public int allocateNewFixtureId() {
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
        return sceneMeta.name != null ? sceneMeta.name : "<unnamed>";
    }

    @Override
    protected void processSystem() {
        // Allocation is explicit; this system has no per-frame work.
    }
}
