package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native Scene2D tree produced from one validated HUD document.
 *
 * <p>When obtained from {@code PixscapeEngine.getActiveHud()}, this object is a borrowed live
 * view owned by the engine. Applications must not dispose it or its shared resources. References
 * to this object and its actors remain usable only while this exact HUD is active; replacing or
 * removing the HUD, changing Scene, or disposing the engine ends that lifetime. Keeping a Java
 * reference does not keep the underlying resources alive. Reacquire the new HUD and reattach
 * listeners after an activation change.</p>
 *
 * <p>The returned actors are the actual native Scene2D and TextraTypist objects and may be used
 * through their native APIs on the LibGDX render thread. The actor index contains only authored
 * document nodes. Actors added later by application code remain ordinary Scene2D runtime state
 * and are not automatically added to this index.</p>
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

    /** Returns the actual native root of the materialized Scene2D tree. */
    public Actor root() {
        return root;
    }

    /** Returns the complete immutable authored-ID-to-Actor index. */
    public Map<String, Actor> actorById() {
        return actorById;
    }

    /**
     * Returns the actual native actor for an authored node ID, or {@code null} when absent.
     * This performs an O(1) average lookup in the complete authored actor index.
     */
    public Actor actor(String nodeId) {
        return actorById.get(nodeId);
    }

    /**
     * Releases actor-local derived resources without disposing borrowed HUD textures.
     * Lifecycle owners use this method internally. Applications must not call it on a
     * {@code MaterializedHud} borrowed from {@code PixscapeEngine.getActiveHud()}.
     */
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
