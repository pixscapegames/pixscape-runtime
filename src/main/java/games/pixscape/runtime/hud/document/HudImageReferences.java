package games.pixscape.runtime.hud.document;

/** Visits every explicit image reference carried by one HUD node. */
public final class HudImageReferences {
    /** Receives the node-relative field path and its configured image reference. */
    public interface Visitor {
        void visit(HudNode node, String fieldPath, HudImageData image);
    }

    private HudImageReferences() {
    }

    /** Visits references in stable document-field order, ignoring absent optional states. */
    public static void visit(HudNode node, Visitor visitor) {
        if (node == null) throw new IllegalArgumentException("HUD node is required.");
        if (visitor == null) throw new IllegalArgumentException("Image visitor is required.");

        if (node.kind == HudNodeKind.IMAGE && node.image != null) {
            visitor.visit(node, "image", node.image);
        } else if (node.kind == HudNodeKind.IMAGE_BUTTON && node.imageButton != null) {
            visit(visitor, node, "imageButton.imageUp", node.imageButton.imageUp);
            visit(visitor, node, "imageButton.imageDown", node.imageButton.imageDown);
            visit(visitor, node, "imageButton.imageOver", node.imageButton.imageOver);
            visit(visitor, node, "imageButton.imageDisabled", node.imageButton.imageDisabled);
            visit(visitor, node, "imageButton.imageChecked", node.imageButton.imageChecked);
            visit(visitor, node, "imageButton.imageCheckedDown", node.imageButton.imageCheckedDown);
            visit(visitor, node, "imageButton.imageCheckedOver", node.imageButton.imageCheckedOver);
        }
    }

    private static void visit(Visitor visitor, HudNode node, String fieldPath,
                              HudImageData image) {
        if (image != null) visitor.visit(node, fieldPath, image);
    }
}
