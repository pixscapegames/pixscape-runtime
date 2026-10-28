package games.pixscape.runtime.hud.document;

/**
 * One ordered parent-child relationship and its exclusive placement policy.
 *
 * <p>{@link HudPlacementKind#CELL} delegates placement to a native Table/Cell.
 * {@link HudPlacementKind#FREE} applies the bounded parent-relative anchor policy.
 * {@link HudPlacementKind#DIRECT} preserves ordinary native child insertion semantics.</p>
 */
public final class HudChild {
    public HudNode node;
    public HudPlacementKind placementKind = HudPlacementKind.DIRECT;
    public HudCellConstraints cell;
    public HudFreePlacement free;

    /** Required by libGDX Json. */
    public HudChild() {
    }

    public static HudChild direct(HudNode node) {
        return placed(node, HudPlacementKind.DIRECT, null, null);
    }

    public static HudChild cell(HudNode node, HudCellConstraints constraints) {
        if (constraints == null) throw new IllegalArgumentException("HUD Cell constraints are required.");
        return placed(node, HudPlacementKind.CELL, constraints, null);
    }

    public static HudChild free(HudNode node, HudFreePlacement placement) {
        if (placement == null) throw new IllegalArgumentException("HUD free placement is required.");
        return placed(node, HudPlacementKind.FREE, null, placement);
    }

    private static HudChild placed(HudNode node, HudPlacementKind kind,
                                   HudCellConstraints cell, HudFreePlacement free) {
        if (node == null) throw new IllegalArgumentException("HUD child node is required.");
        HudChild child = new HudChild();
        child.node = node;
        child.placementKind = kind;
        child.cell = cell;
        child.free = free;
        return child;
    }
}
