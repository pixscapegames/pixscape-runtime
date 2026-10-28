package games.pixscape.runtime.hud.document;

import java.util.ArrayList;

/** Authored values and initial selection of a native Scene2D List of strings. */
public final class HudListData {
    /** Ordered, non-null labels. Equal labels are allowed. */
    public ArrayList<String> items = new ArrayList<String>();
    /** -1 means no initial selection. */
    public int selectedIndex = -1;
    /** When true, a nonempty list requires an initial selection. */
    public boolean required = true;
    public String styleName;
    public Integer fontAssetId;
}
