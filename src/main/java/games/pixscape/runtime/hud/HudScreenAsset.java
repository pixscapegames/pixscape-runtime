package games.pixscape.runtime.hud;

/** Authored data for an autonomous reusable HUD screen. */
public final class HudScreenAsset {
    public static final String EXTENSION = ".hudscreen";
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    /** Project-relative versioned HUD construction document. */
    public String documentId;
    /** Project-relative Skin JSON used to prepare this HUD, or {@code null} while unauthored. */
    public String skinId;
    /** Project-relative TextureAtlas descriptor used to prepare this HUD, or {@code null}. */
    public String atlasId;
    /** Stable built-in texture profile identifier. */
    public String textureProfileId = HudTextureProfile.DEFAULT_ID;

    /** Validates the basic version-1 model; physical resources may remain unassigned. */
    public void validate() {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("HudScreenAsset requires schemaVersion "
                    + CURRENT_SCHEMA_VERSION + ", found " + schemaVersion + ".");
        }
        HudResourceId.normalizeOptional(skinId, "Skin");
        HudResourceId.normalizeOptional(atlasId, "TextureAtlas");
        if (HudResourceId.normalizeOptional(documentId, "HUD document") == null) {
            throw new IllegalArgumentException("HudScreenAsset documentId is required.");
        }
        HudTextureProfile.normalizeIdOrDefault(textureProfileId);
    }
}
