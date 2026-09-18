package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
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
                        || builtInTextButtonStyle() != null)
                && (!requirements.requiresBuiltInImageButtonStyle()
                        || builtInImageButtonStyle() != null)
                && (!requirements.requiresBuiltInTextFieldStyle()
                        || builtInTextFieldStyle() != null)
                && (!requirements.requiresBuiltInSelectBoxStyle()
                        || builtInSelectBoxStyle() != null)
                && (!requirements.requiresBuiltInCheckBoxStyle()
                        || builtInCheckBoxStyle() != null);
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

    @Override public ImageButton.ImageButtonStyle imageButtonStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, ImageButton.ImageButtonStyle.class);
    }

    @Override public ImageButton.ImageButtonStyle builtInImageButtonStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInImageButtonStyle();
    }

    @Override public TextField.TextFieldStyle textFieldStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, TextField.TextFieldStyle.class);
    }

    @Override public TextField.TextFieldStyle builtInTextFieldStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInTextFieldStyle();
    }

    @Override public SelectBox.SelectBoxStyle selectBoxStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, SelectBox.SelectBoxStyle.class);
    }

    @Override public SelectBox.SelectBoxStyle builtInSelectBoxStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInSelectBoxStyle();
    }

    @Override public CheckBox.CheckBoxStyle checkBoxStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, CheckBox.CheckBoxStyle.class);
    }

    @Override public CheckBox.CheckBoxStyle builtInCheckBoxStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInCheckBoxStyle();
    }

    @Override public boolean hasLabelStyle(String name) { return labelStyle(name) != null; }
    @Override public boolean hasBuiltInLabelStyle() { return builtInLabelStyle() != null; }
    @Override public boolean hasTextButtonStyle(String name) { return textButtonStyle(name) != null; }
    @Override public boolean hasBuiltInTextButtonStyle() { return builtInTextButtonStyle() != null; }
    @Override public boolean hasImageButtonStyle(String name) { return imageButtonStyle(name) != null; }
    @Override public boolean hasBuiltInImageButtonStyle() { return builtInImageButtonStyle() != null; }
    @Override public boolean hasTextFieldStyle(String name) {
        TextField.TextFieldStyle style = textFieldStyle(name);
        return isUsableTextFieldStyle(style);
    }
    @Override public boolean hasBuiltInTextFieldStyle() {
        return isUsableTextFieldStyle(builtInTextFieldStyle());
    }
    @Override public boolean hasSelectBoxStyle(String name) {
        return isUsableSelectBoxStyle(selectBoxStyle(name));
    }
    @Override public boolean hasBuiltInSelectBoxStyle() {
        return isUsableSelectBoxStyle(builtInSelectBoxStyle());
    }
    @Override public boolean hasCheckBoxStyle(String name) {
        return isUsableCheckBoxStyle(checkBoxStyle(name));
    }
    @Override public boolean hasBuiltInCheckBoxStyle() {
        return isUsableCheckBoxStyle(builtInCheckBoxStyle());
    }

    private static boolean isUsableTextFieldStyle(TextField.TextFieldStyle style) {
        return style != null && style.font != null && style.fontColor != null;
    }
    private static boolean isUsableSelectBoxStyle(SelectBox.SelectBoxStyle style) {
        return style != null && style.font != null && style.fontColor != null
                && style.listStyle != null
                && style.listStyle.font != null && style.listStyle.selection != null
                && style.scrollStyle != null;
    }
    private static boolean isUsableCheckBoxStyle(CheckBox.CheckBoxStyle style) {
        return style != null && style.font != null
                && style.checkboxOn != null && style.checkboxOff != null;
    }
}
