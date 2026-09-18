package games.pixscape.runtime.hud.document;

/** Typed ImageButton payload with optional native style-image overrides. */
public final class HudImageButtonData {
    /** Null or blank selects the built-in Default style. */
    public String styleName;
    public HudImageData imageUp;
    public HudImageData imageDown;
    public HudImageData imageOver;
    public HudImageData imageDisabled;
    public HudImageData imageChecked;
    public HudImageData imageCheckedDown;
    public HudImageData imageCheckedOver;
}
