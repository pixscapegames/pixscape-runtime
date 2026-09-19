package games.pixscape.runtime.hud.document;

/**
 * Optional GL-free view of logical resources referenced by a HUD document.
 * Implementations do not expose or own Skin, Texture, BitmapFont, or other graphics objects.
 */
public interface HudResourceCatalog {
    boolean hasRegion(String name);

    boolean hasDrawable(String name);

    boolean hasLabelStyle(String name);

    /** Returns whether an existing named Label style supplies its own usable font. */
    default boolean hasLabelStyleFont(String name) { return hasLabelStyle(name); }

    /** Returns whether a standalone bitmap font asset was prepared in this environment. */
    default boolean hasBitmapFont(int assetId) { return false; }

    /** Returns whether the prepared environment contains the built-in Label font region. */
    default boolean hasBuiltInLabelStyle() { return false; }

    boolean hasTextButtonStyle(String name);

    default boolean hasTextButtonStyle(String name, boolean hasFontOverride) {
        return hasTextButtonStyle(name);
    }

    /** Returns whether the prepared environment contains the built-in TextButton resources. */
    default boolean hasBuiltInTextButtonStyle() { return false; }

    default boolean hasImageButtonStyle(String name) { return false; }

    /** Returns whether the prepared environment contains the built-in ImageButton resources. */
    default boolean hasBuiltInImageButtonStyle() { return false; }

    default boolean hasTextFieldStyle(String name) { return false; }

    default boolean hasTextFieldStyle(String name, boolean hasFontOverride) {
        return hasTextFieldStyle(name);
    }

    /** Returns whether the prepared environment contains the built-in TextField resources. */
    default boolean hasBuiltInTextFieldStyle() { return false; }

    default boolean hasSelectBoxStyle(String name) { return false; }

    default boolean hasSelectBoxStyle(String name, boolean hasFontOverride) {
        return hasSelectBoxStyle(name);
    }

    /** Returns whether the prepared environment contains the complete built-in SelectBox style. */
    default boolean hasBuiltInSelectBoxStyle() { return false; }

    default boolean hasCheckBoxStyle(String name) { return false; }

    default boolean hasCheckBoxStyle(String name, boolean hasFontOverride) {
        return hasCheckBoxStyle(name);
    }

    /** Returns whether the prepared environment contains the complete built-in CheckBox style. */
    default boolean hasBuiltInCheckBoxStyle() { return false; }
}
