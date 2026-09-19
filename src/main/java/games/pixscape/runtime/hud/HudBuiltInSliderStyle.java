package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

/** Stable identity and complete native style for the built-in HUD Slider. */
public final class HudBuiltInSliderStyle {
    public static final String BACKGROUND_REGION = HudBuiltInTextButtonStyle.BACKGROUND_REGION;

    private HudBuiltInSliderStyle() { }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }

    /** Creates an independent SliderStyle over a caller-owned packed white region. */
    public static Slider.SliderStyle create(TextureRegion white) {
        if (white == null) throw new IllegalArgumentException("White region is required.");
        Slider.SliderStyle style = new Slider.SliderStyle();
        style.background = drawable(white, new Color(0.16f, 0.19f, 0.22f, 1f), 6f);
        style.backgroundOver = drawable(white, new Color(0.21f, 0.25f, 0.29f, 1f), 6f);
        style.disabledBackground = drawable(white, new Color(0.11f, 0.12f, 0.14f, 0.75f), 6f);
        style.knob = drawable(white, new Color(0.20f, 0.48f, 0.72f, 1f), 16f);
        style.knobOver = drawable(white, new Color(0.26f, 0.56f, 0.80f, 1f), 16f);
        style.knobDown = drawable(white, new Color(0.15f, 0.40f, 0.65f, 1f), 16f);
        style.disabledKnob = drawable(white, new Color(0.18f, 0.31f, 0.42f, 0.75f), 16f);
        return style;
    }

    private static Drawable drawable(TextureRegion region, Color tint, float size) {
        Drawable drawable = new TextureRegionDrawable(region).tint(tint);
        drawable.setMinWidth(size);
        drawable.setMinHeight(size);
        return drawable;
    }
}
