package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Stable identity and resources for the built-in HUD ImageButton style. */
public final class HudBuiltInImageButtonStyle {
    /** Shared packed white pixel also used by the built-in TextButton background. */
    public static final String BACKGROUND_REGION = HudBuiltInTextButtonStyle.BACKGROUND_REGION;

    private HudBuiltInImageButtonStyle() {
    }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Creates one independent no-font style over caller-owned packed resources. */
    public static ImageButton.ImageButtonStyle create(TextureRegion background) {
        if (background == null) throw new IllegalArgumentException("Background region is required.");
        ImageButton.ImageButtonStyle style = new ImageButton.ImageButtonStyle();
        style.up = drawable(background, new Color(0.16f, 0.19f, 0.22f, 1f));
        style.over = drawable(background, new Color(0.22f, 0.27f, 0.31f, 1f));
        style.down = drawable(background, new Color(0.09f, 0.12f, 0.14f, 1f));
        style.checked = drawable(background, new Color(0.09f, 0.12f, 0.14f, 1f));
        style.checkedOver = style.over;
        style.disabled = drawable(background, new Color(0.12f, 0.13f, 0.14f, 0.75f));
        return style;
    }

    private static Drawable drawable(TextureRegion region, Color tint) {
        Drawable result = new TextureRegionDrawable(region).tint(tint);
        result.setMinWidth(40f);
        result.setMinHeight(28f);
        result.setLeftWidth(10f);
        result.setRightWidth(10f);
        result.setTopHeight(5f);
        result.setBottomHeight(5f);
        return result;
    }
}
