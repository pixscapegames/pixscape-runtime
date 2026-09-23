package games.pixscape.runtime.hud.document;

/**
 * Persisted subset of native Scene2D Cell construction constraints.
 * Values describe the relationship between the containing Table and this child, not Actor state.
 */
public final class HudCellConstraints {
    /** {@code null} leaves the native Scene2D minimum width unchanged. */
    public Float minWidth;
    /** {@code null} leaves the native Scene2D minimum height unchanged. */
    public Float minHeight;
    /** {@code null} leaves the native Scene2D preferred width unchanged. */
    public Float prefWidth;
    /** {@code null} leaves the native Scene2D preferred height unchanged. */
    public Float prefHeight;
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
}
