package games.pixscape.runtime.hud.document;

import java.util.ArrayList;

/** Typed native SelectBox payload using strings as its Scene2D values. */
public final class HudSelectBoxData {
    /** Ordered, non-null, distinct text values. */
    public ArrayList<String> items = new ArrayList<String>();
    /** -1 for an empty list, otherwise an index in {@link #items}. */
    public int selectedIndex;
    public String styleName;
    /** Zero means unlimited. */
    public int maxListCount;
    public boolean disabled;
}
