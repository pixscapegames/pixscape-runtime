package games.pixscape.runtime.hud.document;

import java.util.ArrayList;

/** Typed native Dialog payload. Scene2D Dialog uses WindowStyle. */
public final class HudDialogData extends HudWindowData {
    /** Ordered authored buttons outside the Dialog content grid. */
    public ArrayList<HudDialogResultButton> resultButtons = new ArrayList<HudDialogResultButton>();
    public HudDialogData() {
        title = "Dialog";
        modal = true;
    }
}
