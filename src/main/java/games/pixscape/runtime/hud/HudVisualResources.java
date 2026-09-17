package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;

/**
 * Borrowed visual resources needed to materialize a HUD document.
 *
 * <p>Providers retain ownership of the returned objects and remain responsible for their
 * lifetime. A missing named resource is reported by returning {@code null}.</p>
 */
public interface HudVisualResources {
    TextureRegion region(String name);

    Drawable drawable(String name);

    Label.LabelStyle labelStyle(String name);

    /** Skin-independent style used when a Label has no authored custom style reference. */
    default Label.LabelStyle builtInLabelStyle() { return null; }

    TextButton.TextButtonStyle textButtonStyle(String name);
}
