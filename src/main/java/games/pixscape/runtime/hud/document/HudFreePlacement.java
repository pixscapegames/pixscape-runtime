package games.pixscape.runtime.hud.document;

/**
 * Bounded free-layout placement relative to the immediate free-layout parent.
 *
 * <p>The anchor selects a point on the parent bounds. {@code pivotX} and {@code pivotY} select a
 * normalized point on the child bounds, where 0 is the left/bottom edge and 1 is the right/top
 * edge. Offsets are added in HUD reference units. V1 has no peer target or constraint graph.</p>
 */
public final class HudFreePlacement {
    public HudHorizontalAnchor horizontalAnchor = HudHorizontalAnchor.LEFT;
    public HudVerticalAnchor verticalAnchor = HudVerticalAnchor.BOTTOM;
    public float pivotX;
    public float pivotY;
    public float offsetX;
    public float offsetY;
}
