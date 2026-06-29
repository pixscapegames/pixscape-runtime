package games.pixscape.runtime.tiled.profile;

public enum RuntimeTilesetRenderSize {
    NATIVE("native");

    private final String wireName;

    RuntimeTilesetRenderSize(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static RuntimeTilesetRenderSize fromWireName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        for (RuntimeTilesetRenderSize renderSize : values()) {
            if (renderSize.wireName.equalsIgnoreCase(raw) || renderSize.name().equalsIgnoreCase(raw)) {
                return renderSize;
            }
        }
        return null;
    }
}
