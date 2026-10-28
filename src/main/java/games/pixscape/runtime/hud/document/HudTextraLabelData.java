package games.pixscape.runtime.hud.document;

/** Typed TextraTypist label payload using a native Scene2D Label style. */
public final class HudTextraLabelData {
    public String text;
    public String styleName;
    /** Optional project Asset ID overriding only the selected Label style's font. */
    public Integer fontAssetId;
    /** Whether native TypingLabel character progression runs during Stage.act(). */
    public boolean typingEnabled = true;
}
