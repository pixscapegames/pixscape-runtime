package games.pixscape.runtime.hud;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.scenes.scene2d.EventListener;

/**
 * One native Dialog in a materialized HUD. Its buttons, content, listeners, and form state are
 * created once per HUD instance and survive {@link #close()} / {@link #open()} cycles. References
 * become invalid when that HUD is disposed or replaced.
 */
public final class HudDialog extends Dialog {
    /**
     * Called once for a completed user click, before any configured close. Returning true allows
     * that close, while false vetoes it. A button configured to stay open does so either way.
     * Exceptions propagate and leave the Dialog open unless the callback closed it itself.
     */
    public interface ResultHandler {
        boolean onResult(HudDialog source, String resultId);
    }

    private HudDialogSlot slot;
    private boolean stageLevel;
    private boolean authoredKeepWithinStage;
    private boolean open;
    private boolean resultCancelled;
    private ResultHandler resultHandler;

    HudDialog(String title, Window.WindowStyle style) {
        super(title, style);
        // Dialog's ChangeListener also handles programmatic Button.setChecked. Route only
        // completed native clicks through the authored result contract instead.
        Array<EventListener> listeners = getButtonTable().getListeners();
        for (int i = listeners.size - 1; i >= 0; i--)
            if (listeners.get(i) instanceof ChangeListener)
                getButtonTable().removeListener(listeners.get(i));
    }

    /** Replaces the one callback, or removes it with null. With no callback, closing is allowed. */
    public void onResult(ResultHandler handler) { resultHandler = handler; }

    void addResultButton(final Button button, final String resultId,
                         final boolean closeAfterActivation, boolean interactive) {
        button(button, resultId);
        if (!interactive) return;
        button.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (button.isDisabled() || !open) return;
                boolean previousCancellation = resultCancelled;
                resultCancelled = false;
                try {
                    result(resultId);
                    if (closeAfterActivation && !resultCancelled && open) close();
                } finally {
                    resultCancelled = previousCancellation;
                }
            }
        });
    }

    @Override protected void result(Object value) {
        if (resultHandler != null && !resultHandler.onResult(this, (String) value)) cancel();
    }

    @Override public void cancel() { resultCancelled = true; }

    void attach(HudDialogSlot slot, boolean stageLevel, boolean keepWithinStage) {
        this.slot = slot;
        this.stageLevel = stageLevel;
        this.authoredKeepWithinStage = keepWithinStage;
        setKeepWithinStage(stageLevel && keepWithinStage);
    }

    public boolean isOpen() { return open; }

    public void open() {
        if (slot != null && slot.getStage() != null) show(slot.getStage(), null);
    }

    @Override public Dialog show(Stage stage) {
        return show(stage, Actions.sequence(Actions.alpha(0),
                Actions.fadeIn(.4f, Interpolation.fade)));
    }

    @Override public Dialog show(Stage stage, Action action) {
        if (open || slot == null || stage == null || slot.getStage() != stage
                || !effectivelyVisible(slot)) return this;
        Vector2 position = stageLevel
                ? slot.localToStageCoordinates(new Vector2()) : null;
        float width = slot.getWidth();
        float height = slot.getHeight();
        // Native show packs and reparents, so restore
        // authored geometry and, for nested nonmodal dialogs, the stable authored slot.
        super.show(stage, action);
        if (stageLevel) {
            setBounds(position.x, position.y, width, height);
        } else {
            slot.addActor(this);
            setBounds(0f, 0f, width, height);
        }
        setKeepWithinStage(stageLevel && authoredKeepWithinStage);
        setVisible(true);
        open = true;
        return this;
    }

    public void close() {
        hide(null);
    }

    @Override public void hide(Action action) {
        if (!open) {
            if (getStage() != null && getParent() != slot) super.hide(null);
            setVisible(false);
            return;
        }
        Stage stage = getStage();
        if (stage == null) {
            open = false;
            setVisible(false);
            if (!stageLevel && slot != null && getParent() != slot) slot.addActor(this);
            return;
        }
        // Native hide restores focus and removes immediately when action is null.
        super.hide(action);
        open = false;
        if (action == null) {
            setVisible(false);
            if (!stageLevel && slot != null) slot.addActor(this);
        }
        if (!effectivelyVisible(stage.getKeyboardFocus())) stage.setKeyboardFocus(null);
        if (!effectivelyVisible(stage.getScrollFocus())) stage.setScrollFocus(null);
    }

    private static boolean effectivelyVisible(Actor actor) {
        if (actor == null || actor.getStage() == null) return false;
        for (Actor current = actor; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }
}
