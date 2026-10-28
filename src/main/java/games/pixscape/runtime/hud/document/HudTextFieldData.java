package games.pixscape.runtime.hud.document;

/** Typed TextField payload using a logical Skin style name. */
public final class HudTextFieldData {
    public String text = "";
    public String messageText = "";
    public String styleName;
    public Integer fontAssetId;
    /** Zero means unlimited. */
    public int maxLength;
    public boolean passwordMode;
}
