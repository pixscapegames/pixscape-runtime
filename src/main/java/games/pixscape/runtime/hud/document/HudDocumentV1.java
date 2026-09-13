package games.pixscape.runtime.hud.document;

/**
 * Version-1 authored HUD construction document.
 *
 * <p>The document has exactly one root node. The root is materialized against the HUD reference
 * viewport; all other nodes are owned by an ordered {@link HudChild} entry. This class contains
 * construction data only and never owns live Scene2D or graphics resources.</p>
 */
public final class HudDocumentV1 {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public HudNode root;

    /** Required by libGDX Json. */
    public HudDocumentV1() {
    }

    public HudDocumentV1(HudNode root) {
        if (root == null) throw new IllegalArgumentException("HUD document root is required.");
        this.root = root;
    }
}
