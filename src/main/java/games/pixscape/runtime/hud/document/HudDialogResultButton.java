package games.pixscape.runtime.hud.document;

/** One ordered authored button in a Dialog's native button table. */
public final class HudDialogResultButton {
    public HudNode button;
    public String resultId;
    public boolean closeAfterActivation = true;

    public HudDialogResultButton() {
    }

    public HudDialogResultButton(HudNode button, String resultId, boolean closeAfterActivation) {
        this.button = button;
        this.resultId = resultId;
        this.closeAfterActivation = closeAfterActivation;
    }
}
