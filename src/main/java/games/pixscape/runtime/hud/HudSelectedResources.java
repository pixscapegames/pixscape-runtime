package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import games.pixscape.runtime.hud.document.HudResourceCatalog;

/**
 * Non-owning, immutable selection into an owning HUD environment. All lookups are O(1) average,
 * including misses. Skin names resolve only within the selected namespace; regions are shared.
 * The owner must outlive every view and session. This type has no disposal operation.
 */
public final class HudSelectedResources implements HudResourceCatalog, HudVisualResources {
    private final HudResources owner;
    private final String skinId;
    private final Skin skin;

    HudSelectedResources(HudResources owner, String skinId, Skin skin) {
        this.owner = owner;
        this.skinId = skinId;
        this.skin = skin;
    }

    public String skinId() {
        owner.requireOpen();
        return skinId;
    }

    /** Category check; document validation additionally resolves every required name in this view. */
    public boolean satisfies(HudResourceRequirements requirements) {
        owner.requireOpen();
        if (requirements == null) throw new IllegalArgumentException("HudResourceRequirements is required.");
        return (!requirements.requiresSkin() || skin != null)
                && (!requirements.requiresAtlas() || owner.textureArrayBundle() != null)
                && (!requirements.requiresBuiltInLabelStyle() || builtInLabelStyle() != null)
                && (!requirements.requiresBuiltInTextButtonStyle()
                        || builtInTextButtonStyle() != null);
    }

    Skin skin() {
        owner.requireOpen();
        return skin;
    }

    @Override public TextureRegion region(String name) { return owner.sharedRegion(name); }
    @Override public boolean hasRegion(String name) { return region(name) != null; }

    @Override public Drawable drawable(String name) {
        owner.requireOpen();
        return hasDrawable(name) ? skin.getDrawable(name) : null;
    }

    @Override public boolean hasDrawable(String name) {
        owner.requireOpen();
        return skin != null && (skin.has(name, Drawable.class)
                || skin.has(name, TextureRegion.class)
                || skin.has(name, com.badlogic.gdx.graphics.g2d.NinePatch.class)
                || skin.has(name, com.badlogic.gdx.graphics.g2d.Sprite.class));
    }

    @Override public Label.LabelStyle labelStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, Label.LabelStyle.class);
    }

    @Override public Label.LabelStyle builtInLabelStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInLabelStyle();
    }

    @Override public TextButton.TextButtonStyle textButtonStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, TextButton.TextButtonStyle.class);
    }

    @Override public TextButton.TextButtonStyle builtInTextButtonStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInTextButtonStyle();
    }

    @Override public boolean hasLabelStyle(String name) { return labelStyle(name) != null; }
    @Override public boolean hasBuiltInLabelStyle() { return builtInLabelStyle() != null; }
    @Override public boolean hasTextButtonStyle(String name) { return textButtonStyle(name) != null; }
    @Override public boolean hasBuiltInTextButtonStyle() { return builtInTextButtonStyle() != null; }
}
