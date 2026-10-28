package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Stable identity for the built-in HUD TextField style. */
public final class HudBuiltInTextFieldStyle {
    public static final String BACKGROUND_REGION = HudBuiltInTextButtonStyle.BACKGROUND_REGION;

    private HudBuiltInTextFieldStyle() {
    }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Creates one independent style over caller-owned packed resources. */
    public static TextField.TextFieldStyle create(TextureRegion white, BitmapFont font) {
        if (white == null) throw new IllegalArgumentException("White region is required.");
        if (font == null) throw new IllegalArgumentException("BitmapFont is required.");

        Drawable cursor = drawable(white, new Color(0.92f, 0.95f, 0.98f, 1f));
        cursor.setMinWidth(2f);
        Drawable selection = drawable(white, new Color(0.20f, 0.48f, 0.72f, 0.75f));
        Drawable background = drawable(white, new Color(0.10f, 0.12f, 0.14f, 1f));
        background.setMinWidth(96f);
        background.setMinHeight(28f);
        background.setLeftWidth(7f);
        background.setRightWidth(7f);
        background.setTopHeight(5f);
        background.setBottomHeight(5f);

        TextField.TextFieldStyle style = new TextField.TextFieldStyle(
                font, new Color(Color.WHITE), cursor, selection, background);
        style.messageFontColor = new Color(0.58f, 0.62f, 0.66f, 1f);
        return style;
    }

    private static Drawable drawable(TextureRegion region, Color tint) {
        return new TextureRegionDrawable(region).tint(tint);
    }
}
