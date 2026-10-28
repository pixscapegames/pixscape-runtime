package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.TooltipManager;
import com.badlogic.gdx.utils.Disposable;

import java.util.*;

/**
 * {@code SUPPORTED_EXPERT} native Scene2D tree produced from one validated HUD document.
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
 * and are not automatically added to this index. Renaming/reparenting a native actor does not
 * change its authored ID. Missing IDs and wrong types in typed accessors throw IllegalArgumentException;
 * actor(id) remains nullable. No lookup traverses the actor tree or uses reflection.</p>
 *
 * <p>Install listeners once per HUD activation, keep the listener instance and remove it with
 * Actor.removeListener when rebinding. Do not add a new listener each frame. Native ChangeEvents
 * may also result from programmatic changes; use the widget's native event policy where needed.
 * setText changes only that widget and its native layout, not HUD materialization. Formatting,
 * glyph/layout growth and Textra token parsing can allocate: update text only when it changes.
 * Use native setVisible, setDisabled, setValue, setDrawable, selection and focus APIs. Drawables,
 * fonts, textures and skins borrowed from this HUD remain engine-owned; never dispose them.</p>
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

    /** O(1) average typed lookup of the native List; listeners and items belong to the live actor. */
    @SuppressWarnings("unchecked")
    public com.badlogic.gdx.scenes.scene2d.ui.List<String> list(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (actor == null) throw new IllegalArgumentException("HUD List ID '" + nodeId + "' does not exist.");
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.List))
            throw new IllegalArgumentException("HUD node '" + nodeId + "' is not a List.");
        return (com.badlogic.gdx.scenes.scene2d.ui.List<String>) actor;
    }

    /** O(1) average lookup of the borrowed native Label; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.Label label(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Label)) throw wrongType(nodeId, "Label", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.Label) actor;
    }

    /** O(1) average lookup of the borrowed native Button; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.Button button(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Button)) throw wrongType(nodeId, "Button", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.Button) actor;
    }

    /** O(1) average lookup of the borrowed native TextButton; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.TextButton textButton(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.TextButton)) throw wrongType(nodeId, "TextButton", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.TextButton) actor;
    }

    /** O(1) average lookup of the borrowed native CheckBox; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.CheckBox checkBox(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.CheckBox)) throw wrongType(nodeId, "CheckBox", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.CheckBox) actor;
    }

    /** O(1) average lookup of the borrowed native Slider; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.Slider slider(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Slider)) throw wrongType(nodeId, "Slider", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.Slider) actor;
    }

    /** O(1) average lookup of the borrowed native ProgressBar; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.ProgressBar progressBar(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.ProgressBar)) throw wrongType(nodeId, "ProgressBar", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.ProgressBar) actor;
    }

    /** O(1) average lookup of the borrowed native Image; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.Image image(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Image)) throw wrongType(nodeId, "Image", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.Image) actor;
    }

    /** O(1) average lookup of the borrowed native TextField; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.TextField textField(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.TextField)) throw wrongType(nodeId, "TextField", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.TextField) actor;
    }

    /** O(1) average lookup of the borrowed native SelectBox; throws for absent ID or wrong type. */
    @SuppressWarnings("unchecked")
    public com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String> selectBox(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.SelectBox)) throw wrongType(nodeId, "SelectBox", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.SelectBox<String>) actor;
    }

    /** O(1) average lookup of the borrowed native Window; throws for absent ID or wrong type. */
    public com.badlogic.gdx.scenes.scene2d.ui.Window window(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Window)) throw wrongType(nodeId, "Window", actor);
        return (com.badlogic.gdx.scenes.scene2d.ui.Window) actor;
    }

    /** O(1) average lookup of the borrowed native TextraLabel; throws for absent ID or wrong type. */
    public com.github.tommyettinger.textra.TextraLabel textraLabel(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.github.tommyettinger.textra.TextraLabel)) throw wrongType(nodeId, "TextraLabel", actor);
        return (com.github.tommyettinger.textra.TextraLabel) actor;
    }

    /** O(1) average lookup of the borrowed native TypingLabel; throws for absent ID or wrong type. */
    public com.github.tommyettinger.textra.TypingLabel typingLabel(String nodeId) {
        Actor actor = actorById.get(nodeId);
        if (!(actor instanceof com.github.tommyettinger.textra.TypingLabel)) throw wrongType(nodeId, "TypingLabel", actor);
        return (com.github.tommyettinger.textra.TypingLabel) actor;
    }

    private static IllegalArgumentException wrongType(String nodeId, String expected, Actor actor) {
        return new IllegalArgumentException("HUD node '" + nodeId + "' "
                + (actor == null ? "does not exist" : "is not a " + expected) + ".");
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
