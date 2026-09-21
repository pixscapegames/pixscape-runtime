package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Skin-independent TextTooltip style over the existing built-in HUD resources. */
public final class HudBuiltInTextTooltipStyle {
    private HudBuiltInTextTooltipStyle() { }

    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    public static TextTooltip.TextTooltipStyle create(TextureRegion white, Label.LabelStyle label) {
        if (white == null || label == null || label.font == null) {
            throw new IllegalArgumentException("White region and usable Label style are required.");
        }
        Drawable background = new TextureRegionDrawable(white).tint(new Color(.10f, .13f, .17f, .95f));
        TextTooltip.TextTooltipStyle style = new TextTooltip.TextTooltipStyle(
                new Label.LabelStyle(label), background);
        style.wrapWidth = 320f;
        return style;
    }
}
