package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Skin-independent native Window style over existing HUD resources. */
public final class HudBuiltInWindowStyle {
    private HudBuiltInWindowStyle() { }

    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    public static Window.WindowStyle create(TextureRegion white, Label.LabelStyle label) {
        if (white == null || label == null || label.font == null) {
            throw new IllegalArgumentException("White region and usable Label style are required.");
        }
        BaseDrawable background = (BaseDrawable) new TextureRegionDrawable(white)
                .tint(new Color(.10f, .13f, .17f, .95f));
        background.setTopHeight(32f);
        background.setBottomHeight(8f);
        background.setLeftWidth(8f);
        background.setRightWidth(8f);
        return new Window.WindowStyle(label.font, Color.WHITE, background);
    }
}
