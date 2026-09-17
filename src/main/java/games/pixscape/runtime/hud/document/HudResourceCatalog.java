package games.pixscape.runtime.hud.document;

/**
 * Optional GL-free view of logical resources referenced by a HUD document.
 * Implementations do not expose or own Skin, Texture, BitmapFont, or other graphics objects.
 */
public interface HudResourceCatalog {
    boolean hasRegion(String name);

    boolean hasDrawable(String name);

    boolean hasLabelStyle(String name);

    /** Returns whether the prepared environment contains the built-in Label font region. */
    default boolean hasBuiltInLabelStyle() { return false; }

    boolean hasTextButtonStyle(String name);

    /** Returns whether the prepared environment contains the built-in TextButton resources. */
    default boolean hasBuiltInTextButtonStyle() { return false; }
}
