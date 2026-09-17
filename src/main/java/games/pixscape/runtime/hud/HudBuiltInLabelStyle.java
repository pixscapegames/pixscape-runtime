package games.pixscape.runtime.hud;

/** Stable identity for libGDX's bundled 15pt Liberation Sans Label style. */
public final class HudBuiltInLabelStyle {
    public static final String FONT_DESCRIPTOR = "com/badlogic/gdx/utils/lsans-15.fnt";
    public static final String FONT_PAGE = "com/badlogic/gdx/utils/lsans-15.png";
    public static final String ATLAS_REGION = "__pixscape_builtin_label_font";

    private HudBuiltInLabelStyle() {
    }

    /** A missing authored style reference explicitly selects the built-in style. */
    public static boolean isSelected(String styleName) {
        return styleName == null || styleName.trim().length() == 0;
    }
}
