package games.pixscape.runtime.hud.document;

import java.util.ArrayList;

/** One persistently identified node in a {@link HudDocumentV1}. */
public final class HudNode {
    /** Stable document-wide identity. It is not a Java class name or a live Actor reference. */
    public String id;
    public HudNodeKind kind;
    public HudActorProperties actor = new HudActorProperties();
    /** A direct child TABLE of the root GROUP may fill that Group using Table.setFillParent. */
    public boolean fillParent;
    /** Initial Scene2D visibility; runtime Actor visibility remains independently mutable. */
    public boolean visible = true;
    /** Optional TextTooltip behavior; independent of the node's widget payload. */
    public HudTooltipData tooltip;
    /** Optional native visibility operations for authored buttons. */
    public ArrayList<HudWindowAction> windowActions = new ArrayList<HudWindowAction>();

    /** Present only for {@link HudNodeKind#IMAGE}. */
    public HudImageData image;
    /** Present only for {@link HudNodeKind#LABEL}. */
    public HudLabelData label;
    /** Present only for {@link HudNodeKind#TEXTRA_LABEL}. */
    public HudTextraLabelData textraLabel;
    /** Present only for {@link HudNodeKind#TEXT_BUTTON}. */
    public HudTextButtonData textButton;
    /** Present only for {@link HudNodeKind#IMAGE_BUTTON}. */
    public HudImageButtonData imageButton;
    /** Present only for {@link HudNodeKind#IMAGE_TEXT_BUTTON}. */
    public HudImageTextButtonData imageTextButton;
    /** Present only for {@link HudNodeKind#TEXT_FIELD}. */
    public HudTextFieldData textField;
    /** Present only for {@link HudNodeKind#SELECT_BOX}. */
    public HudSelectBoxData selectBox;
    /** Present only for {@link HudNodeKind#LIST}. */
    public HudListData list;
    /** Present only for {@link HudNodeKind#CHECK_BOX}. */
    public HudCheckBoxData checkBox;
    /** Present only for {@link HudNodeKind#SLIDER}. */
    public HudSliderData slider;
    /** Present only for {@link HudNodeKind#PROGRESS_BAR}. */
    public HudProgressBarData progressBar;
    /** Present only for {@link HudNodeKind#CONTAINER}. */
    public HudContainerData container;
    /** Present only for {@link HudNodeKind#SCROLL_PANE}. */
    public HudScrollPaneData scrollPane;
    /** Present only for {@link HudNodeKind#WINDOW}. */
    public HudWindowData window;
    /** Present only for {@link HudNodeKind#DIALOG}. */
    public HudDialogData dialog;

    /**
     * Explicit native Table content for TABLE, WINDOW and DIALOG. These parents must not also
     * use {@link #children}; cell order and colspan define their native Table order.
     */
    public HudTableLayout table;

    /** Children in Scene2D insertion/draw order for non-tabular parents. */
    public ArrayList<HudChild> children = new ArrayList<HudChild>();

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
