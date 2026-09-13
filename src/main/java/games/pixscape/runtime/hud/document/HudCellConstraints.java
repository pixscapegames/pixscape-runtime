package games.pixscape.runtime.hud.document;

/**
 * Persisted subset of native Scene2D Cell construction constraints.
 * Values describe the relationship between the containing Table and this child, not Actor state.
 */
public final class HudCellConstraints {
    public float minWidth;
    public float minHeight;
    public float prefWidth;
    public float prefHeight;
    public float padTop;
    public float padRight;
    public float padBottom;
    public float padLeft;
    public boolean fillX;
    public boolean fillY;
    public boolean expandX;
    public boolean expandY;
    public HudHorizontalAlign horizontalAlign = HudHorizontalAlign.CENTER;
    public HudVerticalAlign verticalAlign = HudVerticalAlign.CENTER;
    /** Starts a new native Table row after this child. */
    public boolean rowAfter;
}
