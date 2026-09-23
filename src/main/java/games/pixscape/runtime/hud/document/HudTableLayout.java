package games.pixscape.runtime.hud.document;

import java.util.ArrayList;

/** Explicit authored grid for the content of a native Scene2D Table. */
public final class HudTableLayout {
    /** Authoritative logical column count. Cell columns are derived from row order and colspan. */
    public int columns;
    public ArrayList<HudTableRow> rows = new ArrayList<HudTableRow>();

    /** Required by libGDX Json. */
    public HudTableLayout() {
    }
}
