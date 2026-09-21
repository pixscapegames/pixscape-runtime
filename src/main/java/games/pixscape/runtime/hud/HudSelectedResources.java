package games.pixscape.runtime.hud;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.github.tommyettinger.textra.Font;
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
                && (!requirements.requiresBuiltInImageTextButtonStyle()
                        || builtInImageTextButtonStyle() != null)
                && (!requirements.requiresBuiltInTextFieldStyle()
                        || builtInTextFieldStyle() != null)
                && (!requirements.requiresBuiltInSelectBoxStyle()
                        || builtInSelectBoxStyle() != null)
                && (!requirements.requiresBuiltInCheckBoxStyle()
                        || builtInCheckBoxStyle() != null)
                && (!requirements.requiresBuiltInSliderStyle()
                        || builtInSliderStyle() != null)
                && (!requirements.requiresBuiltInProgressBarStyle()
                        || builtInProgressBarStyle() != null)
                && (!requirements.requiresBuiltInScrollPaneStyle()
                        || builtInScrollPaneStyle() != null)
                && (!requirements.requiresBuiltInTextTooltipStyle()
                        || builtInTextTooltipStyle() != null)
                && owner.hasBitmapFonts(requirements.bitmapFontAssetIds());
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

    @Override public BitmapFont bitmapFont(int assetId) {
        return owner.sharedBitmapFont(assetId);
    }

    @Override public Font textraFont(BitmapFont bitmapFont) {
        return owner.sharedTextraFont(bitmapFont);
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

    @Override public ImageTextButton.ImageTextButtonStyle imageTextButtonStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, ImageTextButton.ImageTextButtonStyle.class);
    }

    @Override public ImageTextButton.ImageTextButtonStyle builtInImageTextButtonStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInImageTextButtonStyle();
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

    @Override public Slider.SliderStyle sliderStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, Slider.SliderStyle.class);
    }

    @Override public Slider.SliderStyle builtInSliderStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInSliderStyle();
    }

    @Override public ProgressBar.ProgressBarStyle progressBarStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, ProgressBar.ProgressBarStyle.class);
    }

    @Override public ProgressBar.ProgressBarStyle builtInProgressBarStyle() {
        owner.requireOpen();
        return owner.sharedBuiltInProgressBarStyle();
    }

    @Override public ScrollPane.ScrollPaneStyle scrollPaneStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, ScrollPane.ScrollPaneStyle.class);
    }

    @Override public ScrollPane.ScrollPaneStyle builtInScrollPaneStyle() {
        owner.requireOpen(); return owner.sharedBuiltInScrollPaneStyle();
    }

    @Override public TextTooltip.TextTooltipStyle textTooltipStyle(String name) {
        owner.requireOpen();
        return skin == null ? null : skin.optional(name, TextTooltip.TextTooltipStyle.class);
    }

    @Override public TextTooltip.TextTooltipStyle builtInTextTooltipStyle() {
        owner.requireOpen(); return owner.sharedBuiltInTextTooltipStyle();
    }

    @Override public boolean hasLabelStyle(String name) { return labelStyle(name) != null; }
    @Override public boolean hasLabelStyleFont(String name) {
        return HudStyleUsability.isUsableLabelStyle(labelStyle(name), false);
    }
    @Override public boolean hasBitmapFont(int assetId) { return bitmapFont(assetId) != null; }
    @Override public boolean hasBuiltInLabelStyle() { return builtInLabelStyle() != null; }
    @Override public boolean hasTextButtonStyle(String name) {
        return hasTextButtonStyle(name, false);
    }
    @Override public boolean hasTextButtonStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableTextButtonStyle(textButtonStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInTextButtonStyle() { return builtInTextButtonStyle() != null; }
    @Override public boolean hasImageButtonStyle(String name) { return imageButtonStyle(name) != null; }
    @Override public boolean hasBuiltInImageButtonStyle() { return builtInImageButtonStyle() != null; }
    @Override public boolean hasImageTextButtonStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableImageTextButtonStyle(imageTextButtonStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInImageTextButtonStyle() {
        return HudStyleUsability.isUsableImageTextButtonStyle(builtInImageTextButtonStyle(), false);
    }
    @Override public boolean hasTextFieldStyle(String name) {
        return hasTextFieldStyle(name, false);
    }
    @Override public boolean hasTextFieldStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableTextFieldStyle(textFieldStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInTextFieldStyle() {
        return HudStyleUsability.isUsableTextFieldStyle(builtInTextFieldStyle(), false);
    }
    @Override public boolean hasSelectBoxStyle(String name) {
        return hasSelectBoxStyle(name, false);
    }
    @Override public boolean hasSelectBoxStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableSelectBoxStyle(selectBoxStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInSelectBoxStyle() {
        return HudStyleUsability.isUsableSelectBoxStyle(builtInSelectBoxStyle(), false);
    }
    @Override public boolean hasCheckBoxStyle(String name) {
        return hasCheckBoxStyle(name, false);
    }
    @Override public boolean hasCheckBoxStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableCheckBoxStyle(checkBoxStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInCheckBoxStyle() {
        return HudStyleUsability.isUsableCheckBoxStyle(builtInCheckBoxStyle(), false);
    }
    @Override public boolean hasSliderStyle(String name) {
        return HudStyleUsability.isUsableSliderStyle(sliderStyle(name));
    }
    @Override public boolean hasBuiltInSliderStyle() {
        return HudStyleUsability.isUsableSliderStyle(builtInSliderStyle());
    }
    @Override public boolean hasProgressBarStyle(String name) {
        return HudStyleUsability.isUsableProgressBarStyle(progressBarStyle(name));
    }
    @Override public boolean hasBuiltInProgressBarStyle() {
        return HudStyleUsability.isUsableProgressBarStyle(builtInProgressBarStyle());
    }
    @Override public boolean hasScrollPaneStyle(String name) {
        return HudStyleUsability.isUsableScrollPaneStyle(scrollPaneStyle(name));
    }
    @Override public boolean hasBuiltInScrollPaneStyle() {
        return HudStyleUsability.isUsableScrollPaneStyle(builtInScrollPaneStyle());
    }
    @Override public boolean hasTextTooltipStyle(String name, boolean hasFontOverride) {
        return HudStyleUsability.isUsableTextTooltipStyle(textTooltipStyle(name), hasFontOverride);
    }
    @Override public boolean hasBuiltInTextTooltipStyle() {
        return HudStyleUsability.isUsableTextTooltipStyle(builtInTextTooltipStyle(), false);
    }
}
