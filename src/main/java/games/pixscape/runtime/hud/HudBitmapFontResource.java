package games.pixscape.runtime.hud;

/** Stable exported paths and atlas keys for standalone HUD bitmap-font assets. */
public final class HudBitmapFontResource {
    private HudBitmapFontResource() {
    }

    public static String descriptorId(int assetId) {
        requireAssetId(assetId);
        return "fonts/" + assetId + ".fnt";
    }

    public static String pageKey(int assetId, int pageIndex) {
        requireAssetId(assetId);
        if (pageIndex < 0) {
            throw new IllegalArgumentException("Bitmap-font page index must be nonnegative.");
        }
        return "hud-font-" + assetId + "-page-" + pageIndex;
    }

    private static void requireAssetId(int assetId) {
        if (assetId <= 0) {
            throw new IllegalArgumentException("Bitmap-font Asset ID must be positive.");
        }
    }
}
