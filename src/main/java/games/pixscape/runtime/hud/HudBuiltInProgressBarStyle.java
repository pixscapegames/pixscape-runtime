package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;

/** Stable identity and native style for the built-in HUD ProgressBar. */
public final class HudBuiltInProgressBarStyle {
    private HudBuiltInProgressBarStyle() { }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /**
     * Copies the usable non-interactive part of the built-in Slider style without mutating its
     * shared drawables. ProgressBar deliberately needs no knob.
     */
    public static ProgressBar.ProgressBarStyle create(Slider.SliderStyle source) {
        if (!HudStyleUsability.isUsableProgressBarStyle(source)) {
            throw new IllegalArgumentException("A usable Slider style is required.");
        }
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle(source);
        style.knob = null;
        style.disabledKnob = null;
        return style;
    }
}
