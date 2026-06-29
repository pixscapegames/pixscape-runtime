package games.pixscape.runtime.tiled.profile;

public enum RuntimeTilesetAnchor {
    TOP_CENTER("top-center"),
    BOTTOM_CENTER("bottom-center"),
    BOTTOM_LEFT("bottom-left"),
    CENTER("center"),
    TOP_LEFT("top-left");

    private final String wireName;

    RuntimeTilesetAnchor(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static RuntimeTilesetAnchor fromWireName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        for (RuntimeTilesetAnchor anchor : values()) {
            if (anchor.wireName.equalsIgnoreCase(raw) || anchor.name().equalsIgnoreCase(raw)) {
                return anchor;
            }
        }
        return null;
    }
}
