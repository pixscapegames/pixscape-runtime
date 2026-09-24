package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.math.Vector2;

/** Stable authored placement while native Dialog.show/hide temporarily reparents its actor. */
final class HudDialogSlot extends WidgetGroup {
    private final HudDialog dialog;
    private final float authoredWidth;
    private final float authoredHeight;
    private final Vector2 stagePosition = new Vector2();

    HudDialogSlot(HudDialog dialog) {
        this.dialog = dialog;
        setTouchable(Touchable.childrenOnly);
        authoredWidth = dialog.getWidth();
        authoredHeight = dialog.getHeight();
        setSize(dialog.getWidth(), dialog.getHeight());
        addActor(dialog);
    }

    @Override public void layout() {
        if (!dialog.isOpen() && dialog.getParent() == this) {
            dialog.setBounds(0f, 0f, getWidth(), getHeight());
        }
    }

    @Override protected void positionChanged() {
        super.positionChanged();
        if (!dialog.isOpen() || dialog.getParent() == this || getStage() == null) return;
        localToStageCoordinates(stagePosition.set(0f, 0f));
        dialog.setPosition(stagePosition.x, stagePosition.y);
    }

    @Override public float getPrefWidth() { return Math.max(authoredWidth, dialog.getPrefWidth()); }
    @Override public float getPrefHeight() { return Math.max(authoredHeight, dialog.getPrefHeight()); }
}
