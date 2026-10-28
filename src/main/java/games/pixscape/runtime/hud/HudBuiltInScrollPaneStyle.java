package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Complete native default ScrollPane style over the packed white HUD region. */
public final class HudBuiltInScrollPaneStyle {
    private HudBuiltInScrollPaneStyle() { }

    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    public static ScrollPane.ScrollPaneStyle create(TextureRegion white) {
        if (white == null) throw new IllegalArgumentException("White region is required.");
        ScrollPane.ScrollPaneStyle style = new ScrollPane.ScrollPaneStyle();
        style.background = drawable(white, new Color(.11f, .13f, .16f, 1f), 8f);
        style.vScroll = drawable(white, new Color(.16f, .19f, .22f, 1f), 8f);
        style.hScroll = drawable(white, new Color(.16f, .19f, .22f, 1f), 8f);
        style.vScrollKnob = drawable(white, new Color(.31f, .45f, .58f, 1f), 8f);
        style.hScrollKnob = drawable(white, new Color(.31f, .45f, .58f, 1f), 8f);
        return style;
    }

    private static Drawable drawable(TextureRegion region, Color color, float size) {
        Drawable drawable = new TextureRegionDrawable(region).tint(color);
        drawable.setMinWidth(size);
        drawable.setMinHeight(size);
        return drawable;
    }
}
