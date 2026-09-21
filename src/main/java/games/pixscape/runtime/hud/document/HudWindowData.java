package games.pixscape.runtime.hud.document;

/** Typed native Window payload; preview position and size remain Scene2D state. */
public final class HudWindowData {
    public String title = "Window";
    public String styleName;
    public Integer fontAssetId;
    public boolean movable = true;
    public boolean resizable;
    public boolean modal;
    public boolean keepWithinStage = true;
}
