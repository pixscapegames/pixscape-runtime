package games.pixscape.runtime.hud;

/** Explicit descriptor location for one prepared standalone bitmap-font Asset. */
public final class HudBitmapFontSpec {
    private final int assetId;
    private final String descriptorId;

    public HudBitmapFontSpec(int assetId, String descriptorId) {
        if (assetId <= 0) throw new IllegalArgumentException("Bitmap-font Asset ID must be positive.");
        this.assetId = assetId;
        this.descriptorId = HudResourceId.normalizeOptional(descriptorId,
                "Bitmap-font descriptor");
        if (this.descriptorId == null) {
            throw new IllegalArgumentException("Bitmap-font descriptor ID is required.");
        }
    }

    public int assetId() { return assetId; }
    public String descriptorId() { return descriptorId; }
}
