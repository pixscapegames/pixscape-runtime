package games.pixscape.runtime.gameobject;

/** Canonical project-relative logical identity for a Game Object asset. */
public final class GameObjectAssetId {
    public static final String DIRECTORY = "gameobjects";
    private static final String PREFIX = DIRECTORY + "/";
    private static final String RUNTIME_FRAGMENT_SUFFIX = ".pixfragment.json";

    private GameObjectAssetId() { }

    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Game Object asset ID is required.");
        String normalized = value.trim().replace('\\', '/');
        if (normalized.length() == 0) {
            throw new IllegalArgumentException("Game Object asset ID must not be blank.");
        }
        if (normalized.startsWith("/") || normalized.indexOf(':') >= 0
                || normalized.equals("..") || normalized.startsWith("../")
                || normalized.endsWith("/..") || normalized.indexOf("/../") >= 0) {
            throw new IllegalArgumentException("Game Object asset ID must be project-relative: "
                    + value + ".");
        }
        if (!normalized.startsWith(PREFIX)) normalized = PREFIX + normalized;
        String local = normalized.substring(PREFIX.length());
        if (local.length() == 0 || local.indexOf('/') >= 0) {
            throw new IllegalArgumentException("Game Object asset ID must identify one asset in "
                    + PREFIX + ".");
        }
        if (!local.endsWith(GameObjectAsset.EXTENSION)) {
            local += GameObjectAsset.EXTENSION;
        }
        if (local.length() == GameObjectAsset.EXTENSION.length()) {
            throw new IllegalArgumentException("Game Object asset name is required.");
        }
        return PREFIX + local;
    }

    public static String assetName(String logicalId) {
        String normalized = normalize(logicalId);
        return normalized.substring(PREFIX.length(),
                normalized.length() - GameObjectAsset.EXTENSION.length());
    }

    public static String runtimeFragmentRelativePath(String logicalId) {
        return PREFIX + assetName(logicalId) + RUNTIME_FRAGMENT_SUFFIX;
    }
}
