package games.pixscape.runtime.hud.document;

/** Explicit access to the optional bitmap-font override of native textual HUD widgets. */
public final class HudFontReferences {
    private HudFontReferences() { }

    public static Integer assetId(HudNode node) {
        if (node == null || node.kind == null) return null;
        switch (node.kind) {
            case LABEL: return node.label == null ? null : node.label.fontAssetId;
            case TEXTRA_LABEL: return node.textraLabel == null ? null : node.textraLabel.fontAssetId;
            case TEXT_BUTTON: return node.textButton == null ? null : node.textButton.fontAssetId;
            case IMAGE_TEXT_BUTTON: return node.imageTextButton == null ? null : node.imageTextButton.fontAssetId;
            case CHECK_BOX: return node.checkBox == null ? null : node.checkBox.fontAssetId;
            case TEXT_FIELD: return node.textField == null ? null : node.textField.fontAssetId;
            case SELECT_BOX: return node.selectBox == null ? null : node.selectBox.fontAssetId;
            case LIST: return node.list == null ? null : node.list.fontAssetId;
            case WINDOW: return node.window == null ? null : node.window.fontAssetId;
            case DIALOG: return node.dialog == null ? null : node.dialog.fontAssetId;
            default: return null;
        }
    }

    public static Integer tooltipAssetId(HudNode node) {
        return node == null || node.tooltip == null ? null : node.tooltip.fontAssetId;
    }

    public static boolean supports(HudNodeKind kind) {
        return kind == HudNodeKind.LABEL || kind == HudNodeKind.TEXTRA_LABEL
                || kind == HudNodeKind.TEXT_BUTTON
                || kind == HudNodeKind.IMAGE_TEXT_BUTTON
                || kind == HudNodeKind.CHECK_BOX || kind == HudNodeKind.TEXT_FIELD
                || kind == HudNodeKind.SELECT_BOX || kind == HudNodeKind.LIST || kind == HudNodeKind.WINDOW
                || kind == HudNodeKind.DIALOG;
    }

    public static void setAssetId(HudNode node, Integer assetId) {
        if (node == null || node.kind == null) {
            throw new IllegalArgumentException("A typed textual HUD node is required.");
        }
        switch (node.kind) {
            case LABEL: node.label.fontAssetId = assetId; return;
            case TEXTRA_LABEL: node.textraLabel.fontAssetId = assetId; return;
            case TEXT_BUTTON: node.textButton.fontAssetId = assetId; return;
            case IMAGE_TEXT_BUTTON: node.imageTextButton.fontAssetId = assetId; return;
            case CHECK_BOX: node.checkBox.fontAssetId = assetId; return;
            case TEXT_FIELD: node.textField.fontAssetId = assetId; return;
            case SELECT_BOX: node.selectBox.fontAssetId = assetId; return;
            case LIST: node.list.fontAssetId = assetId; return;
            case WINDOW: node.window.fontAssetId = assetId; return;
            case DIALOG: node.dialog.fontAssetId = assetId; return;
            default: throw new IllegalArgumentException(
                    "HUD node kind " + node.kind + " has no font override.");
        }
    }
}
