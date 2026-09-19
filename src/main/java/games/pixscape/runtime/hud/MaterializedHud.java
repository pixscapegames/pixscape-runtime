package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detached native Scene2D tree produced from one validated HUD document.
 *
 * <p>The actor index contains only persisted document nodes. Actors added later by application
 * code remain ordinary Scene2D runtime state and are not added to this index.</p>
 */
public final class MaterializedHud implements Disposable {
    private final Actor root;
    private final Map<String, Actor> actorById;
    private final List<Disposable> ownedResources;
    private boolean disposed;

    MaterializedHud(Actor root, Map<String, Actor> actorById,
                    List<Disposable> ownedResources) {
        this.root = root;
        this.actorById = Collections.unmodifiableMap(
                new LinkedHashMap<String, Actor>(actorById));
        this.ownedResources = new ArrayList<Disposable>(ownedResources);
    }

    /** Returns the actual root of the materialized Scene2D tree. */
    public Actor root() {
        return root;
    }

    /** Returns the complete immutable persisted-ID-to-Actor index. */
    public Map<String, Actor> actorById() {
        return actorById;
    }

    /** Performs an O(1) average lookup in the complete persisted actor index. */
    public Actor actor(String nodeId) {
        return actorById.get(nodeId);
    }

    /** Releases actor-local derived resources without disposing borrowed HUD textures. */
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        for (int i = ownedResources.size() - 1; i >= 0; i--) {
            try {
                ownedResources.get(i).dispose();
            } catch (RuntimeException disposalFailure) {
                if (failure == null) failure = disposalFailure;
            }
        }
        ownedResources.clear();
        if (failure != null) throw failure;
    }
}
