package games.pixscape.runtime.hud.document;

/** Typed native Slider payload using a logical Skin style name. */
public final class HudSliderData {
    public HudSliderOrientation orientation = HudSliderOrientation.HORIZONTAL;
    public float min;
    public float max = 100f;
    public float stepSize = 1f;
    public float value = 50f;
    public String styleName;
    public boolean disabled;
}
