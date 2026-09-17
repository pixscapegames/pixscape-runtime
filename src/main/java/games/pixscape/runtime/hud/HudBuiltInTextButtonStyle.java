package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Stable identity for the built-in HUD TextButton style. */
public final class HudBuiltInTextButtonStyle {
    public static final String BACKGROUND_REGION =
            "__pixscape_internal__/__ps_internal_white_px";

    private HudBuiltInTextButtonStyle() {
    }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Creates one independent style over caller-owned packed resources. */
    public static TextButton.TextButtonStyle create(TextureRegion background, BitmapFont font) {
        if (background == null) throw new IllegalArgumentException("Background region is required.");
        if (font == null) throw new IllegalArgumentException("BitmapFont is required.");
        Drawable up = drawable(background, new Color(0.16f, 0.19f, 0.22f, 1f));
        Drawable over = drawable(background, new Color(0.22f, 0.27f, 0.31f, 1f));
        Drawable down = drawable(background, new Color(0.09f, 0.12f, 0.14f, 1f));
        Drawable disabled = drawable(background, new Color(0.12f, 0.13f, 0.14f, 0.75f));
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle(up, down, down, font);
        style.over = over;
        style.checkedOver = over;
        style.disabled = disabled;
        style.fontColor = new Color(Color.WHITE);
        style.overFontColor = new Color(Color.WHITE);
        style.downFontColor = new Color(0.88f, 0.91f, 0.94f, 1f);
        style.checkedFontColor = new Color(Color.WHITE);
        style.disabledFontColor = new Color(0.55f, 0.58f, 0.61f, 1f);
        return style;
    }

    private static Drawable drawable(TextureRegion region, Color tint) {
        Drawable result = new TextureRegionDrawable(region).tint(tint);
        result.setMinWidth(72f);
        result.setMinHeight(28f);
        result.setLeftWidth(10f);
        result.setRightWidth(10f);
        result.setTopHeight(5f);
        result.setBottomHeight(5f);
        return result;
    }
}
