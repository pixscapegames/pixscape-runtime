package games.pixscape.runtime.hud.document;

/** Typed native Dialog payload. Scene2D Dialog uses WindowStyle. */
public final class HudDialogData extends HudWindowData {
    public HudDialogData() {
        title = "Dialog";
        modal = true;
    }
}
