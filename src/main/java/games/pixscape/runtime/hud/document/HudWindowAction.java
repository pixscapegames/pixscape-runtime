package games.pixscape.runtime.hud.document;

/** One button-to-Window-or-Dialog association, resolved by authored node ID. */
public final class HudWindowAction {
    public String targetId;
    public HudWindowActionKind action = HudWindowActionKind.SHOW;

    public HudWindowAction() { }

    public HudWindowAction(String targetId, HudWindowActionKind action) {
        this.targetId = targetId;
        this.action = action;
    }
}
