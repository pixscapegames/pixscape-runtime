package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Stable identity and complete native style for the built-in HUD SelectBox. */
public final class HudBuiltInSelectBoxStyle {
    public static final String BACKGROUND_REGION = HudBuiltInTextButtonStyle.BACKGROUND_REGION;

    private HudBuiltInSelectBoxStyle() { }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Creates an independent complete SelectBox/List/ScrollPane style over caller-owned resources. */
    public static SelectBox.SelectBoxStyle create(TextureRegion white, BitmapFont font) {
        if (white == null) throw new IllegalArgumentException("White region is required.");
        if (font == null) throw new IllegalArgumentException("BitmapFont is required.");
        Drawable background = box(white, new Color(0.16f, 0.19f, 0.22f, 1f));
        Drawable over = box(white, new Color(0.22f, 0.27f, 0.31f, 1f));
        Drawable open = box(white, new Color(0.10f, 0.13f, 0.16f, 1f));
        Drawable disabled = box(white, new Color(0.12f, 0.13f, 0.14f, 0.75f));
        Drawable listBackground = box(white, new Color(0.08f, 0.10f, 0.12f, 1f));
        Drawable selection = box(white, new Color(0.20f, 0.48f, 0.72f, 1f));
        Drawable scroll = box(white, new Color(0.12f, 0.14f, 0.17f, 1f));
        Drawable scrollKnob = box(white, new Color(0.40f, 0.46f, 0.52f, 1f));
        scroll.setMinWidth(8f);
        scrollKnob.setMinWidth(8f);
        List.ListStyle listStyle = new List.ListStyle(font, new Color(Color.WHITE),
                new Color(0.84f, 0.88f, 0.92f, 1f), selection);
        listStyle.background = listBackground;
        listStyle.over = over;
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = listBackground;
        scrollStyle.vScroll = scroll;
        scrollStyle.vScrollKnob = scrollKnob;
        SelectBox.SelectBoxStyle style = new SelectBox.SelectBoxStyle(font,
                new Color(Color.WHITE), background, scrollStyle, listStyle);
        style.backgroundOver = over;
        style.backgroundOpen = open;
        style.backgroundDisabled = disabled;
        style.overFontColor = new Color(Color.WHITE);
        style.disabledFontColor = new Color(0.55f, 0.58f, 0.61f, 1f);
        return style;
    }

    private static Drawable box(TextureRegion region, Color tint) {
        Drawable result = new TextureRegionDrawable(region).tint(tint);
        result.setMinWidth(96f);
        result.setMinHeight(28f);
        result.setLeftWidth(8f);
        result.setRightWidth(14f);
        result.setTopHeight(5f);
        result.setBottomHeight(5f);
        return result;
    }
}
