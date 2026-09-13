package games.pixscape.runtime.hud;

/** Canonical project-relative logical identity for a HUD screen asset. */
public final class HudScreenAssetId {
    public static final String DIRECTORY = "hud";
    private static final String PREFIX = DIRECTORY + "/";

    private HudScreenAssetId() {
    }

    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("HUD screen asset ID is required.");
        String normalized = value.trim().replace('\\', '/');
        if (normalized.length() == 0) {
            throw new IllegalArgumentException("HUD screen asset ID must not be blank.");
        }
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0
                || normalized.equals("..") || normalized.startsWith("../")
                || normalized.endsWith("/..") || normalized.indexOf("/../") >= 0) {
            throw new IllegalArgumentException("HUD screen asset ID must be project-relative: "
                    + value + ".");
        }
        if (!normalized.startsWith(PREFIX)) normalized = PREFIX + normalized;
        String local = normalized.substring(PREFIX.length());
        if (local.length() == 0 || local.indexOf('/') >= 0) {
            throw new IllegalArgumentException("HUD screen asset ID must identify one asset in "
                    + PREFIX + ".");
        }
        return PREFIX + local;
    }

    public static String normalizeOptional(String value) {
        return value == null || value.trim().length() == 0 ? null : normalize(value);
    }

    /** Returns the file stem encoded by a canonical or shorthand logical screen ID. */
    public static String assetName(String value) {
        String normalized = normalize(value);
        return normalized.substring(PREFIX.length());
    }
}
