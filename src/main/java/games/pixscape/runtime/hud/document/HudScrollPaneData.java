package games.pixscape.runtime.hud.document;

/** Typed native ScrollPane payload; transient scroll position remains owned by Scene2D. */
public final class HudScrollPaneData {
    public String styleName;
    public boolean scrollingDisabledX;
    public boolean scrollingDisabledY;
    public boolean fadeScrollBars = true;
    public boolean flickScroll = true;
    public boolean smoothScrolling = true;
    public boolean overscrollX = true;
    public boolean overscrollY = true;
}
