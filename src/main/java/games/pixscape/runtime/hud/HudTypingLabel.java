package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.github.tommyettinger.textra.Styles;
import com.github.tommyettinger.textra.TypingLabel;

/** Typing labels do not advance or recompute their layout while absent from the visible HUD tree. */
final class HudTypingLabel extends TypingLabel {
    HudTypingLabel(String text, Styles.LabelStyle style) {
        super(text, style);
    }

    @Override
    public void act(float delta) {
        if (!isEffectivelyVisible() && getActions().size == 0) return;
        super.act(delta);
    }

    private boolean isEffectivelyVisible() {
        for (Actor current = this; current != null; current = current.getParent()) {
            if (!current.isVisible()) return false;
        }
        return true;
    }
}
