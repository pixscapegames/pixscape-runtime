package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
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
    private final Map<String, Cell<?>> cellById;
    private final List<Disposable> ownedResources;
    private final TooltipManager tooltipManager;
    private final Map<Actor, TextTooltip> tooltips;
    private boolean disposed;

    MaterializedHud(Actor root, Map<String, Actor> actorById, Map<String, Cell<?>> cellById,
                    List<Disposable> ownedResources, TooltipManager tooltipManager,
                    Map<Actor, TextTooltip> tooltips) {
        this.root = root;
        this.actorById = Collections.unmodifiableMap(
                new LinkedHashMap<String, Actor>(actorById));
        this.cellById = Collections.unmodifiableMap(new LinkedHashMap<String, Cell<?>>(cellById));
        this.ownedResources = new ArrayList<Disposable>(ownedResources);
        this.tooltipManager = tooltipManager;
        this.tooltips = new LinkedHashMap<Actor, TextTooltip>(tooltips);
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

    /** O(1) average typed lookup; returned native Dialog belongs to this HUD instance. */
    public HudDialog dialog(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (actor == null) throw new IllegalArgumentException("HUD Dialog ID '" + nodeId + "' does not exist.");
        if (!(actor instanceof HudDialog))
            throw new IllegalArgumentException("HUD node '" + nodeId + "' is not a Dialog.");
        return (HudDialog) actor;
    }

    /** Returns a materialized explicit Table cell, or null when the cell is absent. */
    public Cell<?> cell(String cellId) {
        return cellById.get(cellId);
    }

    /** Native owner Table for an explicit cell, or null when the cell is absent. */
    public Table cellTable(String cellId) {
        Cell<?> cell = cellById.get(cellId);
        return cell != null ? cell.getTable() : null;
    }

    /** Complete immutable index of explicit authored cells for this materialization. */
    public Map<String, Cell<?>> cellById() {
        return cellById;
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
        for (Actor actor : actorById.values()) {
            if (tooltipManager != null && actor instanceof HudDialog) {
                try {
                    ((HudDialog) actor).close();
                } catch (RuntimeException disposalFailure) {
                    if (failure == null) failure = disposalFailure;
                }
            }
        }
        releaseTooltips(tooltipManager, tooltips);
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

    static void releaseTooltips(TooltipManager manager, Map<Actor, TextTooltip> tooltips) {
        if (manager == null) return;
        manager.hideAll(); // Cancels native delayed show/reset tasks before actors are released.
        for (Map.Entry<Actor, TextTooltip> entry : tooltips.entrySet()) {
            TextTooltip tooltip = entry.getValue();
            entry.getKey().removeListener(tooltip);
            tooltip.getContainer().clearActions();
            tooltip.getContainer().remove();
        }
        tooltips.clear();
    }
}
