package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

/** Stable identity for the built-in HUD ImageTextButton style. */
public final class HudBuiltInImageTextButtonStyle {
    private HudBuiltInImageTextButtonStyle() {
    }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Reuses the complete built-in TextButton visual vocabulary without owning its resources. */
    public static ImageTextButton.ImageTextButtonStyle create(TextButton.TextButtonStyle source) {
        if (source == null || source.font == null) {
            throw new IllegalArgumentException("Usable TextButton style is required.");
        }
        return new ImageTextButton.ImageTextButtonStyle(source);
    }
}
