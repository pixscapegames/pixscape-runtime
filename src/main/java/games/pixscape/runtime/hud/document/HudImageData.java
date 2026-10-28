package games.pixscape.runtime.hud.document;

/** Typed Image payload containing only a logical Skin/atlas resource reference. */
public final class HudImageData {
    public HudImageSource source = HudImageSource.REGION;
    public String resourceName;
}
