package com.badlogic.gdx.scenes.scene2d;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.viewport.Viewport;

/**
 * HUD-specific Stage view of Scene2D's authoritative touch-focus collection.
 *
 * <p>Scene2D 1.14.2 exposes no public query for this state. This class does not mirror or
 * manage focus: it only reads the collection maintained by {@link Stage}.</p>
 */
public final class HudTouchFocusStage extends Stage {
    public HudTouchFocusStage(Viewport viewport, Batch batch) {
        super(viewport, batch);
    }

    public boolean hasTouchFocus() {
        return touchFocuses.size > 0;
    }
}
