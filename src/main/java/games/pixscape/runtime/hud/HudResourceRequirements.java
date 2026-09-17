package games.pixscape.runtime.hud;

import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.ValidatedHudDocument;

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

    private HudResourceRequirements(boolean skin, boolean atlas, boolean builtInLabelStyle,
                                    boolean builtInTextButtonStyle) {
        this.skin = skin;
        this.atlas = atlas;
        this.builtInLabelStyle = builtInLabelStyle;
        this.builtInTextButtonStyle = builtInTextButtonStyle;
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
        for (HudNode node : document.nodeIndex().values()) {
            HudNodeKind kind = node.kind;
            if (kind == HudNodeKind.LABEL) {
                requiresAtlas = true;
                if (HudBuiltInLabelStyle.isSelected(node.label.styleName)) {
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
                if (node.image.source == HudImageSource.DRAWABLE) requiresSkin = true;
            }
        }
        return new HudResourceRequirements(requiresSkin, requiresAtlas,
                requiresBuiltInLabelStyle, requiresBuiltInTextButtonStyle);
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
}
