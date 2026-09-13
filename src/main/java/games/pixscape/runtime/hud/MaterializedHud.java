package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detached native Scene2D tree produced from one validated HUD document.
 *
 * <p>The actor index contains only persisted document nodes. Actors added later by application
 * code remain ordinary Scene2D runtime state and are not added to this index.</p>
 */
public final class MaterializedHud {
    private final Actor root;
    private final Map<String, Actor> actorById;

    MaterializedHud(Actor root, Map<String, Actor> actorById) {
        this.root = root;
        this.actorById = Collections.unmodifiableMap(
                new LinkedHashMap<String, Actor>(actorById));
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
}
