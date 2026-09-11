package games.pixscape.runtime.hud;

/** Authored data for an autonomous reusable HUD screen. */
public final class HudScreenAsset {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int DEFAULT_REFERENCE_WIDTH = 1920;
    public static final int DEFAULT_REFERENCE_HEIGHT = 1080;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public int referenceWidth = DEFAULT_REFERENCE_WIDTH;
    public int referenceHeight = DEFAULT_REFERENCE_HEIGHT;

    /** Validates the complete version-1 authored HUD screen model. */
    public void validate() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("HudScreenAsset requires schemaVersion "
                    + CURRENT_SCHEMA_VERSION + ", found " + schemaVersion + ".");
        }
        if (referenceWidth <= 0) {
            throw new IllegalArgumentException("HudScreenAsset referenceWidth must be positive.");
        }
        if (referenceHeight <= 0) {
            throw new IllegalArgumentException("HudScreenAsset referenceHeight must be positive.");
        }
    }
}
