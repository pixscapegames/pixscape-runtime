package games.pixscape.runtime.hud;

import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageReferences;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudFontReferences;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * GL-free resource categories required to materialize one validated HUD document.
 *
 * <p>Skin-backed V1 actors also require the authored atlas because their textures must belong to
 * the HUD texture array consumed by {@code HudBatch}. A REGION image needs only that atlas.</p>
 */
public final class HudResourceRequirements {
    private final boolean skin;
    private final boolean atlas;
    private final boolean builtInLabelStyle;
    private final boolean builtInTextButtonStyle;
    private final boolean builtInImageButtonStyle;
    private final boolean builtInImageTextButtonStyle;
    private final boolean builtInTextFieldStyle;
    private final boolean builtInSelectBoxStyle;
    private final boolean builtInCheckBoxStyle;
    private final boolean builtInSliderStyle;
    private final boolean builtInProgressBarStyle;
    private final boolean builtInScrollPaneStyle;
    private final Set<Integer> bitmapFontAssetIds;

    private HudResourceRequirements(boolean skin, boolean atlas, boolean builtInLabelStyle,
                                    boolean builtInTextButtonStyle, boolean builtInImageButtonStyle,
                                    boolean builtInImageTextButtonStyle,
                                    boolean builtInTextFieldStyle, boolean builtInSelectBoxStyle,
                                    boolean builtInCheckBoxStyle,
                                    boolean builtInSliderStyle, boolean builtInProgressBarStyle,
                                    boolean builtInScrollPaneStyle,
                                    Set<Integer> bitmapFontAssetIds) {
        this.skin = skin;
        this.atlas = atlas;
        this.builtInLabelStyle = builtInLabelStyle;
        this.builtInTextButtonStyle = builtInTextButtonStyle;
        this.builtInImageButtonStyle = builtInImageButtonStyle;
        this.builtInImageTextButtonStyle = builtInImageTextButtonStyle;
        this.builtInTextFieldStyle = builtInTextFieldStyle;
        this.builtInSelectBoxStyle = builtInSelectBoxStyle;
        this.builtInCheckBoxStyle = builtInCheckBoxStyle;
        this.builtInSliderStyle = builtInSliderStyle;
        this.builtInProgressBarStyle = builtInProgressBarStyle;
        this.builtInScrollPaneStyle = builtInScrollPaneStyle;
        this.bitmapFontAssetIds = Collections.unmodifiableSet(
                new LinkedHashSet<Integer>(bitmapFontAssetIds));
    }

    /** Derives the complete requirement union in one cold-path traversal of validated nodes. */
    public static HudResourceRequirements from(ValidatedHudDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("ValidatedHudDocument is required.");
        }
        boolean requiresSkin = false;
        boolean requiresAtlas = false;
        boolean requiresBuiltInLabelStyle = false;
        boolean requiresBuiltInTextButtonStyle = false;
        boolean requiresBuiltInImageButtonStyle = false;
        boolean requiresBuiltInImageTextButtonStyle = false;
        boolean requiresBuiltInTextFieldStyle = false;
        boolean requiresBuiltInSelectBoxStyle = false;
        boolean requiresBuiltInCheckBoxStyle = false;
        boolean requiresBuiltInSliderStyle = false;
        boolean requiresBuiltInProgressBarStyle = false;
        boolean requiresBuiltInScrollPaneStyle = false;
        Set<Integer> bitmapFontAssetIds = new LinkedHashSet<Integer>();
        SkinRequirementVisitor imageRequirements = new SkinRequirementVisitor();
        for (HudNode node : document.nodeIndex().values()) {
            HudNodeKind kind = node.kind;
            Integer fontAssetId = HudFontReferences.assetId(node);
            if (fontAssetId != null) bitmapFontAssetIds.add(fontAssetId);
            if (kind == HudNodeKind.LABEL) {
                requiresAtlas = true;
                if (HudBuiltInLabelStyle.isSelected(node.label.styleName)) {
                    requiresBuiltInLabelStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.TEXTRA_LABEL) {
                requiresAtlas = true;
                if (HudBuiltInLabelStyle.isSelected(node.textraLabel.styleName)) {
                    requiresBuiltInLabelStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.TEXT_BUTTON) {
                requiresAtlas = true;
                if (HudBuiltInTextButtonStyle.isSelected(node.textButton.styleName)) {
                    requiresBuiltInLabelStyle = true;
                    requiresBuiltInTextButtonStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.IMAGE) {
                requiresAtlas = true;
            } else if (kind == HudNodeKind.IMAGE_BUTTON) {
                requiresAtlas = true;
                if (HudBuiltInImageButtonStyle.isSelected(node.imageButton.styleName)) {
                    requiresBuiltInImageButtonStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.IMAGE_TEXT_BUTTON) {
                requiresAtlas = true;
                if (HudBuiltInImageTextButtonStyle.isSelected(node.imageTextButton.styleName)) {
                    requiresBuiltInLabelStyle = true;
                    requiresBuiltInTextButtonStyle = true;
                    requiresBuiltInImageTextButtonStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.TEXT_FIELD) {
                requiresAtlas = true;
                if (HudBuiltInTextFieldStyle.isSelected(node.textField.styleName)) {
                    requiresBuiltInLabelStyle = true;
                    requiresBuiltInTextFieldStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.SELECT_BOX) {
                requiresAtlas = true;
                if (HudBuiltInSelectBoxStyle.isSelected(node.selectBox.styleName)) {
                    requiresBuiltInLabelStyle = true;
                    requiresBuiltInSelectBoxStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.CHECK_BOX) {
                requiresAtlas = true;
                if (HudBuiltInCheckBoxStyle.isSelected(node.checkBox.styleName)) {
                    requiresBuiltInLabelStyle = true;
                    requiresBuiltInCheckBoxStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.SLIDER) {
                requiresAtlas = true;
                if (HudBuiltInSliderStyle.isSelected(node.slider.styleName)) {
                    requiresBuiltInSliderStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.PROGRESS_BAR) {
                requiresAtlas = true;
                if (HudBuiltInProgressBarStyle.isSelected(node.progressBar.styleName)) {
                    requiresBuiltInProgressBarStyle = true;
                } else {
                    requiresSkin = true;
                }
            } else if (kind == HudNodeKind.SCROLL_PANE) {
                requiresAtlas = true;
                if (HudBuiltInScrollPaneStyle.isSelected(node.scrollPane.styleName)) {
                    requiresBuiltInScrollPaneStyle = true;
                } else {
                    requiresSkin = true;
                }
            }
            HudImageReferences.visit(node, imageRequirements);
        }
        requiresSkin |= imageRequirements.requiresSkin;
        return new HudResourceRequirements(requiresSkin, requiresAtlas,
                requiresBuiltInLabelStyle, requiresBuiltInTextButtonStyle,
                requiresBuiltInImageButtonStyle, requiresBuiltInImageTextButtonStyle,
                requiresBuiltInTextFieldStyle,
                requiresBuiltInSelectBoxStyle, requiresBuiltInCheckBoxStyle,
                requiresBuiltInSliderStyle, requiresBuiltInProgressBarStyle,
                requiresBuiltInScrollPaneStyle,
                bitmapFontAssetIds);
    }

    public boolean requiresSkin() {
        return skin;
    }

    public boolean requiresAtlas() {
        return atlas;
    }

    public boolean requiresBuiltInLabelStyle() {
        return builtInLabelStyle;
    }

    public boolean requiresBuiltInTextButtonStyle() {
        return builtInTextButtonStyle;
    }

    public boolean requiresBuiltInImageButtonStyle() {
        return builtInImageButtonStyle;
    }

    public boolean requiresBuiltInImageTextButtonStyle() {
        return builtInImageTextButtonStyle;
    }

    public boolean requiresBuiltInTextFieldStyle() {
        return builtInTextFieldStyle;
    }

    public boolean requiresBuiltInSelectBoxStyle() {
        return builtInSelectBoxStyle;
    }

    public boolean requiresBuiltInCheckBoxStyle() {
        return builtInCheckBoxStyle;
    }

    public boolean requiresBuiltInSliderStyle() {
        return builtInSliderStyle;
    }

    public boolean requiresBuiltInProgressBarStyle() {
        return builtInProgressBarStyle;
    }

    public boolean requiresBuiltInScrollPaneStyle() {
        return builtInScrollPaneStyle;
    }

    public Set<Integer> bitmapFontAssetIds() {
        return bitmapFontAssetIds;
    }

    private static final class SkinRequirementVisitor implements HudImageReferences.Visitor {
        boolean requiresSkin;

        @Override
        public void visit(HudNode node, String fieldPath, HudImageData image) {
            if (image.source == HudImageSource.DRAWABLE) requiresSkin = true;
        }
    }
}
