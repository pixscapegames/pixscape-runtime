package games.pixscape.runtime.hud.document;

/** Optional native TextTooltip authored on an existing HUD node. */
public final class HudTooltipData {
    public String text = "Tooltip";
    /** Null or blank selects the built-in Default style. */
    public String styleName;
    /** Optional standalone bitmap-font Asset override. */
    public Integer fontAssetId;
}
