package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Stable identity and complete native style for the built-in HUD CheckBox. */
public final class HudBuiltInCheckBoxStyle {
    public static final String BACKGROUND_REGION = HudBuiltInTextButtonStyle.BACKGROUND_REGION;

    private HudBuiltInCheckBoxStyle() { }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Creates an independent CheckBoxStyle over caller-owned packed resources. */
    public static CheckBox.CheckBoxStyle create(TextureRegion white, BitmapFont font) {
        if (white == null) throw new IllegalArgumentException("White region is required.");
        if (font == null) throw new IllegalArgumentException("BitmapFont is required.");
        CheckBox.CheckBoxStyle style = new CheckBox.CheckBoxStyle();
        style.font = font;
        style.fontColor = new Color(Color.WHITE);
        style.disabledFontColor = new Color(0.55f, 0.58f, 0.61f, 1f);
        style.checkboxOff = box(white, new Color(0.13f, 0.16f, 0.19f, 1f));
        style.checkboxOn = box(white, new Color(0.20f, 0.48f, 0.72f, 1f));
        style.checkboxOver = box(white, new Color(0.26f, 0.56f, 0.80f, 1f));
        style.checkboxOffDisabled = box(white, new Color(0.10f, 0.11f, 0.12f, 0.75f));
        style.checkboxOnDisabled = box(white, new Color(0.18f, 0.31f, 0.42f, 0.75f));
        return style;
    }

    private static Drawable box(TextureRegion region, Color tint) {
        Drawable result = new TextureRegionDrawable(region).tint(tint);
        result.setMinWidth(18f);
        result.setMinHeight(18f);
        return result;
    }
}
