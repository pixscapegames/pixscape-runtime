package games.pixscape.runtime.tiled;

public final class PackedTileValue {
    private PackedTileValue() {}

    private static final int ASSET_MASK = 0x00FF_FFFF;
    private static final int FLAGS_SHIFT = 24;

    public static int pack(int assetId, byte flags) {
        return (assetId & ASSET_MASK) | ((flags & 0x7) << FLAGS_SHIFT);
    }

    public static int assetId(int packed) {
        return packed & ASSET_MASK;
    }

    public static byte flags(int packed) {
        return (byte) ((packed >>> FLAGS_SHIFT) & 0x7);
    }
}