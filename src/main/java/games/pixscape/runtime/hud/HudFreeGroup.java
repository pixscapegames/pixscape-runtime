package games.pixscape.runtime.hud;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SnapshotArray;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudHorizontalAnchor;
import games.pixscape.runtime.hud.document.HudVerticalAnchor;

/** Thin Scene2D layout surface for immediate-parent FREE placements. */
final class HudFreeGroup extends WidgetGroup {
    private final ObjectMap<Actor, Placement> freePlacements =
            new ObjectMap<Actor, Placement>();
    private final float authoredPrefWidth;
    private final float authoredPrefHeight;

    HudFreeGroup(float authoredPrefWidth, float authoredPrefHeight) {
        this.authoredPrefWidth = authoredPrefWidth;
        this.authoredPrefHeight = authoredPrefHeight;
    }

    void addFreeActor(Actor actor, HudFreePlacement free) {
        super.addActor(actor);
        freePlacements.put(actor, new Placement(free));
        invalidate();
    }

    @Override
    public void layout() {
        SnapshotArray<Actor> children = getChildren();
        for (int i = 0; i < children.size; i++) {
            Actor child = children.get(i);
            Placement placement = freePlacements.get(child);
            if (placement == null) continue;

            float anchorX = placement.horizontalAnchor == HudHorizontalAnchor.LEFT ? 0f
                    : placement.horizontalAnchor == HudHorizontalAnchor.CENTER ? getWidth() / 2f
                    : getWidth();
            float anchorY = placement.verticalAnchor == HudVerticalAnchor.BOTTOM ? 0f
                    : placement.verticalAnchor == HudVerticalAnchor.CENTER ? getHeight() / 2f
                    : getHeight();
            child.setPosition(
                    anchorX + placement.offsetX - child.getWidth() * placement.pivotX,
                    anchorY + placement.offsetY - child.getHeight() * placement.pivotY);
        }
    }

    @Override
    public float getPrefWidth() {
        return authoredPrefWidth;
    }

    @Override
    public float getPrefHeight() {
        return authoredPrefHeight;
    }

    @Override
    public boolean removeActor(Actor actor, boolean unfocus) {
        boolean removed = super.removeActor(actor, unfocus);
        if (removed) freePlacements.remove(actor);
        return removed;
    }

    @Override
    public Actor removeActorAt(int index, boolean unfocus) {
        Actor removed = super.removeActorAt(index, unfocus);
        if (removed != null) freePlacements.remove(removed);
        return removed;
    }

    @Override
    public void clearChildren(boolean unfocus) {
        super.clearChildren(unfocus);
        freePlacements.clear();
    }

    private static final class Placement {
        private final HudHorizontalAnchor horizontalAnchor;
        private final HudVerticalAnchor verticalAnchor;
        private final float pivotX;
        private final float pivotY;
        private final float offsetX;
        private final float offsetY;

        Placement(HudFreePlacement source) {
            horizontalAnchor = source.horizontalAnchor;
            verticalAnchor = source.verticalAnchor;
            pivotX = source.pivotX;
            pivotY = source.pivotY;
            offsetX = source.offsetX;
            offsetY = source.offsetY;
        }
    }
}
