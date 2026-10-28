package games.pixscape.runtime.hud.document;

/**
 * One persistent native Table cell. It is authored data, not a Scene2D Actor.
 * Its optional content is the sole relationship to the contained widget.
 */
public final class HudTableCell {
    public String id;
    public int colspan = 1;
    public HudCellConstraints constraints = new HudCellConstraints();
    public HudNode content;

    /** Required by libGDX Json. */
    public HudTableCell() {
    }
}
