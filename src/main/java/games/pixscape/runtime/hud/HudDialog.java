package games.pixscape.runtime.hud;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Window;

/** Small adapter around native focus/show/hide for one authored Dialog placement. */
final class HudDialog extends Dialog {
    private HudDialogSlot slot;
    private boolean stageLevel;
    private boolean authoredKeepWithinStage;
    private boolean open;

    HudDialog(String title, Window.WindowStyle style) { super(title, style); }

    void attach(HudDialogSlot slot, boolean stageLevel, boolean keepWithinStage) {
        this.slot = slot;
        this.stageLevel = stageLevel;
        this.authoredKeepWithinStage = keepWithinStage;
        setKeepWithinStage(stageLevel && keepWithinStage);
    }

    boolean isOpen() { return open; }

    void open() {
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

    void close() {
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
