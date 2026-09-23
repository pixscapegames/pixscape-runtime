package games.pixscape.runtime.hud.document;

import java.util.ArrayList;

/** One ordered logical row in a {@link HudTableLayout}. */
public final class HudTableRow {
    public ArrayList<HudTableCell> cells = new ArrayList<HudTableCell>();

    /** Required by libGDX Json. */
    public HudTableRow() {
    }
}
