package games.pixscape.runtime.hud.document;

import java.util.ArrayList;
import java.util.List;

/** One persistently identified node in a {@link HudDocumentV1}. */
public final class HudNode {
    /** Stable document-wide identity. It is not a Java class name or a live Actor reference. */
    public String id;
    public HudNodeKind kind;
    public HudActorProperties actor = new HudActorProperties();

    /** Present only for {@link HudNodeKind#IMAGE}. */
    public HudImageData image;
    /** Present only for {@link HudNodeKind#LABEL}. */
    public HudLabelData label;
    /** Present only for {@link HudNodeKind#TEXT_BUTTON}. */
    public HudTextButtonData textButton;
    /** Present only for {@link HudNodeKind#CONTAINER}. */
    public HudContainerData container;

    /** Children in Scene2D insertion/draw order. Parent links are derived and never persisted. */
    public List<HudChild> children = new ArrayList<HudChild>();

    /** Required by libGDX Json. */
    public HudNode() {
    }

    public HudNode(String id, HudNodeKind kind) {
        if (id == null || id.trim().length() == 0) {
            throw new IllegalArgumentException("HUD node ID must not be blank.");
        }
        if (kind == null) throw new IllegalArgumentException("HUD node kind is required.");
        this.id = id;
        this.kind = kind;
    }
}
