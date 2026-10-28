package games.pixscape.runtime.hud.document;

/** Typed Label payload. A null/blank style selects the built-in style; otherwise Skin owns it. */
public final class HudLabelData {
    public String text;
    public String styleName;
    /** Optional project Asset ID overriding only the selected Label style's font. */
    public Integer fontAssetId;
}
